# NFCWorkFlow Android App

## 📖 简介 / Introduction

**中文**  
这是 MSc Project 25-26 的 Android 客户端，基于 Jetpack Compose 构建，主要用于 NFC 工单流程演示（登录、角色分流、NFC 读取、设置管理）。

**English**  
This is the Android client for MSc Project 25-26, built with Jetpack Compose for an NFC workflow demo (login, role-based routing, NFC reading, and settings).

## ✨ 功能概览 / Features

- 🔐 登录与注册 / Login and registration
- 👤 管理员与工人角色分流 / Admin and worker role routing
- 📲 NFC 标签读取 / NFC tag reading
- ⚙️ 主题、动态色彩、语言设置 / Theme, dynamic color, and language settings
- 🌐 云端 API 对接（Ktor）/ Cloud API integration (Ktor)

## 🧱 技术栈 / Tech Stack
- Kotlin  
- Jetpack Compose  
  声明式 UI 框架，用来实现页面、组件和状态驱动渲染，减少传统 XML + View 的样板代码  
  Declarative UI toolkit used to build screens/components with state-driven rendering and less XML/View boilerplate
- Material 3 (Experimental Branch)  
  实验性分支，用于尝试较新的 Material 3 组件/行为，界面风格与交互会更前沿但可能有变动  
  Experimental branch, for trying newer Material 3 components/behaviors; UI may be more cutting-edge but less stable.
- AndroidX Navigation Compose   
  管理页面路由与导航栈（如登录页、主页、设置页切换），统一导航逻辑  
  Handles route navigation and back stack transitions (e.g., login/home/settings) in a unified way.
- AndroidX DataStore  
  轻量本地持久化方案，存储偏好配置（如主题、动态色彩、身份信息）并提供 Flow 响应式读取  
  Lightweight local persistence for preferences (theme, dynamic color, auth info) with reactive Flow reads.
- Ktor Client  
  网络请求客户端，负责与后端 API 通信  
  HTTP client used to communicate with backend APIs.
- Gradle Kotlin DSL  
  构建系统与依赖管理，使用 Kotlin 脚本维护编译参数、插件和版本  
  EBuild and dependency system configured via Kotlin scripts for plugins, versions, and compile settings.

## 🎨 界面设计参考 / UI Design References

本项目 Android 端的部分视觉与交互风格参考了以下仓库：  
Some visual and interaction styles of this Android app were inspired by the following repositories:

- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)
- [ReSukiSU/ReSukiSU](https://github.com/ReSukiSU/ReSukiSU)

仅用于学习与研究目的，具体实现已根据本项目需求调整。  
Used for learning and research only; the implementation has been adapted to this project's own requirements.
