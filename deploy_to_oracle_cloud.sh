#!/bin/bash

# ==============================================================================
# DEPLOYMENT SCRIPT FOR COZY-PLANNER
# ==============================================================================

# Налаштування змінних
LOCAL_PROJECT_DIR="/home/dik81/IdeaProjects/cozy-planner"
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

# Встановлення ширини виводу для зручності копіювання
# ------------------------------------------------------------------------------

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

# Виконання команд на сервері через SSH
ssh -t $REMOTE_USER@$REMOTE_HOST << EOF
  cd $REMOTE_PROJECT_DIR || { echo "❌ Директорію на сервері не знайдено"; exit 1; }

  echo "📥 Отримання оновлень (git pull)..."
  git pull || { echo "❌ Помилка git pull на сервері"; exit 1; }

  echo "🐳 Перезбірка Docker-образів та запуск..."
  # Оскільки ми використовуємо Gradle з Kotlin DSL всередині Docker,
  # параметр --build забезпечить перекомпіляцію Java/Kotlin коду.
  docker compose up -d --build || { echo "❌ Помилка Docker Compose"; exit 1; }

  # Очищення старих образів для економії місця на 1GB інстансі
  docker image prune -f

  echo "✅ Деплой успішно завершено!"
EOF