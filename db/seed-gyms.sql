-- Gym seed data for testing the browse-gym screen.
-- 30 rows → spans 2 pages at the default page size of 20.
-- Columns with server defaults (id, version, created_at, updated_at) are omitted.

INSERT INTO gym (name, address, city, phone, opening_hours, active) VALUES
  ('GymBook Central',         'Calle Mayor 1',              'Madrid',    '+34 91 000 0001', 'L-V 07:00-23:00, S-D 08:00-22:00', TRUE),
  ('GymBook Norte',           'Paseo de la Castellana 200', 'Madrid',    '+34 91 000 0002', 'L-V 06:30-23:00, S-D 09:00-21:00', TRUE),
  ('GymBook Retiro',          'Calle de Alcalá 155',        'Madrid',    '+34 91 000 0003', 'L-V 07:00-22:00, S 08:00-20:00',   TRUE),
  ('GymBook Vallecas',        'Avenida de la Albufera 50',  'Madrid',    NULL,              'L-V 08:00-22:00',                   TRUE),
  ('GymBook Chamberí',        'Calle de Bravo Murillo 12',  'Madrid',    '+34 91 000 0005', 'L-D 07:00-23:00',                   TRUE),

  ('GymBook Diagonal',        'Avinguda Diagonal 300',      'Barcelona', '+34 93 000 0001', 'L-V 07:00-23:00, S-D 08:00-21:00', TRUE),
  ('GymBook Gràcia',          'Carrer de Gràcia 45',        'Barcelona', '+34 93 000 0002', 'L-V 07:00-22:00, S 09:00-20:00',   TRUE),
  ('GymBook Barceloneta',     'Passeig Marítim 80',         'Barcelona', '+34 93 000 0003', 'L-D 06:00-22:00',                   TRUE),
  ('GymBook Sants',           'Carrer de Sants 112',        'Barcelona', NULL,              'L-V 07:30-22:30',                   TRUE),
  ('GymBook Sarrià',          'Carrer Major de Sarrià 20',  'Barcelona', '+34 93 000 0005', 'L-V 07:00-22:00, S 09:00-18:00',   TRUE),

  ('GymBook Mestalla',        'Carrer de Mestalla 10',      'Valencia',  '+34 96 000 0001', 'L-V 07:00-22:00, S-D 09:00-20:00', TRUE),
  ('GymBook Russafa',         'Carrer de Russafa 55',       'Valencia',  '+34 96 000 0002', 'L-V 08:00-22:00',                   TRUE),
  ('GymBook Benimaclet',      'Avinguda Primado Reig 100',  'Valencia',  NULL,              'L-V 07:30-21:30, S 09:00-14:00',   TRUE),

  ('GymBook Triana',          'Calle Betis 22',             'Sevilla',   '+34 95 000 0001', 'L-V 07:00-22:00, S-D 09:00-20:00', TRUE),
  ('GymBook Centro Sevilla',  'Avenida de la Constitución 5','Sevilla',  '+34 95 000 0002', 'L-V 08:00-22:00, S 09:00-18:00',   TRUE),
  ('GymBook Nervión',         'Calle Luis Montoto 80',      'Sevilla',   NULL,              'L-V 07:00-21:00',                   TRUE),

  ('GymBook Centro Bilbao',   'Gran Vía 30',                'Bilbao',    '+34 94 000 0001', 'L-V 07:00-22:00, S-D 09:00-20:00', TRUE),
  ('GymBook Indautxu',        'Calle Licenciado Poza 15',   'Bilbao',    '+34 94 000 0002', 'L-V 07:30-22:00, S 09:00-18:00',   TRUE),

  ('GymBook Albaicín',        'Calle Calderería Nueva 3',   'Granada',   '+34 95 800 0001', 'L-V 08:00-22:00',                   TRUE),
  ('GymBook Campus Granada',  'Avenida de la Investigación 1','Granada', NULL,              'L-V 07:00-23:00, S 08:00-20:00',   TRUE),

  ('GymBook Casco Viejo',     'Calle San Francisco 14',     'Zaragoza',  '+34 97 600 0001', 'L-V 07:00-22:00, S-D 09:00-21:00', TRUE),
  ('GymBook Delicias',        'Avenida de Madrid 150',      'Zaragoza',  NULL,              'L-V 08:00-22:00, S 09:00-14:00',   TRUE),

  ('GymBook Ensanche',        'Calle Larios 10',            'Málaga',    '+34 95 200 0001', 'L-V 07:00-22:00, S-D 09:00-20:00', TRUE),
  ('GymBook Pedregalejo',     'Paseo Marítimo El Palo 40',  'Málaga',    '+34 95 200 0002', 'L-D 07:00-22:00',                   TRUE),

  ('GymBook Centro Murcia',   'Gran Vía Escultor Salzillo 5','Murcia',   '+34 96 800 0001', 'L-V 07:30-22:00, S 09:00-18:00',   TRUE),

  ('GymBook Compostelana',    'Rúa do Franco 8',            'Santiago de Compostela', '+34 98 100 0001', 'L-V 07:00-22:00, S-D 09:00-20:00', TRUE),

  ('GymBook Alameda',         'Avenida de la Alameda 3',    'Salamanca', '+34 92 300 0001', 'L-V 08:00-22:00',                   TRUE),

  ('GymBook Puerto Banús',    'Muelle Ribera Local 12',     'Marbella',  '+34 95 200 0010', 'L-D 07:00-23:00',                   TRUE),

  -- Inactive gym to verify the app handles active=false correctly
  ('GymBook Cerrado',         'Calle Provisional 99',       'Madrid',    NULL,              NULL,                                FALSE);
