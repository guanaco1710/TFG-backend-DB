package com.example.tfgbackend.rating;

import com.example.tfgbackend.booking.BookingRepository;
import com.example.tfgbackend.classsession.ClassSession;
import com.example.tfgbackend.classsession.ClassSessionRepository;
import com.example.tfgbackend.common.PageResponse;
import com.example.tfgbackend.common.exception.NotAttendedSessionException;
import com.example.tfgbackend.common.exception.RatingAlreadyExistsException;
import com.example.tfgbackend.common.exception.RatingNotFoundException;
import com.example.tfgbackend.common.exception.SessionNotFoundException;
import com.example.tfgbackend.common.exception.UserNotFoundException;
import com.example.tfgbackend.enums.BookingStatus;
import com.example.tfgbackend.enums.UserRole;
import com.example.tfgbackend.rating.dto.CreateRatingRequest;
import com.example.tfgbackend.rating.dto.RatingResponse;
import com.example.tfgbackend.rating.dto.UpdateRatingRequest;
import com.example.tfgbackend.user.User;
import com.example.tfgbackend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingService {

    private final RatingRepository ratingRepository;
    private final ClassSessionRepository classSessionRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public RatingResponse create(Long callerId, CreateRatingRequest request) {
        // 1. Session must exist
        ClassSession session = classSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new SessionNotFoundException(request.sessionId()));
        // 2. User must have an ATTENDED booking for this session
        if (!bookingRepository.existsByUserIdAndSessionIdAndStatus(callerId, request.sessionId(), BookingStatus.ATTENDED)) {
            throw new NotAttendedSessionException(callerId, request.sessionId());
        }
        // 3. No duplicate
        if (ratingRepository.findByUserIdAndSessionId(callerId, request.sessionId()).isPresent()) {
            throw new RatingAlreadyExistsException(callerId, request.sessionId());
        }
        User user = userRepository.findById(callerId)
                .orElseThrow(() -> new UserNotFoundException(callerId));
        Rating rating = Rating.builder()
                .score(request.score())
                .comment(request.comment())
                .user(user)
                .session(session)
                .build();
        return toResponse(ratingRepository.save(rating));
    }

    @Transactional
    public RatingResponse update(Long callerId, Long ratingId, UpdateRatingRequest request) {
        Rating rating = ratingRepository.findById(ratingId)
                .filter(r -> r.getUser().getId().equals(callerId))
                .orElseThrow(() -> new RatingNotFoundException(ratingId));
        rating.setScore(request.score());
        rating.setComment(request.comment());
        rating.setRatedAt(Instant.now());
        return toResponse(ratingRepository.save(rating));
    }

    @Transactional
    public void delete(Long callerId, UserRole callerRole, Long ratingId) {
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new RatingNotFoundException(ratingId));
        // Treat as not-found for non-owners to avoid leaking existence
        if (callerRole != UserRole.ADMIN && !rating.getUser().getId().equals(callerId)) {
            throw new RatingNotFoundException(ratingId);
        }
        ratingRepository.delete(rating);
    }

    public PageResponse<RatingResponse> listBySession(Long sessionId, Pageable pageable) {
        if (!classSessionRepository.existsById(sessionId)) {
            throw new SessionNotFoundException(sessionId);
        }
        return PageResponse.of(ratingRepository.findBySessionId(sessionId, pageable).map(this::toResponse));
    }

    public PageResponse<RatingResponse> myRatings(Long callerId, Pageable pageable) {
        return PageResponse.of(ratingRepository.findByUserId(callerId, pageable).map(this::toResponse));
    }

    private RatingResponse toResponse(Rating rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getScore(),
                rating.getComment(),
                rating.getRatedAt(),
                rating.getUser().getId(),
                rating.getSession().getId()
        );
    }
}
