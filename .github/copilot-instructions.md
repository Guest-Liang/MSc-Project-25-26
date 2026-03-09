# GitHub Copilot Instructions — NFCWorkFlow (MSc Project 25-26)

## 项目概述 / Project Overview

NFCWorkFlow 是一个基于 NFC 的工单管理系统，由 Android 客户端和 Cloudflare Workers 后端两部分组成。
NFCWorkFlow is an NFC-based work order management system with an Android client and a Cloudflare Workers backend.

- **用户角色 / Roles:** Admin（管理员）、Worker（工人）
- **核心功能 / Core features:** 用户认证、工单创建/分配/完成、NFC 标签读取、多语言支持（中英文）
- **部署地址 / Backend URL:** `https://msc-project-api.guestliang.icu`

---

## 仓库结构 / Repository Structure

```
MSc-Project-25-26/
├── .github/
│   ├── copilot-instructions.md   # 本文件
│   └── workflows/
│       ├── build-app.yaml        # Android APK 构建与发布 CI
│       └── crowdin-action.yaml   # Crowdin 翻译同步
├── cloudflare/                   # 后端：Cloudflare Workers + Hono + D1 (SQLite)
│   ├── src/
│   │   ├── index.js              # 应用入口，路由注册
│   │   ├── routes/               # 路由处理器
│   │   │   ├── auth.js           # 认证（登录/登出/注册）
│   │   │   ├── admin.js          # 管理员接口
│   │   │   ├── worker.js         # 工人接口
│   │   │   └── orders.js         # 工单查询与日志
│   │   ├── middleware/
│   │   │   ├── auth.js           # JWT 鉴权中间件（requireAdmin/requireWorker）
│   │   │   └── apiLog.js         # API 请求日志中间件
│   │   └── utils/
│   │       ├── jwt.js            # JWT 签发与验证（HS256，20 分钟过期）
│   │       ├── bcrypt.js         # 密码 bcrypt 哈希
│   │       ├── status.js         # 统一状态码（1xxx 错误 / 9xxx 成功）
│   │       └── response.js       # 标准化响应格式
│   ├── package.json              # 依赖：hono ^4
│   └── wrangler.toml             # Cloudflare 部署配置
├── nfcworkflow/                  # 前端：Android Kotlin + Jetpack Compose
│   ├── app/src/main/
│   │   ├── java/icu/guestliang/nfcworkflow/
│   │   │   ├── data/             # 本地存储（DataStore Preferences）
│   │   │   ├── navigation/       # NavGraph 路由配置
│   │   │   ├── network/          # Ktor 客户端 + API 数据模型
│   │   │   ├── nfc/              # NFC 标签解析
│   │   │   ├── ui/               # 所有 Compose 界面
│   │   │   │   ├── admin/        # 管理员专属界面
│   │   │   │   ├── worker/       # 工人专属界面
│   │   │   │   ├── components/   # 公共 UI 组件
│   │   │   │   └── theme/        # Material 3 主题
│   │   │   └── utils/            # 工具函数
│   │   └── res/                  # 资源文件（布局、字符串等）
│   ├── gradle/
│   │   └── libs.versions.toml    # 版本目录（包含 appVersion）
│   └── build.gradle.kts          # 根 Gradle 构建文件
├── crowdin.yml                   # Crowdin 翻译项目配置
└── README.md                     # 项目总说明（中英双语）
```

---

## 构建与运行 / Build & Run

### Android 客户端

```bash
cd nfcworkflow

# 调试构建
./gradlew assembleDebug

# 发布构建（需签名配置，见下方）
./gradlew assembleRelease

# 安装到已连接的设备/模拟器
./gradlew installDebug
```

**发布签名所需环境变量 / Release signing env vars:**

| 变量名 | 说明 |
|--------|------|
| `ANDROID_KEYSTORE_PATH` | keystore 文件路径 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key 密码 |

**版本管理 / Version management:**
- `versionName`：在 `gradle/libs.versions.toml` 的 `appVersion` 字段中修改
- `versionCode`：由 CI 根据 `nfcworkflow/` 目录内的 git commit 数量自动计算
- Release 标签格式：`v{versionName}+{versionCode}`（如 `v0.4.5+2`）

### 后端（Cloudflare Workers）

```bash
cd cloudflare
pnpm install

# 本地开发
npx wrangler dev

# 部署到生产
npx wrangler deploy
```

**数据库初始化：** 在 Cloudflare D1 控制台中手动执行 `README.md` 中的 SQL 建表语句。

---

## 测试 / Testing

> **注意 / Note:** 本项目目前没有自动化测试（无 `*Test.kt` 或 `*.test.js` 文件）。所有验证通过手动测试进行。

如需添加测试：
- Android：在 `app/src/test/` 下新建 JUnit/Mockk 单元测试文件
- 后端：使用 Jest 或 Vitest 在 `cloudflare/` 目录添加测试

---

## 技术栈详情 / Tech Stack Details

### Android

| 库 / Library | 版本 | 用途 |
|---|---|---|
| Kotlin | 2.3.10 | 主开发语言 |
| Jetpack Compose BOM | 2026.02.01 | 声明式 UI 框架 |
| Material 3 | (via BOM) | UI 组件与主题 |
| Navigation Compose | 2.9.7 | 屏幕导航 |
| Lifecycle Runtime | 2.10.0 | ViewModel 与生命周期 |
| DataStore Preferences | 1.3.0-alpha06 | 本地键值存储 |
| Ktor Client | 3.4.1 | HTTP 网络请求 |
| Kotlinx Serialization | (via BOM) | JSON 序列化 |
| Android Gradle Plugin | 9.0.1 | 构建系统 |

- **Min SDK:** 31（Android 12）
- **Target SDK:** 36（Android 15）
- **Java Version:** 24

### 后端

| 技术 | 版本/说明 |
|---|---|
| Cloudflare Workers | 无服务器运行环境 |
| Hono | ^4（轻量 Web 框架） |
| Cloudflare D1 | SQLite 数据库（托管于 Cloudflare） |
| JWT | HS256 算法，20 分钟过期 |
| bcrypt | 密码哈希 |

---

## 架构模式 / Architecture Patterns

### Android

- **MVVM：** ViewModel + Kotlin Flow 响应式状态管理
- **单一 Activity + Compose Navigation：** 在 `NavGraph.kt` 中统一注册所有屏幕路由
- **DataStore：** 用于持久化 JWT token、用户角色、主题偏好等
- **Ktor Client：** 在 `network/ApiClient.kt` 中配置，所有 API 调用通过此客户端发出

### 导航注意事项 / Navigation Conventions

- **`dropUnlessResumed`**（来自 `androidx.lifecycle.compose`）**仅用于导航相关的点击处理器**（如 `navController.navigate()`、`navController.popBackStack()`），**不得用于本地 UI 状态变更**。

### 组件 API / Component API

- `SplicedBaseWidget` 和 `SplicedJumpPageWidget` 接受 `onClick: () -> Unit` 和 `onLongClick: () -> Unit` 回调（**不是** `(Offset) -> Unit`）。

### 后端

- **中间件链：** `apiLog` → `auth`（JWT 验证）→ 路由处理器
- **角色控制：** `requireAdmin` / `requireWorker` 中间件
- **响应格式：** 全部使用统一格式，见下方

---

## API 规范 / API Specification

### 标准响应格式

```json
{
  "success": true | false,
  "code": 0,
  "message": "描述信息",
  "data": {} | [] | null
}
```

### 状态码约定

| 范围 | 含义 |
|------|------|
| `0` | 成功（通用） |
| `1xxx` | 用户/认证错误（USER_NOT_EXIST、WRONG_PASSWORD、TOKEN_INVALID 等） |
| `2xxx` | 权限错误（NO_PERMISSION、ADMIN_REQUIRED） |
| `3xxx` | 工人相关错误 |
| `4xxx` | 工单相关错误（ORDER_NOT_FOUND、ORDER_NOT_COMPLETABLE 等） |
| `5xxx` | 系统错误（DB_ERROR、INTERNAL_ERROR） |
| `9xxx` | 成功详细码（LOGIN_SUCCESS、ORDER_CREATED_SUCCESS 等） |

### 主要端点

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| `POST` | `/auth/login` | 公开 | 登录，返回 JWT |
| `POST` | `/auth/logout` | 已登录 | 登出，撤销 token |
| `POST` | `/auth/register-admin` | 公开（初始化） | 注册管理员 |
| `POST` | `/auth/register-worker` | Admin | 注册工人 |
| `GET` | `/admin/workers` | Admin | 工人列表（按 created_at DESC 分页） |
| `POST` | `/admin/orders/create` | Admin | 创建工单 |
| `POST` | `/admin/orders/assign` | Admin | 分配工单给工人 |
| `GET` | `/worker/orders` | Worker | 查看已分配工单 |
| `POST` | `/worker/orders/complete` | Worker | 完成工单 |
| `GET` | `/orders/logs` | Admin | 工单操作日志（支持多维过滤） |
| `GET` | `/orders/search` | Admin | 高级工单搜索 |
| `GET` | `/healthz` | 公开 | 服务健康检查 |

---

## 数据库 Schema / Database Schema

### `users`

| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 用户 ID |
| username | TEXT | 用户名 |
| password_hash | TEXT | bcrypt 哈希密码 |
| role | TEXT | `admin` 或 `worker` |
| token | TEXT | 当前有效 JWT（用于撤销） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### `orders`

| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 工单 ID |
| title | TEXT | 工单标题 |
| description | TEXT | 工单描述 |
| nfc_tag | TEXT | NFC 标签标识（可选） |
| status | TEXT | `created` / `assigned` / `completed` |
| assigned_to | INTEGER FK | 分配给的工人 ID |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### `order_logs`

| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 日志 ID |
| order_id | INTEGER FK | 工单 ID |
| action | TEXT | `created` / `assigned` / `completed` |
| operator_id | INTEGER FK | 操作者用户 ID |
| timestamp | DATETIME | 操作时间 |

### `api_logs`

| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 日志 ID |
| method | TEXT | HTTP 方法 |
| path | TEXT | 请求路径 |
| action | TEXT | 操作描述 |
| user_id | INTEGER | 操作用户 ID |
| user_role | TEXT | 用户角色 |
| success | INTEGER | 是否成功（0/1） |
| response_code | INTEGER | 响应状态码 |
| response_message | TEXT | 响应消息 |
| ip | TEXT | 客户端 IP |
| user_agent | TEXT | 客户端 UA |
| request_meta | TEXT | JSON 格式的请求元数据 |
| created_at | DATETIME | 请求时间 |

**索引：** `idx_api_logs_created_at`、`idx_api_logs_user_id`、`idx_api_logs_path`

---

## 代码风格与约定 / Coding Style & Conventions

### Android (Kotlin)

- 类名：PascalCase（如 `ApiClient`、`LoginScreen`）
- 函数/变量：camelCase
- 常量：UPPER_SNAKE_CASE
- 文件名与类名保持一致
- 禁止星号导入（`import *`）：`.editorconfig` 中已配置 max 99
- 导入顺序：`*`, `java`, `javax`, `kotlin`, `android`, `androidx`
- `@Serializable` 注解标注所有 API 数据模型
- Compose 状态管理：`remember` + `collectAsState`
- 路由使用 sealed class 确保类型安全

### 后端 (JavaScript)

- 函数式路由处理器（async/await）
- 中间件组合（compose pattern）
- 所有数据库操作使用参数化查询（防 SQL 注入）
- 状态码统一在 `utils/status.js` 中维护
- 响应格式统一在 `utils/response.js` 中生成

---

## CI/CD 说明 / CI/CD Notes

### build-app.yaml（Android APK 构建）

- **触发条件：** push 到 `main` 分支（`nfcworkflow/**` 或 `.github/workflows/build-app.yaml` 路径变更），或手动触发
- **运行环境：** Ubuntu latest，JDK 24（Temurin），Android SDK
- **产物：** Release APK（已签名）+ Debug APK
- **发布：** push 到 main 时自动创建 GitHub Release，包含 changelog

### crowdin-action.yaml（翻译同步）

- 将 Crowdin 平台的翻译同步到 `L10n-dev` 分支，再合并到 `dev`
- 支持语言：`zh-cn`（中文简体）、`en`（英文）

---

## 已知问题与解决方案 / Known Issues & Workarounds

- **navigation 重复触发：** 由于 Compose 重组可能导致导航点击事件被触发多次。解决方案：对所有导航相关点击处理器使用 `dropUnlessResumed`（仅限导航，不用于 UI 状态变更）。
- **NFC 可选功能：** `AndroidManifest.xml` 中 NFC feature 设置为 `android:required="false"`，非 NFC 设备可安装但无法使用 NFC 功能。
- **Token 过期：** JWT 20 分钟过期，前端需要在 401 响应时重新引导用户登录。
- **版本码计算：** `versionCode` 在 CI 中通过 `git rev-list --count HEAD -- nfcworkflow/` 计算，本地构建时使用硬编码值（当前为 1）。

---

## 本地化 / Localization

- 字符串资源在 `nfcworkflow/app/src/main/res/values/` 及对应语言目录下维护
- Crowdin 负责管理翻译，通过 `crowdin.yml` 配置
- 新增 UI 文本时应同时在英文和中文资源文件中添加对应条目

---

## 代码审查规范 / Code Review Guidelines

> **重要 / Important:** 以下规范必须严格遵守。

1. **语言要求：** 进行代码审查时，所有审查意见和反馈必须使用**中文**回复。

2. **变更说明要求：** 如果审查中提出了具体的修改建议，必须详细说明**为什么要进行该操作**，包括：
   - 该变更解决了什么问题
   - 为什么选择这种方式而非其他方式
   - 该变更对代码质量、性能、安全性或可维护性有何影响
   - 如有必要，给出代码示例说明预期的修改结果
