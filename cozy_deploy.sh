#!/bin/bash

# ======================================================================================================================================================
# FULL AUTOMATED DEPLOYMENT SCRIPT WITH TIMER & LOG MONITORING (LOCAL BUILD -> OCIR PUSH -> REMOTE RESTART)
# ======================================================================================================================================================

# Запуск таймера
START_TIME=$SECONDS

# Налаштування змінних
LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
#LOCAL_PROJECT_DIR="/home/dmytro/dev/projects/cozy-planner"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

# Дані для Oracle Cloud Infrastructure Registry (OCIR)
OCIR_REGISTRY="fra.ocir.io"
OCIR_NAMESPACE="frdhgyiuxpkq"
OCIR_USER="dmitry.bilyk@gmail.com"
OCIR_IMAGE_TAG="fra.ocir.io/frdhgyiuxpkq/cozy-planner:latest"

echo "--- 🛠️ Локальні дії (Комміт, Збірка та Пуш образу) ---"

# Перехід в локальну директорію проекту
cd "$LOCAL_PROJECT_DIR" || { echo "❌ Локальну директорію не знайдено"; exit 1; }

# Зчитування токену з файлу 'token'
if [ -f "token" ]; then
    OCIR_TOKEN=$(cat token | tr -d '\r\n ')
else
    echo "❌ Файл 'token' не знайдено в корені проекту!"
    exit 1
fi

echo "📦 Індексація та коміт локальних змін перед збіркою..."
git add .
# Перевіряємо, чи є взагалі зміни для коміту, щоб скрипт не падав, якщо нічого не змінилося
if ! git diff-index --quiet HEAD --; then
    git commit -m "improvements and updated image tracking"
    echo "✅ Зміни успішно закомічені локально."
else
    echo "ℹ️ Немає локальних змін для коміту."
fi

echo "🐳 Перехід в папку planner та збірка Docker-образу..."
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

echo "--- 📦 Робота з Git (Відправка на GitHub) ---"
echo "📤 Відправка коду в Git-репозиторій..."
git push || { echo "❌ Помилка при git push"; exit 1; }

echo "--- ☁️ Віддалені дії (Oracle Cloud) ---"

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
echo '📋 Моніторинг логів до успішного старту додатку...' && \
docker compose logs -f app | awk '/Started PlannerApplication/ {print \$0; exit} {print}' && \
echo '✅ Деплой на сервері завершено!'"

# Розрахунок витраченого часу
DURATION=$(( SECONDS - START_TIME ))
MINUTES=$(( DURATION / 60 ))
SECONDS_LEFT=$(( DURATION % 60 ))

echo "--------------------------------------------------------------------------------------------------------------------------------------------------------"
echo "✅ Загальний деплой успішно завершено!"
echo "⏱️ Витрачено часу: ${MINUTES}хв ${SECONDS_LEFT}с"
echo "--------------------------------------------------------------------------------------------------------------------------------------------------------"