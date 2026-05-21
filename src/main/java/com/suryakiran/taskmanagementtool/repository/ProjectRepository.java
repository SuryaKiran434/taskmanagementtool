package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Project;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwner(User owner);

    @Query("SELECT p FROM Project p JOIN p.members m WHERE m.user = :user")
    List<Project> findProjectsByMember(@Param("user") User user);

    @Query("SELECT p FROM Project p WHERE p.owner = :user OR EXISTS (SELECT m FROM ProjectMember m WHERE m.project = p AND m.user = :user)")
    List<Project> findAllAccessibleByUser(@Param("user") User user);
}
