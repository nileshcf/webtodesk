# wait-for-port.sh

**Path:** `.github/scripts/wait-for-port.sh`

> Make executable after creating: `chmod +x .github/scripts/wait-for-port.sh`

```bash
#!/usr/bin/env bash
# wait-for-port.sh <port> <timeout_seconds>
# Mirrors Wait-PortReady in start-all.ps1

PORT="$1"
TIMEOUT="${2:-60}"
ELAPSED=0

echo "Waiting for port $PORT (timeout ${TIMEOUT}s)..."

while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
  if nc -z localhost "$PORT" 2>/dev/null; then
    echo "[OK] Port $PORT is open after ${ELAPSED}s"
    exit 0
  fi
  sleep 2
  ELAPSED=$((ELAPSED + 2))
  if [ $((ELAPSED % 15)) -eq 0 ]; then
    echo "  Still waiting... (${ELAPSED}s)"
  fi
done

echo "[FAIL] Port $PORT did not open within ${TIMEOUT}s"
exit 1
```
