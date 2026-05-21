package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Subtask;
import com.suryakiran.taskmanagementtool.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubtaskRepository extends JpaRepository<Subtask, Long> {

    List<Subtask> findByTaskOrderByPositionAsc(Task task);

    Optional<Subtask> findByIdAndTask(Long id, Task task);

    @Query("SELECT COUNT(s) FROM Subtask s WHERE s.task = :task")
    int countByTask(@Param("task") Task task);

    @Query("SELECT COUNT(s) FROM Subtask s WHERE s.task = :task AND s.completed = true")
    int countCompletedByTask(@Param("task") Task task);

    void deleteByTask(Task task);
}
