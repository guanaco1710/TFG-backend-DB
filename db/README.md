# Database Setup

PostgreSQL 16 database setup for the TFG Backend.

## Files

- **Dockerfile** — PostgreSQL 16 Alpine image with initialization scripts
- **init-scripts/** — SQL scripts executed on container startup
  - `01-init.sql` — Basic schema initialization and permissions setup

## Usage

### Build and run with Docker Compose

From the project root:

```bash
# Build the image and start the container
docker-compose up -d

# View logs
docker-compose logs -f postgres

# Stop the container
docker-compose down

# Clean up volumes
docker-compose down -v
```

### Connection details

- **Host:** localhost
- **Port:** 5432
- **Database:** tfg_db
- **Username:** tfg_user
- **Password:** tfg_password

### Build the image directly

```bash
docker build -t tfg-postgres:latest .
```

### Run the image directly

```bash
docker run -d \
  --name tfg-postgres \
  -e POSTGRES_DB=tfg_db \
  -e POSTGRES_USER=tfg_user \
  -e POSTGRES_PASSWORD=tfg_password \
  -p 5432:5432 \
  -v postgres_data:/var/lib/postgresql/data \
  tfg-postgres:latest
```

## Flyway Migrations

Database schema migrations are managed by Flyway. Place migration scripts in `src/main/resources/db/migration/` with the naming convention `V<number>__<description>.sql`.

Example:
```
V1__Initial_schema.sql
V2__Create_user_table.sql
V3__Add_booking_table.sql
```

When the application starts, Flyway will automatically execute pending migrations.

## Development Workflow

1. Start the database: `docker-compose up -d`
2. Run the Spring Boot application: `./mvnw spring-boot:run`
3. The application will automatically run Flyway migrations on startup
4. Make schema changes via Flyway migration files, not direct SQL
