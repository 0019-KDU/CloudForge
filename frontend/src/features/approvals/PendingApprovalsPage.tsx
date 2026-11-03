import { Inbox } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { RequestListView } from "@/features/requests/components/RequestListView";

export function PendingApprovalsPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Pending approvals" description="Requests from your reports waiting for your decision, oldest first." />
      <RequestListView
        caption="Pending approvals"
        scope="ASSIGNED"
        baseStatuses={["PENDING_APPROVAL"]}
        defaultSort={{ field: "submittedAt", direction: "asc" }}
        columns={["number", "requester", "type", "environment", "justification", "submitted", "status"]}
        empty={{
          icon: Inbox,
          title: "No pending approvals",
          description: "You're all caught up. New requests from your reports will appear here, and you'll get a notification.",
        }}
      />
    </div>
  );
}
