package com.cloudforge.resourcecatalog.domain;

/** Target environment of a requested resource. Policies are defined per type and environment. */
public enum Environment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION
}
