package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Priority;
import com.suryakiran.taskmanagementtool.model.Status;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Read queries that feed {@code TaskConversionService.convertToDTO} carry
 * {@code @EntityGraph(attributePaths = {"project", "user", "assignee"})}. Those three to-one
 * associations are dereferenced for every row during DTO conversion, so without the graph each
 * row costs its own SELECT (and, because {@code User.roles} is EAGER, drags the join table too).
 *
 * Only to-one associations are listed, deliberately: adding a to-many such as {@code user.roles}
 * to an entity graph used with {@link Pageable} makes Hibernate paginate in memory (HHH90003004).
 * {@code User.roles} is handled instead by {@code @BatchSize} on the collection itself.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    boolean existsById(String id);

    @EntityGraph(attributePaths = {"project", "user", "assignee"})
    Page<Task> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"project", "user", "assignee"})
    Optional<Task> findById(String id);

    // Global filter (admin use) — AND logic, excludes soft-deleted
    @EntityGraph(attributePaths = {"project", "user", "assignee"})
    @Query("SELECT t FROM Task t WHERE t.deletedAt IS NULL AND (:status IS NULL OR t.status = :status) AND (:priority IS NULL OR t.priority = :priority)")
    Page<Task> findByStatusAndPriority(@Param("status") Status status, @Param("priority") Priority priority, Pageable pageable);

    // User-scoped queries — all exclude soft-deleted tasks
    @EntityGraph(attributePaths = {"project", "user", "assignee"})
    @Query("SELECT t FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL")
    Page<Task> findByUser(@Param("user") User user, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "user", "assignee"})
    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.user = :user AND t.deletedAt IS NULL")
    Optional<Task> findByIdAndUser(@Param("id") String id, @Param("user") User user);

    @EntityGraph(attributePaths = {"project", "user", "assignee"})
    @Query("SELECT t FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL AND (:status IS NULL OR t.status = :status) AND (:priority IS NULL OR t.priority = :priority)")
    Page<Task> findByUserAndStatusAndPriority(@Param("user") User user, @Param("status") Status status, @Param("priority") Priority priority, Pageable pageable);

    boolean existsByIdAndUserId(String id, int userId);

    /**
     * Batch loader for bulk operations. Applies exactly the same predicates as
     * {@link #findByIdAndUser} (owned by the caller, not soft-deleted), so ownership is
     * still enforced per task — it is just enforced for the whole batch in one round trip
     * instead of one SELECT per id.
     */
    @Query("SELECT t FROM Task t WHERE t.id IN :ids AND t.user = :user AND t.deletedAt IS NULL")
    List<Task> findAllByIdInAndUser(@Param("ids") Collection<String> ids, @Param("user") User user);

    // Search by title or description (user-scoped, excludes soft-deleted)
    @EntityGraph(attributePaths = {"project", "user", "assignee"})
    @Query("SELECT t FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL AND (LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Task> searchByUser(@Param("user") User user, @Param("query") String query, Pageable pageable);

    /**
     * Single-pass dashboard aggregate. Replaces five separate COUNT queries over the same rows.
     * SUM(...) returns NULL when the user has no tasks at all, hence the boxed getters.
     */
    interface TaskStatsProjection {
        Long getTotal();
        Long getToDo();
        Long getInProgress();
        Long getComplete();
        Long getOverdue();
    }

    @Query("SELECT COUNT(t) AS total, "
            + "SUM(CASE WHEN t.status = :toDo THEN 1 ELSE 0 END) AS toDo, "
            + "SUM(CASE WHEN t.status = :inProgress THEN 1 ELSE 0 END) AS inProgress, "
            + "SUM(CASE WHEN t.status = :complete THEN 1 ELSE 0 END) AS complete, "
            + "SUM(CASE WHEN t.dueDate < :today AND t.status <> :complete THEN 1 ELSE 0 END) AS overdue "
            + "FROM Task t WHERE t.user = :user AND t.deletedAt IS NULL")
    TaskStatsProjection getStatsByUser(@Param("user") User user,
                                       @Param("toDo") Status toDo,
                                       @Param("inProgress") Status inProgress,
                                       @Param("complete") Status complete,
                                       @Param("today") LocalDate today);

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
