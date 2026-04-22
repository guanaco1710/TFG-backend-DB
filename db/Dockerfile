FROM postgres:16-alpine

ENV POSTGRES_DB=GymBook_DB
ENV POSTGRES_USER=tfg_user
ENV POSTGRES_PASSWORD=tfg_password

COPY init-scripts/ /docker-entrypoint-initdb.d/

EXPOSE 5432

HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD pg_isready -U $POSTGRES_USER -d $POSTGRES_DB
