package com.example.tfgbackend.user;

import com.example.tfgbackend.AbstractRepositoryTest;
import com.example.tfgbackend.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired UserRepository repository;

    private User persist(String email, UserRole role, boolean active) {
        return em.persistAndFlush(User.builder()
                .name("User " + email)
                .email(email)
                .passwordHash("$2a$10$hash")
                .role(role)
                .active(active)
                .build());
    }

    @Test
    void findByEmail_ExistingEmail_ReturnsUser() {
        persist("alice@test.com", UserRole.CUSTOMER, true);
        em.clear();

        Optional<User> found = repository.findByEmail("alice@test.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@test.com");
    }

    @Test
    void findByEmail_UnknownEmail_ReturnsEmpty() {
        assertThat(repository.findByEmail("nobody@test.com")).isEmpty();
    }

    @Test
    void existsByEmail_ExistingEmail_ReturnsTrue() {
        persist("bob@test.com", UserRole.CUSTOMER, true);
        em.clear();

        assertThat(repository.existsByEmail("bob@test.com")).isTrue();
    }

    @Test
    void existsByEmail_UnknownEmail_ReturnsFalse() {
        assertThat(repository.existsByEmail("ghost@test.com")).isFalse();
    }

    @Test
    void findByRole_CustomerRole_ReturnsOnlyCustomers() {
        persist("c1@test.com", UserRole.CUSTOMER, true);
        persist("c2@test.com", UserRole.CUSTOMER, true);
        persist("admin@test.com", UserRole.ADMIN, true);
        em.clear();

        Page<User> page = repository.findByRole(UserRole.CUSTOMER, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2)
                .allMatch(u -> u.getRole() == UserRole.CUSTOMER);
    }

    @Test
    void findByActive_True_ReturnsOnlyActiveUsers() {
        persist("active@test.com", UserRole.CUSTOMER, true);
        em.persistAndFlush(User.builder()
                .name("Inactive")
                .email("inactive@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .active(false)
                .build());
        em.clear();

        Page<User> page = repository.findByActive(true, PageRequest.of(0, 10));

        assertThat(page.getContent()).isNotEmpty()
                .allMatch(User::isActive);
    }

    @Test
    void findByActive_False_ReturnsOnlyInactiveUsers() {
        persist("active2@test.com", UserRole.CUSTOMER, true);
        em.persistAndFlush(User.builder()
                .name("Inactive2")
                .email("inactive2@test.com")
                .passwordHash("$2a$10$hash")
                .role(UserRole.CUSTOMER)
                .active(false)
                .build());
        em.clear();

        Page<User> page = repository.findByActive(false, PageRequest.of(0, 10));

        assertThat(page.getContent()).isNotEmpty()
                .noneMatch(User::isActive);
    }
}
