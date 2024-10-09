package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.UserRole;
import com.suryakiran.taskmanagementtool.model.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}