package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Comment;
import com.suryakiran.taskmanagementtool.model.Task;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTaskOrderByCreatedAtDesc(Task task);

    Optional<Comment> findByIdAndTask(Long id, Task task);

    Optional<Comment> findByIdAndAuthor(Long id, User author);

    int countByTask(Task task);
}
