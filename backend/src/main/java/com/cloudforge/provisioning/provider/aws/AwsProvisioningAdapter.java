package com.cloudforge.provisioning.provider.aws;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cloudforge.provisioning.provider.CloudProvisioningProvider;
import com.cloudforge.provisioning.provider.ProviderMode;
import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.AwsOrganizationsNotInUseException;
import software.amazon.awssdk.services.organizations.model.ConcurrentModificationException;
import software.amazon.awssdk.services.organizations.model.ConstraintViolationException;
import software.amazon.awssdk.services.organizations.model.CreateAccountFailureReason;
import software.amazon.awssdk.services.organizations.model.CreateAccountRequest;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatus;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusRequest;
import software.amazon.awssdk.services.organizations.model.DuplicateAccountException;
import software.amazon.awssdk.services.organizations.model.InvalidInputException;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.awssdk.services.organizations.model.ServiceException;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;

/**
 * Creates member accounts with AWS Organizations ({@code CreateAccount}, SDK v2).
 *
 * <p>Account creation is asynchronous: {@code CreateAccount} returns a status ID that
 * {@code DescribeCreateAccountStatus} follows until SUCCEEDED or FAILED. New accounts land in the
 * organization root, so the adapter then moves the account into the requested organizational unit.
 *
 * <p>Idempotency: the root email must be unique across AWS, so a retry first looks for an existing
 * member account with that email. If one exists and carries this request's {@code cloudforge-request-id}
 * tag, an earlier attempt created it and the adapter finishes the job (moving it to the OU if needed);
 * otherwise the email is taken and the failure is permanent.
 */
public class AwsProvisioningAdapter implements CloudProvisioningProvider {

    static final String REQUEST_ID_TAG = "cloudforge-request-id";
    static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(AwsProvisioningAdapter.class);

    private final OrganizationsClient organizations;

    public AwsProvisioningAdapter(OrganizationsClient organizations) {
        this.organizations = organizations;
    }

    @Override
    public ResourceTypeCode type() {
        return ResourceTypeCode.AWS_ACCOUNT;
    }

    @Override
    public ProviderMode mode() {
        return ProviderMode.REAL;
    }

    @Override
    public ProvisioningOutcome start(ProvisioningCommand command) {
        String email = command.spec().get("email").stringValue();
        try {
            Optional<Account> existing = findAccountByEmail(email);
            if (existing.isPresent()) {
                return isOwnedBy(existing.get().id(), command)
                        ? completeInOu(existing.get().id(), command)
                        : emailTaken();
            }
            CreateAccountStatus status = organizations.createAccount(CreateAccountRequest.builder()
                    .accountName(command.spec().get("accountName").stringValue())
                    .email(email)
                    .tags(Tag.builder().key(REQUEST_ID_TAG).value(command.requestId().toString()).build(),
                            Tag.builder().key("managed-by").value("cloudforge").build())
                    .build()).createAccountStatus();
            log.info("Requested AWS account creation for {} (status id {})", command.requestNumber(), status.id());
            return fromStatus(status, command);
        } catch (SdkException e) {
            return classify(e);
        }
    }

    @Override
    public ProvisioningOutcome poll(ProvisioningCommand command, String createAccountRequestId) {
        try {
            CreateAccountStatus status = organizations.describeCreateAccountStatus(DescribeCreateAccountStatusRequest.builder()
                    .createAccountRequestId(createAccountRequestId).build()).createAccountStatus();
            return fromStatus(status, command);
        } catch (SdkException e) {
            return classify(e);
        }
    }

    private ProvisioningOutcome fromStatus(CreateAccountStatus status, ProvisioningCommand command) {
        return switch (status.state()) {
            case IN_PROGRESS -> new ProvisioningOutcome.InProgress(status.id(), POLL_INTERVAL);
            case SUCCEEDED -> completeInOu(status.accountId(), command);
            // A finished request is final; an administrator retry starts a fresh CreateAccount.
            case FAILED -> status.failureReason() == CreateAccountFailureReason.EMAIL_ALREADY_EXISTS
                    ? emailTaken()
                    : ProvisioningOutcome.Failed.permanent("ACCOUNT_CREATION_FAILED",
                            "AWS could not create the account (" + status.failureReasonAsString() + ").");
            default -> ProvisioningOutcome.Failed.retryable("PROVIDER_ERROR",
                    "Unknown account creation state from AWS: " + status.stateAsString());
        };
    }

    /** Moves the account from wherever it is (normally the root) into the requested OU, then completes. */
    private ProvisioningOutcome completeInOu(String accountId, ProvisioningCommand command) {
        String targetOu = command.spec().get("organizationalUnitId").stringValue();
        List<Parent> parents = organizations.listParents(ListParentsRequest.builder().childId(accountId).build()).parents();
        String currentParent = parents.isEmpty() ? null : parents.getFirst().id();
        if (currentParent != null && !currentParent.equals(targetOu)) {
            try {
                organizations.moveAccount(MoveAccountRequest.builder()
                        .accountId(accountId).sourceParentId(currentParent).destinationParentId(targetOu).build());
            } catch (DuplicateAccountException e) {
                // Already in the destination (a concurrent or earlier move): nothing to do.
            }
        }
        return new ProvisioningOutcome.Completed(accountId, Map.of("accountId", accountId, "organizationalUnitId", targetOu));
    }

    private Optional<Account> findAccountByEmail(String email) {
        for (ListAccountsResponse page : organizations.listAccountsPaginator(ListAccountsRequest.builder().build())) {
            for (Account account : page.accounts()) {
                if (email.equalsIgnoreCase(account.email())) {
                    return Optional.of(account);
                }
            }
        }
        return Optional.empty();
    }

    private boolean isOwnedBy(String accountId, ProvisioningCommand command) {
        return organizations.listTagsForResource(ListTagsForResourceRequest.builder().resourceId(accountId).build())
                .tags().stream()
                .anyMatch(tag -> REQUEST_ID_TAG.equals(tag.key()) && command.requestId().toString().equals(tag.value()));
    }

    private static ProvisioningOutcome emailTaken() {
        return ProvisioningOutcome.Failed.permanent("EMAIL_ALREADY_IN_USE",
                "The root email address is already used by another AWS account.");
    }

    /** SDK messages are not copied verbatim: they can contain ARNs and account IDs. */
    static ProvisioningOutcome classify(SdkException e) {
        if (e instanceof TooManyRequestsException || e instanceof ConcurrentModificationException
                || e instanceof ServiceException || e instanceof SdkClientException) {
            return ProvisioningOutcome.Failed.retryable("PROVIDER_UNAVAILABLE",
                    "AWS Organizations is temporarily unavailable (" + e.getClass().getSimpleName() + ").");
        }
        if (e instanceof AccessDeniedException) {
            return ProvisioningOutcome.Failed.permanent("ACCESS_DENIED",
                    "CloudForge's AWS identity is not allowed to create or move accounts.");
        }
        if (e instanceof AwsOrganizationsNotInUseException) {
            return ProvisioningOutcome.Failed.permanent("ORGANIZATIONS_NOT_ENABLED",
                    "The configured AWS account is not the management account of an organization.");
        }
        if (e instanceof ConstraintViolationException) {
            return ProvisioningOutcome.Failed.permanent("CONSTRAINT_VIOLATION",
                    "AWS Organizations refused the request because of an organization limit.");
        }
        if (e instanceof InvalidInputException) {
            return ProvisioningOutcome.Failed.permanent("INVALID_REQUEST", "AWS Organizations rejected the request input.");
        }
        if (e instanceof AwsServiceException service && (service.statusCode() == 429 || service.statusCode() >= 500)) {
            return ProvisioningOutcome.Failed.retryable("PROVIDER_UNAVAILABLE",
                    "AWS Organizations is temporarily unavailable (HTTP " + service.statusCode() + ").");
        }
        return ProvisioningOutcome.Failed.permanent("PROVIDER_ERROR",
                "Unexpected AWS Organizations error (" + e.getClass().getSimpleName() + ").");
    }
}
