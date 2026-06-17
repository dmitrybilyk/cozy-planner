#!/bin/bash

# ======================================================================================================================================================
# DEPLOYMENT SCRIPT (linkease) — Git push → SSH → remote build → docker compose up
# Mirrors deploy_to_oracle_cloud.sh's structure. Independent of planner: only touches
# linkease-db/linkease-app containers via docker-compose.linkease.yml.
# ======================================================================================================================================================

START_TIME=$SECONDS

LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

REMOTE_IMAGE_TAG="linkease-server:latest"

cd "$LOCAL_PROJECT_DIR" || { echo "❌ Локальну директорію не знайдено"; exit 1; }

echo "--- 📦 Локальні дії (коміт та пуш) ---"

git add .
if ! git diff-index --quiet HEAD --; then
    git commit -m "improvements"
    echo "✅ Зміни закомічені."
else
    echo "ℹ️ Немає змін для коміту."
fi

echo "📤 Відправка коду на GitHub..."
git push || { echo "❌ Помилка при git push"; exit 1; }

echo ""
echo "--- ☁️ Віддалені дії (Oracle Cloud) ---"

ssh -t $REMOTE_USER@$REMOTE_HOST "
set -e

cd $REMOTE_PROJECT_DIR

echo '📥 Отримання оновлень (git pull)...'
git config --global pull.rebase true
git pull

echo '🐳 Збірка Docker-образу linkease-server на сервері...'
docker build -t $REMOTE_IMAGE_TAG -f linkease/server/Dockerfile linkease

echo '🚀 Запуск контейнерів linkease...'
docker compose -f docker-compose.linkease.yml up -d

echo '🧹 Очищення старих образів...'
docker image prune -f

echo '📋 Очікування запуску додатку...'
docker compose -f docker-compose.linkease.yml logs -f linkease-app | awk '/Started LinkeaseServerApplication/ {print \$0; exit} {print}'

echo '✅ Деплой linkease на сервері завершено!'
"

DURATION=$(( SECONDS - START_TIME ))
MINUTES=$(( DURATION / 60 ))
SECS=$(( DURATION % 60 ))

echo ""
echo "========================================================================================"
echo "✅ Деплой linkease успішно завершено!"
echo "⏱️ Витрачено часу: ${MINUTES}хв ${SECS}с"
echo "========================================================================================"
