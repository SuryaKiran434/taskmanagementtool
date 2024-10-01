package com.suryakiran.taskmanagementtool.Repository;

import com.suryakiran.taskmanagementtool.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @Query("SELECT t FROM Task t WHERE t.name = :name")
    List<Task> findTasksByName(@Param("name") String name);
}