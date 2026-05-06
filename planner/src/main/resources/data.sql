-- 1. Insert Coaches
INSERT INTO coaches (name, specialization) VALUES
                                               ('Oleksandr Zinchenko', 'Football & Conditioning'),
                                               ('Olena Rybakova', 'Tennis High-Performance');

-- 2. Insert Athletes (Linked to Coaches)
INSERT INTO athletes (name, coach_id) VALUES
                                          ('Dmitry Bilyk', 1),
                                          ('Max Power', 1),
                                          ('Svitlana Ivanova', 2);

-- 3. Insert Workouts (Linked to Athletes and Coaches)
-- Removed the intensity column from the insert statement
INSERT INTO workouts (title, description, workout_date, workout_time, duration_minutes, athlete_id, coach_id) VALUES
                                                                                                                  ('Morning Interval Run', '10 min warm up, 5x800m sprints, 10 min cool down', CURRENT_DATE, '08:00:00', 45, 1, 1),
                                                                                                                  ('Recovery Swim', 'Easy 1000m total. Focus on breathing and technique.', CURRENT_DATE + INTERVAL '1 day', '09:30:00', 30, 1, 1),
                                                                                                                  ('Strength Training', 'Squats, Deadlifts, and Core. 3 sets of 10.', CURRENT_DATE + INTERVAL '2 days', '18:00:00', 60, 1, 1),
                                                                                                                  ('Tennis Match Practice', 'Focus on second serve and backhand cross-court.', CURRENT_DATE, '11:00:00', 90, 3, 2);