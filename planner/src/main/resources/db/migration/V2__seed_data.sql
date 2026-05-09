-- Клуби
INSERT INTO clubs (name, description) VALUES
    ('Академія Елітного Спорту', 'База для професійної підготовки'),
    ('Київський Тенісний Центр', 'Найкращі корти столиці');

-- Тренери
INSERT INTO coaches (name, specialization, club_id) VALUES
    ('Олександр Зінченко', 'Футбол / Витривалість', (SELECT id FROM clubs WHERE name = 'Академія Елітного Спорту')),
    ('Олена Ковальчук', 'Теніс / Техніка', (SELECT id FROM clubs WHERE name = 'Київський Тенісний Центр'));

-- Атлети
INSERT INTO athletes (name, description, coach_id) VALUES
    ('Дмитро Білик', 'Фокус на Java та марафони', (SELECT id FROM coaches WHERE name = 'Олександр Зінченко')),
    ('Максим Шевченко', 'Силові інтервали', (SELECT id FROM coaches WHERE name = 'Олександр Зінченко')),
    ('Світлана Іваненко', 'Юніорський теніс', (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'));

-- Локації
INSERT INTO locations (name, description, color, coach_id) VALUES
    ('Стадіон "Динамо"', 'Бігові доріжки', '#ef4444', (SELECT id FROM coaches WHERE name = 'Олександр Зінченко')),
    ('Тенісний зал', 'Критий корт', '#22c55e', (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'));

-- Тренування (Workouts)
INSERT INTO workouts (title, description, workout_date, start_time, end_time, coach_id, location_id) VALUES
    -- Сьогодні
    ('Ранковий забіг', 'Темповий біг 10 км', CURRENT_DATE, '08:00:00', '09:00:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),
    ('Тенісний інтенсив', 'Відпрацювання подачі та прийому', CURRENT_DATE, '11:00:00', '12:30:00',
     (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
     (SELECT id FROM locations WHERE name = 'Тенісний зал')),

    -- Завтра
    ('Футбольна розминка', 'Легкий біг + stretch', CURRENT_DATE + 1, '07:30:00', '08:30:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),
    ('Теніс — техніка удару', 'Форхенд і бекхенд', CURRENT_DATE + 1, '10:00:00', '11:30:00',
     (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
     (SELECT id FROM locations WHERE name = 'Тенісний зал')),
    ('Вечірній теніс', 'Гра на час', CURRENT_DATE + 1, '16:00:00', '17:00:00',
     (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
     (SELECT id FROM locations WHERE name = 'Тенісний зал')),

    -- Через 2 дні
    ('Інтервальний біг', '5 × 1000 м з відпочинком', CURRENT_DATE + 2, '08:00:00', '09:15:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),
    ('Теніс — фізика', 'Швидкісні вправи на корті', CURRENT_DATE + 2, '11:00:00', '12:00:00',
     (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
     (SELECT id FROM locations WHERE name = 'Тенісний зал')),

    -- Через 3 дні
    ('Силова підготовка', 'Коло: 8 вправ × 40 сек', CURRENT_DATE + 3, '08:00:00', '09:00:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),
    ('Спарринг-матч', 'Товариська гра', CURRENT_DATE + 3, '10:00:00', '11:30:00',
     (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
     (SELECT id FROM locations WHERE name = 'Тенісний зал')),
    ('Тактика футбол', 'Позиційна гра в малих групах', CURRENT_DATE + 3, '17:00:00', '18:00:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),

    -- Через 4 дні
    ('Ранковий крос', '5 км у спокійному темпі', CURRENT_DATE + 4, '07:00:00', '07:45:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),
    ('Теніс — подача', 'Серія подач у квадрати', CURRENT_DATE + 4, '10:00:00', '11:00:00',
     (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
     (SELECT id FROM locations WHERE name = 'Тенісний зал')),

    -- Через 5 днів
    ('Футбольний матч', 'Товариська зустріч', CURRENT_DATE + 5, '09:00:00', '10:30:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"')),
    ('Теніс — турнір', 'Імітація турнірного дня', CURRENT_DATE + 5, '11:00:00', '13:00:00',
     (SELECT id FROM coaches WHERE name = 'Олена Ковальчук'),
     (SELECT id FROM locations WHERE name = 'Тенісний зал')),

    -- Через 6 днів
    ('Відновлення', 'Розтяжка + дихальні вправи', CURRENT_DATE + 6, '09:00:00', '09:30:00',
     (SELECT id FROM coaches WHERE name = 'Олександр Зінченко'),
     (SELECT id FROM locations WHERE name = 'Стадіон "Динамо"'));

-- Зв'язки атлетів з тренуваннями
INSERT INTO workout_athletes (workout_id, athlete_id) VALUES
    -- Сьогодні
    ((SELECT id FROM workouts WHERE title = 'Ранковий забіг'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик')),
    ((SELECT id FROM workouts WHERE title = 'Ранковий забіг'), (SELECT id FROM athletes WHERE name = 'Максим Шевченко')),
    ((SELECT id FROM workouts WHERE title = 'Тенісний інтенсив'), (SELECT id FROM athletes WHERE name = 'Світлана Іваненко')),

    -- Завтра
    ((SELECT id FROM workouts WHERE title = 'Футбольна розминка'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик')),
    ((SELECT id FROM workouts WHERE title = 'Футбольна розминка'), (SELECT id FROM athletes WHERE name = 'Максим Шевченко')),
    ((SELECT id FROM workouts WHERE title = 'Теніс — техніка удару'), (SELECT id FROM athletes WHERE name = 'Світлана Іваненко')),
    ((SELECT id FROM workouts WHERE title = 'Вечірній теніс'), (SELECT id FROM athletes WHERE name = 'Світлана Іваненко')),

    -- Через 2 дні
    ((SELECT id FROM workouts WHERE title = 'Інтервальний біг'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик')),
    ((SELECT id FROM workouts WHERE title = 'Теніс — фізика'), (SELECT id FROM athletes WHERE name = 'Світлана Іваненко')),

    -- Через 3 дні
    ((SELECT id FROM workouts WHERE title = 'Силова підготовка'), (SELECT id FROM athletes WHERE name = 'Максим Шевченко')),
    ((SELECT id FROM workouts WHERE title = 'Спарринг-матч'), (SELECT id FROM athletes WHERE name = 'Світлана Іваненко')),
    ((SELECT id FROM workouts WHERE title = 'Тактика футбол'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик')),
    ((SELECT id FROM workouts WHERE title = 'Тактика футбол'), (SELECT id FROM athletes WHERE name = 'Максим Шевченко')),

    -- Через 4 дні
    ((SELECT id FROM workouts WHERE title = 'Ранковий крос'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик')),
    ((SELECT id FROM workouts WHERE title = 'Теніс — подача'), (SELECT id FROM athletes WHERE name = 'Світлана Іваненко')),

    -- Через 5 днів
    ((SELECT id FROM workouts WHERE title = 'Футбольний матч'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик')),
    ((SELECT id FROM workouts WHERE title = 'Футбольний матч'), (SELECT id FROM athletes WHERE name = 'Максим Шевченко')),

    -- Через 6 днів
    ((SELECT id FROM workouts WHERE title = 'Відновлення'), (SELECT id FROM athletes WHERE name = 'Дмитро Білик'));
