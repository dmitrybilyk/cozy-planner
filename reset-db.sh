#!/bin/bash
set -e

COMPOSE_FILE="$(dirname "$0")/docker-compose/docker-compose.db.yml"

echo "Stopping db container..."
docker compose -f "$COMPOSE_FILE" down

echo "Removing volume..."
docker volume rm docker-compose_planner_postgres_data

echo "Starting db container..."
docker compose -f "$COMPOSE_FILE" up -d

echo "Waiting for postgres to be healthy..."
until docker exec planner-db pg_isready -U user -d planner_db > /dev/null 2>&1; do
  sleep 1
done

echo "Done. DB is ready."
