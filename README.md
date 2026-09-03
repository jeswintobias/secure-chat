# 🔒 Secure Chat System

[![Build & Test](https://github.com/jeswintobias/secure-chat/actions/workflows/build.yml/badge.svg)](https://github.com/jeswintobias/secure-chat/actions/workflows/build.yml)

Welcome to the **Secure Chat System** repository! This is a full-stack, real-time secure messaging application built with a modern tech stack focusing on performance, security, and exceptional user experience.

## ✨ Unique Selling Propositions (USPs) & Features

Here is what sets the Secure Chat System apart as a premium, enterprise-grade solution:

- **Authentication & Onboarding**:
  - Seamless **Google Identity Services (OAuth 2.0)** integration for 1-click Sign In & Sign Up.
  - Custom-styled, native-feeling premium Google overlay buttons.
  - JWT-based Auth & Role-based Access Control (`USER` & `ADMIN`).
- **Premium SaaS UI/UX**:
  - Exquisite dark-mode aesthetic utilizing `DM Sans` geometric typography.
  - Minimalist, highly functional sidebar navigation.
  - Smooth micro-animations and perfectly rounded glassmorphism elements.
- **Enterprise-Grade Security**:
  - Advanced 4-layer URL security pipeline (Syntax, DNS/SSRF, HTTP Probing, Safe Browsing).
  - Strict XSS-sanitized persistence (JSoup whitelist).
  - Built-in Local Key Management infrastructure preparing for End-to-End Encryption (E2EE).
- **Real-Time Communication Engine**: 
  - Lightning-fast, bidirectional message delivery using **WebSocket** and **STOMP**.
  - **RxStomp** integration on Angular for robust connection resilience.
- **Advanced Chat Capabilities**:
  - **Self-Destructing (Ephemeral)** messages.
  - Live **Read Receipts** and typing indicators.
  - Admin-only **Message Pinning**.
  - **Rich Media** sharing (up to 10MB file and image attachments).
- **Modern Tech Stack**: 
  - **Backend**: Spring Boot 3.3 + Java 23, Spring Security, Spring Data JPA (PostgreSQL).
  - **Frontend**: Angular 19 with Reactive patterns.

## 💡 Technical Highlights (For Recruiters & Developers)

This project was built to demonstrate advanced, production-ready software engineering practices:

- **Hybrid OAuth 2.0 & JWT Architecture**: 
  Implemented a custom authentication bridge using Google Identity Services. The Angular frontend handles the seamless 1-click UX (using an advanced invisible HTML overlay technique to bypass iframe styling limits), while the Spring Boot backend cryptographically verifies the `id_token` via Google's API Client before auto-provisioning the Postgres user and issuing a stateless internal JWT.
- **Automated Testing Suite (TDD Practices)**: 
  Maintains high code reliability through a robust automated testing strategy. Features backend Unit & Integration tests using **JUnit 5** and **Mockito** (mocking database layers like `MessageReactionRepository` for isolated service testing), and frontend component tests using **Karma/Jasmine**.
- **Real-Time Distributed State**:
  Architected a resilient WebSocket layer using the STOMP protocol. Solved complex real-time race conditions (like message read receipts and typing indicators) using RxJS observables (`RxStomp`) on the frontend to maintain a synchronized, localized state without overwhelming the backend.
- **Defense-in-Depth Security**:
  Beyond standard JWT authentication, this application employs defense-in-depth strategies including a 4-tier URL security pipeline (DNS lookups, HTTP probing) to prevent SSRF (Server-Side Request Forgery), and rigorous XSS sanitization utilizing JSoup.

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

#### Automated Tests
```bash
# Backend: JUnit 5 + Mockito unit tests and Spring Boot integration tests (H2 in-memory DB)
cd secure-chat-backend
mvn test -Dspring.profiles.active=test

# Frontend: Karma + Jasmine tests (Chrome headless)
cd secure-chat-frontend
npx ng test --browsers=ChromeHeadless --watch=false
```

#### Manual API Testing
You can also run the provided `test-chat.ps1` PowerShell script located in the root of the repository to quickly test the chat API endpoints and verify functionality.

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
