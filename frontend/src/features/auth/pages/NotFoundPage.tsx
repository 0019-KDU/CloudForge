import { Compass } from "lucide-react";
import { Link } from "react-router";

import { EmptyState } from "@/components/feedback/EmptyState";
import { Button } from "@/components/ui/button";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

export function NotFoundPage() {
  useDocumentTitle("Page not found");
  return (
    <EmptyState
      icon={Compass}
      title="Page not found"
      description="The address may be mistyped, or the page has moved."
      action={
        <Button asChild variant="outline" size="sm">
          <Link to="/">Go to overview</Link>
        </Button>
      }
      className="min-h-[50svh]"
    />
  );
}
