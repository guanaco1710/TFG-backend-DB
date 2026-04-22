package com.example.tfgbackend.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;

@Slf4j
@Component
@Profile("h2")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        log.info("Seeding H2 database with sample data...");

        jdbc.execute("""
            INSERT INTO class_type (name, description, level) VALUES
              ('Spinning', 'High-intensity cycling class', 'INTERMEDIATE'),
              ('Yoga', 'Flexibility and relaxation', 'BASIC'),
              ('CrossFit', 'Functional strength and conditioning', 'ADVANCED'),
              ('Pilates', 'Core strength and posture', 'BASIC'),
              ('HIIT', 'High-intensity interval training', 'INTERMEDIATE')
            """);

        jdbc.execute("""
            INSERT INTO instructor (name, specialty) VALUES
              ('Jorge Martínez', 'Spinning'),
              ('Elena García', 'Yoga'),
              ('Marc Puig', 'CrossFit'),
              ('Laura Sánchez', 'Pilates')
            """);

        jdbc.execute("""
            INSERT INTO app_user (name, email, phone, password_hash, role, active) VALUES
              ('Alice Smith',   'alice@gymbook.com',   '600111001', '$2a$10$placeholder1', 'CUSTOMER',   true),
              ('Bob Jones',     'bob@gymbook.com',     '600111002', '$2a$10$placeholder2', 'CUSTOMER',   true),
              ('Carol White',   'carol@gymbook.com',   '600111003', '$2a$10$placeholder3', 'CUSTOMER',   true),
              ('Admin User',    'admin@gymbook.com',   '600000000', '$2a$10$placeholder4', 'ADMIN',      true),
              ('Jorge Martínez','jorge@gymbook.com',   '600222001', '$2a$10$placeholder5', 'INSTRUCTOR', true)
            """);

        jdbc.execute("""
            INSERT INTO class_session (start_time, duration_minutes, max_capacity, room, status, class_type_id, instructor_id) VALUES
              (NOW() + INTERVAL '1' DAY,     45, 12, '1A', 'SCHEDULED', 1, 1),
              (NOW() + INTERVAL '1' DAY,     60,  8, '2B', 'SCHEDULED', 2, 2),
              (NOW() + INTERVAL '2' DAY,     60, 15, '1A', 'SCHEDULED', 3, 3),
              (NOW() + INTERVAL '2' DAY,     50, 10, '2A', 'SCHEDULED', 4, 4),
              (NOW() + INTERVAL '3' DAY,     45, 12, '1A', 'SCHEDULED', 5, 1),
              (NOW() - INTERVAL '1' DAY,     45, 12, '1A', 'FINISHED',  1, 1),
              (NOW() - INTERVAL '2' DAY,     60,  8, '2B', 'FINISHED',  2, 2)
            """);

        jdbc.execute("""
            INSERT INTO booking (booked_at, status, user_id, session_id) VALUES
              (NOW(), 'CONFIRMED', 1, 1),
              (NOW(), 'CONFIRMED', 2, 1),
              (NOW(), 'CONFIRMED', 1, 2),
              (NOW(), 'ATTENDED',  1, 6),
              (NOW(), 'ATTENDED',  2, 6),
              (NOW(), 'CANCELLED', 3, 7)
            """);

        jdbc.execute("""
            INSERT INTO waitlist (position, joined_at, user_id, session_id) VALUES
              (1, NOW(), 3, 1)
            """);

        jdbc.execute("""
            INSERT INTO notification (type, scheduled_at, sent, user_id, session_id) VALUES
              ('CONFIRMATION', NOW(),                        true,  1, 1),
              ('REMINDER',     NOW() + INTERVAL '22' HOUR,  false, 1, 1),
              ('CONFIRMATION', NOW(),                        true,  2, 1),
              ('REMINDER',     NOW() + INTERVAL '22' HOUR,  false, 2, 1)
            """);

        jdbc.execute("""
            INSERT INTO rating (score, comment, rated_at, user_id, session_id) VALUES
              (5, 'Amazing class, Jorge is the best!', NOW(), 1, 6),
              (4, 'Great energy', NOW(), 2, 6)
            """);

        log.info("Sample data loaded: 5 class types, 4 instructors, 5 users, 7 sessions, 6 bookings, 1 waitlist, 4 notifications, 2 ratings.");
    }
}
