#!/bin/bash

# ======================================================================================================================================================
# REMOTE BUILD DEPLOYMENT — pushes code to git, builds Docker image directly on server (no OCIR needed)
# ======================================================================================================================================================

START_TIME=$SECONDS

LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

cd "$LOCAL_PROJECT_DIR" || { echo "❌ Локальну директорію не знайдено"; exit 1; }

echo "--- 📦 Комміт та пуш коду ---"
git add .
if ! git diff-index --quiet HEAD --; then
    git commit -m "improvements and updated image tracking"
    echo "✅ Зміни закомічено."
else
    echo "ℹ️ Немає змін для коміту."
fi

git push || { echo "❌ Помилка при git push"; exit 1; }

echo "--- ☁️ Збірка та запуск на сервері ---"

ssh -t $REMOTE_USER@$REMOTE_HOST bash << ENDSSH
set -e
cd $REMOTE_PROJECT_DIR

echo '📥 git pull...'
git config --global pull.rebase true
git pull

echo '🐳 Збірка образу на сервері...'
docker build -t cozy-planner-app:latest ./planner

echo '🔄 Перезапуск з новим образом...'
docker compose down app
docker compose up -d app

echo '🧹 Очищення старих образів...'
docker image prune -f

echo '📋 Логи до успішного старту...'
docker compose logs -f app | awk '/Started PlannerApplication/ {print \$0; exit} {print}'

echo '✅ Деплой завершено!'
ENDSSH

DURATION=$(( SECONDS - START_TIME ))
echo "---"
echo "✅ Готово! Час: $(( DURATION / 60 ))хв $(( DURATION % 60 ))с"
