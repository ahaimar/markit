package com.packs.userservice.repository;

import com.packs.userservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByIdAndDeletedAtIsNull(UUID id);

	List<User> findByDeletedAtIsNullOrderByCreatedAtAsc();

	Page<User> findByDeletedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

	boolean existsByEmail(String email);
}