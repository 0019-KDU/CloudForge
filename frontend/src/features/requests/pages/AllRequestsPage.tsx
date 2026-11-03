import { FileStack } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { REQUEST_STATUSES } from "@/types/api";

import { RequestListView } from "../components/RequestListView";

/** Organization-wide request list for platform administrators and auditors (scope=ALL). */
export function AllRequestsPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="All requests" description="Every resource request across the organization." />
      <RequestListView
        caption="All requests"
        scope="ALL"
        statusOptions={[...REQUEST_STATUSES]}
        columns={["number", "resource", "type", "environment", "requester", "approver", "status", "created"]}
        empty={{ icon: FileStack, title: "No requests yet", description: "Requests appear here as soon as engineers create them." }}
      />
    </div>
  );
}
