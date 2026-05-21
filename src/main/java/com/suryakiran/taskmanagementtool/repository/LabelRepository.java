package com.suryakiran.taskmanagementtool.repository;

import com.suryakiran.taskmanagementtool.model.Label;
import com.suryakiran.taskmanagementtool.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {

    List<Label> findByUser(User user);

    Optional<Label> findByIdAndUser(Long id, User user);

    boolean existsByNameAndUser(String name, User user);
}
