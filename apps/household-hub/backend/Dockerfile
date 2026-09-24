FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    SQLITE_DB_PATH=/data/household_hub.db

WORKDIR /app

# Install runtime dependencies for health checks and network calls
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source
COPY app/ app/

# Startup migrates the database, so the migrations ship with the app
COPY alembic.ini .
COPY alembic/ alembic/

# Create data directory for SQLite persistence
RUN mkdir -p /data

# `hub reset-pin "Emma"` from inside the container: the last resort for a forgotten PIN.
RUN printf '#!/bin/sh
exec python -m app.cli "$@"
' > /usr/local/bin/hub && chmod +x /usr/local/bin/hub

EXPOSE 3050

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:3050/api/v1/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "3050"]
