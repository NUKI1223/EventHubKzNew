-- Test users. All passwords: password123
-- Truncates existing data and seeds with deterministic IDs.

BEGIN;

TRUNCATE TABLE user_contacts, user_tags, users RESTART IDENTITY CASCADE;

INSERT INTO users (id, username, email, password, role, description, avatar_url, enabled) VALUES
  (1, 'aidar',     'aidar.kasenov@example.kz',  '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'Backend-разработчик из Алматы. Пишу на Java/Spring, в свободное время — на Go.', NULL, true),
  (2, 'dinara',    'dinara.zhumabaeva@example.kz', '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'ADMIN', 'Senior Frontend / React, организатор IT-митапов в Астане.', NULL, true),
  (3, 'alisher',   'alisher.zhakupov@example.kz',  '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'DevOps инженер. K8s, Terraform, Grafana — стек на каждый день.', NULL, true),
  (4, 'aigerim',   'aigerim.satpaeva@example.kz',  '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'Data Scientist в финтех-стартапе. Python, ML, немного DL.', NULL, true),
  (5, 'timur',     'timur.akhmetov@example.kz',    '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'iOS-разработчик, Swift. Помогаю студентам KBTU с проектами.', NULL, true),
  (6, 'nastya',    'anastasia.sokolova@example.kz','$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'Product-дизайнер. Figma, prototyping, design systems.', NULL, true),
  (7, 'bauyrzhan', 'bauyrzhan.mukashev@example.kz','$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'Security engineer, AppSec & CTF. Веду блог про безопасность.', NULL, true),
  (8, 'kamila',    'kamila.nurlanova@example.kz',  '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'PM в EdTech. Слежу за рынком IT-образования в Казахстане.', NULL, true),
  (9, 'yerzhan',   'yerzhan.serikbaev@example.kz', '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'AI Research, NLP. Делаю pet-проекты с LLM.', NULL, true),
  (10,'alina',     'alina.romanova@example.kz',    '$2b$10$qJ9o8sTfLhMJJgcfXFqBquTG73k0uLPn80xgV1NlICluqNZTPayj2', 'USER',  'Студентка AITU, второй курс. Учусь, хожу на хакатоны.', NULL, true);

-- contacts: additionalProp1=telegram, 2=github, 3=instagram, 4=facebook
INSERT INTO user_contacts (user_id, contact_type, contact_value) VALUES
  (1, 'additionalProp1', 'https://t.me/aidar_kasenov'),
  (1, 'additionalProp2', 'https://github.com/aidar-kz'),
  (2, 'additionalProp1', 'https://t.me/dinara_dev'),
  (2, 'additionalProp2', 'https://github.com/dinara-zh'),
  (2, 'additionalProp3', 'https://instagram.com/dinara.codes'),
  (3, 'additionalProp1', 'https://t.me/alisher_ops'),
  (3, 'additionalProp2', 'https://github.com/alisher-devops'),
  (4, 'additionalProp2', 'https://github.com/aigerim-ml'),
  (5, 'additionalProp1', 'https://t.me/timur_ios'),
  (6, 'additionalProp3', 'https://instagram.com/nastya.designs'),
  (7, 'additionalProp2', 'https://github.com/bauyrzhan-sec'),
  (8, 'additionalProp1', 'https://t.me/kamila_pm'),
  (9, 'additionalProp2', 'https://github.com/yerzhan-ai'),
  (10,'additionalProp1', 'https://t.me/alina_aitu');

INSERT INTO user_tags (user_id, tag_name) VALUES
  (1, 'backend'), (1, 'java'),
  (2, 'frontend'), (2, 'react'), (2, 'javascript'), (2, 'career'),
  (3, 'devops'), (3, 'cloud'),
  (4, 'data'), (4, 'ai'), (4, 'python'),
  (5, 'mobile'),
  (6, 'frontend'), (6, 'career'),
  (7, 'security'), (7, 'backend'),
  (8, 'career'),
  (9, 'ai'), (9, 'python'), (9, 'data'),
  (10, 'career'), (10, 'frontend');

SELECT setval(pg_get_serial_sequence('users', 'id'), 10, true);

COMMIT;
