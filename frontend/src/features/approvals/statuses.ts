import type { RequestStatus } from "@/types/api";

/** Everything a manager has decided; approved requests continue into the provisioning statuses. */
export const DECIDED_STATUSES: RequestStatus[] = ["APPROVED", "REJECTED", "PROVISIONING", "PROVISIONED", "PROVISIONING_FAILED"];
