# 🔒 Secure Chat System

Welcome to the **Secure Chat System** repository! This is a full-stack, real-time secure messaging application built with a modern tech stack focusing on performance, security, and exceptional user experience.

## ✨ Unique Selling Propositions (USPs) & Features

Here's what we have built so far:

- **Real-Time Communication**: Lightning-fast message delivery using **WebSocket** and the **STOMP** protocol.
- **Robust Security**: 
  - JWT-based Authentication & Role-based Authorization (`USER` & `ADMIN`).
  - XSS-sanitized persistence to protect against cross-site scripting.
  - End-to-end focus with public key provision for securing outbound messages.
- **Self-Destructing (Ephemeral) Messages**: Send messages that automatically expire after a set time.
- **Rich Media Sharing**: Support for file and image attachments (up to 10MB).
- **Group Chats**: Create private or group conversations with secure, referral-code-based joining.
- **Read Receipts**: Real-time tracking of which users have read specific messages.
- **Admin Controls**: Administrative features including the ability to PIN important messages in a conversation.
- **Modern Tech Stack**: 
  - **Backend**: Spring Boot 3.3 + Java 21, Spring Security, Spring Data JPA (PostgreSQL).
  - **Frontend**: Angular 19 with RxStomp for reactive WebSocket state management.

## 🏗️ Architecture Overview

The project is structured as a monorepo containing both frontend and backend:

```text
Secure Chat System/
├── secure-chat-backend/       ← Spring Boot 3.3 + Java 21 Backend (REST API + WebSockets)
├── secure-chat-frontend/      ← Angular 19 Client Application
├── docs/                      ← Architectural context and implementation assumptions
└── README.md                  ← You are here
```

For an in-depth look at our models, controllers, and frontend structure, please check the [Project Context](project_context.md) and our [Implementation Assumptions](docs/implementation_assumptions.txt) docs.

## 🚀 Getting Started

### Prerequisites
- **Java 21**
- **Node.js** (latest LTS recommended)
- **Angular CLI 19**
- **PostgreSQL** Database
- **Maven**

### Backend Setup
1. Navigate to the backend directory:
   ```bash
   cd secure-chat-backend
   ```
2. Configure your database and environment variables in `.env` / `application.yml`.
3. **File Uploads**: The application stores file attachments locally in the `secure-chat-backend/uploads/` directory.
4. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```

### Frontend Setup
1. Navigate to the frontend directory:
   ```bash
   cd secure-chat-frontend
   ```
2. Install the required npm packages:
   ```bash
   npm install
   ```
3. Start the Angular development server:
   ```bash
   ng serve
   ```
4. Access the app at `http://localhost:4200`.

### Testing
You can run the provided `test-chat.ps1` PowerShell script located in the root of the repository to quickly test the chat API endpoints and verify functionality.

## 🤝 Contributing

**We are very happy to get pull requests for this project!** 

Whether it's bug fixes, feature additions, or documentation improvements, your contributions are welcome.

### How to Contribute
1. Fork the repository.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

Please ensure your code aligns with our existing architecture and check the `docs/implementation_assumptions.txt` for context on current implementations.

---
*Built with ❤️ for secure and fast communication.*
