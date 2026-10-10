# Deploying DocuVault on free tiers

Four services, all free, each doing the one thing it is good at.

| Piece | Where | Free tier | Why not Netlify |
|---|---|---|---|
| Angular app | **Netlify** | 100 GB bandwidth/mo | — |
| Spring Boot API | **Koyeb** or **Render** | 1 service, sleeps when idle | Netlify has no JVM |
| Postgres | **Neon** | 0.5 GB, 100 compute-hours/mo | — |
| Uploaded files | **Cloudflare R2** | 10 GB, no egress fees | — |

> **Netlify cannot run Spring Boot.** It serves static files and JavaScript/Go
> serverless functions. There is no JVM, so the API has to live elsewhere. This
> is the single most important thing to understand about this deployment.

---

## 1. Postgres on Neon

1. Sign up at [neon.tech](https://neon.tech) and create a project named `docuvault`.
2. Pick the region closest to wherever you put the API — a database in Virginia
   and an API in Singapore adds ~200 ms to every single query.
3. Copy the connection string and convert it to JDBC form:

   Neon gives you
   ```
   postgresql://docuvault_owner:npg_xxxx@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/docuvault?sslmode=require
   ```
   which becomes
   ```
   DB_URL=jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/docuvault?sslmode=require
   DB_USERNAME=docuvault_owner
   DB_PASSWORD=npg_xxxx
   ```

   Note `jdbc:` on the front, and credentials pulled out into their own
   variables. `sslmode=require` is mandatory — Neon refuses plaintext.

You do not create any tables. Flyway runs `V1__baseline_schema.sql` on first
boot and creates everything.

**Free-tier limits that actually bite:**

- **100 compute-hours/month.** Neon scales to zero after 5 minutes idle, so this
  is plenty for a demo, but a connection pool holding idle connections open
  keeps the compute awake and burns the budget. `DB_POOL_MIN=0` in
  `application.yml` is there for exactly this reason — leave it at 0.
- **0.5 GB storage.** Fine for metadata. This is why uploaded files go to R2
  and not into a `bytea` column.
- **Cold start.** The first query after idle takes ~500 ms while compute wakes.

## 2. File storage on Cloudflare R2

Free hosting has an ephemeral filesystem: anything written to disk is gone on
the next restart or redeploy. Object storage is not optional here.

1. In the Cloudflare dashboard → **R2** → create a bucket, `docuvault-files`.
2. **Manage R2 API Tokens** → create one with Object Read & Write.
3. Note your account ID from the R2 overview page.

```bash
STORAGE_TYPE=s3
S3_BUCKET=docuvault-files
S3_REGION=auto
S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
S3_ACCESS_KEY=<access key id>
S3_SECRET_KEY=<secret access key>
```

Supabase Storage and MinIO work identically — the adapter speaks plain S3, only
the endpoint changes.

## 3. API on Koyeb

Koyeb is the better pick of the two: its free instance does not sleep as
aggressively as Render's, so you avoid the ~50-second cold start on first visit.

1. Push this repository to GitHub.
2. [app.koyeb.com](https://app.koyeb.com) → **Create Service** → GitHub → your repo.
3. Builder: **Dockerfile**. Work directory: `backend`. Dockerfile: `Dockerfile`.
4. Port **8080**, health check path `/actuator/health`.
5. Add the environment variables:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://ep-....neon.tech/docuvault?sslmode=require
DB_USERNAME=docuvault_owner
DB_PASSWORD=...
DB_POOL_MAX=5
DB_POOL_MIN=0
JWT_SECRET=<openssl rand -base64 48>
CORS_ALLOWED_ORIGINS=https://your-site.netlify.app
STORAGE_TYPE=s3
S3_BUCKET=docuvault-files
S3_REGION=auto
S3_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
S3_ACCESS_KEY=...
S3_SECRET_KEY=...
```

Mark `DB_PASSWORD`, `JWT_SECRET` and `S3_SECRET_KEY` as **secrets**, not plain
variables.

Generate the JWT secret with:

```bash
openssl rand -base64 48
```

The app will not start without it, and will reject anything under 32 bytes.
That is deliberate: a missing secret should be a loud failure, not a silent
fallback to a default that everyone with the source code already knows.

**On Render instead:** same settings, but choose *Web Service* → *Docker*, root
directory `backend`. Its free tier sleeps after 15 minutes, and waking takes
30–50 seconds. Tell whoever is reviewing the app to expect that on first load.

## 4. App on Netlify

With a normal (free) Netlify account:

1. Point the front end at your deployed API — edit
   `frontend/src/environments/environment.ts`:

   ```ts
   export const environment = {
     production: true,
     apiBaseUrl: 'https://your-api.koyeb.app/api',
   };
   ```

   Commit that. It is a public URL, not a secret.

2. [app.netlify.com](https://app.netlify.com) → **Add new site** → **Import an
   existing project** → GitHub → your repo.

3. Netlify reads `frontend/netlify.toml`, so the build settings fill themselves
   in. If you are asked, they are:

   | Setting | Value |
   |---|---|
   | Base directory | `frontend` |
   | Build command | `npm ci && npm run build` |
   | Publish directory | `frontend/dist/frontend/browser` |

4. Deploy. You get `https://<random-name>.netlify.app` — rename it under
   **Site configuration → Change site name**.

5. **Go back to the API and set `CORS_ALLOWED_ORIGINS` to that exact URL**, then
   redeploy it. Until you do, every request from the browser fails CORS. This is
   the step people forget.

Or from the terminal:

```bash
cd frontend
npm run build
npx netlify-cli deploy --prod --dir=dist/frontend/browser
```

### Why the redirect rule matters

`netlify.toml` sends every unmatched path to `index.html`:

```toml
[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200
```

Angular is a single-page app — there is no file at `/documents` on disk. Without
this rule the site works while you click around and 404s the moment anyone
refreshes a page or opens a shared deep link.

---

## After the first deploy

1. Open the site and **register**. The first account becomes the administrator.
2. Everyone who registers after that is an employee, promoted from
   **Profile → People**.
3. Check `https://your-api.koyeb.app/swagger-ui.html` for the live API docs.

## Checklist

- [ ] `JWT_SECRET` generated fresh, never committed, marked as a secret
- [ ] `DB_PASSWORD` and `S3_SECRET_KEY` marked as secrets
- [ ] `CORS_ALLOWED_ORIGINS` is the real Netlify URL, with no trailing slash
- [ ] `STORAGE_TYPE=s3` — if it is still `local`, uploads vanish on redeploy
- [ ] `DB_POOL_MIN=0`, so Neon's compute can sleep
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] First account registered and confirmed as ADMIN
