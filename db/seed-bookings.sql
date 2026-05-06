-- seed-bookings.sql
-- Adds class sessions (next 7 days, 2/day per gym) and CONFIRMED bookings.
-- Works with any active gyms currently in the DB.
--
-- Prerequisites: at least one active gym row must exist.
-- Idempotency:
--   - Class types and users: ON CONFLICT (email/name) DO NOTHING.
--   - Sessions: NOT idempotent — run once or on a clean slate.
--   - Bookings: ON CONFLICT (user_id, session_id) DO NOTHING.
-- Password for all seeded users: "password"

BEGIN;

-- -------------------------------------------------------------------------
-- 1. Class types (safe to re-run)
-- -------------------------------------------------------------------------
INSERT INTO class_type (name, description, level) VALUES
    ('Spinning 45 min',   'Ciclismo indoor de alta intensidad. Quema hasta 600 kcal.',         'INTERMEDIATE'),
    ('Yoga Restaurativo',  'Sesión suave de yoga enfocada en relajación y flexibilidad.',       'BASIC'),
    ('CrossFit Express',   'Entrenamiento funcional de 30 min con movimientos olímpicos.',      'ADVANCED'),
    ('Pilates Mat',        'Trabajo de fuerza del core y corrección postural sobre colchoneta.','BASIC'),
    ('Body Pump',          'Tonificación muscular con barra y discos.',                         'INTERMEDIATE'),
    ('Zumba Fitness',      'Aeróbic latino de alta energía: salsa, merengue y reggaeton.',      'BASIC'),
    ('HIIT Funcional',     'Circuitos de alta intensidad con intervalos de trabajo y descanso.','ADVANCED'),
    ('Aquagym',            'Ejercicio en el agua de bajo impacto articular.',                   'BASIC')
ON CONFLICT DO NOTHING;

-- -------------------------------------------------------------------------
-- 2. Instructor users (safe to re-run)
-- -------------------------------------------------------------------------
INSERT INTO app_user (name, email, phone, password_hash, role, active, specialty) VALUES
    ('Laura Jiménez Morales', 'laura.jimenez@gymbook.test',  '600111001',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma',
     'INSTRUCTOR', true, 'Spinning y HIIT'),
    ('Marcos Delgado Vega',   'marcos.delgado@gymbook.test', '600111002',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma',
     'INSTRUCTOR', true, 'Yoga y Pilates'),
    ('Sofía Castro Blanco',   'sofia.castro@gymbook.test',   '600111003',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma',
     'INSTRUCTOR', true, 'CrossFit y Funcional'),
    ('Rafael Ortega Prieto',  'rafael.ortega@gymbook.test',  '600111004',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma',
     'INSTRUCTOR', true, 'Body Pump y Zumba')
ON CONFLICT (email) DO NOTHING;

-- -------------------------------------------------------------------------
-- 3. Customer users (safe to re-run)
-- -------------------------------------------------------------------------
INSERT INTO app_user (name, email, phone, password_hash, role, active, specialty) VALUES
    ('Carlos García López',    'carlos.garcia@gymbook.test',   '612345678',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL),
    ('María Fernández Ruiz',   'maria.fernandez@gymbook.test', '623456789',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL),
    ('Juan Martínez Sánchez',  'juan.martinez@gymbook.test',   '634567890',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL),
    ('Ana López González',     'ana.lopez@gymbook.test',       '645678901',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL),
    ('Pedro Romero Navarro',   'pedro.romero@gymbook.test',    '656789012',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL),
    ('Isabel Torres Molina',   'isabel.torres@gymbook.test',   '667890123',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL),
    ('David Ruiz Herrera',     'david.ruiz@gymbook.test',      '678901234',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL),
    ('Lucía Moreno Castillo',  'lucia.moreno@gymbook.test',    '689012345',
     '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER', true, NULL)
ON CONFLICT (email) DO NOTHING;

-- -------------------------------------------------------------------------
-- 4. Sessions (2/day × 7 days = 14 per gym) + CONFIRMED bookings in one shot
--
-- Schedule rotates through class types and instructors.
-- Each session gets 4 customers assigned via a rotating offset so no session
-- has the exact same 4 attendees — creates a more realistic dataset.
-- -------------------------------------------------------------------------
WITH

new_sessions AS (
    INSERT INTO class_session
        (start_time, duration_minutes, max_capacity, room, status, class_type_id, instructor_id, gym_id)
    SELECT
        CURRENT_DATE::TIMESTAMPTZ + slot.day_offset + slot.hour_offset,
        slot.duration_minutes,
        slot.max_capacity,
        slot.room,
        'SCHEDULED',
        ct.id,
        instr.id,
        g.id
    FROM gym g
    CROSS JOIN (VALUES
        -- day 1
        (INTERVAL '1 day', INTERVAL '7 hours',      45, 15, '1A', 'Spinning 45 min',   'laura.jimenez@gymbook.test'),
        (INTERVAL '1 day', INTERVAL '19 hours',     60, 12, '2B', 'Yoga Restaurativo',  'marcos.delgado@gymbook.test'),
        -- day 2
        (INTERVAL '2 days', INTERVAL '8 hours',     30, 10, '3C', 'CrossFit Express',   'sofia.castro@gymbook.test'),
        (INTERVAL '2 days', INTERVAL '18 hours',    45, 15, '1A', 'Body Pump',           'rafael.ortega@gymbook.test'),
        -- day 3
        (INTERVAL '3 days', INTERVAL '7 hours',     45, 15, '1A', 'Spinning 45 min',    'laura.jimenez@gymbook.test'),
        (INTERVAL '3 days', INTERVAL '20 hours',    60, 20, '2B', 'Zumba Fitness',       'rafael.ortega@gymbook.test'),
        -- day 4
        (INTERVAL '4 days', INTERVAL '9 hours',     60, 12, '2B', 'Pilates Mat',         'marcos.delgado@gymbook.test'),
        (INTERVAL '4 days', INTERVAL '18 hours 30 minutes', 45, 10, '3C', 'HIIT Funcional', 'sofia.castro@gymbook.test'),
        -- day 5
        (INTERVAL '5 days', INTERVAL '7 hours 30 minutes',  45, 15, '1A', 'Spinning 45 min', 'laura.jimenez@gymbook.test'),
        (INTERVAL '5 days', INTERVAL '19 hours 30 minutes', 60, 12, '2B', 'Yoga Restaurativo', 'marcos.delgado@gymbook.test'),
        -- day 6
        (INTERVAL '6 days', INTERVAL '8 hours',     30, 10, '3C', 'CrossFit Express',   'sofia.castro@gymbook.test'),
        (INTERVAL '6 days', INTERVAL '18 hours',    45, 15, '1A', 'Body Pump',           'rafael.ortega@gymbook.test'),
        -- day 7
        (INTERVAL '7 days', INTERVAL '10 hours',    60, 20, '1A', 'Zumba Fitness',       'rafael.ortega@gymbook.test'),
        (INTERVAL '7 days', INTERVAL '11 hours 30 minutes', 60, 15, '2B', 'Yoga Restaurativo', 'marcos.delgado@gymbook.test')
    ) AS slot(day_offset, hour_offset, duration_minutes, max_capacity, room, class_type_name, instructor_email)
    JOIN      class_type ct   ON ct.name    = slot.class_type_name
    LEFT JOIN app_user   instr ON instr.email = slot.instructor_email
    WHERE g.active = true
    RETURNING id
),

-- Number sessions and customers for the rotating assignment
sessions_rn AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY id) - 1) AS rn
    FROM   new_sessions
),

customers AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY id) - 1) AS rn, COUNT(*) OVER () AS total
    FROM   app_user
    WHERE  role = 'CUSTOMER' AND active = true
)

-- Each session gets 4 customers; the set rotates by session index so different
-- sessions have different attendees (realistic fill pattern).
INSERT INTO booking (booked_at, status, user_id, session_id)
SELECT
    -- Spread booked_at across the past week for realistic history
    NOW() - ((s.rn % 7) || ' days')::INTERVAL - ((c.rn * 97) % 1440 || ' minutes')::INTERVAL,
    'CONFIRMED',
    c.id,
    s.id
FROM   sessions_rn s
JOIN   customers   c ON MOD(c.rn - s.rn + c.total, c.total) < 4
ON CONFLICT (user_id, session_id) DO NOTHING;

COMMIT;
