# 📘 API 文档 / API Documentation   
所有 API 采用 JSON 格式返回，并使用标准化格式   
All APIs return data in JSON format and using a standardized format.   

```json
{
  "success": true | false,
  "data": {} | null,
  "error": { "code": number, "message": string } | null
}
```
错误代码请参考：`cloudflare/src/utils/errors.js`

---

# 🔐 身份验证 / Authentication
## **POST /auth/login**
用户登录，返回 JWT   
User login, returned JWT.   
### Body
```json
{ "username": "admin", "password": "123456" }
```

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

## **GET /admin/orderLogs**
查询工单日志（可以筛选）   
Query work order logs (filterable)
### Headers
```
Authorization: Bearer <admin-token>
```
### Query Params（可选）

| 参数  | 示例  |
| --------- | ------------------------------- |
| orderId   | `1,2,3`                         |
| status    | `created,assigned,completed`    |
| operator  | `1,3`                           |
| before    | `2025-01-01`                    |
| after     | `2024-12-01`                    |
| from & to | `from=2024-12-01&to=2024-12-31` |

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

# 📱 NFC 接口
## **GET /orders/byTag/{tagId}**
通过 NFC 标签查询工单。
```
GET /orders/byTag/NFC_TAG_123
```
