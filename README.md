# DocuVault

Document management with role-based access, full version history and an audit
trail. Angular front end, Spring Boot API, Postgres.

```
docuvault/
├── backend/     Spring Boot 4 · Java 17 · Postgres · Flyway · JWT
├── frontend/    Angular 20 · standalone · zoneless · Tailwind v4
├── docs/        deployment and architecture notes
└── docker-compose.yml   local Postgres
```

## Run it on any machine

**Docker is the only thing you need installed.** No Java, no Node, no Maven.

```bash
git clone <this-repo> docuvault
cd docuvault
docker compose --profile app up -d --build
```

The first build takes a few minutes while it compiles the API and the app
inside the containers. When it finishes, open **http://localhost:8088**.

Register an account — **the first account on a fresh database becomes the
administrator**. Everyone after that is an employee, promoted from
**Profile → People**.

```bash
docker compose --profile app logs -f    # watch it start
docker compose --profile app down       # stop it
docker compose --profile app down -v    # stop it and erase the data
```

## Run it for development

With Java 17+, Maven, Node 22.12+ and Docker:

```bash
# 1. Database only
docker compose up -d

# 2. API  → http://localhost:8080  (docs at /swagger-ui.html)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. App  → http://localhost:4200
cd frontend && npm install && npm start
```

If port 8080 is taken on your machine, run the API elsewhere and point the dev
server at it — the port is not baked into any tracked file:

```bash
cd backend  && PORT=8081 mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && API_URL=http://localhost:8081 npm start
```

The dev server proxies `/api` to the backend, so development is same-origin and
CORS never enters the picture locally.

## How access control works

| | ADMIN | MANAGER | EMPLOYEE |
|---|---|---|---|
| Own documents | ✅ | ✅ | ✅ |
| Everyone's documents | ✅ | ✅ | ❌ |
| Categories | ✅ | ✅ | read only |
| Audit log | ✅ | ✅ | ❌ |
| Manage users and roles | ✅ | ❌ | ❌ |

Two rules are worth stating explicitly:

- **The browser is never trusted.** The cached role in `localStorage` decides
  which menu items render and nothing else. Every request is authorised server
  side from the signed JWT, so editing that value in DevTools changes what the
  user sees and not what they can do.
- **Row-level rules live in the service layer.** URL patterns can express "only
  managers may POST here"; they cannot express "only the owner may open document
  42". That check is in `DocumentService`, not `SecurityConfig`.

## Working with documents

- **Preview before upload.** Choosing or dropping a file shows it immediately —
  images inline, PDFs in the browser's own viewer, the head of text and CSV
  files as text, and name/type/size for everything else. The client applies the
  same allowlist and 25 MB limit the server does, so a file that would be
  rejected is caught before it is sent, not after.
- **Drag and drop**, with the title pre-filled from the filename.
- **Bulk actions.** Tick rows to move them into a category, download them as a
  zip, or delete them together. Only rows the caller may act on are selectable,
  and the API re-checks every id — a bulk request reports per-id failures rather
  than letting one document the caller does not own cancel the rest.
- **Versioning.** Attaching a file to an existing document creates a new
  version; an identical file is refused, because a version with nothing new in
  it is not worth keeping. De-duplication is per document, so two separate
  documents may hold the same file.

## Testing

```bash
cd backend && mvn test      # needs Docker: runs against real Postgres via Testcontainers
cd frontend && npm test
```

The backend suite covers the security rules directly — that a role supplied at
registration is ignored, that an employee cannot read or delete another user's
document, that an identical re-upload is rejected, and that restoring an older
version appends to history rather than rewriting it.

## Configuration

Nothing secret is committed. The API reads everything from the environment and
**refuses to start without a JWT secret** rather than falling back to a default.
See [`backend/.env.example`](backend/.env.example) for the full list.

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Postgres connection |
| `JWT_SECRET` | Token signing key — `openssl rand -base64 48` |
| `CORS_ALLOWED_ORIGINS` | Your front-end origin(s), comma-separated |
| `STORAGE_TYPE` | `local` for development, `s3` in production |
| `S3_*` | Bucket credentials when `STORAGE_TYPE=s3` |

## Deploying

The front end goes to Netlify and the API goes somewhere that can run a JVM —
Netlify serves static files and JS/Go functions, so it cannot host Spring Boot.

See **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)** for the full walkthrough:
Neon for Postgres, Koyeb or Render for the API, Cloudflare R2 for uploads, and
Netlify for the app — all on free tiers.

## Architecture notes

- **Flyway owns the schema.** `ddl-auto` is `validate`, so the database shape is
  reviewable in a pull request and Hibernate only checks that the code agrees
  with it.
- **Files are not on the filesystem.** `StorageService` is a port with a local
  adapter for development and an S3-compatible adapter for production. Free
  hosting tiers have ephemeral disks; anything written to one is gone on the
  next restart.
- **Versions are immutable and de-duplicated.** Each upload is hashed with
  SHA-256 while it streams to storage. A re-upload of an identical file is
  rejected by an indexed checksum lookup, and by a unique constraint underneath
  it so a race cannot slip past. Restoring an older version appends a new
  current pointer; nothing in history is ever rewritten.
- **Errors are RFC 9457 problem+json.** Unmapped exceptions are logged with a
  stack trace and returned as a featureless 500, so no internal detail reaches
  the browser.
