#!/bin/bash

# Starts ngrok TCP tunnel for Kafka, updates local docker-compose/.env
# (so kafka-prod compose picks up the right advertised listener automatically),
# updates Oracle Cloud .env, and restarts the remote app.
#
# Usage:
#   ./update_kafka_ngrok.sh                          # auto-start ngrok
#   ./update_kafka_ngrok.sh 4.tcp.eu.ngrok.io:20308  # use existing address

REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
REMOTE_ENV="/home/ubuntu/cozy-planner/.env"
LOCAL_COMPOSE_ENV="$(dirname "$0")/docker-compose/.env"

get_tcp_url() {
    for port in 4040 4041; do
        URL=$(curl -s http://localhost:$port/api/tunnels 2>/dev/null \
          | python3 -c "
import sys, json
tunnels = json.load(sys.stdin).get('tunnels', [])
tcp = next((t for t in tunnels if t.get('proto') == 'tcp'), None)
print(tcp['public_url'].replace('tcp://', '')) if tcp else sys.exit(1)
" 2>/dev/null)
        [ -n "$URL" ] && echo "$URL" && return 0
    done
    return 1
}

# ── Get or start ngrok TCP tunnel ─────────────────────────────────────────────
if [ -n "$1" ]; then
    NGROK_URL="$1"
    echo "📌 Використовуємо передану адресу: $NGROK_URL"
else
    NGROK_URL=$(get_tcp_url)

    if [ -n "$NGROK_URL" ]; then
        echo "ℹ️  TCP тунель вже активний: $NGROK_URL"
    elif curl -s http://localhost:4040/api/tunnels >/dev/null 2>&1 || curl -s http://localhost:4041/api/tunnels >/dev/null 2>&1; then
        echo "🔌 Додаємо TCP тунель до запущеного ngrok агента..."
        NGROK_API_PORT=$(curl -s http://localhost:4040/api/tunnels >/dev/null 2>&1 && echo 4040 || echo 4041)
        curl -s -X POST http://localhost:$NGROK_API_PORT/api/tunnels \
          -H "Content-Type: application/json" \
          -d '{"name":"kafka","proto":"tcp","addr":9093}' >/dev/null
        sleep 2
        NGROK_URL=$(get_tcp_url)
    else
        echo "🚇 Запуск ngrok tcp 9093..."
        ngrok tcp 9093 > /tmp/ngrok.log 2>&1 &
        echo -n "⏳ Очікування на адресу..."
        for i in $(seq 1 20); do
            sleep 1; echo -n "."
            NGROK_URL=$(get_tcp_url) && break
        done
        echo ""
    fi
fi

if [ -z "$NGROK_URL" ]; then
    echo "❌ Не вдалося запустити TCP тунель"
    [ -f /tmp/ngrok.log ] && cat /tmp/ngrok.log
    exit 1
fi

NGROK_HOST=$(echo "$NGROK_URL" | cut -d: -f1)
NGROK_PORT=$(echo "$NGROK_URL" | cut -d: -f2)
echo "✅ Адреса: $NGROK_URL"

# ── Update local docker-compose/.env (auto-loaded by kafka-prod compose) ──────
echo "📝 Оновлення локального $LOCAL_COMPOSE_ENV..."
cat > "$LOCAL_COMPOSE_ENV" <<EOF
NGROK_KAFKA_HOST=$NGROK_HOST
NGROK_KAFKA_PORT=$NGROK_PORT
EOF
echo "   → NGROK_KAFKA_HOST=$NGROK_HOST"
echo "   → NGROK_KAFKA_PORT=$NGROK_PORT"

# ── Restart local Kafka with new advertised listener ──────────────────────────
echo "🔄 Перезапуск локального Kafka..."
docker compose -f "$(dirname "$0")/docker-compose/docker-compose.kafka-prod.yml" \
  -p docker-compose up -d kafka

# ── Update Oracle Cloud .env and restart app ──────────────────────────────────
echo "🔄 Оновлення Oracle Cloud .env..."
ssh $REMOTE_USER@$REMOTE_HOST "
  cp /etc/planner/secrets.env $REMOTE_ENV
  if grep -q '^KAFKA_BOOTSTRAP_SERVERS=' $REMOTE_ENV; then
    sed -i 's|^KAFKA_BOOTSTRAP_SERVERS=.*|KAFKA_BOOTSTRAP_SERVERS=$NGROK_URL|' $REMOTE_ENV
  else
    echo 'KAFKA_BOOTSTRAP_SERVERS=$NGROK_URL' >> $REMOTE_ENV
  fi
  grep KAFKA_BOOTSTRAP_SERVERS $REMOTE_ENV
"

echo "🐳 Перезапуск app на Oracle Cloud..."
ssh $REMOTE_USER@$REMOTE_HOST "cd cozy-planner && docker compose up -d --force-recreate app"

echo ""
echo "✅ Готово!"
echo "   ngrok:        $NGROK_URL"
echo "   Local Kafka:  EXTERNAL listener оновлено"
echo "   Oracle Cloud: використовує Kafka через $NGROK_URL"
