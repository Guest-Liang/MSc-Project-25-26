# 📘 API 文档 / API Documentation

## 统一响应格式 / Unified Response Envelope

所有 API 都返回 JSON，并统一使用以下结构。  
All APIs return JSON with the following unified envelope.

```json
{
  "success": true,
  "code": 9013,
  "message": "Order scan processed successfully",
  "data": {}
}
```

```json
{
  "success": false,
  "code": 4013,
  "message": "Scanned UID Hex is out of the required order",
  "data": {
    "expectedStepIndex": 2,
    "expectedUidHex": "0D000D000D00"
  }
}
```

| 字段 | 类型 | 中文说明 | English |
| --- | --- | --- | --- |
| `success` | boolean | 请求是否成功 | Whether the request succeeded |
| `code` | number | 业务成功码或错误码 | Business success code or error code |
| `message` | string | 人类可读消息 | Human-readable message |
| `data` | object / array / null | 返回数据；失败时也可能携带上下文信息 | Returned payload; failed responses may also contain contextual data |

状态码定义见 `cloudflare/src/utils/status.js`。  
See `cloudflare/src/utils/status.js` for the status-code definitions.

---

## 已废弃接口 / Deprecated API

**中文**  
当前文档以下方的 canonical API 为准。旧接口中，`POST /worker/orders/complete` 已废弃，仅保留废弃说明，推荐统一使用 `POST /worker/orders/scan`。

**English**  
This document is centered on the canonical API only. Among the older endpoints, `POST /worker/orders/complete` is deprecated and is kept here only with a deprecation note. Use `POST /worker/orders/scan` instead.

## 工单模型 / Order Model

| 类型 / Type | `orderType` | 中文 | English |
| --- | --- | --- | --- |
| 普通工单 / Standard order | `standard` | 主单保存一个 `targetUidHex`，匹配成功后直接完成 | The main order stores one `targetUidHex`, and a successful match completes the order immediately |
| 顺序工单 / Sequence order | `sequence` | 主单不保存单一目标 UID，通过 `order_steps` 保存有序步骤链 | The main order does not store one target UID; ordered steps are stored in `order_steps` |

---

# 身份验证 / Authentication

## POST `/auth/login`

用户登录并返回 JWT。  
Log in and return a JWT.

### Body

```json
{ "username": "admin", "password": "123456" }
```

### Notes / 说明

- 若该用户已有有效 token，则直接返回旧 token。  
  If the user already has a valid token, the existing token is returned.
- 若没有有效 token，则签发新 token。  
  If no valid token exists, a new token is generated.
- `username` 会先去除首尾空白后再查询。  
  `username` is trimmed before lookup.
- `password` 只做非空校验，不会被裁剪后再存储。  
  `password` is only validated for non-emptiness and is not trimmed before storage.

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

## POST `/auth/logout`

用户退出登录。  
Log out the current user.

### Headers

```text
Authorization: Bearer <token>
```

### Response Example

```json
{
  "success": true,
  "code": 9001,
  "message": "Logout success",
  "data": {}
}
```

## POST `/auth/register-admin`

注册管理员账号，需要管理员 token。  
Register an admin account; admin token required.

### Body

```json
{ "username": "root", "password": "123456" }
```

## POST `/auth/register-worker`

注册工人账号，需要管理员 token。  
Register a worker account; admin token required.

### Body

```json
{ "username": "worker1", "password": "123456" }
```

## POST `/auth/reset-admin-password`

重置管理员密码，需要管理员 token。  
Reset an admin password; admin token required.

### Body

```json
{ "username": "root", "password": "654321" }
```

## POST `/auth/reset-worker-password`

重置工人密码，需要管理员 token。  
Reset a worker password; admin token required.

### Body

```json
{ "username": "worker1", "password": "654321" }
```

### 通用认证说明 / Shared Auth Notes

- 若用户名不存在，登录返回 `1001 User does not exist`。  
  If the username does not exist, login returns `1001 User does not exist`.
- 若密码错误，登录返回 `1002 Wrong password`。  
  If the password is incorrect, login returns `1002 Wrong password`.
- 若用户名已存在，注册接口返回 `1005 User already exists`。  
  If the username already exists, the register endpoints return `1005 User already exists`.
- 所有带 `username` / `password` 的认证接口都会校验非空字符串，否则返回 `1008 Username and password must be non-empty strings`。  
  All auth endpoints with `username` / `password` validate non-empty strings; otherwise they return `1008 Username and password must be non-empty strings`.

---

# 健康检查 / Health Check

## GET `/healthz`

无鉴权的健康检查接口。  
Unauthenticated health-check endpoint.

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

# 管理员接口 / Admin APIs

## GET `/admin/workers`

查询工人列表。  
Return the worker list.

### Headers

```text
Authorization: Bearer <admin-token>
```

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
      }
    ]
  }
}
```

## POST `/admin/orders/create`

创建工单。  
Create a work order.

### Headers

```text
Authorization: Bearer <admin-token>
```

### Canonical Body

#### 普通工单 / Standard order

```json
{
  "title": "Check Room 301",
  "description": "Arrive at room 301 and confirm the maintenance task",
  "orderType": "standard",
  "targetUidHex": "04AABBCCDD11",
  "locationCode": "room301",
  "displayName": "Room 301"
}
```

#### 顺序工单 / Sequence order

```json
{
  "title": "Floor 3 Sequence Patrol",
  "description": "Visit all checkpoints in order",
  "orderType": "sequence",
  "locationCode": "floor3",
  "displayName": "Floor 3 sequence patrol"
}
```

### Notes / 说明

- `orderType` 只允许 `standard` 或 `sequence`。  
  `orderType` only allows `standard` or `sequence`.
- `standard` 工单必须提供 `targetUidHex`。  
  A `standard` order must provide `targetUidHex`.
- `sequence` 工单创建时不需要单一目标 UID，后续通过 `/admin/orders/steps/save` 保存步骤。  
  A `sequence` order does not need one target UID at creation time; steps are saved later via `/admin/orders/steps/save`.

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

## POST `/admin/orders/steps/save`

一次性保存或覆盖顺序工单的完整步骤链。  
Save or replace the full step chain for a sequence order in one request.

### Headers

```text
Authorization: Bearer <admin-token>
```

### Body

```json
{
  "orderId": 101,
  "steps": [
    {
      "stepIndex": 1,
      "targetUidHex": "04AABBCCDD11",
      "locationCode": "room301",
      "displayName": "Room 301"
    },
    {
      "stepIndex": 2,
      "targetUidHex": "04EEFF001122",
      "locationCode": "room302-server1",
      "displayName": "Room 302 Server 1"
    }
  ]
}
```

### Notes / 说明

- 仅 `sequence` 工单允许保存步骤。  
  Only `sequence` orders allow step saving.
- 步骤必须从 `1..N` 连续编号。  
  Steps must be continuously indexed from `1..N`.
- 每一步都必须提供合法 `targetUidHex`。  
  Every step must provide a valid `targetUidHex`.
- 只允许在工单尚未派出、尚未开始执行时保存或覆盖步骤。  
  Steps can only be saved or replaced before the order is assigned / started.

### Response Example

```json
{
  "success": true,
  "code": 9012,
  "message": "Order steps saved successfully",
  "data": {
    "orderId": 101,
    "stepCount": 2
  }
}
```

## POST `/admin/orders/assign`

给工人派单，或取消派单。  
Assign an order to a worker, or unassign it.

### Headers

```text
Authorization: Bearer <admin-token>
```

### Assign Body

```json
{
  "orderId": 101,
  "userId": 2
}
```

### Unassign Body

```json
{
  "orderId": 101,
  "userId": null
}
```

### Notes / 说明

- 派单即代表工单开始；后端不会再设计单独的 `start` 接口。  
  Assignment already counts as the start of work; the backend does not introduce a separate `start` endpoint.
- `sequence` 工单必须先保存至少 1 个步骤，才能派单。  
  A `sequence` order must already have at least one saved step before assignment.
- 若顺序工单已经产生有效进度，则不允许取消分配回 `created`。  
  Once a sequence order has recorded real progress, it cannot be unassigned back to `created`.

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

# 工人接口 / Worker APIs

## GET `/worker/orders`

查看当前工人被分配的工单。  
View the current worker’s assigned orders.

### Headers

```text
Authorization: Bearer <worker-token>
```

### Notes / 说明

- 对顺序工单，响应还会返回下一步预期的 UID / 地点信息。  
  For sequence orders, the response also returns the next expected UID / location information.

### Response Example

```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": [
    {
      "id": 101,
      "title": "Floor 3 Sequence Patrol",
      "description": "Visit all checkpoints in order",
      "orderType": "sequence",
      "targetUidHex": null,
      "status": "assigned",
      "assignedTo": 2,
      "locationCode": "floor3",
      "displayName": "Floor 3 sequence patrol",
      "assignedAt": "2026-03-22 09:00:00",
      "sequenceTotalSteps": 3,
      "sequenceCompletedSteps": 1,
      "nextStepIndex": 2,
      "nextExpectedUidHex": "04EEFF001122",
      "nextLocationCode": "room302-server1",
      "nextDisplayName": "Room 302 Server 1"
    }
  ]
}
```

## POST `/worker/orders/scan`

工人的核心执行接口。扫描即提交，后端基于 `uidHex` 完成匹配、顺序校验、evidence 记录与完单推进。  
This is the primary worker execution endpoint. The worker submits a scan, and the backend performs `uidHex` matching, sequence validation, evidence recording, and completion / progression.

### Headers

```text
Authorization: Bearer <worker-token>
```

### Body

```json
{
  "orderId": 101,
  "uidHex": "04AABBCCDD11",
  "rawText": "ID: 04AABBCCDD11",
  "ndefText": "room301"
}
```

### Notes / 说明

- `uidHex` 是唯一匹配输入。  
  `uidHex` is the only matching input.
- `rawText` / `ndefText` 仅写入日志扩展字段，不参与匹配。  
  `rawText` / `ndefText` are only written to log metadata and do not affect matching.
- 普通工单匹配成功后会直接 `completed`。  
  A matching scan on a standard order completes it immediately.
- 顺序工单每次匹配成功只推进一步。  
  A matching scan on a sequence order advances exactly one step.

### 成功示例：普通工单 / Success Example: standard order

```json
{
  "success": true,
  "code": 9013,
  "message": "Order scan processed successfully",
  "data": {
    "orderId": 102,
    "orderType": "standard",
    "matched": true,
    "completed": true,
    "scannedUidHex": "04AABBCCDD11",
    "expectedUidHex": "04AABBCCDD11",
    "locationCode": "room301",
    "displayName": "Room 301",
    "sequenceTotalSteps": 0,
    "sequenceCompletedSteps": 0,
    "nextStepIndex": null,
    "nextExpectedUidHex": null
  }
}
```

### 成功示例：顺序工单推进 / Success Example: sequence progression

```json
{
  "success": true,
  "code": 9013,
  "message": "Order scan processed successfully",
  "data": {
    "orderId": 101,
    "orderType": "sequence",
    "matched": true,
    "completed": false,
    "scannedUidHex": "04AABBCCDD11",
    "completedStepIndex": 1,
    "sequenceTotalSteps": 3,
    "sequenceCompletedSteps": 1,
    "nextStepIndex": 2,
    "nextExpectedUidHex": "04EEFF001122",
    "nextLocationCode": "room302-server1",
    "nextDisplayName": "Room 302 Server 1"
  }
}
```

### 失败示例：顺序错误 / Failure Example: out of order

```json
{
  "success": false,
  "code": 4013,
  "message": "Scanned UID Hex is out of the required order",
  "data": {
    "orderId": 101,
    "orderType": "sequence",
    "scannedUidHex": "04FFFF889900",
    "expectedStepIndex": 2,
    "expectedUidHex": "04EEFF001122",
    "expectedLocationCode": "room302-server1",
    "expectedDisplayName": "Room 302 Server 1",
    "scannedStepIndex": 3
  }
}
```

### 常见失败码 / Common Failure Codes

| code | message | 中文说明 | English |
| --- | --- | --- | --- |
| `4012` | `Scanned UID Hex does not match the required target` | 扫描 UID 不匹配当前要求 | The scanned UID does not match the current requirement |
| `4013` | `Scanned UID Hex is out of the required order` | 顺序工单越步扫描 | The scan skipped ahead in a sequence order |
| `4014` | `This sequence step has already been completed` | 重复扫描已完成步骤 | A completed sequence step was scanned again |
| `4024` | `Scan payload must include a valid orderId and uidHex` | 扫描请求体不合法 | The scan payload is invalid |

## POST `/worker/orders/complete`

已废弃。  
Deprecated.

### Behavior / 行为

- 始终返回错误 `4016 Manual completion API is deprecated; use /worker/orders/scan`。  
  Always returns error `4016 Manual completion API is deprecated; use /worker/orders/scan`.
- 服务端会额外写入一条 `deprecated_complete_api` 日志，便于观察旧客户端是否仍在调用。  
  The server also writes a `deprecated_complete_api` log entry so you can observe whether old clients are still calling it.

### Response Example

```json
{
  "success": false,
  "code": 4016,
  "message": "Manual completion API is deprecated; use /worker/orders/scan",
  "data": {
    "orderId": 101,
    "recommendedEndpoint": "/worker/orders/scan"
  }
}
```

## GET `/worker/history`

查询当前工人的 visit history / scan history / completion history。  
Query the current worker’s visit / scan / completion history.

### Headers

```text
Authorization: Bearer <worker-token>
```

### Query Params（可选 / optional）

| 参数 / Param | 示例 / Example | 中文说明 | English |
| --- | --- | --- | --- |
| `orderId` | `101,102` | 仅查询这些工单 | Only these order IDs |
| `action` | `scan,completed` | 日志动作筛选 | Log action filter |
| `result` | `standard_matched,mismatch` | 执行结果筛选 | Execution result filter |
| `uidHex` | `04AABBCCDD11` | 按扫描或期望 UID 过滤 | Filter by scanned or expected UID |
| `startTime` | `2026-03-20 00:00:00` | 起始时间（含） | Start time inclusive |
| `endTime` | `2026-03-20 23:59:59` | 结束时间（含） | End time inclusive |

### Response Example

```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": [
    {
      "id": 88,
      "order_id": 101,
      "action": "scan",
      "operator_id": 2,
      "timestamp": "2026-03-22 11:20:30",
      "result": "sequence_step_completed",
      "step_index": 1,
      "scan_uid_hex": "04AABBCCDD11",
      "expected_uid_hex": "04AABBCCDD11",
      "location_code": "room301",
      "display_name": "Room 301",
      "orderType": "sequence",
      "orderTitle": "Floor 3 Sequence Patrol",
      "stepIndex": 1,
      "scanUidHex": "04AABBCCDD11",
      "expectedUidHex": "04AABBCCDD11",
      "locationCode": "room301",
      "displayName": "Room 301",
      "details": {
        "orderType": "sequence",
        "rawText": "ID: 04AABBCCDD11"
      }
    }
  ]
}
```

## GET `/worker/history/summary`

返回当前工人的基础 tracking summary。  
Return the current worker’s basic tracking summary.

### Headers

```text
Authorization: Bearer <worker-token>
```

### Response Example

```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": {
    "workerId": 2,
    "totalScanCount": 18,
    "successfulScanCount": 12,
    "mismatchCount": 3,
    "outOfOrderCount": 2,
    "duplicateCount": 1,
    "completedOrderCount": 5,
    "visitedOrderCount": 6,
    "uniqueVisitedUidCount": 8,
    "lastScanAt": "2026-03-22 15:10:00",
    "lastCompletedAt": "2026-03-22 15:10:00"
  }
}
```

---

# 工单查询与分析接口 / Order Query and Analytics APIs

以下接口均为管理员权限。  
All endpoints below require admin privileges.

## GET `/orders/steps`

查看指定顺序工单的步骤定义。  
View the step definition of a sequence order.

### Query Params

| 参数 / Param | 必填 / Required | 中文说明 | English |
| --- | --- | --- | --- |
| `orderId` | yes | 工单 ID | Order ID |

### Response Example

```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": {
    "orderId": 101,
    "orderType": "sequence",
    "steps": [
      {
        "id": 1,
        "order_id": 101,
        "step_index": 1,
        "target_uid_hex": "04AABBCCDD11",
        "location_code": "room301",
        "display_name": "Room 301",
        "stepIndex": 1,
        "targetUidHex": "04AABBCCDD11",
        "locationCode": "room301",
        "displayName": "Room 301"
      }
    ]
  }
}
```

## GET `/orders/logs`

管理员查询工单生命周期日志、扫描 evidence 和失败记录。  
Admins query order lifecycle logs, scan evidence, and failure records.

### Query Params（可选 / optional）

| 参数 / Param | 示例 / Example | 中文说明 | English |
| --- | --- | --- | --- |
| `orderId` | `101,102` | 多个工单 ID | Multiple order IDs |
| `action` | `created,assigned,scan,completed` | 动作筛选 | Action filter |
| `result` | `sequence_step_completed,mismatch` | 扫描/完成结果筛选 | Scan / completion result filter |
| `workerId` | `2,3` | 按工人操作人过滤 | Filter by worker operator ID |
| `uidHex` | `04AABBCCDD11` | 按扫描或期望 UID 过滤 | Filter by scanned or expected UID |
| `orderType` | `standard,sequence` | 按工单类型过滤 | Filter by order type |
| `startTime` | `2026-03-20 00:00:00` | 起始时间（含） | Start time inclusive |
| `endTime` | `2026-03-20 23:59:59` | 结束时间（含） | End time inclusive |

### 典型 action / result 值 / Typical action and result values

| `action` | `result` 示例 / Examples |
| --- | --- |
| `created` | `null` |
| `steps_saved` | `sequence_steps_saved` |
| `assigned` | `null` |
| `unassigned` | `null` |
| `scan` | `standard_matched`, `sequence_step_completed`, `mismatch`, `out_of_order`, `duplicate` |
| `completed` | `standard_completed`, `sequence_completed` |
| `complete` | `deprecated_complete_api` |

## GET `/orders/search`

按多条件筛选工单。  
Search work orders with multiple filters.

### Query Params（可选 / optional）

| 参数 / Param | 示例 / Example | 中文说明 | English |
| --- | --- | --- | --- |
| `title` | `Floor 3` | 标题模糊匹配 | Fuzzy match on title |
| `description` | `server` | 描述模糊匹配 | Fuzzy match on description |
| `targetUidHex` | `04AABBCCDD11` | 按标准 UID Hex 精确过滤 | Exact filter on canonical UID Hex |
| `orderType` | `standard,sequence` | 工单类型 | Order type |
| `status` | `created,assigned,completed` | 工单状态 | Order status |
| `assigned` | `2,3` / `NULL` | 指派工人或未指派 | Assigned worker IDs or unassigned |
| `progress` | `not_started,in_progress,completed` | 执行进度 | Execution progress |
| `createdStart` | `2026-03-01 00:00:00` | 创建时间起点（含） | Created time start inclusive |
| `createdEnd` | `2026-03-31 23:59:59` | 创建时间终点（含） | Created time end inclusive |
| `updatedStart` | `2026-03-01 00:00:00` | 更新时间起点（含） | Updated time start inclusive |
| `updatedEnd` | `2026-03-31 23:59:59` | 更新时间终点（含） | Updated time end inclusive |

### `progress` 语义 / `progress` semantics

- `not_started`: 未完成，且顺序工单进度为 0；标准工单未完成时也视为 `not_started`。  
  `not_started`: not completed, and sequence progress is 0; incomplete standard orders are also treated as `not_started`.
- `in_progress`: 仅顺序工单使用，表示已经完成至少 1 步但尚未完成整单。  
  `in_progress`: sequence orders only; at least one step is done, but the whole order is not completed yet.
- `completed`: 工单已经完成。  
  `completed`: the order is completed.

## GET `/orders/analysis/summary`

管理员查看基础 tracking / analysis 汇总。  
Admins view the basic tracking / analytics summary.

### Response Example

```json
{
  "success": true,
  "code": 9002,
  "message": "SQL query success",
  "data": {
    "totals": {
      "totalOrders": 24,
      "completedOrders": 11,
      "standardOrders": 15,
      "sequenceOrders": 9
    },
    "scans": {
      "totalScanEvidenceCount": 48,
      "standardMatchedCount": 10,
      "sequenceStepCompletedCount": 22,
      "mismatchCount": 9,
      "outOfOrderCount": 5,
      "duplicateCount": 2,
      "uniqueVisitedUidCount": 14
    },
    "workers": [
      {
        "workerId": 2,
        "username": "worker-a",
        "currentAssignedOrderCount": 2,
        "totalScanCount": 18,
        "successfulScanCount": 12,
        "mismatchCount": 3,
        "outOfOrderCount": 2,
        "duplicateCount": 1,
        "completedOrderCount": 5,
        "uniqueVisitedUidCount": 8,
        "lastScanAt": "2026-03-22 15:10:00"
      }
    ]
  }
}
```

---

# Android 接入建议 / Android Integration Notes

**中文**
- Android 客户端扫描后，请优先提交 `uidHex`。
- 不要把 NDEF 文本、自定义字符串或展示名称当作后端匹配值。
- 创建普通工单时，请录入目标标签的 UID Hex。
- 创建顺序工单时，建议先创建主单，再调用 `/admin/orders/steps/save` 一次提交完整步骤链。
- 工人侧执行时，建议直接将当前工单 `orderId + uidHex` 提交到 `/worker/orders/scan`。

**English**
- After scanning, the Android client should primarily submit `uidHex`.
- Do not treat NDEF text, custom strings, or display names as the backend matching key.
- When creating standard orders, enter the target tag’s UID Hex.
- When creating sequence orders, create the main order first, then call `/admin/orders/steps/save` once with the full ordered step chain.
- During worker execution, submit the current `orderId + uidHex` directly to `/worker/orders/scan`.
