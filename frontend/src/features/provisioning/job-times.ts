import type { ProvisioningResponse } from "@/types/api";

/** Started = first attempt; completed = last attempt once the job is final (the job has no such fields). */
export function jobTimes(job: ProvisioningResponse) {
  const first = job.attempts[0];
  const last = job.attempts[job.attempts.length - 1];
  const final = job.status === "SUCCEEDED" || job.status === "FAILED";
  return { startedAt: first?.startedAt ?? null, completedAt: final ? (last?.finishedAt ?? null) : null };
}
