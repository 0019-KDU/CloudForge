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
import { displayNameSchema, newPasswordSchema, PASSWORD_MIN_LENGTH, withConfirmation } from "@/lib/validations/common";

import { authApi } from "../api";
import { useFragmentToken } from "../useFragmentToken";

const registerSchema = withConfirmation(
  z.object({
    invitationToken: z.string().trim().min(1, "Paste the invitation code you received.").max(128),
    displayName: displayNameSchema,
    newPassword: newPasswordSchema,
    confirmPassword: z.string(),
  }),
);

type RegisterValues = z.infer<typeof registerSchema>;

export function RegisterPage() {
  useDocumentTitle("Create account");
  const navigate = useNavigate();
  const fragmentToken = useFragmentToken();
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { invitationToken: fragmentToken, displayName: "", newPassword: "", confirmPassword: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async (values) => {
    setFormError(null);
    try {
      await authApi.register({
        invitationToken: values.invitationToken,
        displayName: values.displayName,
        password: values.newPassword,
      });
      void navigate("/login?reason=registered", { replace: true });
    } catch (error) {
      setFormError(
        applyServerErrors(error, form.setError, {
          fields: ["invitationToken", "displayName", "newPassword"],
          fieldMap: { password: "newPassword" },
          codeFields: { WEAK_PASSWORD: "newPassword" },
        }),
      );
    }
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">
          <h1>Create your account</h1>
        </CardTitle>
        <CardDescription>
          Accounts are invitation-only. Your email, roles and team come from the invitation.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <FormAlert>{formError}</FormAlert>
          {fragmentToken ? null : (
            <FormField label="Invitation code" error={errors.invitationToken?.message}>
              <Input autoComplete="off" spellCheck={false} className="font-mono" {...form.register("invitationToken")} />
            </FormField>
          )}
          <FormField label="Your name" error={errors.displayName?.message}>
            <Input autoComplete="name" autoFocus {...form.register("displayName")} />
          </FormField>
          <FormField
            label="Password"
            error={errors.newPassword?.message}
            hint={`At least ${PASSWORD_MIN_LENGTH} characters. A passphrase works well.`}
          >
            <Input type="password" autoComplete="new-password" {...form.register("newPassword")} />
          </FormField>
          <FormField label="Confirm password" error={errors.confirmPassword?.message}>
            <Input type="password" autoComplete="new-password" {...form.register("confirmPassword")} />
          </FormField>
          {fragmentToken && errors.invitationToken ? <FormAlert>{errors.invitationToken.message}</FormAlert> : null}
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            {isSubmitting ? <Loader2 className="animate-spin" aria-hidden /> : null}
            Create account
          </Button>
        </form>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-primary underline-offset-4 hover:underline">
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}
