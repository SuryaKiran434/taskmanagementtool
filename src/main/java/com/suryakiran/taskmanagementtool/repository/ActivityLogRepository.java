package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.ActivityLog;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByTaskIdOrderByCreatedAtDesc(String taskId);

    Page<ActivityLog> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
