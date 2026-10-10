# Running without Docker

Three things to install, then two commands. Everything below assumes a fresh
machine that has never seen this project.

| | Version | Why |
|---|---|---|
| **JDK** | 17 or newer | Runs the API |
| **Node.js** | 22.12 or newer | Builds and serves the app |
| **PostgreSQL** | 14 or newer | The database |

Maven is **not** needed — the repository carries a Maven wrapper (`./mvnw`)
that fetches the right version by itself.

---

## 1. Install the three

### macOS

```bash
brew install openjdk@21 node postgresql@17
brew services start postgresql@17
```

Homebrew does not put its JDK on the path automatically:

```bash
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
             /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

### Windows

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install OpenJS.NodeJS.LTS
winget install PostgreSQL.PostgreSQL.17
```

The PostgreSQL installer asks for a password for the `postgres` superuser —
write it down, step 2 needs it. Close and reopen the terminal afterwards so the
new `PATH` applies.

### Linux (Debian / Ubuntu)

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk postgresql
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
sudo systemctl start postgresql
```

### Check all three

```bash
java -version     # 17 or higher
node -v           # v22.12 or higher
psql --version    # 14 or higher
```

---

## 2. Create the database

The application expects a database, user and password all called `docuvault`.
Those are the defaults in `application-dev.yml`; step 4 shows how to use
different ones.

**macOS / Linux**

```bash
psql -d postgres -c "CREATE USER docuvault WITH PASSWORD 'docuvault';"
psql -d postgres -c "CREATE DATABASE docuvault OWNER docuvault;"
```

**Windows** (in SQL Shell / psql, logged in as `postgres`)

```sql
CREATE USER docuvault WITH PASSWORD 'docuvault';
CREATE DATABASE docuvault OWNER docuvault;
```

You do not create any tables. Flyway builds the schema on first start.

Verify the connection works:

```bash
psql "postgresql://docuvault:docuvault@localhost:5432/docuvault" -c "select 1;"
```

---

## 3. Start it

Two terminals, left open while you use the app.

**Terminal 1 — the API** (http://localhost:8080)

```bash
cd docuvault/backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

The first run downloads Maven and the dependencies, so give it a few minutes.
It is ready when the log says `Started DocuVaultApplication`.

**Terminal 2 — the app** (http://localhost:4200)

```bash
cd docuvault/frontend
npm install
npm start
```

Open **http://localhost:4200** and register. **The first account on a fresh
database becomes the administrator**; everyone after is an employee, promoted
from Profile → People.

The dev server proxies `/api` to the backend, so both halves look like one
origin to the browser and CORS never comes into it.

---

## 4. When something is already using a port

Port 8080 in particular is popular. Run the API somewhere else and point the
dev server at it — neither value is baked into a tracked file:

```bash
# Terminal 1
PORT=8081 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2
API_URL=http://localhost:8081 npm start
```

On Windows PowerShell:

```powershell
$env:PORT=8081; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
$env:API_URL="http://localhost:8081"; npm start
```

Same idea for the database if yours is elsewhere, or the credentials differ:

```bash
DB_URL="jdbc:postgresql://localhost:5432/mydb?sslmode=disable" \
DB_USERNAME=myuser DB_PASSWORD=mypass \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Alternative: skip the local database entirely

If installing PostgreSQL is the annoying part, point the API at a free
[Neon](https://neon.tech) database instead. Nothing to install, and it works
from any machine with internet:

```bash
DB_URL="jdbc:postgresql://ep-xxxx.region.aws.neon.tech/docuvault?sslmode=require" \
DB_USERNAME=docuvault_owner \
DB_PASSWORD=your-neon-password \
JWT_SECRET="$(openssl rand -base64 48)" \
./mvnw spring-boot:run
```

Note `sslmode=require` — Neon refuses plaintext connections, unlike a local
PostgreSQL. See [DEPLOYMENT.md](DEPLOYMENT.md) for getting the connection
string.

---

## Alternative: one jar, no Node at runtime

To hand someone a build rather than a checkout, produce a jar and a static
bundle once:

```bash
cd backend  && ./mvnw clean package          # -> target/docuvault-backend-1.0.0.jar
cd ../frontend && npm ci && npm run build    # -> dist/frontend/browser
```

Then they need only a JDK and the database:

```bash
java -jar docuvault-backend-1.0.0.jar
```

The front end in `dist/frontend/browser` is plain static files — serve them
with anything (`npx serve`, nginx, Apache, Netlify). Two things to get right
if you do: unmatched paths must fall back to `index.html` or a refresh on
`/documents` returns 404, and the API must list that origin in
`CORS_ALLOWED_ORIGINS`, since serving the files separately means the browser is
no longer on one origin.

---

## Troubleshooting

**`Web server failed to start. Port 8080 was already in use.`**
Something else owns the port. Use `PORT=8081` as above, or find the culprit
with `lsof -nP -iTCP:8080 -sTCP:LISTEN` (macOS/Linux) or
`netstat -ano | findstr :8080` (Windows).

**`Connection to localhost:5432 refused`**
PostgreSQL is not running. `brew services start postgresql@17`,
`sudo systemctl start postgresql`, or start the service from the Windows
Services panel.

**`password authentication failed for user "docuvault"`**
Step 2 did not complete. Re-run the two `CREATE` statements.

**`The connection attempt failed` with an `EOFException`**
The JDBC driver is trying SSL against a server that does not offer it. The dev
profile already appends `?sslmode=disable`; if you overrode `DB_URL`, add it.

**`docuvault.jwt.secret must be at least 32 bytes`**
Only happens outside the `dev` profile. Supply one:
`JWT_SECRET="$(openssl rand -base64 48)"`. The application refuses to start
without a real signing key rather than falling back to a default.

**The app loads but every request fails**
The API is not running, or it is on a different port than the dev server
expects. Check Terminal 1, then set `API_URL` to match.

Beware of one trap while diagnosing this: when the proxy target is down, the
dev server falls through to the single-page app and answers `/api/...` with
`index.html` and a **200**, which looks like success. Check the content type,
not the status code:

```bash
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' \
  -X POST http://localhost:4200/api/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"x@y.z","password":"zzzzz"}'
```

`401 application/problem+json` means the API answered. `200 text/html` means it
did not, and you are looking at the app's own index page.
