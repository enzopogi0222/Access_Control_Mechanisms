package com.test.ias_firebase.model;

import java.util.Set;


/**
 * User roles for RBAC. Permissions enforce least privilege.
 */
public enum Role {
    admin,
    user;

    /** Permissions granted to this role. */
    public Set<Permission> getPermissions() {
        switch (this) {
            case admin:
                return Set.of(
                        Permission.REGISTER_USER,
                        Permission.VIEW_DASHBOARD,
                        Permission.MANAGE_OWN_TOTP,
                        Permission.MANAGE_USERS
                );
            case user:
                return Set.of(
                        Permission.VIEW_DASHBOARD,
                        Permission.MANAGE_OWN_TOTP
                );
            default:
                return Set.of();
        }
    }
}
