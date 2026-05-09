-- Очищення та скидання лічильників
TRUNCATE workout_athletes, workouts, locations, athletes, coaches, clubs RESTART IDENTITY CASCADE;

-- 1. Клуби
INSERT INTO clubs (name, description) VALUES
                                          ('Академія Елітного Спорту', 'База для професійної підготовки'),
                                          ('Київський Тенісний Центр', 'Найкращі корти столиці');

-- 2. Тренери
INSERT INTO coaches (name, specialization, club_id) VALUES
                                                        ('Олександр Зінченко', 'Футбол / Витривалість', (SELECT id FROM clubs WHERE name = 'Академія Елітного Спорту')),
                                                        ('Олена Ковальчук', 'Теніс / Техніка', (SELECT id FROM clubs WHERE name = 'Київський Тенісний Центр'));

-- 3. Атлети
INSERT INTO athletes (name, description, coach_id) VALUES
                                                       ('Дмитро Білик', 'Фокус на Java та марафони', (SELECT id FROM coaches WHERE name = 'Олександр Зінченко')),
                                                       ('Максим Шевченко', 'Силові інтервали', (SELECT id FROM coaches WHERE name = 'Олександр Зінченко')),
                                                       ('Світлана Іваненко', 'Юніорський теніс', (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'));

-- 4. Локації
INSERT INTO locations (name, description, color, coach_id) VALUES
                                                               ('Стадіон "Динамо"', 'Бігові доріжки', '#ef4444', (SELECT id FROM coaches WHERE name = 'Олександр Зінченко')),
                                                               ('Тенісний зал', 'Критий корт', '#22c55e', (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'));

-- 5. Тренування (Workouts)
-- Назви колонок: workout_date, start_time
INSERT INTO workouts (title, description, workout_date, start_time, end_time, coach_id, location_id) VALUES
                                                                                                         ('Ранковий забіг', 'Темповий біг 10км', CURRENT_DATE, '08:00:00', '09:00:00',
                                                                                                          (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
                                                                                                          (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),

                                                                                                         ('Тенісний інтенсив', 'Відпрацювання подачі', CURRENT_DATE, '11:00:00', '12:00:00',
                                                                                                          (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
                                                                                                          (SELECT id FROM locations WHERE name = 'Тенісний зал'));

-- 6. Зв'язки атлетів
INSERT INTO workout_athletes (workout_id, athlete_id) VALUES
                                                          ((SELECT id FROM workouts WHERE title = 'Ранковий забіг'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик')),
                                                          ((SELECT id FROM workouts WHERE title = 'Тенісний інтенсив'), (SELECT id FROM athletes WHERE name = 'Світлана Іваненко'));