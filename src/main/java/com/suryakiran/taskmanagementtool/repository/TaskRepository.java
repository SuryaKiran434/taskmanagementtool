package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    boolean existsById(String id);

    Page<Task> findByStatus(Status status, Pageable pageable);
    Page<Task> findByPriority(Priority priority, Pageable pageable);
    Page<Task> findByStatusAndPriority(Status status, Priority priority, Pageable pageable);
}