import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { toApiError } from "@/lib/api/problem";

interface ServerErrorOptions {
  /** Form field paths that can display an error. */
  fields: readonly string[];
  /** Backend field paths that differ from form paths, e.g. `{ password: "newPassword" }`. */
  fieldMap?: Record<string, string>;
  /** Business error codes that belong to one field, e.g. `{ WEAK_PASSWORD: "newPassword" }`. */
  codeFields?: Record<string, string>;
}

/**
 * Puts the backend's field violations (`errors[].field`, e.g. `spec.namespace`) on the matching form
 * fields. Returns a message for the form-level alert when something could not be attached to a field
 * (or the error is not a field error at all), otherwise null.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  { fields, fieldMap = {}, codeFields = {} }: ServerErrorOptions,
): string | null {
  const apiError = toApiError(error);

  const codeField = codeFields[apiError.code];
  if (codeField && fields.includes(codeField)) {
    setError(codeField as Path<T>, { type: "server", message: apiError.message });
    return null;
  }
  if (apiError.fieldErrors.length === 0) return apiError.message;

  const unmatched: string[] = [];
  for (const violation of apiError.fieldErrors) {
    const field = fieldMap[violation.field] ?? violation.field;
    if (fields.includes(field)) {
      setError(field as Path<T>, { type: "server", message: capitalize(violation.message) });
    } else {
      unmatched.push(`${violation.field} ${violation.message}`);
    }
  }
  return unmatched.length > 0 ? unmatched.join(". ") : null;
}

function capitalize(text: string) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}
