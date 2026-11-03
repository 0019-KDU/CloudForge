# AWS sandbox setup for real AWS account provisioning

CloudForge creates **real AWS member accounts** with AWS Organizations when AWS runs in `real` mode.
Read before starting:

- A closed AWS account stays suspended for 90 days, and only 10% of accounts can be closed per month.
- Every account needs a **unique root email** (across all of AWS).
- A new organization allows about 10 accounts by default.
- If you use a **company** AWS account, get approval from the people who own your company's AWS
  organization first. Never run CloudForge with admin credentials.
- Never put access keys in the repo, in `backend/.env` or in chat. They belong only in your local AWS CLI profile.

## Step 1: Check the AWS account is an organization's management account

`CreateAccount` only works from the **management account** of an AWS Organization.

Console: open **AWS Organizations**.

- It lists accounts and marks your account as **Management account** → this account works. Continue.
- It says the account is a member, or offers **Create an organization** → this account can't create
  accounts. Use a separate sandbox AWS account, or ask for access to the management account.

For a brand-new sandbox account (not the company's), enable Organizations yourself:

```powershell
aws organizations create-organization --feature-set ALL --profile sandbox-admin
```

## Step 2: Create a sandbox OU

Console: **AWS Organizations → AWS accounts → Root → Actions → Create new** (organizational unit),
name `CloudForge-Sandbox`. Or with the CLI (as an admin):

```powershell
aws organizations list-roots --profile sandbox-admin
aws organizations create-organizational-unit --parent-id r-xxxx --name CloudForge-Sandbox --profile sandbox-admin
```

Note the OU ID (`ou-xxxx-xxxxxxxx`).

## Step 3: Create the CloudForge policy

Console: **IAM → Policies → Create policy → JSON**, paste, name it `CloudForgeOrganizationsProvisioning`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "organizations:CreateAccount",
        "organizations:DescribeCreateAccountStatus",
        "organizations:ListAccounts",
        "organizations:ListParents",
        "organizations:ListTagsForResource",
        "organizations:MoveAccount",
        "organizations:TagResource"
      ],
      "Resource": "*"
    }
  ]
}
```

These are exactly the calls the backend makes (`AwsProviderConfig.java`).

## Step 4: Create a dedicated IAM user for CloudForge

Do **not** create access keys for your own admin user.

1. **IAM → Users → Create user**, name `cloudforge-provisioner`, no console access.
2. **Attach policies directly** → only `CloudForgeOrganizationsProvisioning`.
3. Open the user → **Security credentials → Create access key → Application running outside AWS**.
   Copy the Access key ID and Secret access key (the secret is shown only once).

## Step 5: Save the keys as an AWS CLI profile on your PC

```powershell
aws configure --profile cloudforge-sandbox
```

Answers: Access key ID, Secret access key, region `us-east-1`, output `json`.
The keys are stored in `C:\Users\<you>\.aws\credentials`.

Check:

```powershell
aws sts get-caller-identity --profile cloudforge-sandbox
aws organizations describe-organization --profile cloudforge-sandbox
aws organizations list-accounts --profile cloudforge-sandbox
```

- The first shows `user/cloudforge-provisioner`.
- In the second, `MasterAccountId` must equal your account ID.

## Step 6: Switch only AWS to real mode

The `local` profile keeps every provider in `mock` (`application-local.yaml`). Operating-system
environment variables take precedence, so set these in the **IntelliJ run configuration →
Environment variables** (not in `backend/.env`):

```
CLOUDFORGE_PROVISIONING_AWS_MODE=real
AWS_PROFILE=cloudforge-sandbox
```

Restart the backend. The startup log warns that AWS runs in **real** mode. GCP and Kubernetes stay `mock`.

## Step 7: Allow AWS accounts in CloudForge

Sign in as admin → **Governance → Resource policies → AWS account → Development → Edit**:

| Field | Value |
|---|---|
| Allow requests | ✔ |
| Active requests per person | `1` |
| Approved organizational units | your `ou-xxxx-xxxxxxxx` |
| Approved email domains | the domain you'll use for root emails, e.g. your company domain |

## Step 8: First test

1. Admin: **Governance → Invitations** → invite a Manager, then an Engineer with that manager.
2. Engineer: **New request → AWS account**
   - account name `cf-sandbox-01`
   - root email: unique, e.g. `you+cf-sandbox-01@yourdomain.com`
   - organizational unit: your OU
3. Manager: open the request → **Approve**.
4. CloudForge calls `CreateAccount`, checks every 30 seconds (usually a few minutes), moves the
   account into the OU, and the request shows **Provisioned** with the new account ID.
5. Check **AWS Organizations**: the account is in `CloudForge-Sandbox` with tags
   `cloudforge-request-id` and `managed-by=cloudforge`.

## Errors shown on the request page

| Error | Cause |
|---|---|
| `ACCESS_DENIED` | Missing permission or wrong `AWS_PROFILE` |
| `ORGANIZATIONS_NOT_ENABLED` | Not the organization's management account |
| `EMAIL_ALREADY_IN_USE` | That root email already belongs to an AWS account |
| `CONSTRAINT_VIOLATION` | Usually the organization's account limit |
| `INVALID_REQUEST` | AWS rejected the input (name or email) |

CloudTrail in the management account shows the exact failed call.

## Cleanup and back to safe mode

- Close a test account (with an admin profile; CloudForge's user can't do this):
  `aws organizations close-account --account-id <id> --profile sandbox-admin`
- Remove `CLOUDFORGE_PROVISIONING_AWS_MODE` from the run configuration (back to `mock`) and restart.
- Delete or deactivate the `cloudforge-provisioner` access key when you're done testing.
