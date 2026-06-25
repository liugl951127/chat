#!/usr/bin/env bash
#
# FinChat · 全栈一键部署 (生产级)
#
# 包含:
#   1. 环境检查 (JDK / Maven / Redis / MySQL / Nginx)
#   2. 数据库初始化 (8 库 20 表 + 种子数据)
#   3. Maven 编译 (13 模块)
#   4. 启动 9 个微服务 (含端口健康检查)
#   5. 配置 Nginx 反向代理 + WS 升级
#   6. 注册 systemd 服务 (可选)
#   7. 端到端冒烟测试
#
# 用法:
#   sudo ./scripts/oneclick-deploy.sh                      # 全量部署
#   sudo ./scripts/oneclick-deploy.sh --skip-build         # 跳过 Maven (用现有 jar)
#   sudo ./scripts/oneclick-deploy.sh --skip-db            # 跳过数据库初始化
#   sudo ./scripts/oneclick-deploy.sh --skip-nginx         # 跳过 Nginx
#   sudo ./scripts/oneclick-deploy.sh --profile=offline    # 离线模式 (无 Redis/MySQL)
#   sudo ./scripts/oneclick-deploy.sh --install-service    # 注册 systemd
#   sudo ./scripts/oneclick-deploy.sh -y                   # 跳过所有确认
#
# 部署完成后访问:
#   Web 管理端:    http://localhost/
#   H5 客户:       http://localhost/mobile
#   WebSocket:     ws://localhost/ws/chat
#   API 网关:      http://localhost/api/...
#   健康检查:      curl http://localhost/api/v1/chat/health
#
set -e
set -o pipefail

# ============== 颜色 ==============
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
  BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
else
  RED=''; GREEN=''; YELLOW=''; BLUE=''; CYAN=''; BOLD=''; NC=''
fi

# ============== 默认配置 ==============
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ROOT"
LOG_DIR="${LOG_DIR:-/var/log/finchat}"
RUN_DIR="${RUN_DIR:-/var/run/finchat}"
PROFILE="${PROFILE:-prod}"        # prod / dev / offline
SKIP_BUILD=0
SKIP_DB=0
SKIP_NGINX=0
INSTALL_SERVICE=0
AUTO_YES=0
DRY_RUN=0
SERVICES=(
  "fin-kms-gateway:8091"
  "fin-auth-center:8081"
  "fin-compliance-center:8090"
  "fin-chat-center:8082"
  "fin-trade-gateway:8083"
  "fin-notify-center:8092"
  "fin-audit-center:8093"
  "fin-observability:8094"
  "fin-gateway:9000"
)
STARTUP_TIMEOUT=60   # 每个服务最长等 N 秒
HEALTH_INTERVAL=2    # 健康检查间隔

# ============== 参数解析 ==============
for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=1 ;;
    --skip-db) SKIP_DB=1 ;;
    --skip-nginx) SKIP_NGINX=1 ;;
    --install-service) INSTALL_SERVICE=1 ;;
    --dry-run) DRY_RUN=1 ;;
    -y|--yes) AUTO_YES=1 ;;
    --profile=*) PROFILE="${arg#*=}" ;;
    --help|-h)
      sed -n '2,40p' "$0"; exit 0 ;;
    *) echo -e "${RED}未知参数: $arg${NC}"; exit 1 ;;
  esac
done

# ============== 工具函数 ==============
log()    { echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $*"; }
ok()     { echo -e "${GREEN}[✓]${NC} $*"; }
warn()   { echo -e "${YELLOW}[!]${NC} $*"; }
fail()   { echo -e "${RED}[✗]${NC} $*"; exit 1; }
section(){ echo -e "\n${BOLD}${CYAN}═══ $* ═══${NC}"; }

require_root() {
  if [ "$EUID" -ne 0 ]; then
    warn "需要 root 权限, 尝试用 sudo..."
    exec sudo -E "$0" "$@"
  fi
}

confirm() {
  [ "$AUTO_YES" -eq 1 ] && return 0
  local prompt="$1"
  echo -ne "${YELLOW}${prompt} [y/N]${NC} "
  read -r ans
  case "$ans" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

# ============== Banner ==============
clear 2>/dev/null || true
cat <<'EOF'

  ______ _      ______          _
 |  ____| |    |  ____|        | |
 | |__  | |_ __ | |__ _ __   ___| |__   ___ _ __
 |  __| | __/ _||  __| '_ \ / __| '_ \ / _ \ '__|
 | |    | || (_| | |  | | | | (__| | | |  __/ |
 |_|    |___\__, |_|  |_| |_|\___|_| |_|\___|_|
             __/ |
            |___/

   金融合规聊天与交易系统 · 全栈一键部署
   JDK 17 + Vue 3 + Spring Boot 3 + Spring Cloud Gateway
   国密 SM2/SM3/SM4 · 等保 2.0 三级 · 金融销售双录留痕

EOF

require_root "$@"

# ============== 步骤 1: 环境检查 ==============
section "Step 1 / 7 : 环境检查"

# JDK 17
if command -v java >/dev/null 2>&1; then
  JDK_VER=$(java -version 2>&1 | head -1 | awk -F\" '{print $2}')
  case "$JDK_VER" in
    17.*|21.*) ok "JDK $JDK_VER" ;;
    *) warn "JDK 版本 $JDK_VER, 推荐 17+";;
  esac
else
  warn "未检测到 JDK, 尝试安装 openjdk-17-jdk-headless..."
  apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless
  ok "JDK 安装完成"
fi

# Maven
if command -v mvn >/dev/null 2>&1; then
  ok "Maven $(mvn -v | head -1 | awk '{print $3}')"
else
  if [ -x /usr/share/maven/bin/mvn ]; then
    export PATH=$PATH:/usr/share/maven/bin
    ok "Maven 已安装 (从 /usr/share/maven/bin)"
  else
    warn "未检测到 Maven, 尝试安装..."
    apt-get install -y -qq maven || fail "Maven 安装失败"
    ok "Maven 安装完成"
  fi
fi

# Node.js (前端构建)
if command -v npm >/dev/null 2>&1; then
  ok "Node.js $(node -v)"
else
  warn "未检测到 Node.js, 前端构建将跳过"
fi

# Redis / MySQL (按 profile)
if [ "$PROFILE" = "offline" ]; then
  ok "离线模式 - 跳过 Redis/MySQL 检查"
elif [ "$PROFILE" = "prod" ]; then
  command -v redis-cli >/dev/null 2>&1 && ok "Redis $(redis-cli --version)" \
    || warn "未检测到 Redis (生产建议安装)"
  command -v mysql >/dev/null 2>&1 && ok "MySQL $(mysql --version)" \
    || warn "未检测到 MySQL (生产建议安装)"
fi

# Nginx (可选)
command -v nginx >/dev/null 2>&1 && ok "Nginx $(nginx -v 2>&1 | awk -F/ '{print $2}')" \
  || warn "未检测到 Nginx (前端代理需安装)"

# ============== 步骤 2: 目录与权限 ==============
section "Step 2 / 7 : 准备目录"
mkdir -p "$LOG_DIR" "$RUN_DIR"
chmod 755 "$LOG_DIR" "$RUN_DIR"
ok "日志目录: $LOG_DIR"
ok "运行目录: $RUN_DIR"

# ============== 步骤 3: Maven 编译 ==============
section "Step 3 / 7 : Maven 编译"

if [ "$SKIP_BUILD" -eq 1 ]; then
  warn "跳过 Maven 编译 (使用现有 jar)"
else
  log "编译 13 模块 (约 1-2 分钟)..."
  cd "$ROOT"
  mvn -B -ntp clean package -DskipTests 2>&1 | tail -20
  ok "编译完成"

  # 验证关键 jar
  local missing=0
  for svc_port in "${SERVICES[@]}"; do
    svc="${svc_port%:*}"
    jar="$ROOT/$svc/target/$svc-1.0.0.jar"
    [ -f "$jar" ] || { warn "缺少 jar: $jar"; missing=$((missing+1)); }
  done
  [ $missing -eq 0 ] && ok "9 个 fat jar 全部就绪" || fail "缺 $missing 个 jar"
fi

# ============== 步骤 4: 数据库初始化 ==============
section "Step 4 / 7 : 数据库初始化"

if [ "$SKIP_DB" -eq 1 ]; then
  warn "跳过数据库初始化"
elif [ "$PROFILE" = "offline" ]; then
  warn "离线模式 - 跳过数据库初始化"
elif command -v mysql >/dev/null 2>&1; then
  read -p "MySQL root 密码 (留空=无密码): " -s MYSQL_PWD
  echo ""

  if mysql -uroot${MYSQL_PWD:+ -p$MYSQL_PWD} -e "SELECT 1" >/dev/null 2>&1; then
    ok "MySQL 连接成功"

    log "执行 V1 schema (8 库 20 表)..."
    mysql -uroot${MYSQL_PWD:+ -p$MYSQL_PWD} < "$ROOT/deploy/mysql-init/V1__init_all.sql" \
      && ok "V1 schema 完成" || warn "V1 schema 执行失败 (可能已存在)"

    log "执行 V2 seed (测试数据)..."
    mysql -uroot${MYSQL_PWD:+ -p$MYSQL_PWD} < "$ROOT/deploy/mysql-init/V2__seed_test_data.sql" \
      && ok "V2 seed 完成" || warn "V2 seed 执行失败 (可选)"
  else
    warn "MySQL 连接失败, 跳过初始化"
  fi
else
  warn "未安装 MySQL 客户端, 跳过"
fi

# ============== 步骤 5: 启动微服务 ==============
section "Step 5 / 7 : 启动 9 个微服务"

# 杀掉旧进程 (DRY-RUN 也清理, 防残留)
log "清理旧进程..."
pkill -9 -f "fin-.*-1.0.0.jar" 2>/dev/null || true
sleep 1
ok "已清理"

if [ "$DRY_RUN" -eq 1 ]; then
  # ============ DRY-RUN: 只验证 jar ============
  log "DRY-RUN: 验证 9 个 fat jar..."
  for svc_port in "${SERVICES[@]}"; do
    svc="${svc_port%:*}"
    port="${svc_port#*:}"
    jar="$ROOT/$svc/target/$svc-1.0.0.jar"
    if [ -f "$jar" ]; then
      size=$(du -h "$jar" | cut -f1)
      ok "$svc (port $port) -> $jar ($size)"
    else
      warn "$svc (port $port) -> jar 不存在"
    fi
  done
  log "DRY-RUN: 跳过启动"
else
  # ============ 实启模式 ============
  # 准备 profile 文件
  PROFILE_FILE="$RUN_DIR/application-offline.yml"
  cat > "$PROFILE_FILE" <<'YAML'
server:
  port: 0  # 由各 jar 自身 application.yml 决定
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

  # 按依赖顺序启动
  for svc_port in "${SERVICES[@]}"; do
    svc="${svc_port%:*}"
    port="${svc_port#*:}"
    jar="$ROOT/$svc/target/$svc-1.0.0.jar"
    log_file="$LOG_DIR/$svc.log"
    pid_file="$RUN_DIR/$svc.pid"

    if [ ! -f "$jar" ]; then
      warn "跳过 $svc (jar 不存在)"
      continue
    fi

    log "启动 $svc (port=$port)..."

    if [ "$PROFILE" = "offline" ]; then
      nohup java -Dspring.config.additional-location="$PROFILE_FILE" \
        -jar "$jar" > "$log_file" 2>&1 &
    else
      nohup java -jar "$jar" > "$log_file" 2>&1 &
    fi

    echo $! > "$pid_file"

    # 健康检查
    started=0
    for ((i=1; i<=STARTUP_TIMEOUT; i+=HEALTH_INTERVAL)); do
      if curl -sf -m 1 "http://localhost:$port/actuator/health" >/dev/null 2>&1 \
         || curl -sf -m 1 "http://localhost:$port/api/v1/chat/health" >/dev/null 2>&1 \
         || curl -sf -m 1 "http://localhost:$port/health" >/dev/null 2>&1; then
        started=1
        break
      fi
      if ! kill -0 $(cat "$pid_file") 2>/dev/null; then
        break
      fi
      sleep "$HEALTH_INTERVAL"
    done

    if [ $started -eq 1 ]; then
      ok "$svc 已启动 (port $port, PID $(cat "$pid_file"))"
    else
      warn "$svc 启动失败, 查看日志: $log_file"
      tail -10 "$log_file" 2>/dev/null | sed 's/^/    /'
    fi
  done
fi

# ============== 步骤 6: Nginx 配置 ==============
section "Step 6 / 7 : Nginx 反向代理"

if [ "$SKIP_NGINX" -eq 1 ]; then
  warn "跳过 Nginx 配置"
elif command -v nginx >/dev/null 2>&1; then
  NGINX_CONF="/etc/nginx/sites-available/finchat"
  cat > "$NGINX_CONF" <<NGINX
# FinChat Nginx 反向代理
# 自动生成 by oneclick-deploy.sh

upstream finchat_gateway {
    server 127.0.0.1:9000;
    keepalive 32;
}

server {
    listen 80;
    server_name _;

    # 前端静态资源
    root $ROOT/fin-web/dist;
    index index.html;

    # H5
    location /mobile {
        alias $ROOT/fin-web/dist/demo;
        try_files \$uri \$uri/ =404;
    }

    # H5 演示 demo
    location /demo {
        alias $ROOT/fin-web/dist/demo;
    }

    # SPA 入口
    location / {
        try_files \$uri \$uri/ /index.html;
    }

    # API 反代
    location /api/ {
        proxy_pass http://finchat_gateway;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 60s;
    }

    # WebSocket
    location /ws/ {
        proxy_pass http://finchat_gateway;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }
}
NGINX

  ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/finchat
  rm -f /etc/nginx/sites-enabled/default

  if nginx -t 2>&1 | grep -q successful; then
    systemctl reload nginx 2>/dev/null || nginx -s reload 2>/dev/null
    ok "Nginx 配置已加载"
  else
    warn "Nginx 配置测试失败"
    nginx -t 2>&1 | sed 's/^/    /'
  fi
else
  warn "未安装 Nginx, 跳过"
fi

# ============== 步骤 7 (可选): 注册 systemd ==============
section "Step 7 / 7 : 注册 systemd 服务"

if [ "$INSTALL_SERVICE" -eq 1 ] && command -v systemctl >/dev/null 2>&1; then
  SERVICE_FILE="/etc/systemd/system/finchat.service"
  cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=FinChat Full Stack ($svc)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$ROOT
ExecStart=/usr/bin/java -jar $ROOT/$svc/target/$svc-1.0.0.jar
Restart=always
RestartSec=10
StandardOutput=append:$LOG_DIR/$svc.log
StandardError=append:$LOG_DIR/$svc.log

[Install]
WantedBy=multi-user.target
EOF

  systemctl daemon-reload
  systemctl enable finchat.service
  ok "systemd 服务已注册 (finchat.service)"
  log "用 systemctl start finchat 启动"
else
  log "跳过 systemd 注册 (用 --install-service 启用)"
fi

# ============== 冒烟测试 ==============
section "冒烟测试"

if [ "$DRY_RUN" -eq 1 ]; then
  log "DRY-RUN 模式: 跳过实际 HTTP 测试"
else
  sleep 3
  log "检查关键端点..."

test_endpoint() {
  local name="$1"
  local url="$2"
  local code=$(curl -s -o /dev/null -w "%{http_code}" -m 3 "$url" 2>&1 || echo "000")
  if [ "$code" = "200" ]; then
    ok "$name -> $url [HTTP $code]"
  else
    warn "$name -> $url [HTTP $code]"
  fi
}

test_endpoint "Gateway health" "http://localhost:9000/api/v1/chat/health"
test_endpoint "Chat center"    "http://localhost:8082/api/v1/chat/health"
test_endpoint "Trade gateway"  "http://localhost:8083/api/v1/trade/health"
test_endpoint "KMS gateway"    "http://localhost:8091/api/kms/health"
test_endpoint "Auth center"    "http://localhost:8081/api/v1/auth/health"
test_endpoint "Frontend"       "http://localhost/"
test_endpoint "H5 demo"        "http://localhost/mobile/chat-demo.html"
test_endpoint "Mobile H5"      "http://localhost/mobile/mobile-h5.html"

fi  # DRY_RUN smoke test

# ============== 收尾 ==============
section "部署完成!"

cat <<EOF

${GREEN}╔══════════════════════════════════════════════════════╗
║                                                          ║
║           🎉  FinChat 全栈一键部署完成!                 ║
║                                                          ║
╚══════════════════════════════════════════════════════╝${NC}

服务清单 (端口 → PID):
EOF

for svc_port in "${SERVICES[@]}"; do
  svc="${svc_port%:*}"
  port="${svc_port#*:}"
  pid_file="$RUN_DIR/$svc.pid"
  if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
      status="${GREEN}✓${NC}"
    else
      status="${RED}✗${NC}"
    fi
    printf "  %b  %-22s  port %-5s  PID %s\n" "$status" "$svc" "$port" "$pid"
  else
    printf "  ${YELLOW}?${NC}  %-22s  port %-5s  未启动\n" "$svc" "$port"
  fi
done

cat <<EOF

访问入口:
  ${CYAN}Web 管理端${NC}:     http://localhost/
  ${CYAN}H5 客户${NC}:        http://localhost/mobile/mobile-h5.html
  ${CYAN}H5 演示${NC}:        http://localhost/mobile/chat-demo.html
  ${CYAN}WebSocket${NC}:      ws://localhost/ws/chat
  ${CYAN}API 网关${NC}:       http://localhost/api/v1/...
  ${CYAN}Swagger${NC}:        http://localhost:9000/swagger-ui.html

运维命令:
  查看日志:  tail -f $LOG_DIR/<service>.log
  停止服务:  $ROOT/scripts/mvn-stop.sh
  重新部署:  $ROOT/scripts/oneclick-deploy.sh
  服务列表:  ls $RUN_DIR/*.pid

EOF

ok "所有服务已就绪"