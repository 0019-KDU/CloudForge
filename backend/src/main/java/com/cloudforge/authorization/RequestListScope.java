package com.cloudforge.authorization;

/** Which requests a list query covers. */
public enum RequestListScope {
    /** Requests the caller created. */
    MINE,
    /** Requests assigned to the caller for approval. */
    ASSIGNED,
    /** Every request; platform administrators and auditors only. */
    ALL
}
