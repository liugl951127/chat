-- ============================================================
-- fin-chat 全库 schema (MySQL 8.0)
-- 8 个分库: auth / chat / trade / compliance / kms / notify / audit / observability
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. fin_auth 用户主档
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_auth DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_auth;

DROP TABLE IF EXISTS fin_user;
CREATE TABLE fin_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    unionid         VARCHAR(64) COMMENT '微信 unionid',
    wecom_userid    VARCHAR(64) COMMENT '企微 userid',
    mobile_hash     CHAR(64) COMMENT 'SM3(手机号)',
    mobile_enc      VARBINARY(256) COMMENT 'SM4(手机号)',
    id_card_hash    CHAR(64) COMMENT 'SM3(身份证)',
    real_name_enc   VARBINARY(256) COMMENT 'SM4(姓名)',
    real_name_status TINYINT DEFAULT 0 COMMENT '0=未 1=弱 2=强',
    risk_level      TINYINT DEFAULT 1 COMMENT 'C1-C5',
    status          TINYINT DEFAULT 1 COMMENT '0=注销 1=正常 2=冻结',
    nickname        VARCHAR(128),
    avatar          VARCHAR(512),
    last_login_at   DATETIME(3),
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_unionid (unionid),
    INDEX idx_wecom (wecom_userid),
    INDEX idx_mobile_hash (mobile_hash),
    INDEX idx_status_risk (status, risk_level)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_user_device;
CREATE TABLE fin_user_device (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    device_id   VARCHAR(128) NOT NULL,
    device_type VARCHAR(32) NOT NULL COMMENT 'WX_H5/WX_MINI/WECOM/IOS/ANDROID/WEB',
    first_seen  DATETIME(3),
    last_seen   DATETIME(3),
    login_count INT DEFAULT 0,
    trusted     TINYINT DEFAULT 0,
    UNIQUE KEY uk_user_device (user_id, device_id, device_type),
    INDEX idx_user (user_id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_login_log;
CREATE TABLE fin_login_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT,
    channel     VARCHAR(32),
    openid      VARCHAR(64),
    device_id   VARCHAR(128),
    ip          VARCHAR(64),
    success     TINYINT,
    fail_reason VARCHAR(64),
    trace_id    VARCHAR(64),
    created_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_trace (trace_id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_consent_log;
CREATE TABLE fin_consent_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    consent_type VARCHAR(32) COMMENT 'PRIVACY/SERVICE/SENSITIVE_PII/MARKETING',
    agreed      TINYINT,
    ip          VARCHAR(64),
    ua          VARCHAR(512),
    agreed_at   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_user (user_id)
) ENGINE=InnoDB;

-- ============================================================
-- 2. fin_chat 会话与消息
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_chat DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_chat;

DROP TABLE IF EXISTS fin_conversation;
CREATE TABLE fin_conversation (
    id              VARCHAR(32) PRIMARY KEY,
    type            VARCHAR(16) COMMENT 'P2P/GROUP/ADVISOR',
    title           VARCHAR(128),
    user_id         BIGINT NOT NULL,
    advisor_id      BIGINT,
    status          TINYINT DEFAULT 1,
    last_msg_id     VARCHAR(32),
    last_msg_at     DATETIME(3),
    msg_count       INT DEFAULT 0,
    tags            JSON,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_user (user_id, status, last_msg_at),
    INDEX idx_advisor (advisor_id, last_msg_at)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_message;
CREATE TABLE fin_message (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    msg_id          VARCHAR(32) NOT NULL,
    conversation_id VARCHAR(32) NOT NULL,
    user_id         BIGINT,
    sender_id       BIGINT,
    sender_type     VARCHAR(16) COMMENT 'CUSTOMER/ADVISOR/SYSTEM',
    msg_type        VARCHAR(16) COMMENT 'TEXT/IMAGE/FILE/TRADE_PROPOSAL',
    content_enc     VARBINARY(4096) COMMENT 'SM4 密文',
    content_hash    CHAR(64) NOT NULL COMMENT 'SHA-256',
    prev_hash       CHAR(64),
    merkle_root     CHAR(64),
    tsa_sign        TEXT,
    ext             JSON,
    server_ts       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id, server_ts),
    UNIQUE KEY uk_msg_id (msg_id, server_ts),
    INDEX idx_conv_ts (conversation_id, server_ts),
    INDEX idx_user_ts (user_id, server_ts)
) ENGINE=InnoDB
  PARTITION BY RANGE (TO_DAYS(server_ts)) (
    PARTITION p2024 VALUES LESS THAN (TO_DAYS('2025-01-01')),
    PARTITION p2025 VALUES LESS THAN (TO_DAYS('2026-01-01')),
    PARTITION p2026 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION pmax VALUES LESS THAN MAXVALUE
  );

DROP TABLE IF EXISTS fin_message_hash_chain;
CREATE TABLE fin_message_hash_chain (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id VARCHAR(32) NOT NULL,
    block_no        INT NOT NULL COMMENT '每 100 条一个 block',
    msg_count       INT NOT NULL,
    merkle_root     CHAR(64) NOT NULL,
    prev_block_hash CHAR(64),
    block_hash      CHAR(64) NOT NULL,
    tsa_sign        TEXT,
    sealed_at       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_conv_block (conversation_id, block_no),
    INDEX idx_conv (conversation_id)
) ENGINE=InnoDB;

-- ============================================================
-- 3. fin_trade 交易
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_trade DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_trade;

DROP TABLE IF EXISTS fin_product;
CREATE TABLE fin_product (
    code            VARCHAR(32) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    risk_level      VARCHAR(8) NOT NULL COMMENT 'R1-R5',
    category        VARCHAR(32) COMMENT 'FUND/STOCK/BOND/...',
    nav             DECIMAL(18,6),
    min_amount      DECIMAL(18,4) DEFAULT 0,
    status          TINYINT DEFAULT 1,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

INSERT INTO fin_product (code, name, risk_level, category, nav) VALUES
  ('FUND001', '稳健理财·货币基金', 'R1', 'FUND', 1.023400),
  ('FUND002', '平衡配置·混合基金', 'R3', 'FUND', 2.145600),
  ('STOCK001', '沪深 300 ETF', 'R4', 'STOCK', 3.987600),
  ('BOND001', '国债逆回购 7 天', 'R2', 'BOND', 1.000000);

DROP TABLE IF EXISTS fin_trade;
CREATE TABLE fin_trade (
    id              VARCHAR(32) PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    product_code    VARCHAR(32),
    biz_type        VARCHAR(16),
    amount          DECIMAL(18,4),
    quantity        DECIMAL(18,4),
    price           DECIMAL(18,6),
    account_hash    CHAR(64),
    device_id       VARCHAR(128),
    risk_decision   JSON,
    sms_verified    TINYINT,
    canonical_text  TEXT,
    digest          CHAR(64),
    signature       TEXT,
    public_key      TEXT,
    tsa_sign        TEXT,
    core_serial     VARCHAR(64),
    core_status     VARCHAR(16),
    status          VARCHAR(16),
    failure_reason  VARCHAR(256),
    trace_id        VARCHAR(64),
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_status (status, created_at),
    INDEX idx_trace (trace_id)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_sms_log;
CREATE TABLE fin_sms_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT,
    mobile_hash     CHAR(64),
    biz_type        VARCHAR(32),
    channel         VARCHAR(16),
    channel_trace   VARCHAR(64),
    success         TINYINT,
    error_code      VARCHAR(64),
    ip              VARCHAR(64),
    device_id       VARCHAR(128),
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB;

-- ============================================================
-- 4. fin_compliance 合规与风险测评
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_compliance DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_compliance;

DROP TABLE IF EXISTS fin_risk_test;
CREATE TABLE fin_risk_test (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    answers     JSON,
    score       INT,
    level       VARCHAR(8),
    tested_at   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    expire_at   DATETIME(3),
    INDEX idx_user (user_id, expire_at)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_blacklist;
CREATE TABLE fin_blacklist (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type VARCHAR(16) COMMENT 'USER/DEVICE/IP',
    target_value VARCHAR(128),
    reason      VARCHAR(256),
    added_by    BIGINT,
    created_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_target (target_type, target_value)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_report_job;
CREATE TABLE fin_report_job (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_type VARCHAR(32) COMMENT 'SUITABILITY_MONTHLY/AML_DAILY',
    status      VARCHAR(16),
    period      VARCHAR(16),
    payload     JSON,
    result_url  VARCHAR(512),
    created_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3)
) ENGINE=InnoDB;

-- ============================================================
-- 5. fin_kms 加密机元数据 (密钥索引)
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_kms DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_kms;

DROP TABLE IF EXISTS fin_key_meta;
CREATE TABLE fin_key_meta (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    key_alias       VARCHAR(128) UNIQUE NOT NULL,
    algorithm       VARCHAR(16) NOT NULL COMMENT 'SM2/SM4',
    key_usage       VARCHAR(16) NOT NULL COMMENT 'SIGN/ENC/JWT',
    business_id     VARCHAR(64),
    hsm_index       VARCHAR(64) COMMENT '加密机内部索引',
    status          TINYINT DEFAULT 1 COMMENT '1=启用 2=禁用 3=过期',
    expire_at       DATETIME(3),
    rotated_at      DATETIME(3),
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_business (business_id),
    INDEX idx_status_expire (status, expire_at)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_kms_audit;
CREATE TABLE fin_kms_audit (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    trace_id        VARCHAR(64),
    operation       VARCHAR(32) COMMENT 'SM3/SM4_ENC/SM2_SIGN/KEY_GEN',
    key_alias       VARCHAR(128),
    user_id         BIGINT,
    caller_ip       VARCHAR(64),
    success         TINYINT,
    duration_ms     INT,
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_trace (trace_id),
    INDEX idx_time (created_at)
) ENGINE=InnoDB;

-- ============================================================
-- 6. fin_notify 通知日志
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_notify DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_notify;

DROP TABLE IF EXISTS fin_sms_send_log;
CREATE TABLE fin_sms_send_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT,
    mobile_hash     CHAR(64),
    biz_type        VARCHAR(32),
    template_code   VARCHAR(32),
    channel         VARCHAR(16) COMMENT 'ALIYUN/TENCENT/HUAWEI',
    channel_resp    TEXT,
    success         TINYINT,
    error_code      VARCHAR(64),
    cost            INT COMMENT '条数',
    created_at      DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB;

-- ============================================================
-- 7. fin_audit 审计存证 (核心, 永久保留)
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_audit DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_audit;

DROP TABLE IF EXISTS fin_audit_log;
CREATE TABLE fin_audit_log (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    trace_id        VARCHAR(64) NOT NULL,
    user_id         BIGINT,
    user_role       VARCHAR(32),
    event_type      VARCHAR(64) NOT NULL,
    target_type     VARCHAR(32),
    target_id       VARCHAR(64),
    action          VARCHAR(32),
    payload         JSON,
    result          VARCHAR(16),
    error_code      VARCHAR(64),
    ip              VARCHAR(64),
    ua              VARCHAR(512),
    device_id       VARCHAR(128),
    request_id      VARCHAR(64),
    session_id      VARCHAR(64),
    prev_hash       CHAR(64),
    curr_hash       CHAR(64),
    merkle_root     CHAR(64),
    tsa_sign        TEXT,
    ts              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id, ts),
    INDEX idx_trace (trace_id),
    INDEX idx_user_time (user_id, ts),
    INDEX idx_event (event_type, ts),
    INDEX idx_target (target_type, target_id)
) ENGINE=InnoDB
  PARTITION BY RANGE (TO_DAYS(ts)) (
    PARTITION p2024 VALUES LESS THAN (TO_DAYS('2025-01-01')),
    PARTITION p2025 VALUES LESS THAN (TO_DAYS('2026-01-01')),
    PARTITION p2026 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION pmax VALUES LESS THAN MAXVALUE
  );

DROP TABLE IF EXISTS fin_audit_block;
CREATE TABLE fin_audit_block (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    block_no        INT NOT NULL,
    event_count     INT NOT NULL,
    merkle_root     CHAR(64) NOT NULL,
    prev_block_hash CHAR(64),
    block_hash      CHAR(64) NOT NULL,
    tsa_sign        TEXT,
    sealed_at       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_block (block_no)
) ENGINE=InnoDB;

-- ============================================================
-- 8. fin_observability 监控指标
-- ============================================================
CREATE DATABASE IF NOT EXISTS fin_observability DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fin_observability;

DROP TABLE IF EXISTS fin_alert_rule;
CREATE TABLE fin_alert_rule (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(128),
    metric      VARCHAR(64),
    operator    VARCHAR(8) COMMENT '>/</>=/<=',
    threshold   DECIMAL(18,4),
    severity    VARCHAR(16) COMMENT 'P0/P1/P2/P3',
    channels    VARCHAR(128) COMMENT 'WEBHOOK/EMAIL/SMS',
    enabled     TINYINT DEFAULT 1,
    created_at  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS fin_alert_history;
CREATE TABLE fin_alert_history (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id     BIGINT,
    metric      VARCHAR(64),
    value       DECIMAL(18,4),
    severity    VARCHAR(16),
    status      VARCHAR(16) COMMENT 'FIRING/RESOLVED',
    notified    TINYINT,
    fired_at    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3),
    INDEX idx_time (fired_at)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 完成: 8 库 18 表
-- ============================================================
