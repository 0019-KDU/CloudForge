package com.cloudforge.provisioning.provider.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.cloudforge.provisioning.provider.ProvisioningCommand;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Completed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.Failed;
import com.cloudforge.provisioning.provider.ProvisioningOutcome.InProgress;
import com.cloudforge.resourcecatalog.domain.Environment;
import com.cloudforge.resourcecatalog.domain.ResourceTypeCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.organizations.OrganizationsClient;
import software.amazon.awssdk.services.organizations.model.AccessDeniedException;
import software.amazon.awssdk.services.organizations.model.Account;
import software.amazon.awssdk.services.organizations.model.CreateAccountFailureReason;
import software.amazon.awssdk.services.organizations.model.CreateAccountRequest;
import software.amazon.awssdk.services.organizations.model.CreateAccountResponse;
import software.amazon.awssdk.services.organizations.model.CreateAccountState;
import software.amazon.awssdk.services.organizations.model.CreateAccountStatus;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusRequest;
import software.amazon.awssdk.services.organizations.model.DescribeCreateAccountStatusResponse;
import software.amazon.awssdk.services.organizations.model.ListAccountsRequest;
import software.amazon.awssdk.services.organizations.model.ListAccountsResponse;
import software.amazon.awssdk.services.organizations.model.ListParentsRequest;
import software.amazon.awssdk.services.organizations.model.ListParentsResponse;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.organizations.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.organizations.model.MoveAccountRequest;
import software.amazon.awssdk.services.organizations.model.Parent;
import software.amazon.awssdk.services.organizations.model.Tag;
import software.amazon.awssdk.services.organizations.model.TooManyRequestsException;
import software.amazon.awssdk.services.organizations.paginators.ListAccountsIterable;
import tools.jackson.databind.json.JsonMapper;

class AwsProvisioningAdapterTest {

    private static final String OU = "ou-ab12-cd34ef56";
    private static final String ROOT = "r-ab12";
    private static final String EMAIL = "aws-payments-dev@acme.com";

    private final UUID requestId = UUID.randomUUID();
    private final OrganizationsClient organizations = mock(OrganizationsClient.class);
    private final AwsProvisioningAdapter adapter = new AwsProvisioningAdapter(organizations);

    private final ProvisioningCommand command = new ProvisioningCommand(requestId, "CF-2026-000099",
            ResourceTypeCode.AWS_ACCOUNT, Environment.PRODUCTION, JsonMapper.builder().build().readTree(
                    "{\"accountName\": \"payments-dev\", \"email\": \"" + EMAIL + "\", \"organizationalUnitId\": \"" + OU + "\"}"),
            "cloudforge-" + requestId);

    @BeforeEach
    void noExistingAccounts() {
        when(organizations.listAccountsPaginator(any(ListAccountsRequest.class)))
                .thenAnswer(call -> new ListAccountsIterable(organizations, call.getArgument(0)));
        accounts();
    }

    private void accounts(Account... accounts) {
        when(organizations.listAccounts(any(ListAccountsRequest.class)))
                .thenReturn(ListAccountsResponse.builder().accounts(accounts).build());
    }

    private static CreateAccountStatus status(CreateAccountState state) {
        return CreateAccountStatus.builder().id("car-123").state(state).accountId("111122223333").build();
    }

    private void accountIsIn(String parent) {
        when(organizations.listParents(any(ListParentsRequest.class)))
                .thenReturn(ListParentsResponse.builder().parents(Parent.builder().id(parent).build()).build());
    }

    @Test
    void startsAsyncAccountCreationWithOwnershipTag() {
        when(organizations.createAccount(any(CreateAccountRequest.class)))
                .thenReturn(CreateAccountResponse.builder().createAccountStatus(status(CreateAccountState.IN_PROGRESS)).build());

        assertThat(adapter.start(command)).isEqualTo(new InProgress("car-123", AwsProvisioningAdapter.POLL_INTERVAL));

        ArgumentCaptor<CreateAccountRequest> sent = ArgumentCaptor.forClass(CreateAccountRequest.class);
        verify(organizations).createAccount(sent.capture());
        assertThat(sent.getValue().email()).isEqualTo(EMAIL);
        assertThat(sent.getValue().accountName()).isEqualTo("payments-dev");
        assertThat(sent.getValue().tags()).contains(Tag.builder().key("cloudforge-request-id").value(requestId.toString()).build());
    }

    @Test
    void successfulCreationMovesTheAccountFromRootIntoTheRequestedOu() {
        when(organizations.describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class)))
                .thenReturn(DescribeCreateAccountStatusResponse.builder().createAccountStatus(status(CreateAccountState.SUCCEEDED)).build());
        accountIsIn(ROOT);

        Completed completed = (Completed) adapter.poll(command, "car-123");

        assertThat(completed.externalResourceId()).isEqualTo("111122223333");
        verify(organizations).moveAccount(MoveAccountRequest.builder()
                .accountId("111122223333").sourceParentId(ROOT).destinationParentId(OU).build());
    }

    @Test
    void retryAdoptsOnlyTheAccountItCreatedEarlier() {
        accounts(Account.builder().id("111122223333").email(EMAIL).build());
        when(organizations.listTagsForResource(any(ListTagsForResourceRequest.class))).thenReturn(ListTagsForResourceResponse
                .builder().tags(Tag.builder().key("cloudforge-request-id").value(requestId.toString()).build()).build());
        accountIsIn(OU);

        assertThat(adapter.start(command)).isInstanceOf(Completed.class);
        verify(organizations, never()).createAccount(any(CreateAccountRequest.class));
        verify(organizations, never()).moveAccount(any(MoveAccountRequest.class));
    }

    @Test
    void emailUsedByAnotherAccountIsPermanent() {
        accounts(Account.builder().id("999999999999").email(EMAIL.toUpperCase()).build());
        when(organizations.listTagsForResource(any(ListTagsForResourceRequest.class)))
                .thenReturn(ListTagsForResourceResponse.builder().tags(List.of()).build());

        Failed failed = (Failed) adapter.start(command);

        assertThat(failed.code()).isEqualTo("EMAIL_ALREADY_IN_USE");
        assertThat(failed.retryable()).isFalse();
        verify(organizations, never()).createAccount(any(CreateAccountRequest.class));
    }

    @Test
    void failedCreationIsReportedWithItsReason() {
        when(organizations.describeCreateAccountStatus(any(DescribeCreateAccountStatusRequest.class)))
                .thenReturn(DescribeCreateAccountStatusResponse.builder().createAccountStatus(CreateAccountStatus.builder()
                        .id("car-123").state(CreateAccountState.FAILED)
                        .failureReason(CreateAccountFailureReason.ACCOUNT_LIMIT_EXCEEDED).build()).build());

        Failed failed = (Failed) adapter.poll(command, "car-123");

        assertThat(failed.retryable()).isFalse();
        assertThat(failed.message()).contains("ACCOUNT_LIMIT_EXCEEDED");
    }

    @Test
    void classifiesSdkErrors() {
        when(organizations.createAccount(any(CreateAccountRequest.class)))
                .thenThrow(TooManyRequestsException.builder().message("Rate exceeded").statusCode(400).build());
        assertThat(((Failed) adapter.start(command)).retryable()).isTrue();

        when(organizations.listAccounts(any(ListAccountsRequest.class)))
                .thenThrow(SdkClientException.create("Unable to execute HTTP request: connect timed out"));
        assertThat(((Failed) adapter.start(command)).retryable()).isTrue();

        when(organizations.listAccounts(any(ListAccountsRequest.class)))
                .thenThrow(AccessDeniedException.builder().message("User arn:aws:iam::1:role/x is not authorized")
                        .statusCode(400).build());
        Failed denied = (Failed) adapter.start(command);
        assertThat(denied.code()).isEqualTo("ACCESS_DENIED");
        assertThat(denied.message()).doesNotContain("arn:aws");
    }
}
