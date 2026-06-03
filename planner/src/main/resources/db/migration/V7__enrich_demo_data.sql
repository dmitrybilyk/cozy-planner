-- Replace sparse demo seed with rich showcase data

DELETE FROM meetings          WHERE mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');
DELETE FROM mentor_availability WHERE mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');
DELETE FROM places            WHERE mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');
DELETE FROM trainees          WHERE mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

UPDATE mentors SET work_start = '08:00', work_end = '21:00', avail_step = 60, timezone = 'Europe/Kyiv'
WHERE name = 'Катя';

-- ── Locations ──────────────────────────────────────────────────────────────
INSERT INTO places (name, description, color, mentor_id) VALUES
    ('Зал А',     'Основний тренувальний зал',        '#3b82f6', (SELECT id FROM mentors WHERE name = 'Катя')),
    ('Зал Б',     'Малий зал для персональних',       '#22c55e', (SELECT id FROM mentors WHERE name = 'Катя')),
    ('Майданчик', 'Відкрита площадка',                '#f59e0b', (SELECT id FROM mentors WHERE name = 'Катя'));

-- ── Trainees ───────────────────────────────────────────────────────────────
INSERT INTO trainees (name, description, mentor_id, timezone) VALUES
    ('Анна К.',    'Початківець, 3 місяці занять',       (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kyiv'),
    ('Марія С.',   'Досвідчена спортсменка',             (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kyiv'),
    ('Олена В.',   'Середній рівень, бігунка',           (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kyiv'),
    ('Дмитро Н.',  'Силові тренування',                 (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kyiv'),
    ('Наталія Р.', 'Відновлення після травми коліна',   (SELECT id FROM mentors WHERE name = 'Катя'), 'Europe/Kyiv');

-- ── Helper aliases ─────────────────────────────────────────────────────────
-- used inline via subqueries below

-- ── Past sessions ──────────────────────────────────────────────────────────

-- Day -8
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Силове тренування',  'Базові вправи зі штангою та гантелями', CURRENT_DATE - 8, '10:00', '11:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Вечірня розтяжка',   'Гнучкість та релаксація', CURRENT_DATE - 8, '18:00', '19:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH');

-- Day -6
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Ранкова зарядка',    'Розминка та легке кардіо на вулиці',    CURRENT_DATE - 6, '09:00', '10:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Майданчик' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Кардіо тренування',  'Інтервальний біг + HIIT',              CURRENT_DATE - 6, '11:00', '12:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Йога',               'Хатха-йога, дихальні практики',        CURRENT_DATE - 6, '15:00', '16:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH');

-- Day -4
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Групове тренування',  'Функціональний фітнес у групі',        CURRENT_DATE - 4, '09:30', '11:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Персональне заняття', 'Відновна програма після травми',       CURRENT_DATE - 4, '14:00', '15:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH');

-- Day -2
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Функціональний фітнес', 'Кросфіт + TRX вправи',              CURRENT_DATE - 2, '09:00', '10:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Розтяжка',              'Глибокий стретчинг',                 CURRENT_DATE - 2, '11:00', '12:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Вечірнє силове',        'Присідання, жим, тяга',              CURRENT_DATE - 2, '17:00', '18:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH');

-- Day -1
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Ранкове тренування',  'Легкий біг + розминка',                CURRENT_DATE - 1, '09:00', '10:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Майданчик' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Кардіо',              'Степ-аеробіка та кардіо-ланцюжок',     CURRENT_DATE - 1, '14:00', '15:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Вечірня йога',        'Йога Нідра та медитація',              CURRENT_DATE - 1, '18:00', '19:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH');

-- ── Today ──────────────────────────────────────────────────────────────────

INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Ранкова зарядка',      'Розминка + легке кардіо',             CURRENT_DATE, '08:30', '09:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Майданчик' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Силове тренування',    'Базові вправи на всі групи м''язів',  CURRENT_DATE, '11:00', '12:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'CONFIRMED', 'COACH'),
    ('Персональне заняття',  'Індивідуальна програма відновлення',  CURRENT_DATE, '14:30', '16:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH'),
    ('Вечірнє тренування',   'Групова динамічна сесія',             CURRENT_DATE, '17:30', '19:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH');

-- ── Future sessions ────────────────────────────────────────────────────────

-- Day +1
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Ранкове тренування',   'Функціональна розминка та кардіо',    CURRENT_DATE + 1, '09:00', '10:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH'),
    ('Функціональний фітнес','Кросфіт-елементи та HIIT',            CURRENT_DATE + 1, '14:00', '15:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH'),
    ('Розтяжка та йога',     'Флексибіліті, баланс і дихання',      CURRENT_DATE + 1, '17:00', '18:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH');

-- Day +2
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Кардіо тренування',    'Інтервальний біг та стрибки',         CURRENT_DATE + 2, '10:00', '11:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Майданчик' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH'),
    ('Відновне тренування',  'Мякий рух, лімфодренаж',            CURRENT_DATE + 2, '15:00', '16:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH');

-- Day +4
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Групове тренування',   'Командна динамічна сесія',            CURRENT_DATE + 4, '09:30', '11:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH'),
    ('Кардіо',               'Інтервали на майданчику',             CURRENT_DATE + 4, '14:00', '15:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Майданчик' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH');

-- Day +7
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Силове тренування',    'Вільні ваги та машини',               CURRENT_DATE + 7, '10:00', '11:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH'),
    ('Легкий біг',           'Відновний пробіг 5 км',               CURRENT_DATE + 7, '15:00', '16:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Майданчик' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH'),
    ('Йога',                 'Відновна практика',                   CURRENT_DATE + 7, '17:00', '18:30',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал Б' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH');

-- Day +10
INSERT INTO meetings (title, description, meeting_date, start_time, end_time, mentor_id, place_id, confirmation_status, created_by) VALUES
    ('Групова підготовка',   'Підсумкове заняття тижня', CURRENT_DATE + 10, '11:00', '13:00',
     (SELECT id FROM mentors WHERE name = 'Катя'),
     (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')),
     'NONE', 'COACH');

-- ── meeting_trainees ───────────────────────────────────────────────────────

-- Day -8: Силове — Дмитро, Олена
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Силове тренування' AND m.meeting_date = CURRENT_DATE - 8
  AND t.name IN ('Дмитро Н.', 'Олена В.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -8: Вечірня розтяжка — Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Вечірня розтяжка' AND m.meeting_date = CURRENT_DATE - 8
  AND t.name = 'Наталія Р.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -6: Ранкова зарядка — Анна, Марія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Ранкова зарядка' AND m.meeting_date = CURRENT_DATE - 6
  AND t.name IN ('Анна К.', 'Марія С.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -6: Кардіо тренування — Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Кардіо тренування' AND m.meeting_date = CURRENT_DATE - 6
  AND t.name = 'Дмитро Н.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -6: Йога — Олена, Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Йога' AND m.meeting_date = CURRENT_DATE - 6
  AND t.name IN ('Олена В.', 'Наталія Р.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -4: Групове тренування — Анна, Марія, Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Групове тренування' AND m.meeting_date = CURRENT_DATE - 4
  AND t.name IN ('Анна К.', 'Марія С.', 'Дмитро Н.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -4: Персональне заняття — Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Персональне заняття' AND m.meeting_date = CURRENT_DATE - 4
  AND t.name = 'Наталія Р.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -2: Функціональний фітнес — Анна, Олена
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Функціональний фітнес' AND m.meeting_date = CURRENT_DATE - 2
  AND t.name IN ('Анна К.', 'Олена В.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -2: Розтяжка — Марія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Розтяжка' AND m.meeting_date = CURRENT_DATE - 2
  AND t.name = 'Марія С.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -2: Вечірнє силове — Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Вечірнє силове' AND m.meeting_date = CURRENT_DATE - 2
  AND t.name = 'Дмитро Н.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -1: Ранкове тренування — Анна, Марія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Ранкове тренування' AND m.meeting_date = CURRENT_DATE - 1
  AND t.name IN ('Анна К.', 'Марія С.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -1: Кардіо — Дмитро, Олена
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Кардіо' AND m.meeting_date = CURRENT_DATE - 1
  AND t.name IN ('Дмитро Н.', 'Олена В.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day -1: Вечірня йога — Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Вечірня йога' AND m.meeting_date = CURRENT_DATE - 1
  AND t.name = 'Наталія Р.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Today: Ранкова зарядка — Анна, Марія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Ранкова зарядка' AND m.meeting_date = CURRENT_DATE
  AND t.name IN ('Анна К.', 'Марія С.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Today: Силове тренування — Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Силове тренування' AND m.meeting_date = CURRENT_DATE
  AND t.name = 'Дмитро Н.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Today: Персональне заняття — Олена
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Персональне заняття' AND m.meeting_date = CURRENT_DATE
  AND t.name = 'Олена В.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Today: Вечірнє тренування — Анна, Марія, Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Вечірнє тренування' AND m.meeting_date = CURRENT_DATE
  AND t.name IN ('Анна К.', 'Марія С.', 'Дмитро Н.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +1: Ранкове тренування — Анна, Олена
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Ранкове тренування' AND m.meeting_date = CURRENT_DATE + 1
  AND t.name IN ('Анна К.', 'Олена В.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +1: Функціональний фітнес — Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Функціональний фітнес' AND m.meeting_date = CURRENT_DATE + 1
  AND t.name = 'Дмитро Н.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +1: Розтяжка та йога — Марія, Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Розтяжка та йога' AND m.meeting_date = CURRENT_DATE + 1
  AND t.name IN ('Марія С.', 'Наталія Р.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +2: Кардіо тренування — Анна, Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Кардіо тренування' AND m.meeting_date = CURRENT_DATE + 2
  AND t.name IN ('Анна К.', 'Дмитро Н.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +2: Відновне тренування — Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Відновне тренування' AND m.meeting_date = CURRENT_DATE + 2
  AND t.name = 'Наталія Р.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +4: Групове тренування — Анна, Марія, Олена, Дмитро
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Групове тренування' AND m.meeting_date = CURRENT_DATE + 4
  AND t.name IN ('Анна К.', 'Марія С.', 'Олена В.', 'Дмитро Н.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +4: Кардіо — Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Кардіо' AND m.meeting_date = CURRENT_DATE + 4
  AND t.name = 'Наталія Р.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +7: Силове тренування — Дмитро, Олена
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Силове тренування' AND m.meeting_date = CURRENT_DATE + 7
  AND t.name IN ('Дмитро Н.', 'Олена В.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +7: Легкий біг — Анна
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Легкий біг' AND m.meeting_date = CURRENT_DATE + 7
  AND t.name = 'Анна К.'
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +7: Йога — Марія, Наталія
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Йога' AND m.meeting_date = CURRENT_DATE + 7
  AND t.name IN ('Марія С.', 'Наталія Р.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Day +10: Групова підготовка — all
INSERT INTO meeting_trainees (meeting_id, trainee_id)
SELECT m.id, t.id FROM meetings m, trainees t
WHERE m.title = 'Групова підготовка' AND m.meeting_date = CURRENT_DATE + 10
  AND t.name IN ('Анна К.', 'Марія С.', 'Олена В.', 'Дмитро Н.', 'Наталія Р.')
  AND m.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- ── Set confirmed_trainee_ids for all CONFIRMED sessions ──────────────────
UPDATE meetings SET confirmed_trainee_ids = COALESCE(
    (SELECT string_agg(mt.trainee_id::text, ',')
     FROM meeting_trainees mt WHERE mt.meeting_id = meetings.id), '')
WHERE confirmation_status = 'CONFIRMED'
  AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- Today's morning sessions also mark as confirmed
UPDATE meetings SET confirmed_trainee_ids = COALESCE(
    (SELECT string_agg(mt.trainee_id::text, ',')
     FROM meeting_trainees mt WHERE mt.meeting_id = meetings.id), '')
WHERE meeting_date = CURRENT_DATE
  AND start_time < '13:00'
  AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- For today's evening session, partially confirm Анна К. only
UPDATE meetings SET confirmed_trainee_ids = COALESCE(
    (SELECT t.id::text FROM trainees t
     WHERE t.name = 'Анна К.'
       AND t.mentor_id = (SELECT id FROM mentors WHERE name = 'Катя')), '')
WHERE title = 'Вечірнє тренування'
  AND meeting_date = CURRENT_DATE
  AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя');

-- ── Availability for the current week ─────────────────────────────────────
INSERT INTO mentor_availability (mentor_id, date, start_time, end_time, place_id)
SELECT
    (SELECT id FROM mentors WHERE name = 'Катя'),
    CURRENT_DATE + offs,
    '08:00'::time,
    '13:00'::time,
    (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя'))
FROM generate_series(0, 9) AS offs
WHERE EXTRACT(DOW FROM CURRENT_DATE + offs) NOT IN (0);

INSERT INTO mentor_availability (mentor_id, date, start_time, end_time, place_id)
SELECT
    (SELECT id FROM mentors WHERE name = 'Катя'),
    CURRENT_DATE + offs,
    '14:00'::time,
    '21:00'::time,
    (SELECT id FROM places WHERE name = 'Зал А' AND mentor_id = (SELECT id FROM mentors WHERE name = 'Катя'))
FROM generate_series(0, 9) AS offs
WHERE EXTRACT(DOW FROM CURRENT_DATE + offs) NOT IN (0);