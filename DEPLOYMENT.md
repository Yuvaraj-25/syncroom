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
| **Backend** | Java 25 / Spring Boot 4 | Render | `https://syncroom-backend.onrender.com` |
| **Frontend** | Next.js 14 / React 18 | Vercel | `https://syncroom.vercel.app` |

---

## 🗄️ Step 1: Database Setup on Supabase

| Step | Action | Instructions / Details |
| :--- | :--- | :--- |
| **1** | **Create Project** | Go to [supabase.com](https://supabase.com) and create a **New Project**. |
| **2** | **Save Password** | Securely save your **Database Password**. |
| **3** | **Initialize Schema** | Go to **SQL Editor** from the left navigation, paste the contents of [`supabase/schema.sql`](supabase/schema.sql), and click **Run**. |
| **4** | **Retrieve Connection** | Navigate to **Project Settings** → **Database** → **Connection String** and select **URI** (or **Transaction Pooler**). |
| **5** | **JDBC URI** | Format: `jdbc:postgresql://db.<YOUR_PROJECT_REF>.supabase.co:5432/postgres?sslmode=require` |
| **6** | **Credentials** | **Username**: `postgres` (or `postgres.<YOUR_PROJECT_REF>` if using pooler)<br>**Password**: The password saved in Step 1.2 |

---

## 🐍 Step 2: Deploy Python Service on Render

| Configuration Field | Setting / Value |
| :--- | :--- |
| **Name** | `syncroom-python-service` |
| **Region** | Choose the region closest to you (e.g. *Singapore*, *Oregon*, *Frankfurt*) |
| **Branch** | `main` |
| **Root Directory** | `python-service` *(⚠️ Required)* |
| **Runtime** | **Docker** *(or Python 3)* |
| **Build Command** | Leave empty if Docker *(or `pip install -r requirements.txt` if Python 3)* |
| **Start Command** | Leave empty if Docker *(or `uvicorn main:app --host 0.0.0.0 --port $PORT` if Python 3)* |
| **Instance Type** | **Free** |

### Environment Variables:

| Variable Key | Value | Description |
| :--- | :--- | :--- |
| `CORS_ORIGIN` | `*` | Allowed origins (can update with your Vercel URL later) |

### Deployment Steps:
1. Log in to [**dashboard.render.com**](https://dashboard.render.com) and click **New +** → **Web Service**.
2. Connect your GitHub repository: `Yuvaraj-25/syncroom`.
3. Configure the settings above and add the environment variables.
4. Click **Deploy Web Service** and verify by opening `https://<your-service>.onrender.com/health` (should respond).

---

## ☕ Step 3: Deploy Spring Boot Backend on Render

| Configuration Field | Setting / Value |
| :--- | :--- |
| **Name** | `syncroom-backend` |
| **Region** | Choose the same region as your Python service |
| **Branch** | `main` |
| **Root Directory** | `backend` *(⚠️ Required)* |
| **Runtime** | **Docker** |
| **Instance Type** | **Free** *(or Starter)* |

### Environment Variables:

| Variable Key | Example Value | Description |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db.<REF>.supabase.co:5432/postgres?sslmode=require` | Supabase JDBC Connection URI |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Supabase DB Username |
| `SPRING_DATASOURCE_PASSWORD` | `YourSupabasePassword123` | Supabase DB Password |
| `CORS_ALLOWED_ORIGINS` | `https://your-frontend.vercel.app,http://localhost:3000` | Allowed frontend domains (comma-separated) |
| `JWT_SECRET` | `syncroom_jwt_super_secure_secret_key_minimum_256_bits_length_1234567890` | Secret key for JWT auth tokens |
| `YOUTUBE_API_KEY` | *(Optional)* | Google YouTube Data API v3 key |
| `OPENROUTER_API_KEY` | *(Optional)* | OpenRouter API Key for `@ai` assistant chat |

### Deployment Steps:
1. Click **New +** → **Web Service** on Render dashboard.
2. Connect `Yuvaraj-25/syncroom`.
3. Configure the settings above and add the environment variables.
4. Click **Deploy Web Service** and verify via `https://syncroom-backend.onrender.com/health` (should return `{"status":"ok"}`).

---

## ⚡ Step 4: Deploy Next.js Frontend on Vercel

| Configuration Field | Setting / Value |
| :--- | :--- |
| **Framework Preset** | `Next.js` |
| **Root Directory** | `frontend` *(⚠️ Required)* |

### Environment Variables:

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

### Deployment Steps:
1. Log in to [**vercel.com**](https://vercel.com), click **Add New...** → **Project** and Import `Yuvaraj-25/syncroom`.
2. Set configuration settings and environment variables as detailed above.
3. Click **Deploy**.

---

## 🔗 Step 5: Final Cross-Service Connection Check

| Step | Service | Action Required |
| :--- | :--- | :--- |
| **1** | **Backend CORS** | Go to `syncroom-backend` on Render → **Environment**.<br>Update `CORS_ALLOWED_ORIGINS` to include your exact Vercel production URL (e.g. `https://syncroom-frontend.vercel.app,http://localhost:3000`). Save and let it redeploy. |
| **2** | **Google OAuth** | In Google Cloud Console → Credentials → Authorized redirect URIs:<br>Add `https://your-app-name.vercel.app/api/auth/callback/google`. |

---

## ✅ You're Done!
Your watch party platform is now live across Supabase, Render, and Vercel!
