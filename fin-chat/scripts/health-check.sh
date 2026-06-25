#!/usr/bin/env bash
# ============================================================
# FinChat 健康检查脚本
# ============================================================
# 用法:
#   ./scripts/health-check.sh              # 全量检查
#   ./scripts/health-check.sh --url URL    # 自定义 URL
#   ./scripts/health-check.sh --watch      # 持续监控 (10s 一次)
#   ./scripts/health-check.sh --json       # JSON 输出
# ============================================================

set -e

# 颜色
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
else
  RED='' GREEN='' YELLOW='' BLUE='' CYAN='' BOLD='' NC=''
fi

GATEWAY_URL="${GATEWAY_URL:-http://localhost:9000}"

# 模块清单: name|path|port
SERVICES=(
  "KMS|${GATEWAY_URL%/}/api/kms/health|8091"
  "AUTH|${GATEWAY_URL%/}/api/v1/auth/health|8081"
  "CHAT|${GATEWAY_URL%/}/api/v1/chat/health|8082"
  "TRADE|${GATEWAY_URL%/}/api/v1/trade/health|8083"
  "COMPLIANCE|${GATEWAY_URL%/}/api/v1/compliance/health|8090"
  "NOTIFY|${GATEWAY_URL%/}/api/v1/notify/health|8092"
  "AUDIT|${GATEWAY_URL%/}/api/v1/audit/health|8093"
  "OBSERVABILITY|${GATEWAY_URL%/}/api/v1/observability/health|8094"
)

WATCH=0
JSON=0
CUSTOM_URL=""

print_help() {
  cat <<'EOF'
FinChat 健康检查

用法:
  ./scripts/health-check.sh [选项]

选项:
  --url URL       自定义健康 URL
  --watch         持续监控 (10s 一次, Ctrl+C 退出)
  --json          JSON 输出
  --no-color      关闭彩色
  -h, --help      帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --url)        CUSTOM_URL="$2"; shift 2 ;;
    --watch)      WATCH=1; shift ;;
    --json)       JSON=1; shift ;;
    --no-color)   NO_COLOR=1; shift ;;
    -h|--help)    print_help; exit 0 ;;
    *)            echo "未知参数: $1"; exit 1 ;;
  esac
done

check_one() {
  local name="$1"
  local url="$2"
  local port="$3"
  local start=$(date +%s%3N)
  local code=$(curl -s -o /tmp/health.$$ -w "%{http_code}" --max-time 3 "$url" 2>/dev/null || echo "000")
  local cost=$(( $(date +%s%3N) - start ))
  local body=$(cat /tmp/health.$$ 2>/dev/null || echo "")

  if [ "$code" = "200" ]; then
    local msg=$(echo "$body" | head -c 200)
    if [ "$JSON" -eq 1 ]; then
      echo "{\"name\":\"$name\",\"url\":\"$url\",\"port\":$port,\"code\":$code,\"latency_ms\":$cost,\"ok\":true}"
    else
      echo -e "${GREEN}✅ $name${NC}  port=$port  ${cost}ms  HTTP $code"
      [ -n "$msg" ] && echo "    $msg" | head -c 200
    fi
    rm -f /tmp/health.$$
    return 0
  else
    if [ "$JSON" -eq 1 ]; then
      echo "{\"name\":\"$name\",\"url\":\"$url\",\"port\":$port,\"code\":$code,\"latency_ms\":$cost,\"ok\":false}"
    else
      echo -e "${RED}❌ $name${NC}  port=$port  ${cost}ms  HTTP $code"
    fi
    rm -f /tmp/health.$$
    return 1
  fi
}

check_all() {
  local total=${#SERVICES[@]}
  local pass=0

  if [ "$JSON" -eq 0 ]; then
    echo ""
    echo "════════════════════════════════════════"
    echo "  FinChat 健康检查 ($(date '+%Y-%m-%d %H:%M:%S'))"
    echo "════════════════════════════════════════"
  fi

  for svc in "${SERVICES[@]}"; do
    IFS='|' read -r name url port <<< "$svc"
    if check_one "$name" "$url" "$port"; then
      pass=$((pass + 1))
    fi
  done

  if [ "$JSON" -eq 0 ]; then
    echo ""
    echo "────────────────────────────────────────"
    if [ $pass -eq $total ]; then
      echo -e "${GREEN}  ✅ 全部健康: $pass/$total${NC}"
    else
      echo -e "${YELLOW}  ⚠ 部分异常: $pass/$total${NC}"
    fi
    echo "════════════════════════════════════════"
  else
    echo "{\"summary\":{\"pass\":$pass,\"total\":$total,\"all_ok\":$([ $pass -eq $total ] && echo true || echo false)}}"
  fi
}

if [ -n "$CUSTOM_URL" ]; then
  check_one "CUSTOM" "$CUSTOM_URL" "?"
else
  if [ "$WATCH" -eq 1 ]; then
    while true; do
      check_all
      sleep 10
    done
  else
    check_all
  fi
fi
