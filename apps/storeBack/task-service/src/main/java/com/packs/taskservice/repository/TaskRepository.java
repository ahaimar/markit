package com.packs.taskservice.repository;

import com.packs.taskservice.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

	Optional<Task> findByIdAndUserId(UUID id, UUID userId);

	Page<Task> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	Page<Task> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status, Pageable pageable);
}