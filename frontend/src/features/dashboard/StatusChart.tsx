import { useQueries } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { Skeleton } from "@/components/ui/skeleton";
import { requestsApi } from "@/features/requests/api";
import { requestKeys } from "@/features/requests/queries";
import { statusLabel } from "@/lib/utils/labels";
import { REQUEST_STATUSES } from "@/types/api";

/**
 * Organization-wide requests per status: one series (magnitude), so one hue and no legend; status
 * colours stay reserved for status badges. One count query per status. Lazy-loaded with Recharts.
 */
export default function StatusChart() {
  const results = useQueries({
    queries: REQUEST_STATUSES.map((status) => {
      const params = { scope: "ALL" as const, status: [status], page: 0, size: 1 };
      return {
        queryKey: requestKeys.count(params),
        queryFn: () => requestsApi.list(params),
        select: (page: { totalElements: number }) => page.totalElements,
      };
    }),
  });

  if (results.some((result) => result.isLoading)) return <Skeleton className="h-72 w-full" />;
  if (results.some((result) => result.error)) {
    return <p className="py-10 text-center text-sm text-muted-foreground">Status breakdown is unavailable right now.</p>;
  }

  const data = REQUEST_STATUSES.map((status, index) => ({ label: statusLabel(status), count: results[index]?.data ?? 0 }));
  if (data.every((item) => item.count === 0)) {
    return <p className="py-10 text-center text-sm text-muted-foreground">No requests yet.</p>;
  }

  return (
    <figure className="space-y-3">
      <div className="h-72" aria-hidden>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ left: 0, right: 32, top: 0, bottom: 0 }} barCategoryGap={6}>
            <CartesianGrid horizontal={false} stroke="var(--border)" strokeDasharray="0" />
            <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11, fill: "var(--muted-foreground)" }} axisLine={false} tickLine={false} />
            <YAxis
              type="category"
              dataKey="label"
              width={128}
              tick={{ fontSize: 12, fill: "var(--muted-foreground)" }}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              cursor={{ fill: "var(--accent)" }}
              formatter={(value) => [value, "Requests"]}
              contentStyle={{
                background: "var(--popover)",
                border: "1px solid var(--border)",
                borderRadius: 8,
                fontSize: 12,
                color: "var(--popover-foreground)",
              }}
            />
            <Bar dataKey="count" fill="var(--chart-1)" radius={[0, 4, 4, 0]} maxBarSize={14} isAnimationActive={false}>
              <LabelList dataKey="count" position="right" style={{ fontSize: 11, fill: "var(--foreground)" }} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <figcaption>
        <details className="text-xs text-muted-foreground">
          <summary className="cursor-pointer select-none hover:text-foreground">View as table</summary>
          <table className="mt-2 w-full max-w-xs text-sm text-foreground">
            <caption className="sr-only">Requests by status</caption>
            <thead>
              <tr className="text-left text-xs text-muted-foreground">
                <th className="py-1 font-medium">Status</th>
                <th className="py-1 text-right font-medium">Requests</th>
              </tr>
            </thead>
            <tbody>
              {data.map((item) => (
                <tr key={item.label} className="border-t">
                  <td className="py-1">{item.label}</td>
                  <td className="py-1 text-right tabular-nums">{item.count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      </figcaption>
    </figure>
  );
}
