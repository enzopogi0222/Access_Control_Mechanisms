package com.test.ias_firebase.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_clearance")
public class UserClearance {
    @Id
    private String uid;

    private String email;

    @Enumerated(EnumType.STRING)
    private SecurityLevel clearance = SecurityLevel.PUBLIC;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public SecurityLevel getClearance() {
        return clearance;
    }

    public void setClearance(SecurityLevel clearance) {
        this.clearance = (clearance != null) ? clearance : SecurityLevel.PUBLIC;
    }
}
