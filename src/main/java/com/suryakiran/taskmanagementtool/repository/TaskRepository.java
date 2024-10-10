package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {
    boolean existsById(String id);

    @Query("SELECT t FROM Task t WHERE (:status IS NULL OR t.status = :status) OR (:priority IS NULL OR t.priority = :priority)")
    Page<Task> findByStatusOrPriority(@Param("status") Status status, @Param("priority") Priority priority, Pageable pageable);
}