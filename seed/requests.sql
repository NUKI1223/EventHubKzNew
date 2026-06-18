-- Test event-requests (admin moderation queue) + support messages.
-- Lives in events_db (event-service owns event_requests & support_messages).
-- References user IDs from users.sql (1..11; 2 = dinara/ADMIN, 11 = admin).

BEGIN;

TRUNCATE TABLE event_request_tags, event_requests, support_messages RESTART IDENTITY CASCADE;

-- ── Event requests ────────────────────────────────────────────────
-- Большинство в статусе PENDING — чтобы в админ-панели была живая очередь на модерацию.
INSERT INTO event_requests
  (id, title, short_description, full_description, location, online, event_date, registration_deadline,
   main_image_url, external_link, requester_id, requester_email, contact_email, status, admin_comment, reviewer_id, reviewed_at, created_at) VALUES
  (1, 'Kotlin Multiplatform Meetup Алматы',
      'Вечерний митап про KMP: один код на Android, iOS и desktop.',
      'Разбираем Kotlin Multiplatform на реальных кейсах: общий бизнес-слой, Compose Multiplatform UI, интеграция с нативными API. Два доклада + нетворкинг с пиццей.',
      'Алматы, SmArt.Point', false, '2026-07-15 18:30:00', '2026-07-14 23:59:00',
      NULL, 'https://kmp-almaty.kz', 5, 'timur.akhmetov@example.kz', 'timur.akhmetov@example.kz',
      'PENDING', NULL, NULL, NULL, NOW() - INTERVAL '2 days'),

  (2, 'PyData Astana 2026',
      'Конференция по data science и ML-инжинирингу в Астане.',
      'Полный день докладов: feature stores, MLOps, LLM в проде, аналитика на больших данных. Спикеры из Kaspi, Halyk и пары финтех-стартапов. Будет запись.',
      'Астана, Astana Hub', false, '2026-09-12 10:00:00', '2026-09-08 23:59:00',
      NULL, 'https://pydata.kz', 4, 'aigerim.satpaeva@example.kz', 'events@pydata.kz',
      'PENDING', NULL, NULL, NULL, NOW() - INTERVAL '1 day'),

  (3, 'Frontend United Online',
      'Бесплатный онлайн-митап: React 19, Server Components, состояние без боли.',
      'Три доклада онлайн: что нового в React 19, паттерны управления состоянием в 2026, и как мы переписали SPA на RSC. Прямая трансляция в YouTube.',
      'Онлайн', true, '2026-07-30 19:00:00', '2026-07-30 18:00:00',
      NULL, 'https://frontend-united.kz', 2, 'dinara.zhumabaeva@example.kz', 'dinara.zhumabaeva@example.kz',
      'PENDING', NULL, NULL, NULL, NOW() - INTERVAL '5 hours'),

  (4, 'DevOps Days Karaganda',
      'Первый DevOps-движ в Караганде: CI/CD, Kubernetes, observability.',
      'Региональная конференция для инженеров эксплуатации. Воркшоп по GitOps (ArgoCD), доклады про SRE-культуру и стоимость облака. Нетворкинг с местным комьюнити.',
      'Караганда, IT-Hub', false, '2026-10-18 10:00:00', '2026-10-14 23:59:00',
      NULL, 'https://devopsdays-krg.kz', 3, 'alisher.zhakupov@example.kz', 'alisher.zhakupov@example.kz',
      'PENDING', NULL, NULL, NULL, NOW() - INTERVAL '3 days'),

  (5, 'CTF Night by AppSec KZ',
      'Ночной jeopardy-CTF для начинающих и не только.',
      'Командный CTF на 6 часов: web, crypto, reverse, forensics. Отдельный easy-трек для новичков с подсказками. Призы от спонсоров и разбор тасков в конце.',
      'Алматы, KBTU', false, '2026-08-22 18:00:00', '2026-08-20 23:59:00',
      NULL, 'https://ctf.appsec.kz', 7, 'bauyrzhan.mukashev@example.kz', 'ctf@appsec.kz',
      'PENDING', NULL, NULL, NULL, NOW() - INTERVAL '8 hours'),

  (6, 'IT Career Day для студентов',
      'Ярмарка вакансий и карьерные консультации для студентов вузов.',
      'Компании-партнёры проводят мини-интервью, ревью резюме и доклады про вход в профессию. Отдельная зона для джунов и стажёров. Вход свободный по регистрации.',
      'Алматы, AITU', false, '2026-09-27 11:00:00', '2026-09-25 23:59:00',
      NULL, 'https://careerday.kz', 10, 'alina.romanova@example.kz', 'alina.romanova@example.kz',
      'PENDING', NULL, NULL, NULL, NOW() - INTERVAL '12 hours'),

  (7, 'Go Meetup Алматы #4',
      'Регулярный митап Go-сообщества: дженерики, конкуррентность, тулинг.',
      'Четвёртая встреча Go-комьюнити. Доклады про практику дженериков, профилирование с pprof и сборку микросервисов. После — свободное общение.',
      'Алматы, MOST Hub', false, '2026-07-24 19:00:00', '2026-07-23 23:59:00',
      NULL, 'https://gophers.kz', 1, 'aidar.kasenov@example.kz', 'aidar.kasenov@example.kz',
      'APPROVED', 'Отличная заявка, добавили в календарь. Спасибо!', 2, NOW() - INTERVAL '1 day', NOW() - INTERVAL '4 days'),

  (8, 'UX/UI Workshop: Design Systems',
      'Практический воркшоп по построению дизайн-систем в Figma.',
      'За один день собираем токены, компоненты и документацию дизайн-системы. Нужен ноутбук с Figma. Количество мест ограничено.',
      'Астана, Terricon Valley', false, '2026-08-09 10:00:00', '2026-08-06 23:59:00',
      NULL, 'https://uxworkshop.kz', 6, 'anastasia.sokolova@example.kz', 'anastasia.sokolova@example.kz',
      'APPROVED', 'Одобрено. Уточните, пожалуйста, максимальное число участников ближе к дате.', 11, NOW() - INTERVAL '6 hours', NOW() - INTERVAL '3 days'),

  (9, 'Crypto & Web3 Summit',
      'Саммит про блокчейн, DeFi и регулирование крипты в РК.',
      'Однодневный саммит с докладами про Web3-разработку, смарт-контракты и юридические аспекты. Панельная дискуссия с участием регулятора.',
      'Алматы, Ritz-Carlton', false, '2026-11-01 09:00:00', '2026-10-25 23:59:00',
      NULL, 'https://web3summit.kz', 9, 'yerzhan.serikbaev@example.kz', 'org@web3summit.kz',
      'REJECTED', 'Тематика слабо связана с IT-разработкой и ближе к коммерческому ивенту. В таком виде не публикуем.', 2, NOW() - INTERVAL '2 days', NOW() - INTERVAL '6 days'),

  (10,'JavaScript Quiz Night',
      'Развлекательный квиз по JavaScript с призами.',
      'Командный квиз по фронтенду и JS-каверзам: hoisting, event loop, типичные баги. Лёгкий формат, бар, нетворкинг.',
      'Алматы, бар Tweed', false, '2026-08-15 19:30:00', '2026-08-14 23:59:00',
      NULL, 'https://jsquiz.kz', 8, 'kamila.nurlanova@example.kz', 'kamila.nurlanova@example.kz',
      'PENDING', NULL, NULL, NULL, NOW() - INTERVAL '20 hours');

INSERT INTO event_request_tags (request_id, tag_name) VALUES
  (1, 'mobile'), (1, 'java'),
  (2, 'data'), (2, 'ai'), (2, 'python'),
  (3, 'frontend'), (3, 'react'), (3, 'javascript'),
  (4, 'devops'), (4, 'cloud'),
  (5, 'security'),
  (6, 'career'),
  (7, 'backend'),
  (8, 'frontend'),
  (9, 'backend'),
  (10, 'frontend'), (10, 'javascript');

SELECT setval(pg_get_serial_sequence('event_requests', 'id'), 10, true);

-- ── Support messages (техподдержка) ──────────────────────────────
-- Смесь нерешённых (для очереди в админке) и решённых с ответом администратора.
INSERT INTO support_messages
  (id, user_id, name, email, message, resolved, admin_reply, created_at, resolved_at) VALUES
  (1, 5, 'timur', 'timur.akhmetov@example.kz',
      'Здравствуйте! Не приходит письмо с подтверждением регистрации на мероприятие. Проверял спам — пусто. Подскажите, что делать?',
      false, NULL, NOW() - INTERVAL '3 hours', NULL),

  (2, 10, 'alina', 'alina.romanova@example.kz',
      'Можно ли как-то отменить запись на мероприятие? Не нашла кнопку в профиле.',
      false, NULL, NOW() - INTERVAL '1 day', NULL),

  (3, NULL, 'Гость', 'guest.user@gmail.com',
      'Добрый день! Хочу разместить своё мероприятие, но форма заявки требует ссылку на внешний сайт. А если сайта нет?',
      false, NULL, NOW() - INTERVAL '6 hours', NULL),

  (4, 3, 'alisher', 'alisher.zhakupov@example.kz',
      'QR-код в билете не сканируется на входе. Можно ли отмечать участников по коду вручную?',
      false, NULL, NOW() - INTERVAL '40 minutes', NULL),

  (5, 8, 'kamila', 'kamila.nurlanova@example.kz',
      'Подскажите, как стать организатором и получить доступ к аналитике по своим мероприятиям?',
      true, 'Здравствуйте! Создайте заявку на мероприятие через раздел «Создать заявку». После одобрения у вас появится кабинет организатора с аналитикой.',
      NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days'),

  (6, 1, 'aidar', 'aidar.kasenov@example.kz',
      'Нашёл опечатку в описании моего мероприятия. Как отредактировать после публикации?',
      true, 'Спасибо за сообщение! Редактирование уже доступно в кабинете организатора, во вкладке вашего мероприятия.',
      NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days'),

  (7, 6, 'nastya', 'anastasia.sokolova@example.kz',
      'Аватарка не загружается — выдаёт ошибку при сохранении профиля. Файл PNG, 800x800.',
      false, NULL, NOW() - INTERVAL '2 hours', NULL),

  (8, NULL, 'Marat O.', 'marat.partner@company.kz',
      'Здравствуйте! Мы хотим стать партнёром платформы и проводить серию митапов. С кем можно обсудить сотрудничество?',
      true, 'Добрый день, Марат! Передал ваш запрос команде партнёрств — с вами свяжутся по этому адресу в ближайшие дни.',
      NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days');

SELECT setval(pg_get_serial_sequence('support_messages', 'id'), 8, true);

COMMIT;
