# 合规清单与监管对接 (compliance-center + audit-center)

> **合规基线**：等保 2.0 三级 + 个人信息保护法 + 金融销售留痕 + 国密合规 + 监管报送
> **目标**：自查可过审, 监管可对接, 事故可追溯

---

## 1. 等保 2.0 三级 (GB/T 22239-2019)

### 1.1 安全物理环境
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 1.1 | 物理位置选择 | 机房在国境内 | ✅ 国内机房 (阿里云/华为云金融专区) |
| 1.2 | 物理访问控制 | 7×24 门禁+监控 | 第三方 IDC 提供 |
| 1.3 | 防雷击防火 | 满足 GB 50057 | 第三方 IDC 提供 |
| 1.4 | 温湿度控制 | 温度 18-27°C, 湿度 40-60% | 第三方 IDC 提供 |
| 1.5 | 防水防潮 | 防水检测+排水 | 第三方 IDC 提供 |
| 1.6 | 防静电 | 接地+防静电地板 | 第三方 IDC 提供 |
| 1.7 | 电磁防护 | 电磁屏蔽 | 第三方 IDC 提供 |
| 1.8 | 电力供应 | UPS + 发电机, ≥ 4h | 第三方 IDC 提供 |

### 1.2 安全通信网络
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 2.1 | 网络架构 | 分区: DMZ/内网/工控 | VPC + 子网划分 |
| 2.2 | 通信传输 | 敏感信息加密 (TLS 1.2+) | ✅ 全站 HTTPS / WSS |
| 2.3 | 可信验证 | mTLS 双向证书 | ✅ 内网 mTLS |
| 2.4 | 入侵防范 | IPS / WAF | ✅ 云 WAF + 自研规则 |
| 2.5 | 恶意代码防范 | 防病毒+统一管理 | ✅ 镜像层 + 主机层 |

### 1.3 安全区域边界
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 3.1 | 边界防护 | 防火墙+ACL | ✅ 安全组 |
| 3.2 | 访问控制 | 默认拒绝+白名单 | ✅ 默认 deny |
| 3.3 | 入侵防范 | 异常流量检测 | ✅ 自研 + 商业 |
| 3.4 | 恶意代码防范 | 网关层 + 主机层 | ✅ WAF |
| 3.5 | 安全审计 | 边界日志集中 | ✅ audit-center |

### 1.4 安全计算环境 — 身份鉴别
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 4.1.1 | 身份标识唯一 | userId 全局唯一 | ✅ 雪花算法 |
| 4.1.2 | 身份鉴别信息复杂度 | 8+ 字符, 多因子 | ✅ JWT + 短信 + 设备指纹 |
| 4.1.3 | 身份鉴别信息加密 | 不可逆加密存储 | ✅ SM3 哈希 |
| 4.1.4 | 鉴别失败处理 | 锁定 5 分钟 | ✅ `user:lock:*` Redis |
| 4.1.5 | 远程鉴别 | 双向认证 | ✅ mTLS |
| 4.1.6 | 重要业务二次鉴别 | 交易/密码重置 | ✅ SmsVerifyService |

### 1.5 安全计算环境 — 访问控制
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 4.2.1 | 自主访问控制 | ACL | ✅ RBAC + 数据级 ACL |
| 4.2.2 | 强制访问控制 | 安全标记 | ✅ 数据密级 + 用户级别 |
| 4.2.3 | 安全标记 | 主客体标记 | ✅ 字段级加密 |
| 4.2.4 | 访问控制策略 | 最小权限 | ✅ Spring Security |
| 4.2.5 | 特权用户管理 | 三权分立 | ✅ 安全/审计/系统管理员 |
| 4.2.6 | 访问控制粒度 | 主体/客体/操作 | ✅ 字段级 |

### 1.6 安全计算环境 — 安全审计
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 4.3.1 | 审计覆盖 | 覆盖所有用户 | ✅ 全链路 |
| 4.3.2 | 审计记录 | 事件类型/时间/主体/客体/结果 | ✅ audit-center 表 |
| 4.3.3 | 审计记录保护 | 防篡改 | ✅ 哈希链 + TSA |
| 4.3.4 | 审计进程保护 | 独立进程 | ✅ audit-center 单独服务 |
| 4.3.5 | 审计记录保留 | ≥ 6 个月 | ✅ 默认 10 年 |

### 1.7 安全计算环境 — 入侵防范
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 4.4.1 | 最小安装 | 仅装必要组件 | ✅ 镜像最小化 |
| 4.4.2 | 漏洞修补 | 30 天内修复高危 | ✅ Trivy + 灰度修复 |
| 4.4.3 | 入侵检测 | 异常行为检测 | ✅ 自研 + 商业 |
| 4.4.4 | 恶意代码防范 | 集中管控 | ✅ 镜像扫描 |

### 1.8 安全计算环境 — 数据完整性
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 5.1.1 | 鉴别数据完整性 | 哈希 + 数字签名 | ✅ SM3 + SM2 |
| 5.1.2 | 业务数据完整性 | CRC + 备份恢复 | ✅ MySQL binlog + 哈希链 |
| 5.1.3 | 个人数据完整性 | 校验 | ✅ SM3 |
| 5.1.4 | 重要数据传输完整性 | TLS + 哈希 | ✅ |

### 1.9 安全计算环境 — 数据保密性
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 5.2.1 | 鉴别数据保密性 | 加密存储 | ✅ SM3 + SM4 |
| 5.2.2 | 个人敏感信息保密性 | 加密存储+脱敏 | ✅ SM4 + DLP |
| 5.2.3 | 重要数据传输保密性 | TLS | ✅ WSS |

### 1.10 安全管理中心
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 6.1 | 系统管理 | 三权分立 | ✅ 角色: SYS_ADMIN / SEC_ADMIN / AUD_ADMIN |
| 6.2 | 审计管理 | 集中审计 | ✅ audit-center |
| 6.3 | 安全管理 | 安全策略集中 | ✅ Nacos 配置中心 |
| 6.4 | 集中管控 | 集中监测+告警 | ✅ observability |

### 1.11 安全运维
| # | 控制点 | 要求 | 实现 |
|---|--------|------|------|
| 7.1 | 环境管理 | 物理环境管理 | ✅ 第三方 IDC |
| 7.2 | 资产管理 | 资产清单 | ✅ CMDB |
| 7.3 | 漏洞管理 | 30 天内修复高危 | ✅ 漏洞扫描 + 修复 SOP |
| 7.4 | 网络安全管理 | 安全策略 | ✅ WAF + 安全组 |
| 7.5 | 恶意代码防范管理 | 集中防病毒 | ✅ 镜像 + 主机 |
| 7.6 | 配置管理 | 基线配置 | ✅ CIS 基线 |
| 7.7 | 密码管理 | 国密合规 | ✅ 全国密 |
| 7.8 | 变更管理 | 变更审批+测试 | ✅ 变更流程 |
| 7.9 | 备份与恢复 | 3-2-1 策略 | ✅ 同城 + 异地 |
| 7.10 | 安全事件处置 | 应急预案 | ✅ 7×24 响应 |
| 7.11 | 应急预案管理 | 演练 | ✅ 季度演练 |
| 7.12 | 外包运维管理 | 服务商评估 | ✅ |

---

## 2. 个人信息保护法 (PIPL)

### 2.1 核心义务清单
| # | 义务 | 要求 | 实现 |
|---|------|------|------|
| P1 | 告知 | 隐私政策清晰易懂 | ✅ 应用首次启动弹窗 |
| P2 | 同意义务 | 单独同意 | ✅ 实名/营销/共享 分开勾选 |
| P3 | 最小必要 | 不超范围采集 | ✅ DTO 白名单 |
| P4 | 准确性 | 数据准确 | ✅ 用户自助更正 |
| P5 | 安全义务 | 加密+访问控制 | ✅ SM4 + RBAC |
| P6 | 告知 | 处理目的/方式 | ✅ |
| P7 | 公开 | 处理规则 | ✅ 隐私政策 |
| P8 | 不得过度 | 不超范围 | ✅ |
| P9 | 共同处理 | 协议 | ✅ 供应商协议 |
| P10 | 委托处理 | 受托人义务 | ✅ 协议 + 审计 |
| P11 | 转移告知 | 共享告知 | ✅ 共享清单 |
| P12 | 公开告知 | 公开处理 | ✅ |
| P13 | 跨境传输 | 安全评估 | ⚠ 仅国内 |
| P14 | 删除义务 | 撤回同意后删除 | ✅ 一键注销 |
| P15 | 解释义务 | 算法透明 | ✅ |

### 2.2 敏感个人信息 (身份证/手机/银行账号/生物特征)
- ✅ **单独同意**: 注册时单独勾选"我同意处理敏感个人信息"
- ✅ **最小必要**: 仅交易时采集银行卡四要素, 不存 CVV
- ✅ **加密存储**: SM4 + 国密机托管密钥
- ✅ **传输加密**: WSS + 字段 SM4
- ✅ **访问审计**: 所有读取行为进 audit

### 2.3 个人权利实现
| 权利 | 路径 | 时效 |
|------|------|------|
| 查询 | APP → 我的 → 个人信息副本 | 15 天 |
| 更正 | APP → 我的 → 资料编辑 | 即时 |
| 删除 | APP → 设置 → 注销账户 | 30 天 (反洗钱期) |
| 撤回同意 | APP → 设置 → 隐私 | 即时 |
| 注销 | APP → 设置 → 注销 | 30 天 |

### 2.4 PIA (个人信息保护影响评估)
- ✅ 每年一次
- ✅ 业务重大变更时
- ✅ 上报监管 (金融行业要求)

---

## 3. 金融销售留痕 (银保监/证监)

### 3.1 银保监 (银保监会令 [2017] 8 号)
| # | 要求 | 实现 |
|---|------|------|
| F1 | 销售全过程录音录像 (双录) | ⚠ 视频双录 SDK (待对接) |
| F2 | 关键环节展示 | ✅ 风险揭示书 + 适当性匹配 |
| F3 | 风险揭示 | ✅ |
| F4 | 客户确认 | ✅ 电子签字 (国密签名) |
| F5 | 资料保存 | ✅ ≥ 5 年 |
| F6 | 可回溯 | ✅ 哈希链 + TSA |

### 3.2 证监会 (《证券期货投资者适当性管理办法》)
| # | 要求 | 实现 |
|---|------|------|
| Z1 | 投资者分类 | ✅ C1-C5 风险等级 |
| Z2 | 产品风险分级 | ✅ R1-R5 等级 |
| Z3 | 适当性匹配 | ✅ 矩阵自动校验 |
| Z4 | 双录 (高风险) | ⚠ 待对接 |
| Z5 | 风险测评 12 个月 | ✅ 强制重测 |
| Z6 | 冷静期 (私募) | ✅ 24h |
| Z7 | 资料保存 ≥ 20 年 | ✅ |
| Z8 | 监管报送 | ✅ 月报 |

### 3.3 反洗钱 (反洗钱法)
| # | 要求 | 实现 |
|---|------|------|
| A1 | 客户身份识别 (KYC) | ✅ 实名 + 活体 |
| A2 | 大额报告 (≥ 5 万) | ✅ 自动报送 |
| A3 | 可疑报告 | ✅ 风控规则 |
| A4 | 客户身份资料保存 ≥ 5 年 | ✅ |
| A5 | 交易记录保存 ≥ 5 年 | ✅ |
| A6 | 高风险客户强化尽调 (EDD) | ✅ |

---

## 4. 国密合规 (GM/T 系列)

### 4.1 算法合规
| 算法 | 国密标准 | 用途 | 实现 |
|------|----------|------|------|
| SM2 | GM/T 0003 | 公钥密码 (签名/加密/密钥交换) | ✅ 交易签名 |
| SM3 | GM/T 0004 | 杂凑算法 | ✅ 哈希/摘要 |
| SM4 | GM/T 0002 | 对称分组 (128 bit) | ✅ 字段加密 |
| SM9 | GM/T 0044 | 基于标识的密码 | 🔜 标识加密 |
| ZUC | GM/T 0001 | 流密码 | ✅ 通信加密 |

### 4.2 密码模块合规 (GM/T 0028)
- ✅ **物理安全**: FIPS 140-2 Level 3+
- ✅ **密钥管理**: 硬件生成/存储/销毁
- ✅ **角色鉴别**: 多用户 + 多因子
- ✅ **攻击防护**: 防侧信道/故障注入
- ✅ **运行环境**: 独立硬件

### 4.3 密钥生命周期
| 阶段 | 实现 |
|------|------|
| 生成 | 加密机内部生成, 永不出硬件 |
| 分发 | 国密 IPSec 通道 + 数字信封 |
| 存储 | 加密机内部 (SMK 加密 WLK) |
| 使用 | API 调用, 不返回明文 |
| 更新 | 季度自动轮换 |
| 备份 | 双机热备 (主备同步) |
| 销毁 | 物理粉碎 + 多重擦除 |
| 审计 | 全量调用日志 |

---

## 5. 监管报送接口

### 5.1 投资者适当性 (证监)
```yaml
# 月报报送 (T+5 工作日)
endpoint: https://www.csrc.gov.cn/api/suitability/monthly
format: JSON (GB/T 36344)
auth: 双向证书 + SM2 签名
```

### 5.2 反洗钱大额可疑 (人行)
```yaml
# 实时报送
endpoint: https://aml.pbcs.gov.cn/api/report
format: XML (人行规范)
auth: 专线 VPN + 国密 IPSec
```

### 5.3 双录数据 (银保监)
```yaml
# T+1 日
endpoint: https://report.cbirc.gov.cn/api/double-record
format: 视频文件 + 元数据 JSON
storage: 监管指定对象存储
```

### 5.4 报送实现
```java
// compliance-center/src/main/java/com/fin/compliance/report/ReportScheduler.java
@Component
@Slf4j
public class ReportScheduler {

    @Autowired private ReportService reportService;
    @Autowired private CsrcClient csrcClient;
    @Autowired private PbocClient pbocClient;
    @Autowired private CbircClient cbircClient;

    /** 月度适当性报告: 每月 5 日 03:00 */
    @Scheduled(cron = "0 0 3 5 * ?")
    public void monthlySuitabilityReport() {
        List<SuitabilityReport> reports = reportService.genMonthlySuitability();
        csrcClient.uploadMonthly(reports);
    }

    /** 反洗钱实时: 单笔 ≥ 5 万立即报 */
    @EventListener
    @Async
    public void onLargeTrade(LargeTradeEvent e) {
        pbocClient.reportLarge(e.getTrade());
    }

    /** 可疑交易: 风控触发即报 */
    @EventListener
    @Async
    public void onSuspicious(SuspiciousEvent e) {
        pbocClient.reportSuspicious(e.getTrade(), e.getReason());
    }
}
```

---

## 6. audit-center 留痕规范

### 6.1 必留痕事件
| 类别 | 事件 |
|------|------|
| 鉴权 | LOGIN / LOGOUT / TOKEN_REFRESH / LOGIN_FAIL |
| 会话 | CONVERSATION_CREATE / MSG_SEND / MSG_RECALL |
| 交易 | TRADE_INITIATE / TRADE_CONFIRM / TRADE_SUCCESS / TRADE_REJECT |
| 风控 | RISK_WARN / BLACKLIST_ADD / COOLING_APPLY |
| 加密 | KMS_CALL / KEY_ROTATE / SIGN_CREATE / SIGN_VERIFY |
| 数据 | PII_ACCESS / PII_MODIFY / PII_DELETE |
| 短信 | SMS_SEND / SMS_VERIFY / SMS_FAIL |
| 管理 | USER_CREATE / ROLE_GRANT / POLICY_CHANGE |

### 6.2 审计表结构
```sql
CREATE TABLE fin_audit_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id    VARCHAR(64) NOT NULL,
    user_id     BIGINT,
    user_role   VARCHAR(32),
    event_type  VARCHAR(64) NOT NULL,
    target_type VARCHAR(32),     -- USER / TRADE / MESSAGE / DEVICE
    target_id   VARCHAR(64),
    action      VARCHAR(32),     -- CREATE / READ / UPDATE / DELETE
    payload     JSON,            -- 脱敏后参数
    result      VARCHAR(16),     -- SUCCESS / FAIL / REJECT
    error_code  VARCHAR(64),
    ip          VARCHAR(64),
    ua          VARCHAR(512),
    device_id   VARCHAR(128),
    request_id  VARCHAR(64),
    session_id  VARCHAR(64),
    prev_hash   CHAR(64),        -- 上一条审计 hash (链)
    curr_hash   CHAR(64),        -- SM3(全部字段)
    merkle_root CHAR(64),
    tsa_sign    TEXT,
    ts          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_trace (trace_id),
    INDEX idx_user_time (user_id, ts),
    INDEX idx_event (event_type, ts),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB CHARSET=utf8mb4;
```

### 6.3 监管查询接口
```java
// audit-center/src/main/java/com/fin/audit/controller/RegulatorQueryController.java
@RestController
@RequestMapping("/api/audit/regulator")
@PreAuthorize("hasRole('REGULATOR') or hasRole('COMPLIANCE_OFFICER')")
public class RegulatorQueryController {

    /** 客户全量行为 (反洗钱调查) */
    @GetMapping("/user/{userId}/timeline")
    public UserTimeline getUserTimeline(
        @PathVariable Long userId,
        @RequestParam @DateTimeFormat(pattern="yyyy-MM-dd") Date start,
        @RequestParam @DateTimeFormat(pattern="yyyy-MM-dd") Date end) {
        // 返回: 登录 + 交易 + 聊天 + 风控 + 设备
        return auditService.timeline(userId, start, end);
    }

    /** 哈希链验证 (篡改自证) */
    @GetMapping("/chain/verify/{traceId}")
    public ChainVerifyReport verify(@PathVariable String traceId) {
        return auditService.verifyChain(traceId);
    }

    /** 监管证据导出 */
    @PostMapping("/export")
    public ExportJob export(@Valid @RequestBody ExportRequest req) {
        // 异步导出加密 zip + 国密签名
        return exportService.startExport(req);
    }
}
```

---

## 7. 自查清单 (上线前)

### 7.1 技术合规自查
- [ ] 等保三级测评报告 (由测评机构出具)
- [ ] 密码应用安全性评估报告 (GM/T 0054)
- [ ] 个人信息保护影响评估 (PIA)
- [ ] 渗透测试报告 (无高危漏洞)
- [ ] 漏洞扫描报告 (无中危以上)
- [ ] 双活/灾备演练报告 (RPO/RTO 达标)
- [ ] 数据备份恢复演练报告 (季度)
- [ ] 安全应急演练报告 (半年)

### 7.2 业务合规自查
- [ ] 金融业务许可证 (银保监/证监会)
- [ ] 客户隐私政策 (法务审核)
- [ ] 用户协议 (法务审核)
- [ ] 风险揭示书模板 (合规审核)
- [ ] 适当性管理细则 (合规审核)
- [ ] 反洗钱管理办法 (合规审核)
- [ ] 投诉处理流程 (客服审核)
- [ ] 监管报送接口联调报告 (技术+合规)

### 7.3 运维合规自查
- [ ] 三权分立账号清单 (系统/安全/审计管理员)
- [ ] 变更管理流程 (季度审计)
- [ ] 事件响应 SOP (半年演练)
- [ ] 备份与恢复策略 (RPO 5min, RTO 30min)
- [ ] 密钥管理台账 (含轮换/销毁记录)
- [ ] 第三方供应商安全评估
- [ ] 员工保密协议 (全员)
- [ ] 安全培训记录 (季度)

---

## 8. 合规组织与流程

```
                    ┌────────────────────────────┐
                    │   信息科技治理委员会        │
                    │   (高管层, 季度会议)        │
                    └─────────────┬──────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
        ┌──────────┐        ┌──────────┐        ┌──────────┐
        │ 技术委员会│        │ 业务委员会│        │ 风险合规委员会│
        │ (CTO)   │        │ (COO)    │        │ (CRO)    │
        └──────────┘        └──────────┘        └──────────┘
              │                   │                   │
              └───────────────────┴───────────────────┘
                                  │
                                  ▼
                    ┌────────────────────────────┐
                    │      安全合规执行团队        │
                    │  · CISO (首席信息安全官)    │
                    │  · 合规经理                 │
                    │  · 数据保护官 (DPO)         │
                    │  · 安全工程师               │
                    │  · 审计员                   │
                    └────────────────────────────┘
```

### 8.1 角色与职责
| 角色 | 职责 |
|------|------|
| **CISO** | 安全战略, 安全事件总指挥 |
| **DPO** | 个人信息保护合规, 监管对接 |
| **合规经理** | 业务合规审核, 报送执行 |
| **审计员** | 审计日志管理, 监管调查配合 |
| **安全工程师** | 技术安全实施, 漏洞修复 |
| **三权分立** | 系统管理员 / 安全管理员 / 审计管理员 互斥 |

### 8.2 变更流程
```
需求 ──► 合规预审 ──► 安全评审 ──► 技术评审 ──► 测试 ──► 灰度 ──► 上线
   │         │            │           │          │        │       │
   │         │            │           │          │        │       │
   ▼         ▼            ▼           ▼          ▼        ▼       ▼
 法务    适当性    安全方案    代码审计    自动化    监控    审计留痕
 审核    影响评估  风险评估    SAST/DAST  测试    告警     + 通知
```

---

## 9. 应急响应预案

### 9.1 事件分级
| 等级 | 描述 | 响应时效 |
|------|------|----------|
| P0 | 大规模数据泄露 | 15 分钟 |
| P1 | 关键服务不可用 | 30 分钟 |
| P2 | 局部功能异常 | 2 小时 |
| P3 | 性能问题 | 8 小时 |
| P4 | 咨询问题 | 24 小时 |

### 9.2 响应流程
```
发现 ──► 研判 ──► 通报 ──► 处置 ──► 恢复 ──► 复盘
  │        │        │        │        │        │
  │        │        │        │        │        ▼
  │        │        │        │        │      监管报告
  │        │        │        │        │       (P0/P1)
  │        │        │        │        ▼
  │        │        │        │     客户通知
  │        │        │        │      (P0)
  │        │        │        ▼
  │        │        │     根因修复
  │        │        ▼
  │        │     内部通报 + 上报
  │        ▼
  │     升级研判
  ▼
 自动告警 + 人工确认
```

### 9.3 监管报告义务
- **网络安全法**: 重大事件 24h 内
- **个保法**: 数据泄露立即告知 + 报告
- **金融行业**: 重大风险事件 24h 报告

---

## 10. 监管对接清单

| 监管 | 接口 | 频率 | 责任人 |
|------|------|------|--------|
| 证监会 | 适当性月报 | 月 | 合规经理 |
| 证监会 | 异常交易实时 | 实时 | 风控 |
| 人行 | 反洗钱大额 | 实时 | 反洗钱专员 |
| 人行 | 反洗钱可疑 | 实时 | 反洗钱专员 |
| 银保监 | 双录数据 | 日 | 业务+技术 |
| 网信办 | 个人信息影响评估 | 年 | DPO |
| 公安部 | 网络安全等级保护 | 年 | CISO |
| 工信部 | ICP / 增值电信 | 年 | 运维 |
