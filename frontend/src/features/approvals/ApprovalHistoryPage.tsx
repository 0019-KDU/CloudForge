import { History } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { RequestListView } from "@/features/requests/components/RequestListView";

import { DECIDED_STATUSES } from "./statuses";

export function ApprovalHistoryPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Approval history" description="Requests you've approved or rejected, and what happened next." />
      <RequestListView
        caption="Approval history"
        scope="ASSIGNED"
        baseStatuses={DECIDED_STATUSES}
        statusOptions={DECIDED_STATUSES}
        defaultSort={{ field: "submittedAt", direction: "desc" }}
        columns={["number", "requester", "resource", "type", "environment", "status", "submitted"]}
        empty={{
          icon: History,
          title: "No decisions yet",
          description: "Requests you approve or reject will be listed here.",
        }}
      />
    </div>
  );
}
