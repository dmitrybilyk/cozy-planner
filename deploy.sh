#!/bin/bash

# ======================================================================================================================================================
# DEPLOYMENT SCRIPT FOR COZY-PLANNER (IMAGE FROM OCIR)
# ======================================================================================================================================================

# Налаштування змінних
REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_PROJECT_DIR="cozy-planner"

# Дані для авторизації в Oracle Cloud Registry
OCIR_REGISTRY="fra.ocir.io"
OCIR_NAMESPACE="frdhgyiuxpkq"
OCIR_USER="dmitry.bilyk@gmail.com"
# Рекомендується згенерувати Auth Token в OCI та вставити сюди
OCIR_TOKEN="{Dyht2dZ+J3typSgVgDZ"

echo "--- Віддалені дії (Oracle Cloud) ---"

# Виконуємо команди на сервері
ssh -t $REMOTE_USER@$REMOTE_HOST "cd $REMOTE_PROJECT_DIR && \
echo '🔐 Авторизація в Oracle Container Registry...' && \
echo '$OCIR_TOKEN' | docker login $OCIR_REGISTRY -u '$OCIR_NAMESPACE/$OCIR_USER' --password-stdin && \
echo '📥 Стягування нового Docker-образу...' && \
docker compose pull && \
echo '🐳 Перезапуск контейнерів з нового образу...' && \
docker compose up -d && \
echo '🧹 Очищення старих образів...' && \

echo '✅ Деплой успішно завершено!'"