package com.example.tfgbackend.user;

import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.SpecialtyNotAllowedException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.user.dto.AdminUpdateUserRequest;
import com.example.tfgbackend.user.dto.UpdateUserRequest;
import com.example.tfgbackend.user.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private User buildUser(Long id, String name, String email, UserRole role, boolean active) throws Exception {
        User user = User.builder()
                .name(name).email(email).phone("600000001")
                .passwordHash("$2a$12$hash").role(role).active(active)
                .build();
        var idField = com.example.tfgbackend.common.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
        return user;
    }

    // ---------------------------------------------------------------------------
    // getById
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getById: existing user returns mapped response")
    void getById_ExistingUser_ReturnsResponse() throws Exception {
        User user = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse resp = userService.getById(1L);

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.email()).isEqualTo("alice@test.com");
        assertThat(resp.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("getById: unknown id throws UserNotFoundException")
    void getById_UnknownId_ThrowsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---------------------------------------------------------------------------
    // updateMe
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("updateMe: updates both name and phone when both provided")
    void updateMe_BothFields_UpdatesUser() throws Exception {
        User user = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateUserRequest req = new UpdateUserRequest("Alice Updated", "600000002", null);
        UserResponse resp = userService.updateMe(1L, req);

        assertThat(resp.name()).isEqualTo("Alice Updated");
        assertThat(resp.phone()).isEqualTo("600000002");
    }

    @Test
    @DisplayName("updateMe: null phone keeps existing phone unchanged")
    void updateMe_NullPhone_KeepsExistingPhone() throws Exception {
        User user = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateUserRequest req = new UpdateUserRequest("New Name", null, null);
        UserResponse resp = userService.updateMe(1L, req);

        assertThat(resp.name()).isEqualTo("New Name");
        assertThat(resp.phone()).isEqualTo("600000001");
    }

    @Test
    @DisplayName("updateMe: null name keeps existing name unchanged")
    void updateMe_NullName_KeepsExistingName() throws Exception {
        User user = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateUserRequest req = new UpdateUserRequest(null, "700000000", null);
        UserResponse resp = userService.updateMe(1L, req);

        assertThat(resp.name()).isEqualTo("Alice");
        assertThat(resp.phone()).isEqualTo("700000000");
    }

    @Test
    @DisplayName("updateMe: unknown user throws UserNotFoundException")
    void updateMe_UnknownUser_ThrowsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMe(99L, new UpdateUserRequest("X", null, null)))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("updateMe: instructor can set their own specialty")
    void updateMe_SpecialtyProvided_InstructorUser_SetsSpecialty() throws Exception {
        User instructor = buildUser(1L, "Jorge", "jorge@test.com", UserRole.INSTRUCTOR, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(instructor));

        UserResponse resp = userService.updateMe(1L, new UpdateUserRequest(null, null, "Spinning"));

        assertThat(resp.specialty()).isEqualTo("Spinning");
    }

    @Test
    @DisplayName("updateMe: non-instructor setting specialty throws SpecialtyNotAllowedException")
    void updateMe_SpecialtyProvided_NonInstructorUser_ThrowsSpecialtyNotAllowedException() throws Exception {
        User customer = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> userService.updateMe(1L, new UpdateUserRequest(null, null, "Spinning")))
                .isInstanceOf(SpecialtyNotAllowedException.class);
    }

    // ---------------------------------------------------------------------------
    // listUsers
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("listUsers: no filter returns all users")
    void listUsers_NoFilter_ReturnsAllUsers() throws Exception {
        User alice = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        User bob = buildUser(2L, "Bob", "bob@test.com", UserRole.INSTRUCTOR, true);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(alice, bob), pageable, 2);

        when(userRepository.findAll(pageable)).thenReturn(page);

        PageResponse<UserResponse> result = userService.listUsers(null, pageable);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).hasSize(2);
    }

    @Test
    @DisplayName("listUsers: with role filter returns only matching users")
    void listUsers_WithRoleFilter_ReturnsFilteredUsers() throws Exception {
        User alice = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(alice), pageable, 1);

        when(userRepository.findByRole(UserRole.CUSTOMER, pageable)).thenReturn(page);

        PageResponse<UserResponse> result = userService.listUsers(UserRole.CUSTOMER, pageable);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("listUsers: empty result returns empty page")
    void listUsers_EmptyResult_ReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = Page.empty(pageable);

        when(userRepository.findAll(pageable)).thenReturn(page);

        PageResponse<UserResponse> result = userService.listUsers(null, pageable);

        assertThat(result.totalElements()).isZero();
        assertThat(result.content()).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // adminUpdateUser
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("adminUpdateUser: updates all provided fields")
    void adminUpdateUser_AllFields_UpdatesUser() throws Exception {
        User user = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUpdateUserRequest req = new AdminUpdateUserRequest("Admin Alice", "700000000", UserRole.INSTRUCTOR, false, null);
        UserResponse resp = userService.adminUpdateUser(1L, req);

        assertThat(resp.name()).isEqualTo("Admin Alice");
        assertThat(resp.phone()).isEqualTo("700000000");
        assertThat(resp.role()).isEqualTo(UserRole.INSTRUCTOR);
        assertThat(resp.active()).isFalse();
    }

    @Test
    @DisplayName("adminUpdateUser: null fields leave existing values unchanged")
    void adminUpdateUser_NullFields_KeepsExistingValues() throws Exception {
        User user = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        AdminUpdateUserRequest req = new AdminUpdateUserRequest(null, null, null, null, null);
        UserResponse resp = userService.adminUpdateUser(1L, req);

        assertThat(resp.name()).isEqualTo("Alice");
        assertThat(resp.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(resp.active()).isTrue();
    }

    @Test
    @DisplayName("adminUpdateUser: unknown user throws UserNotFoundException")
    void adminUpdateUser_UnknownUser_ThrowsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        AdminUpdateUserRequest req = new AdminUpdateUserRequest("Name", null, null, null, null);

        assertThatThrownBy(() -> userService.adminUpdateUser(99L, req))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("adminUpdateUser: specialty set on already-instructor user (no role change) stores it")
    void adminUpdateUser_SpecialtyProvided_ExistingInstructor_SetsSpecialty() throws Exception {
        User instructor = buildUser(1L, "Jorge", "jorge@test.com", UserRole.INSTRUCTOR, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(instructor));

        UserResponse resp = userService.adminUpdateUser(1L,
                new AdminUpdateUserRequest(null, null, null, null, "Spinning"));

        assertThat(resp.specialty()).isEqualTo("Spinning");
    }

    @Test
    @DisplayName("adminUpdateUser: specialty set while promoting user to INSTRUCTOR in same request stores it")
    void adminUpdateUser_SpecialtyProvided_UserPromotedToInstructor_SetsSpecialty() throws Exception {
        User customer = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        UserResponse resp = userService.adminUpdateUser(1L,
                new AdminUpdateUserRequest(null, null, UserRole.INSTRUCTOR, null, "Yoga"));

        assertThat(resp.specialty()).isEqualTo("Yoga");
        assertThat(resp.role()).isEqualTo(UserRole.INSTRUCTOR);
    }

    @Test
    @DisplayName("adminUpdateUser: specialty set on customer (no role change) throws SpecialtyNotAllowedException")
    void adminUpdateUser_SpecialtyProvided_CustomerUser_ThrowsSpecialtyNotAllowedException() throws Exception {
        User customer = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> userService.adminUpdateUser(1L,
                new AdminUpdateUserRequest(null, null, null, null, "Spinning")))
                .isInstanceOf(SpecialtyNotAllowedException.class);
    }

    @Test
    @DisplayName("adminUpdateUser: specialty set while demoting instructor throws SpecialtyNotAllowedException")
    void adminUpdateUser_SpecialtyProvided_InstructorDemoted_ThrowsSpecialtyNotAllowedException() throws Exception {
        User instructor = buildUser(1L, "Jorge", "jorge@test.com", UserRole.INSTRUCTOR, true);
        instructor.setSpecialty("Spinning");
        when(userRepository.findById(1L)).thenReturn(Optional.of(instructor));

        assertThatThrownBy(() -> userService.adminUpdateUser(1L,
                new AdminUpdateUserRequest(null, null, UserRole.CUSTOMER, null, "Spinning")))
                .isInstanceOf(SpecialtyNotAllowedException.class);
    }

    @Test
    @DisplayName("adminUpdateUser: demoting instructor with no specialty in request auto-clears specialty")
    void adminUpdateUser_SpecialtyNull_InstructorDemoted_ClearsSpecialty() throws Exception {
        User instructor = buildUser(1L, "Jorge", "jorge@test.com", UserRole.INSTRUCTOR, true);
        instructor.setSpecialty("Spinning");
        when(userRepository.findById(1L)).thenReturn(Optional.of(instructor));

        UserResponse resp = userService.adminUpdateUser(1L,
                new AdminUpdateUserRequest(null, null, UserRole.ADMIN, null, null));

        assertThat(resp.specialty()).isNull();
        assertThat(resp.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("adminUpdateUser: instructor with no specialty in request keeps existing specialty unchanged")
    void adminUpdateUser_SpecialtyNull_InstructorStaysInstructor_KeepsSpecialty() throws Exception {
        User instructor = buildUser(1L, "Jorge", "jorge@test.com", UserRole.INSTRUCTOR, true);
        instructor.setSpecialty("Spinning");
        when(userRepository.findById(1L)).thenReturn(Optional.of(instructor));

        UserResponse resp = userService.adminUpdateUser(1L,
                new AdminUpdateUserRequest(null, null, null, null, null));

        assertThat(resp.specialty()).isEqualTo("Spinning");
    }

    // ---------------------------------------------------------------------------
    // deactivateUser
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("deactivateUser: sets active to false")
    void deactivateUser_ExistingUser_SetsActiveFalse() throws Exception {
        User user = buildUser(1L, "Alice", "alice@test.com", UserRole.CUSTOMER, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deactivateUser(1L);

        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("deactivateUser: unknown user throws UserNotFoundException")
    void deactivateUser_UnknownUser_ThrowsUserNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("99");
    }
}
