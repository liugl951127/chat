# 关键代码骨架 — 团队可直接上手

> 6 个核心模块的最小可运行骨架，每个文件都能直接落地。

---

## 1. 工程结构

```
fin-chat/
├── fin-commons/                  # 公共 (响应/异常/上下文)
├── fin-api/                      # DTO/VO/枚举
├── fin-storage/                  # MyBatis-Plus + 多租户
├── fin-auth-center/              # ★ 多端登录 (8081)
├── fin-chat-center/              # ★ 聊天 + 回溯 (8082)
├── fin-trade-gateway/            # ★ 交易网关 (8083)
├── fin-compliance-center/        # ★ 合规引擎 (8090)
├── fin-kms-gateway/              # ★ 加密机代理 (8091)
├── fin-notify-center/            # 短信/推送 (8092)
├── fin-audit-center/             # ★ 审计存证 (8093)
├── fin-observability/            # 监控告警
├── fin-gateway/                  # Spring Cloud Gateway (9000)
├── fin-bootstrap/                # 统一启动器
└── fin-web/                      # ★ Vue 3 管理后台
```

---

## 2. 父 POM 关键依赖

```xml
<!-- pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>

<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <hutool.version>5.8.27</hutool.version>
    <nacos.version>2022.0.0.0</nacos.version>
</properties>

<modules>
    <module>fin-commons</module>
    <module>fin-api</module>
    <module>fin-storage</module>
    <module>fin-auth-center</module>
    <module>fin-chat-center</module>
    <module>fin-trade-gateway</module>
    <module>fin-compliance-center</module>
    <module>fin-kms-gateway</module>
    <module>fin-notify-center</module>
    <module>fin-audit-center</module>
    <module>fin-observability</module>
    <module>fin-gateway</module>
    <module>fin-bootstrap</module>
</modules>
```

---

## 3. fin-commons 通用响应

```java
// fin-commons/src/main/java/com/fin/commons/resp/ApiResponse.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;          // 0 = 成功, 其他 = 错误码
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
            .code(0).message("OK").data(data)
            .traceId(TraceContext.get())
            .build();
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return ApiResponse.<T>builder()
            .code(code).message(message)
            .traceId(TraceContext.get())
            .build();
    }
}
```

```java
// fin-commons/src/main/java/com/fin/commons/exception/BizException.java
@Getter
public class BizException extends RuntimeException {
    private final int code;
    private final String bizCode;

    public BizException(String bizCode, String message) {
        super(message);
        this.code = 4000;
        this.bizCode = bizCode;
    }

    public BizException(int code, String bizCode, String message) {
        super(message);
        this.code = code;
        this.bizCode = bizCode;
    }
}
```

```java
// fin-commons/src/main/java/com/fin/commons/exception/GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> biz(BizException e) {
        log.warn("biz exception: code={}, msg={}", e.getBizCode(), e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> valid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ":" + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ApiResponse.fail(4001, msg);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> other(Exception e) {
        log.error("internal error", e);
        return ApiResponse.fail(5000, "内部错误");
    }
}
```

---

## 4. fin-gateway 网关关键配置

```yaml
# fin-gateway/src/main/resources/application.yml
server:
  port: 9000

spring:
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        namespace: fin-prod
    gateway:
      routes:
        # 多端登录
        - id: auth
          uri: lb://fin-auth-center
          predicates:
            - Path=/api/v1/auth/**
        # 聊天
        - id: chat
          uri: lb://fin-chat-center
          predicates:
            - Path=/api/v1/chat/**,/api/v1/conversation/**
        # 交易
        - id: trade
          uri: lb://fin-trade-gateway
          predicates:
            - Path=/api/v1/trade/**
        # 合规
        - id: compliance
          uri: lb://fin-compliance-center
          predicates:
            - Path=/api/v1/compliance/**
        # 审计
        - id: audit
          uri: lb://fin-audit-center
          predicates:
            - Path=/api/v1/audit/**
        # WebSocket
        - id: ws
          uri: lb://fin-chat-center
          predicates:
            - Path=/ws/**
```

```java
// fin-gateway/src/main/java/com/fin/gateway/filter/JwtAuthFilter.java
@Component
@Slf4j
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Autowired private JwtVerifier jwtVerifier;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();

        // 1. 白名单放行
        if (isWhitelist(path)) return chain.filter(exchange);

        // 2. 提取 JWT
        String token = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || !token.startsWith("Bearer ")) {
            return reject(exchange, "missing_token");
        }
        token = token.substring(7);

        // 3. 验签 (公钥来自 KMS, 这里从 Nacos 配置中心取)
        Claims claims;
        try {
            claims = jwtVerifier.verify(token);
        } catch (Exception e) {
            return reject(exchange, "invalid_token");
        }

        // 4. 注入用户信息到下游
        ServerHttpRequest mutated = req.mutate()
            .header("X-User-Id", claims.getSubject())
            .header("X-User-Roles", String.join(",", claims.get("roles", List.class)))
            .header("X-Trace-Id", TraceContext.getOrCreate())
            .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isWhitelist(String path) {
        return path.startsWith("/api/v1/auth/login")
            || path.startsWith("/api/v1/auth/sms")
            || path.startsWith("/api/v1/auth/wx/")
            || path.startsWith("/ws/")
            || path.startsWith("/actuator/")
            || path.startsWith("/api/v1/audit/regulator/");
    }

    @Override public int getOrder() { return -100; }
}
```

---

## 5. fin-auth-center 启动器

```java
// fin-auth-center/src/main/java/com/fin/auth/AuthCenterApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.fin.auth",
    "com.fin.commons",
    "com.fin.storage",
    "com.fin.api"
})
@EnableDiscoveryClient
@EnableJpaAuditing
@MapperScan("com.fin.storage.mapper")
public class AuthCenterApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthCenterApplication.class, args);
    }
}
```

```yaml
# application.yml
server:
  port: 8081

spring:
  application:
    name: fin-auth-center
  datasource:
    url: jdbc:mysql://mysql:3306/fin_auth?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USER:fin}
    password: ${DB_PASS:fin123}
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        namespace: fin-prod

fin:
  wx:
    mini:
      appid: ${WX_MINI_APPID}
      secret: ${WX_MINI_SECRET}
  jwt:
    issuer: fin-auth
  kms:
    host: ${KMS_HOST:127.0.0.1}
    port: ${KMS_PORT:8000}
```

---

## 6. fin-chat-center WebSocket

```java
// fin-chat-center/src/main/java/com/fin/chat/ws/StompConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue/", "/topic/")
              .setHeartbeatValue(new long[]{10000, 10000});
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user/");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("https://*.example.com");
    }
}
```

```java
// fin-chat-center/src/main/java/com/fin/chat/ws/AuthHandshakeInterceptor.java
@Component
@Slf4j
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired private JwtVerifier jwtVerifier;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims c = jwtVerifier.verify(token);
            attributes.put("userId", Long.parseLong(c.getSubject()));
            attributes.put("deviceId", c.get("device", String.class));
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override public void afterHandshake(ServerHttpRequest r, ServerHttpResponse re,
                                          WebSocketHandler ws, Exception ex) {}
}
```

---

## 7. fin-trade-gateway 关键拦截器

```java
// fin-trade-gateway/src/main/java/com/fin/trade/interceptor/TradeAuditInterceptor.java
@Component
@Slf4j
public class TradeAuditInterceptor implements HandlerInterceptor {

    @Autowired private AuditService auditService;
    @Autowired private KmsGatewayClient kmsClient;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        // 1. 准备审计上下文
        long start = System.currentTimeMillis();
        req.setAttribute("trade.start", start);

        // 2. 调前置
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp,
                                Object handler, Exception ex) {
        long cost = System.currentTimeMillis() - (long) req.getAttribute("trade.start");

        // 3. 异步留痕 (不进主流程)
        Long userId = Long.parseLong(req.getHeader("X-User-Id"));
        String traceId = req.getHeader("X-Trace-Id");

        AuditEvent event = AuditEvent.builder()
            .traceId(traceId)
            .userId(userId)
            .eventType("TRADE_HTTP")
            .action(req.getMethod())
            .targetType(req.getRequestURI())
            .payload(Map.of(
                "uri", req.getRequestURI(),
                "ip", req.getRemoteAddr(),
                "ua", req.getHeader("User-Agent"),
                "device", req.getHeader("X-Device-Id"),
                "userAgentHash", kmsClient.sm3Hash(req.getHeader("User-Agent") == null ? "" : req.getHeader("User-Agent"))
            ))
            .result(resp.getStatus() < 400 ? "SUCCESS" : "FAIL")
            .status(resp.getStatus())
            .costMs(cost)
            .ts(Instant.now())
            .build();

        auditService.asyncRecord(event);
    }
}
```

---

## 8. fin-kms-gateway 配置

```yaml
# application.yml
fin:
  kms:
    host: ${KMS_HOST:127.0.0.1}
    port: ${KMS_PORT:8000}
    app-id: fin-app-001
    app-key: ${KMS_APP_KEY}
    pool-size: 8
    timeout-ms: 3000
    # 软算法 fallback, 仅 dev/sandbox 用
    soft-fallback:
      enabled: ${KMS_SOFT:false}
```

```java
// fin-kms-gateway/src/main/java/com/fin/kms/config/KmsAutoConfig.java
@Configuration
@EnableConfigurationProperties(KmsProperties.class)
public class KmsAutoConfig {

    @Bean
    @ConditionalOnProperty(name = "fin.kms.soft-fallback.enabled", havingValue = "true")
    public KmsGatewayClient softKmsClient() {
        log.warn("!!! Using SOFT KMS client, only for dev/sandbox !!!");
        return new SoftKmsClient();
    }

    @Bean
    @ConditionalOnProperty(name = "fin.kms.soft-fallback.enabled", havingValue = "false", matchIfMissing = true)
    public KmsGatewayClient hsmKmsClient(KmsProperties props) {
        return new Sjl03KmsClient(props);
    }
}
```

---

## 9. Vue 3 工程结构

```
fin-web/
├── src/
│   ├── api/
│   │   ├── auth.ts          # 登录 API
│   │   ├── chat.ts          # 聊天 API
│   │   ├── trade.ts         # 交易 API
│   │   └── audit.ts         # 审计查询
│   ├── composables/
│   │   ├── useAuth.ts       # 鉴权 Hook
│   │   ├── useWebSocket.ts  # WS Hook
│   │   └── useTrace.ts      # 链路追踪
│   ├── views/
│   │   ├── chat/
│   │   │   ├── ChatWindow.vue
│   │   │   ├── HistoryDrawer.vue
│   │   │   └── VerifyRefsPanel.vue
│   │   ├── trade/
│   │   │   └── TradeConfirmDialog.vue
│   │   ├── audit/
│   │   │   └── AuditQuery.vue
│   │   └── compliance/
│   │       └── ReportList.vue
│   ├── router/
│   │   └── index.ts
│   ├── stores/              # Pinia
│   │   ├── auth.ts
│   │   └── chat.ts
│   ├── utils/
│   │   ├── crypto.ts        # 客户端国密 (可选)
│   │   └── dlp.ts           # 客户端预脱敏
│   ├── App.vue
│   └── main.ts
├── package.json
├── vite.config.ts
└── tsconfig.json
```

```json
// package.json
{
  "name": "fin-web",
  "version": "1.0.0",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.3.0",
    "pinia": "^2.1.0",
    "axios": "^1.6.0",
    "@stomp/stompjs": "^7.0.0",
    "element-plus": "^2.5.0",
    "@element-plus/icons-vue": "^2.3.0",
    "sm-crypto": "^0.3.0",
    "echarts": "^5.4.0"
  }
}
```

```typescript
// vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:9000',
        changeOrigin: true,
        ws: true
      }
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'echarts': ['echarts'],
          'sm-crypto': ['sm-crypto']
        }
      }
    }
  }
})
```

```typescript
// src/api/auth.ts
import axios from './request'

export interface LoginResp {
  userId: number
  accessToken: string
  refreshToken: string
  expiresIn: number
  realNameStatus: number
  riskLevel: number
}

export const loginByCode = (data: {
  code: string
  encryptedData?: string
  iv?: string
  deviceId: string
}) => axios.post<LoginResp>('/api/v1/auth/wx/mini/login', data)

export const loginByMobile = (data: {
  mobile: string
  smsCode: string
  deviceId: string
}) => axios.post<LoginResp>('/api/v1/auth/mobile/login', data)

export const refreshToken = (rt: string) =>
  axios.post<{ accessToken: string }>('/api/v1/auth/refresh', { refreshToken: rt })

export const logout = () =>
  axios.post('/api/v1/auth/logout')
```

```typescript
// src/api/request.ts (axios 拦截器)
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuth } from '@/composables/useAuth'

const instance = axios.create({
  baseURL: '/',
  timeout: 15_000,
})

instance.interceptors.request.use((cfg: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('access_token')
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  cfg.headers['X-Trace-Id'] = crypto.randomUUID()
  return cfg
})

instance.interceptors.response.use(
  (resp) => resp.data,
  async (error: AxiosError<any>) => {
    const { response, config } = error
    if (response?.status === 401 && !config?.headers?.['_retry']) {
      config.headers['_retry'] = '1'
      try {
        const { silentRefresh } = useAuth()
        const newToken = await silentRefresh()
        config.headers.Authorization = `Bearer ${newToken}`
        return instance(config)
      } catch {
        // 刷新失败 → 跳登录
        localStorage.clear()
        location.href = '/login'
      }
    }
    ElMessage.error(response?.data?.message || '请求失败')
    return Promise.reject(error)
  }
)

export default instance
```

```typescript
// src/composables/useWebSocket.ts
import { ref, onUnmounted } from 'vue'
import { Client, IMessage } from '@stomp/stompjs'

export function useWebSocket(onMessage: (msg: any) => void) {
  const connected = ref(false)
  let client: Client | null = null

  const connect = () => {
    client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/chat`,
      connectHeaders: {
        Authorization: `Bearer ${localStorage.getItem('access_token')}`
      },
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        connected.value = true
        client!.subscribe('/user/queue/chat', (m: IMessage) => {
          onMessage(JSON.parse(m.body))
        })
      },
      onDisconnect: () => { connected.value = false },
      onStompError: (frame) => {
        console.error('STOMP error', frame.headers['message'])
      }
    })
    client.activate()
  }

  const send = (destination: string, body: any) => {
    client?.publish({ destination, body: JSON.stringify(body) })
  }

  const disconnect = () => client?.deactivate()

  onUnmounted(disconnect)

  return { connected, connect, send, disconnect }
}
```

---

## 10. 数据库初始化脚本 (MySQL)

```sql
-- V1.0.0__init.sql
CREATE DATABASE IF NOT EXISTS fin_auth DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS fin_chat DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS fin_trade DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS fin_compliance DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS fin_audit DEFAULT CHARSET utf8mb4;

-- fin_auth
USE fin_auth;
CREATE TABLE fin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    unionid VARCHAR(64) UNIQUE,
    wecom_userid VARCHAR(64) UNIQUE,
    mobile_hash CHAR(64),
    mobile_enc VARBINARY(256),
    id_card_hash CHAR(64),
    real_name_enc VARBINARY(256),
    real_name_status TINYINT DEFAULT 0,
    risk_level TINYINT DEFAULT 1,
    status TINYINT DEFAULT 1,
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_unionid (unionid),
    INDEX idx_mobile_hash (mobile_hash)
) ENGINE=InnoDB;

-- fin_audit
USE fin_audit;
CREATE TABLE fin_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL,
    user_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),
    target_id VARCHAR(64),
    action VARCHAR(32),
    payload JSON,
    result VARCHAR(16),
    ip VARCHAR(64),
    device_id VARCHAR(128),
    prev_hash CHAR(64),
    curr_hash CHAR(64),
    merkle_root CHAR(64),
    tsa_sign TEXT,
    ts DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_trace (trace_id),
    INDEX idx_user_time (user_id, ts),
    INDEX idx_event (event_type, ts)
) ENGINE=InnoDB;
```

---

## 11. Docker Compose (开发环境)

```yaml
# deploy/docker-compose.yml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
    ports: ["3306:3306"]
    volumes:
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  nacos:
    image: nacos/nacos-server:v2.3.2
    environment:
      MODE: standalone
    ports: ["8848:8848", "9848:9848"]

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENERS: PLAINTEXT://:9092
    depends_on: [zookeeper]

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0

  mongo:
    image: mongo:6
    ports: ["27017:27017"]

  auth-center:
    build: ../fin-auth-center
    ports: ["8081:8081"]
    depends_on: [mysql, redis, nacos]

  chat-center:
    build: ../fin-chat-center
    ports: ["8082:8082"]
    depends_on: [mysql, redis, kafka, mongo, nacos]

  trade-gateway:
    build: ../fin-trade-gateway
    ports: ["8083:8083"]
    depends_on: [mysql, redis, nacos]

  compliance-center:
    build: ../fin-compliance-center
    ports: ["8090:8090"]
    depends_on: [mysql, redis, nacos]

  kms-gateway:
    build: ../fin-kms-gateway
    ports: ["8091:8091"]

  audit-center:
    build: ../fin-audit-center
    ports: ["8093:8093"]
    depends_on: [mysql, redis]

  gateway:
    build: ../fin-gateway
    ports: ["9000:9000"]
    depends_on: [auth-center, chat-center, trade-gateway, audit-center]

  web:
    build: ../fin-web
    ports: ["8080:80"]

volumes:
  mysql_data:
```

---

## 12. 启动顺序与验证

```bash
# 1. 基础服务
docker-compose up -d mysql redis nacos kafka mongo

# 2. 启动应用 (分批, 避免 OOM)
docker-compose up -d auth-center chat-center trade-gateway

# 3. 启动合规和审计
docker-compose up -d compliance-center audit-center kms-gateway

# 4. 启动网关 + Web
docker-compose up -d gateway web

# 5. 健康检查
curl http://localhost:9000/actuator/health
curl http://localhost:8081/api/v1/auth/health
```

---

## 13. 关键 API 速查

### 多端登录
```
POST /api/v1/auth/wx/mini/login     {code, encryptedData?, iv?, deviceId}
POST /api/v1/auth/wx/h5/login        {code, state, deviceId}
POST /api/v1/auth/wecom/login        {code, deviceId}
POST /api/v1/auth/mobile/login       {mobile, smsCode, deviceId}
POST /api/v1/auth/sms/send           {mobile, bizType}
POST /api/v1/auth/refresh            {refreshToken}
POST /api/v1/auth/logout
```

### 聊天
```
GET  /api/v1/conversation/list
POST /api/v1/conversation/create     {advisorId, title}
GET  /api/v1/chat/history/{convId}   ?cursor=&size=&keyword=
GET  /api/v1/chat/verify/{convId}
WS   /ws/chat                        STOMP
```

### 联网核查
```
POST /api/v1/chat/verify             {text}  # 同步核查
GET  /api/v1/chat/verify/refs/{msgId}
```

### 交易
```
POST /api/v1/trade/initiate          {productCode, bizType, amount, account?}
POST /api/v1/trade/confirm           {tradeId, smsCode}
GET  /api/v1/trade/list              ?status=&productCode=
GET  /api/v1/trade/{id}
```

### 合规
```
POST /api/v1/compliance/risk-test    {answers: []}
GET  /api/v1/compliance/risk-level
GET  /api/v1/compliance/appropriate/{productCode}
POST /api/v1/compliance/report/monthly
```

### 审计 (合规员)
```
GET  /api/v1/audit/regulator/user/{userId}/timeline?start=&end=
GET  /api/v1/audit/regulator/chain/verify/{traceId}
POST /api/v1/audit/regulator/export
```

---

## 14. 后续工作

| 阶段 | 周 | 任务 |
|------|----|------|
| P1 | W1 | 工程脚手架 + 6 模块骨架 |
| P2 | W2 | auth-center 5 端登录 + JWT |
| P3 | W3 | chat-center WebSocket + 历史回溯 + 哈希链 |
| P4 | W4 | 联网核查 + 12 个 Provider |
| P5 | W5 | trade-gateway + 短信 + 国密签名 |
| P6 | W6 | compliance-center + 适当性 + 风控 |
| P7 | W7 | audit-center + TSA + 监管接口 |
| P8 | W8 | Vue 3 全端 (Web/小程序) |
| P9 | W9 | 等保测评整改 |
| P10 | W10 | UAT + 上线 |
