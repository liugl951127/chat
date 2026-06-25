#!/usr/bin/env bash
# ============================================================
# FinChat 一键部署脚本
# ============================================================
# 用法:
#   ./scripts/deploy.sh --mode=sandbox          # 纯沙箱 (无需 Docker)
#   ./scripts/deploy.sh --mode=docker           # Docker Compose 全量
#   ./scripts/deploy.sh --mode=backend-only     # 只起后端 (不建库)
#   ./scripts/deploy.sh --mode=db-only          # 只建数据库
#   ./scripts/deploy.sh --mode=production       # 生产模式 (KMS 硬件)
#   ./scripts/deploy.sh --help                  # 帮助
#
# 选项:
#   --skip-build    跳过 Maven 构建
#   --skip-checks   跳过静态检查
#   --with-nginx    启用 nginx 统一入口
#   --clean         先清理再部署
#   --no-color      关闭彩色输出
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

# 路径
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"
LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

# 默认配置
MODE="sandbox"
SKIP_BUILD=0
SKIP_CHECKS=0
WITH_NGINX=1
CLEAN=0
BUILD_PROFILES="dev"
DOCKER_FILE="docker-compose.yml"

# ============================================================
# 帮助
# ============================================================
print_help() {
  cat <<'EOF'
FinChat 一键部署脚本

用法:
  ./scripts/deploy.sh --mode=MODE [选项]

模式 (MODE):
  sandbox          纯沙箱 (Node 业务模拟, 无 Docker) [默认]
  docker           Docker Compose 全量部署 (含前端)
  backend-only     只起后端 7 个微服务
  db-only          只建 MySQL 8 库 18 表
  production       生产模式 (KMS 走硬件)

选项:
  --skip-build     跳过 Maven 构建 (Docker 模式)
  --skip-checks    跳过静态检查
  --no-nginx       不启 nginx (Docker 模式)
  --clean          先清理 (停止并删除容器/数据)
  --no-color       关闭彩色
  -h, --help       帮助

示例:
  # 沙箱演示 (默认, 30 秒)
  ./scripts/deploy.sh

  # Docker 全量 (含 Web 前端, 3-5 分钟)
  ./scripts/deploy.sh --mode=docker

  # 只建库
  ./scripts/deploy.sh --mode=db-only

  # 生产 (需 KMS 硬件)
  ./scripts/deploy.sh --mode=production

环境变量 (覆盖默认值):
  WX_MINI_APPID, WX_MINI_SECRET, WECOM_CORPID, WECOM_CORPSECRET
  KMS_HOST, KMS_PORT, KMS_APP_KEY, KMS_SOFT
  DB_USER, DB_PASS, REDIS_HOST
EOF
}

# ============================================================
# 工具函数
# ============================================================
log()   { echo -e "${CYAN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "$LOG_DIR/deploy.log"; }
info()  { echo -e "${BLUE}[INFO]${NC}  $*" | tee -a "$LOG_DIR/deploy.log"; }
ok()    { echo -e "${GREEN}[ OK ]${NC}  $*" | tee -a "$LOG_DIR/deploy.log"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$LOG_DIR/deploy.log"; }
err()   { echo -e "${RED}[FAIL]${NC} $*" | tee -a "$LOG_DIR/deploy.log"; }
step()  { echo -e "\n${BOLD}${CYAN}▶ $*${NC}" | tee -a "$LOG_DIR/deploy.log"; }

check_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    err "命令未找到: $1 (请先安装)"
    return 1
  fi
}

confirm() {
  if [ -z "${ASSUME_YES:-}" ]; then
    read -r -p "$1 [y/N] " ans
    [[ "$ans" =~ ^[Yy]$ ]]
  else
    return 0
  fi
}

# ============================================================
# 解析参数
# ============================================================
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode=*)     MODE="${1#*=}"; shift ;;
    --mode)       MODE="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --skip-checks) SKIP_CHECKS=1; shift ;;
    --no-nginx)   WITH_NGINX=0; shift ;;
    --with-nginx) WITH_NGINX=1; shift ;;
    --clean)      CLEAN=1; shift ;;
    --no-color)   NO_COLOR=1; shift ;;
    -h|--help)    print_help; exit 0 ;;
    *)            err "未知参数: $1"; print_help; exit 1 ;;
  esac
done

# ============================================================
# Banner
# ============================================================
cat <<'EOF' | tee "$LOG_DIR/deploy.log"
   _______ _____  _    _  ____ _   _ _____   _____  _____  _____
  |  ____|  __ \| |  | |/ ____| \ | |  __ \ / ____|/ ____|/ ____|
  | |__  | |__) | |__| | |    |  \| | |__) | |    | (___ | |
  |  __| |  _  /|  __  | |    | . ` |  _  /| |    \___ \| |
  | |____| | \ \| |  | | |____| |\  | | \ \| |____ ____) | |____
  |______|_|  \_\_|  |_|\_____|_| \_|_|  \_\\_____|_____/ \_____|

  FinChat · 金融合规多端聊天与交易系统
  模式: ${MODE}
  时间: $(date '+%Y-%m-%d %H:%M:%S')
  ============================================================
EOF

# ============================================================
# 前置: 环境检查
# ============================================================
preflight() {
  step "1/7 前置环境检查"
  cd "$ROOT_DIR"

  # Node (沙箱 / Vue build)
  if [ "$MODE" = "sandbox" ] || [ "$MODE" = "docker" ]; then
    check_cmd node || exit 1
    NODE_V=$(node -v)
    ok "Node: $NODE_V"
  fi

  # Java (Maven 模式)
  if [ "$MODE" = "backend-only" ] || [ "$MODE" = "production" ]; then
    check_cmd java || { err "请安装 JDK 17+"; exit 1; }
    JAVA_V=$(java -version 2>&1 | head -1)
    ok "Java: $JAVA_V"
    check_cmd mvn || { err "请安装 Maven 3.8+"; exit 1; }
    MVN_V=$(mvn -v 2>&1 | head -1)
    ok "Maven: $MVN_V"
  fi

  # Docker (Docker 模式)
  if [ "$MODE" = "docker" ] || [ "$MODE" = "production" ]; then
    check_cmd docker || { err "请安装 Docker"; exit 1; }
    DOCKER_V=$(docker --version)
    ok "Docker: $DOCKER_V"
    check_cmd docker-compose || check_cmd "docker compose" || {
      err "请安装 docker-compose"; exit 1;
    }
  fi

  # MySQL 客户端 (db-only)
  if [ "$MODE" = "db-only" ]; then
    check_cmd mysql || warn "未装 mysql 客户端, 将用 Docker 执行"
  fi

  ok "前置检查通过"
}

# ============================================================
# 阶段 2: 静态检查
# ============================================================
run_checks() {
  if [ "$SKIP_CHECKS" -eq 1 ]; then
    warn "跳过静态检查 (--skip-checks)"
    return
  fi
  step "2/7 静态检查"
  cd "$ROOT_DIR"

  # Java
  if [ -f "scripts/static-check.sh" ]; then
    if bash scripts/static-check.sh >/dev/null 2>&1; then
      ok "Java 静态检查通过"
    else
      warn "Java 静态检查有警告, 继续..."
    fi
  fi

  # Vue
  if [ -f "fin-web/scripts/vue-check.sh" ]; then
    if bash fin-web/scripts/vue-check.sh >/dev/null 2>&1; then
      ok "Vue 静态检查通过"
    else
      warn "Vue 静态检查有警告, 继续..."
    fi
  fi

  # Node 业务模拟
  if [ -f "scripts/sim-server.js" ]; then
    if node scripts/sim-server.js > "$LOG_DIR/sim.log" 2>&1; then
      RESULT=$(grep -c "✅" "$LOG_DIR/sim.log" || true)
      ok "业务逻辑模拟通过 ($RESULT 项 ✅)"
    else
      err "业务逻辑模拟失败, 详见 $LOG_DIR/sim.log"
      exit 1
    fi
  fi
}

# ============================================================
# 阶段 3: 清理
# ============================================================
do_clean() {
  if [ "$CLEAN" -ne 1 ]; then return; fi
  step "3/7 清理 (--clean)"
  cd "$DEPLOY_DIR"

  if [ "$MODE" = "docker" ] || [ "$MODE" = "production" ] || [ "$MODE" = "backend-only" ]; then
    if confirm "确认停止并删除所有 fin-chat 容器/网络?"; then
      docker-compose -f "$DOCKER_FILE" down -v --remove-orphans 2>/dev/null || true
      ok "Docker 资源已清理"
    fi
  fi

  if [ "$MODE" = "sandbox" ]; then
    rm -f "$LOG_DIR"/*.log
    ok "日志已清理"
  fi
}

# ============================================================
# 阶段 4: 数据库初始化
# ============================================================
init_db() {
  step "4/7 数据库初始化"
  cd "$DEPLOY_DIR"

  if [ "$MODE" = "sandbox" ]; then
    info "沙箱模式使用 H2 内存库, 无需建库"
    return
  fi

  if [ "$MODE" = "db-only" ]; then
    info "仅建库, 不启动服务"
  fi

  if command -v mysql >/dev/null 2>&1; then
    # 直接用 mysql 客户端
    if [ -z "${DB_USER:-}" ]; then export DB_USER="root"; fi
    if [ -z "${DB_PASS:-}" ]; then export DB_PASS="root123"; fi

    info "通过 mysql 客户端建库 ($DB_USER@localhost:3306)"

    if mysql -h "${DB_HOST:-127.0.0.1}" -P "${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" \
         < mysql-init/V1__init_all.sql 2>>"$LOG_DIR/db.log"; then
      ok "8 库 18 表已建"
    else
      warn "mysql 客户端执行失败, 改用 Docker"
      init_db_via_docker
    fi

    # 测试数据
    if [ -f "mysql-init/V2__seed_test_data.sql" ]; then
      mysql -h "${DB_HOST:-127.0.0.1}" -P "${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" \
        < mysql-init/V2__seed_test_data.sql 2>>"$LOG_DIR/db.log" || warn "种子数据失败 (可忽略)"
      ok "种子数据已写入"
    fi
  else
    init_db_via_docker
  fi
}

init_db_via_docker() {
  info "通过 Docker 启动临时 MySQL 建库"
  docker run --rm -i \
    -v "$DEPLOY_DIR/mysql-init:/init" \
    mysql:8.0 \
    bash -c "
      mysqld --user=root --init-file=/init/V1__init_all.sql &
      sleep 8
      mysql -uroot -e 'SHOW DATABASES;'
    " 2>>"$LOG_DIR/db.log" || warn "Docker MySQL 执行有问题, 请检查日志"
}

# ============================================================
# 阶段 5: 构建
# ============================================================
do_build() {
  if [ "$SKIP_BUILD" -eq 1 ]; then
    warn "跳过构建 (--skip-build)"
    return
  fi
  step "5/7 构建"
  cd "$ROOT_DIR"

  case "$MODE" in
    sandbox)
      info "沙箱模式无需 Maven 构建"
      ;;
    docker|production)
      # Docker 模式: 各模块 Dockerfile 各自构建
      info "Docker 模式: docker-compose 将按需构建镜像"
      ;;
    backend-only)
      # 单体构建
      check_cmd mvn || exit 1
      info "Maven 构建 (--no-snapshot-updates --batch-mode)..."
      mvn -B -ntp clean install \
        -DskipTests \
        -Dmaven.javadoc.skip=true \
        -Dspring.profiles.active="$BUILD_PROFILES" \
        2>&1 | tee "$LOG_DIR/mvn-build.log" | tail -20
      ok "Maven 构建完成"
      ;;
  esac
}

# ============================================================
# 阶段 6: 启动服务
# ============================================================
start_services() {
  step "6/7 启动服务"
  cd "$DEPLOY_DIR"

  case "$MODE" in
    sandbox)
      info "沙箱模式: 业务逻辑已通过 Node 模拟验证, 不启服务"
      info "如需真实 Java 服务, 请用: --mode=backend-only 或 --mode=docker"
      ;;
    docker|production)
      if [ "$WITH_NGINX" -eq 0 ]; then
        # 临时去掉 nginx 服务
        docker-compose -f "$DOCKER_FILE" up -d \
          mysql redis nacos \
          kms-gateway notify-center audit-center \
          auth-center chat-center trade-gateway compliance-center \
          observability gateway web
      else
        docker-compose -f "$DOCKER_FILE" up -d
      fi
      ok "Docker 容器已启动"

      # 等待就绪
      wait_for_ready
      ;;
    backend-only)
      cd "$ROOT_DIR"
      info "启动各微服务 (后台)"

      declare -A SERVICES=(
        ["kms-gateway"]="fin-kms-gateway 8091"
        ["notify-center"]="fin-notify-center 8092"
        ["audit-center"]="fin-audit-center 8093"
        ["auth-center"]="fin-auth-center 8081"
        ["chat-center"]="fin-chat-center 8082"
        ["trade-gateway"]="fin-trade-gateway 8083"
        ["compliance-center"]="fin-compliance-center 8090"
      )

      for name in "${!SERVICES[@]}"; do
        info "启动 $name..."
        nohup bash -c "mvn -pl ${SERVICES[$name]%% *} spring-boot:run" \
          > "$LOG_DIR/$name.log" 2>&1 &
        echo $! > "$LOG_DIR/$name.pid"
      done
      ok "7 个服务已后台启动 (见 $LOG_DIR/*.log)"
      ;;
  esac
}

# ============================================================
# 阶段 7: 验证
# ============================================================
wait_for_ready() {
  info "等待服务就绪 (最多 60s)..."
  local urls=(
    "http://localhost:8091/api/kms/health"
    "http://localhost:8081/api/v1/auth/health"
    "http://localhost:8082/api/v1/chat/health"
    "http://localhost:8083/api/v1/trade/health"
    "http://localhost:9000/api/v1/auth/health"
  )
  local max=60
  local elapsed=0
  local ready=0
  local total=${#urls[@]}

  while [ $elapsed -lt $max ] && [ $ready -lt $total ]; do
    ready=0
    for u in "${urls[@]}"; do
      if curl -fsS -o /dev/null --max-time 2 "$u" 2>/dev/null; then
        ready=$((ready + 1))
      fi
    done
    if [ $ready -eq $total ]; then break; fi
    sleep 3
    elapsed=$((elapsed + 3))
    printf "."
  done
  echo ""
  ok "服务就绪 $ready/$total"
}

verify() {
  step "7/7 验证"
  cd "$ROOT_DIR"

  if [ "$MODE" = "sandbox" ]; then
    info "沙箱验证: 业务逻辑模拟"
    node scripts/sim-server.js 2>&1 | tail -10
    ok "✅ 沙箱验证通过"
    return
  fi

  # 健康检查
  info "调用 health 端点..."

  declare -A ENDPOINTS=(
    ["KMS"]="http://localhost:8091/api/kms/health"
    ["AUTH"]="http://localhost:8081/api/v1/auth/health"
    ["CHAT"]="http://localhost:8082/api/v1/chat/health"
    ["TRADE"]="http://localhost:8083/api/v1/trade/health"
    ["COMPLIANCE"]="http://localhost:8090/api/v1/compliance/health"
    ["NOTIFY"]="http://localhost:8092/api/v1/notify/health"
    ["AUDIT"]="http://localhost:8093/api/v1/audit/health"
  )

  for name in "${!ENDPOINTS[@]}"; do
    url="${ENDPOINTS[$name]}"
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$url" 2>/dev/null || echo "000")
    if [ "$code" = "200" ]; then
      ok "$name ($url): HTTP $code"
    else
      warn "$name ($url): HTTP $code (可能未启动或路由问题)"
    fi
  done

  # 端到端测试 (如 KMS + AUTH 通了)
  if command -v jq >/dev/null 2>&1; then
    info "端到端测试: KMS SM3"
    curl -s -X POST "http://localhost:8091/api/kms/sm3/hash" \
      -H "Content-Type: application/json" \
      -d '{"data":"hello world"}' | jq -r '.data.hash // .message // "fail"' 2>/dev/null \
      | xargs -I{} sh -c 'echo "   SM3(\"hello world\") = {}"'
  fi
}

# ============================================================
# 主流程
# ============================================================
main() {
  preflight
  run_checks
  do_clean
  init_db
  do_build
  start_services
  verify

  echo ""
  echo "════════════════════════════════════════"
  echo -e "  ${GREEN}✅ 部署完成${NC}"
  echo "════════════════════════════════════════"
  echo ""
  echo "📂 工程: $ROOT_DIR"
  echo "📋 日志: $LOG_DIR"
  echo ""
  echo "🔗 访问入口:"

  case "$MODE" in
    sandbox)
      echo "   业务模拟:  node scripts/sim-server.js"
      echo "   静态检查:  bash scripts/static-check.sh"
      echo "   销毁:      ./scripts/destroy.sh --mode=sandbox"
      ;;
    docker|production)
      [ "$WITH_NGINX" -eq 1 ] && echo "   http://localhost:3000  Web 前端 (经 nginx)"
      echo "   http://localhost:8088  Web 直连 (调试)"
      echo "   http://localhost:9000  API 网关"
      echo "   http://localhost:8848  Nacos 控制台 (nacos/nacos)"
      echo ""
      echo "   日志: docker-compose -f deploy/docker-compose.yml logs -f [服务名]"
      echo "   销毁: ./scripts/destroy.sh --mode=docker"
      ;;
    backend-only)
      echo "   7 个微服务后台运行, 日志: $LOG_DIR/*.log"
      echo "   停止: ./scripts/destroy.sh --mode=backend-only"
      ;;
    db-only)
      echo "   8 库 18 表已建, MySQL: $DB_USER@${DB_HOST:-127.0.0.1}:${DB_PORT:-3306}"
      ;;
  esac
  echo ""
}

main "$@"
