# Odoo AI Chat — แนวคิดและ Architecture

## Core Concept

1 Domain = 1 บริษัท = 1 Odoo Instance
URL ของ Odoo hardcode ไว้ใน `application.properties`
ไม่รองรับหลาย company ในระบบเดียว

---

## เป้าหมายของ Project

ระบบ AI Chat ที่ให้ผู้ใช้พิมพ์คำถามภาษาไทยธรรมดา แล้วดึงข้อมูลจาก Odoo จริงมาตอบ
โดยไม่ต้องรู้ชื่อ field หรือ model ของ Odoo เลย

ตัวอย่างคำถามที่ระบบต้องตอบได้:
- "ลูกหนี้ค้างชำระเกิน 30 วันมีใครบ้าง"
- "มี PO ไหนที่รับสินค้าแล้วแต่ยังไม่ได้วาง bill"
- "vendor ไหนส่ง bill มาเกินราคา PO"
- "เดือนนี้ซื้อของเกินงบไปไหม"

---

## Tech Stack

| ส่วน | เทคโนโลยี |
|---|---|
| Backend | Java Spring Boot 3.3 / Java 21 |
| Frontend | Thymeleaf + Vanilla JS (chat UI style AI) |
| Database | PostgreSQL (เก็บ session + config) |
| AI | Claude API (Tool Use) |
| Odoo | JSON-RPC (ไม่แตะ source code Odoo) |
| Deploy | On-premise / Server บริษัท / HTTP |

---

## Authentication Flow

```
User ใส่ Odoo username/password
        ↓
Spring Boot เรียก Odoo JSON-RPC /web/dataset/call_kw
        ↓
ได้ Odoo Session (session_id + uid)
        ↓
เก็บไว้ใน Spring HTTP Session
        ↓
ทุก query ใช้ session ของ user นั้น → permission ตาม Odoo จริง
```

**ไม่เก็บ password ลง DB เลย**
permission และ record rules ของ Odoo ทำงานอัตโนมัติตาม user ที่ login

---

## AI Flow

```
User พิมพ์คำถาม
        ↓
Claude API (Tool Use) รับ prompt
        ↓
Claude เรียก tool: query_odoo
        ↓
OdooQueryService เรียก Odoo JSON-RPC ด้วย session ของ user
        ↓
ส่งผลกลับให้ Claude
        ↓
Claude ตอบเป็นภาษาไทย
```

---

## Package Structure

```
src/main/java/com/yourcompany/odooai/
├── config/
│   └── SecurityConfig.java         ← login page, route protection
├── controller/
│   ├── AuthController.java         ← POST /login, GET /logout
│   ├── ChatController.java         ← POST /api/ask
│   └── SettingsController.java     ← POST /api/settings/claude (admin)
├── service/
│   ├── OdooAuthService.java        ← login Odoo → return session
│   ├── OdooQueryService.java       ← callKw, search_read
│   ├── ClaudeService.java          ← Tool Use loop
│   └── SystemConfigService.java    ← อ่าน config จาก DB / fallback properties
├── model/
│   ├── OdooSession.java            ← sessionId, uid, userName
│   └── SystemConfig.java           ← key-value config entity
└── OdooAiApplication.java
```

---

## หน้าที่มีในระบบ

```
/login     → form ใส่ Odoo user/pass (ไม่มีสมัครสมาชิก)
/chat      → AI chat UI (protected, ต้อง login ก่อน)
/settings  → ตั้งค่า Claude API Key (admin Odoo เท่านั้น)
```

---

## application.properties

```properties
# Odoo — hardcode ต่อบริษัท
odoo.url=http://192.168.1.100:8069
odoo.db=company_db

# Claude — ตั้งค่าผ่านหน้า Settings ได้ นี่คือ default fallback
claude.api-key=
claude.model=claude-haiku-4-5-20251001

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/odoo_ai
spring.datasource.username=postgres
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update

# Session
spring.session.store-type=jdbc
server.servlet.session.timeout=8h
```

---

## Config Management

```
application.properties  ← Odoo URL, DB connection (fixed per company)
Settings หน้า Admin     ← Claude API Key (บันทึกลง DB)
Environment Variable    ← secrets สำคัญ เช่น DB password
```

Claude API Key เก็บใน table `system_config` (key-value)
Code อ่านจาก DB ก่อน ถ้าไม่มีค่า fallback ไป application.properties

---

## Security ที่ต้องทำตั้งแต่วันแรก

| ความเสี่ยง | วิธีแก้ |
|---|---|
| Prompt Injection | System prompt ที่แน่น + validate input |
| API Key หลุด | เก็บใน DB / Env Variable ไม่ commit Git |
| สแปม Claude API | Rate limit ต่อ user (เช่น 20 req/นาที) |
| ไม่รู้ว่าใครถามอะไร | Audit log (user + timestamp + คำถาม) |
| XSS | Sanitize output ก่อน render |
| Session หมด | Check Odoo session ก่อนทุก query |

**HTTP ใช้ได้** — ลูกค้าจัดการ HTTPS เองที่ network layer

---

## Tools ที่ให้ Claude ใช้ได้ (read-only)

```
query_odoo    ← search_read, search_count จาก Odoo
get_field_meta ← ดู field definitions ของ model
```

ไม่มี create / write / delete — project นี้ query อย่างเดียว

---

## สิ่งที่ตัดออกจาก project เดิม (basicteam-eiei)

- UAT Test Runner ทั้งหมด
- GitHub integration
- สร้าง/แก้ไข/ลบข้อมูล Odoo
- Multi-project / หลาย Odoo instance
- Import CSV test case
