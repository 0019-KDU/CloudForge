import { Lock } from "lucide-react";
import { Link } from "react-router";

import { EmptyState } from "@/components/feedback/EmptyState";
import { Button } from "@/components/ui/button";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

export function UnauthorizedPage() {
  useDocumentTitle("Access denied");
  return (
    <EmptyState
      icon={Lock}
      title="You don't have access to this page"
      description="Your roles don't include this area of CloudForge. If you need access, ask a platform administrator."
      action={
        <Button asChild variant="outline" size="sm">
          <Link to="/">Go to overview</Link>
        </Button>
      }
      className="min-h-[50svh]"
    />
  );
}
