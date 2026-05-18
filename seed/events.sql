-- Test events + tags. References user IDs from users.sql.

BEGIN;

TRUNCATE TABLE event_tags, event_likes_unused_placeholder, events, event_requests, tags RESTART IDENTITY CASCADE;
-- Note: event_likes_unused_placeholder doesn't exist; if it does (it won't), it gets truncated. Safe-guard for partial schemas.

INSERT INTO tags (id, name) VALUES
  (1, 'backend'),
  (2, 'frontend'),
  (3, 'mobile'),
  (4, 'devops'),
  (5, 'ai'),
  (6, 'data'),
  (7, 'security'),
  (8, 'gamedev'),
  (9, 'cloud'),
  (10,'python'),
  (11,'react'),
  (12,'javascript'),
  (13,'java'),
  (14,'career'),
  (15,'hackathon');

SELECT setval(pg_get_serial_sequence('tags', 'id'), 15, true);

INSERT INTO events (id, title, short_description, full_description, location, online, event_date, registration_deadline, main_image_url, external_link, organizer_id, organizer_email, like_count, created_at, updated_at) VALUES
  (1, 'Almaty Spring Hackathon 2026',
      '48-часовой хакатон в Алматы — придумай и собери MVP за выходные.',
      'Командный хакатон в коворкинге MOST. 48 часов, 4 номинации (AI, FinTech, EdTech, Open). Призовой фонд 3 000 000 ₸, менторы из Kaspi.kz и Halyk Tech.',
      'Алматы, MOST Hub', false,
      '2026-05-23 10:00:00', '2026-05-20 23:59:00', NULL, 'https://hackathon.kz/spring2026',
      1, 'aidar.kasenov@example.kz', 0, NOW(), NOW()),

  (2, 'DevFest Astana 2026',
      'Главная Google-конференция года — два дня докладов про Android, Web и Cloud.',
      'DevFest Astana собирает GDG-комьюнити со всей страны. Треки: Android, Web, Cloud, ML, Flutter. Хедлайнер — спикер из Google London.',
      'Астана, EXPO Congress Center', false,
      '2026-10-04 09:30:00', '2026-09-28 23:59:00', NULL, 'https://devfest.kz',
      2, 'dinara.zhumabaeva@example.kz', 0, NOW(), NOW()),

  (3, 'AI Weekend Алматы',
      'Двухдневный воркшоп по LLM-агентам, RAG и fine-tuning open-source моделей.',
      'Практический интенсив: пишем агента на LangChain, разворачиваем локальный RAG на Qdrant, файнтюним Llama 3 8B. Ноутбук с CUDA желателен.',
      'Алматы, nFactorial School', false,
      '2026-06-14 10:00:00', '2026-06-10 23:59:00', NULL, NULL,
      9, 'yerzhan.serikbaev@example.kz', 0, NOW(), NOW()),

  (4, 'Backend Conf KZ',
      'Конференция бэкенд-разработчиков: Java, Go, Kotlin, distributed systems.',
      'Доклады от инженеров Kaspi, Halyk, Beeline и Yandex. Треки: микросервисы, перформанс, observability, базы данных. After-party на крыше.',
      'Алматы, Ritz Carlton', false,
      '2026-09-12 10:00:00', '2026-09-05 23:59:00', NULL, NULL,
      1, 'aidar.kasenov@example.kz', 0, NOW(), NOW()),

  (5, 'React Almaty Meetup #14',
      'Ежеквартальная встреча React-сообщества — три доклада и pizza & beer.',
      'Темы: React 19 use(), Suspense на проде, миграция с Webpack на Vite. Доклады по 25 минут + Q&A. Регистрация бесплатная, мест 80.',
      'Алматы, ASTANA HUB Almaty', false,
      '2026-05-30 19:00:00', '2026-05-29 18:00:00', NULL, NULL,
      2, 'dinara.zhumabaeva@example.kz', 0, NOW(), NOW()),

  (6, 'DataFest Astana 2026',
      'Конференция о данных: ML, аналитика, инженерия данных и dbt.',
      'Однодневная конференция. Спикеры из Kaspi Data Science, Halyk Data Lab, AITU. Воркшопы по dbt и Airflow во второй половине дня.',
      'Астана, AITU', false,
      '2026-11-22 10:00:00', '2026-11-15 23:59:00', NULL, NULL,
      4, 'aigerim.satpaeva@example.kz', 0, NOW(), NOW()),

  (7, 'DevOps Days Almaty',
      'Сообщество DevOps в Казахстане — два дня про SRE, Kubernetes и платформы.',
      'Open Spaces формат + Ignite talks. Темы: Argo CD, Cilium eBPF, GitOps на проде, инцидент-менеджмент. Без слайдов про "как мы внедрили Kubernetes".',
      'Алматы, Almaty Tech Garden', false,
      '2026-07-18 09:00:00', '2026-07-10 23:59:00', NULL, NULL,
      3, 'alisher.zhakupov@example.kz', 0, NOW(), NOW()),

  (8, 'Kazakh Mobile Tech Talk',
      'Митап мобильных разработчиков — iOS и Android в одном вечере.',
      'Три доклада: SwiftUI vs UIKit в 2026, KMP в проде, оптимизация холодного запуска Android-приложения. Свободный микрофон в конце.',
      'Алматы, Open Space Astana Hub', false,
      '2026-06-05 19:00:00', '2026-06-04 18:00:00', NULL, NULL,
      5, 'timur.akhmetov@example.kz', 0, NOW(), NOW()),

  (9, 'Cybersecurity Summit KZ',
      'Главная ИБ-конференция года: AppSec, BugBounty, реагирование на инциденты.',
      'Доклады, CTF на 6 часов и панельная дискуссия с CISO банков второго уровня. Регистрация для проф. участников бесплатная.',
      'Астана, Hilton Astana', false,
      '2026-10-25 09:30:00', '2026-10-18 23:59:00', NULL, NULL,
      7, 'bauyrzhan.mukashev@example.kz', 0, NOW(), NOW()),

  (10,'Tech Career Day Алматы',
      'Ярмарка вакансий: Kaspi, Halyk, Beeline, inDrive, Forte и стартапы.',
      'Стенды компаний + 1-on-1 мини-интервью на месте, CV-ревью от рекрутеров. Параллельный трек докладов "как пройти собес в Big Tech".',
      'Алматы, Almaty Towers', false,
      '2026-09-20 11:00:00', '2026-09-18 23:59:00', NULL, NULL,
      8, 'kamila.nurlanova@example.kz', 0, NOW(), NOW()),

  (11,'GameDev Weekend KZ',
      'Два дня про игры: Unity, Unreal, инди-разработка и геймдизайн.',
      'Доклады от разработчиков из Astana Game Studio и WEPLAY. Воркшоп по Unity URP и lightning talks от студентов KBTU.',
      'Алматы, IT-парк Tumar', false,
      '2026-08-08 10:00:00', '2026-08-01 23:59:00', NULL, NULL,
      5, 'timur.akhmetov@example.kz', 0, NOW(), NOW()),

  (12,'Cloud Native Astana',
      'Митап про cloud-native: Kubernetes, service mesh, serverless в КЗ.',
      'Кейсы миграции с on-prem на облако от Halyk Cloud и Yandex Cloud KZ. Бонусом — демо OpenTofu (форк Terraform) на проде.',
      'Астана, ASTANA HUB', false,
      '2026-06-21 18:30:00', '2026-06-20 18:00:00', NULL, NULL,
      3, 'alisher.zhakupov@example.kz', 0, NOW(), NOW()),

  (13,'WomenInTech KZ Conference',
      'Конференция о женщинах в IT: карьера, лидерство, баланс.',
      'Доклады от Tech Lead-ов и CTO компаний. Менторинг-сессии, networking lunch, after-party. Бесплатно для студенток.',
      'Алматы, Forte Hall', false,
      '2026-11-08 10:00:00', '2026-11-01 23:59:00', NULL, NULL,
      6, 'anastasia.sokolova@example.kz', 0, NOW(), NOW()),

  (14,'Python Almaty Online Meetup',
      'Полностью онлайн-встреча — три доклада в Zoom + Q&A в Telegram.',
      'Темы: async Python на проде, типизация с mypy, миграция с Django на FastAPI. Запись будет доступна в YouTube-канале сообщества.',
      'Online', true,
      '2026-05-28 19:00:00', '2026-05-28 18:30:00', NULL, NULL,
      4, 'aigerim.satpaeva@example.kz', 0, NOW(), NOW()),

  (15,'Open Source Day Astana',
      'Hacktoberfest-style день: контрибутим в open source, edu-сессии для новичков.',
      'Помогаем сделать первый PR в open source. Менторы из CNCF проектов и Spring сообщества. По итогам — мерч и сертификаты.',
      'Астана, AITU Library', false,
      '2026-10-17 11:00:00', '2026-10-15 23:59:00', NULL, NULL,
      10, 'alina.romanova@example.kz', 0, NOW(), NOW());

SELECT setval(pg_get_serial_sequence('events', 'id'), 15, true);

-- Event-tag associations
INSERT INTO event_tags (event_id, tag_name) VALUES
  (1, 'hackathon'), (1, 'backend'), (1, 'frontend'),
  (2, 'frontend'),  (2, 'mobile'),  (2, 'cloud'),  (2, 'ai'),
  (3, 'ai'),        (3, 'python'),
  (4, 'backend'),   (4, 'java'),    (4, 'devops'),
  (5, 'frontend'),  (5, 'react'),   (5, 'javascript'),
  (6, 'data'),      (6, 'ai'),      (6, 'python'),
  (7, 'devops'),    (7, 'cloud'),
  (8, 'mobile'),
  (9, 'security'),
  (10,'career'),    (10,'backend'), (10,'frontend'),
  (11,'gamedev'),
  (12,'cloud'),     (12,'devops'),
  (13,'career'),
  (14,'python'),    (14,'backend'),
  (15,'backend'),   (15,'frontend'),(15,'career');

COMMIT;
