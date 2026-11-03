import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { z } from "zod";

import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { applyServerErrors } from "@/lib/utils/forms";
import { newPasswordSchema, PASSWORD_MIN_LENGTH, withConfirmation } from "@/lib/validations/common";

import { authApi } from "../api";
import { useFragmentToken } from "../useFragmentToken";

const resetSchema = withConfirmation(
  z.object({
    token: z.string().trim().min(1, "Paste the reset code from your administrator.").max(128),
    newPassword: newPasswordSchema,
    confirmPassword: z.string(),
  }),
);

type ResetValues = z.infer<typeof resetSchema>;

export function ResetPasswordPage() {
  useDocumentTitle("Reset password");
  const navigate = useNavigate();
  const fragmentToken = useFragmentToken();
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<ResetValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { token: fragmentToken, newPassword: "", confirmPassword: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async ({ token, newPassword }) => {
    setFormError(null);
    try {
      await authApi.resetPassword({ token, newPassword });
      void navigate("/login?reason=password-reset", { replace: true });
    } catch (error) {
      setFormError(
        applyServerErrors(error, form.setError, {
          fields: ["token", "newPassword"],
          codeFields: { WEAK_PASSWORD: "newPassword" },
        }),
      );
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">
          <h1>Choose a new password</h1>
        </CardTitle>
        <CardDescription>
          Reset links come from a platform administrator and are valid for 24 hours. Resetting signs you out
          everywhere.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <FormAlert>{formError}</FormAlert>
          {fragmentToken ? null : (
            <FormField label="Reset code" error={errors.token?.message}>
              <Input autoComplete="off" spellCheck={false} className="font-mono" {...form.register("token")} />
            </FormField>
          )}
          <FormField
            label="New password"
            error={errors.newPassword?.message}
            hint={`At least ${PASSWORD_MIN_LENGTH} characters.`}
          >
            <Input type="password" autoComplete="new-password" autoFocus {...form.register("newPassword")} />
          </FormField>
          <FormField label="Confirm new password" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...form.register("confirmPassword")} />
          </FormField>
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="animate-spin" aria-hidden /> : null}
            Set new password
          </Button>
        </form>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            Back to sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
