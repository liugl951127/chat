# 聊天与会话回溯 (chat-center)

> **端口**：8082 | **传输**：WSS (WebSocket Secure) | **存储**：MySQL 热 + MongoDB 冷 + ES 检索
> **核心目标**：实时聊天 + 历史回溯 + 不可篡改留痕 + 5 年留存

---

## 1. 总体架构

```
                          ┌─────────────────────────┐
       Vue 客户端 ──────► │  WebSocket Gateway       │
       (STOMP over WSS)   │  (chat-center:8082)      │
                          │  · 连接鉴权 (JWT)         │
                          │  · 限流 (每秒 20 条)      │
                          │  · 消息分发 (点对点/群)    │
                          └────────┬────────────────┘
                                   │
                ┌──────────────────┼──────────────────┐
                ▼                  ▼                  ▼
        ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
        │  Kafka       │   │  MongoDB     │   │  Elastic     │
        │  (异步消费)  │   │  (会话分片)  │   │  (历史检索)  │
        │  · 留痕      │   │  · 5 年留存   │   │  · 全文 + 标签│
        │  · 风控扫描  │   │  · 字段级加密 │   │              │
        │  · 联网核查  │   └──────────────┘   └──────────────┘
        └──────┬───────┘
               ▼
        ┌──────────────┐
        │ audit-center │
        │ 哈希链存证    │
        └──────────────┘
```

## 2. 消息协议 (WebSocket)

### 2.1 STOMP 帧定义
```
客户端 → 服务端:
  SEND /app/chat/{conversationId}
  Headers: Authorization: Bearer <jwt>
  Body: {
    "type": "TEXT",                 // TEXT / IMAGE / FILE / TRADE_PROPOSAL / TRADE_RESULT
    "content": "买入 100 股 平安银行",
    "clientMsgId": "uuid",          // 幂等
    "mentioned": ["advisor_001"],
    "ext": { "clientTs": 1719000000000 }
  }

服务端 → 客户端:
  MESSAGE /user/{userId}/queue/chat
  Body: {
    "msgId": "M-2025-06-21-00001",
    "conversationId": "C-...",
    "sender": "user_123",
    "type": "TEXT",
    "content": "买入 100 股 平安银行",
    "ts": 1719000000000,
    "hash": "a3f2...",               // 内容哈希 (Merkle 链入)
    "prevHash": "5d8a...",
    "auditProof": "..."              // 存证证明
  }
```

### 2.2 Java 消息体
```java
// chat-center/src/main/java/com/fin/chat/protocol/ChatMessage.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {
    private String clientMsgId;       // 客户端幂等键
    private String msgId;             // 服务端分配
    private String conversationId;
    private String senderId;
    private MessageType type;         // TEXT / IMAGE / FILE / TRADE_PROPOSAL / TRADE_RESULT / SYSTEM
    private String content;           // 已 DLP 脱敏
    private String contentEnc;        // SM4 加密密文 (落库用)
    private List<String> mentioned;
    private Map<String, Object> ext;
    private Long clientTs;
    private Long serverTs;
    private String hash;               // SHA-256(content)
    private String prevHash;           // 上一条消息 hash (链式)
    private String auditProof;         // 存证回执
    private AuditFlag auditFlag;       // NORMAL / DLP_MASKED / REFER_VERIFIED / RISK_WARN
}
```

## 3. 数据库设计

### 3.1 会话主档 (MySQL 热)
```sql
CREATE TABLE fin_conversation (
    id              VARCHAR(32) PRIMARY KEY,   -- C-{yyyyMMdd}-{seq}
    type            VARCHAR(16) COMMENT 'P2P / GROUP / ADVISOR',
    title           VARCHAR(128),
    user_id         BIGINT NOT NULL,
    advisor_id      BIGINT COMMENT '客户经理 ID',
    status          TINYINT DEFAULT 1 COMMENT '1=进行 2=结束 3=归档',
    last_msg_id     VARCHAR(32),
    last_msg_at     DATETIME(3),
    msg_count       INT DEFAULT 0,
    tags            JSON COMMENT '标签: ["产品咨询","风险测评"]',
    created_at      DATETIME(3),
    updated_at      DATETIME(3),
    INDEX idx_user (user_id, status, last_msg_at),
    INDEX idx_advisor (advisor_id, last_msg_at)
) ENGINE=InnoDB CHARSET=utf8mb4;
```

### 3.2 消息明细 (MongoDB 分片)
```javascript
// MongoDB chat-center.messages
db.messages.createIndex({ conversationId: 1, serverTs: 1 })
db.messages.createIndex({ userId: 1, serverTs: -1 })
db.messages.createIndex({ "ext.productCode": 1 })  // 交易消息按产品检索

// 文档结构
{
  _id: ObjectId,
  msgId: "M-2025-06-21-00001",
  conversationId: "C-20250621-0001",
  userId: 12345,
  senderId: "user_12345",
  senderType: "CUSTOMER",     // CUSTOMER / ADVISOR / SYSTEM / AI_BOT
  type: "TEXT",
  contentEnc: BinData(0, "..."),  // SM4 密文
  contentHash: "a3f2...",        // SHA-256 (用于链式验证)
  prevHash: "5d8a...",
  mentioned: ["advisor_001"],
  ext: {
    clientTs: 1719000000000,
    serverTs: 1719000000123,
    auditProof: "...",
    dlpHits: ["MOBILE", "ID_CARD"],   // 命中规则
    verifyRefs: ["ENT_xxx", "CREDIT_xxx"]  // 联网核查引用
  },
  retentionUntil: ISODate("2030-06-21"),  // 5 年到期
  archiveStatus: "HOT"  // HOT / WARM / COLD
}
```

### 3.3 哈希链存证表
```sql
CREATE TABLE fin_message_hash_chain (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id VARCHAR(32),
    msg_id          VARCHAR(32),
    msg_hash        CHAR(64) NOT NULL COMMENT 'SHA-256(content)',
    prev_hash       CHAR(64),
    block_hash      CHAR(64) NOT NULL COMMENT 'SHA-256(msgHash + prevHash + ts)',
    merkle_root     CHAR(64) COMMENT '每 100 条聚一棵 Merkle',
    tsa_sign        TEXT COMMENT '第三方时间戳签名',
    created_at      DATETIME(3),
    UNIQUE KEY uk_msg (msg_id),
    INDEX idx_conv (conversation_id, id)
) ENGINE=InnoDB CHARSET=utf8mb4;
```

## 4. 核心 Java 代码

### 4.1 WebSocket 网关
```java
// chat-center/src/main/java/com/fin/chat/ws/StompWebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 客户经理订阅自己的队列
        config.enableSimpleBroker("/queue/", "/topic/")
              .setTaskScheduler(heartBeatScheduler())
              .setHeartbeatValue(new long[]{10000, 10000});  // 10s 心跳
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user/");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("https://*.example.com")  // 严控跨域
                .withSockJS();
        // 生产直接用 WSS, 不需要 SockJS, 这里保留兼容
    }

    @Bean public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(4);
        s.setThreadNamePrefix("ws-heartbeat-");
        return s;
    }
}
```

### 4.2 消息处理器 (含 DLP + 哈希链 + 存证)
```java
// chat-center/src/main/java/com/fin/chat/handler/ChatMessageHandler.java
@Controller
@Slf4j
public class ChatMessageHandler {

    @Autowired private MongoTemplate mongo;
    @Autowired private KafkaTemplate<String, ChatEvent> kafka;
    @Autowired private DlpService dlpService;
    @Autowired private VerifyEngine verifyEngine;
    @Autowired private HashChainService hashChainService;
    @Autowired private AuditService auditService;
    @Autowired private RateLimiter rateLimiter;

    @MessageMapping("/chat/{conversationId}")
    public void onMessage(@DestinationVariable String conversationId,
                          Principal principal, ChatMessage msg) {
        Long userId = Long.parseLong(principal.getName());

        // 1. 限流 (每秒 20 条)
        if (!rateLimiter.tryAcquire("chat:" + userId, 20, 1, TimeUnit.SECONDS)) {
            throw new RateLimitException("消息频率过高");
        }

        // 2. 幂等 (clientMsgId 维度)
        if (mongo.exists(query(where("clientMsgId").is(msg.getClientMsgId())), Message.class)) {
            log.info("duplicate message ignored: {}", msg.getClientMsgId());
            return;
        }

        // 3. DLP 扫描 + 脱敏
        DlpResult dlp = dlpService.scanAndMask(msg.getContent());
        msg.setContent(dlp.getMasked());
        msg.setExt(Map.of("dlpHits", dlp.getHits()));

        // 4. 联网核查 (如触发)
        if (dlp.isNeedVerify()) {
            VerifyResult vr = verifyEngine.verify(dlp.getHits(), msg.getContent());
            msg.getExt().put("verifyRefs", vr.getReferences());
        }

        // 5. 内容哈希 + 链式
        String hash = hashChainService.nextHash(conversationId, msg.getContent());

        // 6. SM4 加密落库
        String enc = kmsClient.sm4Encrypt(msg.getContent());

        // 7. 写 MongoDB
        msg.setContentEnc(enc);
        msg.setHash(hash);
        msg.setServerTs(System.currentTimeMillis());
        msg.setMsgId("M-" + LocalDate.now() + "-" + seq.nextId());
        mongo.save(toMessageDoc(msg));

        // 8. 异步事件 (留痕 + 风控 + 推送)
        kafka.send("chat-events", ChatEvent.of(msg));

        // 9. 推给对方
        messagingTemplate.convertAndSendToUser(
            msg.getReceiverId(), "/queue/chat", msg
        );
    }
}
```

### 4.3 哈希链服务 (Merkle)
```java
// chat-center/src/main/java/com/fin/chat/integrity/HashChainService.java
@Service
@Slf4j
public class HashChainService {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private KmsGatewayClient kmsClient;
    @Autowired private TsaClient tsaClient;     // 第三方时间戳

    private static final int BATCH_SIZE = 100;
    private final List<String> batchHashes = new CopyOnWriteArrayList<>();
    private final AtomicInteger batchCounter = new AtomicInteger();

    public String nextHash(String conversationId, String content) {
        // 1. 上一条 hash
        String prevHash = jdbc.queryForObject(
            "SELECT msg_hash FROM fin_message_hash_chain " +
            "WHERE conversation_id = ? ORDER BY id DESC LIMIT 1",
            String.class, conversationId);

        // 2. 当前 hash
        String ts = String.valueOf(System.currentTimeMillis());
        String current = sha256(content + "|" + (prevHash == null ? "GENESIS" : prevHash) + "|" + ts);

        // 3. 入库 (异步批量)
        batchHashes.add(current);
        int idx = batchCounter.incrementAndGet();

        if (idx % BATCH_SIZE == 0) {
            flushBatch(conversationId);
        }

        return current;
    }

    @Scheduled(fixedRate = 30_000)
    public void flushBatch(String conversationId) {
        // 每 100 条聚一棵 Merkle, 送 TSA 签时间戳
        String merkleRoot = MerkleTree.build(batchHashes).getRoot();
        String tsa = tsaClient.timestamp(merkleRoot);  // 第三方权威时间戳

        jdbc.update(
            "INSERT INTO fin_message_hash_chain " +
            "(conversation_id, msg_hash, prev_hash, block_hash, merkle_root, tsa_sign) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            conversationId, batchHashes.get(batchHashes.size()-1),
            null, sha256(merkleRoot + tsa), merkleRoot, tsa
        );
        batchHashes.clear();
    }
}
```

## 5. 历史回溯 API

### 5.1 分页查询 (带权限)
```java
// chat-center/src/main/java/com/fin/chat/controller/ChatHistoryController.java
@RestController
@RequestMapping("/api/chat/history")
public class ChatHistoryController {

    @Autowired private MongoTemplate mongo;
    @Autowired private PermissionService perm;

    @GetMapping("/{conversationId}")
    @PreAuthorize("@perm.canRead(#userId, #conversationId)")
    public PageResult<MessageVO> history(
        @PathVariable String conversationId,
        @RequestParam(required = false) String cursor,    // 上一页最后 msgId
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword,
        @RequestHeader("X-User-Id") Long userId
    ) {
        Query q = new Query();
        q.addCriteria(where("conversationId").is(conversationId));

        // 游标分页 (避免 OFFSET 性能问题)
        if (cursor != null) {
            q.addCriteria(where("_id").lt(new ObjectId(cursor)));
        }

        // 关键词 (ES 检索, 这里给 MongoDB 正则兜底)
        if (StringUtils.isNotBlank(keyword)) {
            q.addCriteria(where("contentEnc").regex(Pattern.quote(keyword)));
        }

        q.with(Sort.by(Sort.Direction.DESC, "_id")).limit(size);

        List<Message> msgs = mongo.find(q, Message.class);
        // 解密 (用 SM4 密钥, 仅本人 + 合规员 + 客服主管可见)
        List<MessageVO> vos = msgs.stream().map(this::decryptFor).toList();
        return PageResult.of(vos, vos.size() == size ? vos.get(vos.size()-1).getId() : null);
    }

    @GetMapping("/verify/{conversationId}")
    @PreAuthorize("hasRole('COMPLIANCE')")
    public IntegrityReport verifyChain(@PathVariable String conversationId) {
        // 监管合规员调用, 验证哈希链完整性
        return hashChainService.verify(conversationId);
    }
}
```

## 6. Vue 3 聊天界面

### 6.1 聊天组件
```vue
<!-- web/src/views/chat/ChatWindow.vue -->
<template>
  <div class="chat-window">
    <div class="header">
      <span class="title">{{ conversation.title }}</span>
      <el-button @click="onHistory" size="small">历史回溯</el-button>
    </div>

    <div ref="messageList" class="messages">
      <div v-for="m in messages" :key="m.msgId"
           :class="['msg', m.senderId === currentUserId ? 'mine' : 'other']">
        <div class="bubble">
          <span v-if="m.type === 'TEXT'">{{ m.content }}</span>
          <img v-else-if="m.type === 'IMAGE'" :src="m.content" />
          <TradeProposal v-else-if="m.type === 'TRADE_PROPOSAL'" :data="m.ext" />
          <div v-if="m.ext?.verifyRefs?.length" class="verify-refs">
            <el-tag v-for="r in m.ext.verifyRefs" :key="r.id" size="small">
              {{ r.source }}: {{ r.summary }}
            </el-tag>
          </div>
        </div>
        <div class="meta">
          <span class="time">{{ formatTime(m.serverTs) }}</span>
          <el-tooltip v-if="m.auditProof" content="已存证" placement="top">
            <el-icon><CircleCheck /></el-icon>
          </el-tooltip>
        </div>
      </div>
    </div>

    <div class="composer">
      <el-input v-model="draft" type="textarea" :rows="3"
                placeholder="请输入消息..." @keydown.ctrl.enter="send" />
      <el-button type="primary" @click="send" :loading="sending">发送</el-button>
    </div>

    <HistoryDrawer v-model:visible="showHistory" :conversation-id="conversation.id" />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { Client } from '@stomp/stompjs'
import { fetchHistory } from '@/api/chat'
import HistoryDrawer from './HistoryDrawer.vue'

const props = defineProps({ conversation: Object })
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const showHistory = ref(false)
const messageList = ref()
const currentUserId = +localStorage.getItem('user_id')

let stomp = null

onMounted(async () => {
  // 1. 加载历史 (近 30 条)
  const { data } = await fetchHistory(props.conversation.id, { size: 30 })
  messages.value = data.records.reverse()

  // 2. 建立 WebSocket
  stomp = new Client({
    brokerURL: `wss://${location.host}/ws/chat`,
    connectHeaders: { Authorization: `Bearer ${localStorage.getItem('access_token')}` },
    onConnect: () => {
      stomp.subscribe(`/user/queue/chat`, (frame) => {
        const m = JSON.parse(frame.body)
        if (m.conversationId === props.conversation.id) {
          messages.value.push(m)
          scrollToBottom()
        }
      })
    }
  })
  stomp.activate()
})

const send = async () => {
  if (!draft.value.trim()) return
  sending.value = true
  const payload = {
    type: 'TEXT',
    content: draft.value,
    clientMsgId: crypto.randomUUID(),
  }
  stomp.publish({
    destination: `/app/chat/${props.conversation.id}`,
    body: JSON.stringify(payload),
  })
  draft.value = ''
  sending.value = false
}

const scrollToBottom = async () => {
  await nextTick()
  messageList.value?.scrollTo({ top: messageList.value.scrollHeight })
}
</script>

<style scoped lang="scss">
.chat-window { display: flex; flex-direction: column; height: 100vh; }
.messages { flex: 1; overflow-y: auto; padding: 16px; }
.msg { display: flex; margin: 8px 0; &.mine { justify-content: flex-end; .bubble { background: #95d3ff; } } }
.bubble { max-width: 60%; padding: 8px 12px; border-radius: 8px; background: #f3f3f3; }
.verify-refs { margin-top: 4px; .el-tag { margin-right: 4px; } }
.composer { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #eee; }
</style>
```

### 6.2 历史回溯抽屉
```vue
<!-- web/src/views/chat/HistoryDrawer.vue -->
<template>
  <el-drawer v-model="visible" title="历史回溯" size="600px" direction="rtl">
    <div class="history">
      <el-date-picker v-model="dateRange" type="daterange"
                      @change="onDateChange" />
      <el-input v-model="keyword" placeholder="搜索关键词"
                @keyup.enter="onSearch" clearable />
      <el-button @click="onSearch">搜索</el-button>

      <el-timeline>
        <el-timeline-item v-for="m in messages" :key="m.msgId"
                          :timestamp="formatTime(m.serverTs)">
          <div :class="['msg', isDlpHit(m) && 'dlp-hit']">
            {{ m.content }}
            <div v-if="m.verifyRefs" class="verify-refs">
              <el-tag v-for="r in m.verifyRefs" :key="r.id" type="success">
                {{ r.source }}: {{ r.summary }}
              </el-tag>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>

      <el-pagination v-model:current-page="page"
                     :total="total" :page-size="20"
                     @current-change="loadMore" />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { searchHistory } from '@/api/chat'

const props = defineProps({ conversationId: String })
const visible = defineModel('visible')
const messages = ref([])
const keyword = ref('')
const dateRange = ref([])
const page = ref(1)
const total = ref(0)

watch(visible, (v) => { if (v) loadMore() })

const loadMore = async () => {
  const { data } = await searchHistory({
    conversationId: props.conversationId,
    keyword: keyword.value,
    start: dateRange.value?.[0],
    end: dateRange.value?.[1],
    page: page.value,
    size: 20,
  })
  messages.value.push(...data.records)
  total.value = data.total
}
</script>

<style scoped>
.history { padding: 16px; }
.dlp-hit { background: #fff7e6; padding: 4px 8px; border-left: 3px solid #faad14; }
.verify-refs { margin-top: 4px; }
</style>
```

## 7. 联网核查接入 (摘要)

完整方案见 [03-online-verification.md](./03-online-verification.md)。聊天中触发场景：

| 触发 | 核查源 | 响应 |
|------|--------|------|
| 提及企业名称 | 国家企业信用信息公示 | 工商基础信息 |
| 提及股票代码 | Wind/同花顺 | 实时行情 |
| 提及身份证 | 公安二要素 | 实名核验 |
| 提及银行账号 | 银联 | 账户校验 |
| 提及新闻事件 | 舆情 API | 风险标签 |
| 提及竞品产品 | 自有产品库 | 适当性匹配 |

## 8. 合规清单

| # | 要求 | 实现 |
|---|------|------|
| 1 | 销售留痕 | 哈希链 + TSA 时间戳 |
| 2 | 5 年留存 | MongoDB TTL + 冷归档 |
| 3 | 全程加密 | WebSocket WSS + 字段 SM4 |
| 4 | 敏感信息脱敏 | DLP 实时扫描 |
| 5 | 监管可查 | `/api/chat/verify/{id}` 接口 |
| 6 | 反欺诈 | 设备/IP/频次 三维风控 |
| 7 | 数据主权 | 国内部署 + 等保三级机房 |
