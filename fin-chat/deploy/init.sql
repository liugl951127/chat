-- MySQL 初始化
CREATE DATABASE IF NOT EXISTS fin_chat DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fin_auth DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fin_audit DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fin_kms DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- fin_auth
USE fin_auth;
CREATE TABLE IF NOT EXISTS fin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    unionid VARCHAR(64),
    wecom_userid VARCHAR(64),
    mobile_hash CHAR(64),
    mobile_enc VARBINARY(256),
    id_card_hash CHAR(64),
    real_name_enc VARBINARY(256),
    real_name_status INT DEFAULT 0,
    risk_level INT DEFAULT 1,
    status INT DEFAULT 1,
    nickname VARCHAR(128),
    avatar VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_unionid (unionid),
    INDEX idx_mobile_hash (mobile_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- fin_audit
USE fin_audit;
CREATE TABLE IF NOT EXISTS fin_audit_log (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
