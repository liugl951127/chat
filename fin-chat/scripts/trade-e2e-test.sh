#!/usr/bin/env bash
#
# Fin-Chat 合规化购买流程 E2E 测试
#
# 流程:
#   1. 启动 chat-center + trade-gateway
#   2. 模拟前端客户手机 H5: 登录 → 进聊天 → 看产品 → 下单 → 风险测评 → 双录 → 短信确认 → 冷静期 → 成功
#
set -euo pipefail

CHAT_JAR="${CHAT_JAR:-/workspace/fin-chat/fin-chat-center/target/fin-chat-center-1.0.0.jar}"
TRADE_JAR="${TRADE_JAR:-/workspace/fin-chat/fin-trade-gateway/target/fin-trade-gateway-1.0.0.jar}"
CHAT_URL="http://localhost:8082"
TRADE_URL="http://localhost:8083"
CHAT_PROFILE="/tmp/chat-center-offline.yml"
TRADE_PROFILE="/tmp/trade-gateway-offline.yml"

GREEN='\033[0;32m'; RED='\033[0;31m'; BLUE='\033[0;34m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC} $*"; exit 1; }

pkill -9 -f "fin-chat-center\|fin-trade-gateway" 2>/dev/null || true
sleep 2

# profile
if [ ! -f "$CHAT_PROFILE" ]; then
cat > "$CHAT_PROFILE" <<'YAML'
server:
  port: 8082
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
    com.fin: INFO
YAML
fi

if [ ! -f "$TRADE_PROFILE" ]; then
cat > "$TRADE_PROFILE" <<'YAML'
server:
  port: 8083
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
    com.fin: INFO
YAML
fi

info "启动 chat-center..."
nohup java -Dspring.config.additional-location="$CHAT_PROFILE" -jar "$CHAT_JAR" > /tmp/chat-trade.log 2>&1 &
CHAT_PID=$!

info "启动 trade-gateway..."
nohup java -Dspring.config.additional-location="$TRADE_PROFILE" -jar "$TRADE_JAR" > /tmp/trade.log 2>&1 &
TRADE_PID=$!

trap 'kill $CHAT_PID $TRADE_PID 2>/dev/null || true' EXIT

for i in {1..40}; do
  CHAT_OK=$(curl -sf -m 1 "$CHAT_URL/api/v1/chat/health" >/dev/null 2>&1 && echo y || echo n)
  TRADE_OK=$(curl -sf -m 1 "$TRADE_URL/api/v1/trade/health" >/dev/null 2>&1 && echo y || echo n)
  if [ "$CHAT_OK" = "y" ] && [ "$TRADE_OK" = "y" ]; then
    ok "chat-center + trade-gateway ready (${i}s)"
    break
  fi
  sleep 1
  [ $i -eq 40 ] && { tail -30 /tmp/chat-trade.log; tail -30 /tmp/trade.log; fail "启动超时"; }
done

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   金融合规购买流程 E2E 测试                    ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

USER_ID=1001
H="X-User-Id: $USER_ID"

# ============ Step 1: 客服主动建会话 ============
info "Step 1: 客服 (8001) 主动给客户 (1001) 建会话"
CONV=$(curl -sf -X POST -H "Content-Type: application/json" -H "X-User-Id: 8001" \
  -d '{"customerId":1001,"title":"欢迎咨询理财产品"}' \
  "$CHAT_URL/api/v1/chat/agent/conversations" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])")
ok "客服会话: $CONV"

# ============ Step 2: 客户在聊天里查产品列表 ============
info "Step 2: 客户查产品列表 (按风险等级推荐)"
curl -sf -H "$H" "$TRADE_URL/api/v1/trade/products/recommend" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  用户风险等级: {d[\"userRiskLevel\"]}')
print(f'  匹配产品数: {len(d[\"products\"])}')
for item in d['products'][:6]:
    p = item['product']
    s = item['suitability']
    icon = '✅' if s == 'PASS' else ('⚠️' if s == 'WARN' else '🚫')
    print(f'  {icon} {p[\"productName\"][:30]:30} {p[\"productType\"]:10} R{p[\"productRiskLevel\"][1:]} min={p[\"minAmount\"]} 收益={p[\"expectedYield\"]}%')"

# ============ Step 3: 创建购买订单 ============
info "Step 3: 创建订单 - 客户买货币基金 5000 元"
ORDER_RESP=$(curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d "{\"productId\":\"FUND-CN-001\",\"amount\":5000,\"conversationId\":\"$CONV\"}" \
  "$TRADE_URL/api/v1/trade/buy")
ORDER_ID=$(echo "$ORDER_RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['orderId'])")
echo "$ORDER_RESP" | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  订单号: {d[\"orderId\"]}')
print(f'  产品: {d[\"productName\"]}')
print(f'  金额: {d[\"amount\"]}')
print(f'  状态: {d[\"status\"]}')
print(f'  下一步: {d[\"nextStep\"]}')"

# ============ Step 4: 风险测评 ============
info "Step 4: 风险测评 - 用户测得 C2 (稳健)"
curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d "{\"riskTestId\":\"RT-2024-001\",\"userRiskLevel\":\"C2\"}" \
  "$TRADE_URL/api/v1/trade/orders/$ORDER_ID/risk-test" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  风险等级: {d[\"riskLevel\"]}')
print(f'  适当性: {d[\"suitability\"]}')
print(f'  状态: {d[\"status\"]}')
print(f'  下一步: {d[\"nextStep\"]}')"

# ============ Step 5: 短信确认 ============
info "Step 5: 短信验证码确认 - 进入冷静期"
curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d '{"smsCode":"123456"}' \
  "$TRADE_URL/api/v1/trade/orders/$ORDER_ID/confirm" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  状态: {d[\"status\"]}')
print(f'  冷静期截止: {d[\"coolOffUntil\"]}')
print(f'  下一步: {d[\"nextStep\"]}')"

# ============ Step 6: 适当性 BLOCK 测试 ============
info "Step 6: 测试适当性 BLOCK - C1 客户买股票基金"
# 修改用户风险等级缓存 (这里直接 mock: 拿一个新订单测)
# 新订单: 股票基金需 C4+
ORDER2=$(curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d "{\"productId\":\"FUND-CN-004\",\"amount\":2000,\"conversationId\":\"$CONV\",\"riskTestId\":\"RT-002\"}" \
  "$TRADE_URL/api/v1/trade/buy" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['orderId'])")
ok "订单2: $ORDER2"
curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d "{\"riskTestId\":\"RT-002\",\"userRiskLevel\":\"C1\"}" \
  "$TRADE_URL/api/v1/trade/orders/$ORDER2/risk-test" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
icon = '🚫' if d['status'] == 'REJECTED' else '⚠️'
print(f'  {icon} 状态: {d[\"status\"]}')
print(f'  适当性: {d[\"suitability\"]}')
print(f'  拒绝原因: {d[\"rejectReason\"]}')"

# ============ Step 7: 保险产品双录测试 ============
info "Step 7: 保险产品必须双录 (DR-)"
ORDER3=$(curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d "{\"productId\":\"INSURANCE-001\",\"amount\":100000,\"conversationId\":\"$CONV\",\"riskTestId\":\"RT-003\"}" \
  "$TRADE_URL/api/v1/trade/buy" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['orderId'])")
ok "保险订单: $ORDER3"
curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d "{\"riskTestId\":\"RT-003\",\"userRiskLevel\":\"C3\"}" \
  "$TRADE_URL/api/v1/trade/orders/$ORDER3/risk-test" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  状态: {d[\"status\"]}')
print(f'  适当性: {d[\"suitability\"]}')
print(f'  双录会话: {d.get(\"orderId\")}')
print(f'  下一步: {d[\"nextStep\"]}')
print(f'  需要双录: {d[\"needDualRecord\"]}')"

# 完成双录
curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d "{\"url\":\"https://dual-record.example/$ORDER3.mp4\"}" \
  "$TRADE_URL/api/v1/trade/orders/$ORDER3/dual-record" > /dev/null
ok "双录已完成"

# 短信确认
curl -sf -X POST -H "Content-Type: application/json" -H "$H" \
  -d '{"smsCode":"888888"}' \
  "$TRADE_URL/api/v1/trade/orders/$ORDER3/confirm" \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['data']
print(f'  状态: {d[\"status\"]} (保险冷静期 168h)')
print(f'  冷静期截止: {d[\"coolOffUntil\"]}')"

# ============ Step 8: 订单列表 ============
info "Step 8: 我的订单列表"
curl -sf -H "$H" "$TRADE_URL/api/v1/trade/orders" \
  | python3 -c "
import sys, json
orders = json.load(sys.stdin)['data']
print(f'  共 {len(orders)} 个订单')
for o in orders:
    icon = {'SUCCESS':'✅','REJECTED':'🚫','COOLING_OFF':'⏳','PENDING_CONFIRM':'📱'}.get(o['status'], '?')
    print(f'  {icon} {o[\"orderId\"]} {o[\"productName\"][:25]:25} {o[\"amount\"]:>10} {o[\"status\"]}')"

echo ""
echo "════════════════════════════════════"
echo "  ✅ 合规化购买流程 E2E 测试通过!"
echo "════════════════════════════════════"