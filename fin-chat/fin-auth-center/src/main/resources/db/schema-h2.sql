-- H2 初始化 (沙箱)
CREATE TABLE IF NOT EXISTS fin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_unionid ON fin_user(unionid);
CREATE INDEX IF NOT EXISTS idx_mobile_hash ON fin_user(mobile_hash);
