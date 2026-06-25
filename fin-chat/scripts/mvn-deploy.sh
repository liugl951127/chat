#!/usr/bin/env bash
# ============================================================
# FinChat · Maven 打包编译一键部署
# ============================================================
# 核心流程: 静态检查 → mvn package → 启 9 个 fat jar
#
# 用法:
#   ./scripts/mvn-deploy.sh                # 全量 (静态检查 + 打包 + 启动)
#   ./scripts/mvn-deploy.sh --skip-checks  # 跳过静态检查
#   ./scripts/mvn-deploy.sh --skip-build   # 跳过打包 (用现有 jar)
#   ./scripts/mvn-deploy.sh --build-only   # 只打包不启动
#   ./scripts/mvn-deploy.sh --clean        # 清理 target + 旧 jar
#   ./scripts/mvn-deploy.sh --module=fin-auth-center   # 只部署指定模块
#   ./scripts/mvn-deploy.sh --profile=dev  # Spring profile (默认 dev)
#   ./scripts/mvn-deploy.sh -y             # 跳过确认
#
# 依赖:
#   - JDK 17+
#   - Maven 3.8+
#   - Redis (可选, 端口 6379)
#   - MySQL 8 (可选, 端口 3306, 用 H2 则不需要)
# ============================================================

set -e
set -o pipefail

# ============== 颜色 ==============
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
  BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
else
  RED='' GREEN='' YELLOW='' BLUE='' CYAN='' BOLD='' NC=''
fi

# ============== 路径 ==============
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_DIR="$ROOT_DIR/logs/pids"
RUN_DIR="$ROOT_DIR/run"
mkdir -p "$LOG_DIR" "$PID_DIR" "$RUN_DIR"

# ============== 默认配置 ==============
SKIP_CHECKS=0
SKIP_BUILD=0
BUILD_ONLY=0
CLEAN=0
ASSUME_YES=0
SPRING_PROFILE="dev"
TARGET_MODULE=""  # 空 = 全量, 否则只部署指定模块

# 模块清单: name|module-dir|port|context-path
# 启动顺序: 基础 → 业务
SERVICES=(
  "kms-gateway|fin-kms-gateway|8091|"
  "notify-center|fin-notify-center|8092|"
  "audit-center|fin-audit-center|8093|"
  "auth-center|fin-auth-center|8081|"
  "chat-center|fin-chat-center|8082|"
  "trade-gateway|fin-trade-gateway|8083|"
  "compliance-center|fin-compliance-center|8090|"
  "observability|fin-observability|8094|"
  "gateway|fin-gateway|9000|"
)

# ============== 工具函数 ==============
log()  { echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "$LOG_DIR/deploy.log"; }
info() { echo -e "${BLUE}[INFO]${NC}  $*" | tee -a "$LOG_DIR/deploy.log"; }
ok()   { echo -e "${GREEN}[ OK ]${NC}  $*" | tee -a "$LOG_DIR/deploy.log"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$LOG_DIR/deploy.log"; }
err()  { echo -e "${RED}[FAIL]${NC} $*" | tee -a "$LOG_DIR/deploy.log"; }
step() { echo -e "\n${BOLD}${CYAN}▶ $*${NC}" | tee -a "$LOG_DIR/deploy.log"; }

confirm() {
  [ "$ASSUME_YES" -eq 1 ] && return 0
  read -r -p "$1 [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]]
}

print_help() {
  cat <<EOF
FinChat · Maven 打包编译一键部署

核心流程: 静态检查 → mvn package → 启 9 个 fat jar

用法:
  ./scripts/mvn-deploy.sh                # 全量 (静态检查 + 打包 + 启动)
  ./scripts/mvn-deploy.sh --skip-checks  # 跳过静态检查
  ./scripts/mvn-deploy.sh --skip-build   # 跳过打包 (用现有 jar)
  ./scripts/mvn-deploy.sh --build-only   # 只打包不启动
  ./scripts/mvn-deploy.sh --clean        # 清理 target + 旧 jar
  ./scripts/mvn-deploy.sh --module=fin-auth-center   # 只部署指定模块
  ./scripts/mvn-deploy.sh --profile=dev  # Spring profile (默认 dev)
  ./scripts/mvn-deploy.sh -y             # 跳过确认

依赖:
  - JDK 17+
  - Maven 3.8+
  - Redis (可选, 端口 6379)
  - MySQL 8 (可选, 用 H2 则不需要)
EOF
  exit 0
}

# ============== 解析参数 ==============
while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-checks)  SKIP_CHECKS=1; shift ;;
    --skip-build)   SKIP_BUILD=1; shift ;;
    --build-only)   BUILD_ONLY=1; shift ;;
    --clean)        CLEAN=1; shift ;;
    --module=*)     TARGET_MODULE="${1#*=}"; shift ;;
    --module)       TARGET_MODULE="$2"; shift 2 ;;
    --profile=*)    SPRING_PROFILE="${1#*=}"; shift ;;
    --profile)      SPRING_PROFILE="$2"; shift 2 ;;
    -y|--yes)       ASSUME_YES=1; shift ;;
    --no-color)     NO_COLOR=1; shift ;;
    -h|--help)      print_help ;;
    *)              err "未知参数: $1"; print_help; exit 1 ;;
  esac
done

# --help / -h 提前退出
if [ "${HELP_ONLY:-}" = "1" ]; then exit 0; fi

# ============== Banner ==============
cat <<'BANNER' | tee "$LOG_DIR/deploy.log"
   _______ _____  _    _  ____ _   _ _____   _____  _____  _____
  |  ____|  __ \| |  | |/ ____| \ | |  __ \ / ____|/ ____|/ ____|
  | |__  | |__) | |__| | |    |  \| | |__) | |    | (___ | |
  |  __| |  _  /|  __  | |    | . ` |  _  /| |    \___ \| |
  | |____| | \ \| |  | | |____| |\  | | \ \| |____ ____) | |____
  |______|_|  \_\_|  |_|\_____|_| \_|_|  \_\\_____|_____/ \_____|
BANNER
echo "  FinChat · Maven 一键部署"
echo "  Spring Profile: $SPRING_PROFILE"
echo "  目标: ${TARGET_MODULE:-all modules}"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "════════════════════════════════════════"

# ============== 阶段 1: 前置检查 ==============
preflight() {
  step "1/6 前置环境检查"
  cd "$ROOT_DIR"

  command -v java >/dev/null 2>&1 || { err "JDK 未安装"; exit 1; }
  JAVA_V=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}')
  ok "Java: $JAVA_V"

  command -v mvn >/dev/null 2>&1 || { err "Maven 未安装"; exit 1; }
  MVN_V=$(mvn -v 2>&1 | head -1 | awk '{print $3}')
  ok "Maven: $MVN_V"

  # JDK 版本必须 ≥ 17
  JAVA_MAJOR=$(echo "$JAVA_V" | cut -d. -f1)
  if [ "$JAVA_MAJOR" -lt 17 ]; then
    err "JDK 版本必须 ≥ 17 (当前 $JAVA_V)"
    exit 1
  fi

  # 内存检查
  TOTAL_MEM=$(free -m 2>/dev/null | awk '/^Mem:/{print $2}' || sysctl -n hw.memsize 2>/dev/null | awk '{print int($1/1024/1024)}')
  if [ -n "$TOTAL_MEM" ] && [ "$TOTAL_MEM" -lt 2048 ]; then
    warn "系统内存 < 2GB, 建议加内存后再全量部署 (9 个 JVM)"
  else
    ok "内存: ${TOTAL_MEM}MB"
  fi
}

# ============== 阶段 2: 清理 ==============
do_clean() {
  if [ "$CLEAN" -ne 1 ]; then return; fi
  step "2/6 清理 (--clean)"
  cd "$ROOT_DIR"

  # 停掉旧进程
  if [ -d "$PID_DIR" ]; then
    local n=$(ls "$PID_DIR" 2>/dev/null | wc -l)
    if [ "$n" -gt 0 ]; then
      info "发现 $n 个旧 PID, 先停"
      for p in "$PID_DIR"/*.pid; do
        [ -f "$p" ] || continue
        local pid=$(cat "$p" 2>/dev/null)
        [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
        rm -f "$p"
      done
      sleep 2
      pkill -f "spring-boot-loader" 2>/dev/null || true
      ok "旧进程已停"
    fi
  fi

  # 删 target (mvn clean)
  if confirm "删除所有 target/ 目录?"; then
    find "$ROOT_DIR" -type d -name "target" -not -path "*/node_modules/*" -exec rm -rf {} + 2>/dev/null || true
    ok "target/ 已清理"
  fi
}

# ============== 阶段 3: 静态检查 ==============
run_checks() {
  if [ "$SKIP_CHECKS" -eq 1 ]; then
    warn "跳过静态检查 (--skip-checks)"
    return
  fi
  step "3/6 静态检查"
  cd "$ROOT_DIR"

  if [ -f "scripts/static-check.sh" ]; then
    if bash scripts/static-check.sh 2>&1 | tail -3 | grep -q "错误: 0"; then
      ok "Java 静态检查通过"
    else
      err "Java 静态检查有错误, 请修复"
      bash scripts/static-check.sh | tail -10
      exit 1
    fi
  fi

  if [ -f "fin-web/scripts/vue-check.sh" ]; then
    if bash fin-web/scripts/vue-check.sh 2>&1 | tail -3 | grep -q "错误: 0"; then
      ok "Vue 静态检查通过"
    fi
  fi
}

# ============== 阶段 4: Maven 打包 ==============
do_build() {
  if [ "$SKIP_BUILD" -eq 1 ]; then
    warn "跳过打包 (--skip-build)"
    return
  fi
  step "4/6 Maven 打包编译"

  local modules_args="-pl"
  if [ -n "$TARGET_MODULE" ]; then
    modules_args="-pl $TARGET_MODULE"
    info "只打包: $TARGET_MODULE"
  else
    # 全量: 用 -am 处理依赖模块
    info "打包全部 9 个可执行模块 + 依赖"
  fi

  # 关键: 用 spring-boot:repackage 生成可执行 fat jar
  cd "$ROOT_DIR"

  local mvn_cmd="mvn -B -ntp -T 1C clean package -DskipTests \
    -Dmaven.javadoc.skip=true \
    -Dspring.profiles.active=$SPRING_PROFILE \
    $modules_args"

  info "执行: $mvn_cmd"
  echo ""

  if $mvn_cmd 2>&1 | tee "$LOG_DIR/mvn-build.log" | tail -50; then
    # 检查 jar 是否生成
    local jar_count=$(find "$ROOT_DIR" -path "*/target/*-spring-boot.jar" 2>/dev/null | wc -l)
    ok "Maven 打包完成, 生成 $jar_count 个 fat jar"
  else
    err "Maven 打包失败, 详见 $LOG_DIR/mvn-build.log"
    exit 1
  fi
}

# ============== 阶段 5: 启动服务 ==============
start_service() {
  local name="$1"
  local module="$2"
  local port="$3"
  local context="$4"
  local jar_path="$ROOT_DIR/$module/target/*-spring-boot.jar"

  # 找 jar
  local jar=$(ls $jar_path 2>/dev/null | head -1)
  if [ -z "$jar" ]; then
    err "  $name: jar 不存在 ($jar_path)"
    return 1
  fi

  # 检查端口占用
  if lsof -i :$port >/dev/null 2>&1; then
    warn "  $name: 端口 $port 已被占用, 跳过"
    return 0
  fi

  info "  启动 $name (port=$port)..."

  # 启动参数
  local args="-Xms256m -Xmx512m"
  args="$args -Dserver.port=$port"
  args="$args -Dspring.profiles.active=$SPRING_PROFILE"
  args="$args -Dfile.encoding=UTF-8"
  args="$args -Djava.io.tmpdir=$RUN_DIR/tmp"

  # 后台启动
  nohup java $args -jar "$jar" \
    > "$LOG_DIR/$name.log" 2>&1 &

  local pid=$!
  echo "$pid" > "$PID_DIR/$name.pid"

  # 等 2 秒看是否还活着
  sleep 2
  if kill -0 "$pid" 2>/dev/null; then
    ok "    $name pid=$pid, 日志: logs/$name.log"
    return 0
  else
    err "    $name 启动失败, 看 logs/$name.log"
    return 1
  fi
}

start_services() {
  step "5/6 启动服务 (Spring profile=$SPRING_PROFILE)"

  local started=0
  local failed=0

  for svc in "${SERVICES[@]}"; do
    IFS='|' read -r name module port context <<< "$svc"

    # 过滤单模块
    if [ -n "$TARGET_MODULE" ] && [ "$module" != "$TARGET_MODULE" ]; then
      continue
    fi

    if start_service "$name" "$module" "$port" "$context"; then
      started=$((started + 1))
    else
      failed=$((failed + 1))
    fi

    # 间隔 3s 避免端口冲突
    sleep 3
  done

  ok "已启动: $started, 失败: $failed"
}

# ============== 阶段 6: 验证 ==============
verify() {
  step "6/6 健康检查"
  sleep 5  # 等 JVM 完全启动

  local total=0
  local pass=0

  for svc in "${SERVICES[@]}"; do
    IFS='|' read -r name module port context <<< "$svc"
    if [ -n "$TARGET_MODULE" ] && [ "$module" != "$TARGET_MODULE" ]; then
      continue
    fi
    total=$((total + 1))

    local url="http://localhost:$port$context/api"
    case "$name" in
      kms-gateway) url="$url/kms/health" ;;
      *)           url="$url/v1/$([ "$name" = "gateway" ] && echo "auth" || echo "$([ "$name" = "observability" ] && echo "observability" || echo "${name%-*}")")/health" ;;
    esac

    local code=$(curl -s -o /tmp/h -w "%{http_code}" --max-time 3 "$url" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
      ok "  $name (port=$port): HTTP $code"
      pass=$((pass + 1))
    else
      warn "  $name (port=$port): HTTP $code (可能未就绪)"
    fi
  done

  echo ""
  if [ $pass -eq $total ]; then
    echo -e "${GREEN}════════════════════════════════════════${NC}"
    echo -e "${GREEN}  ✅ 全部健康: $pass/$total${NC}"
    echo -e "${GREEN}════════════════════════════════════════${NC}"
  else
    echo -e "${YELLOW}════════════════════════════════════════${NC}"
    echo -e "${YELLOW}  ⚠ 部分异常: $pass/$total (服务可能还在启动)${NC}"
    echo -e "${YELLOW}════════════════════════════════════════${NC}"
  fi

  rm -f /tmp/h
}

# ============== 主流程 ==============
main() {
  preflight
  do_clean
  run_checks
  do_build

  if [ "$BUILD_ONLY" -eq 1 ]; then
    echo ""
    ok "打包完成 (--build-only), 未启动服务"
    echo "  启动: ./scripts/mvn-deploy.sh --skip-build"
    exit 0
  fi

  start_services
  verify

  echo ""
  echo "════════════════════════════════════════"
  echo -e "${GREEN}  🎉 部署完成${NC}"
  echo "════════════════════════════════════════"
  echo ""
  echo "📂 工程: $ROOT_DIR"
  echo "📋 日志: $LOG_DIR/*.log"
  echo "🔧 PID : $PID_DIR/*.pid"
  echo ""
  echo "🔗 访问入口:"
  echo "   http://localhost:9000  API 网关"
  echo "   http://localhost:8081  Auth (登录)"
  echo "   http://localhost:8082  Chat (WebSocket)"
  echo "   http://localhost:8083  Trade (交易)"
  echo "   http://localhost:8091  KMS (加密机)"
  echo "   http://localhost:8093  Audit (审计)"
  echo ""
  echo "  停止: ./scripts/mvn-stop.sh"
  echo "  重启: ./scripts/mvn-stop.sh && ./scripts/mvn-deploy.sh --skip-build"
  echo "  日志: tail -f logs/<service>.log"
  echo "  清理: ./scripts/mvn-deploy.sh --clean"
  echo ""
}

main
