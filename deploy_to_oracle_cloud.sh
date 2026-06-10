#!/bin/bash

# ======================================================================================================================================================
# DEPLOYMENT SCRIPT — Git push → SSH → remote build → docker compose up
# ======================================================================================================================================================

START_TIME=$SECONDS

LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
#LOCAL_PROJECT_DIR="/home/dmytro/dev/projects/cozy-planner"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

# Image tag must match what docker-compose.yml expects so compose uses the locally-built image without pulling from OCIR
REMOTE_IMAGE_TAG="fra.ocir.io/frdhgyiuxpkq/cozy-planner:latest"

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

echo '🐳 Збірка Docker-образу на сервері...'
docker build -t $REMOTE_IMAGE_TAG ./planner

echo '🚀 Запуск контейнерів...'
docker compose up -d

echo '🧹 Очищення старих образів...'
docker image prune -f

echo '📋 Очікування запуску додатку...'
docker compose logs -f app | awk '/Started PlannerApplication/ {print \$0; exit} {print}'

echo '✅ Деплой на сервері завершено!'
"

DURATION=$(( SECONDS - START_TIME ))
MINUTES=$(( DURATION / 60 ))
SECS=$(( DURATION % 60 ))

echo ""
echo "========================================================================================"
echo "✅ Деплой успішно завершено!"
echo "⏱️ Витрачено часу: ${MINUTES}хв ${SECS}с"
echo "========================================================================================"
