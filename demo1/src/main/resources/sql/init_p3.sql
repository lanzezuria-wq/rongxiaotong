-- =============================================================================
-- 融销通 · P3 建表脚本  init_p3.sql
--
-- 覆盖范围：P3 交付边界
--   M4 融资服务   t_financing_apply / t_guarantee_group / t_guarantee_member
--   M6 融资管理   t_bank_approval
--   M5 专家助力   t_article_comment / t_article_like / t_question / t_appointment
--   另给 P1 的表补列：t_article.like_count/comment_count、t_expert.rating/answer_count
--
-- 执行顺序（三个脚本都要跑，顺序不能反）：
--   1) init.sql        ← P1：地基 + 用户体系 + 系统管理（建库、t_user/t_expert/t_dict/…）
--   2) init_trade.sql  ← P2：M2 首页 + M3 农产品交易
--   3) init_p3.sql     ← 本文件
--
-- 本脚本可重复执行（先 DROP 自己的表再建；补列用存储过程判断存在性；
-- 种子账号用 INSERT IGNORE 挡重复）。
--
-- 【两个刻意的取舍，改动前请先看】
--
-- 1) 为什么不并进 init.sql？
--    跟 P1/P2 的做法保持一致：每个模块一个文件、只 DROP 自己的表。
--    并进 init.sql 会让同一份 DDL 存在两处，四个人并行开发时谁改了列却只改一处，
--    两边就静默不一致 —— 这类问题极难排查。P2 也选了独立文件（init_trade.sql）。
--
-- 2) 为什么 M4/M6 和 M5 合成一个文件，而不是像 P2 那样一个模块一个？
--    因为两边的种子数据是**共用**的（M4 的联保小组和 M5 的提问/预约都要用同一批农户
--    和专家账号）。拆成两个文件的话，种子账号得写在其中一个里、另一个依赖它先执行，
--    或者两边都写一遍（重复执行就重号）。合成一个文件既简单又没有这个依赖。
--    代码那边仍然是两个包（com.rxt.finance / com.rxt.assist），模块边界没丢。
-- =============================================================================

USE rxt_db;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 先删后建：只删 P3 引入的表，绝不碰 P1 / P2 的表
-- 删除顺序是"被引用的后删"，虽然这里没有真实外键约束，但保持习惯
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS t_appointment;
DROP TABLE IF EXISTS t_question;
DROP TABLE IF EXISTS t_article_like;
DROP TABLE IF EXISTS t_article_comment;
DROP TABLE IF EXISTS t_bank_approval;
DROP TABLE IF EXISTS t_guarantee_member;
DROP TABLE IF EXISTS t_guarantee_group;
DROP TABLE IF EXISTS t_financing_apply;


-- =============================================================================
-- 一、M4 融资服务
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. t_financing_apply  融资申请
--
-- status 状态机：
--   0 待匹配 ──加入联保小组──> 1 已匹配 ──小组成型(≥3人)──> 2 待银行审批
--                                                            │
--                                         银行通过 ──> 3 已通过 ┤
--                                         银行拒绝 ──> 4 已拒绝 ┘
--   任意进行中(0/1/2) 且小组未被银行处理 ──申请人取消──> 5 已取消
--
-- 状态推进只有"加入小组"和"银行审批"两个入口，所以不需要额外的状态流转流水表。
-- -----------------------------------------------------------------------------
CREATE TABLE t_financing_apply (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL                         COMMENT '申请人 t_user.id（逻辑外键）',
    amount      DECIMAL(10,2)   NOT NULL                         COMMENT '申请金额（元），业务范围 10000.00 ~ 500000.00',
    `usage`     VARCHAR(50)     DEFAULT NULL                     COMMENT '资金用途，取自字典 fund_usage。⚠️ usage 是 MySQL 关键字，DDL 与实体都必须转义（这里用反引号，实体用 @TableField），只写一边会在运行期 INSERT 时炸 SQL 语法错误',
    industry    VARCHAR(50)     DEFAULT NULL                     COMMENT '所属行业，取自字典 industry。同行业才允许互保',
    province    VARCHAR(30)     DEFAULT NULL                     COMMENT '省，用于联保小组的地域匹配',
    city        VARCHAR(30)     DEFAULT NULL                     COMMENT '市',
    description VARCHAR(500)    DEFAULT NULL                     COMMENT '用途说明 / 还款来源说明',
    loan_term   VARCHAR(10)     DEFAULT NULL                     COMMENT '贷款期限，取自字典 loan_term，存编码如 M12',
    status      TINYINT         NOT NULL DEFAULT 0               COMMENT '状态：0 待匹配 / 1 已匹配 / 2 待银行审批 / 3 已通过 / 4 已拒绝 / 5 已取消',
    group_id    BIGINT UNSIGNED DEFAULT NULL                     COMMENT '所属联保小组 t_guarantee_group.id，待匹配时为 NULL',
    bank_id     BIGINT UNSIGNED DEFAULT NULL                     COMMENT '审批银行 t_user.id，审批后回填',
    deleted     TINYINT         NOT NULL DEFAULT 0               COMMENT '逻辑删除：0 未删 / 1 已删。取消申请是改状态而不是删除，这列留给"申请人删除历史记录"',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_fin_user_status (user_id, status),
    KEY idx_fin_group (group_id),
    KEY idx_fin_match (status, industry, province)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '融资申请';


-- -----------------------------------------------------------------------------
-- 2. t_guarantee_group  联保小组
--
-- 「联保」是这套融资模型的核心：单个农户没有抵押物，需要 3~10 户组成小组互相担保，
-- 银行审的是「小组」而不是「个人」，所以 t_bank_approval 挂在 group_id 上。
--
-- status 状态机：
--   0 招募中      成员数 < 3（MIN_MEMBERS），小组未成型，银行看不到
--   1 已匹配      成员数 >= 3，已成型，进入银行待审池（银行端列表默认展示这一档）
--   2 待银行审批  **预留位**：本项目"成型即入池"，不需要农户手动提交，
--                 所以正常流程不会停在 2。保留是为了兼容文档定义，
--                 以及将来要加"线下材料齐了再提交"的流程时不用改表结构。
--   3 已通过 / 4 已拒绝   银行审批结果，不可撤回
--
-- 退回到招募中：已成型的小组有人退出、人数掉回 3 以下时自动退回 0（见 syncGroupState）。
-- -----------------------------------------------------------------------------
CREATE TABLE t_guarantee_group (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT      COMMENT '主键',
    group_no        VARCHAR(32)     DEFAULT NULL                 COMMENT '小组编号，形如 LB-20260920-000123。允许为 NULL 是刻意的：编号里要带自增主键才能保证全局唯一，而主键 INSERT 之后才拿得到，所以流程是"先插入拿 id、再回填编号"；MySQL 唯一索引允许多个 NULL，这两步之间不冲突',
    creator_id      BIGINT UNSIGNED NOT NULL                     COMMENT '发起人 t_user.id',
    industry        VARCHAR(50)     DEFAULT NULL                 COMMENT '所属行业，同行业才允许互保（风险相关性）',
    province        VARCHAR(30)     DEFAULT NULL                 COMMENT '省',
    city            VARCHAR(30)     DEFAULT NULL                 COMMENT '市',
    total_amount    DECIMAL(12,2)   NOT NULL DEFAULT 0           COMMENT '组内申请金额合计（元）。**冗余列**，由成员表 SUM 出来，只允许 syncGroupState 刷新',
    current_members INT             NOT NULL DEFAULT 0           COMMENT '当前成员数。**冗余列**，由成员表 COUNT 出来，只允许 syncGroupState 刷新。业务范围 3~10',
    bank_id         BIGINT UNSIGNED DEFAULT NULL                 COMMENT '审批银行 t_user.id',
    status          TINYINT         NOT NULL DEFAULT 0           COMMENT '状态：0 招募中 / 1 已匹配 / 2 待银行审批（预留）/ 3 已通过 / 4 已拒绝',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_no (group_no),
    KEY idx_grp_status (status),
    KEY idx_grp_match (status, industry, province),
    KEY idx_grp_creator (creator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '联保小组';


-- -----------------------------------------------------------------------------
-- 3. t_guarantee_member  联保小组成员
--
-- 它既是关联表也是快照表：(group_id, user_id) 是关联键（唯一索引挡住重复加入），
-- apply_amount / industry / province / city 是从融资申请复制过来的**快照**。
--
-- 为什么冗余这些字段而不连表查？因为银行审批页要把整个小组一次摊开看，
-- 连表或 N+1 查询在 10 人小组上就是 10 次子查询；更重要的是审批快照的语义：
-- 银行看到的是"提交审批那一刻的申请信息"，农户事后改金额不应该让已提交的材料跟着变。
--
-- 没有 deleted 列：退出小组是**物理删除**（join_status 里的 2 表示"被拒绝加入"，
-- 语义与"自己主动退出"不同；而且留着旧记录会让这个人再也无法重新加入同一小组）。
-- -----------------------------------------------------------------------------
CREATE TABLE t_guarantee_member (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT         COMMENT '主键',
    group_id     BIGINT UNSIGNED NOT NULL                        COMMENT '联保小组 t_guarantee_group.id',
    user_id      BIGINT UNSIGNED NOT NULL                        COMMENT '成员 t_user.id',
    apply_id     BIGINT UNSIGNED NOT NULL                        COMMENT '对应的融资申请 t_financing_apply.id',
    apply_amount DECIMAL(10,2)   DEFAULT NULL                    COMMENT '该成员的申请金额（快照）',
    industry     VARCHAR(50)     DEFAULT NULL                    COMMENT '冗余所属行业（快照）',
    province     VARCHAR(30)     DEFAULT NULL                    COMMENT '冗余省（快照）',
    city         VARCHAR(30)     DEFAULT NULL                    COMMENT '冗余市（快照）',
    join_status  TINYINT         NOT NULL DEFAULT 0              COMMENT '加入状态：0 申请中（预留）/ 1 已加入 / 2 已拒绝（预留）',
    joined_at    DATETIME        DEFAULT NULL                    COMMENT '加入时间',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gm_group_user (group_id, user_id),
    KEY idx_gm_apply (apply_id),
    KEY idx_gm_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '联保小组成员';


-- =============================================================================
-- 二、M6 融资管理（银行端）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 4. t_bank_approval  银行审批记录
--
-- 一次审批 = 一行，挂在 group_id 上。业务上"一个小组只会被审批一次"
-- （审批结果不可撤回），所以 Service 里靠"该 group 是否已有审批记录"来拒绝重复审批
-- —— 这是比"看小组状态"更可靠的数据兜底：万一组状态因某个 bug 没改成功，
-- 有审批记录这一条仍然挡得住第二次审批，避免一个小组批出两个相反的结论。
--
-- credit_score / interest_rate / decision_amount 在 Mock 模式下由
-- com.rxt.finance.config.MockConfig 自动生成（见 README 的 Mock 说明）。
-- -----------------------------------------------------------------------------
CREATE TABLE t_bank_approval (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT      COMMENT '主键',
    group_id        BIGINT UNSIGNED NOT NULL                     COMMENT '被审批的联保小组 t_guarantee_group.id',
    bank_id         BIGINT UNSIGNED NOT NULL                     COMMENT '审批银行 t_user.id',
    result          VARCHAR(20)     NOT NULL                     COMMENT '审批结果：APPROVED 通过 / REJECTED 拒绝',
    credit_score    INT             DEFAULT NULL                 COMMENT '信用评分 0~100，Mock 模式下自动生成',
    interest_rate   DECIMAL(5,2)    DEFAULT NULL                 COMMENT '年化利率 %，业务范围 3.85 ~ 7.00',
    decision_amount DECIMAL(12,2)   DEFAULT NULL                 COMMENT '核定额度（元）。拒绝时为 NULL 而不是 0 —— 给 0 会让人误以为"批了 0 元"',
    remark          VARCHAR(500)    DEFAULT NULL                 COMMENT '审批意见，拒绝时必填',
    approved_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
    PRIMARY KEY (id),
    KEY idx_ba_group (group_id),
    KEY idx_ba_bank_time (bank_id, approved_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '银行审批记录';


-- =============================================================================
-- 三、M5 专家助力
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 5. t_article_comment  文章评论
-- 一次写入、永不修改，所以没有 update_time，也没有逻辑删除。
-- -----------------------------------------------------------------------------
CREATE TABLE t_article_comment (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
    article_id  BIGINT UNSIGNED NOT NULL                         COMMENT '文章 t_article.id',
    user_id     BIGINT UNSIGNED NOT NULL                         COMMENT '评论人 t_user.id',
    content     VARCHAR(200)    NOT NULL                         COMMENT '评论内容，需求规定上限 200 字',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    PRIMARY KEY (id),
    KEY idx_ac_article_time (article_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文章评论';


-- -----------------------------------------------------------------------------
-- 6. t_article_like  文章点赞
--
-- ⚠️ 这张表存在的意义就是那个唯一索引：点赞是**可切换**的（再点一次取消），
-- 所以除了给 t_article.like_count 做加减，还必须能回答"当前这个用户赞过没有"
-- —— 否则前端刷新后无法判断按钮该显示"已赞"还是"点赞"（ArticleDetailVO.isLiked）。
-- 只靠计数列是答不出这个问题的。
-- -----------------------------------------------------------------------------
CREATE TABLE t_article_like (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
    article_id  BIGINT UNSIGNED NOT NULL                         COMMENT '文章 t_article.id',
    user_id     BIGINT UNSIGNED NOT NULL                         COMMENT '点赞人 t_user.id',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_al_article_user (article_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文章点赞（一人一篇只能赞一次）';


-- -----------------------------------------------------------------------------
-- 7. t_question  在线提问
-- expert_id 允许为 NULL —— 契约写明"可选，不填则所有专家可见"，
-- 这类问题任何已通过审核的专家都能回答（先到先得）。
-- answer_by 记录"实际是谁答的"：公开提问下 expert_id 是 NULL，它才是唯一能追溯的依据。
-- -----------------------------------------------------------------------------
CREATE TABLE t_question (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT          COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL                         COMMENT '提问人 t_user.id',
    expert_id   BIGINT UNSIGNED DEFAULT NULL                     COMMENT '指定专家 t_expert.id，NULL 表示面向所有专家',
    title       VARCHAR(100)    NOT NULL                         COMMENT '问题标题',
    content     VARCHAR(500)    NOT NULL                         COMMENT '问题详情',
    answer      VARCHAR(1000)   DEFAULT NULL                     COMMENT '专家回答',
    answer_by   BIGINT UNSIGNED DEFAULT NULL                     COMMENT '回答专家 t_expert.id',
    status      TINYINT         NOT NULL DEFAULT 0               COMMENT '状态：0 待回答 / 1 已回答',
    answered_at DATETIME        DEFAULT NULL                     COMMENT '回答时间',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    PRIMARY KEY (id),
    KEY idx_q_expert_status (expert_id, status),
    KEY idx_q_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '在线提问';


-- -----------------------------------------------------------------------------
-- 8. t_appointment  专家预约
-- type=online 必须填 meeting_link，type=offline 必须填 meeting_place。
-- 这条"跨字段条件必填"和"审核拒绝必须填理由"是同一类问题，Bean Validation 表达不了，
-- 只能写在 Service 里（ConsultServiceImpl#createAppointment）。
-- status 直接存大写字符串，不做数字映射 —— 少一层转换就少一处出错的地方。
-- -----------------------------------------------------------------------------
CREATE TABLE t_appointment (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT     COMMENT '主键',
    user_id          BIGINT UNSIGNED NOT NULL                    COMMENT '预约人 t_user.id',
    expert_id        BIGINT UNSIGNED NOT NULL                    COMMENT '被预约专家 t_expert.id',
    type             VARCHAR(10)     NOT NULL                    COMMENT '方式：online 线上 / offline 线下',
    appointment_time DATETIME        NOT NULL                    COMMENT '预约时间，必须晚于当前时间',
    phone            VARCHAR(20)     DEFAULT NULL                COMMENT '联系电话',
    description      VARCHAR(500)    DEFAULT NULL                COMMENT '问题描述',
    meeting_link     VARCHAR(255)    DEFAULT NULL                COMMENT '线上会议链接，type=online 时必填',
    meeting_place    VARCHAR(255)    DEFAULT NULL                COMMENT '线下见面地点，type=offline 时必填',
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING'  COMMENT '状态：PENDING 待处理 / ACCEPTED 已接受 / REJECTED 已拒绝',
    handled_at       DATETIME        DEFAULT NULL                COMMENT '专家处理时间',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    PRIMARY KEY (id),
    KEY idx_ap_expert_status (expert_id, status),
    KEY idx_ap_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '专家预约';


-- =============================================================================
-- 四、给 P1 已建的表补列
--
-- MySQL 8.0 没有 "ADD COLUMN IF NOT EXISTS"，所以用存储过程查 information_schema
-- 来判断列是否已存在，保证本脚本可以重复执行。
--
-- 为什么这两组列放在 P3 的脚本里补，而不是去改 init.sql？
-- 因为 init.sql 是 P1 的文件、P1 已推送。改它会产生跨模块 diff，
-- 而且 P2 也在读 t_article（M2 首页的文章入口在 HomeServiceImpl 里），
-- 谁改谁负责的历史不清晰。补列脚本 + 实体里加字段是影响最小的做法。
-- =============================================================================

DROP PROCEDURE IF EXISTS rxt_add_column_if_absent;

DELIMITER $$
CREATE PROCEDURE rxt_add_column_if_absent(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- t_article：文章详情要展示"点赞数 / 评论数"。
-- 冗余计数列而不是每次 COUNT(*)：列表页 10 条文章各查一次 count 就是 10 次额外查询，
-- 而计数只需要在写点赞/评论的那一刻同步 ±1（由 ArticleServiceImpl 负责）。
CALL rxt_add_column_if_absent('t_article', 'like_count',
    "like_count INT NOT NULL DEFAULT 0 COMMENT '点赞数，冗余计数，只允许 ArticleServiceImpl 刷新' AFTER view_count");
CALL rxt_add_column_if_absent('t_article', 'comment_count',
    "comment_count INT NOT NULL DEFAULT 0 COMMENT '评论数，冗余计数，只允许 ArticleServiceImpl 刷新' AFTER like_count");

-- t_expert：专家列表要展示评分和回答数（ExpertCardVO.rating / answerCount）。
-- rating 初始给 5.00 而不是 0：新专家没被评价过时按评分排序会永远沉底，等于被隐藏；
-- 给满分是"中性偏好"的处理，等真有评价数据后再由评价流程覆盖。
CALL rxt_add_column_if_absent('t_expert', 'rating',
    "rating DECIMAL(3,2) NOT NULL DEFAULT 5.00 COMMENT '评分 0.00~5.00'");
CALL rxt_add_column_if_absent('t_expert', 'answer_count',
    "answer_count INT NOT NULL DEFAULT 0 COMMENT '已回答问题数，冗余计数，只允许 ConsultServiceImpl 刷新'");

DROP PROCEDURE IF EXISTS rxt_add_column_if_absent;


-- =============================================================================
-- 五、种子数据
--
-- 目的：让 P4 一接上接口就有数据可看，不用手工造。
-- 密码统一 123456（BCrypt cost=10，哈希串与 P1/P2 的种子账号完全相同）。
--
-- ⚠️ 手机号分段（避免和别人撞号）：
--   13900000000~06  P1（管理员/银行/待审专家/农户/专家）
--   13900000008     P2（演示用买家）
--   13900000011~14  P3 农户  ← 本文件
--   13900000015~16  P3 专家  ← 本文件
--   13900000007/09/10 留空备用
--
-- 为什么要补 4 个农户 + 2 个专家？
--   联保小组最少要 3 个农户才成得了型（FinancingConstants.MIN_MEMBERS），
--   只有 P1 的 1 个农户根本演示不了 M4 的核心链路（组队 → 成型 → 银行审批）。
--   专家列表页只有 1 个专家也看不出按领域筛选的效果。
-- =============================================================================

INSERT IGNORE INTO t_user (phone, password, name, role, status, email, province, city) VALUES
('13900000011', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '王大山', 'farmer', 1, 'farmer2@rxt.com', '山东省', '潍坊市'),
('13900000012', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '陈秀兰', 'farmer', 1, 'farmer3@rxt.com', '山东省', '潍坊市'),
('13900000013', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '刘四海', 'farmer', 1, 'farmer4@rxt.com', '山东省', '寿光市'),
('13900000014', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '孙小满', 'farmer', 1, 'farmer5@rxt.com', '河南省', '周口市');

INSERT IGNORE INTO t_user (phone, password, name, role, status, email, province, city, extend_json) VALUES
('13900000015', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '钱牧之', 'expert', 1, 'expert4@rxt.com', '山东省', '潍坊市',
 '{"title":"高级畜牧师","org":"山东省畜牧兽医站","domain":"畜禽养殖与疫病防控","intro":"从事畜牧技术推广 20 年，擅长规模化养殖场防疫体系设计","certUrl":null}'),
('13900000016', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '周文博', 'expert', 1, 'expert5@rxt.com', '浙江省', '杭州市',
 '{"title":"副教授","org":"浙江大学农业技术推广中心","domain":"农产品电商与品牌营销","intro":"研究方向为农产品供应链数字化，主持省级课题 3 项","certUrl":null}');

-- t_expert 上有 uk_expert_user 唯一索引，所以 INSERT IGNORE 也能防重复
INSERT IGNORE INTO t_expert (user_id, real_name, title, org, domain, intro, rating, answer_count, audit_remark, audit_by, audit_time)
SELECT id, '钱牧之', '高级畜牧师', '山东省畜牧兽医站', '畜禽养殖与疫病防控',
       '从事畜牧技术推广 20 年，擅长规模化养殖场防疫体系设计', 4.80, 0,
       '资质齐备，予以通过', (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW()
FROM t_user WHERE phone = '13900000015';

INSERT IGNORE INTO t_expert (user_id, real_name, title, org, domain, intro, rating, answer_count, audit_remark, audit_by, audit_time)
SELECT id, '周文博', '副教授', '浙江大学农业技术推广中心', '农产品电商与品牌营销',
       '研究方向为农产品供应链数字化，主持省级课题 3 项', 4.60, 0,
       '资质齐备，予以通过', (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW()
FROM t_user WHERE phone = '13900000016';

-- 把 P1 已建的那条赵教授的 t_expert 补上评分（P1 建表时还没有 rating 列）
UPDATE t_expert SET rating = 4.90
WHERE user_id = (SELECT id FROM t_user WHERE phone = '13900000006' LIMIT 1);

-- ---- 联保小组演示数据：1 个已成型的小组（银行端一打开就有东西可审） ----
-- 成型需要 >= 3 名成员，这里用 李农户(P1) + 王大山 + 陈秀兰 凑成，金额合计 260000。
-- group_no 写死而不是动态生成，保证脚本可重复执行、结果一致。
INSERT INTO t_guarantee_group (group_no, creator_id, industry, province, city, total_amount, current_members, status)
SELECT 'LB-DEMO-0001', u.id, 'PLANTING', '山东省', '潍坊市', 260000.00, 3, 1
FROM t_user u WHERE u.phone = '13900000004';

-- 三个成员的申请：先进小组、状态=1 已匹配
-- 李农户 130000：申请人本人
INSERT INTO t_financing_apply (user_id, amount, `usage`, industry, province, city, description, loan_term, status, group_id)
SELECT u.id, 130000.00, 'MACHINE', 'PLANTING', '河南省', '周口市',
       '购置大型拖拉机及旋耕机各一台，服务本村及周边耕地。还款来源为农机作业服务费。', 'M24', 1,
       (SELECT id FROM t_guarantee_group WHERE group_no = 'LB-DEMO-0001' LIMIT 1)
FROM t_user u WHERE u.phone = '13900000004';

-- 王大山 80000、陈秀兰 50000：先建成"待匹配"，再挂到小组上
INSERT INTO t_financing_apply (user_id, amount, `usage`, industry, province, city, description, loan_term, status)
SELECT id, 80000.00, 'GREENHOUSE', 'PLANTING', '山东省', '潍坊市',
       '计划新建 3 座冬暖式大棚，用于反季节番茄种植。还款来源为当季销售收入。', 'M12', 0
FROM t_user WHERE phone = '13900000011';

INSERT INTO t_financing_apply (user_id, amount, `usage`, industry, province, city, description, loan_term, status)
SELECT id, 50000.00, 'SEED', 'PLANTING', '山东省', '潍坊市',
       '春耕备耕，采购种子化肥及地膜。还款来源为小麦与玉米两季收入。', 'M6', 0
FROM t_user WHERE phone = '13900000012';

UPDATE t_financing_apply
SET status = 1,
    group_id = (SELECT id FROM t_guarantee_group WHERE group_no = 'LB-DEMO-0001' LIMIT 1)
WHERE user_id IN (SELECT id FROM t_user WHERE phone IN ('13900000011', '13900000012'));

-- 成员记录（三条），金额与上面的申请一一对应，合计正好 260000
INSERT INTO t_guarantee_member (group_id, user_id, apply_id, apply_amount, industry, province, city, join_status, joined_at)
SELECT g.id, u.id, a.id, a.amount, a.industry, a.province, a.city, 1, NOW() - INTERVAL 3 DAY
FROM t_user u
JOIN t_guarantee_group g ON g.group_no = 'LB-DEMO-0001'
JOIN t_financing_apply a ON a.user_id = u.id AND a.group_id = g.id
WHERE u.phone = '13900000004';

INSERT INTO t_guarantee_member (group_id, user_id, apply_id, apply_amount, industry, province, city, join_status, joined_at)
SELECT g.id, u.id, a.id, a.amount, a.industry, a.province, a.city, 1, NOW() - INTERVAL 2 DAY
FROM t_user u
JOIN t_guarantee_group g ON g.group_no = 'LB-DEMO-0001'
JOIN t_financing_apply a ON a.user_id = u.id AND a.group_id = g.id
WHERE u.phone = '13900000011';

INSERT INTO t_guarantee_member (group_id, user_id, apply_id, apply_amount, industry, province, city, join_status, joined_at)
SELECT g.id, u.id, a.id, a.amount, a.industry, a.province, a.city, 1, NOW() - INTERVAL 1 DAY
FROM t_user u
JOIN t_guarantee_group g ON g.group_no = 'LB-DEMO-0001'
JOIN t_financing_apply a ON a.user_id = u.id AND a.group_id = g.id
WHERE u.phone = '13900000012';

-- ---- 另外两条"待匹配"的申请（让"可加入的小组"和"加入小组"有数据可试） ----
-- 刘四海：跨行业（养殖）跨地域（寿光），用来验证"匹配是按同行业+同省强匹配"
INSERT INTO t_financing_apply (user_id, amount, `usage`, industry, province, city, description, loan_term, status)
SELECT id, 120000.00, 'BREED', 'BREEDING', '山东省', '寿光市',
       '扩建蛋鸡养殖栏舍，新增存栏 5000 羽。还款来源为鸡蛋销售月流水。', 'M24', 0
FROM t_user WHERE phone = '13900000013';

-- 孙小满：故意**不给申请** —— 用来测"从零提交申请"和"没有申请不能发起小组"
-- （见 init_p3.sql 的自检语句）

-- ---- 文章互动数据：给 P1 那篇已发布的文章加点评论和赞 ----
INSERT INTO t_article_comment (article_id, user_id, content)
SELECT a.id, u.id, '我们大棚连续种了 5 年番茄，确实病害一年比一年重，回去试试夏季闷棚。'
FROM t_article a JOIN t_user u ON u.phone = '13900000011'
WHERE a.title = '设施蔬菜连作障碍的成因与对策';

INSERT INTO t_article_comment (article_id, user_id, content)
SELECT a.id, u.id, '请问增施有机肥的话，每亩大概用多少方比较合适？'
FROM t_article a JOIN t_user u ON u.phone = '13900000012'
WHERE a.title = '设施蔬菜连作障碍的成因与对策';

INSERT INTO t_article_like (article_id, user_id)
SELECT a.id, u.id FROM t_article a JOIN t_user u ON u.phone = '13900000011'
WHERE a.title = '设施蔬菜连作障碍的成因与对策';

INSERT INTO t_article_like (article_id, user_id)
SELECT a.id, u.id FROM t_article a JOIN t_user u ON u.phone = '13900000012'
WHERE a.title = '设施蔬菜连作障碍的成因与对策';

-- 计数列与上面的明细保持一致（写死成 2 而不是 +2，保证重复执行结果不变）
UPDATE t_article SET like_count = 2, comment_count = 2
WHERE title = '设施蔬菜连作障碍的成因与对策';

-- ---- 提问：1 条定向待回答 + 1 条公开已回答 ----
INSERT INTO t_question (user_id, expert_id, title, content, status)
SELECT u.id, e.id, '大棚番茄叶片发黄是什么原因？',
       '最近一周大棚里的番茄中下部叶片开始发黄，叶脉还是绿的，浇水施肥都正常，请问是什么问题？',
       0
FROM t_user u, t_expert e
JOIN t_user eu ON eu.id = e.user_id
WHERE u.phone = '13900000011' AND eu.phone = '13900000006';

INSERT INTO t_question (user_id, expert_id, title, content, answer, answer_by, status, answered_at)
SELECT u.id, NULL, '农产品线上销售的包装成本如何控制？',
       '做生鲜电商损耗比较大，包装成本一直降不下来，有没有比较实用的办法？',
       '建议从三方面入手：一是把包装与物流方案一起设计，先定快递再定箱型；二是用可折叠的蜂窝纸箱替代泡沫箱，单件成本可降 30% 左右；三是按品类分级包装，高毛利品类才用冷链方案。',
       e.id, 1, NOW() - INTERVAL 2 DAY
FROM t_user u
LEFT JOIN t_expert e ON e.user_id = (SELECT id FROM t_user WHERE phone = '13900000016' LIMIT 1)
WHERE u.phone = '13900000012';

-- 写死成 1（不是 +1），保证重复执行不会累加
UPDATE t_expert SET answer_count = 1
WHERE user_id = (SELECT id FROM t_user WHERE phone = '13900000016' LIMIT 1);

-- ---- 预约：1 条待处理（专家的"待办"里一打开就有东西） ----
INSERT INTO t_appointment (user_id, expert_id, type, appointment_time, phone, description, meeting_link, status)
SELECT u.id, e.id, 'online', NOW() + INTERVAL 3 DAY,
       '13900000013', '想请教蛋鸡产蛋率突然下降的排查思路，方便的话想开个视频会议看下现场。',
       'https://meeting.example.com/rxt-demo-001', 'PENDING'
FROM t_user u, t_expert e
JOIN t_user eu ON eu.id = e.user_id
WHERE u.phone = '13900000013' AND eu.phone = '13900000015';


-- ---- 追加 2 个专家 + 3 条提问（让专家列表、提问页更丰富） ----
INSERT IGNORE INTO t_user (phone, password, name, role, status, email, province, city, extend_json) VALUES
('13900000017', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '郑国平', 'expert', 1, 'expert6@rxt.com', '广东省', '广州市',
 '{"title":"教授","org":"华南农业大学园艺学院","domain":"果树栽培与育种","intro":"长期从事南方果树栽培与品种选育研究","certUrl":null}'),
('13900000018', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '吴志远', 'expert', 1, 'expert7@rxt.com', '北京市', '北京市',
 '{"title":"高级工程师","org":"中国农业科学院","domain":"智慧农业与农业物联网","intro":"从事农业信息化与智能装备研发 15 年","certUrl":null}');

INSERT IGNORE INTO t_expert (user_id, real_name, title, org, domain, intro, rating, answer_count, audit_remark, audit_by, audit_time)
SELECT id, '郑国平', '教授', '华南农业大学园艺学院', '果树栽培与育种',
       '长期从事南方果树栽培与品种选育研究', 4.70, 0,
       '资质齐备，予以通过', (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW()
FROM t_user WHERE phone = '13900000017';

INSERT IGNORE INTO t_expert (user_id, real_name, title, org, domain, intro, rating, answer_count, audit_remark, audit_by, audit_time)
SELECT id, '吴志远', '高级工程师', '中国农业科学院', '智慧农业与农业物联网',
       '从事农业信息化与智能装备研发 15 年', 4.50, 0,
       '资质齐备，予以通过', (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW()
FROM t_user WHERE phone = '13900000018';

INSERT INTO t_question (user_id, expert_id, title, content, status)
SELECT u.id, NULL, '大棚湿度总是偏高，蔬菜容易烂根怎么办？',
       '冬季大棚通风少，湿度经常在 90% 以上，番茄灰霉病反复发作，有什么控湿的好办法？',
       0
FROM t_user u WHERE u.phone = '13900000014';

INSERT INTO t_question (user_id, expert_id, title, content, answer, answer_by, status, answered_at)
SELECT u.id, e.id, '南方柑橘冬季修剪有什么注意事项？',
       '家里的砂糖橘树龄有 8 年了，树形比较乱，想问下冬季修剪要注意哪些要点？',
       '冬季修剪以疏除交叉枝、病虫枝和过密枝为主，注意开天窗、留足内膛光照；剪口要平、粗枝涂愈合剂，避免感染。',
       e.id, 1, NOW() - INTERVAL 5 DAY
FROM t_user u, t_expert e
JOIN t_user eu ON eu.id = e.user_id
WHERE u.phone = '13900000013' AND eu.phone = '13900000017';

INSERT INTO t_question (user_id, expert_id, title, content, answer, answer_by, status, answered_at)
SELECT u.id, e.id, '想给自家果园上物联网监测，投入大概多少？',
       '有 30 亩果园，想装土壤墒情和气象监测设备，请问投入成本和实用性怎么样？',
       '小规模果园建议从最关键的墒情监测做起，一套基础设备几千元即可落地；先解决灌溉决策这个最大痛点，再逐步扩展到气象和虫情监测。',
       e.id, 1, NOW() - INTERVAL 3 DAY
FROM t_user u, t_expert e
JOIN t_user eu ON eu.id = e.user_id
WHERE u.phone = '13900000012' AND eu.phone = '13900000018';

-- 写死 answer_count，保证重复执行不累加
UPDATE t_expert SET answer_count = 1
WHERE user_id = (SELECT id FROM t_user WHERE phone = '13900000017' LIMIT 1);

UPDATE t_expert SET answer_count = 1
WHERE user_id = (SELECT id FROM t_user WHERE phone = '13900000018' LIMIT 1);

-- =============================================================================
-- 自检（执行完可以跑这几条核对，数字是实测值）
--   SELECT COUNT(*) FROM t_financing_apply;   -- 4（3 条挂在演示小组 + 1 条待匹配）
--   SELECT COUNT(*) FROM t_guarantee_group;   -- 1
--   SELECT COUNT(*) FROM t_guarantee_member;  -- 3
--   SELECT COUNT(*) FROM t_bank_approval;     -- 0（审批是运行时动作，不预置）
--   SELECT COUNT(*) FROM t_expert;            -- 5（P1 赵教授 + P3 钱牧之/周文博/郑国平/吴志远）
--   SELECT COUNT(*) FROM t_question;          -- 5（2 待回答 + 3 已回答）
--   SELECT COUNT(*) FROM t_appointment;       -- 1
--
--   -- 不变量自检：小组的 totalAmount 必须等于成员申请金额之和
--   SELECT g.group_no, g.current_members, g.total_amount,
--          (SELECT SUM(m.apply_amount) FROM t_guarantee_member m WHERE m.group_id = g.id) AS member_sum
--   FROM t_guarantee_group g;
-- =============================================================================
