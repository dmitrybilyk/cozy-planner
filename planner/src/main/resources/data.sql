-- Очищення та скидання лічильників
TRUNCATE session_trainees, sessions, locations, trainees, mentors, clubs RESTART IDENTITY CASCADE;

-- 1. Клуби
INSERT INTO clubs (name, description) VALUES
                                           ('Академія Елітного Спорту', 'База для професійної підготовки'),
                                           ('Київський Тенісний Центр', 'Найкращі корти столиці');

-- 2. Ментори
INSERT INTO mentors (name, specialization, club_id) VALUES
                                                         ('Олександр Зінченко', 'Футбол / Витривалість', (SELECT id FROM clubs WHERE name = 'Академія Елітного Спорту')),
                                                         ('Олена Ковальчук', 'Теніс / Техніка', (SELECT id FROM clubs WHERE name = 'Київський Тенісний Центр'));

-- 3. Треновані
INSERT INTO trainees (name, description, mentor_id) VALUES
                                                        ('Дмитро Білик', 'Фокус на Java та марафони', (SELECT id FROM mentors WHERE name = 'Олександр Зінченко')),
                                                        ('Максим Шевченко', 'Силові інтервали', (SELECT id FROM mentors WHERE name = 'Олександр Зінченко')),
                                                        ('Світлана Іваненко', 'Юніорський теніс', (SELECT id FROM mentors WHERE name = 'Олена Ковальчук'));

-- 4. Локації
INSERT INTO locations (name, description, color, mentor_id) VALUES
                                                                ('Стадіон "Динамо"', 'Бігові доріжки', '#ef4444', (SELECT id FROM mentors WHERE name = 'Олександр Зінченко')),
                                                                ('Тенісний зал', 'Критий корт', '#22c55e', (SELECT id FROM mentors WHERE name = 'Олена Ковальчук'));

-- 5. Сесії
-- Назви колонок: session_date, start_time
INSERT INTO sessions (title, description, session_date, start_time, end_time, mentor_id, location_id) VALUES
                                                                                                          ('Ранковий забіг', 'Темповий біг 10км', CURRENT_DATE, '08:00:00', '09:00:00',
                                                                                                           (SELECT id FROM mentors WHERE name = 'Олександр Зінченко'),
                                                                                                           (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),

                                                                                                          ('Тенісний інтенсив', 'Відпрацювання подачі', CURRENT_DATE, '11:00:00', '12:00:00',
                                                                                                           (SELECT id FROM mentors WHERE name = 'Олена Ковальчук'),
                                                                                                           (SELECT id FROM locations WHERE name = 'Тенісний зал'));

-- 6. Зв'язки тренованих
INSERT INTO session_trainees (session_id, trainee_id) VALUES
                                                           ((SELECT id FROM sessions WHERE title = 'Ранковий забіг'), (SELECT id FROM trainees WHERE name = 'Дмитро Білик')),
                                                           ((SELECT id FROM sessions WHERE title = 'Тенісний інтенсив'), (SELECT id FROM trainees WHERE name = 'Світлана Іваненко'));
