import {
  AlertTriangle,
  CheckCircle,
  CircleCheckBig,
  Clock,
  FileStack,
  Inbox,
  Loader,
  Plus,
  XCircle,
} from "lucide-react";
import { lazy, Suspense } from "react";
import { Link } from "react-router";

import { PageHeader } from "@/components/shared/PageHeader";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { DECIDED_STATUSES } from "@/features/approvals/statuses";
import { useCurrentUser } from "@/features/auth/auth-context";
import { useJobCount } from "@/features/provisioning/api";
import { useRequestCount } from "@/features/requests/queries";
import { canCreateRequests, canViewAllRequests, hasRole } from "@/lib/utils/permissions";

import { RecentRequests } from "./RecentRequests";
import { StatCard } from "./StatCard";

const StatusChart = lazy(() => import("./StatusChart"));

/** One section per role the user holds; every number is a live count from the API. */
export function OverviewPage() {
  const user = useCurrentUser();
  const firstName = user.displayName.split(/\s+/)[0] ?? user.displayName;
  const requester = canCreateRequests(user);

  return (
    <div className="space-y-8">
      <PageHeader
        title="Overview"
        description={`Welcome back, ${firstName}.`}
        actions={
          requester ? (
            <Button asChild>
              <Link to="/requests/new">
                <Plus aria-hidden /> New request
              </Link>
            </Button>
          ) : null
        }
      />
      {hasRole(user, "MANAGER") ? <ManagerSection /> : null}
      {requester ? <RequesterSection /> : null}
      {canViewAllRequests(user) ? <PlatformSection /> : null}
    </div>
  );
}

function SectionTitle({ children }: { children: string }) {
  return <h2 className="text-sm font-semibold">{children}</h2>;
}

function RequesterSection() {
  const total = useRequestCount({ scope: "MINE" });
  const pending = useRequestCount({ scope: "MINE", status: ["PENDING_APPROVAL"] });
  const approved = useRequestCount({ scope: "MINE", status: ["APPROVED"] });
  const provisioning = useRequestCount({ scope: "MINE", status: ["PROVISIONING"] });
  const provisioned = useRequestCount({ scope: "MINE", status: ["PROVISIONED"] });
  const failed = useRequestCount({ scope: "MINE", status: ["PROVISIONING_FAILED"] });

  // Each card counts exactly what its link lists.
  return (
    <section className="space-y-3" aria-label="Your requests">
      <SectionTitle>Your requests</SectionTitle>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
        <StatCard label="Total" query={total} icon={FileStack} to="/requests" />
        <StatCard label="Pending approval" query={pending} icon={Clock} tone="warning" to="/requests?status=PENDING_APPROVAL" />
        <StatCard label="Approved" query={approved} icon={CheckCircle} tone="info" to="/requests?status=APPROVED" hint="Queued for provisioning" />
        <StatCard label="Provisioning" query={provisioning} icon={Loader} tone="info" to="/requests?status=PROVISIONING" />
        <StatCard label="Provisioned" query={provisioned} icon={CircleCheckBig} tone="success" to="/requests?status=PROVISIONED" />
        <StatCard label="Provisioning failed" query={failed} icon={AlertTriangle} tone="danger" to="/requests?status=PROVISIONING_FAILED" />
      </div>
      <RecentRequests
        title="Recent requests"
        params={{ scope: "MINE", sort: "createdAt,desc" }}
        viewAll="/requests"
        empty={{
          icon: FileStack,
          title: "No requests yet",
          description: "Request an AWS account, a GCP project or a Kubernetes namespace to get started.",
          action: (
            <Button asChild size="sm">
              <Link to="/requests/new">New request</Link>
            </Button>
          ),
        }}
      />
    </section>
  );
}

function ManagerSection() {
  const pending = useRequestCount({ scope: "ASSIGNED", status: ["PENDING_APPROVAL"] });
  const approved = useRequestCount({ scope: "ASSIGNED", status: DECIDED_STATUSES.filter((s) => s !== "REJECTED") });
  const rejected = useRequestCount({ scope: "ASSIGNED", status: ["REJECTED"] });

  return (
    <section className="space-y-3" aria-label="Approvals">
      <SectionTitle>Approvals</SectionTitle>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-3">
        <StatCard label="Waiting for you" query={pending} icon={Inbox} tone="warning" to="/approvals" />
        <StatCard label="Approved" query={approved} icon={CheckCircle} tone="success" to="/approvals/history" />
        <StatCard label="Rejected" query={rejected} icon={XCircle} tone="danger" to="/approvals/history?status=REJECTED" />
      </div>
      <RecentRequests
        title="Oldest pending approvals"
        params={{ scope: "ASSIGNED", status: ["PENDING_APPROVAL"], sort: "submittedAt,asc" }}
        viewAll="/approvals"
        showRequester
        empty={{ icon: Inbox, title: "No pending approvals", description: "You're all caught up." }}
      />
    </section>
  );
}

function PlatformSection() {
  const pending = useRequestCount({ scope: "ALL", status: ["PENDING_APPROVAL"] });
  const provisioning = useRequestCount({ scope: "ALL", status: ["APPROVED", "PROVISIONING"] });
  const failedJobs = useJobCount(["FAILED"]);
  const provisioned = useRequestCount({ scope: "ALL", status: ["PROVISIONED"] });

  return (
    <section className="space-y-3" aria-label="Platform">
      <SectionTitle>Platform</SectionTitle>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Awaiting approval" query={pending} icon={Clock} tone="warning" to="/operations/requests?status=PENDING_APPROVAL" />
        <StatCard label="Provisioning" query={provisioning} icon={Loader} tone="info" to="/operations/provisioning?view=active" />
        <StatCard label="Failed jobs" query={failedJobs} icon={AlertTriangle} tone="danger" to="/operations/provisioning?view=failed" />
        <StatCard label="Provisioned resources" query={provisioned} icon={CircleCheckBig} tone="success" to="/inventory" />
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-semibold">
              <h3>Requests by status</h3>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Suspense fallback={<Skeleton className="h-72 w-full" />}>
              <StatusChart />
            </Suspense>
          </CardContent>
        </Card>
        <RecentRequests
          title="Latest requests"
          params={{ scope: "ALL", sort: "createdAt,desc" }}
          viewAll="/operations/requests"
          showRequester
          empty={{ icon: FileStack, title: "No requests yet", description: "Requests appear here as soon as engineers create them." }}
        />
      </div>
    </section>
  );
}
