#!/bin/bash

# ======================================================================================================================================================
# FAST DEPLOYMENT SCRIPT (linkease) — reuses existing WASM/JS frontend, only rebuilds server JAR.
#
# Use this when ONLY server-side files changed:
#   ✅ server/src/main/kotlin/**          (controllers, repos, services, DTOs, config)
#   ✅ server/src/main/resources/**       (application.yaml, Flyway migrations, static files like manifest.json/sw.js/icons)
#
# Use the FULL deploy_linkease.sh instead when any of these changed:
#   ❌ composeApp/src/wasmJsMain/kotlin/** (web frontend Kotlin code)
#   ❌ composeApp/src/commonMain/kotlin/**  (shared models, repositories, business logic)
#   ❌ composeApp/src/wasmJsMain/resources/index.html (the html template itself)
#
# Requires: a previous successful build (linkease/composeApp/build/dist/wasmJs/productionExecutable/ must exist).
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

WASM_DIR="$LINKEASE_DIR/composeApp/build/dist/wasmJs/productionExecutable"
if [ ! -d "$WASM_DIR" ]; then
    echo "❌ WASM build output not found at $WASM_DIR"
    echo "   Run the full deploy_linkease.sh first to produce the frontend build."
    exit 1
fi

cd "$LOCAL_PROJECT_DIR" || { echo "❌ Project directory not found"; exit 1; }

echo "--- 📦 Commit & push ---"
git add .
if ! git diff-index --quiet HEAD --; then
    git commit -m "improvements"
    echo "✅ Changes committed."
else
    echo "ℹ️  Nothing to commit."
fi
git push || { echo "❌ git push failed"; exit 1; }

echo ""
echo "--- 🔨 Server-only build (skipping WASM recompile) ---"
cd "$LINKEASE_DIR" || { echo "❌ linkease directory not found"; exit 1; }

./gradlew :server:bootJar -Plinkease.wasmDist=production -Plinkease.skipWasm=true -x test \
    || { echo "❌ Build failed"; exit 1; }

JAR_PATH=$(find server/build/libs -name "*-SNAPSHOT.jar" | head -1)
[ -z "$JAR_PATH" ] && { echo "❌ JAR not found after build"; exit 1; }
echo "✅ JAR built: $JAR_PATH"

echo ""
echo "--- 🚢 Uploading to server ---"
ssh "$REMOTE_USER@$REMOTE_HOST" "mkdir -p $REMOTE_DEPLOY_DIR"
scp "$JAR_PATH" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DEPLOY_DIR/app.jar"
scp "$LINKEASE_DIR/server/Dockerfile.runtime" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DEPLOY_DIR/Dockerfile"
echo "✅ JAR and Dockerfile uploaded."

echo ""
echo "--- ☁️  Remote actions (Oracle Cloud) ---"
ssh -t "$REMOTE_USER@$REMOTE_HOST" "
set -e
cd $REMOTE_PROJECT_DIR
echo '📥 git pull...'
git config --global pull.rebase true
git pull
echo '🐳 Building minimal Docker image (~5 sec)...'
docker build -t $REMOTE_IMAGE_TAG $REMOTE_DEPLOY_DIR/
echo '🚀 Starting linkease containers...'
docker compose --env-file /etc/linkease/secrets.env -f docker-compose.linkease.yml up -d
echo '🧹 Pruning old images...'
docker image prune -f
echo '📋 Waiting for app to start...'
docker compose --env-file /etc/linkease/secrets.env -f docker-compose.linkease.yml logs -f linkease-app | awk '/Started LinkeaseServerApplication/ {print \$0; exit} {print}'
echo '✅ Fast deploy complete!'
"

cd "$LOCAL_PROJECT_DIR"
DURATION=$(( SECONDS - START_TIME ))
echo ""
echo "========================================================================================"
echo "✅ Fast deploy complete!"
echo "⏱️  Total time: $(( DURATION / 60 ))m $(( DURATION % 60 ))s"
echo "========================================================================================"
