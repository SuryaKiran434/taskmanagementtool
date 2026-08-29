package com.suryakiran.taskmanagementtool;

import com.suryakiran.taskmanagementtool.dto.TaskDTO;
import com.suryakiran.taskmanagementtool.dto.TaskStatsDTO;
import com.suryakiran.taskmanagementtool.model.*;
import com.suryakiran.taskmanagementtool.repository.ProjectRepository;
import com.suryakiran.taskmanagementtool.repository.RoleRepository;
import com.suryakiran.taskmanagementtool.repository.TaskRepository;
import com.suryakiran.taskmanagementtool.repository.UserRepository;
import com.suryakiran.taskmanagementtool.service.TaskService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Asserts JDBC statement counts (not just correctness) for the task read/stats/bulk paths.
 *
 * Hibernate statistics are enabled for this context only, and the class uses its own
 * in-memory database so seeded data cannot interfere with the other test classes.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.datasource.url=jdbc:h2:mem:querycountdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL"
})
class TaskQueryCountIntegrationTest {

    private static final int PAGE_SIZE = 20;

    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private User owner;
    private User otherUser;
    private List<String> ownerTaskIds;
    private List<String> otherUserTaskIds;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /** Runs the action with a cleared statistics counter and returns the JDBC statements prepared. */
    private long queriesFor(Runnable action) {
        Statistics stats = statistics();
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    private Authentication authFor(User user) {
        return new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of());
    }

    @BeforeEach
    void seed() {
        // Belt and braces: the property above configures it, this guarantees it at runtime.
        statistics().setStatisticsEnabled(true);
        assertTrue(statistics().isStatisticsEnabled(),
                "Hibernate statistics must be on, otherwise these assertions are vacuous");

        taskRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("ROLE_USER");
                    return roleRepository.save(r);
                });
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setName("ROLE_ADMIN");
                    return roleRepository.save(r);
                });

        owner = newUser("owner@example.com", Set.of(userRole, adminRole));
        otherUser = newUser("other@example.com", Set.of(userRole));

        // Each task gets its OWN project and its OWN assignee. This is what a real page of
        // tasks looks like, and it is the only shape that exposes the N+1: if every row shared
        // one project and one user, the first-level cache would hide the problem entirely.
        ownerTaskIds = new ArrayList<>();
        for (int i = 0; i < PAGE_SIZE; i++) {
            Project project = new Project();
            project.setName("Project " + i);
            project.setOwner(owner);
            project = projectRepository.save(project);

            User assignee = newUser("assignee" + i + "@example.com", Set.of(userRole));

            Task t = newTask("OWN" + String.format("%05d", i), owner, project, statusFor(i), i);
            t.setAssignee(assignee);
            ownerTaskIds.add(taskRepository.save(t).getId());
        }

        otherUserTaskIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Task t = newTask("OTH" + String.format("%05d", i), otherUser, null, Status.TO_DO, i);
            otherUserTaskIds.add(taskRepository.save(t).getId());
        }
    }

    private Status statusFor(int i) {
        return switch (i % 3) {
            case 0 -> Status.TO_DO;
            case 1 -> Status.IN_PROGRESS;
            default -> Status.COMPLETE;
        };
    }

    private User newUser(String email, Set<Role> roles) {
        User u = new User();
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEmail(email);
        u.setPassword("ValidPass1!");
        u.setRoles(roles);
        return userRepository.save(u);
    }

    private Task newTask(String id, User user, Project project, Status status, int i) {
        Task t = new Task();
        t.setId(id);
        t.setTitle("Task " + i);
        t.setDescription("Description for task " + i);
        t.setUser(user);
        t.setProject(project);
        t.setStatus(status);
        t.setPriority(Priority.MEDIUM);
        // Half the tasks are overdue, so the overdue aggregate is actually exercised.
        t.setDueDate(i % 2 == 0 ? LocalDate.now().minusDays(5) : LocalDate.now().plusDays(5));
        return t;
    }

    // ---------------------------------------------------------------- reads

    @Test
    void taskPageIsASmallConstantNumberOfQueries() {
        Authentication auth = authFor(owner);
        Pageable pageable = PageRequest.of(0, PAGE_SIZE);

        // Warm up so we measure steady state, not first-call metadata work.
        taskService.getAllTasks(pageable, auth);

        List<TaskDTO> content = new ArrayList<>();
        long queries = queriesFor(() -> {
            Page<TaskDTO> page = taskService.getAllTasks(pageable, auth);
            content.addAll(page.getContent());
        });

        System.out.println("[query-count] getAllTasks(page of " + PAGE_SIZE + ") = " + queries + " queries");

        assertEquals(PAGE_SIZE, content.size(), "page should contain all seeded tasks");
        // Every row must be fully populated without extra round trips.
        content.forEach(dto -> {
            assertNotNull(dto.getProjectName(), "project must be fetched for " + dto.getId());
            assertNotNull(dto.getCreator(), "creator must be fetched for " + dto.getId());
            assertNotNull(dto.getCreatorFirstName());
            assertNotNull(dto.getAssigneeEmail(), "assignee must be fetched for " + dto.getId());
        });

        assertTrue(queries <= 6,
                "A " + PAGE_SIZE + "-row task page must cost a small constant number of queries "
                        + "independent of page size, but took " + queries);
    }

    @Test
    void taskPageQueryCountDoesNotGrowWithPageSize() {
        Authentication auth = authFor(owner);
        taskService.getAllTasks(PageRequest.of(0, 5), auth); // warm up

        long small = queriesFor(() -> taskService.getAllTasks(PageRequest.of(0, 5), auth).getContent().size());
        long large = queriesFor(() -> taskService.getAllTasks(PageRequest.of(0, PAGE_SIZE), auth).getContent().size());

        System.out.println("[query-count] page of 5 = " + small + ", page of " + PAGE_SIZE + " = " + large);

        assertEquals(small, large,
                "Query count must be constant w.r.t. page size (N+1 regression): "
                        + small + " for 5 rows vs " + large + " for " + PAGE_SIZE + " rows");
    }

    @Test
    void filteredAndSearchPagesAreAlsoConstant() {
        Authentication auth = authFor(owner);
        Pageable pageable = PageRequest.of(0, PAGE_SIZE);
        taskService.getTasks(null, null, pageable, auth); // warm up
        taskService.searchTasks("Task", pageable, auth);

        long filtered = queriesFor(() -> taskService.getTasks(null, null, pageable, auth).getContent().size());
        long searched = queriesFor(() -> taskService.searchTasks("Task", pageable, auth).getContent().size());

        System.out.println("[query-count] filtered page = " + filtered + ", search page = " + searched);

        assertTrue(filtered <= 6, "filtered page took " + filtered + " queries (pre-fix this was 44)");
        assertTrue(searched <= 6, "search page took " + searched + " queries (pre-fix this was 44)");
    }

    @Test
    void singleTaskLookupIsConstant() {
        Authentication auth = authFor(owner);
        String id = ownerTaskIds.get(0);
        taskService.getTaskById(id, auth); // warm up

        long queries = queriesFor(() -> assertTrue(taskService.getTaskById(id, auth).isPresent()));
        System.out.println("[query-count] getTaskById = " + queries + " queries");
        assertTrue(queries <= 4, "single task lookup took " + queries + " queries");
    }

    // ---------------------------------------------------------------- stats

    @Test
    void dashboardStatsIsASingleAggregateQuery() {
        Authentication auth = authFor(owner);
        taskService.getTaskStats(auth); // warm up

        Statistics stats = statistics();
        stats.clear();
        TaskStatsDTO result = taskService.getTaskStats(auth);
        long total = stats.getPrepareStatementCount();

        // The user lookup that resolves the principal is unavoidable; isolate the stats query itself.
        long userLookup = queriesFor(() -> userRepository.findByEmail(owner.getEmail()));
        long statsQueries = total - userLookup;

        System.out.println("[query-count] getTaskStats total = " + total
                + " (user lookup " + userLookup + ", stats " + statsQueries + ")");

        // Correctness: the DTO shape and values must be unchanged.
        assertEquals(PAGE_SIZE, result.getTotal());
        assertEquals(7, result.getToDo());
        assertEquals(7, result.getInProgress());
        assertEquals(6, result.getComplete());
        // Even-indexed tasks are overdue; of those, the COMPLETE ones don't count.
        assertEquals(7, result.getOverdue());

        assertEquals(1, statsQueries,
                "Dashboard stats must be a single conditional-aggregation query, was " + statsQueries);
    }

    // ---------------------------------------------------------------- bulk

    /**
     * The write per changed row is inherent; the lookup per row is not. These tests pin the
     * SELECT side: the batch must be loaded with a single query no matter how many ids it holds.
     */
    @Test
    void bulkUpdateLoadsTheWholeBatchWithOneSelect() {
        Authentication auth = authFor(owner);
        taskService.bulkUpdateTasks(List.of(ownerTaskIds.get(0)), Status.TO_DO, null, auth); // warm up

        Statistics stats = statistics();

        stats.clear();
        taskService.bulkUpdateTasks(ownerTaskIds.subList(0, 5), Status.IN_PROGRESS, Priority.LOW, auth);
        long selectsForFive = stats.getQueryExecutionCount();

        stats.clear();
        int updated = taskService.bulkUpdateTasks(ownerTaskIds, Status.COMPLETE, Priority.HIGH, auth);
        long selectsForTwenty = stats.getQueryExecutionCount();
        long statements = stats.getPrepareStatementCount();

        System.out.println("[query-count] bulkUpdateTasks(" + PAGE_SIZE + " tasks) = " + statements
                + " statements, " + selectsForTwenty + " selects (5 tasks = " + selectsForFive + " selects)");

        assertEquals(PAGE_SIZE, updated, "all owned tasks should be updated");
        taskRepository.findAllById(ownerTaskIds).forEach(t -> {
            assertEquals(Status.COMPLETE, t.getStatus());
            assertEquals(Priority.HIGH, t.getPriority());
        });

        assertEquals(selectsForFive, selectsForTwenty,
                "the batch must be loaded with one query regardless of batch size, but 5 ids cost "
                        + selectsForFive + " selects and " + PAGE_SIZE + " ids cost " + selectsForTwenty);
        assertTrue(statements <= 3 + PAGE_SIZE,
                "bulk update must not do a lookup AND a save per item (expected at most one select "
                        + "plus one write per row), was " + statements);
    }

    @Test
    void bulkDeleteLoadsTheWholeBatchWithOneSelect() {
        Authentication auth = authFor(owner);

        Statistics stats = statistics();
        stats.clear();
        int deleted = taskService.bulkDeleteTasks(ownerTaskIds, auth);
        long selects = stats.getQueryExecutionCount();
        long statements = stats.getPrepareStatementCount();

        System.out.println("[query-count] bulkDeleteTasks(" + PAGE_SIZE + " tasks) = " + statements
                + " statements, " + selects + " selects");

        assertEquals(PAGE_SIZE, deleted, "all owned tasks should be soft-deleted");
        taskRepository.findAllById(ownerTaskIds)
                .forEach(t -> assertNotNull(t.getDeletedAt(), "task should be soft-deleted"));

        // One query to resolve the principal, one to load the whole batch. Nothing per id.
        assertEquals(2, selects,
                "bulk delete must issue one principal lookup plus one batch select, was " + selects);
        assertTrue(statements <= 3 + PAGE_SIZE,
                "bulk delete must not do a lookup AND a save per item, was " + statements);
    }

    // ------------------------------------------------- bulk authorisation

    @Test
    void bulkUpdateIgnoresTasksOwnedByAnotherUser() {
        Authentication auth = authFor(otherUser);

        List<String> mixed = new ArrayList<>(ownerTaskIds);
        mixed.addAll(otherUserTaskIds);

        int updated = taskService.bulkUpdateTasks(mixed, Status.COMPLETE, Priority.HIGH, auth);

        assertEquals(otherUserTaskIds.size(), updated,
                "only the caller's own tasks may be counted as updated");

        // The other user's tasks must be untouched.
        taskRepository.findAllById(ownerTaskIds).forEach(t -> {
            assertNotEquals(Priority.HIGH, t.getPriority(),
                    "task " + t.getId() + " belongs to another user and must not be modified");
        });
        taskRepository.findAllById(otherUserTaskIds).forEach(t -> {
            assertEquals(Status.COMPLETE, t.getStatus());
            assertEquals(Priority.HIGH, t.getPriority());
        });
    }

    @Test
    void bulkDeleteIgnoresTasksOwnedByAnotherUser() {
        Authentication auth = authFor(otherUser);

        List<String> mixed = new ArrayList<>(ownerTaskIds);
        mixed.addAll(otherUserTaskIds);

        int deleted = taskService.bulkDeleteTasks(mixed, auth);

        assertEquals(otherUserTaskIds.size(), deleted,
                "only the caller's own tasks may be counted as deleted");

        taskRepository.findAllById(ownerTaskIds).forEach(t ->
                assertNull(t.getDeletedAt(),
                        "task " + t.getId() + " belongs to another user and must not be soft-deleted"));
        taskRepository.findAllById(otherUserTaskIds).forEach(t ->
                assertNotNull(t.getDeletedAt()));
    }

    @Test
    void bulkOperationsSkipAlreadySoftDeletedTasks() {
        Authentication auth = authFor(owner);

        // Soft-delete one task, then try to include it in a bulk update.
        taskService.bulkDeleteTasks(List.of(ownerTaskIds.get(0)), auth);

        int updated = taskService.bulkUpdateTasks(ownerTaskIds, Status.COMPLETE, null, auth);
        assertEquals(PAGE_SIZE - 1, updated,
                "soft-deleted tasks must stay excluded from bulk updates");
    }
}
