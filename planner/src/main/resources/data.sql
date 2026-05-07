-- 1. Clubs (Базовий рівень)
INSERT INTO clubs (name, description) VALUES
                                          ('Elite Sports Academy', 'A premium facility for professional athletes'),
                                          ('City Tennis Club', 'Community focused tennis club with top-tier coaches');

-- 2. Coaches (Прив'язані до клубів через club_id)
-- Zinchenko в Elite Sports (1), Rybakova в City Tennis (2)
INSERT INTO coaches (name, specialization, club_id) VALUES
                                                        ('Oleksandr Zinchenko', 'Football & Conditioning', 1),
                                                        ('Olena Rybakova', 'Tennis High-Performance', 2);

-- 3. Athletes (Прив'язані до тренерів)
INSERT INTO athletes (name, description, coach_id) VALUES
                                                       ('Dmitry Bilyk', 'Focus on reactive programming and endurance', 1),
                                                       ('Max Power', 'High-intensity interval specialist', 1),
                                                       ('Svitlana Ivanova', 'Professional junior tennis player', 2);

-- 4. Workouts (Прив'язані до атлетів та тренерів)
INSERT INTO workouts (title, description, workout_date, workout_time, duration_minutes, athlete_id, coach_id) VALUES
                                                                                                                  ('Morning Interval Run', '10 min warm up, 5x800m sprints, 10 min cool down', CURRENT_DATE, '08:00:00', 45, 1, 1),
                                                                                                                  ('Recovery Swim', 'Easy 1000m total. Focus on breathing and technique.', CURRENT_DATE + INTERVAL '1 day', '09:30:00', 30, 1, 1),
                                                                                                                  ('Strength Training', 'Squats, Deadlifts, and Core. 3 sets of 10.', CURRENT_DATE + INTERVAL '2 days', '18:00:00', 60, 1, 1),
                                                                                                                  ('Tennis Match Practice', 'Focus on second serve and backhand cross-court.', CURRENT_DATE, '11:00:00', 90, 3, 2);