package com.example.tfgbackend.user;

import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.dto.UserResponse;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    @Test
    @DisplayName("getById: existing user returns mapped response")
    void getById_ExistingUser_ReturnsResponse() throws Exception {
        User user = User.builder()
                .name("Alice").email("alice@test.com").phone("600000001")
                .passwordHash("$2a$12$hash").role(UserRole.CUSTOMER).active(true)
                .build();
        var idField = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse resp = userService.getById(1L);

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.email()).isEqualTo("alice@test.com");
        assertThat(resp.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("getById: unknown id throws EntityNotFoundException")
    void getById_UnknownId_ThrowsEntityNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}
