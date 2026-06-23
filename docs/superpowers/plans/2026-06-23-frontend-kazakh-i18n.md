# Frontend Kazakh Localization (RU/KK) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a RU/KK language switcher and localize all static interface text to Kazakh, with Russian fallback.

**Architecture:** react-i18next initialized once (`src/i18n/index.js`), two locale dictionaries (`ru.json`, `kk.json`), a `LanguageSwitcher` in the header. Components replace hardcoded strings with `t('namespace.key')`. Dynamic/API data and backend-generated strings are NOT translated.

**Tech Stack:** React 19, Vite 6, react-i18next, i18next.

## Global Constraints

- Commits authored by the user's account only — NO Claude / Co-Authored-By / Anthropic attribution in commit messages.
- NEVER `git add` or commit `.env`.
- Branch is `main`; do NOT push (the user pushes via `git push origin main:master`).
- `fallbackLng: 'ru'`; persisted language in `localStorage` key `lang`.
- In `ru.json`, each key's value is the EXACT original Russian string (verbatim, including punctuation/emoji). `kk.json` holds the Kazakh translation of the same key.
- Do NOT translate: data from the API (event titles/descriptions, usernames, tags), backend-generated strings, or code comments.
- All frontend work happens in `frontend/`. Verify with `npm run build` (from `frontend/`) — must succeed for every task.
- Keys are nested JSON grouped by screen namespace (e.g. `header.events`, `landing.heroTitle`). The same key path must exist in both `ru.json` and `kk.json`.

---

## Conversion Recipe (used by every translation task)

For each component in a task's file list, apply this exact recipe:

1. Add the hook import and call inside the component:
   ```jsx
   import { useTranslation } from 'react-i18next';
   // inside component body, before return:
   const { t } = useTranslation();
   ```
2. For each hardcoded Russian UI string in JSX (text nodes, `placeholder=`, `title=`, `alt=`, `aria-label=`, button labels, headings, empty-state text):
   - choose a key under the task's namespace, e.g. `events.emptyTitle`;
   - add to `ru.json`: the key with the EXACT original string as value;
   - add to `kk.json`: the same key with its Kazakh translation;
   - replace the JSX literal with `{t('events.emptyTitle')}` (or `placeholder={t('...')}`).
3. For strings that interpolate data, use named interpolation:
   ```jsx
   // before:  <p>Найдено {count} событий</p>
   // ru.json: "events.found": "Найдено {{count}} событий"
   // kk.json: "events.found": "{{count}} іс-шара табылды"
   // after:   <p>{t('events.found', { count })}</p>
   ```
   Do NOT wrap the interpolated DATA itself (e.g. `event.title`) — only the surrounding static text.
4. Leave untouched: any string rendered from API objects (`event.*`, `user.*`, tag values), and code comments.

**Worked example (Header.jsx nav item):**
```jsx
// before:
{ path: "/eventlist", label: "Мероприятия" },
// after (label resolved at render via t):
{ path: "/eventlist", labelKey: "header.events" },
// ...and where the label is rendered:
{t(item.labelKey)}
// ru.json: { "header": { "events": "Мероприятия" } }
// kk.json: { "header": { "events": "Іс-шаралар" } }
```

> Kazakh translation quality is the user's final review (per spec). Generate complete `kk.json`; the `ru` fallback prevents blanks if a key is missed.

---

### Task 1: i18n infrastructure + switcher + Header/common

**Files:**
- Modify: `frontend/package.json` (add deps)
- Create: `frontend/src/i18n/index.js`
- Create: `frontend/src/i18n/locales/ru.json`
- Create: `frontend/src/i18n/locales/kk.json`
- Create: `frontend/src/components/LanguageSwitcher.jsx`
- Modify: `frontend/src/main.jsx` (import i18n)
- Modify: `frontend/src/components/Header.jsx` (use t + mount switcher)
- Modify: `frontend/src/components/PageError.jsx`, `frontend/src/components/Pagination.jsx`

**Interfaces:**
- Produces: initialized i18next (`t`, `i18n.changeLanguage`); namespaces `common.*`, `header.*`; `LanguageSwitcher` component.

- [ ] **Step 1: Install dependencies**

Run (from `frontend/`):
```bash
npm install i18next react-i18next
```
Expected: packages added to `package.json` dependencies.

- [ ] **Step 2: Create the i18n init module**

Create `frontend/src/i18n/index.js`:
```js
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import ru from './locales/ru.json';
import kk from './locales/kk.json';

const saved = (typeof localStorage !== 'undefined' && localStorage.getItem('lang')) || 'ru';

i18n.use(initReactI18next).init({
  resources: { ru: { translation: ru }, kk: { translation: kk } },
  lng: saved,
  fallbackLng: 'ru',
  interpolation: { escapeValue: false },
});

export default i18n;
```

- [ ] **Step 3: Create locale files with the common + header namespaces**

Create `frontend/src/i18n/locales/ru.json`:
```json
{
  "common": {
    "loading": "Загрузка...",
    "save": "Сохранить",
    "cancel": "Отмена",
    "back": "Назад",
    "notFound": "Не найдено"
  },
  "header": {
    "home": "Главная",
    "events": "Мероприятия",
    "createRequest": "Создать заявку",
    "support": "Поддержка",
    "admin": "Админ",
    "signIn": "Войти",
    "signOut": "Выйти",
    "profile": "Профиль"
  }
}
```

Create `frontend/src/i18n/locales/kk.json` (Kazakh translations of the SAME keys):
```json
{
  "common": {
    "loading": "Жүктелуде...",
    "save": "Сақтау",
    "cancel": "Бас тарту",
    "back": "Артқа",
    "notFound": "Табылмады"
  },
  "header": {
    "home": "Басты бет",
    "events": "Іс-шаралар",
    "createRequest": "Өтінім жасау",
    "support": "Қолдау",
    "admin": "Әкімші",
    "signIn": "Кіру",
    "signOut": "Шығу",
    "profile": "Профиль"
  }
}
```
> Adjust the exact `ru` values to match the real strings found in `Header.jsx` verbatim; add any header keys the component actually uses.

- [ ] **Step 4: Import i18n in main.jsx**

In `frontend/src/main.jsx`, add the side-effect import near the other imports (before `App` renders):
```jsx
import './i18n';
```

- [ ] **Step 5: Create the LanguageSwitcher**

Create `frontend/src/components/LanguageSwitcher.jsx`:
```jsx
import { useTranslation } from 'react-i18next';

const LANGS = [
  { code: 'ru', label: 'RU' },
  { code: 'kk', label: 'KK' },
];

export default function LanguageSwitcher() {
  const { i18n } = useTranslation();
  const change = (code) => {
    i18n.changeLanguage(code);
    localStorage.setItem('lang', code);
  };
  return (
    <div className="lang-switch">
      {LANGS.map((l) => (
        <button
          key={l.code}
          type="button"
          className={`lang-switch__btn${i18n.resolvedLanguage === l.code ? ' lang-switch__btn--on' : ''}`}
          onClick={() => change(l.code)}
        >
          {l.label}
        </button>
      ))}
    </div>
  );
}
```

- [ ] **Step 6: Wire Header to t() and mount the switcher**

In `frontend/src/components/Header.jsx`: apply the Conversion Recipe to all static strings (nav labels, auth buttons), import and render `<LanguageSwitcher />` in the header bar. Apply the recipe to `PageError.jsx` and `Pagination.jsx` too (small).

- [ ] **Step 7: Verify build + manual switch**

Run (from `frontend/`):
```bash
npm run build
```
Expected: build succeeds. Then `npm run dev`, open the app, click KK → header/nav switches to Kazakh; reload → still KK; click RU → back to Russian.

- [ ] **Step 8: Commit**
```bash
git add frontend/package.json frontend/package-lock.json frontend/src/i18n frontend/src/main.jsx frontend/src/components/LanguageSwitcher.jsx frontend/src/components/Header.jsx frontend/src/components/PageError.jsx frontend/src/components/Pagination.jsx
git commit -m "feat(i18n): add react-i18next infra, RU/KK switcher, localized header"
```

---

### Task 2: Landing page

**Files:** Modify `frontend/src/components/MainPage.jsx`. Add keys under `landing.*` to `ru.json`/`kk.json`.

- [ ] **Step 1:** Apply the Conversion Recipe to every static string in `MainPage.jsx` under namespace `landing.*`.
- [ ] **Step 2:** Run `npm run build` (from `frontend/`) — expected: success.
- [ ] **Step 3:** `npm run dev`, open `/`, toggle KK — landing text is Kazakh; toggle RU — Russian.
- [ ] **Step 4:** Commit:
```bash
git add frontend/src/components/MainPage.jsx frontend/src/i18n/locales
git commit -m "feat(i18n): localize landing page"
```

---

### Task 3: Events browsing (list, card, search, likes)

**Files:** Modify `frontend/src/components/EventList.jsx`, `EventCard.jsx`, `SearchResults.jsx`, `LikedEvents.jsx`, `RegisteredEvents.jsx`, `TagSelector.jsx`, `LikeButton.jsx`, `EventLikes.jsx`, `EventLikers.jsx`. Keys under `events.*`.

- [ ] **Step 1:** Apply the Conversion Recipe to all static strings in the listed files under namespace `events.*`. Do NOT translate `event.title`/`event.shortDescription` or other API fields.
- [ ] **Step 2:** Run `npm run build` — expected: success.
- [ ] **Step 3:** `npm run dev`, open `/eventlist` and `/search`, toggle KK — labels/empty states/buttons are Kazakh, event card DATA stays as entered.
- [ ] **Step 4:** Commit:
```bash
git add frontend/src/components/EventList.jsx frontend/src/components/EventCard.jsx frontend/src/components/SearchResults.jsx frontend/src/components/LikedEvents.jsx frontend/src/components/RegisteredEvents.jsx frontend/src/components/TagSelector.jsx frontend/src/components/LikeButton.jsx frontend/src/components/EventLikes.jsx frontend/src/components/EventLikers.jsx frontend/src/i18n/locales
git commit -m "feat(i18n): localize events browsing screens"
```

---

### Task 4: Event detail + registration/ticket/check-in

**Files:** Modify `frontend/src/components/EventDetail.jsx`, `RegisterButton.jsx`, `RegistrationModal.jsx`, `EventTicket.jsx`, `CheckinPage.jsx`, `EventRegistrations.jsx`. Keys under `eventDetail.*`.

- [ ] **Step 1:** Apply the Conversion Recipe under namespace `eventDetail.*`. The custom question labels come from the event object (API) — do NOT translate those; only translate static UI around them.
- [ ] **Step 2:** Run `npm run build` — expected: success.
- [ ] **Step 3:** `npm run dev`, open an event (`/events/3`), toggle KK — buttons/labels Kazakh; register flow + ticket + `/checkin` static text Kazakh.
- [ ] **Step 4:** Commit:
```bash
git add frontend/src/components/EventDetail.jsx frontend/src/components/RegisterButton.jsx frontend/src/components/RegistrationModal.jsx frontend/src/components/EventTicket.jsx frontend/src/components/CheckinPage.jsx frontend/src/components/EventRegistrations.jsx frontend/src/i18n/locales
git commit -m "feat(i18n): localize event detail, registration, ticket, check-in"
```

---

### Task 5: Profile

**Files:** Modify `frontend/src/components/Profile.jsx`, `EditProfile.jsx`. Keys under `profile.*`.

- [ ] **Step 1:** Apply the Conversion Recipe under namespace `profile.*`. Do NOT translate `user.username`/`user.description`/tag values.
- [ ] **Step 2:** Run `npm run build` — expected: success.
- [ ] **Step 3:** `npm run dev`, open own profile + edit-profile, toggle KK — labels/buttons Kazakh; user data unchanged.
- [ ] **Step 4:** Commit:
```bash
git add frontend/src/components/Profile.jsx frontend/src/components/EditProfile.jsx frontend/src/i18n/locales
git commit -m "feat(i18n): localize profile screens"
```

---

### Task 6: Organizer + request form + admin

**Files:** Modify `frontend/src/components/EventRequestForm.jsx`, `QuestionEditor.jsx`, `OrganizerDashboard.jsx`, `EventRegistrants.jsx`, `AdminEventRequests.jsx`, `AdminSupportMessages.jsx`, `AdminDashboard.jsx`. Keys under `requestForm.*`, `organizer.*`, `admin.*`.

- [ ] **Step 1:** Apply the Conversion Recipe. Use `requestForm.*` for the form + `QuestionEditor`, `organizer.*` for the dashboard + registrants, `admin.*` for admin screens. Registrants' answer keys/values come from API — do NOT translate.
- [ ] **Step 2:** Run `npm run build` — expected: success.
- [ ] **Step 3:** `npm run dev`, open `/request-event`, organizer dashboard, `/admin`, toggle KK — all static labels Kazakh.
- [ ] **Step 4:** Commit:
```bash
git add frontend/src/components/EventRequestForm.jsx frontend/src/components/QuestionEditor.jsx frontend/src/components/OrganizerDashboard.jsx frontend/src/components/EventRegistrants.jsx frontend/src/components/AdminEventRequests.jsx frontend/src/components/AdminSupportMessages.jsx frontend/src/components/AdminDashboard.jsx frontend/src/i18n/locales
git commit -m "feat(i18n): localize organizer, request form, admin screens"
```

---

### Task 7: Auth (sign in / sign up / verify)

**Files:** Modify `frontend/src/components/SignIn.jsx`, `SignUpNew.jsx`, `Verify.jsx`. Keys under `auth.*`.

- [ ] **Step 1:** Apply the Conversion Recipe under namespace `auth.*` (labels, placeholders, buttons, helper/notice text). Server error messages returned from the API stay as-is.
- [ ] **Step 2:** Run `npm run build` — expected: success.
- [ ] **Step 3:** `npm run dev`, open `/signin`, `/signup`, `/verify`, toggle KK — form chrome Kazakh.
- [ ] **Step 4:** Commit:
```bash
git add frontend/src/components/SignIn.jsx frontend/src/components/SignUpNew.jsx frontend/src/components/Verify.jsx frontend/src/i18n/locales
git commit -m "feat(i18n): localize auth screens"
```

---

### Task 8: Support + notifications + sweep

**Files:** Modify `frontend/src/components/Support.jsx`, `NotificationsDropdown.jsx`. Keys under `support.*`, `notifications.*`.

- [ ] **Step 1:** Apply the Conversion Recipe to `Support.jsx` (`support.*`) and `NotificationsDropdown.jsx` (`notifications.*`). The AI assistant replies and notification bodies come from the backend — do NOT translate those; only the static chrome (header, input placeholder, "Передать админу" button, empty states).
- [ ] **Step 2: Final sweep** — find any remaining hardcoded Cyrillic UI strings in components and convert them:
```bash
cd frontend && grep -rnE ">[^<]*[А-Яа-я]|placeholder=\"[^\"]*[А-Яа-я]|title=\"[^\"]*[А-Яа-я]" src/components --include=*.jsx | grep -vE "i18n/|t\(" | head -50
```
Convert anything user-facing that this surfaces (skip comments and API-data bindings).
- [ ] **Step 3:** Run `npm run build` — expected: success.
- [ ] **Step 4:** `npm run dev`, click through the whole app in KK mode — no Russian static chrome remains except backend-sourced strings (notifications/AI replies/server errors, which are out of scope per spec).
- [ ] **Step 5:** Commit:
```bash
git add frontend/src/components/Support.jsx frontend/src/components/NotificationsDropdown.jsx frontend/src/i18n/locales
git commit -m "feat(i18n): localize support, notifications; final sweep"
```

---

## Self-Review

**Spec coverage:**
- react-i18next infra + `i18n/index.js` + main.jsx import → Task 1. ✓
- Locale files ru/kk, nested namespaces → Task 1 + every task adds keys. ✓
- LanguageSwitcher in header, changeLanguage + localStorage → Task 1. ✓
- fallbackLng ru → Task 1 (init config). ✓
- Translate all static UI across screens → Tasks 2–8 cover landing, events, detail/registration, profile, organizer/admin, auth, support/notifications, plus Task 8 sweep. ✓
- Do NOT translate API data / backend strings → stated in Global Constraints and each task's Step 1. ✓
- Verify build + on-the-fly switch + persistence + fallback → Task 1 Step 7; build+visual check in every task. ✓

**Placeholder scan:** Infra code is complete and concrete. Translation tasks use the single shared Conversion Recipe (DRY) with a worked example, exact file lists, namespaces, and verification — not "similar to Task N" hand-waving. The actual translated strings are data produced during execution (cannot be pre-listed for ~556 strings); `ru` value = verbatim original, `kk` = translation, enforced by Global Constraints. ✓

**Key/namespace consistency:** namespaces are unique per task (`common`,`header`,`landing`,`events`,`eventDetail`,`profile`,`requestForm`,`organizer`,`admin`,`auth`,`support`,`notifications`); every key added to `ru.json` is mirrored in `kk.json` (Global Constraint). ✓
