package com.test.ias_firebase.model;

/**
 * Permissions for RBAC (least privilege).
 * Each role is assigned a subset of these permissions.
 */
public enum Permission {
    /** Allow access to registration page and APIs (admin only). */
    REGISTER_USER,
    /** View dashboard and secure content. */
    VIEW_DASHBOARD,
    /** Setup and manage own TOTP. */
    MANAGE_OWN_TOTP,
    /** Assign roles to users (admin only). */
    MANAGE_USERS
}
