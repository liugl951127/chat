#!/usr/bin/env bash
#
# Fin-Chat 前后端聊天 E2E 测试脚本
#
# 流程:
#   1. 启动 chat-center (内存模式, 无 MySQL/Redis 依赖)
#   2. 模拟前端 HTTP 客户端: 创建会话 → 发消息 → 拉历史 → 验哈希链
#   3. 检查所有 REST API + 哈希链正确性
#
# Usage:
#   ./scripts/chat-e2e-test.sh
#
set -euo pipefail

CHAT_JAR="${CHAT_JAR:-/workspace/fin-chat/fin-chat-center/target/fin-chat-center-1.0.0.jar}"
PROFILE="${PROFILE:-/tmp/chat-center-offline.yml}"
BASE_URL="${BASE_URL:-http://localhost:8082}"
USER_ID=1001

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

# 0. 清理旧进程
pkill -f fin-chat-center 2>/dev/null || true
sleep 1

# 1. 启动 chat-center
if [ ! -f "$CHAT_JAR" ]; then
  fail "找不到 jar: $CHAT_JAR, 请先 mvn package"
fi

if [ ! -f "$PROFILE" ]; then
  cat > "$PROFILE" <<'YAML'
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
      - org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
      - org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration
fin:
  storage:
    enabled: false
logging:
  level:
    com.fin: DEBUG
YAML
fi

info "启动 chat-center (内存模式)..."
nohup java -Dspring.config.additional-location="$PROFILE" \
  -jar "$CHAT_JAR" > /tmp/chat-e2e.log 2>&1 &
PID=$!

# 等启动
for i in {1..30}; do
  if curl -sf -m 1 "$BASE_URL/api/v1/chat/health" >/dev/null 2>&1; then
    ok "chat-center 启动 (PID=$PID, 耗时 ${i}s)"
    break
  fi
  sleep 1
  [ $i -eq 30 ] && { tail -50 /tmp/chat-e2e.log; fail "启动超时"; }
done

# 清理 trap
trap 'pkill -f fin-chat-center 2>/dev/null || true' EXIT

# 2. E2E 测试
echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   Fin-Chat 前后端聊天 E2E 测试                ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

info "1) 健康检查"
curl -sf "$BASE_URL/api/v1/chat/health" | python3 -m json.tool | head -5

info "2) 创建会话"
CONV=$(curl -sf -X POST -H "Content-Type: application/json" -H "X-User-Id: $USER_ID" \
  -d '{"title":"E2E 测试会话"}' "$BASE_URL/api/v1/chat/conversations" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
ok "会话 ID: $CONV"

info "3) 发送消息 #1"
H1=$(curl -sf -X POST -H "Content-Type: application/json" -H "X-User-Id: $USER_ID" \
  -d "{\"conversationId\":\"$CONV\",\"content\":\"你好, 前端客户 A\"}" \
  "$BASE_URL/api/v1/chat/messages" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['contentHash'])")
ok "msg#1 hash=${H1:0:16}..."

info "4) 发送消息 #2 (应 prevHash = H1)"
R2=$(curl -sf -X POST -H "Content-Type: application/json" -H "X-User-Id: $USER_ID" \
  -d "{\"conversationId\":\"$CONV\",\"content\":\"第二条消息\"}" \
  "$BASE_URL/api/v1/chat/messages")
H2=$(echo "$R2" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['contentHash'])")
P2=$(echo "$R2" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['prevHash'])")
[ "$P2" = "$H1" ] && ok "msg#2 prevHash ✓ == msg#1 hash" || fail "msg#2 prevHash 不匹配!"
ok "msg#2 hash=${H2:0:16}..."

info "5) 发送消息 #3"
curl -sf -X POST -H "Content-Type: application/json" -H "X-User-Id: $USER_ID" \
  -d "{\"conversationId\":\"$CONV\",\"content\":\"第三条\"}" \
  "$BASE_URL/api/v1/chat/messages" >/dev/null
ok "msg#3 已发送"

info "6) 拉取历史消息 (倒序)"
curl -sf -H "X-User-Id: $USER_ID" "$BASE_URL/api/v1/chat/messages/$CONV?size=10" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'   total: {d[\"total\"]}')
for m in d['records']:
    print(f'   - [{m[\"senderType\"]}] {m[\"content\"]}  hash={m[\"contentHash\"][:12]}')"

info "7) 验证哈希链"
VR=$(curl -sf -H "X-User-Id: $USER_ID" "$BASE_URL/api/v1/chat/verify/$CONV")
echo "$VR" | python3 -m json.tool
VALID=$(echo "$VR" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['valid'])")
[ "$VALID" = "True" ] && ok "哈希链 valid: True ✅" || fail "哈希链 invalid!"

echo ""
echo -e "${GREEN}════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ E2E 测试全部通过!${NC}"
echo -e "${GREEN}════════════════════════════════════${NC}"
echo ""
echo "现在你可以:"
echo "  1) 浏览器打开 demo: fin-web/dist/demo/chat-demo.html"
echo "  2) 通过 Gateway: curl http://localhost:9000/api/v1/chat/..."
echo "  3) WebSocket: ws://localhost:8082/ws/chat (STOMP)"