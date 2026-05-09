-- Клуб
INSERT INTO clubs (name, description) VALUES
    ('Спортивний Hub', 'Багатофункціональний спортивний простір');

-- Тренер
INSERT INTO coaches (name, specialization, club_id) VALUES
    ('Катя', 'Фітнес / Атлетика', (SELECT id FROM clubs WHERE name = 'Спортивний Hub'));

-- Атлети
INSERT INTO athletes (name, description, coach_id) VALUES
    ('Дмитро', 'Футбол / Витривалість', (SELECT id FROM coaches WHERE name = 'Катя')),
    ('Руслан', 'Силові тренування', (SELECT id FROM coaches WHERE name = 'Катя')),
    ('Оксана', 'Аеробіка / Тонус', (SELECT id FROM coaches WHERE name = 'Катя')),
    ('Андрій', 'Кросфіт / Спринт', (SELECT id FROM coaches WHERE name = 'Катя'));

-- Локації
INSERT INTO locations (name, description, color, coach_id) VALUES
    ('Левандівка', 'Відкритий тенісний корт', '#f59e0b', (SELECT id FROM coaches WHERE name = 'Катя')),
    ('Електрон', 'Критий тенісний корт', '#06b6d4', (SELECT id FROM coaches WHERE name = 'Катя'));

-- Тренування
INSERT INTO workouts (title, description, workout_date, start_time, end_time, coach_id, location_id) VALUES
    -- День 0 (сьогодні)
    ('Ранкова розминка', 'Розігрів + вправи на корті', CURRENT_DATE, '08:00:00', '09:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Левандівка')),
    ('Подача — серія', 'Відпрацювання першої та другої подач', CURRENT_DATE, '17:00:00', '18:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Електрон')),

    -- День +1
    ('Форхенд і бекхенд', 'Техніка удару з відскоку', CURRENT_DATE + 1, '08:00:00', '09:30:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Левандівка')),
    ('Спарринг', 'Гра на рахунок', CURRENT_DATE + 1, '17:00:00', '18:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Електрон')),

    -- День +2
    ('Удар з льоту', 'Сітка + завершення біля сітки', CURRENT_DATE + 2, '08:00:00', '09:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Левандівка')),
    ('Пересування кортом', 'Дрили на швидкість і координацію', CURRENT_DATE + 2, '17:00:00', '18:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Електрон')),

    -- День +3
    ('Тенісна техніка', 'Робота над обертанням мяча', CURRENT_DATE + 3, '08:00:00', '09:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Левандівка')),
    ('Матчева практика', 'Повноцінний сет', CURRENT_DATE + 3, '17:00:00', '18:30:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Електрон')),

    -- День +4
    ('Прийом подачі', 'Повернення + контратака', CURRENT_DATE + 4, '08:00:00', '09:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Левандівка')),
    ('Тактика гри', 'Розбір позицій і переміщень', CURRENT_DATE + 4, '17:00:00', '18:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Електрон')),

    -- День +5
    ('Смєш та свіч', 'Завершення біля сітки + захисні удари', CURRENT_DATE + 5, '08:00:00', '09:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Левандівка')),
    ('Турнірний день', 'Міні-турнір серед групи', CURRENT_DATE + 5, '17:00:00', '18:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Електрон')),

    -- День +6
    ('Відновлення', 'Легка гра + розтяжка', CURRENT_DATE + 6, '09:00:00', '10:00:00',
     (SELECT id FROM coaches WHERE name = 'Катя'),
     (SELECT id FROM locations WHERE name = 'Левандівка'));

-- Зв'язки атлетів з тренуваннями
INSERT INTO workout_athletes (workout_id, athlete_id) VALUES
    -- День 0
    ((SELECT id FROM workouts WHERE title = 'Ранкова розминка'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Ранкова розминка'), (SELECT id FROM athletes WHERE name = 'Руслан')),
    ((SELECT id FROM workouts WHERE title = 'Подача — серія'), (SELECT id FROM athletes WHERE name = 'Оксана')),
    ((SELECT id FROM workouts WHERE title = 'Подача — серія'), (SELECT id FROM athletes WHERE name = 'Андрій')),

    -- День +1
    ((SELECT id FROM workouts WHERE title = 'Форхенд і бекхенд'), (SELECT id FROM athletes WHERE name = 'Оксана')),
    ((SELECT id FROM workouts WHERE title = 'Форхенд і бекхенд'), (SELECT id FROM athletes WHERE name = 'Андрій')),
    ((SELECT id FROM workouts WHERE title = 'Спарринг'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Спарринг'), (SELECT id FROM athletes WHERE name = 'Руслан')),

    -- День +2
    ((SELECT id FROM workouts WHERE title = 'Удар з льоту'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Удар з льоту'), (SELECT id FROM athletes WHERE name = 'Руслан')),
    ((SELECT id FROM workouts WHERE title = 'Удар з льоту'), (SELECT id FROM athletes WHERE name = 'Оксана')),
    ((SELECT id FROM workouts WHERE title = 'Удар з льоту'), (SELECT id FROM athletes WHERE name = 'Андрій')),
    ((SELECT id FROM workouts WHERE title = 'Пересування кортом'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Пересування кортом'), (SELECT id FROM athletes WHERE name = 'Руслан')),

    -- День +3
    ((SELECT id FROM workouts WHERE title = 'Тенісна техніка'), (SELECT id FROM athletes WHERE name = 'Оксана')),
    ((SELECT id FROM workouts WHERE title = 'Матчева практика'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Матчева практика'), (SELECT id FROM athletes WHERE name = 'Руслан')),
    ((SELECT id FROM workouts WHERE title = 'Матчева практика'), (SELECT id FROM athletes WHERE name = 'Андрій')),

    -- День +4
    ((SELECT id FROM workouts WHERE title = 'Прийом подачі'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Прийом подачі'), (SELECT id FROM athletes WHERE name = 'Андрій')),
    ((SELECT id FROM workouts WHERE title = 'Тактика гри'), (SELECT id FROM athletes WHERE name = 'Оксана')),

    -- День +5
    ((SELECT id FROM workouts WHERE title = 'Смєш та свіч'), (SELECT id FROM athletes WHERE name = 'Оксана')),
    ((SELECT id FROM workouts WHERE title = 'Смєш та свіч'), (SELECT id FROM athletes WHERE name = 'Андрій')),
    ((SELECT id FROM workouts WHERE title = 'Турнірний день'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Турнірний день'), (SELECT id FROM athletes WHERE name = 'Руслан')),
    ((SELECT id FROM workouts WHERE title = 'Турнірний день'), (SELECT id FROM athletes WHERE name = 'Оксана')),
    ((SELECT id FROM workouts WHERE title = 'Турнірний день'), (SELECT id FROM athletes WHERE name = 'Андрій')),

    -- День +6
    ((SELECT id FROM workouts WHERE title = 'Відновлення'), (SELECT id FROM athletes WHERE name = 'Дмитро')),
    ((SELECT id FROM workouts WHERE title = 'Відновлення'), (SELECT id FROM athletes WHERE name = 'Оксана'));
