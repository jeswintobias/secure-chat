# Secure Chat System - Architecture Context

This document provides a high-level overview of the Secure Chat System architecture to avoid redundant context loading and consolidate memory.

## Project Structure (Monorepo)

```
Secure Chat System/
├── secure-chat-backend/       ← Spring Boot 3.3 + Java 21
│   ├── pom.xml
│   └── src/main/java/com/securechat/
├── secure-chat-frontend/      ← Angular 19
│   ├── package.json
│   └── src/app/
├── docs/
│   └── implementation_assumptions.txt
└── project_context.md
```

## Backend — Core Entities (`secure-chat-backend/src/main/java/com/securechat/entity/`)

The system uses JPA entities with PostgreSQL persistence, UUID primary keys, and STOMP WebSocket messaging:

1. **`User`** (`User.java`)
   - **Properties:** Username, email, password hash, role (USER/ADMIN), online status.
   - **Responsibilities:** Authentication via JWT, authorization checks.

2. **`ChatMessage`** (`ChatMessage.java`)
   - **Properties:** Sender, conversation, content, message type (TEXT/SYSTEM/IMAGE/FILE), timestamps, optional expiry, attachmentUrl, attachmentType, pinned, pinnedBy, pinnedAt.
   - **Responsibilities:** Supports ephemeral (self-destructing) messages via `expiresAt`. XSS-sanitized on persistence. Supports file/image attachments and message pinning.

3. **`MessageRead`** (`MessageRead.java`)
   - **Properties:** Message, user, readAt timestamp.
   - **Responsibilities:** Tracks which users have read which messages (read receipts).

4. **`Conversation`** (`Conversation.java`)
   - **Properties:** Type (PRIVATE/GROUP), name, referral code, public key, member list.
   - **Responsibilities:** 
     - **Membership Management:** Validates `referralCode` before adding new users.
     - **Message Management:** Messages are queried with expiry filtering.
     - **Encryption Setup:** Provides `publicKey` for securing outbound messages.

## Backend — Controllers & Services

1. **`ChatController`** — Combined REST + WebSocket controller
   - **WebSocket:** `/app/chat.send/{id}`, `/app/chat.typing/{id}`, `/app/chat.read/{id}`
   - **REST:** `GET /api/conversations/{id}/messages`, `GET .../messages/pinned`, `POST /api/messages/{id}/pin`, `POST .../unpin`

2. **`FileUploadController`** — `POST /api/upload`, `GET /api/upload/files/{filename}`

3. **`GroupController`** — CRUD for groups, join via referral code

4. **`AuthController`** — JWT login/register

## Frontend — Architecture (`secure-chat-frontend/src/app/`)

- **Core Services:**
  - `AuthService` — JWT lifecycle, stores token/username/role in localStorage
  - `WebSocketService` — RxStomp wrapper: messages, typing, read receipts, roster
  - `MessageService` — REST for message history, pinned messages, pin/unpin
  - `FileUploadService` — REST file upload (POST /api/upload)
  - `GroupService` — REST group CRUD
  - `UserService` — REST user profiles

- **Features:**
  - **Auth:** Login, Register
  - **Chat:** Shell (orchestrator), Sidebar, Header (with pinned banner), MessageWindow, MessageInput (with attach + expiry), MessageBubble (with attachments, read ticks, pin indicator)

- **Shared:**
  - DTO models: `MessageResponse`, `MessageReadDto`, `MessageReadPayload`, `FileUploadResponse`, `WebSocketMessagePayload`, `GroupResponse`, etc.
  - `RelativeTimePipe`

## Development Guidelines
- Always refer back to this document for structural knowledge. Do not inject redundant explanations of these core models in prompts.
- Backend commands run from `secure-chat-backend/` (e.g., `cd secure-chat-backend && mvn spring-boot:run`)
- Frontend commands run from `secure-chat-frontend/` (e.g., `cd secure-chat-frontend && ng serve`)
