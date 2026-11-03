import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { authApi } from "@/features/auth/api";
import { useAuth } from "@/features/auth/auth-context";
import { toApiError } from "@/lib/api/problem";
import { applyServerErrors } from "@/lib/utils/forms";
import { newPasswordSchema, PASSWORD_MIN_LENGTH, withConfirmation } from "@/lib/validations/common";

const schema = withConfirmation(
  z.object({
    currentPassword: z.string().min(1, "Enter your current password.").max(256),
    newPassword: newPasswordSchema,
    confirmPassword: z.string(),
  }),
);
type Values = z.infer<typeof schema>;

export function ChangePasswordForm() {
  const { endSession } = useAuth();
  const [formError, setFormError] = useState<string | null>(null);
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });
  const { errors, isSubmitting } = form.formState;

  const onSubmit = form.handleSubmit(async ({ currentPassword, newPassword }) => {
    setFormError(null);
    try {
      await authApi.changePassword({ currentPassword, newPassword });
      // The backend revoked every session, including this one. The route guard then sends the user
      // to sign in, with this reason and a link back here.
      endSession("password-changed");
    } catch (error) {
      const apiError = toApiError(error);
      setFormError(
        apiError.kind === "rate-limited"
          ? "Too many attempts. Wait a few minutes and try again."
          : applyServerErrors(error, form.setError, {
              fields: ["currentPassword", "newPassword"],
              codeFields: {
                INVALID_CURRENT_PASSWORD: "currentPassword",
                PASSWORD_UNCHANGED: "newPassword",
                WEAK_PASSWORD: "newPassword",
              },
            }),
      );
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="max-w-md space-y-4">
      <FormAlert>{formError}</FormAlert>
      <FormField label="Current password" error={errors.currentPassword?.message}>
        <Input type="password" autoComplete="current-password" {...form.register("currentPassword")} />
      </FormField>
      <FormField
        label="New password"
        error={errors.newPassword?.message}
        hint={`At least ${PASSWORD_MIN_LENGTH} characters.`}
      >
        <Input type="password" autoComplete="new-password" {...form.register("newPassword")} />
      </FormField>
      <FormField label="Confirm new password" error={errors.confirmPassword?.message}>
        <Input type="password" autoComplete="new-password" {...form.register("confirmPassword")} />
      </FormField>
      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? <Loader2 className="animate-spin" aria-hidden /> : null}
        Change password
      </Button>
    </form>
  );
}
