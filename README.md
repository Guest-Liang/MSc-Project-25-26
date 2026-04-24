# MSc Project 25-26

## <img alt="WakaTime Logo" src="https://wakatime.com/static/img/wakatime.svg" width="25"> Wakatime badge

[![wakatime](https://wakatime.com/badge/github/Guest-Liang/MSc-Project-25-26.svg)](https://wakatime.com/badge/github/Guest-Liang/MSc-Project-25-26)

## 📖 简介 / Introduction

**中文**  
如你所见，这是一个硕士项目

**English**  
As you can see, this is a MSc Project.

## 🌍 本地化翻译 / Localization

本项目使用 [Crowdin](https://zh.crowdin.com/project/nfcworkflow) 作为客户端文本翻译平台

This project uses [Crowdin](https://zh.crowdin.com/project/nfcworkflow) as a client text translation platform

| Language | Status |  
|----------|--------|  
| zh-cn | Originally Support |  
| en | ![en translation](https://img.shields.io/badge/dynamic/json?color=blue&label=en&style=flat&logo=crowdin&query=%24.progress.0.data.translationProgress&url=https%3A%2F%2Fbadges.awesome-crowdin.com%2Fstats-17447424-849246.json) |  

## ⚙️ 使用的工具 / Tech Stack
- [Visual Studio Code](https://code.visualstudio.com/)
- [Android Studio](https://developer.android.com/studio?hl=zh-cn)  
- [Crowdin](https://zh.crowdin.com/)  
- [Cloudflare](https://www.cloudflare.com/zh-cn/)  

## 🧑‍💻 开发指南 & 使用手册 / Development & Instruction

### 🗄️ 数据库初始化 / Database initialization (Cloudflare D1)

手动创建数据库表（Cloudflare Dashboard → D1 SQL 数据库→ 控制台）  

Manually create database tables (Cloudflare Dashboard → D1 SQL Database → Console)  

```sql
CREATE TABLE users (
  id INTEGER PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  password_hash TEXT,
  role TEXT,
  created_at TEXT,
  updated_at TEXT,
  token TEXT
);

CREATE TABLE orders (
  id INTEGER PRIMARY KEY,
  title TEXT,
  description TEXT,
  nfc_tag TEXT,
  status TEXT,
  assigned_to INTEGER,
  created_at TEXT,
  updated_at TEXT,
  order_type TEXT NOT NULL DEFAULT 'standard',
  target_uid_hex TEXT,
  location_code TEXT,
  display_name TEXT,
  assigned_at TEXT,
  completed_at TEXT,
  sequence_total_steps INTEGER NOT NULL DEFAULT 0,
  sequence_completed_steps INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE order_steps (
  id INTEGER PRIMARY KEY,
  order_id INTEGER NOT NULL,
  step_index INTEGER NOT NULL,
  target_uid_hex TEXT NOT NULL,
  location_code TEXT,
  display_name TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(order_id, step_index),
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE order_logs (
  id INTEGER PRIMARY KEY,
  order_id INTEGER,
  action TEXT,
  operator_id INTEGER,
  timestamp TEXT,
  result TEXT,
  step_index INTEGER,
  scan_uid_hex TEXT,
  expected_uid_hex TEXT,
  location_code TEXT,
  display_name TEXT,
  details_json TEXT,
  FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL ON UPDATE CASCADE,
  FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE
);

CREATE TABLE api_logs (
  id INTEGER PRIMARY KEY,
  method TEXT NOT NULL,
  path TEXT NOT NULL,
  action TEXT NOT NULL,
  user_id INTEGER,
  user_role TEXT,
  success INTEGER NOT NULL,
  response_code INTEGER,
  response_message TEXT,
  ip TEXT,
  user_agent TEXT,
  request_meta TEXT,
  created_at TEXT NOT NULL
);

CREATE INDEX idx_orders_order_type ON orders(order_type);
CREATE INDEX idx_orders_target_uid_hex ON orders(target_uid_hex);
CREATE INDEX idx_orders_assigned_to_status ON orders(assigned_to, status);
CREATE INDEX idx_orders_completed_at ON orders(completed_at DESC);

CREATE INDEX idx_order_steps_order_id_step_index ON order_steps(order_id, step_index);
CREATE INDEX idx_order_steps_target_uid_hex ON order_steps(target_uid_hex);

CREATE INDEX idx_order_logs_order_id_timestamp ON order_logs(order_id, timestamp DESC);
CREATE INDEX idx_order_logs_operator_id_timestamp ON order_logs(operator_id, timestamp DESC);
CREATE INDEX idx_order_logs_action_result ON order_logs(action, result);
CREATE INDEX idx_order_logs_scan_uid_hex ON order_logs(scan_uid_hex);
CREATE INDEX idx_order_logs_expected_uid_hex ON order_logs(expected_uid_hex);

CREATE INDEX idx_api_logs_created_at ON api_logs(created_at DESC);
CREATE INDEX idx_api_logs_user_id ON api_logs(user_id);
CREATE INDEX idx_api_logs_path ON api_logs(path);
```

> [!WARNING]
> 首次初始化时，需要先在 Cloudflare Dashboard 的 D1 SQL 控制台手动插入一个初始管理员账号，然后通过 `/auth/login` 获取 token。  
>
> During first-time setup, manually insert an initial administrator account in the Cloudflare D1 SQL console, then log in via `/auth/login` to obtain a token.

## API 文档 / API Documentation

详细接口说明、canonical payload 与返回示例见 [cloudflare/API-Description.md](./cloudflare/API-Description.md)。

See [cloudflare/API-Description.md](./cloudflare/API-Description.md) for detailed API descriptions, canonical payloads, and response examples.

## <img src="https://cf-assets.www.cloudflare.com/dzlvafdwdttg/69wNwfiY5mFmgpd9eQFW6j/d5131c08085a977aa70f19e7aada3fa9/1pixel-down__1_.svg" width="150" alt="Cloudflare 彩色标识"> 部署 / Deployment

GitHub 与 Cloudflare 绑定后，Cloudflare Pages / Workers 构建配置如下。  

After binding GitHub to Cloudflare, use the following build configuration.

### 构建命令 / Build command

```bash
pnpm install
```

### 部署命令 / Deployment command

```bash
npx wrangler deploy
```

### 根目录 / Root directory

```text
/cloudflare
```

### 变量和机密 / Environment variables

> [!WARNING]
> 需要在 **Workers & Pages → 对应 Worker → Settings → Variables and Secrets** 中配置 JWT 签名密钥。
> 
> Before deploying the Cloudflare Worker, configure the JWT signing key in **Workers & Pages → your Worker → Settings → Variables and Secrets**.

| Type | Name | Value |
|---|---|---|
| Secret | `JWT_SECRET` | A random 32-byte or longer secret value |

| 类型 | 名称 | 值 |
|---|---|---|
| 密钥 | `JWT_SECRET` | 随机生成的 32 字节或更长密钥 |

## <img src="https://support.crowdin.com/assets/logos/core-logo/svg/crowdin-core-logo-cDark.svg" width="120" alt="Crowdin 标识"> 翻译流程 / Translation Workflow

前往 [Crowdin 项目页面](https://zh.crowdin.com/project/nfcworkflow/integrations/system/github) 检查同步时间，在主页选择英文进行翻译。翻译完成后等待 Crowdin 同步到 `L10n-dev` 分支，再合并到 `dev`。

Visit the [Crowdin project page](https://zh.crowdin.com/project/nfcworkflow/integrations/system/github) to check sync status, then select English on the homepage for translation. After translation, wait for Crowdin to sync to the `L10n-dev` branch, and then merge into `dev`.
