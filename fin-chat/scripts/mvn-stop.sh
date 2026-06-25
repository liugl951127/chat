#!/usr/bin/env bash
# ============================================================
# FinChat · 一键停止所有 Maven 启动的 fat jar
# ============================================================
# 用法:
#   ./scripts/mvn-stop.sh              # 停所有
#   ./scripts/mvn-stop.sh fin-auth-center fin-chat-center   # 停指定
#   ./scripts/mvn-stop.sh --force      # 强杀 (SIGKILL)
#   ./scripts/mvn-stop.sh --all        # 包括 spring-boot:run 残留
#   -y                                # 跳过确认
# ============================================================

set -e
set -o pipefail

# 颜色
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
else
  RED='' GREEN='' YELLOW='' BLUE='' CYAN='' BOLD='' NC=''
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_DIR="$LOG_DIR/pids"

FORCE=0
STOP_ALL=0
ASSUME_YES=0
TARGETS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force|-9)    FORCE=1; shift ;;
    --all)         STOP_ALL=1; shift ;;
    -y|--yes)      ASSUME_YES=1; shift ;;
    --no-color)    NO_COLOR=1; shift ;;
    -h|--help)
      cat <<EOF
FinChat · 一键停止

用法:
  ./scripts/mvn-stop.sh              # 停所有
  ./scripts/mvn-stop.sh fin-auth-center fin-chat-center   # 停指定
  ./scripts/mvn-stop.sh --force      # 强杀 (SIGKILL)
  ./scripts/mvn-stop.sh --all        # 包括 spring-boot 残留
  -y                                # 跳过确认
EOF
      exit 0 ;;
    -*)            echo "未知参数: $1"; exit 1 ;;
    *)             TARGETS+=("$1"); shift ;;
  esac
done

log()  { echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $*"; }
info() { echo -e "${BLUE}[INFO]${NC}  $*"; }
ok()   { echo -e "${GREEN}[ OK ]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[FAIL]${NC} $*"; }

stop_one() {
  local name="$1"
  local pidfile="$PID_DIR/$name.pid"

  if [ ! -f "$pidfile" ]; then
    warn "  $name: 无 PID 文件 (可能未启动)"
    return 0
  fi

  local pid=$(cat "$pidfile" 2>/dev/null)
  if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
    info "  $name: 进程不在 (清理 PID 文件)"
    rm -f "$pidfile"
    return 0
  fi

  local sig="TERM"
  [ "$FORCE" -eq 1 ] && sig="KILL"

  info "  停止 $name (pid=$pid, signal=$sig)..."
  kill -$sig "$pid" 2>/dev/null || true

  # 等 5 秒优雅退出
  local waited=0
  while kill -0 "$pid" 2>/dev/null && [ $waited -lt 5 ]; do
    sleep 1
    waited=$((waited + 1))
  done

  if kill -0 "$pid" 2>/dev/null; then
    if [ "$FORCE" -eq 1 ]; then
      err "  $name: SIGKILL 仍未退出, 失败"
      return 1
    else
      warn "  $name: 5s 未退出, 用 SIGKILL 重试"
      kill -9 "$pid" 2>/dev/null || true
      sleep 1
    fi
  fi

  ok "  $name: 已停止"
  rm -f "$pidfile"
  return 0
}

# 兜底: 通过端口找进程
stop_by_port() {
  local name="$1"
  local port="$2"
  local pids=$(lsof -ti :$port 2>/dev/null || true)
  if [ -n "$pids" ]; then
    warn "  $name: 端口 $port 被占 (pid=$pids), 兜底停止"
    if [ "$FORCE" -eq 1 ]; then
      echo "$pids" | xargs kill -9 2>/dev/null || true
    else
      echo "$pids" | xargs kill 2>/dev/null || true
      sleep 3
      # 还活着就强杀
      pids=$(lsof -ti :$port 2>/dev/null || true)
      [ -n "$pids" ] && echo "$pids" | xargs kill -9 2>/dev/null || true
    fi
    ok "  $name: 端口已释放"
  fi
}

main() {
  echo ""
  echo "════════════════════════════════════════"
  echo "  🛑 FinChat 停止"
  echo "════════════════════════════════════════"
  echo ""

  # 模式 1: 指定 targets
  if [ ${#TARGETS[@]} -gt 0 ]; then
    for t in "${TARGETS[@]}"; do
      stop_one "$t"
    done
  else
    # 模式 2: 停所有 PID 文件里的
    if [ -d "$PID_DIR" ]; then
      local count=$(ls "$PID_DIR"/*.pid 2>/dev/null | wc -l)
      if [ "$count" -gt 0 ]; then
        for pidfile in "$PID_DIR"/*.pid; do
          [ -f "$pidfile" ] || continue
          local name=$(basename "$pidfile" .pid)
          stop_one "$name"
        done
      else
        info "无 PID 文件记录"
      fi
    fi
  fi

  # --all: 兜底找 spring-boot-loader 残留
  if [ "$STOP_ALL" -eq 1 ]; then
    info "查找 spring-boot 残留进程..."
    local leftovers=$(pgrep -f "spring-boot-loader" 2>/dev/null || true)
    if [ -n "$leftovers" ]; then
      warn "发现残留: $leftovers"
      if [ "$FORCE" -eq 1 ]; then
        echo "$leftovers" | xargs kill -9 2>/dev/null || true
      else
        echo "$leftovers" | xargs kill 2>/dev/null || true
        sleep 2
      fi
      ok "残留已清理"
    else
      info "无残留"
    fi
  fi

  # 验证端口释放
  echo ""
  info "端口验证:"
  local ports=(9000 8081 8082 8083 8090 8091 8092 8093 8094)
  for p in "${ports[@]}"; do
    if lsof -i :$p >/dev/null 2>&1; then
      warn "  :$p 仍被占用"
    else
      ok "  :$p 已释放"
    fi
  done

  echo ""
  echo "════════════════════════════════════════"
  echo -e "${GREEN}  ✅ 停止完成${NC}"
  echo "════════════════════════════════════════"
  echo ""
  echo "  重新启动: ./scripts/mvn-deploy.sh --skip-build"
  echo "  全清: ./scripts/mvn-deploy.sh --clean"
  echo ""
}

main
