import { Boxes } from "lucide-react";

import { PageHeader } from "@/components/shared/PageHeader";
import { useCurrentUser } from "@/features/auth/auth-context";
import { RequestListView } from "@/features/requests/components/RequestListView";
import { canViewAllRequests } from "@/lib/utils/permissions";

/** Provisioned resources: the caller's own, or the whole organization's for admins and auditors. */
export function InventoryPage() {
  const user = useCurrentUser();
  const all = canViewAllRequests(user);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Resource inventory"
        description={
          all
            ? "Every resource CloudForge has provisioned. Open one for its provider ID and details."
            : "Resources provisioned for you. Open one for its provider ID and details."
        }
      />
      <RequestListView
        caption="Provisioned resources"
        scope={all ? "ALL" : "MINE"}
        baseStatuses={["PROVISIONED"]}
        columns={all ? ["number", "resource", "type", "environment", "requester", "updated"] : ["number", "resource", "type", "environment", "updated"]}
        empty={{
          icon: Boxes,
          title: "No provisioned resources yet",
          description: "Resources appear here once an approved request has been provisioned.",
        }}
      />
    </div>
  );
}
