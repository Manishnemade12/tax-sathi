# TaxSathi Java Spring Boot Backend — Architecture & Skills Documentation

> **Purpose:** This document is the single source of truth for the Java backend.  
> Feed this file to any AI assistant before making changes to the backend.  
> Last updated: 2026-03-07

---

## 1. Project Overview

| Item | Value |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Build Tool | Maven |
| Port | 8080 (same as Go backend) |
| Root Package | `com.taxsathi` |
| Source Root | `backend-java/src/main/java/com/taxsathi/` |

The backend is a stateless REST API that acts as a secure proxy between the React frontend and Supabase (PostgreSQL, Storage, Edge Functions) plus the Groq AI API.

---

## 2. Folder Structure

```
backend-java/
├── pom.xml                              ← Maven build (deps, plugins)
├── .env                                 ← Local dev secrets (never commit real keys)
├── src/main/resources/
│   └── application.yml                  ← App config, reads from env vars
└── src/main/java/com/taxsathi/
    ├── TaxSathiApplication.java          ← Entry point, loads .env
    ├── config/
    │   ├── AppConfig.java               ← HttpClient + ObjectMapper beans
    │   ├── CorsConfig.java              ← CORS rules (localhost any port + FRONTEND_URL)
    │   └── SecurityConfig.java          ← Spring Security (stateless, JWT filter)
    ├── security/
    │   ├── JwtAuthFilter.java           ← Validates Supabase JWT on every request
    │   └── UserPrincipal.java           ← Record: userId, email, jwt
    ├── client/
    │   └── SupabaseClient.java          ← HTTP wrapper: REST, Storage, Edge Functions
    ├── service/
    │   ├── ProfileService.java          ← Profile CRUD + onboarding
    │   ├── DocumentService.java         ← Upload, list, delete, analyze documents
    │   ├── FinancialService.java        ← Financial data CRUD (auto-create on first GET)
    │   ├── TaxAnalysisService.java      ← Get/run tax analysis via Edge Function
    │   ├── TaxBuddyService.java         ← ITR form logic + Groq AI strategy generation
    │   ├── DashboardService.java        ← Aggregated stats for the dashboard
    │   └── SchemesService.java          ← Cached + personalized scheme recommendations
    ├── controller/
    │   ├── HealthController.java        ← GET /health (public)
    │   ├── ProfileController.java       ← GET|PUT /api/profile, POST /api/onboarding/complete
    │   ├── DocumentController.java      ← CRUD + analyze /api/documents
    │   ├── FinancialController.java     ← GET|PUT /api/financial-data
    │   ├── TaxAnalysisController.java   ← GET|POST /api/tax-analysis
    │   ├── TaxBuddyController.java      ← POST /api/taxbuddy/strategy
    │   ├── DashboardController.java     ← GET /api/dashboard/stats
    │   └── SchemesController.java       ← GET /api/schemes, POST /api/schemes/personalized
    ├── dto/
    │   ├── TaxBuddyRequest.java         ← Request body for TaxBuddy strategy
    │   ├── OnboardingRequest.java       ← Request body for onboarding completion
    │   └── TaxAnalysisRequest.java      ← Request body for running tax analysis
    ├── exception/
    │   ├── SupabaseException.java       ← Unchecked exception for Supabase API errors
    │   └── GlobalExceptionHandler.java  ← @ControllerAdvice → JSON error responses
    └── util/
        ├── SecurityUtils.java           ← Get userId/jwt from SecurityContextHolder
        └── FinancialFieldUtil.java      ← Field whitelist + alias normalization
```

---

## 3. API Endpoints

All `/api/*` routes require `Authorization: Bearer <supabase-jwt>` header.

| Method | Path | Controller Method | Notes |
|--------|------|--------------------|-------|
| GET | `/health` | `HealthController.health` | Public, no auth |
| GET | `/api/profile` | `ProfileController.getProfile` | Returns 404 if no profile |
| PUT | `/api/profile` | `ProfileController.updateProfile` | Whitelist: full_name, employment_type, age_group, tax_regime, income_sources |
| POST | `/api/onboarding/complete` | `ProfileController.completeOnboarding` | Sets onboarding_completed=true, seeds financial_data |
| GET | `/api/documents` | `DocumentController.listDocuments` | Ordered by created_at desc |
| POST | `/api/documents/upload` | `DocumentController.upload` | Multipart file upload to Supabase Storage |
| DELETE | `/api/documents/{id}` | `DocumentController.delete` | Deletes from storage + DB (ownership verified) |
| POST | `/api/documents/{id}/analyze` | `DocumentController.analyze` | Calls `analyze-document` Edge Function |
| GET | `/api/financial-data` | `FinancialController.getFinancialData` | Auto-creates blank record if none exists |
| PUT | `/api/financial-data` | `FinancialController.updateFinancialData` | Only whitelisted financial fields pass through |
| GET | `/api/tax-analysis` | `TaxAnalysisController.getAnalysis` | Returns `null` JSON if no analysis yet |
| POST | `/api/tax-analysis/run` | `TaxAnalysisController.runAnalysis` | Saves fin data then calls `tax-analysis` Edge Function |
| POST | `/api/taxbuddy/strategy` | `TaxBuddyController.generateStrategy` | ITR form determination + Groq AI response |
| GET | `/api/dashboard/stats` | `DashboardController.getStats` | Aggregates docs, income, tax, savings |
| GET | `/api/schemes` | `SchemesController.getSchemes` | Reads cached scheme_recommendations from DB |
| POST | `/api/schemes/personalized` | `SchemesController.getPersonalized` | Calls `tax-analysis` Edge Function for fresh results |

---

## 4. Authentication Flow

```
Frontend (React)
    │
    └── HTTP Request with "Authorization: Bearer <supabase-jwt>"
           │
           ▼
    JwtAuthFilter (runs on every request)
           │
           ├── Extracts Bearer token
           ├── Calls GET {SUPABASE_URL}/auth/v1/user with token
           │       SupabaseClient.validateToken()
           │
           ├── On success: creates UserPrincipal(userId, email, jwt)
           │              stores in SecurityContextHolder
           │
           └── On failure: leaves context empty → Spring Security returns 401

    Controller
           │
           └── SecurityUtils.getUserId() / getUserJwt()
                   Reads from SecurityContextHolder
```

---

## 5. Supabase Integration (SupabaseClient)

`SupabaseClient` is the single gateway to all Supabase services. All methods take a `jwt` parameter (the user's Supabase token) to enforce RLS policies.

### PostgREST Methods
| Method | Supabase Call | Notes |
|--------|--------------|-------|
| `query(table, queryParams, jwt)` | GET /rest/v1/{table}?{params} | Returns JSON array string |
| `querySingle(table, queryParams, jwt)` | GET with `Accept: application/vnd.pgrst.object+json` | Returns null on 406 (not found) |
| `count(table, queryParams, jwt)` | HEAD with `Prefer: count=exact` | Reads Content-Range header |
| `insert(table, data, jwt)` | POST /rest/v1/{table} | Returns inserted record(s) |
| `update(table, filter, data, jwt)` | PATCH /rest/v1/{table}?{filter} | Returns updated record(s) |
| `delete(table, filter, jwt)` | DELETE /rest/v1/{table}?{filter} | Void return |

### Storage Methods
| Method | Description |
|--------|------------|
| `storageUpload(bucket, path, bytes, contentType, jwt)` | POST /storage/v1/object/{bucket}/{path} |
| `storageDownload(bucket, path, jwt)` | GET → returns StorageFile(data, contentType) |
| `storageDelete(bucket, paths[], jwt)` | DELETE with body `{"prefixes": [...]}` |

### Edge Functions
| Method | Description |
|--------|------------|
| `invokeEdgeFunction(name, body, jwt)` | POST /functions/v1/{name} |

---

## 6. Financial Field Whitelist

Only these fields are accepted in financial data update requests (`PUT /api/financial-data` and `POST /api/tax-analysis/run`):

```
gross_salary, hra_received, lta_received, other_income, rental_income,
interest_income, business_income, deductions_80c, deductions_80d,
deductions_80e, deductions_80g, deductions_nps, deductions_hra,
deductions_lta, standard_deduction, other_deductions, raw_data, financial_year
```

**Aliases** (old key → canonical key):
```
deduction_80c    → deductions_80c
deduction_80d    → deductions_80d
deduction_80e    → deductions_80e
deduction_80g    → deductions_80g
deduction_nps    → deductions_nps
hra_exemption    → deductions_hra
professional_tax → other_deductions
```

**System fields always stripped:** `id`, `user_id`, `created_at`, `updated_at`

Managed by: `FinancialFieldUtil.normalizeAndFilter()`

---

## 7. TaxBuddy AI Service — ITR Form Logic

Pure Java logic (no AI needed for this part) determines the correct ITR form:

```
Has business/freelancing income?  → ITR-3
Is a company director?            → ITR-2
Has capital gains?                → ITR-2
Residential status = NRI/RNOR?   → ITR-2
Estimated income > ₹50 lakh?     → ITR-2
Otherwise                         → ITR-1
```

**Smart Alerts triggered when:**
- Income > ₹50L → Schedule AL required
- Age ≥ 60 → Section 80TTB benefit available
- Has agricultural income → Partial integration warning

The ITR verdict + alerts + user context are bundled into a prompt sent to **Groq** (`llama-3.3-70b-versatile` by default). The AI response is sanitized to strip markdown symbols before returning to the frontend.

---

## 8. Environment Variables

| Variable | Used By | Description |
|----------|---------|-------------|
| `SUPABASE_URL` | `SupabaseClient` | Supabase project URL |
| `SUPABASE_ANON_KEY` | `SupabaseClient` | Supabase anonymous key (sent as `apikey` header) |
| `GROQ_API_KEY` | `TaxBuddyService` | Groq LLM API key |
| `GROQ_MODEL` | `TaxBuddyService` | Default: `llama-3.3-70b-versatile` |
| `PORT` | Spring Boot | Server port, default 8080 |
| `FRONTEND_URL` | `CorsConfig` | Allowed CORS origin in production |

All vars are loaded from `.env` in development (via `dotenv-java`). In production, set them as system environment variables.

---

## 9. Key Design Decisions

### Why not use Spring Data / JPA?
The backend talks to Supabase via its REST API (PostgREST), not a direct JDBC/JNDI connection. Using raw `HttpClient` with Supabase's REST API respects Row Level Security (RLS) policies — a direct DB connection would bypass RLS entirely.

### Why is ObjectMapper using SNAKE_CASE?
Supabase PostgREST returns snake_case JSON. `PropertyNamingStrategies.SNAKE_CASE` ensures automatic field mapping without `@JsonProperty` on every field.

### Why is the response body a raw String in some controllers?
Some Supabase endpoints (like document list, financial data) return opaque JSON that we stream directly to the frontend without deserializing + re-serializing. Using `ResponseEntity<String>` with `Content-Type: application/json` is the most efficient approach and avoids any data loss from type coercion.

### Why is the JWT validated via Supabase HTTP call (not locally)?
Supabase uses asymmetric JWT signing. To verify locally you'd need the JWT secret. The HTTP validation approach means the auth logic stays server-side and works without any secret management, at the cost of one extra HTTP call per request.

---

## 10. Running Locally

```bash
# 1. Make sure Java 17+ and Maven 3.8+ are installed
java -version   # should say 17+
mvn -version    # should say 3.8+

# 2. Edit .env with your Supabase credentials
# SUPABASE_URL and SUPABASE_ANON_KEY are required

# 3. Add your GROQ_API_KEY if you want TaxBuddy AI to work

# 4. Start the server
cd backend-java
mvn spring-boot:run

# Server starts at http://localhost:8080
# Test: curl http://localhost:8080/health
```

---

## 11. How to Add a New Feature

1. **New endpoint in existing resource** → Add method to existing `*Service` + `*Controller`
2. **New Supabase table interaction** → Add method to `SupabaseClient` if needed, then in the service
3. **New API resource** → Create `NewFeatureService.java` + `NewFeatureController.java` following the existing patterns
4. **New request body** → Add a new DTO in `dto/` package with `@JsonProperty` annotations
5. **New env var** → Add to `.env`, `application.yml` with `${VAR_NAME:default}` syntax, inject with `@Value`

---

## 12. Database Tables (Supabase / PostgreSQL)

| Table | Key Fields | Used By |
|-------|-----------|---------|
| `profiles` | user_id, full_name, employment_type, age_group, tax_regime, income_sources, onboarding_completed | ProfileService |
| `documents` | id, user_id, file_name, file_type, file_path, file_size, status, created_at | DocumentService |
| `financial_data` | id, user_id, financial_year, gross_salary, rental_income, interest_income, other_income, business_income, deductions_*, standard_deduction | FinancialService |
| `tax_analyses` | id, user_id, financial_year, old_regime_tax, new_regime_tax, scheme_recommendations | TaxAnalysisService, DashboardService, SchemesService |

All tables have Row Level Security (RLS) enabled — enforced via the user's JWT passed to every Supabase request.

---

## 13. Supabase Edge Functions Used

| Function Name | Triggered By | Purpose |
|--------------|-------------|---------|
| `analyze-document` | POST /api/documents/{id}/analyze | AI document data extraction |
| `tax-analysis` | POST /api/tax-analysis/run, POST /api/schemes/personalized | AI tax calculation + regime comparison + scheme recommendations |
