#!/bin/bash
# 端到端 API 验证 (假设服务都跑起来了)
# 用 node 模拟客户端调用

set -e
cd "$(dirname "$0")/.."

echo "════════════════════════════════════════"
echo "  端到端 API 验证"
echo "════════════════════════════════════════"

# 检查必需命令
command -v curl >/dev/null 2>&1 || { echo "需要 curl"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "需要 node"; exit 1; }

GATEWAY_URL=${GATEWAY_URL:-http://localhost:9000}

# 1. 健康检查
echo ""
echo "--- 健康检查 ---"
for path in /api/kms/health; do
    code=$(curl -s -o /tmp/health_resp -w '%{http_code}' "${GATEWAY_URL}${path}" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
        echo "✅ ${path}: 200"
    else
        echo "⚠️  ${path}: ${code} (服务可能未启动)"
    fi
done

# 2. SM3 哈希
echo ""
echo "--- KMS SM3 哈希 ---"
SM3_RESP=$(curl -s -X POST "${GATEWAY_URL}/api/kms/sm3/hash" \
    -H "Content-Type: application/json" \
    -d '{"data":"hello world"}' 2>&1 || echo '{"code":-1}')
echo "响应: $SM3_RESP"

# 3. SM4 加解密
echo ""
echo "--- KMS SM4 加解密 ---"
SM4_RESP=$(curl -s -X POST "${GATEWAY_URL}/api/kms/sm4/encrypt" \
    -H "Content-Type: application/json" \
    -d '{"keyAlias":"test-key","data":"secret message"}' 2>&1 || echo '{"code":-1}')
echo "加密响应: $SM4_RESP"

# 4. 短信下发
echo ""
echo "--- Auth 短信下发 ---"
SMS_RESP=$(curl -s -X POST "${GATEWAY_URL}/api/v1/auth/sms/send" \
    -H "Content-Type: application/json" \
    -d '{"mobile":"13800138000","biz":"LOGIN"}' 2>&1 || echo '{"code":-1}')
echo "响应: $SMS_RESP"

# 5. 风险测评提交
echo ""
echo "--- Compliance 风险测评 ---"
RT_RESP=$(curl -s -X POST "${GATEWAY_URL}/api/v1/compliance/risk-test" \
    -H "Content-Type: application/json" \
    -H "X-User-Id: 1" \
    -d '{"answers":[3,4,3,2,3,4,4,3,4,3]}' 2>&1 || echo '{"code":-1}')
echo "响应: $RT_RESP"

# 6. 审计哈希链验证
echo ""
echo "--- Audit 哈希链验证 ---"
VERIFY_RESP=$(curl -s "${GATEWAY_URL}/api/v1/audit/verify/chain" 2>&1 || echo '{"code":-1}')
echo "响应: $VERIFY_RESP"

echo ""
echo "════════════════════════════════════════"
echo "  验证完成"
echo "════════════════════════════════════════"
