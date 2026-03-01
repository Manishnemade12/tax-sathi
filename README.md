# TaxSathi

TaxSathi is a full-stack tax assistant for FY 2025-26 with:

- React + Vite frontend for onboarding, documents, analysis, and dashboard
- Go + Chi backend API with Supabase-authenticated routes
- Supabase (Auth, Postgres, Storage) as the primary data layer
- AI-assisted document extraction and tax strategy via edge-function workflow

## Features

- Secure authentication with Supabase Auth
- Guided onboarding and profile completion
- Document upload and AI-based extraction
- Financial data capture and tax analysis
- Regime-aware guidance and tax-saving suggestions
- Dashboard insights and scheme recommendations
- TaxBuddy strategy endpoint for quick personalized advice

## Tech Stack

### Frontend

- React 18 + TypeScript
- Vite
- Tailwind CSS + shadcn/ui
- TanStack Query
- Supabase JS client

### Backend

- Go 1.22+
- Chi router + middleware
- CORS middleware
- Supabase REST/Storage integration

### Infra/Data

- Supabase Postgres + RLS
- Supabase Storage
- Supabase Edge Functions

## Project Structure

```text
tax-sathi/
	src/                 # Frontend app
	backend/             # Go API server
	supabase/            # Migrations and edge functions
	docs/                # Internal workflow docs
```

## Prerequisites

- Node.js 18+ and npm
- Go 1.22+
- Supabase project credentials

## Environment Setup

### 1) Frontend env (`.env` in repo root)

```dotenv
VITE_SUPABASE_URL=your_supabase_url
VITE_SUPABASE_PUBLISHABLE_KEY=your_supabase_anon_key
VITE_API_URL=http://localhost:8080
```

### 2) Backend env (`backend/.env`)

Use [backend/.env.example](backend/.env.example) as the template.

```dotenv
SUPABASE_URL=your_supabase_url
SUPABASE_ANON_KEY=your_supabase_anon_key
PORT=8080
FRONTEND_URL=http://localhost:5173
GROQ_API_KEY=your_groq_api_key
GROQ_MODEL=llama-3.3-70b-versatile
```

### Security note

- Never commit `.env` files.
- If a key is accidentally committed, rotate it immediately.

## Local Development

### 1) Install frontend dependencies

```bash
npm install
```

### 2) Run backend

```bash
cd backend
go mod download
go run .
```

Backend default URL: `http://localhost:8080`

### 3) Run frontend

Open a new terminal in project root:

```bash
npm run dev
```

Frontend default URL: `http://localhost:5173`

### 4) Verify health

```bash
curl http://localhost:8080/health
```

Expected:

```json
{"status":"ok"}
```

## Frontend Scripts

- `npm run dev` - Start Vite dev server
- `npm run build` - Build production bundle
- `npm run build:dev` - Build in development mode
- `npm run preview` - Preview production build
- `npm run lint` - Run ESLint
- `npm run test` - Run Vitest once
- `npm run test:watch` - Run Vitest in watch mode

## Backend API (Current Routes)

Public:

- `GET /health`

Authenticated (`Authorization: Bearer <supabase-jwt>`):

- `GET /api/profile`
- `PUT /api/profile`
- `POST /api/onboarding/complete`
- `GET /api/documents`
- `POST /api/documents/upload`
- `DELETE /api/documents/{id}`
- `POST /api/documents/{id}/analyze`
- `GET /api/financial-data`
- `PUT /api/financial-data`
- `GET /api/tax-analysis`
- `POST /api/tax-analysis/run`
- `POST /api/taxbuddy/strategy`
- `GET /api/dashboard/stats`
- `GET /api/schemes`
- `POST /api/schemes/personalized`

## Database & Migrations

Supabase migrations are in [supabase/migrations](supabase/migrations).

Main consolidated schema:

- [supabase/migrations/20260227184500_consolidated_schema.sql](supabase/migrations/20260227184500_consolidated_schema.sql)

## Troubleshooting

### Backend exits immediately

- Ensure `backend/.env` exists and has valid values.
- Check if port 8080 is occupied: `ss -ltnp | grep :8080`

### Frontend loads but API calls fail

- Confirm `VITE_API_URL` points to running backend.
- Confirm user is authenticated in Supabase.
- Confirm backend receives JWT in `Authorization` header.

### CORS errors

- Localhost origins are allowed by backend.
- For non-local frontend URLs, set `FRONTEND_URL` in `backend/.env`.

## Contributing

1. Create a branch from `main`.
2. Keep changes focused and small.
3. Run lint/tests before pushing.
4. Open a PR with clear context and screenshots (if UI changes).

## Disclaimer

TaxSathi provides AI-assisted guidance for educational and informational use. Always verify final filing decisions with a qualified tax professional.
