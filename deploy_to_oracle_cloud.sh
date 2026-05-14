#!/bin/bash

# Налаштування змінних
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
PROJECT_DIR="cozy-planner"

echo "🚀 Починаємо деплой на $REMOTE_HOST..."

# Виконання команд на віддаленому сервері через SSH
ssh -t $REMOTE_USER@$REMOTE_HOST << EOF
  cd $PROJECT_DIR || { echo "❌ Директорію $PROJECT_DIR не знайдено"; exit 1; }

  echo "📥 Оновлюємо код з Git..."
  git pull || { echo "❌ Помилка git pull"; exit 1; }

  echo "🐳 Перезбірка та запуск контейнерів..."
  # Використовуємо --build для оновлення твого Java-додатку
  docker compose up -d --build || { echo "❌ Помилка Docker Compose"; exit 1; }

  echo "✅ Деплой успішно завершено!"
EOF