#!/usr/bin/env bash
# ============================================================
# FinChat 一键销毁脚本
# ============================================================
# 用法:
#   ./scripts/destroy.sh --mode=sandbox          # 清沙箱日志
#   ./scripts/destroy.sh --mode=docker           # 停 Docker 容器 + 删卷
#   ./scripts/destroy.sh --mode=backend-only     # 停后端服务
#   ./scripts/destroy.sh --mode=all              # 全部清理
#   ./scripts/destroy.sh --mode=db               # 只删库
#   -y, --yes                                  # 跳过确认
# ============================================================

set -e
set -o pipefail

# 颜色
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  RED='\033[0;31m'
  GREEN='\033[0;32m'
  YELLOW='\033[1;33m'
  BLUE='\033[0;34m'
  CYAN='\033[0;36m'
  BOLD='\033[1m'
  NC='\033[0m'
else
  RED='' GREEN='' YELLOW='' BLUE='' CYAN='' BOLD='' NC=''
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"
LOG_DIR="$ROOT_DIR/logs"

MODE="docker"
ASSUME_YES=0

print_help() {
  cat <<'EOF'
FinChat 一键销毁

用法:
  ./scripts/destroy.sh --mode=MODE [-y]

模式:
  docker         停 Docker 容器 + 网络 + 卷
  backend-only   停后台 Java 进程
  sandbox        清沙箱日志
  db             删 8 个 fin_* 库
  all            全部清理 (docker + db + logs)

选项:
  -y, --yes      跳过确认
  --no-color     关闭彩色
  -h, --help     帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode=*)   MODE="${1#*=}"; shift ;;
    --mode)     MODE="$2"; shift 2 ;;
    -y|--yes)   ASSUME_YES=1; shift ;;
    --no-color) NO_COLOR=1; shift ;;
    -h|--help)  print_help; exit 0 ;;
    *)          echo -e "${RED}未知参数: $1${NC}"; exit 1 ;;
  esac
done

log()  { echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $*"; }
info() { echo -e "${BLUE}[INFO]${NC}  $*"; }
ok()   { echo -e "${GREEN}[ OK ]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[FAIL]${NC} $*"; }

confirm() {
  if [ "$ASSUME_YES" -eq 1 ]; then return 0; fi
  read -r -p "$1 [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]]
}

banner() {
  cat <<EOF

   🛑 FinChat 销毁
   模式: ${MODE}
   时间: $(date '+%Y-%m-%d %H:%M:%S')
   $(printf '=%.0s' {1..50})

EOF
}

stop_docker() {
  cd "$DEPLOY_DIR"
  if ! command -v docker >/dev/null 2>&1; then
    warn "Docker 未安装, 跳过"
    return
  fi
  if [ ! -f "docker-compose.yml" ]; then
    warn "docker-compose.yml 不存在, 跳过"
    return
  fi
  info "停止 Docker 容器..."
  docker-compose down --remove-orphans 2>/dev/null || docker compose down --remove-orphans 2>/dev/null || true
  ok "Docker 容器已停止"

  if confirm "删除数据卷 (mysql_data, redis_data)?"; then
    docker-compose down -v --remove-orphans 2>/dev/null || true
    ok "数据卷已删除"
  fi
}

stop_backend() {
  info "停止 Java 后台进程..."
  local stopped=0
  for pidfile in "$LOG_DIR"/*.pid; do
    [ -f "$pidfile" ] || continue
    local pid=$(cat "$pidfile" 2>/dev/null)
    local name=$(basename "$pidfile" .pid)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      # 杀子进程
      pkill -P "$pid" 2>/dev/null || true
      ok "  $name (pid=$pid) 已停止"
      stopped=$((stopped + 1))
    fi
    rm -f "$pidfile"
  done
  if [ $stopped -eq 0 ]; then
    info "  无后台进程"
  fi
  # 兜底: 找 mvn spring-boot:run 残留
  pkill -f "spring-boot:run" 2>/dev/null && ok "清理 mvn 残留" || true
}

clean_sandbox() {
  info "清理沙箱日志..."
  if [ -d "$LOG_DIR" ]; then
    rm -f "$LOG_DIR"/*.log "$LOG_DIR"/*.pid
    ok "沙箱日志已清理"
  fi
  # 清理 Maven target (沙箱用不到)
  if confirm "删除所有 target/ 目录?"; then
    find "$ROOT_DIR" -type d -name "target" -not -path "*/node_modules/*" -exec rm -rf {} + 2>/dev/null || true
    ok "target/ 已清理"
  fi
}

drop_db() {
  info "删除 fin_* 数据库..."
  local cmd=""
  if command -v mysql >/dev/null 2>&1; then
    cmd="mysql -h ${DB_HOST:-127.0.0.1} -P ${DB_PORT:-3306} -u${DB_USER:-root} -p${DB_PASS:-root123}"
  else
    warn "mysql 客户端未装, 跳过 (用 docker run mysql 兜底)"
    return
  fi
  for db in fin_auth fin_chat fin_trade fin_compliance fin_kms fin_notify fin_audit fin_observability; do
    if confirm "  删除 $db ?"; then
      $cmd -e "DROP DATABASE IF EXISTS $db;" 2>/dev/null && ok "  $db 已删" || warn "  $db 删除失败"
    fi
  done
}

main() {
  banner
  case "$MODE" in
    docker)
      stop_docker
      ;;
    backend-only)
      stop_backend
      ;;
    sandbox)
      clean_sandbox
      ;;
    db)
      drop_db
      ;;
    all)
      stop_docker
      stop_backend
      drop_db
      clean_sandbox
      ;;
    *)
      err "未知模式: $MODE"
      print_help
      exit 1
      ;;
  esac

  echo ""
  echo -e "${GREEN}════════════════════════════════════════${NC}"
  echo -e "${GREEN}  ✅ 销毁完成 (mode=$MODE)${NC}"
  echo -e "${GREEN}════════════════════════════════════════${NC}"
}

main
