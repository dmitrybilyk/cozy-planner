#!/bin/bash

# Starts ngrok TCP tunnel for Kafka and updates KAFKA_BOOTSTRAP_SERVERS
# in /etc/planner/secrets.env on Oracle Cloud.
#
# Usage: ./update_kafka_ngrok.sh

REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"
SECRETS_FILE="/etc/planner/secrets.env"

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

# ── Ensure TCP tunnel is running ───────────────────────────────────────────────
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
        sleep 1
        echo -n "."
        NGROK_URL=$(get_tcp_url) && break
    done
    echo ""
fi

if [ -z "$NGROK_URL" ]; then
    echo "❌ Не вдалося запустити TCP тунель"
    [ -f /tmp/ngrok.log ] && cat /tmp/ngrok.log
    exit 1
fi

echo "✅ Адреса: $NGROK_URL"

# ── Update secrets.env on Oracle Cloud ────────────────────────────────────────
echo "🔄 Оновлення $SECRETS_FILE на Oracle Cloud..."
ssh $REMOTE_USER@$REMOTE_HOST "
  if grep -q '^KAFKA_BOOTSTRAP_SERVERS=' $SECRETS_FILE; then
    sed -i 's|^KAFKA_BOOTSTRAP_SERVERS=.*|KAFKA_BOOTSTRAP_SERVERS=$NGROK_URL|' $SECRETS_FILE
  else
    echo 'KAFKA_BOOTSTRAP_SERVERS=$NGROK_URL' >> $SECRETS_FILE
  fi
  grep KAFKA_BOOTSTRAP_SERVERS $SECRETS_FILE
"

echo "🐳 Перезапуск app на Oracle Cloud..."
ssh $REMOTE_USER@$REMOTE_HOST "cd cozy-planner && docker compose up -d app"

echo "✅ Готово! Oracle Cloud використовує Kafka через $NGROK_URL"
