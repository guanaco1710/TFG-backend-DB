package com.example.tfgbackend.common;

import com.example.tfgbackend.common.exception.AlreadyBookedException;
import com.example.tfgbackend.common.exception.CardExpiredException;
import com.example.tfgbackend.common.exception.ClassTypeInUseException;
import com.example.tfgbackend.common.exception.DuplicatePaymentMethodException;
import com.example.tfgbackend.common.exception.InvalidDefaultToggleException;
import com.example.tfgbackend.common.exception.PaymentMethodNotFoundException;
import com.example.tfgbackend.common.exception.NotificationNotFoundException;
import com.example.tfgbackend.common.exception.NotAttendedSessionException;
import com.example.tfgbackend.common.exception.RatingAlreadyExistsException;
import com.example.tfgbackend.common.exception.RatingNotFoundException;
import com.example.tfgbackend.common.exception.ClassTypeNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.ClassTypeNotFoundException;
import com.example.tfgbackend.common.exception.GymNameAlreadyExistsException;
import com.example.tfgbackend.common.exception.GymNotFoundException;
import com.example.tfgbackend.common.exception.InstructorNotFoundException;
import com.example.tfgbackend.common.exception.AlreadyOnWaitlistException;
import com.example.tfgbackend.common.exception.AttendanceNotFoundException;
import com.example.tfgbackend.common.exception.BookingAlreadyCancelledException;
import com.example.tfgbackend.common.exception.BookingNotFoundException;
import com.example.tfgbackend.common.exception.ClassFullException;
import com.example.tfgbackend.common.exception.EmailAlreadyExistsException;
import com.example.tfgbackend.common.exception.InvalidCredentialsException;
import com.example.tfgbackend.common.exception.InvalidResetTokenException;
import com.example.tfgbackend.common.exception.MembershipPlanInUseException;
import com.example.tfgbackend.common.exception.MembershipPlanInactiveException;
import com.example.tfgbackend.common.exception.MembershipPlanNotFoundException;
import com.example.tfgbackend.common.exception.MonthlyClassLimitReachedException;
import com.example.tfgbackend.common.exception.NoActiveSubscriptionException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotAttendableException;
import com.example.tfgbackend.common.exception.SessionNotBookableException;
import com.example.tfgbackend.common.exception.SpecialtyNotAllowedException;
import com.example.tfgbackend.common.exception.SubscriptionAlreadyActiveException;
import com.example.tfgbackend.common.exception.SubscriptionCancellationPendingException;
import com.example.tfgbackend.common.exception.SubscriptionNotActiveException;
import com.example.tfgbackend.common.exception.SubscriptionNotFoundException;
import com.example.tfgbackend.common.exception.TokenExpiredException;
import com.example.tfgbackend.common.exception.TokenRevokedException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.common.exception.WaitlistEntryNotFoundException;
import com.example.tfgbackend.common.exception.WaitlistNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central mapping from domain exceptions to HTTP error responses.
 *
 * <p>Error body shape:
 * <pre>
 * { "timestamp": "...", "status": 409, "error": "EmailAlreadyExists", "message": "...", "path": "/api/v1/auth/register" }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<Map<String, Object>> handleEmailAlreadyExists(
            EmailAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "EmailAlreadyExists", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "InvalidCredentials", ex.getMessage(), req);
    }

    @ExceptionHandler(TokenExpiredException.class)
    ResponseEntity<Map<String, Object>> handleTokenExpired(
            TokenExpiredException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "TokenExpired", ex.getMessage(), req);
    }

    @ExceptionHandler(TokenRevokedException.class)
    ResponseEntity<Map<String, Object>> handleTokenRevoked(
            TokenRevokedException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "TokenRevoked", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidResetTokenException.class)
    ResponseEntity<Map<String, Object>> handleInvalidResetToken(
            InvalidResetTokenException ex, HttpServletRequest req) {
        return error(HttpStatus.UNAUTHORIZED, "InvalidResetToken", ex.getMessage(), req);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "UserNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(BookingNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleBookingNotFound(BookingNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "BookingNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleSessionNotFound(SessionNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "SessionNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(AlreadyBookedException.class)
    ResponseEntity<Map<String, Object>> handleAlreadyBooked(AlreadyBookedException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "AlreadyBooked", ex.getMessage(), req);
    }

    @ExceptionHandler(ClassFullException.class)
    ResponseEntity<Map<String, Object>> handleClassFull(ClassFullException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "ClassFull", ex.getMessage(), req);
    }

    @ExceptionHandler(SessionNotBookableException.class)
    ResponseEntity<Map<String, Object>> handleSessionNotBookable(SessionNotBookableException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "SessionNotBookable", ex.getMessage(), req);
    }

    @ExceptionHandler(BookingAlreadyCancelledException.class)
    ResponseEntity<Map<String, Object>> handleBookingAlreadyCancelled(BookingAlreadyCancelledException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "BookingAlreadyCancelled", ex.getMessage(), req);
    }

    @ExceptionHandler(AlreadyOnWaitlistException.class)
    ResponseEntity<Map<String, Object>> handleAlreadyOnWaitlist(AlreadyOnWaitlistException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "AlreadyOnWaitlist", ex.getMessage(), req);
    }

    @ExceptionHandler(WaitlistNotPermittedException.class)
    ResponseEntity<Map<String, Object>> handleWaitlistNotPermitted(WaitlistNotPermittedException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "WaitlistNotPermitted", ex.getMessage(), req);
    }

    @ExceptionHandler(WaitlistEntryNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleWaitlistEntryNotFound(WaitlistEntryNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "WaitlistEntryNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(AttendanceNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleAttendanceNotFound(AttendanceNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "AttendanceNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(SessionNotAttendableException.class)
    ResponseEntity<Map<String, Object>> handleSessionNotAttendable(SessionNotAttendableException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "SessionNotAttendable", ex.getMessage(), req);
    }

    @ExceptionHandler(MembershipPlanNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleMembershipPlanNotFound(MembershipPlanNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "MembershipPlanNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(MembershipPlanInUseException.class)
    ResponseEntity<Map<String, Object>> handleMembershipPlanInUse(MembershipPlanInUseException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "MembershipPlanInUse", ex.getMessage(), req);
    }

    @ExceptionHandler(MembershipPlanInactiveException.class)
    ResponseEntity<Map<String, Object>> handleMembershipPlanInactive(MembershipPlanInactiveException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "MembershipPlanInactive", ex.getMessage(), req);
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleSubscriptionNotFound(SubscriptionNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "SubscriptionNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(SubscriptionAlreadyActiveException.class)
    ResponseEntity<Map<String, Object>> handleSubscriptionAlreadyActive(SubscriptionAlreadyActiveException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "SubscriptionAlreadyActive", ex.getMessage(), req);
    }

    @ExceptionHandler(SubscriptionNotActiveException.class)
    ResponseEntity<Map<String, Object>> handleSubscriptionNotActive(SubscriptionNotActiveException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "SubscriptionNotActive", ex.getMessage(), req);
    }

    @ExceptionHandler(SubscriptionCancellationPendingException.class)
    ResponseEntity<Map<String, Object>> handleSubscriptionCancellationPending(SubscriptionCancellationPendingException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "SubscriptionCancellationPending", ex.getMessage(), req);
    }

    @ExceptionHandler(NoActiveSubscriptionException.class)
    ResponseEntity<Map<String, Object>> handleNoActiveSubscription(NoActiveSubscriptionException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "NoActiveSubscription", ex.getMessage(), req);
    }

    @ExceptionHandler(MonthlyClassLimitReachedException.class)
    ResponseEntity<Map<String, Object>> handleMonthlyClassLimitReached(MonthlyClassLimitReachedException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "MonthlyClassLimitReached", ex.getMessage(), req);
    }

    @ExceptionHandler(GymNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleGymNotFound(GymNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "GymNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(GymNameAlreadyExistsException.class)
    ResponseEntity<Map<String, Object>> handleGymNameAlreadyExists(GymNameAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "GymNameAlreadyExists", ex.getMessage(), req);
    }

    @ExceptionHandler(ClassTypeNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleClassTypeNotFound(ClassTypeNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "ClassTypeNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(ClassTypeNameAlreadyExistsException.class)
    ResponseEntity<Map<String, Object>> handleClassTypeNameAlreadyExists(
            ClassTypeNameAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "ClassTypeNameAlreadyExists", ex.getMessage(), req);
    }

    @ExceptionHandler(ClassTypeInUseException.class)
    ResponseEntity<Map<String, Object>> handleClassTypeInUse(ClassTypeInUseException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "ClassTypeInUse", ex.getMessage(), req);
    }

    @ExceptionHandler(InstructorNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleInstructorNotFound(InstructorNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "InstructorNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(SpecialtyNotAllowedException.class)
    ResponseEntity<Map<String, Object>> handleSpecialtyNotAllowed(SpecialtyNotAllowedException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "SpecialtyNotAllowed", ex.getMessage(), req);
    }

    @ExceptionHandler(RatingNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleRatingNotFound(RatingNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "RatingNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(RatingAlreadyExistsException.class)
    ResponseEntity<Map<String, Object>> handleRatingAlreadyExists(RatingAlreadyExistsException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "RatingAlreadyExists", ex.getMessage(), req);
    }

    @ExceptionHandler(NotAttendedSessionException.class)
    ResponseEntity<Map<String, Object>> handleNotAttendedSession(NotAttendedSessionException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "NotAttendedSession", ex.getMessage(), req);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<Map<String, Object>> handleNotificationNotFound(NotificationNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "NotificationNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(PaymentMethodNotFoundException.class)
    ResponseEntity<Map<String, Object>> handlePaymentMethodNotFound(PaymentMethodNotFoundException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "PaymentMethodNotFound", ex.getMessage(), req);
    }

    @ExceptionHandler(CardExpiredException.class)
    ResponseEntity<Map<String, Object>> handleCardExpired(CardExpiredException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "CardExpired", ex.getMessage(), req);
    }

    @ExceptionHandler(DuplicatePaymentMethodException.class)
    ResponseEntity<Map<String, Object>> handleDuplicatePaymentMethod(DuplicatePaymentMethodException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "DuplicatePaymentMethod", ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidDefaultToggleException.class)
    ResponseEntity<Map<String, Object>> handleInvalidDefaultToggle(InvalidDefaultToggleException ex, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "InvalidDefaultToggle", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, "ValidationFailed", message, req);
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String error, String message, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", req.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
