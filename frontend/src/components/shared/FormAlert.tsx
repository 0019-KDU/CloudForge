import { AlertCircle, CheckCircle2 } from "lucide-react";
import type { ReactNode } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";

export function FormAlert({ children, tone = "error" }: { children: ReactNode; tone?: "error" | "success" }) {
  if (!children) return null;
  return (
    <Alert
      variant={tone === "error" ? "destructive" : "default"}
      className={tone === "success" ? "border-success/30 bg-success-subtle text-success" : undefined}
      role={tone === "error" ? "alert" : "status"}
    >
      {tone === "error" ? <AlertCircle aria-hidden /> : <CheckCircle2 aria-hidden />}
      <AlertDescription className={tone === "success" ? "text-success" : undefined}>{children}</AlertDescription>
    </Alert>
  );
}
