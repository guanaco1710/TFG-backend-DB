package com.example.tfgbackend.user;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.dto.AdminUpdateUserRequest;
import com.example.tfgbackend.user.dto.UpdateUserRequest;
import com.example.tfgbackend.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for user profile operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * Retrieves a user by ID and maps to a {@link UserResponse} DTO.
     *
     * @throws UserNotFoundException if no user with the given ID exists
     */
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return toResponse(user);
    }

    /**
     * Applies a partial update of name and/or phone for the authenticated user.
     * Only non-null fields from the request are applied.
     */
    @Transactional
    public UserResponse updateMe(Long userId, UpdateUserRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (req.name() != null) {
            user.setName(req.name());
        }
        if (req.phone() != null) {
            user.setPhone(req.phone());
        }
        return toResponse(user);
    }

    /**
     * Returns a paginated list of all users, optionally filtered by role.
     */
    public PageResponse<UserResponse> listUsers(UserRole roleFilter, Pageable pageable) {
        Page<User> page = (roleFilter != null)
                ? userRepository.findByRole(roleFilter, pageable)
                : userRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    /**
     * Admin-level partial update: name, phone, role, and/or active flag.
     * Only non-null fields are applied.
     */
    @Transactional
    public UserResponse adminUpdateUser(Long id, AdminUpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (req.name() != null) {
            user.setName(req.name());
        }
        if (req.phone() != null) {
            user.setPhone(req.phone());
        }
        if (req.role() != null) {
            user.setRole(req.role());
        }
        if (req.active() != null) {
            user.setActive(req.active());
        }
        return toResponse(user);
    }

    /**
     * Soft-deletes a user by setting {@code active = false}.
     */
    @Transactional
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.setActive(false);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
