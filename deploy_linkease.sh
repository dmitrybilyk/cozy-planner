#!/bin/bash

# ======================================================================================================================================================
# DEPLOYMENT SCRIPT (linkease) — Build JAR locally → SCP to server → minimal Docker image → compose up
#
# Why local build? The Dockerfile previously ran wasmJsBrowserDistribution (Binaryen wasm-opt)
# inside Docker on the Oracle Cloud VM. That task takes 8+ minutes locally with a warm Gradle
# cache; on the slow server it was ~140 minutes every deploy. Now the fat-JAR is built here
# (fast, cached) and the server just wraps it in a 5-second docker build.
# ======================================================================================================================================================

set -e
START_TIME=$SECONDS

LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
LINKEASE_DIR="$LOCAL_PROJECT_DIR/linkease"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"
REMOTE_DEPLOY_DIR="/home/ubuntu/linkease-deploy"
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
echo "--- 🔨 Локальна збірка (WasmJS + server bootJar) ---"
echo "    (wasmJsBrowserDistribution + wasm-opt запускаються тут, на швидкій локальній машині)"

cd "$LINKEASE_DIR" || { echo "❌ linkease директорію не знайдено"; exit 1; }

./gradlew :server:bootJar -Plinkease.wasmDist=production -x test \
    || { echo "❌ Помилка локальної збірки"; exit 1; }

JAR_PATH=$(find server/build/libs -name "*-SNAPSHOT.jar" | head -1)
[ -z "$JAR_PATH" ] && { echo "❌ JAR-файл не знайдено після збірки"; exit 1; }
echo "✅ JAR зібрано: $JAR_PATH"

echo ""
echo "--- 🚢 Відправка артефакту на сервер ---"

ssh "$REMOTE_USER@$REMOTE_HOST" "mkdir -p $REMOTE_DEPLOY_DIR"
scp "$JAR_PATH" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DEPLOY_DIR/app.jar"
scp "$LINKEASE_DIR/server/Dockerfile.runtime" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DEPLOY_DIR/Dockerfile"
echo "✅ JAR та Dockerfile.runtime відправлено на сервер."

echo ""
echo "--- ☁️ Віддалені дії (Oracle Cloud) ---"

ssh -t "$REMOTE_USER@$REMOTE_HOST" "
set -e

cd $REMOTE_PROJECT_DIR

echo '📥 Оновлення коду (git pull)...'
git config --global pull.rebase true
git pull

echo '🐳 Збірка мінімального Docker-образу (лише копіює JAR, ~5 сек)...'
docker build -t $REMOTE_IMAGE_TAG $REMOTE_DEPLOY_DIR/

echo '🚀 Запуск контейнерів linkease...'
docker compose --env-file /etc/linkease/secrets.env -f docker-compose.linkease.yml up -d

echo '🧹 Очищення старих образів...'
docker image prune -f

echo '📋 Очікування запуску додатку...'
docker compose --env-file /etc/linkease/secrets.env -f docker-compose.linkease.yml logs -f linkease-app | awk '/Started LinkeaseServerApplication/ {print \$0; exit} {print}'

echo '✅ Деплой linkease на сервері завершено!'
"

cd "$LOCAL_PROJECT_DIR"
DURATION=$(( SECONDS - START_TIME ))
MINUTES=$(( DURATION / 60 ))
SECS=$(( DURATION % 60 ))

echo ""
echo "========================================================================================"
echo "✅ Деплой linkease успішно завершено!"
echo "⏱️ Витрачено часу: ${MINUTES}хв ${SECS}с"
echo "========================================================================================"
