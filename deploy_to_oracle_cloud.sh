#!/bin/bash

# ======================================================================================================================================================
# DEPLOYMENT SCRIPT FOR COZY-PLANNER (FIXED SSH TERMINATION)
# ======================================================================================================================================================

# Налаштування змінних
LOCAL_PROJECT_DIR="/home/dmytro/dev/projects/cozy-planner"
#LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

echo "--- Локальні дії ---"

# Перехід в локальну директорію проекту
cd "$LOCAL_PROJECT_DIR" || { echo "❌ Локальну директорію не знайдено"; exit 1; }

# Додавання змін, коміт та пуш
echo "📦 Індексація та коміт змін..."
git add .
git commit -m "improvements"

echo "📤 Відправка коду в репозиторій (git push)..."
git push || { echo "❌ Помилка при git push"; exit 1; }

echo "--- Віддалені дії (Oracle Cloud) ---"

# Використовуємо звичайний запуск команд одним рядком замість Heredoc (<< EOF).
# Прапорець -t залишаємо, щоб бачити кольоровий вивід логів Docker, але команди розділяємо через логічне '&&'.
# Це змусить SSH закрити сесію одразу після завершення останньої команди docker compose.

ssh -t $REMOTE_USER@$REMOTE_HOST "cd $REMOTE_PROJECT_DIR && \
echo '📥 Отримання оновлень (git pull)...' && \
git pull && \
echo '🔑 Оновлення .env із secrets.env...' && \
cp secrets.env .env && \
echo '🐳 Перезбірка Docker-образів та запуск...' && \
docker compose up -d --build && \
echo '🧹 Очищення старих образів...' && \
docker image prune -f && \
echo '✅ Деплой успішно завершено!'"

# Тепер локальний термінал віддасть тобі керування назад, як тільки сервер закінчить роботу.