<p align="center">
  <img src="https://img.icons8.com/fluency/96/tv-show.png" alt="SyncRoom Logo" width="80" />
</p>

<h1 align="center">SyncRoom</h1>

<p align="center">
  <b>Watch Together. Chat Together. Be Together.</b><br/>
  A real-time, Java Full-Stack watch party application for synchronized video viewing, interactive polls, Q&A, whiteboard, and WebRTC video/voice calls with friends.
</p>

<p align="center">
  <a href="https://syncroom-frontend.vercel.app/">
    <img src="https://img.shields.io/badge/🌐_Live_Demo-syncroom--frontend.vercel.app-indigo?style=for-the-badge" alt="Live Demo" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Frontend-Next.js_14-black?style=flat-square&logo=next.js" alt="Next.js" />
  <img src="https://img.shields.io/badge/Backend-Java_21_+_Spring_Boot_3-orange?style=flat-square&logo=openjdk" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Database-Supabase_/_PostgreSQL-3ECF8E?style=flat-square&logo=supabase" alt="Supabase" />
  <img src="https://img.shields.io/badge/Real--Time-Spring_WebSocket_+_Socket.IO-blue?style=flat-square&logo=socket.io" alt="Socket.IO" />
  <img src="https://img.shields.io/badge/WebRTC-PeerJS-orange?style=flat-square&logo=webrtc" alt="WebRTC" />
  <img src="https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square&logo=apachemaven" alt="Maven" />
  <img src="https://img.shields.io/badge/Security-Spring_Security_6_+_JWT-6DB33F?style=flat-square&logo=springsecurity" alt="Spring Security" />
  <img src="https://img.shields.io/badge/Microservice-FastAPI-teal?style=flat-square&logo=fastapi" alt="FastAPI" />
  <img src="https://img.shields.io/badge/TailwindCSS-3-38bdf8?style=flat-square&logo=tailwindcss" alt="TailwindCSS" />
</p>

---

## ✨ Features

### 🎬 Synchronized Video Playback
- **YouTube Integration** — Paste any YouTube URL or search YouTube directly within the room; videos sync across all participants in real time.
- **Local File Support** — Upload and play local video files directly in the browser.
- **Play / Pause / Seek Sync** — Every action is broadcast via Socket.IO/WebSockets with sub-second synchronization.
- **Video Queue** — Add multiple videos to a collaborative queue, reorder, or autoplay the next item.

### 💬 Real-Time Chat & AI Assistant
- **Instant Messaging** — Low-latency chat broadcast to all room participants.
- **AI Assistant Integration** — Mention `@ai <query>` to get instant answers in chat powered by OpenRouter / Gemini.
- **User Avatars** — Profile pictures and DiceBear adventurer avatars displayed alongside messages.
- **Emoji Reactions** — Interactive emoji bursts appearing dynamically on screen for all members.

### 📹 Voice & Video Calls (WebRTC)
- **Peer-to-Peer Video** — Direct WebRTC connections via PeerJS signaling.
- **Camera & Mic Controls** — Easy toggling of audio/video streams with state sync.
- **Screen Sharing** — Real-time screen capture sharing with automatic reversion to camera.
- **Tab-Switch Media Recovery** — Seamless media track recovery when returning from inactive tabs.

### 📊 Interactive Room Tools
- **Collaborative Whiteboard** — Real-time drawing canvas synced among participants.
- **Live Polls** — Create polls, vote in real-time, close, and view instant results.
- **Interactive Q&A** — Post questions, upvote popular questions, and submit answers.
- **Host Moderation** — Primary owner controls: kick users, force mute, lower all hands, transfer host permissions.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Frontend (Next.js 14 / React 18)             │
│  Landing Page ──► Dashboard ──► Watch Room                      │
│  (Google OAuth)   (Create/Join)  (Video + Chat + WebRTC + Tools)│
│                                                                 │
│  State: Zustand │ Hooks: useWebRTC │ UI: shadcn/ui + Tailwind  │
└────────────┬────────────────────────┬───────────────────────────┘
             │ REST API + Socket.IO   │ WebRTC PeerJS Signaling
             ▼                        ▼
┌─────────────────────────────────────────────────────────────────┐
│              Backend (Java 21 + Spring Boot 3)                  │
│              Port: 4000                                         │
│                                                                 │
│  • Spring MVC REST Controllers (/api/users, /api/rooms, /health)│
│  • Spring Data JPA Repositories (PostgreSQL / Supabase)         │
│  • Spring Security 6 + JWT Stateless Authentication            │
│  • Spring WebSocket (Socket.IO v4 Engine + PeerJS Signaling)    │
│  • In-Memory Thread-Safe RoomStateManager                      │
│  • AI Assistant Service (OpenRouter / Gemini)                   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Supabase / PostgreSQL Database                     │
│  Tables: users │ user_recent_rooms │ rooms │ messages           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites
- **Java**: JDK 21+ (`java -version`)
- **Maven**: 3.9+ (`mvn -version`)
- **Node.js**: v18+ (`node -v`)
- **Python**: 3.10+ (for metadata service)
- **Supabase / PostgreSQL**: Hosted Supabase project or local PostgreSQL instance (port 5432).

---

### Backend Setup (Spring Boot)

1. Navigate to the `backend` directory:
   ```bash
   cd backend
   ```

2. Configure environment properties in `src/main/resources/application.properties` (or set environment variables):
   ```properties
   server.port=4000
   spring.datasource.url=jdbc:postgresql://localhost:5432/syncroom
   spring.datasource.username=postgres
   spring.datasource.password=postgres
   cors.allowed-origins=http://localhost:3000
   jwt.secret=syncroom_jwt_super_secure_secret_key_minimum_256_bits_length_1234567890
   youtube.api.key=YOUR_YOUTUBE_API_KEY # (Optional)
   openrouter.api.key=YOUR_OPENROUTER_API_KEY # (Optional)
   ```
   > **Note for Supabase Cloud**: For hosted Supabase, set `spring.datasource.url=jdbc:postgresql://db.<PROJECT_REF>.supabase.co:5432/postgres?sslmode=require` and use your Supabase database password. Run `supabase/schema.sql` in the Supabase SQL Editor.

3. Build and run the backend:
   ```bash
   # Run tests
   mvn clean test

   # Start Spring Boot application
   mvn spring-boot:run
   ```

   The backend will start on **`http://localhost:4000`**.

---

### Frontend Setup (Next.js)

1. Navigate to the `frontend` directory:
   ```bash
   cd frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Ensure `.env.local` is configured:
   ```env
   NEXT_PUBLIC_SOCKET_URL=http://localhost:4000
   NEXT_PUBLIC_PEER_HOST=localhost
   NEXT_PUBLIC_PEER_PORT=4000
   NEXT_PUBLIC_PYTHON_SERVICE_URL=http://localhost:8000
   NEXTAUTH_URL=http://localhost:3000
   NEXTAUTH_SECRET=your_nextauth_secret
   ```

4. Start development server:
   ```bash
   npm run dev
   ```

   The frontend will run on **`http://localhost:3000`**.

---

### Python Metadata Service Setup

1. Navigate to `python-service`:
   ```bash
   cd python-service
   ```

2. Set up virtual environment and run:
   ```bash
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   pip install -r requirements.txt
   uvicorn main:app --host 0.0.0.0 --port 8000 --reload
   ```

---

### One-Click Startup Script

- **Windows**: Run `start-dev.bat`
- **Linux / macOS**: Run `./start-dev.sh`

---

## 🐳 Docker Deployment

To spin up all services (PostgreSQL, Spring Boot Backend, Python Service, Next.js Frontend) simultaneously:

```bash
docker-compose up --build
```

---

## 📡 REST API Reference

| Method | Endpoint | Description | Request Body / Params | Status |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/health` | Server health check | None | `200 OK` |
| `POST` | `/api/users/sync` | Sync user on sign-in | `{ "name": "...", "email": "...", "image": "..." }` | `200 OK` |
| `GET` | `/api/users/profile` | Get user profile | `?email=user@example.com` | `200 OK` |
| `POST` | `/api/users/profile/update` | Update name / avatar | `{ "email": "...", "name": "...", "image": "..." }` | `200 OK` |
| `GET` | `/api/users/recent-rooms` | Fetch recent room history | `?email=user@example.com` | `200 OK` |
| `POST` | `/api/users/recent-rooms` | Add room to history | `{ "email": "...", "roomId": "..." }` | `200 OK` |
| `GET` | `/api/rooms/{roomId}` | Get room details | Path variable `{roomId}` | `200 OK` |
| `POST` | `/api/rooms/create` | Create new watch room | `{ "roomId": "...", "ownerId": "...", "isPrivate": false, "password": "..." }` | `201 Created` |
| `GET` | `/api/youtube/search` | Search YouTube videos | `?q=search_query` | `200 OK` |
| `GET` | `/peerjs/myapp/id` | PeerJS ID allocation | None | `200 OK` |

---

## 🔄 MERN to Java Spring Boot Architecture Mapping

| Layer / Feature | Original MERN Implementation | New Java Spring Boot Implementation |
| :--- | :--- | :--- |
| **Server Framework** | Express 5.x (`server.js`) | Spring Boot 3.3.x (`SyncRoomApplication.java`) |
| **ORM / Database** | Mongoose (`models/*.js`) | Spring Data JPA + Supabase / PostgreSQL (`@Entity`, `JpaRepository`) |
| **Security & Auth** | Custom Express handlers + JWT | Spring Security 6 (`SecurityFilterChain`, `JwtTokenProvider`, `JwtAuthenticationFilter`) |
| **Password Hashing** | Manual / Plaintext | BCrypt (`BCryptPasswordEncoder`) |
| **Real-time Protocol** | `socket.io` npm package | Spring WebSocket (`SocketIoWebSocketHandler` Engine.IO 4 / Socket.IO 4 protocol) |
| **WebRTC Signaling** | `peer` npm package (`ExpressPeerServer`) | Spring WebSocket (`PeerJsWebSocketHandler` + `PeerJsHttpController`) |
| **In-Memory State** | Node `Map` in `socket/index.js` | Thread-safe `RoomStateManager` (`ConcurrentHashMap`) |
| **AI Assistant** | Node `fetch` to OpenRouter | Spring `RestClient` in `AiAssistantService` |
| **Build & Dependency** | npm (`package.json`) | Apache Maven (`pom.xml`) with Java 21 |
| **Containerization** | Node Dockerfile | Multi-stage Eclipse Temurin 21 Dockerfile |
| **CI/CD** | None | GitHub Actions (`.github/workflows/ci.yml`) |