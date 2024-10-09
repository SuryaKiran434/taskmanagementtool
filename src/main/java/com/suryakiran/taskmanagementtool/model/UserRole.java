package com.suryakiran.taskmanagementtool.model;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "user_roles")
@IdClass(UserRoleId.class)
public class UserRole {
    @Id
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_user"))
    private User user;

    @Id
    @ManyToOne
    @JoinColumn(name = "role_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_role"))
    private Role role;
}