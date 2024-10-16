package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
}