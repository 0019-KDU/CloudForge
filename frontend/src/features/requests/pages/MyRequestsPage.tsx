import { FileStack, Plus } from "lucide-react";
import { Link } from "react-router";

import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { REQUEST_STATUSES } from "@/types/api";

import { RequestListView } from "../components/RequestListView";

export function MyRequestsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="My requests"
        description="Everything you've requested, from drafts to provisioned resources."
        actions={
          <Button asChild>
            <Link to="/requests/new">
              <Plus aria-hidden /> New request
            </Link>
          </Button>
        }
      />
      <RequestListView
        caption="My requests"
        scope="MINE"
        columns={["number", "resource", "type", "environment", "approver", "status", "created"]}
        statusOptions={[...REQUEST_STATUSES]}
        empty={{
          icon: FileStack,
          title: "No requests yet",
          description: "Request an AWS account, a GCP project or a Kubernetes namespace. Your manager approves it, then it's provisioned automatically.",
          action: (
            <Button asChild size="sm">
              <Link to="/requests/new">
                <Plus aria-hidden /> New request
              </Link>
            </Button>
          ),
        }}
      />
    </div>
  );
}
