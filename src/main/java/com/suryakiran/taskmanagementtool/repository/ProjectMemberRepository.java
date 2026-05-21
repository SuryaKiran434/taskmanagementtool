package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Project;
import com.suryakiran.taskmanagementtool.model.ProjectMember;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProject(Project project);

    Optional<ProjectMember> findByProjectAndUser(Project project, User user);

    boolean existsByProjectAndUser(Project project, User user);

    void deleteByProjectAndUser(Project project, User user);
}
