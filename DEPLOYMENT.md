# SyncRoom Deployment Guide (Render + Vercel + Supabase)

This guide provides the exact step-by-step instructions to deploy SyncRoom in production using:
- **Database**: [Supabase](https://supabase.com) (PostgreSQL)
- **Backend (Spring Boot)**: [Render](https://render.com) (Web Service)
- **Python Service (FastAPI)**: [Render](https://render.com) (Web Service)
- **Frontend (Next.js)**: [Vercel](https://vercel.com) (Next.js Hosting)

---

## 📋 Architecture Overview & URLs

| Service | Technology | Hosting Platform | Example Production URL |
| :--- | :--- | :--- | :--- |
| **Database** | PostgreSQL | Supabase | `db.<REF>.supabase.co:5432` |
| **Python Service** | FastAPI (Python 3.11) | Render | `https://syncroom-python.onrender.com` |
| **Backend** | Java 21 / Spring Boot 3 | Render | `https://syncroom-backend.onrender.com` |
| **Frontend** | Next.js 14 / React 18 | Vercel | `https://syncroom.vercel.app` |

---

## 🗄️ Step 1: Database Setup on Supabase

1. Go to [**supabase.com**](https://supabase.com) and create a **New Project**.
2. Save your **Database Password**.
3. Once the database is provisioned, click **SQL Editor** from the left navigation.
4. Copy the entire contents of [`supabase/schema.sql`](supabase/schema.sql) and paste it into the editor. Click **Run**.
5. Retrieve your connection details:
   - Go to **Project Settings** (gear icon) → **Database**.
   - Under **Connection String**, select **URI** (or **Transaction Pooler** in Session mode).
   - Your JDBC connection string will be:
     ```
     jdbc:postgresql://db.<YOUR_PROJECT_REF>.supabase.co:5432/postgres?sslmode=require
     ```
   - Username is `postgres` (or `postgres.<YOUR_PROJECT_REF>` if using the connection pooler).

---

## 🐍 Step 2: Deploy Python Service on Render

1. Log in to [**dashboard.render.com**](https://dashboard.render.com).
2. Click **New +** → **Web Service**.
3. Connect your GitHub repository: `Yuvaraj-25/syncroom`.
4. Configure the settings:
   - **Name**: `syncroom-python-service`
   - **Region**: Choose the region closest to you (e.g. *Singapore*, *Oregon*, *Frankfurt*)
   - **Branch**: `main`
   - **Root Directory**: `python-service` *(⚠️ Required)*
   - **Runtime**: **Docker** *(or Python 3)*
   - **Build Command**: *(leave empty if Docker, or `pip install -r requirements.txt` if Python 3)*
   - **Start Command**: *(leave empty if Docker, or `uvicorn main:app --host 0.0.0.0 --port $PORT` if Python 3)*
   - **Instance Type**: **Free**
5. Add **Environment Variables**:

| Variable Key | Value | Description |
| :--- | :--- | :--- |
| `CORS_ORIGIN` | `*` | Allowed origins (can update with your Vercel URL later) |

6. Click **Deploy Web Service**.
7. Copy your public URL (e.g. `https://syncroom-python-service.onrender.com`).
   - Test it by opening `https://syncroom-python-service.onrender.com/health` in your browser.

---

## ☕ Step 3: Deploy Spring Boot Backend on Render

1. On [**dashboard.render.com**](https://dashboard.render.com), click **New +** → **Web Service**.
2. Connect your GitHub repository: `Yuvaraj-25/syncroom`.
3. Configure the settings:
   - **Name**: `syncroom-backend`
   - **Region**: Choose the same region as your Python service
   - **Branch**: `main`
   - **Root Directory**: `backend` *(⚠️ Required)*
   - **Runtime**: **Docker**
   - **Instance Type**: **Free** *(or Starter)*
4. Under **Environment Variables**, add the following:

### Backend Environment Variables on Render:

| Variable Key | Example Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db.<REF>.supabase.co:5432/postgres?sslmode=require` | Supabase JDBC Connection URI |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Supabase DB Username |
| `SPRING_DATASOURCE_PASSWORD` | `YourSupabasePassword123` | Supabase DB Password |
| `CORS_ALLOWED_ORIGINS` | `https://your-frontend.vercel.app,http://localhost:3000` | Allowed frontend domains (comma-separated) |
| `JWT_SECRET` | `syncroom_jwt_super_secure_secret_key_minimum_256_bits_length_1234567890` | Secret key for JWT auth tokens |
| `YOUTUBE_API_KEY` | *(Optional)* | Google YouTube Data API v3 key |
| `OPENROUTER_API_KEY` | *(Optional)* | OpenRouter API Key for `@ai` assistant chat |

5. Click **Deploy Web Service**.
6. Once deployed and marked **Live**, copy your backend URL:
   ```
   https://syncroom-backend.onrender.com
   ```
   - Test the health endpoint: `https://syncroom-backend.onrender.com/health` → `{"status":"ok"}`.

---

## ⚡ Step 4: Deploy Next.js Frontend on Vercel

1. Log in to [**vercel.com**](https://vercel.com).
2. Click **Add New...** → **Project** → Import `Yuvaraj-25/syncroom`.
3. In the project configuration:
   - **Framework Preset**: `Next.js`
   - **Root Directory**: Click **Edit** and select `frontend` *(⚠️ Required)*
4. Expand the **Environment Variables** section and add:

### Frontend Environment Variables on Vercel:

| Variable Key | Example Value | Description |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_SOCKET_URL` | `https://syncroom-backend.onrender.com` | Backend WebSocket / REST URL *(HTTPS)* |
| `NEXT_PUBLIC_PEER_HOST` | `syncroom-backend.onrender.com` | Hostname for WebRTC PeerJS *(without https://)* |
| `NEXT_PUBLIC_PEER_PORT` | `443` | Port 443 for HTTPS/WSS on Render |
| `NEXT_PUBLIC_PYTHON_SERVICE_URL` | `https://syncroom-python-service.onrender.com` | Python metadata service URL |
| `NEXTAUTH_URL` | `https://your-app-name.vercel.app` | Production domain of your Vercel deployment |
| `NEXTAUTH_SECRET` | `syncroom_production_auth_secret_9876543210_abcdef` | Random 32+ character string for NextAuth session encryption |
| `GOOGLE_CLIENT_ID` | `123456789-abc.apps.googleusercontent.com` | *(Optional)* Google OAuth Client ID |
| `GOOGLE_CLIENT_SECRET` | `GOCSPX-abcdef123456789` | *(Optional)* Google OAuth Secret |

5. Click **Deploy**.

---

## 🔗 Step 5: Final Cross-Service Connection Check

1. **Update Backend CORS**:
   - Go to your `syncroom-backend` on Render → **Environment**.
   - Update `CORS_ALLOWED_ORIGINS` to include your exact Vercel production URL (e.g. `https://syncroom-frontend.vercel.app,http://localhost:3000`).
   - Save changes (Render will trigger a quick redeploy).

2. **Update Google OAuth Redirect URI (If using Google Auth)**:
   - In Google Cloud Console → Credentials → Authorized redirect URIs:
     Add `https://your-app-name.vercel.app/api/auth/callback/google`.

---

## ✅ You're Done!
Your watch party platform is now live across Supabase, Render, and Vercel!
