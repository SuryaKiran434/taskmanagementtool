package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    boolean existsById(String id);

    // Global filter (admin use) — AND logic, excludes soft-deleted
    @Query("SELECT t FROM Task t WHERE t.deletedAt IS NULL AND (:status IS NULL OR t.status = :status) AND (:priority IS NULL OR t.priority = :priority)")
    Page<Task> findByStatusAndPriority(@Param("status") Status status, @Param("priority") Priority priority, Pageable pageable);

    // User-scoped queries — all exclude soft-deleted tasks
    @Query("SELECT t FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL")
    Page<Task> findByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.user = :user AND t.deletedAt IS NULL")
    Optional<Task> findByIdAndUser(@Param("id") String id, @Param("user") User user);

    @Query("SELECT t FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL AND (:status IS NULL OR t.status = :status) AND (:priority IS NULL OR t.priority = :priority)")
    Page<Task> findByUserAndStatusAndPriority(@Param("user") User user, @Param("status") Status status, @Param("priority") Priority priority, Pageable pageable);

    boolean existsByIdAndUserId(String id, int userId);

    // Search by title or description (user-scoped, excludes soft-deleted)
    @Query("SELECT t FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Task> searchByUser(@Param("user") User user, @Param("query") String query, Pageable pageable);

    // Stats queries (exclude soft-deleted)
    @Query("SELECT COUNT(t) FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL")
    long countByUser(@Param("user") User user);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.user = :user AND t.status = :status AND t.deletedAt IS NULL")
    long countByUserAndStatus(@Param("user") User user, @Param("status") Status status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.user = :user AND t.dueDate < :date AND t.status <> :status AND t.deletedAt IS NULL")
    long countByUserAndDueDateBeforeAndStatusNot(@Param("user") User user, @Param("date") LocalDate date, @Param("status") Status status);

    // Admin stats (exclude soft-deleted)
    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status AND t.deletedAt IS NULL")
    long countByStatus(@Param("status") Status status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.dueDate < :date AND t.status <> :status AND t.deletedAt IS NULL")
    long countByDueDateBeforeAndStatusNot(@Param("date") LocalDate date, @Param("status") Status status);

    // Scheduler queries for notifications
    @Query("SELECT t FROM Task t WHERE t.dueDate = :tomorrow AND t.status <> 'COMPLETE' AND t.deletedAt IS NULL")
    org.springframework.data.domain.Page<Task> findTasksDueSoon(@Param("tomorrow") LocalDate tomorrow, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.dueDate < :today AND t.status <> 'COMPLETE' AND t.deletedAt IS NULL")
    org.springframework.data.domain.Page<Task> findOverdueTasks(@Param("today") LocalDate today, org.springframework.data.domain.Pageable pageable);
}
