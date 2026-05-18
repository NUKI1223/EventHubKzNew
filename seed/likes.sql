-- Test likes: scattered across users and events.
-- Pattern: each event has 3-8 likers; each user likes 4-9 events.

BEGIN;

TRUNCATE TABLE event_likes RESTART IDENTITY CASCADE;

INSERT INTO event_likes (user_id, event_id, created_at) VALUES
  -- Almaty Spring Hackathon (1) — popular
  (2, 1, NOW()), (3, 1, NOW()), (4, 1, NOW()), (5, 1, NOW()), (8, 1, NOW()), (10, 1, NOW()),
  -- DevFest Astana (2)
  (1, 2, NOW()), (3, 2, NOW()), (5, 2, NOW()), (6, 2, NOW()), (9, 2, NOW()),
  -- AI Weekend (3)
  (1, 3, NOW()), (4, 3, NOW()), (6, 3, NOW()), (8, 3, NOW()), (10, 3, NOW()),
  -- Backend Conf (4)
  (3, 4, NOW()), (5, 4, NOW()), (7, 4, NOW()), (9, 4, NOW()),
  -- React Almaty (5)
  (1, 5, NOW()), (6, 5, NOW()), (10, 5, NOW()),
  -- DataFest (6)
  (1, 6, NOW()), (2, 6, NOW()), (9, 6, NOW()), (10, 6, NOW()),
  -- DevOps Days (7)
  (1, 7, NOW()), (2, 7, NOW()), (4, 7, NOW()),
  -- Mobile Tech Talk (8)
  (2, 8, NOW()), (6, 8, NOW()), (10, 8, NOW()),
  -- Cybersecurity Summit (9)
  (1, 9, NOW()), (3, 9, NOW()), (5, 9, NOW()), (8, 9, NOW()),
  -- Tech Career Day (10) — very popular
  (1, 10, NOW()), (2, 10, NOW()), (3, 10, NOW()), (4, 10, NOW()), (5, 10, NOW()), (6, 10, NOW()), (7, 10, NOW()), (9, 10, NOW()), (10, 10, NOW()),
  -- GameDev Weekend (11)
  (2, 11, NOW()), (6, 11, NOW()), (10, 11, NOW()),
  -- Cloud Native (12)
  (1, 12, NOW()), (4, 12, NOW()), (7, 12, NOW()),
  -- WomenInTech (13)
  (2, 13, NOW()), (4, 13, NOW()), (8, 13, NOW()), (10, 13, NOW()),
  -- Python Almaty (14)
  (1, 14, NOW()), (3, 14, NOW()), (9, 14, NOW()), (10, 14, NOW()),
  -- Open Source Day (15)
  (1, 15, NOW()), (2, 15, NOW()), (3, 15, NOW()), (4, 15, NOW()), (8, 15, NOW());

COMMIT;
