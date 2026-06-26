#!/bin/bash
# Patches nginx on the Oracle Cloud server to handle SSE (Server-Sent Events)
# without 504 gateway timeout on /api/events

REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"

ssh -t "$REMOTE_USER@$REMOTE_HOST" 'bash -s' << 'ENDSSH'
set -e

echo "=== Finding nginx config for cozy-planner ==="

# Try to find the site config file
NGINX_CONF=""
for f in /etc/nginx/sites-available/cozy-planner.duckdns.org \
          /etc/nginx/sites-available/cozy-planner \
          /etc/nginx/sites-available/linkease \
          /etc/nginx/sites-available/default; do
    if [ -f "$f" ]; then
        NGINX_CONF="$f"
        break
    fi
done

if [ -z "$NGINX_CONF" ]; then
    echo "Checking conf.d..."
    NGINX_CONF=$(ls /etc/nginx/conf.d/*.conf 2>/dev/null | head -1)
fi

if [ -z "$NGINX_CONF" ]; then
    echo "❌ Could not find nginx config. Files in sites-available:"
    ls -la /etc/nginx/sites-available/ 2>/dev/null
    echo "Files in conf.d:"
    ls -la /etc/nginx/conf.d/ 2>/dev/null
    exit 1
fi

echo "✅ Using config: $NGINX_CONF"
echo ""
echo "=== Current proxy_pass lines ==="
grep -n "proxy_pass\|location\|proxy_read_timeout" "$NGINX_CONF" | head -20 || true

if grep -q "location /api/events" "$NGINX_CONF"; then
    echo ""
    echo "⚠️  SSE location block already exists — skipping."
else
    echo ""
    echo "=== Adding SSE location block ==="

    # Create a temp file with the SSE block
    cat > /tmp/sse_block.txt << 'SSEBLOCK'

    # SSE: long-lived connection — disable buffering and extend timeout
    location /api/events {
        proxy_pass http://127.0.0.1:8445;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        chunked_transfer_encoding on;
    }

SSEBLOCK

    # Back up original
    sudo cp "$NGINX_CONF" "${NGINX_CONF}.bak"

    # Insert our block before the first "location / {" (or "location /api")
    # Use awk to insert before first location / line
    sudo awk '
        !inserted && /^[[:space:]]*location[[:space:]]+\// {
            while ((getline line < "/tmp/sse_block.txt") > 0) print line;
            close("/tmp/sse_block.txt");
            inserted = 1;
        }
        { print }
    ' "${NGINX_CONF}.bak" | sudo tee "$NGINX_CONF" > /dev/null

    echo "✅ SSE block inserted."
fi

echo ""
echo "=== Validating nginx config ==="
sudo nginx -t

echo ""
echo "=== Reloading nginx ==="
sudo systemctl reload nginx

echo ""
echo "✅ Done! /api/events will now stay connected up to 1 hour without 504."
ENDSSH
