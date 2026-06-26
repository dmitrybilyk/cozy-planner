#!/bin/bash
# Patches nginx on the Oracle Cloud server to handle SSE (Server-Sent Events)
# without 504 gateway timeout on /api/events

REMOTE_USER="ubuntu"
REMOTE_HOST="92.5.42.35"

ssh -t "$REMOTE_USER@$REMOTE_HOST" 'bash -s' << 'ENDSSH'
set -e

echo "=== Finding nginx config for cozy-planner.duckdns.org / linkease ==="
NGINX_CONF=$(nginx -T 2>/dev/null | grep "# configuration file" | grep -E "sites-available|conf\.d" | head -1 | awk '{print $NF}' | tr -d ':')
echo "Detected config: $NGINX_CONF"

if [ -z "$NGINX_CONF" ]; then
    echo "Could not auto-detect config. Listing /etc/nginx/sites-available/:"
    ls -la /etc/nginx/sites-available/ 2>/dev/null || echo "(empty)"
    echo ""
    echo "Please run this script again with NGINX_CONF set, or edit manually."
    exit 1
fi

echo ""
echo "=== Current config excerpt (proxy_pass lines) ==="
grep -n "proxy_pass\|location\|proxy_read_timeout" "$NGINX_CONF" || true

SSE_SNIPPET='
    # SSE: long-lived connection, do not timeout or buffer
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
'

if grep -q "location /api/events" "$NGINX_CONF"; then
    echo ""
    echo "SSE location block already exists in $NGINX_CONF — skipping insertion."
else
    echo ""
    echo "=== Adding SSE location block before first 'location /' ==="
    # Insert the SSE block before the first "location /" line
    sudo python3 - "$NGINX_CONF" "$SSE_SNIPPET" << 'PYEOF'
import sys, re

conf_path = sys.argv[1]
snippet = sys.argv[2]

with open(conf_path, 'r') as f:
    content = f.read()

# Insert before the first "location /" block
pattern = r'(\s*location\s+/\s*\{)'
match = re.search(pattern, content)
if match:
    insert_at = match.start()
    new_content = content[:insert_at] + snippet + '\n' + content[insert_at:]
    with open(conf_path, 'w') as f:
        f.write(new_content)
    print("Inserted SSE block successfully.")
else:
    print("Could not find 'location /' block. Appending to end of server block.")
    # Append before last closing brace in the file
    idx = content.rfind('}')
    new_content = content[:idx] + snippet + '\n' + content[idx:]
    with open(conf_path, 'w') as f:
        f.write(new_content)
PYEOF
fi

echo ""
echo "=== Validating nginx config ==="
sudo nginx -t

echo ""
echo "=== Reloading nginx ==="
sudo systemctl reload nginx

echo ""
echo "✅ nginx SSE fix applied. /api/events will now stay connected up to 1 hour."
ENDSSH
