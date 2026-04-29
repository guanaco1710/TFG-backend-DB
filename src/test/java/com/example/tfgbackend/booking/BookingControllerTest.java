package com.example.tfgbackend.booking;

import com.example.tfgbackend.auth.AuthenticatedUser;
import com.example.tfgbackend.auth.JwtService;
import com.example.tfgbackend.booking.dto.BookingResponse;
import com.example.tfgbackend.booking.dto.ClassSessionSummary;
import com.example.tfgbackend.booking.dto.CreateBookingRequest;
import com.example.tfgbackend.booking.dto.RosterEntryResponse;
import com.example.tfgbackend.common.GlobalExceptionHandler;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.common.exception.AlreadyBookedException;
import com.example.tfgbackend.common.exception.AlreadyOnWaitlistException;
import com.example.tfgbackend.common.exception.BookingAlreadyCancelledException;
import com.example.tfgbackend.common.exception.BookingNotFoundException;
import com.example.tfgbackend.common.exception.ClassFullException;
import com.example.tfgbackend.common.exception.MonthlyClassLimitReachedException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotBookableException;
import com.example.tfgbackend.common.exception.WaitlistEntryNotFoundException;
import com.example.tfgbackend.common.exception.WaitlistNotPermittedException;
import com.example.tfgbackend.config.SecurityConfig;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.waitlist.dto.WaitlistEntryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link BookingController}.
 *
 * <p>Spring Security is active; we authenticate requests using
 * {@link SecurityMockMvcRequestPostProcessors#authentication} rather than
 * relying on a real JWT, so the JWT filter is bypassed cleanly.
 *
 * <p>The controller does not yet exist; tests will fail to compile until the
 * production class is created. That is intentional — TDD.
 */
@WebMvcTest(BookingController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class BookingControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean BookingService bookingService;
    @MockitoBean JwtService jwtService;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String BOOKINGS_BASE = "/api/v1/bookings";
    private static final String SESSIONS_BASE = "/api/v1/class-sessions";

    private static final ClassSessionSummary SESSION_SUMMARY =
            new ClassSessionSummary(100L, "Spinning", LocalDateTime.now().plusDays(1), "Downtown Gym");

    // ---------------------------------------------------------------------------
    // Authentication helpers
    // ---------------------------------------------------------------------------

    private UsernamePasswordAuthenticationToken customerAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "alice@test.com", UserRole.CUSTOMER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private UsernamePasswordAuthenticationToken adminAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "admin@test.com", UserRole.ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken instructorAuth(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "instructor@test.com", UserRole.INSTRUCTOR);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR")));
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/bookings
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/bookings — createBooking")
    class CreateBooking {

        @Test
        @DisplayName("happy path: returns 201 with Location header and booking body")
        void createBooking_ValidRequest_Returns201WithLocationAndBody() throws Exception {
            BookingResponse response = new BookingResponse(200L, SESSION_SUMMARY, BookingStatus.CONFIRMED, Instant.now());
            when(bookingService.createBooking(eq(1L), eq(100L))).thenReturn(response);

            CreateBookingRequest body = new CreateBookingRequest(100L);

            mvc.perform(post(BOOKINGS_BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", BOOKINGS_BASE + "/200"))
                    .andExpect(jsonPath("$.id").value(200))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.classSession.id").value(100));
        }

        @Test
        @DisplayName("missing sessionId returns 400")
        void createBooking_MissingSessionId_Returns400() throws Exception {
            mvc.perform(post(BOOKINGS_BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\": null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("ValidationFailed"));
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void createBooking_Unauthenticated_Returns401() throws Exception {
            CreateBookingRequest body = new CreateBookingRequest(100L);

            mvc.perform(post(BOOKINGS_BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("session not found returns 404")
        void createBooking_SessionNotFound_Returns404() throws Exception {
            when(bookingService.createBooking(any(), any()))
                    .thenThrow(new SessionNotFoundException(999L));

            CreateBookingRequest body = new CreateBookingRequest(999L);

            mvc.perform(post(BOOKINGS_BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }

        @Test
        @DisplayName("session not bookable returns 409")
        void createBooking_SessionNotBookable_Returns409() throws Exception {
            when(bookingService.createBooking(any(), any()))
                    .thenThrow(new SessionNotBookableException(100L));

            CreateBookingRequest body = new CreateBookingRequest(100L);

            mvc.perform(post(BOOKINGS_BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SessionNotBookable"));
        }

        @Test
        @DisplayName("already booked returns 409")
        void createBooking_AlreadyBooked_Returns409() throws Exception {
            when(bookingService.createBooking(any(), any()))
                    .thenThrow(new AlreadyBookedException(1L, 100L));

            CreateBookingRequest body = new CreateBookingRequest(100L);

            mvc.perform(post(BOOKINGS_BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("AlreadyBooked"));
        }

        @Test
        @DisplayName("class full returns 409")
        void createBooking_ClassFull_Returns409() throws Exception {
            when(bookingService.createBooking(any(), any()))
                    .thenThrow(new ClassFullException(100L));

            CreateBookingRequest body = new CreateBookingRequest(100L);

            mvc.perform(post(BOOKINGS_BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("ClassFull"));
        }

        @Test
        @DisplayName("monthly class limit reached returns 409")
        void createBooking_MonthlyLimitReached_Returns409() throws Exception {
            when(bookingService.createBooking(any(), any()))
                    .thenThrow(new MonthlyClassLimitReachedException("Monthly class limit of 10 reached"));

            CreateBookingRequest body = new CreateBookingRequest(100L);

            mvc.perform(post(BOOKINGS_BASE)
                            .with(authentication(customerAuth(1L)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("MonthlyClassLimitReached"));
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/bookings/{id}/cancel
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/bookings/{id}/cancel — cancelBooking")
    class CancelBooking {

        @Test
        @DisplayName("customer cancels own booking returns 204")
        void cancelBooking_OwnerCancels_Returns204() throws Exception {
            mvc.perform(post(BOOKINGS_BASE + "/200/cancel")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("admin cancels any booking returns 204")
        void cancelBooking_AdminCancels_Returns204() throws Exception {
            mvc.perform(post(BOOKINGS_BASE + "/200/cancel")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void cancelBooking_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(BOOKINGS_BASE + "/200/cancel"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("booking not found returns 404")
        void cancelBooking_BookingNotFound_Returns404() throws Exception {
            doThrow(new BookingNotFoundException(999L))
                    .when(bookingService).cancelBooking(eq(999L), any(), any());

            mvc.perform(post(BOOKINGS_BASE + "/999/cancel")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("BookingNotFound"));
        }

        @Test
        @DisplayName("booking already cancelled returns 409")
        void cancelBooking_AlreadyCancelled_Returns409() throws Exception {
            doThrow(new BookingAlreadyCancelledException(200L))
                    .when(bookingService).cancelBooking(eq(200L), any(), any());

            mvc.perform(post(BOOKINGS_BASE + "/200/cancel")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("BookingAlreadyCancelled"));
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/bookings/me
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/bookings/me — getMyBookings")
    class GetMyBookings {

        @Test
        @DisplayName("returns 200 with paginated bookings")
        void getMyBookings_Authenticated_Returns200WithPage() throws Exception {
            BookingResponse br = new BookingResponse(1L, SESSION_SUMMARY, BookingStatus.CONFIRMED, Instant.now());
            PageResponse<BookingResponse> page = new PageResponse<>(
                    List.of(br), 0, 10, 1L, 1, false);

            when(bookingService.getMyBookings(eq(1L), any(), any())).thenReturn(page);

            mvc.perform(get(BOOKINGS_BASE + "/me")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("status filter is forwarded to the service")
        void getMyBookings_WithStatusFilter_ForwardsFilterToService() throws Exception {
            PageResponse<BookingResponse> page = new PageResponse<>(List.of(), 0, 10, 0L, 0, false);
            when(bookingService.getMyBookings(eq(1L), eq(BookingStatus.CANCELLED), any())).thenReturn(page);

            mvc.perform(get(BOOKINGS_BASE + "/me")
                            .param("status", "CANCELLED")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getMyBookings_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BOOKINGS_BASE + "/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/bookings/me/waitlist
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/bookings/me/waitlist — getMyWaitlistEntries")
    class GetMyWaitlistEntries {

        @Test
        @DisplayName("returns 200 with list of waitlist entries")
        void getMyWaitlistEntries_Authenticated_Returns200WithList() throws Exception {
            WaitlistEntryResponse entry = new WaitlistEntryResponse(
                    10L, SESSION_SUMMARY, 1L, 1, Instant.now());
            when(bookingService.getMyWaitlistEntries(1L)).thenReturn(List.of(entry));

            mvc.perform(get(BOOKINGS_BASE + "/me/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].position").value(1));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getMyWaitlistEntries_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BOOKINGS_BASE + "/me/waitlist"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/bookings/{id}
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/bookings/{id} — getBookingById")
    class GetBookingById {

        @Test
        @DisplayName("owner retrieves own booking returns 200")
        void getBookingById_Owner_Returns200() throws Exception {
            BookingResponse response = new BookingResponse(200L, SESSION_SUMMARY, BookingStatus.CONFIRMED, Instant.now());
            when(bookingService.getBookingById(200L, 1L, false)).thenReturn(response);

            mvc.perform(get(BOOKINGS_BASE + "/200")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(200))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"));
        }

        @Test
        @DisplayName("admin retrieves any booking returns 200")
        void getBookingById_Admin_Returns200() throws Exception {
            BookingResponse response = new BookingResponse(200L, SESSION_SUMMARY, BookingStatus.CONFIRMED, Instant.now());
            when(bookingService.getBookingById(200L, 99L, true)).thenReturn(response);

            mvc.perform(get(BOOKINGS_BASE + "/200")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(200));
        }

        @Test
        @DisplayName("booking not found returns 404")
        void getBookingById_NotFound_Returns404() throws Exception {
            when(bookingService.getBookingById(eq(999L), any(), any()))
                    .thenThrow(new BookingNotFoundException(999L));

            mvc.perform(get(BOOKINGS_BASE + "/999")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("BookingNotFound"));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getBookingById_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(BOOKINGS_BASE + "/200"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/class-sessions/{sessionId}/bookings
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/v1/class-sessions/{sessionId}/bookings — getSessionRoster")
    class GetSessionRoster {

        @Test
        @DisplayName("instructor retrieves roster returns 200")
        void getSessionRoster_Instructor_Returns200() throws Exception {
            RosterEntryResponse entry = new RosterEntryResponse(
                    1L, BookingStatus.CONFIRMED, Instant.now(), 1L, "Alice", "alice@test.com");
            PageResponse<RosterEntryResponse> page = new PageResponse<>(
                    List.of(entry), 0, 10, 1L, 1, false);

            when(bookingService.getSessionRoster(eq(100L), any())).thenReturn(page);

            mvc.perform(get(SESSIONS_BASE + "/100/bookings")
                            .with(authentication(instructorAuth(10L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].userId").value(1))
                    .andExpect(jsonPath("$.content[0].userFullName").value("Alice"));
        }

        @Test
        @DisplayName("admin retrieves roster returns 200")
        void getSessionRoster_Admin_Returns200() throws Exception {
            PageResponse<RosterEntryResponse> page = new PageResponse<>(List.of(), 0, 10, 0L, 0, false);
            when(bookingService.getSessionRoster(eq(100L), any())).thenReturn(page);

            mvc.perform(get(SESSIONS_BASE + "/100/bookings")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("customer is forbidden — returns 403")
        void getSessionRoster_Customer_Returns403() throws Exception {
            mvc.perform(get(SESSIONS_BASE + "/100/bookings")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void getSessionRoster_Unauthenticated_Returns401() throws Exception {
            mvc.perform(get(SESSIONS_BASE + "/100/bookings"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("session not found returns 404")
        void getSessionRoster_SessionNotFound_Returns404() throws Exception {
            when(bookingService.getSessionRoster(eq(999L), any()))
                    .thenThrow(new SessionNotFoundException(999L));

            mvc.perform(get(SESSIONS_BASE + "/999/bookings")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/class-sessions/{sessionId}/waitlist
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/v1/class-sessions/{sessionId}/waitlist — joinWaitlist")
    class JoinWaitlist {

        @Test
        @DisplayName("customer joins waitlist returns 201 with Location")
        void joinWaitlist_Customer_Returns201WithLocation() throws Exception {
            WaitlistEntryResponse entry = new WaitlistEntryResponse(
                    50L, SESSION_SUMMARY, 1L, 1, Instant.now());
            when(bookingService.joinWaitlist(1L, 100L)).thenReturn(entry);

            mvc.perform(post(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString("/50")))
                    .andExpect(jsonPath("$.id").value(50))
                    .andExpect(jsonPath("$.position").value(1));
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void joinWaitlist_Unauthenticated_Returns401() throws Exception {
            mvc.perform(post(SESSIONS_BASE + "/100/waitlist"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("session not found returns 404")
        void joinWaitlist_SessionNotFound_Returns404() throws Exception {
            when(bookingService.joinWaitlist(any(), eq(999L)))
                    .thenThrow(new SessionNotFoundException(999L));

            mvc.perform(post(SESSIONS_BASE + "/999/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("SessionNotFound"));
        }

        @Test
        @DisplayName("already booked returns 409")
        void joinWaitlist_AlreadyBooked_Returns409() throws Exception {
            when(bookingService.joinWaitlist(any(), eq(100L)))
                    .thenThrow(new AlreadyBookedException(1L, 100L));

            mvc.perform(post(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("AlreadyBooked"));
        }

        @Test
        @DisplayName("already on waitlist returns 409")
        void joinWaitlist_AlreadyOnWaitlist_Returns409() throws Exception {
            when(bookingService.joinWaitlist(any(), eq(100L)))
                    .thenThrow(new AlreadyOnWaitlistException(1L, 100L));

            mvc.perform(post(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("AlreadyOnWaitlist"));
        }

        @Test
        @DisplayName("session not bookable returns 409")
        void joinWaitlist_SessionNotBookable_Returns409() throws Exception {
            when(bookingService.joinWaitlist(any(), eq(100L)))
                    .thenThrow(new SessionNotBookableException(100L));

            mvc.perform(post(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("SessionNotBookable"));
        }

        @Test
        @DisplayName("membership plan does not allow waitlist returns 403")
        void joinWaitlist_WaitlistNotPermitted_Returns403() throws Exception {
            when(bookingService.joinWaitlist(any(), eq(100L)))
                    .thenThrow(new WaitlistNotPermittedException());

            mvc.perform(post(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("WaitlistNotPermitted"));
        }
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/class-sessions/{sessionId}/waitlist
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/v1/class-sessions/{sessionId}/waitlist — leaveWaitlist")
    class LeaveWaitlist {

        @Test
        @DisplayName("customer leaves waitlist returns 204")
        void leaveWaitlist_Customer_Returns204() throws Exception {
            mvc.perform(delete(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("admin can remove a customer from waitlist returns 204")
        void leaveWaitlist_Admin_Returns204() throws Exception {
            mvc.perform(delete(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(adminAuth(99L))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("unauthenticated returns 401")
        void leaveWaitlist_Unauthenticated_Returns401() throws Exception {
            mvc.perform(delete(SESSIONS_BASE + "/100/waitlist"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("entry not found returns 404")
        void leaveWaitlist_EntryNotFound_Returns404() throws Exception {
            doThrow(new WaitlistEntryNotFoundException(1L, 100L))
                    .when(bookingService).leaveWaitlist(any(), eq(100L));

            mvc.perform(delete(SESSIONS_BASE + "/100/waitlist")
                            .with(authentication(customerAuth(1L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("WaitlistEntryNotFound"));
        }
    }
}
