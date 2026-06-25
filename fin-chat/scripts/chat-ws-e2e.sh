#!/usr/bin/env bash
#
# Fin-Chat WebSocket (STOMP) E2E 测试
#
# 启动 chat-center + 跑 Node 模拟两个用户 WS 通信
#
set -euo pipefail

CHAT_JAR="${CHAT_JAR:-/workspace/fin-chat/fin-chat-center/target/fin-chat-center-1.0.0.jar}"
PROFILE="${PROFILE:-/tmp/chat-center-offline.yml}"
LOG_FILE="${LOG_FILE:-/tmp/chat-ws.log}"

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

pkill -9 -f fin-chat-center 2>/dev/null || true
sleep 2
> "$LOG_FILE"

info "启动 chat-center (no hangup)..."
nohup java -Dspring.config.additional-location="$PROFILE" \
  -jar "$CHAT_JAR" > "$LOG_FILE" 2>&1 &
JAVA_PID=$!
trap 'kill $JAVA_PID 2>/dev/null || true' EXIT

for i in {1..30}; do
  if curl -sf -m 1 http://localhost:8082/api/v1/chat/health > /dev/null 2>&1; then
    ok "chat-center ready (PID=$JAVA_PID, ${i}s)"
    break
  fi
  sleep 1
  [ $i -eq 30 ] && { tail -50 "$LOG_FILE"; fail "启动超时"; }
done

cd "$(dirname "$0")"
info "运行 WS E2E 测试 (USER A + USER B 双向通信)..."
node chat-ws-test.js 2>&1
RC=$?

if [ $RC -eq 0 ]; then
  ok "WS E2E 测试通过 ✅"
else
  fail "WS E2E 测试失败 (rc=$RC)"
fi