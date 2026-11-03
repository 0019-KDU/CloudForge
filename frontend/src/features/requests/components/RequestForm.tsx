import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useMemo, useState } from "react";
import { Controller, useForm, useWatch, type Resolver } from "react-hook-form";
import { z } from "zod";

import { FormAlert } from "@/components/shared/FormAlert";
import { FormField } from "@/components/shared/FormField";
import { JsonObjectList } from "@/components/shared/KeyValueList";
import { ResourceTypeLabel } from "@/components/shared/ProviderIcon";
import { EnvironmentBadge } from "@/components/shared/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { catalogApi } from "@/features/resources/api";
import { toApiError } from "@/lib/api/problem";
import { applyServerErrors } from "@/lib/utils/forms";
import { environmentLabel } from "@/lib/utils/labels";
import type { Environment, ResourceTypeResponse } from "@/types/api";

import type { RequestInput } from "../api";
import { justificationSchema, specFields, specSchema, SPEC_LABELS, toSpec, type SpecValues } from "../spec";
import { StepIndicator } from "./StepIndicator";

export interface RequestFormValues {
  environment: Environment | "";
  spec: SpecValues;
  justification: string;
}

export type RequestFormAction = "draft" | "submit";

interface RequestFormProps {
  type: ResourceTypeResponse;
  initialValues: RequestFormValues;
  /** Steps shown before "Configuration" (the new-request flow adds "Resource type"). */
  leadingSteps?: string[];
  onBackToLeading?: () => void;
  /** Resolves when done; rejects with the API error so the form can show it. */
  onSave: (action: RequestFormAction, input: RequestInput) => Promise<void>;
  saveDraftLabel: string;
}

const STEPS = ["Configuration", "Justification", "Review"];

export function RequestForm({ type, initialValues, leadingSteps = [], onBackToLeading, onSave, saveDraftLabel }: RequestFormProps) {
  const [step, setStep] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [pendingAction, setPendingAction] = useState<RequestFormAction | null>(null);

  const policyFor = (environment: string) => type.environments.find((p) => p.environment === environment);

  const resolver = useMemo<Resolver<RequestFormValues>>(
    () => (values, context, options) => {
      const allowed: readonly string[] = type.allowedEnvironments;
      const schema = z.object({
        environment: z
          .string()
          .refine((value) => allowed.includes(value), {
            message: allowed.length > 0 ? "Choose an environment." : "This resource type can't be requested in any environment right now.",
          }),
        spec: specSchema(type.code, policyFor(values.environment)),
        justification: justificationSchema,
      });
      // The schema is built per call; its inferred field names are wider than the form type.
      return zodResolver(schema)(values, context, options as never) as ReturnType<Resolver<RequestFormValues>>;
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps -- policyFor only reads `type`
    [type],
  );

  const form = useForm<RequestFormValues>({ resolver, defaultValues: initialValues, mode: "onTouched" });
  const { errors } = form.formState;
  const environment = useWatch({ control: form.control, name: "environment" });
  const policy = environment ? policyFor(environment) : undefined;
  const fields = specFields(type.code, policy);
  const specPaths = fields.map((field) => `spec.${field.name}` as const);

  const goToJustification = async () => {
    setFormError(null);
    const valid = await form.trigger(["environment", ...specPaths]);
    if (!valid || !environment) return;
    // The server holds the authoritative rules (policy constraints, reserved names); ask it now so
    // problems surface on this step rather than at submission.
    setChecking(true);
    try {
      await catalogApi.validateSpec(type.code, environment, toSpec(type.code, form.getValues("spec")));
      setStep(1);
    } catch (error) {
      setFormError(applyServerErrors(error, form.setError, { fields: ["environment", ...specPaths] }));
    } finally {
      setChecking(false);
    }
  };

  const goToReview = async () => {
    if (await form.trigger("justification")) setStep(2);
  };

  const save = (action: RequestFormAction) =>
    form.handleSubmit(async (values) => {
      setFormError(null);
      setPendingAction(action);
      try {
        await onSave(action, {
          environment: values.environment as Environment,
          spec: toSpec(type.code, values.spec),
          justification: values.justification.trim(),
        });
      } catch (error) {
        const message = applyServerErrors(error, form.setError, {
          fields: ["environment", "justification", ...specPaths],
        });
        // Field errors belong to earlier steps: go back to where they can be fixed.
        const apiError = toApiError(error);
        if (apiError.fieldErrors.some((e) => e.field.startsWith("spec") || e.field === "environment")) setStep(0);
        else if (apiError.fieldErrors.some((e) => e.field === "justification")) setStep(1);
        setFormError(message);
      } finally {
        setPendingAction(null);
      }
    })();

  const allSteps = [...leadingSteps, ...STEPS];
  const busy = pendingAction !== null;

  return (
    <div className="space-y-6">
      <StepIndicator
        steps={allSteps}
        current={leadingSteps.length + step}
        onStepClick={(index) => {
          if (index < leadingSteps.length) onBackToLeading?.();
          else setStep(index - leadingSteps.length);
        }}
      />

      <Card>
        <CardHeader className="border-b">
          <div className="flex items-center justify-between gap-4">
            <div>
              <CardTitle className="text-base">{STEPS[step]}</CardTitle>
              <CardDescription>
                {step === 0 && "Where the resource runs and how it is configured."}
                {step === 1 && "Your approver reads this to decide. Be specific about the need."}
                {step === 2 && "Check everything before sending it to your manager."}
              </CardDescription>
            </div>
            <ResourceTypeLabel code={type.code} withProvider />
          </div>
        </CardHeader>

        <CardContent className="space-y-5 pt-6">
          <FormAlert>{formError}</FormAlert>

          <div hidden={step !== 0} className="space-y-5">
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">Environment</legend>
              <Controller
                control={form.control}
                name="environment"
                render={({ field }) => (
                  <RadioGroup
                    value={field.value}
                    onValueChange={(value) => {
                      field.onChange(value);
                      // Limits depend on the environment's policy: re-check touched spec fields.
                      void form.trigger(specPaths.filter((path) => form.getFieldState(path).isTouched));
                    }}
                    className="grid gap-2 sm:grid-cols-3"
                    aria-invalid={errors.environment ? true : undefined}
                  >
                    {type.allowedEnvironments.map((env) => (
                      <Label
                        key={env}
                        htmlFor={`env-${env}`}
                        className="flex cursor-pointer items-center gap-3 rounded-md border p-3 font-normal has-data-[state=checked]:border-primary has-data-[state=checked]:bg-primary/5"
                      >
                        <RadioGroupItem value={env} id={`env-${env}`} />
                        <span>{environmentLabel(env)}</span>
                      </Label>
                    ))}
                  </RadioGroup>
                )}
              />
              {errors.environment ? <p className="text-xs font-medium text-destructive">{errors.environment.message}</p> : null}
              {policy?.maxActivePerUser ? (
                <p className="text-xs text-muted-foreground">
                  You can have at most {policy.maxActivePerUser} active {type.displayName} requests in{" "}
                  {environmentLabel(policy.environment).toLowerCase()}.
                </p>
              ) : null}
            </fieldset>

            {environment ? (
              <div className="grid gap-4 sm:grid-cols-2">
                {fields.map((field) => {
                  const error = errors.spec?.[field.name]?.message;
                  const hint = field.max !== undefined ? `${field.hint ?? ""} Maximum ${field.max} ${field.unit ?? ""}.`.trim() : field.hint;
                  return (
                    <FormField key={field.name} label={field.label} error={error} hint={hint} optional={field.optional ?? false}>
                      {field.options ? (
                        <Controller
                          control={form.control}
                          name={`spec.${field.name}`}
                          render={({ field: control }) => (
                            <Select value={control.value} onValueChange={control.onChange} disabled={field.options?.length === 0}>
                              <SelectTrigger className="w-full font-mono" onBlur={control.onBlur}>
                                <SelectValue placeholder={field.options?.length ? "Choose…" : "None approved for this environment"} />
                              </SelectTrigger>
                              <SelectContent>
                                {field.options?.map((option) => (
                                  <SelectItem key={option} value={option} className="font-mono">
                                    {option}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          )}
                        />
                      ) : (
                        <Input
                          inputMode={field.kind === "number" ? "decimal" : undefined}
                          placeholder={field.placeholder}
                          autoComplete="off"
                          spellCheck={false}
                          className={field.monospace ? "font-mono" : undefined}
                          {...form.register(`spec.${field.name}`)}
                        />
                      )}
                    </FormField>
                  );
                })}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">Choose an environment to configure the resource.</p>
            )}
          </div>

          <div hidden={step !== 1}>
            <FormField
              label="Business justification"
              error={errors.justification?.message}
              hint="What it's for, who uses it and for how long. 10–2000 characters."
            >
              <Textarea rows={7} maxLength={2000} {...form.register("justification")} />
            </FormField>
          </div>

          {step === 2 ? <ReviewStep type={type} values={form.getValues()} /> : null}
        </CardContent>

        <CardFooter className="flex flex-col-reverse gap-2 border-t pt-6 sm:flex-row sm:justify-between">
          <div>
            {step > 0 ? (
              <Button variant="ghost" onClick={() => setStep(step - 1)} disabled={busy}>
                Back
              </Button>
            ) : onBackToLeading ? (
              <Button variant="ghost" onClick={onBackToLeading}>
                Change resource type
              </Button>
            ) : null}
          </div>
          <div className="flex flex-col-reverse gap-2 sm:flex-row">
            {step === 2 ? (
              <>
                <Button variant="outline" onClick={() => void save("draft")} disabled={busy}>
                  {pendingAction === "draft" ? <Loader2 className="animate-spin" aria-hidden /> : null}
                  {saveDraftLabel}
                </Button>
                <Button onClick={() => void save("submit")} disabled={busy}>
                  {pendingAction === "submit" ? <Loader2 className="animate-spin" aria-hidden /> : null}
                  Submit for approval
                </Button>
              </>
            ) : (
              <Button onClick={() => void (step === 0 ? goToJustification() : goToReview())} disabled={checking}>
                {checking ? <Loader2 className="animate-spin" aria-hidden /> : null}
                Continue
              </Button>
            )}
          </div>
        </CardFooter>
      </Card>
    </div>
  );
}

function ReviewStep({ type, values }: { type: ResourceTypeResponse; values: RequestFormValues }) {
  const spec = toSpec(type.code, values.spec);
  return (
    <div className="space-y-6">
      <section className="space-y-3">
        <h3 className="text-sm font-medium">Resource</h3>
        <div className="flex flex-wrap items-center gap-3">
          <ResourceTypeLabel code={type.code} withProvider />
          {values.environment ? <EnvironmentBadge environment={values.environment} /> : null}
        </div>
        <JsonObjectList value={spec} labels={SPEC_LABELS} />
      </section>
      <section className="space-y-2 border-t pt-5">
        <h3 className="text-sm font-medium">Justification</h3>
        <p className="text-sm whitespace-pre-wrap">{values.justification.trim()}</p>
      </section>
      <p className="rounded-md bg-muted px-3 py-2 text-xs text-muted-foreground">
        Submitting sends the request to your manager for approval. After submission it can be cancelled but no longer
        edited. Approved requests are provisioned automatically.
      </p>
    </div>
  );
}
