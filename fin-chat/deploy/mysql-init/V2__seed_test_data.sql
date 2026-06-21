-- ============================================================
-- 测试数据
-- ============================================================

USE fin_auth;
-- 测试用户 (密码 / 13800138000)
-- mobile_hash = SM3("13800138000") = 模拟值
INSERT INTO fin_user (id, unionid, mobile_hash, mobile_enc, real_name_status, risk_level, status, nickname)
VALUES (1, 'test-unionid-001', 'placeholder_sm3_hash', 'placeholder_sm4_enc', 1, 3, 1, '张三'),
       (2, 'test-unionid-002', 'placeholder_sm3_hash', 'placeholder_sm4_enc', 2, 4, 1, '李四'),
       (3, NULL, 'placeholder_sm3_hash', 'placeholder_sm4_enc', 0, 1, 1, '王五');

USE fin_trade;
-- 产品已在 V1 插入

USE fin_compliance;
-- 风控规则种子
INSERT INTO fin_blacklist (target_type, target_value, reason, added_by)
VALUES ('IP', '192.168.1.100', '测试黑名单', 1);

USE fin_observability;
-- 告警规则种子
INSERT INTO fin_alert_rule (name, metric, operator, threshold, severity, channels, enabled)
VALUES
  ('响应时间 P99 超 1s', 'http_request_duration_p99', '>', 1.0, 'P1', 'WEBHOOK,EMAIL', 1),
  ('错误率超 1%', 'http_error_rate', '>', 0.01, 'P1', 'WEBHOOK,EMAIL', 1),
  ('登录失败率超 5%', 'login_failure_rate', '>', 0.05, 'P2', 'WEBHOOK', 1),
  ('KMS 不可用', 'kms_alive', '<', 1, 'P0', 'WEBHOOK,EMAIL,SMS', 1);
