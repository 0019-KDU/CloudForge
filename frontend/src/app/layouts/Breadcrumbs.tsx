import { Fragment } from "react";
import { Link, useMatches, type UIMatch } from "react-router";

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";

/** Routes declare `handle: { crumb: "Label" }` (or a function of the match) to appear here. */
export interface RouteHandle {
  crumb?: string | ((match: UIMatch) => string);
}

function crumbOf(match: UIMatch): string | null {
  const handle = match.handle as RouteHandle | undefined;
  if (!handle?.crumb) return null;
  return typeof handle.crumb === "function" ? handle.crumb(match) : handle.crumb;
}

export function Breadcrumbs() {
  const crumbs = useMatches()
    .map((match) => ({ match, label: crumbOf(match) }))
    .filter((entry): entry is { match: UIMatch; label: string } => entry.label !== null);

  if (crumbs.length === 0) return null;

  return (
    <Breadcrumb>
      <BreadcrumbList>
        {crumbs.map(({ match, label }, index) => {
          const last = index === crumbs.length - 1;
          return (
            <Fragment key={match.id}>
              {index > 0 ? <BreadcrumbSeparator className="hidden sm:block" /> : null}
              <BreadcrumbItem className={last ? undefined : "hidden sm:inline-flex"}>
                {last ? (
                  <BreadcrumbPage>{label}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink asChild>
                    <Link to={match.pathname}>{label}</Link>
                  </BreadcrumbLink>
                )}
              </BreadcrumbItem>
            </Fragment>
          );
        })}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
