package com.suryakiran.taskmanagementtool.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Setter
@Getter
public class UserRoleId implements Serializable {
    // Getters and setters
    private int user;
    private Long role;

    // Default constructor
    public UserRoleId() {}

    // Parameterized constructor
    public UserRoleId(int user, Long role) {
        this.user = user;
        this.role = role;
    }

    // Override equals and hashCode methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleId that = (UserRoleId) o;
        return user == that.user && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, role);
    }
}