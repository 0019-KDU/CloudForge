import { z } from "zod";

/** Mirrors the backend PasswordPolicy length rules; the common-password blocklist stays server-side. */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_BYTES = 72;

const utf8 = new TextEncoder();

/** Counts Unicode code points, as the backend does (Java's codePointCount). */
function codePoints(value: string) {
  return Array.from(value).length;
}

export const newPasswordSchema = z
  .string()
  .refine((value) => codePoints(value) >= PASSWORD_MIN_LENGTH, {
    message: `Use at least ${PASSWORD_MIN_LENGTH} characters.`,
  })
  .refine((value) => utf8.encode(value).length <= PASSWORD_MAX_BYTES, {
    message: `Use at most ${PASSWORD_MAX_BYTES} bytes (about ${PASSWORD_MAX_BYTES} plain characters).`,
  });

export const displayNameSchema = z
  .string()
  .trim()
  .min(1, "Enter your name.")
  .max(100, "Use at most 100 characters.");

export const emailSchema = z.string().trim().min(1, "Enter your email address.").max(254).pipe(z.email("Enter a valid email address."));

/** Adds a "confirm password" check to a schema with a `password`-like field. */
export function withConfirmation<T extends z.ZodObject<{ newPassword: z.ZodType<string>; confirmPassword: z.ZodType<string> }>>(
  schema: T,
) {
  return schema.refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords don't match.",
  });
}
