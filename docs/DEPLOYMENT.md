# Deployment

## Development without Docker

Install PostgreSQL 16 and create a dedicated database/user. Apply `database/migrations/001_initial.sql` once. The schema_migrations table records version 1; subsequent migrations must have new version numbers. Never use `EnsureCreated` to evolve production databases.

```powershell
$env:ConnectionStrings__Database = 'Host=localhost;Database=passport;Username=passport;Password=YOUR_LOCAL_PASSWORD'
$env:Firebase__ProjectId = 'YOUR_PROJECT_ID'
$env:GOOGLE_APPLICATION_CREDENTIALS = 'C:\private\firebase-admin.json'
dotnet run --project backend/Passport.Api
```

Configure a local HTTPS endpoint/certificate for mobile session verification. The mobile client refuses cleartext API URLs. Do not weaken certificate validation.

## Docker

Copy `.env.example` to `.env`, fill the values and run `docker compose up --build`. The database is not published to the host network. The API is mapped only to `127.0.0.1:8080`. The service account is mounted as a read-only Docker secret and not built into the image. Use managed identity for hosting environments that support it.

The init migration runs only on a **new** database volume. For an existing database apply each new migration using a controlled migration job after a snapshot; do not delete volumes to upgrade. A failed initial migration should be investigated and rolled back, not ignored. Set database connection limits and backups appropriate to the host.

Terminate TLS in a trusted reverse proxy, restrict access to the internal HTTP port, configure request limits and IP-level throttling at the edge, and forward only headers from trusted proxies. Do not publish Swagger in production. The API runs as the non-root .NET container user. Rotate credentials and keep runtime/container dependencies patched.

Health endpoint is liveness only. Add operational readiness checks for PostgreSQL and Firebase credentials to your hosting setup. Validate restoration from PostgreSQL backups before launch.

The Android app works with no server. Hosting the API does not enable cloud synchronization or document uploads. No website, Firebase project, paid service or deployment was created automatically.
