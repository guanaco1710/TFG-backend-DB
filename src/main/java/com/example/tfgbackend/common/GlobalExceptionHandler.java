package com.example.tfgbackend.common;

import com.example.tfgbackend.common.exception.AlreadyBookedException;
import com.example.tfgbackend.common.exception.AlreadyOnWaitlistException;
import com.example.tfgbackend.common.exception.BookingAlreadyCancelledException;
import com.example.tfgbackend.common.exception.BookingNotFoundException;
import com.example.tfgbackend.common.exception.ClassFullException;
import com.example.tfgbackend.common.exception.EmailAlreadyExistsException;
import com.example.tfgbackend.common.exception.InvalidCredentialsException;
import com.example.tfgbackend.common.exception.InvalidResetTokenException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotBookableException;
import com.example.tfgbackend.common.exception.TokenExpiredException;
import com.example.tfgbackend.common.exception.TokenRevokedException;
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
