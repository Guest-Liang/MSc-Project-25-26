# GitHub Copilot Instructions — NFCWorkFlow (MSc Project 25-26)

## Project Overview

NFCWorkFlow is an NFC-based work order management system with an Android client and a Cloudflare Workers backend.

- **Roles:** Admin, Worker
- **Core features:** User authentication, work order creation/assignment/completion, NFC tag reading, multi-language support (Chinese and English)
- **Backend URL:** `https://msc-project-api.guestliang.icu`

---

## Repository Structure

```
MSc-Project-25-26/
├── .github/
│   ├── copilot-instructions.md               # This file
│   ├── dependabot.yml                         # Dependabot dependency update config (npm + GitHub Actions)
│   └── workflows/
│       ├── build-app.yaml                     # Android APK build and release CI
│       └── crowdin-action.yaml                # Crowdin translation sync
├── cloudflare/                                # Backend: Cloudflare Workers + Hono + D1 (SQLite)
│   ├── src/
│   │   ├── index.js                           # App entry point, route registration
│   │   ├── routes/
│   │   │   ├── auth.js                        # Authentication (login/logout/register)
│   │   │   ├── admin.js                       # Admin endpoints
│   │   │   ├── worker.js                      # Worker endpoints
│   │   │   └── orders.js                      # Order queries and logs
│   │   ├── middleware/
│   │   │   ├── auth.js                        # JWT auth middleware (requireAdmin/requireWorker)
│   │   │   └── apiLog.js                      # API request logging middleware
│   │   └── utils/
│   │       ├── jwt.js                         # JWT sign and verify (HS256, 20-minute expiry)
│   │       ├── bcrypt.js                      # bcrypt password hashing
│   │       ├── status.js                      # Unified status codes (1xxx errors / 9xxx success)
│   │       └── response.js                    # Standardized response format
│   ├── API-Description.md                     # API documentation
│   ├── package.json                           # Dependencies: hono
│   ├── pnpm-lock.yaml                         # pnpm lockfile
│   └── wrangler.toml                          # Cloudflare deployment config
├── nfcworkflow/                               # Frontend: Android Kotlin + Jetpack Compose
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/icu/guestliang/nfcworkflow/
│   │   │   │   ├── MainActivity.kt            # App entry point
│   │   │   │   ├── NFCWorkFlowApp.kt          # Application class
│   │   │   │   ├── data/
│   │   │   │   │   ├── ApiConstants.kt        # Base URL and API path constants
│   │   │   │   │   ├── Models.kt              # Shared data models
│   │   │   │   │   └── PrefsDataStore.kt      # DataStore Preferences (token, role, theme)
│   │   │   │   ├── logging/
│   │   │   │   │   └── AppLogger.kt           # App-wide logging utility
│   │   │   │   ├── navigation/
│   │   │   │   │   └── NavGraph.kt            # Navigation graph and route definitions
│   │   │   │   ├── network/
│   │   │   │   │   ├── ApiClient.kt           # Ktor HTTP client configuration
│   │   │   │   │   └── ApiModels.kt           # API request/response data models
│   │   │   │   ├── nfc/
│   │   │   │   │   └── NfcParser.kt           # NFC tag parsing logic
│   │   │   │   ├── ui/
│   │   │   │   │   ├── HomeScreen.kt          # Home screen (role-aware entry)
│   │   │   │   │   ├── NfcScreen.kt           # NFC scan screen
│   │   │   │   │   ├── SettingsScreen.kt      # App settings screen
│   │   │   │   │   ├── admin/
│   │   │   │   │   │   ├── AdminAssignOrderScreen.kt
│   │   │   │   │   │   ├── AdminCreateOrderScreen.kt
│   │   │   │   │   │   ├── AdminQueryLogsScreen.kt
│   │   │   │   │   │   ├── AdminRegisterWorkerScreen.kt
│   │   │   │   │   │   ├── AdminSearchOrdersScreen.kt
│   │   │   │   │   │   └── AdminViewModel.kt
│   │   │   │   │   ├── components/            # Shared UI components
│   │   │   │   │   │   ├── SplicedWidgets.kt
│   │   │   │   │   │   └── TimePickerWidgets.kt
│   │   │   │   │   ├── login/
│   │   │   │   │   │   ├── LoginScreen.kt
│   │   │   │   │   │   ├── LoginViewModel.kt
│   │   │   │   │   │   ├── RegisterScreen.kt
│   │   │   │   │   │   └── RegisterViewModel.kt
│   │   │   │   │   ├── theme/                 # Material 3 theme
│   │   │   │   │   │   ├── Color.kt
│   │   │   │   │   │   ├── Dimensions.kt
│   │   │   │   │   │   ├── Theme.kt
│   │   │   │   │   │   └── Typography.kt
│   │   │   │   │   ├── view/
│   │   │   │   │   │   └── MainPagerScreen.kt # Main pager/tab container
│   │   │   │   │   └── worker/
│   │   │   │   │       ├── CompleteOrderScreen.kt
│   │   │   │   │       ├── ViewOrdersScreen.kt
│   │   │   │   │       └── WorkerViewModel.kt
│   │   │   │   └── utils/
│   │   │   │       ├── ContextExt.kt          # Context extension functions
│   │   │   │       └── LocalizationUtils.kt   # Localization helpers
│   │   │   └── res/                           # Android resources
│   │   │       ├── drawable/                  # Vector drawables (launcher icons)
│   │   │       ├── mipmap-*/                  # Launcher icon densities (hdpi→xxxhdpi)
│   │   │       ├── values/                    # Default strings, colors, themes
│   │   │       ├── values-en/                 # English string overrides
│   │   │       ├── values-night/              # Dark theme overrides
│   │   │       └── xml/                       # Backup rules, locales config
│   │   ├── build.gradle.kts                   # App module build config
│   │   └── proguard-rules.pro                 # ProGuard / R8 rules
│   ├── gradle/
│   │   ├── gradle-daemon-jvm.properties       # Gradle daemon JVM config
│   │   ├── libs.versions.toml                 # Version catalog (contains appVersion)
│   │   └── wrapper/
│   │       ├── gradle-wrapper.jar
│   │       └── gradle-wrapper.properties
│   ├── .editorconfig                          # Code style config (import order, line length)
│   ├── build.gradle.kts                       # Root Gradle build file
│   ├── gradle.properties                      # Gradle properties
│   ├── gradlew                                # Gradle wrapper script (Unix)
│   ├── gradlew.bat                            # Gradle wrapper script (Windows)
│   └── settings.gradle.kts                    # Gradle settings (module declarations)
├── .gitignore                                 # Root gitignore
├── crowdin.yml                                # Crowdin translation project config
└── README.md                                  # Project documentation (Chinese and English)
```

---

## Build & Run

### Android Client

```bash
cd nfcworkflow

# Debug build
./gradlew assembleDebug

# Release build (requires signing config, see below)
./gradlew assembleRelease

# Install to connected device/emulator
./gradlew installDebug
```

**Release signing env vars:**

| Variable | Description |
|--------|------|
| `ANDROID_KEYSTORE_PATH` | Path to the keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

**Version management:**
- `versionName`: Edit the `appVersion` field in `gradle/libs.versions.toml`
- `versionCode`: Automatically computed by CI from the git commit count inside `nfcworkflow/`
- Release tag format: `v{versionName}+{versionCode}` (e.g. `v0.4.5+2`)

### Backend (Cloudflare Workers)

```bash
cd cloudflare
pnpm install

# Local development
npx wrangler dev

# Deploy to production
npx wrangler deploy
```

**Database initialization:** Manually execute the SQL statements from `README.md` in the Cloudflare D1 console.

---

## Testing

> **Note:** This project currently has no automated tests (no `*Test.kt` or `*.test.js` files). All verification is done through manual testing.

To add tests:
- Android: Create JUnit/Mockk unit test files under `app/src/test/`
- Backend: Add Jest or Vitest tests in the `cloudflare/` directory

---

## Tech Stack Details

### Android

| Library | Purpose |
|---|---|
| Kotlin | Primary language |
| Jetpack Compose BOM | Declarative UI framework |
| Material 3 | UI components and theming |
| Navigation Compose | Screen navigation |
| Lifecycle Runtime | ViewModel and lifecycle |
| DataStore Preferences | Local key-value storage |
| Ktor Client | HTTP networking |
| Kotlinx Serialization | JSON serialization |
| Android Gradle Plugin | Build system |

- **Min SDK:** 31 (Android 12)
- **Target SDK:** 36 (Android 15)
- **Java Version:** 24

### Backend

| Technology | Description |
|---|---|
| Cloudflare Workers | Serverless runtime |
| Hono | Lightweight web framework |
| Cloudflare D1 | Managed SQLite database |
| JWT | HS256, 20-minute expiry |
| bcrypt | Password hashing |

---

## Architecture Patterns

### Android

- **MVVM:** ViewModel + Kotlin Flow for reactive state management
- **Single Activity + Compose Navigation:** All screen routes registered in `NavGraph.kt`
- **DataStore:** Persists JWT token, user role, theme preferences, etc.
- **Ktor Client:** Configured in `network/ApiClient.kt`; all API calls go through this client

### Navigation Conventions

- **`dropUnlessResumed`** (from `androidx.lifecycle.compose`) is **only for navigation-related click handlers** (e.g. `navController.navigate()`, `navController.popBackStack()`). Do **not** use it for local UI state changes.

### Component API

- `SplicedBaseWidget` and `SplicedJumpPageWidget` accept `onClick: () -> Unit` and `onLongClick: () -> Unit` callbacks (**not** `(Offset) -> Unit`).

### Backend

- **Middleware chain:** `apiLog` → `auth` (JWT verification) → route handler
- **Role control:** `requireAdmin` / `requireWorker` middleware
- **Response format:** All responses use the unified format described below

---

## API Specification

### Standard Response Format

```json
{
  "success": true | false,
  "code": 0,
  "message": "description",
  "data": {} | [] | null
}
```

### Status Code Conventions

| Range | Meaning |
|------|------|
| `0` | Success (generic) |
| `1xxx` | User/auth errors (USER_NOT_EXIST, WRONG_PASSWORD, TOKEN_INVALID, etc.) |
| `2xxx` | Permission errors (NO_PERMISSION, ADMIN_REQUIRED) |
| `3xxx` | Worker-related errors |
| `4xxx` | Order-related errors (ORDER_NOT_FOUND, ORDER_NOT_COMPLETABLE, etc.) |
| `5xxx` | System errors (DB_ERROR, INTERNAL_ERROR) |
| `9xxx` | Success detail codes (LOGIN_SUCCESS, ORDER_CREATED_SUCCESS, etc.) |

### Endpoints

| Method | Path | Auth | Description |
|------|------|------|------|
| `POST` | `/auth/login` | Public | Login, returns JWT |
| `POST` | `/auth/logout` | Authenticated | Logout, revokes token |
| `POST` | `/auth/register-admin` | Public (init) | Register admin |
| `POST` | `/auth/register-worker` | Admin | Register worker |
| `GET` | `/admin/workers` | Admin | Worker list (paginated by created_at DESC) |
| `POST` | `/admin/orders/create` | Admin | Create work order |
| `POST` | `/admin/orders/assign` | Admin | Assign order to worker |
| `GET` | `/worker/orders` | Worker | View assigned orders |
| `POST` | `/worker/orders/complete` | Worker | Complete an order |
| `GET` | `/orders/logs` | Admin | Order activity logs (multi-filter) |
| `GET` | `/orders/search` | Admin | Advanced order search |
| `GET` | `/healthz` | Public | Health check |

---

## Database Schema

### `users`

| Column | Type | Description |
|----|------|------|
| id | INTEGER PK | User ID |
| username | TEXT | Username |
| password_hash | TEXT | bcrypt hashed password |
| role | TEXT | `admin` or `worker` |
| token | TEXT | Current valid JWT (used for revocation) |
| created_at | DATETIME | Creation timestamp |
| updated_at | DATETIME | Last update timestamp |

### `orders`

| Column | Type | Description |
|----|------|------|
| id | INTEGER PK | Order ID |
| title | TEXT | Order title |
| description | TEXT | Order description |
| nfc_tag | TEXT | NFC tag identifier (optional) |
| status | TEXT | `created` / `assigned` / `completed` |
| assigned_to | INTEGER FK | Worker user ID |
| created_at | DATETIME | Creation timestamp |
| updated_at | DATETIME | Last update timestamp |

### `order_logs`

| Column | Type | Description |
|----|------|------|
| id | INTEGER PK | Log ID |
| order_id | INTEGER FK | Order ID |
| action | TEXT | `created` / `assigned` / `completed` |
| operator_id | INTEGER FK | User who performed the action |
| timestamp | DATETIME | Action timestamp |

### `api_logs`

| Column | Type | Description |
|----|------|------|
| id | INTEGER PK | Log ID |
| method | TEXT | HTTP method |
| path | TEXT | Request path |
| action | TEXT | Action description |
| user_id | INTEGER | User who made the request |
| user_role | TEXT | User role |
| success | INTEGER | Success flag (0/1) |
| response_code | INTEGER | Response status code |
| response_message | TEXT | Response message |
| ip | TEXT | Client IP |
| user_agent | TEXT | Client user agent |
| request_meta | TEXT | JSON request metadata |
| created_at | DATETIME | Request timestamp |

**Indexes:** `idx_api_logs_created_at`, `idx_api_logs_user_id`, `idx_api_logs_path`

---

## Coding Style & Conventions

### Android (Kotlin)

- Classes: PascalCase (e.g. `ApiClient`, `LoginScreen`)
- Functions/variables: camelCase
- Constants: UPPER_SNAKE_CASE
- File names match class names
- No wildcard imports (`import *`): max 99 configured in `.editorconfig`
- Import order: `*`, `java`, `javax`, `kotlin`, `android`, `androidx`
- All API data models annotated with `@Serializable`
- Compose state management: `remember` + `collectAsState`
- Routes use sealed classes for type safety

### Backend (JavaScript)

- Functional route handlers (async/await)
- Middleware composition pattern
- All database operations use parameterized queries (SQL injection prevention)
- Status codes centralized in `utils/status.js`
- Response format generated by `utils/response.js`

---

## CI/CD Notes

### build-app.yaml (Android APK build)

- **Triggers:** Push to `main` branch (paths: `nfcworkflow/**` or `.github/workflows/build-app.yaml`), or manual dispatch
- **Environment:** Ubuntu latest, JDK 24 (Temurin), Android SDK
- **Artifacts:** Signed release APK + debug APK
- **Release:** Automatically creates a GitHub Release with changelog on push to main

### crowdin-action.yaml (Translation sync)

- Syncs translations from Crowdin to the `L10n-dev` branch, then merges into `dev`
- Supported languages: `zh-cn` (Simplified Chinese), `en` (English)

---

## Known Issues & Workarounds

- **Navigation double-trigger:** Compose recomposition can cause navigation click events to fire multiple times. Fix: use `dropUnlessResumed` on all navigation-related click handlers (navigation only, not local UI state changes).
- **NFC optional:** The NFC feature in `AndroidManifest.xml` is set to `android:required="false"`, so non-NFC devices can install the app but cannot use NFC features.
- **Token expiry:** JWT expires after 20 minutes; the frontend must redirect users to login on a 401 response.
- **Version code calculation:** `versionCode` is computed in CI via `git rev-list --count HEAD -- nfcworkflow/`; local builds use a hard-coded value (currently 1).

---

## Localization

- String resources are maintained in `nfcworkflow/app/src/main/res/values/` and corresponding language subdirectories
- Crowdin manages translations via `crowdin.yml`
- When adding new UI text, add entries to both the English and Chinese resource files

---

## Code Review Guidelines

> **Important:** The following rules must be strictly followed.

1. **Language:** When performing a code review, respond in **Chinese**.

2. **Change explanations:** If a suggested change is given, explain in detail why this action was taken, including:
   - What problem the change solves
   - Why this approach was chosen over alternatives
   - How the change impacts code quality, performance, security, or maintainability
   - Code examples showing the expected result, if necessary
