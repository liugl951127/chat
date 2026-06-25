#!/usr/bin/env bash
#
# 验证 REAL_NAME / RISK_TEST 等新枚举值不再抛 IllegalArgumentException
#
set -e
AUTH_JAR="${AUTH_JAR:-/workspace/fin-chat/fin-auth-center/target/fin-auth-center-1.0.0.jar}"
LOG="/tmp/auth-sms.log"
GREEN='\033[0;32m'; RED='\033[0;31m'; BLUE='\033[0;34m'; NC='\033[0m'
info() { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

pkill -9 -f fin-auth-center 2>/dev/null || true
sleep 2
> $LOG

info "启动 auth-center..."
nohup java -jar "$AUTH_JAR" > $LOG 2>&1 &
PID=$!
trap "kill $PID 2>/dev/null || true" EXIT

for i in {1..30}; do
  if curl -sf -m 1 http://localhost:8081/api/v1/auth/health >/dev/null 2>&1; then
    ok "auth-center ready (${i}s)"
    break
  fi
  sleep 1
  [ $i -eq 30 ] && { tail -20 $LOG; fail "启动超时"; }
done

echo ""
echo "═══ 短信业务枚举值测试 ═══"

# 测 8 种业务类型 (含新增的 REAL_NAME / RISK_TEST / COOLING_OFF_REMIND)
for BIZ in LOGIN REGISTER RESET_PASSWORD BIND_DEVICE TRADE_CONFIRM REAL_NAME RISK_TEST COOLING_OFF_REMIND; do
  CODE=$(curl -s -o /tmp/r.json -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" -H "X-User-Id: 1001" \
    -d "{\"mobile\":\"13800138000\",\"biz\":\"$BIZ\"}" \
    http://localhost:8081/api/v1/auth/sms/send)
  if [ "$CODE" = "200" ]; then
    ok "$BIZ -> 200 (短信已发)"
  else
    warn "$BIZ -> HTTP $CODE"
    cat /tmp/r.json
  fi
  sleep 1  # 避免冷却
done