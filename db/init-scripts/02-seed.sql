-- Datos de prueba en español.
-- Contraseña de todos los usuarios de prueba: "password"
-- Re-ejecutar: docker-compose down -v && docker-compose up -d

-- -------------------------------------------------------------------------
-- GIMNASIOS
-- -------------------------------------------------------------------------
INSERT INTO gym (name, address, city, phone, opening_hours, active) VALUES
    ('FitCenter Madrid Centro',       'Calle Gran Vía 45',                 'Madrid',    '911234567', 'Lun–Vie 07:00–22:00 · Sáb–Dom 09:00–20:00', true),
    ('GymPro Barcelona Eixample',     'Carrer del Consell de Cent 200',    'Barcelona', '931234567', 'Lun–Vie 06:30–23:00 · Sáb–Dom 08:00–21:00', true),
    ('SportLife Sevilla',             'Avenida de la Constitución 12',     'Sevilla',   '951234567', 'Lun–Vie 07:00–22:00 · Sáb 09:00–15:00',     true),
    ('Wellness Valencia Norte',       'Calle Colón 33',                    'Valencia',  '961234567', 'Lun–Dom 07:00–23:00',                        true),
    ('PowerGym Bilbao',               'Gran Vía Diego López de Haro 45',   'Bilbao',    '944123456', 'Lun–Vie 07:00–22:00 · Sáb 09:00–18:00',     true),
    ('AquaFit Málaga',                'Paseo del Parque 8',                'Málaga',    '952345678', 'Lun–Dom 08:00–22:00',                        true);

-- -------------------------------------------------------------------------
-- PLANES DE MEMBRESÍA
-- -------------------------------------------------------------------------
INSERT INTO membership_plan (name, description, price_monthly, classes_per_month, allows_waitlist, active, duration_months) VALUES
    ('Plan Básico',
     'Acceso a 8 clases al mes. Ideal para quienes se inician en el fitness y quieren establecer una rutina semanal de dos días.',
     29.99, 8, false, true, 1),

    ('Plan Estándar',
     'Acceso a 16 clases al mes. El plan más popular: combina spinning, yoga y musculación con total flexibilidad de horario.',
     39.99, 16, true, true, 1),

    ('Plan Premium',
     'Acceso ilimitado a todas las clases y actividades. Incluye reserva en lista de espera, vestuario premium y toalla incluida.',
     54.99, NULL, true, true, 1),

    ('Plan Estudiante',
     'Tarifa especial universitaria con descuento del 35 %. Acceso a 6 clases al mes. Se requiere carné de estudiante vigente.',
     19.99, 6, false, true, 1),

    ('Plan Trimestral Plus',
     'Tres meses de acceso ilimitado con un ahorro del 15 % respecto a contratar tres meses por separado. Ideal para objetivos a medio plazo.',
     139.99, NULL, true, true, 3),

    ('Plan Familiar',
     'Hasta 4 miembros de la misma unidad familiar. Cada miembro disfruta de 12 clases mensuales. Gestión centralizada desde una sola cuenta.',
     89.99, 12, true, true, 1);

-- -------------------------------------------------------------------------
-- TIPOS DE CLASE
-- -------------------------------------------------------------------------
INSERT INTO class_type (name, description, level) VALUES
    ('Spinning 45 min',   'Ciclismo indoor de alta intensidad con música motivacional. Quema hasta 600 kcal por sesión.',          'INTERMEDIATE'),
    ('Yoga Restaurativo',  'Sesión suave de yoga enfocada en la relajación profunda y la flexibilidad. Apta para todos los niveles.', 'BASIC'),
    ('CrossFit Express',   'Entrenamiento funcional de 30 minutos con movimientos olímpicos y alta intensidad.',                    'ADVANCED'),
    ('Pilates Mat',        'Trabajo de fuerza del core y corrección postural sobre colchoneta. Sin material adicional.',            'BASIC'),
    ('Body Pump',          'Tonificación muscular con barra y discos. Ideal para definición y resistencia muscular.',               'INTERMEDIATE'),
    ('Zumba Fitness',      'Aeróbic latino de alta energía combinando salsa, merengue y reggaeton. Divertida y efectiva.',          'BASIC'),
    ('HIIT Funcional',     'Circuitos de alta intensidad con intervalos de trabajo y descanso. Máxima quema calórica en 45 min.',   'ADVANCED'),
    ('Aquagym',            'Ejercicio en el agua de bajo impacto articular. Perfecta para rehabilitación y mantenimiento.',         'BASIC');

-- -------------------------------------------------------------------------
-- USUARIOS (instructores + clientes)
-- -------------------------------------------------------------------------
INSERT INTO app_user (name, email, phone, password_hash, role, active, specialty) VALUES
    -- Instructores
    ('Laura Jiménez Morales',  'laura.jimenez@gymbook.test',   '600111001', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'INSTRUCTOR', true, 'Spinning y HIIT'),
    ('Marcos Delgado Vega',    'marcos.delgado@gymbook.test',  '600111002', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'INSTRUCTOR', true, 'Yoga y Pilates'),
    ('Sofía Castro Blanco',    'sofia.castro@gymbook.test',    '600111003', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'INSTRUCTOR', true, 'CrossFit y Funcional'),
    ('Rafael Ortega Prieto',   'rafael.ortega@gymbook.test',   '600111004', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'INSTRUCTOR', true, 'Body Pump y Zumba'),
    -- Admin
    ('Admin GymBook',          'admin@gymbook.test',           '600000001', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'ADMIN',      true, NULL),
    -- Clientes
    ('Carlos García López',    'carlos.garcia@gymbook.test',   '612345678', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL),
    ('María Fernández Ruiz',   'maria.fernandez@gymbook.test', '623456789', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL),
    ('Juan Martínez Sánchez',  'juan.martinez@gymbook.test',   '634567890', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL),
    ('Ana López González',     'ana.lopez@gymbook.test',       '645678901', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL),
    ('Pedro Romero Navarro',   'pedro.romero@gymbook.test',    '656789012', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL),
    ('Isabel Torres Molina',   'isabel.torres@gymbook.test',   '667890123', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL),
    ('David Ruiz Herrera',     'david.ruiz@gymbook.test',      '678901234', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL),
    ('Lucía Moreno Castillo',  'lucia.moreno@gymbook.test',    '689012345', '$2a$10$N.zmdr9zkoa05OY3Pom9mehCOvWjzTvEKQq5JHfKPsKvKBkHBxWma', 'CUSTOMER',   true, NULL);

-- -------------------------------------------------------------------------
-- SUSCRIPCIONES
-- Cada cliente activo suscrito a un plan en un gimnasio.
-- Subconsultas para no depender de IDs concretos.
-- -------------------------------------------------------------------------
INSERT INTO subscription (user_id, plan_id, gym_id, status, start_date, renewal_date, classes_used_this_month)
SELECT u.id, mp.id, g.id, 'ACTIVE', CURRENT_DATE, CURRENT_DATE + (mp.duration_months * INTERVAL '1 month'), 0
FROM (VALUES
    ('carlos.garcia@gymbook.test',   'Plan Estándar',       'FitCenter Madrid Centro'),
    ('maria.fernandez@gymbook.test', 'Plan Premium',        'GymPro Barcelona Eixample'),
    ('juan.martinez@gymbook.test',   'Plan Básico',         'FitCenter Madrid Centro'),
    ('ana.lopez@gymbook.test',       'Plan Trimestral Plus','SportLife Sevilla'),
    ('pedro.romero@gymbook.test',    'Plan Estudiante',     'Wellness Valencia Norte'),
    ('isabel.torres@gymbook.test',   'Plan Estándar',       'PowerGym Bilbao'),
    ('david.ruiz@gymbook.test',      'Plan Premium',        'AquaFit Málaga'),
    ('lucia.moreno@gymbook.test',    'Plan Familiar',       'FitCenter Madrid Centro')
) AS seed(email, plan_name, gym_name)
JOIN app_user      u  ON u.email     = seed.email
JOIN membership_plan mp ON mp.name  = seed.plan_name
JOIN gym           g  ON g.name     = seed.gym_name;
