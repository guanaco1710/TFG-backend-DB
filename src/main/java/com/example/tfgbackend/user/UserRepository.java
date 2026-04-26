package com.example.tfgbackend.user;

import com.example.tfgbackend.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Used during login and duplicate-email checks. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Admin user list filtered by role. */
    Page<User> findByRole(UserRole role, Pageable pageable);

    /** Admin user list filtered by active status. */
    Page<User> findByActive(boolean active, Pageable pageable);
}
