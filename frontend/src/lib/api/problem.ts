import { isAxiosError } from "axios";

import type { FieldViolation, ProblemDetail } from "@/types/api";

export type ApiErrorKind =
  | "validation" // 400/422 with field errors
  | "unauthenticated" // 401
  | "forbidden" // 403
  | "not-found" // 404
  | "conflict" // 409 / 412: changed by someone else
  | "business" // 422 business rule
  | "rate-limited" // 429
  | "server" // 5xx
  | "network" // no response
  | "unknown";

/**
 * Every failed API call is normalized to this error, so UI code never inspects Axios internals.
 * `message` is always safe to show: the backend guarantees problem `detail` is user-facing, and
 * anything unexpected falls back to a generic text.
 */
export class ApiError extends Error {
  readonly status: number | null;
  readonly code: string;
  readonly kind: ApiErrorKind;
  readonly fieldErrors: FieldViolation[];
  readonly correlationId: string | null;
  /** Seconds, from Retry-After on 429. */
  readonly retryAfter: number | null;

  constructor(init: {
    status: number | null;
    code: string;
    message: string;
    fieldErrors?: FieldViolation[];
    correlationId?: string | null;
    retryAfter?: number | null;
  }) {
    super(init.message);
    this.name = "ApiError";
    this.status = init.status;
    this.code = init.code;
    this.fieldErrors = init.fieldErrors ?? [];
    this.correlationId = init.correlationId ?? null;
    this.retryAfter = init.retryAfter ?? null;
    this.kind = classify(init.status, this.fieldErrors);
  }

  is(code: string): boolean {
    return this.code === code;
  }
}

const GENERIC_MESSAGES: Partial<Record<ApiErrorKind, string>> = {
  unauthenticated: "Your session has expired. Sign in again.",
  forbidden: "You don't have permission to perform this action.",
  "not-found": "This item doesn't exist or you don't have access to it.",
  conflict: "This item was changed by someone else. Reload it and try again.",
  "rate-limited": "Too many attempts. Wait a moment and try again.",
  server: "Something went wrong on our side. Try again in a moment.",
  network: "Can't reach CloudForge. Check your connection and try again.",
  unknown: "Something went wrong. Try again.",
};

function classify(status: number | null, fieldErrors: FieldViolation[]): ApiErrorKind {
  if (status === null) return "network";
  if ((status === 400 || status === 422) && fieldErrors.length > 0) return "validation";
  if (status === 400) return "validation";
  if (status === 401) return "unauthenticated";
  if (status === 403) return "forbidden";
  if (status === 404) return "not-found";
  if (status === 409 || status === 412) return "conflict";
  if (status === 422) return "business";
  if (status === 429) return "rate-limited";
  if (status >= 500) return "server";
  return "unknown";
}

function isProblem(body: unknown): body is ProblemDetail {
  return typeof body === "object" && body !== null && "code" in body && typeof body.code === "string";
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;

  if (isAxiosError(error)) {
    const response = error.response;
    if (!response) {
      return new ApiError({ status: null, code: "NETWORK_ERROR", message: GENERIC_MESSAGES.network ?? "" });
    }
    const headerId: unknown = response.headers["x-correlation-id"];
    const correlationHeader = typeof headerId === "string" ? headerId : null;
    const retryHeader: unknown = response.headers["retry-after"];
    const retryAfter = typeof retryHeader === "string" && /^\d+$/.test(retryHeader) ? Number(retryHeader) : null;
    const body: unknown = response.data;

    if (isProblem(body)) {
      const status = body.status || response.status;
      const kind = classify(status, body.errors ?? []);
      // 5xx detail is generic by backend design; still prefer our wording for consistency.
      const message = kind === "server" || !body.detail ? (GENERIC_MESSAGES[kind] ?? "") : body.detail;
      return new ApiError({
        status,
        code: body.code,
        message,
        fieldErrors: body.errors ?? [],
        correlationId: body.correlationId ?? correlationHeader,
        retryAfter,
      });
    }

    const kind = classify(response.status, []);
    return new ApiError({
      status: response.status,
      code: `HTTP_${response.status}`,
      message: GENERIC_MESSAGES[kind] ?? GENERIC_MESSAGES.unknown ?? "",
      correlationId: correlationHeader,
      retryAfter,
    });
  }

  return new ApiError({ status: null, code: "CLIENT_ERROR", message: GENERIC_MESSAGES.unknown ?? "" });
}

/** For places that only need text, such as toasts. */
export function errorMessage(error: unknown): string {
  return toApiError(error).message;
}
