-- 增量迁移：保留现有业务数据；连接到目标库后执行，可重复运行。
DELIMITER $$
DROP PROCEDURE IF EXISTS rxt_portfolio_migrate$$
CREATE PROCEDURE rxt_portfolio_migrate()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'token_version') THEN
        ALTER TABLE t_user ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT '登录凭证版本';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 't_operation_log' AND column_name = 'request_id') THEN
        ALTER TABLE t_operation_log ADD COLUMN request_id VARCHAR(36) DEFAULT NULL COMMENT '请求追踪编号';
    END IF;
END$$
CALL rxt_portfolio_migrate()$$
DROP PROCEDURE rxt_portfolio_migrate$$
DELIMITER ;
