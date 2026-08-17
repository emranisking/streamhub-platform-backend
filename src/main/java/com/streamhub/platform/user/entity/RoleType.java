package com.streamhub.platform.user.entity;

/**
 * The three roles supported by the platform. Stored directly on
 * `users.role_type` as a string enum (no separate roles table / join
 * table - deliberately simple per product requirements).
 */
public enum RoleType {
    ADMIN,
    ANALYTIC,
    NORMAL_USER
}
