package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    boolean existsById(String id);
}