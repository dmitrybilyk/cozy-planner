#!/bin/bash

# ======================================================================================================================================================
# FULL AUTOMATED DEPLOYMENT SCRIPT FOR COZY-PLANNER (LOCAL BUILD -> OCIR PUSH -> REMOTE RESTART via docker-compose.yml)
# ======================================================================================================================================================

# Налаштування змінних
LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

# Дані для Oracle Cloud Infrastructure Registry (OCIR)
OCIR_REGISTRY="fra.ocir.io"
OCIR_NAMESPACE="frdhgyiuxpkq"
OCIR_USER="dmitry.bilyk@gmail.com"
OCIR_IMAGE_TAG="fra.ocir.io/frdhgyiuxpkq/cozy-planner:latest"

echo "--- 🛠️ Локальні дії (Збірка та Пуш образу) ---"

# Перехід в локальну директорію проекту
cd "$LOCAL_PROJECT_DIR" || { echo "❌ Локальну директорію не знайдено"; exit 1; }

# Зчитування токену з файлу 'token'
if [ -f "token" ]; then
    OCIR_TOKEN=$(cat token | tr -d '\r\n ')
else
    echo "❌ Файл 'token' не знайдено в корені проекту!"
    exit 1
fi

echo "🐳 Перехід в папку planner та збірка Docker-образу..."
# Заходимо в папку модуля, щоб контекст збірки збігався з розташуванням файлів Gradle
cd planner || { echo "❌ Папку planner не знайдено"; exit 1; }

docker build -t cozy-planner-app:latest . || { echo "❌ Помилка при збірці Docker-образу"; exit 1; }

# Повертаємося в корінь проекту для Git та логіну
cd ..

echo "🔐 Авторизація в Oracle Container Registry..."
echo "$OCIR_TOKEN" | docker login $OCIR_REGISTRY -u "$OCIR_NAMESPACE/$OCIR_USER" --password-stdin || { echo "❌ Помилка авторизації в OCIR"; exit 1; }

echo "🏷️ Тегування образу..."
docker tag cozy-planner-app:latest $OCIR_IMAGE_TAG

echo "📤 Пуш образу в Oracle Cloud Registry (OCIR)..."
docker push $OCIR_IMAGE_TAG || { echo "❌ Помилка при пуші образу в OCIR"; exit 1; }

echo "--- 📦 Робота з Git ---"
echo "📦 Індексація та коміт змін..."
git add .
git commit -m "improvements and updated image tracking"

echo "📤 Відправка коду в Git-репозиторій..."
git push || { echo "❌ Помилка при git push"; exit 1; }

echo "--- ☁️ Віддалені дії (Oracle Cloud) ---"

# Підключаємось по SSH, робимо pull оновленого docker-compose.yml та перезапускаємо сервіс з нового образу
ssh -t $REMOTE_USER@$REMOTE_HOST "cd $REMOTE_PROJECT_DIR && \
echo '📥 Отримання оновленого docker-compose.yml (git pull)...' && \
git pull && \
echo '🔐 Авторизація в OCIR на сервері...' && \
echo '$OCIR_TOKEN' | docker login $OCIR_REGISTRY -u '$OCIR_NAMESPACE/$OCIR_USER' --password-stdin && \
echo '📥 Стягування нового образу на сервері...' && \
docker compose pull && \
echo '🐳 Перезапуск контейнерів...' && \
docker compose up -d && \
echo '🧹 Очищення старих образів...' && \
docker image prune -f && \
echo '✅ Деплой успішно завершено!'"