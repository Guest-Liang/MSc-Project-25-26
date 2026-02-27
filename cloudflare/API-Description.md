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

错误代码请参考：`cloudflare/src/utils/status.js`

---

# 🔐 身份验证 / Authentication
## **POST /auth/login**
用户登录，返回 JWT   
User login, returned JWT.   
### Body
```json
{ "username": "admin", "password": "123456" }
```

## **POST /auth/logout**
用户退出登录  
User logout.   

## **POST /auth/register-admin**
注册管理员账号   
Register administrator account   
### Body
```json
{ "username": "root", "password": "123456" }
```

## **POST /auth/register-worker**   
注册工人账号（需要管理员 Token）   
Register a worker account (administrator token required)   
### Headers
```
Authorization: Bearer <admin-token>
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
    "status": "ok"
  }
}
```

---
# 🛠 管理员接口 / Admin APIs
## **POST /admin/orders/create**
创建工单   
Create a work order.   
### Body
```json
{
  "title": "Repair A",
  "description": "Description...",
  "tag": "NFC_TAG_123"
}
```

## **POST /admin/orders/assign**
指派工单给工人   
Assign work orders to workers.   
### Body
```json
{
  "orderId": 1,
  "userId": 2
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
| status | `created,(un)assigned,completed` | 多个状态 | Multiple states can be combined |
| operator | `1,3` | 操作人（用户ID） | Operator (User ID) |
| startTime | `2025-01-01 00:00:00` | 查询此时间之后（含），精确到秒 | Query for times after this date. (Included) |
| endTime  | `2025-01-31 23:59:59`  | 查询此时间之前（含），精确到秒 | Query for times before this date. (Included) |

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
| status       | `created,(un)assigned,completed` | 多状态筛选，精确匹配 | Exact match, multiple states allowed |
| assigned     | `1,3` / `NULL`                   | 多个工人ID，支持NULL查询 | Multiple worker IDs, support `NULL` query. |
| createdStart | `2025-01-01 00:00:00`            | 创建时间开始（含） | Created time ≥ this value |
| createdEnd   | `2025-01-31 23:59:59`            | 创建时间结束（含） | Created time ≤ this value |
| updatedStart | `2025-02-01 00:00:00`            | 更新时间开始（含） | Updated time ≥ this value |
| updatedEnd   | `2025-02-10 23:59:59`            | 更新时间结束（含） | Updated time ≤ this value |
