import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router";
import { z } from "zod";

import { safeNext } from "@/lib/utils/navigation";
import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { toApiError } from "@/lib/api/problem";
import { emailSchema } from "@/lib/validations/common";

import { useAuth } from "../auth-context";

const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Enter your password.").max(256),
});

type LoginValues = z.infer<typeof loginSchema>;

const REASON_MESSAGES: Record<string, string> = {
  expired: "Your session has expired. Sign in again to continue.",
  "password-changed": "Your password was changed. Sign in with the new password.",
  "password-reset": "Your password has been reset. Sign in with the new password.",
  registered: "Your account is ready. Sign in to get started.",
};

export function LoginPage() {
  useDocumentTitle("Sign in");
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [formError, setFormError] = useState<string | null>(null);
  const notice = REASON_MESSAGES[params.get("reason") ?? ""];

  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login(values);
      void navigate(safeNext(params), { replace: true });
    } catch (error) {
      const apiError = toApiError(error);
      if (apiError.kind === "rate-limited") {
        setFormError(
          apiError.retryAfter
            ? `Too many sign-in attempts. Try again in ${formatWait(apiError.retryAfter)}.`
            : apiError.message,
        );
      } else if (apiError.is("INVALID_CREDENTIALS")) {
        // Deliberately generic: the backend does not reveal whether the account exists or is locked.
        setFormError("Email or password is incorrect. Repeated failures temporarily lock the account.");
      } else {
        setFormError(apiError.message);
      }
      form.resetField("password");
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">
          <h1>Sign in</h1>
        </CardTitle>
        <CardDescription>Use your CloudForge account.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          {notice && !formError ? <FormAlert tone="success">{notice}</FormAlert> : null}
          <FormAlert>{formError}</FormAlert>
          <FormField label="Email" error={errors.email?.message}>
            <Input type="email" autoComplete="username" autoFocus {...form.register("email")} />
          </FormField>
          <FormField label="Password" error={errors.password?.message}>
            <Input type="password" autoComplete="current-password" {...form.register("password")} />
          </FormField>
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="animate-spin" aria-hidden /> : null}
            Sign in
          </Button>
        </form>
        <div className="mt-6 space-y-1 text-center text-xs text-muted-foreground">
          <p>Forgot your password? Ask a platform administrator for a reset link.</p>
          <p>
            Have an invitation?{" "}
            <Link to="/register" className="font-medium text-primary underline-offset-4 hover:underline">
              Create your account
            </Link>
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

function formatWait(seconds: number) {
  if (seconds < 60) return `${seconds} seconds`;
  const minutes = Math.ceil(seconds / 60);
  return minutes === 1 ? "1 minute" : `${minutes} minutes`;
}
