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
- [Android Studio](https://developer.android.com/studio)  
- [Crowdin](https://crowdin.com/)  
- [Cloudflare](https://www.cloudflare.com/)  

## 🧑‍💻 开发指南 & 使用手册 / Development & Instruction

### 🗄️ 数据库初始化 / Database initialization (Cloudflare D1)

手动创建数据库表（Cloudflare Dashboard → D1 SQL 数据库→ 控制台）  

Manually create database tables (Cloudflare Dashboard → D1 SQL Database → Console)  

```sql
CREATE TABLE users (
  id INTEGER PRIMARY KEY,
  username TEXT,
  password_hash TEXT,
  role TEXT,
  created_at TEXT,
  updated_at TEXT
);

CREATE TABLE orders (
  id INTEGER PRIMARY KEY,
  title TEXT,
  description TEXT,
  nfc_tag TEXT,
  status TEXT,
  assigned_to INTEGER,
  created_at TEXT,
  updated_at TEXT
);

CREATE TABLE order_logs (
  id INTEGER PRIMARY KEY,
  order_id INTEGER,
  action TEXT,
  operator_id INTEGER,
  timestamp TEXT
);
```

### 部署到 / Deploy to <img src="https://cf-assets.www.cloudflare.com/dzlvafdwdttg/69wNwfiY5mFmgpd9eQFW6j/d5131c08085a977aa70f19e7aada3fa9/1pixel-down__1_.svg" width="120" alt="Cloudflare 彩色标识">

GitHub → Cloudflare 绑定后，构建配置如下：

After binding with GitHub → Cloudflare, the build configuration is as follows:

#### 构建命令 / Build command

```
pnpm install
```

#### 部署命令 / Deployment command

```
npx wrangler deploy
```

#### 根目录 / Root directory

```
/cloudflare
```
