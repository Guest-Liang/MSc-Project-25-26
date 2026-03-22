# 📘 API 文档 / API Documentation   
所有 API 采用 JSON 格式返回，并使用标准化格式   
All APIs return data in JSON format and using a standardized format.   

```json
{
  "success": true | false,
  "code": number,
  "message": string,
  "data": {} | [] | null
}
```  

| 字段 | 类型 | 说明 | Description |
| --- | --- | --- | --- |
| `success` | boolean               | 请求是否成功 | Whether the request succeeded |
| `code`    | number                | 成功或错误码 | Success / error code |
| `message` | string                | 对应消息 | Human-readable message |
| `data`    | object / array / null | 返回数据内容 | Returned payload |

### Status Codes / 状态码
`cloudflare/src/utils/status.js`

---

# 🔐 身份验证 / Authentication
## **POST /auth/login**
用户登录，返回 JWT   
User login, returned JWT.   
### Body
```json
{ "username": "admin", "password": "123456" }
```
### Notes / 说明
- 若该用户已有有效 token，则直接返回旧 token  
  If the user already has a valid token, the existing token is returned.
- 若没有有效 token，则签发新 token  
  If no valid token exists, a new token is generated.
- 若请求体不合法（缺字段、非字符串、空字符串或纯空白字符串），返回 `1008 Username and password must be non-empty strings`。  
  If the request body is invalid (missing fields, non-string values, empty strings, or whitespace-only strings), it returns `1008 Username and password must be non-empty strings`.
### Response Example
```json
{
  "success": true,
  "code": 9011,
  "message": "Token generated successfully",
  "data": {
    "token": "<jwt-token>",
    "role": "admin"
  }
}
```

## **POST /auth/logout**
用户退出登录  
User logout.   
### Headers
```
Authorization: Bearer <token>
```
### Notes / 说明
- 会清空数据库中的当前 token。  
  The current token stored in DB will be cleared.
### Response Example
```json
{
  "success": true,
  "code": 9001,
  "message": "Logout success",
  "data": {}
}
```

### Auth Flow Notes / 认证流程说明
- 登录接口返回 `1001 User does not exist` 时，前端应引导到注册接口。  
  When `/auth/login` returns `1001 User does not exist`, the frontend should guide the user to a registration API.
- 登录接口返回 `1002 Wrong password` 时，前端应引导到重置密码接口。  
  When `/auth/login` returns `1002 Wrong password`, the frontend should guide the user to a password reset API.
- 从当前版本开始，注册接口与重置密码接口完全拆分：`register-*` 只负责创建账号，`reset-*-password` 只负责修改已存在账号的密码。  
  In the current version, registration and password reset are fully separated: `register-*` only creates accounts, and `reset-*-password` only updates passwords for existing accounts.
- 所有带 `username` / `password` 的认证接口都会做统一请求体验证：两个字段都必须是 string，`username.trim()` 不能为空，`password.trim()` 不能为空；否则返回 `1008 Username and password must be non-empty strings`。  
  All auth endpoints that accept `username` / `password` apply the same payload validation: both fields must be strings, `username.trim()` must not be empty, and `password.trim()` must not be empty; otherwise the API returns `1008 Username and password must be non-empty strings`.
- 服务端会在查询和写库前使用去掉首尾空白后的 `username`；`password` 仅做非空校验，不会自动裁剪后再存储。  
  Before querying or writing to the database, the server uses the trimmed `username`; `password` is only validated for non-emptiness and is not auto-trimmed before storage.

## **POST /auth/register-admin**
注册管理员账号   
Register administrator account   
### Headers
```
Authorization: Bearer <admin-token>
```
### Body
```json
{ "username": "root", "password": "123456" }
```
### Notes / 说明
- 首次部署时，请先在 Cloudflare D1 控制台手动插入一个初始管理员账号，再通过登录获取 token。  
  On first deployment, manually insert an initial administrator account in the Cloudflare D1 console, then log in to obtain a token.
- 若用户名已存在（无论现有账号角色为何），返回 `1005 User already exists`。  
  If the username already exists, regardless of the existing account role, it returns `1005 User already exists`.
- 若请求体不合法（缺字段、非字符串、空字符串或纯空白字符串），返回 `1008 Username and password must be non-empty strings`。  
  If the request body is invalid (missing fields, non-string values, empty strings, or whitespace-only strings), it returns `1008 Username and password must be non-empty strings`.
### Response Example
```json
{
  "success": true,
  "code": 9006,
  "message": "Admin created success",
  "data": {}
}
```

## **POST /auth/register-worker**   
注册工人账号（需要管理员 Token）   
Register a worker account (administrator token required)   
### Headers
```
Authorization: Bearer <admin-token>
```
### Body
```json
{ "username": "worker1", "password": "123456" }
```
### Notes / 说明
- 若用户名已存在（无论现有账号角色为何），返回 `1005 User already exists`。  
  If the username already exists, regardless of the existing account role, it returns `1005 User already exists`.
- 若请求体不合法（缺字段、非字符串、空字符串或纯空白字符串），返回 `1008 Username and password must be non-empty strings`。  
  If the request body is invalid (missing fields, non-string values, empty strings, or whitespace-only strings), it returns `1008 Username and password must be non-empty strings`.
### Response Example
```json
{
  "success": true,
  "code": 9007,
  "message": "Worker created success",
  "data": {}
}
```

## **POST /auth/reset-admin-password**
重置管理员密码（需要管理员 Token）  
Reset administrator password (administrator token required)  
### Headers
```
Authorization: Bearer <admin-token>
```
### Body
```json
{ "username": "root", "password": "654321" }
```
### Notes / 说明
- 若用户名不存在，返回 `1001 User does not exist`。  
  If the username does not exist, it returns `1001 User does not exist`.
- 若用户名存在但该账号角色不是 `admin`，返回 `1007 The specified user is not an admin`。  
  If the username exists but the account role is not `admin`, it returns `1007 The specified user is not an admin`.
- 成功后会更新密码哈希，并将该账号当前保存的 token 清空；该用户需要重新登录获取新 token。  
  On success, the password hash is updated and the account's stored token is cleared; the user must log in again to obtain a new token.
- 若请求体不合法（缺字段、非字符串、空字符串或纯空白字符串），返回 `1008 Username and password must be non-empty strings`。  
  If the request body is invalid (missing fields, non-string values, empty strings, or whitespace-only strings), it returns `1008 Username and password must be non-empty strings`.
### Response Example
```json
{
  "success": true,
  "code": 9003,
  "message": "Password updated",
  "data": {}
}
```

## **POST /auth/reset-worker-password**
重置工人密码（需要管理员 Token）  
Reset worker password (administrator token required)  
### Headers
```
Authorization: Bearer <admin-token>
```
### Body
```json
{ "username": "worker1", "password": "654321" }
```
### Notes / 说明
- 若用户名不存在，返回 `1001 User does not exist`。  
  If the username does not exist, it returns `1001 User does not exist`.
- 若用户名存在但该账号角色不是 `worker`，返回 `3002 The specified user is not a worker`。  
  If the username exists but the account role is not `worker`, it returns `3002 The specified user is not a worker`.
- 成功后会更新密码哈希，并将该账号当前保存的 token 清空；该用户需要重新登录获取新 token。  
  On success, the password hash is updated and the account's stored token is cleared; the user must log in again to obtain a new token.
- 若请求体不合法（缺字段、非字符串、空字符串或纯空白字符串），返回 `1008 Username and password must be non-empty strings`。  
  If the request body is invalid (missing fields, non-string values, empty strings, or whitespace-only strings), it returns `1008 Username and password must be non-empty strings`.
### Response Example
```json
{
  "success": true,
  "code": 9003,
  "message": "Password updated",
  "data": {}
}
```
---
# 🩺 健康检查 / Health Check
## **GET /healthz**
检查服务是否可访问（无鉴权）  
Check whether the service is reachable (no authentication required).  
### Response Example
```json
{
  "success": true,
  "code": 0,
  "message": "Success",
  "data": {
    "ciallo": "Ciallo～(∠・ω< )⌒★"
  }
}
```

---
# 🛠 管理员接口 / Admin APIs
## **GET /admin/workers**
查询工人列表  
Query worker list
### Headers
```
Authorization: Bearer <admin-token>
```
返回顺序：按 `created_at DESC`（创建时间倒序）。  
Sort order: `created_at DESC` (created time descending).
### Response Example
```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": {
    "WorkerList": [
      {
        "id": 12,
        "username": "worker-a",
        "role": "worker",
        "created_at": "2026-03-05 13:05:22"
      },
      {
        "id": 9,
        "username": "worker-b",
        "role": "worker",
        "created_at": "2026-03-04 19:20:11"
      }
    ]
  }
}
```

## **POST /admin/orders/create**
创建工单   
Create a work order.   
### Headers
```
Authorization: Bearer <admin-token>
```
### Body
```json
{
  "title": "Repair A",
  "description": "Description...",
  "tag": "NFC_TAG_123"
}
```
### Response Example
```json
{
  "success": true,
  "code": 9010,
  "message": "Order created successfully",
  "data": {
    "orderId": 101
  }
}
```

## **POST /admin/orders/assign**
指派工单给工人   
Assign work orders to workers.   
### Headers
```
Authorization: Bearer <admin-token>
```
### Body
```json
{
  "orderId": 1,
  "userId": 2
}
```
### Notes / 说明
- `userId` 为数字时：将工单分配给对应工人，状态设为 `assigned`  
  When `userId` is numeric: assign order to that worker, set status to `assigned`.
- `userId` 为 `null` 时：取消分配，`assigned_to = NULL`，状态改回 `created`  
  When `userId` is `null`: unassign order, set `assigned_to = NULL`, revert status to `created`.
### Response Example
```json
{
  "success": true,
  "code": 9004,
  "message": "Order assigned success",
  "data": {}
}
```
---
# 👷 工人接口 / Worker APIs
## **GET /worker/orders**
查看自己被指派的工单   
View your assigned work orders   
### Headers
```
Authorization: Bearer <worker-token>
```
返回顺序：按 `updated_at DESC`（更新时间倒序）。  
Sort order: `updated_at DESC` (updated time descending).
### Response Example
```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": [
    {
      "id": 1,
      "title": "Repair A",
      "description": "Fix pipe leak",
      "nfc_tag": "NFC_TAG_123",
      "status": "assigned",
      "assigned_to": 2,
      "created_at": "2026-03-01 09:00:00",
      "updated_at": "2026-03-05 10:30:00"
    }
  ]
}
```

## **POST /worker/orders/complete**
完成工单   
Complete work order   
### Headers
```
Authorization: Bearer <worker-token>
```
### Body
```json
{ "orderId": 1 }
```
### Notes / 说明
- 只有该工单的当前被分配工人才能完成。  
  Only the currently assigned worker can complete the order.
- 工单状态必须为 `assigned`，否则不能完成。  
  The order status must be `assigned`, otherwise completion is rejected.
### Response Example
```json
{
  "success": true,
  "code": 9008,
  "message": "Set work order completion success",
  "data": {}
}
```
---
# 📄 工单接口 / Orders APIs
## **GET /orders/logs**
查询工单日志（可以筛选）   
Query work order logs (filterable)
### Headers
```
Authorization: Bearer <admin-token>
```
### Query Params（可选）

| 参数 Params | 示例值 Examples | 说明 | Description |
| --- | --- | --- | --- |
| orderId | `1,2,3` | 多个 ID 用逗号分隔 | Multiple IDs separated by commas |
| status | `created,assigned,unassigned,completed` | 多个操作（对应日志 `action`） | Multiple actions (mapped to log `action`) |
| operator | `1,3` | 操作人（用户ID） | Operator (User ID) |
| startTime | `2025-01-01 00:00:00` | 查询此时间之后（含），精确到秒 | Query for times after this date. (Included) |
| endTime  | `2025-01-31 23:59:59`  | 查询此时间之前（含），精确到秒 | Query for times before this date. (Included) |

返回顺序：按 `timestamp DESC`（日志时间倒序）。  
Sort order: `timestamp DESC` (log time descending).
### Notes / 说明
- 时间范围校验：不允许 `startTime > endTime`  
  Time range validation: `startTime > endTime` is not allowed.
### Response Example
```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": [
    {
      "id": 18,
      "order_id": 1,
      "action": "assigned",
      "operator_id": 1,
      "timestamp": "2026-03-05 10:00:00"
    }
  ]
}
```

## **GET /orders/search**
查询工单（支持多条件筛选）
Search work orders with filter options
### **Headers**
```
Authorization: Bearer <admin-token>
```
### **Query Params（可选 Optional）**
注意：`NULL` 不能与 `assigned` 中的常规 ID 组合使用。  
查询必须仅包含 `NULL` 或仅包含数字ID。  
Note: `NULL` cannot be combined with regular IDs in the `assigned` filter.  
The query must contain either `NULL` alone or numeric IDs only.  

| 参数 Params | 示例 Examples | 说明 | Description |
| --- | --- | --- | --- |
| title        |  `AC`                            | 按标题模糊匹配 | Fuzzy match on title |
| description  | `water`                          | 按描述模糊匹配 | Fuzzy match on description |
| nfc_tag      | `room101` / `nfc123`             | 按NFC标签模糊匹配 | Fuzzy match on nfc_tag |
| status       | `created,assigned,completed`     | 工单状态筛选      | Order status filter |
| assigned     | `1,3` / `NULL`                   | 多个工人ID，支持NULL查询 | Multiple worker IDs, support `NULL` query. |
| createdStart | `2025-01-01 00:00:00`            | 创建时间开始（含） | Created time ≥ this value |
| createdEnd   | `2025-01-31 23:59:59`            | 创建时间结束（含） | Created time ≤ this value |
| updatedStart | `2025-02-01 00:00:00`            | 更新时间开始（含） | Updated time ≥ this value |
| updatedEnd   | `2025-02-10 23:59:59`            | 更新时间结束（含） | Updated time ≤ this value |

返回顺序：按 `id DESC`（工单 ID 倒序）。  
Sort order: `id DESC` (work order ID descending).
### Notes / 说明
- 时间范围校验：`createdStart > createdEnd` 或 `updatedStart > updatedEnd` 均不允许。  
  Time range validation: Neither `createdStart > createdEnd` nor `updatedStart > updatedEnd` were allowed.
### Response Example
```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": [
    {
      "id": 1,
      "title": "Repair A",
      "description": "Fix pipe leak",
      "nfc_tag": "NFC_TAG_123",
      "status": "assigned",
      "assigned_to": 2,
      "created_at": "2026-03-01 09:00:00",
      "updated_at": "2026-03-05 10:30:00"
    }
  ]
}
```
