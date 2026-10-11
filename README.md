# DocuVault

DocuVault is a document management application with role-based access
control, document version history, and an audit trail.

**Technology stack** - **Frontend:** Angular 20, standalone components,
zoneless change detection, Tailwind CSS v4 - **Backend:** Spring Boot 4,
Java 17+ - **Database:** PostgreSQL 14+ - **Database migrations:**
Flyway - **Authentication:** JWT - **File storage:** Local storage for
development, with an S3-compatible option for production

``` text
docuvault/
├── backend/       Spring Boot API, Java, Flyway, JWT
├── frontend/      Angular application
├── docs/          Deployment and architecture notes
└── docker-compose.yml
```

This guide explains how to run DocuVault **without Docker**. You will
install Java, Node.js, and PostgreSQL directly on your computer.

## 1. Requirements

Install the following software:

  Software     Version          Purpose
  ------------ ---------------- --------------------------
  Java JDK     17 or newer      Runs the Spring Boot API
  Node.js      22.12 or newer   Runs Angular and npm
  PostgreSQL   14 or newer      Stores application data

You do **not** need to install Maven separately. The repository includes
a Maven Wrapper (`mvnw` / `mvnw.cmd`).

### Windows

You can install the required tools with `winget` in PowerShell:

``` powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install OpenJS.NodeJS.LTS
winget install PostgreSQL.PostgreSQL.17
```

During PostgreSQL installation, set a password for the `postgres`
database administrator and keep it available for the next step. Close
and reopen the terminal after installation so updated PATH settings take
effect.

Downloads: - [Java JDK](https://adoptium.net/) -
[Node.js](https://nodejs.org/) - [PostgreSQL for
Windows](https://www.postgresql.org/download/windows/)

### macOS

With Homebrew:

``` bash
brew install openjdk@21 node postgresql@17
brew services start postgresql@17
```

If needed, follow Homebrew's instructions to make the JDK available on
your PATH.

### Linux (Debian/Ubuntu)

``` bash
sudo apt update
sudo apt install -y openjdk-21-jdk postgresql
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt install -y nodejs
sudo systemctl start postgresql
```

### Verify installations

Open a new terminal and run:

``` bash
java -version
node -v
npm -v
psql --version
```

Confirm that Java is 17 or newer, Node.js is 22.12 or newer, and
PostgreSQL is 14 or newer. If `psql` is not recognized, PostgreSQL may
still be installed; its command-line tools might simply not be on your
PATH.

## 2. Create the PostgreSQL database

The development configuration expects a PostgreSQL database and user
named `docuvault`, with password `docuvault`. You can use different
credentials, but the backend environment/configuration must match them.

> The credentials below are convenient for local development only. Do
> not use these example credentials in production.

### Windows

Open **SQL Shell (psql)** from the Start menu. Connect as the `postgres`
user, then run these statements separately:

``` sql
CREATE USER docuvault WITH PASSWORD 'docuvault';
CREATE DATABASE docuvault OWNER docuvault;
```

If you get a message that the user or database already exists, do not
create it again; verify the existing configuration instead.

### macOS / Linux

Depending on your PostgreSQL setup, connect as an administrator and run:

``` bash
psql -d postgres -c "CREATE USER docuvault WITH PASSWORD 'docuvault';"
psql -d postgres -c "CREATE DATABASE docuvault OWNER docuvault;"
```

### Verify the connection

``` bash
psql "postgresql://docuvault:docuvault@localhost:5432/docuvault" -c "select 1;"
```

If the command succeeds, the local database connection works. You do not
need to create application tables manually: Flyway is responsible for
applying the schema migrations when the backend starts.

## 3. Get the project

If you have not downloaded the repository yet, clone it and enter the
project folder:

``` bash
git clone <this-repo> docuvault
cd docuvault
```

Replace `<this-repo>` with the actual repository URL. If you already
have the project on your computer, open a terminal in that existing
`docuvault` folder.

## 4. Start the backend API

Open **Terminal 1** in the project folder.

### Windows Command Prompt or PowerShell

``` powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### macOS / Linux

``` bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The first run downloads Maven and the required dependencies, so it may
take a few minutes. Keep this terminal open.

The API should be available at:

-   API base URL: `http://localhost:8080`
-   Swagger UI, if enabled: `http://localhost:8080/swagger-ui.html`

Wait until the application log indicates that `DocuVaultApplication` has
started successfully. If startup fails, check the database settings and
the troubleshooting section below.

## 5. Start the Angular frontend

Open **Terminal 2** in the project folder. Leave the backend running in
Terminal 1.

``` bash
cd frontend
npm install
npm start
```

When Angular has started, open:

**http://localhost:4200**

Register an account. On a fresh database, the first account becomes the
administrator. Subsequent accounts are employees; an administrator can
promote users from **Profile → People**.

The Angular development server proxies `/api` requests to the backend.
This keeps local development on the same browser origin, so a separate
CORS setup is not normally needed for this development workflow.

## 6. Configure ports or database credentials

### If port 8080 is already in use

Start the backend on port 8081.

**Windows PowerShell --- backend terminal:**

``` powershell
$env:PORT = "8081"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Windows PowerShell --- frontend terminal:**

``` powershell
$env:API_URL = "http://localhost:8081"
npm start
```

**macOS / Linux --- backend terminal:**

``` bash
PORT=8081 ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**macOS / Linux --- frontend terminal:**

``` bash
API_URL=http://localhost:8081 npm start
```

### If your database uses different credentials

Set the database environment variables before starting the backend.

**Windows PowerShell:**

``` powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/docuvault?sslmode=disable"
$env:DB_USERNAME = "docuvault"
$env:DB_PASSWORD = "your-database-password"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**macOS / Linux:**

``` bash
DB_URL="jdbc:postgresql://localhost:5432/docuvault?sslmode=disable" \
DB_USERNAME="docuvault" \
DB_PASSWORD="your-database-password" \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Use the values that match your PostgreSQL setup and the project's
configuration.

## 7. How access control works

  Capability                      ADMIN   MANAGER    EMPLOYEE
  ----------------------------- ------- --------- -----------
  Access own documents              Yes       Yes         Yes
  Access everyone's documents       Yes       Yes          No
  Manage categories                 Yes       Yes   Read only
  View audit log                    Yes       Yes          No
  Manage users and roles            Yes        No          No

Two important security rules:

-   **The browser is not trusted.** The role cached in `localStorage`
    controls which menu items appear, but it does not grant permissions.
    The backend authorizes requests using the signed JWT.
-   **Document ownership is checked on the server.** Role-level endpoint
    rules are not enough to determine whether a user may access a
    specific document. Ownership checks are handled in the service
    layer, including `DocumentService`.

## 8. Working with documents

-   **Preview before upload:** Selected or dropped files can be
    previewed before upload. Images display inline, PDFs use the browser
    viewer, text and CSV files show their beginning, and other files
    show their name, type, and size.
-   **File validation:** The client applies the configured file-type
    allowlist and 25 MB size limit. The server must also validate
    uploads independently.
-   **Drag and drop:** Drop a file into the upload area; the title is
    pre-filled from its filename.
-   **Bulk actions:** Select eligible rows to change category, download
    documents as a ZIP, or delete documents. The API re-checks
    permissions for each document ID and can report individual failures.
-   **Version history:** Uploading a new file to an existing document
    creates a new version. An identical file is rejected for that
    document. Separate documents can contain the same file.
-   **Restore a version:** Restoring an older version appends a new
    current version reference instead of rewriting historical version
    records.

## 9. Testing

### Frontend tests

``` bash
cd frontend
npm test
```

Run the frontend test command configured by the project.

### Backend tests

``` bash
cd backend
./mvnw test
```

On Windows, use `.\mvnw.cmd test` from the `backend` directory.

**Important:** The repository's backend test suite uses Testcontainers
to run tests against a real PostgreSQL instance. Testcontainers normally
requires a container runtime such as Docker. Therefore, the application
can be run without Docker, but the existing backend test suite may still
require Docker unless the tests or test configuration are changed to use
a locally installed PostgreSQL database. The frontend and backend
runtime instructions above do not require Docker.

The backend tests cover security and document-version rules, including
ignoring a role supplied during registration, preventing employees from
reading or deleting another user's documents, rejecting identical
re-uploads, and preserving history when an older version is restored.

## 10. Configuration and secrets

The API reads configuration from environment variables. See
[`backend/.env.example`](backend/.env.example) for the project's
complete configuration list.

  Variable                 Purpose
  ------------------------ ------------------------------------------------
  `DB_URL`                 PostgreSQL JDBC connection URL
  `DB_USERNAME`            PostgreSQL username
  `DB_PASSWORD`            PostgreSQL password
  `JWT_SECRET`             Secret used to sign JWTs
  `CORS_ALLOWED_ORIGINS`   Comma-separated allowed frontend origins
  `STORAGE_TYPE`           `local` for development or `s3` for production
  `S3_*`                   S3-compatible storage settings when using S3

The application refuses to start without a sufficiently strong JWT
secret when running outside a development configuration. Generate a
secret securely, for example with `openssl rand -base64 48`, and provide
it through the environment. Never commit real credentials or secrets to
the repository.

## 11. File storage and architecture

-   **Flyway owns the schema.** Hibernate's `ddl-auto` setting is
    `validate`, so Hibernate checks that the schema matches the entities
    rather than creating or changing the schema automatically.
-   **Storage is abstracted.** `StorageService` provides a storage
    interface, with a local adapter for development and an S3-compatible
    adapter for production.
-   **Versions are immutable and de-duplicated.** Uploads are hashed
    with SHA-256 while streaming. An indexed checksum lookup and a
    database uniqueness constraint help reject duplicate versions of the
    same document.
-   **Errors use RFC 9457 Problem Details.** Unexpected errors are
    logged on the server and returned without exposing internal stack
    traces to the browser.

## 12. Troubleshooting

### `Web server failed to start. Port 8080 was already in use.`

Another process is using port 8080. Use the alternate-port instructions
in Section 6.

On Windows:

``` powershell
netstat -ano | findstr :8080
```

### `Connection to localhost:5432 refused`

PostgreSQL is not running, or it is listening on a different port. Start
the PostgreSQL service from the Windows Services panel, or use the
appropriate service command for macOS/Linux.

### `password authentication failed for user "docuvault"`

Check that the PostgreSQL user and password match `DB_USERNAME` and
`DB_PASSWORD`. If you use the example setup, verify that the `docuvault`
user was created successfully.

### The backend cannot connect to PostgreSQL over SSL

For a local PostgreSQL server that does not use SSL, the development
JDBC URL can include `?sslmode=disable`. Do not use this setting for a
hosted database that requires SSL.

### `docuvault.jwt.secret must be at least 32 bytes`

Provide a sufficiently long `JWT_SECRET` in the environment when running
outside the development profile. Do not replace it with a hard-coded
production default.

### The app loads but API requests fail

Confirm that the backend is running and that the Angular proxy points to
the correct API port. If the proxy target is unavailable, a request can
sometimes receive the frontend's `index.html` instead of an API
response. Check the response content type as well as the HTTP status.

## 13. Deploying

For deployment, the frontend can be hosted as static files on a service
such as Netlify, while the Spring Boot API needs a host that can run a
JVM. PostgreSQL can be hosted separately, and uploaded files can use
S3-compatible storage.

See [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for the deployment
walkthrough and supported configuration options.
