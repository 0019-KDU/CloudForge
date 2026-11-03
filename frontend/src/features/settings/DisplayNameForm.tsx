import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { authApi } from "@/features/auth/api";
import { useAuth, useCurrentUser } from "@/features/auth/auth-context";
import { applyServerErrors } from "@/lib/utils/forms";
import { displayNameSchema } from "@/lib/validations/common";

const schema = z.object({ displayName: displayNameSchema });
type Values = z.infer<typeof schema>;

export function DisplayNameForm() {
  const user = useCurrentUser();
  const { setUser } = useAuth();
  const [formError, setFormError] = useState<string | null>(null);
  const form = useForm<Values>({ resolver: zodResolver(schema), values: { displayName: user.displayName } });
  const { errors, isSubmitting, isDirty } = form.formState;

  const onSubmit = form.handleSubmit(async ({ displayName }) => {
    setFormError(null);
    try {
      setUser(await authApi.updateProfile(displayName));
      toast.success("Name updated");
    } catch (error) {
      setFormError(applyServerErrors(error, form.setError, { fields: ["displayName"] }));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-3 border-t pt-5">
      <FormAlert>{formError}</FormAlert>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <FormField label="Display name" error={errors.displayName?.message} className="flex-1">
          <Input autoComplete="name" {...form.register("displayName")} />
        </FormField>
        <Button type="submit" variant="outline" className="sm:mt-6" disabled={!isDirty || isSubmitting}>
          {isSubmitting ? <Loader2 className="animate-spin" aria-hidden /> : null}
          Save
        </Button>
      </div>
    </form>
  );
}
