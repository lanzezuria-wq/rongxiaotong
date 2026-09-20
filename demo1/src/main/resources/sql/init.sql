-- =============================================================================
-- 融销通 · 数据库初始化脚本  init.sql
-- 数据库：rxt_db          字符集：utf8mb4 / utf8mb4_unicode_ci
-- 覆盖范围：P1 交付边界（M1 用户体系 + M7 系统管理）
--
-- 生成依据：
--   《融销通软件需求规格说明书》表11-14（M1 功能）、表38-41（M7 功能）、
--                              表53-54（M1 API 与错误码）、表59-61（M7 API 与错误码）
--   《融销通_概要设计说明书》  2.3.2 用户角色与权限、5.2.2 错误码规范、
--                              7.1.1 全局异常处理、8.1 认证与授权
--
-- [重要] 概要设计说明书 1.2 节明确声明"本文档不覆盖详细数据库表设计"，
--        但部署章节又引用了 src/main/resources/sql/init.sql。
--        本脚本是按需求反推的初版，字段命名需与 P2-P4 同学核对后定稿。
--
-- 脚本可重复执行：会先 DROP 四张业务表再重建。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS rxt_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE rxt_db;

SET NAMES utf8mb4;

-- 先删后建，保证脚本可反复执行
DROP TABLE IF EXISTS t_operation_log;
DROP TABLE IF EXISTS t_article;
DROP TABLE IF EXISTS t_expert;
DROP TABLE IF EXISTS t_dict;
DROP TABLE IF EXISTS t_user;


-- -----------------------------------------------------------------------------
-- 1. t_user  用户主表
--    五类角色共用一张表，角色差异字段放进 extend_json，避免为每种角色加列。
-- -----------------------------------------------------------------------------
CREATE TABLE t_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT       COMMENT '主键',
    phone         VARCHAR(20)     NOT NULL                      COMMENT '手机号，登录账号，全局唯一',
    password      VARCHAR(100)    NOT NULL                      COMMENT '密码，BCrypt 密文（60 字符，留余量）',
    name          VARCHAR(50)     DEFAULT NULL                  COMMENT '姓名 / 昵称',
    role          VARCHAR(20)     NOT NULL DEFAULT 'farmer'     COMMENT '角色：farmer 农户或买家 / expert 农业专家 / bank 银行人员 / admin 平台管理员',
    status        TINYINT         NOT NULL DEFAULT 1            COMMENT '状态：0 待审核 / 1 正常 / 2 已拒绝 / 3 已禁用',
    province      VARCHAR(30)     DEFAULT NULL                  COMMENT '省',
    city          VARCHAR(30)     DEFAULT NULL                  COMMENT '市',
    district      VARCHAR(30)     DEFAULT NULL                  COMMENT '区县',
    email         VARCHAR(100)    DEFAULT NULL                  COMMENT '邮箱，格式校验在前端与 DTO 层',
    avatar        VARCHAR(255)    DEFAULT NULL                  COMMENT '头像地址',
    extend_json   JSON            DEFAULT NULL                  COMMENT '角色扩展属性：农户=种植规模/主营作物，银行=所属分行/工号，专家=资质信息',
    audit_remark  VARCHAR(255)    DEFAULT NULL                  COMMENT '审核意见，拒绝时必填',
    fail_count    TINYINT         NOT NULL DEFAULT 0            COMMENT '连续登录失败次数，登录成功清零',
    lock_until    DATETIME        DEFAULT NULL                  COMMENT '锁定截止时间，NULL 表示未锁定（连错 5 次锁 30 分钟）',
    last_login_at DATETIME        DEFAULT NULL                  COMMENT '最后登录时间',
    deleted       TINYINT         NOT NULL DEFAULT 0            COMMENT '逻辑删除：0 未删 / 1 已删',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_phone (phone),
    KEY idx_user_role_status (role, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户主表';


-- -----------------------------------------------------------------------------
-- 2. t_expert  专家扩展表
--    仅在管理员审核通过时写入（概要设计 M7 表39：通过 → t_user.status=1 且写入 t_expert）。
-- -----------------------------------------------------------------------------
CREATE TABLE t_expert (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT        COMMENT '主键',
    user_id      BIGINT UNSIGNED NOT NULL                       COMMENT '关联 t_user.id（逻辑外键，不建约束便于分片与灵活删除）',
    real_name    VARCHAR(50)     DEFAULT NULL                   COMMENT '真实姓名',
    title        VARCHAR(50)     DEFAULT NULL                   COMMENT '职称，如 高级农艺师',
    org          VARCHAR(100)    DEFAULT NULL                   COMMENT '所属机构',
    domain       VARCHAR(100)    DEFAULT NULL                   COMMENT '专业领域',
    intro        VARCHAR(500)    DEFAULT NULL                   COMMENT '个人简介',
    cert_url     VARCHAR(255)    DEFAULT NULL                   COMMENT '资质证明文件地址',
    audit_remark VARCHAR(255)    DEFAULT NULL                   COMMENT '审核意见',
    audit_by     BIGINT UNSIGNED DEFAULT NULL                   COMMENT '审核人 t_user.id',
    audit_time   DATETIME        DEFAULT NULL                   COMMENT '审核时间',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_expert_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '专家扩展表，审核通过后写入';


-- -----------------------------------------------------------------------------
-- 3. t_dict  数据字典
--    类型固定四类：产品分类 / 资金用途 / 所属行业 / 贷款期限（M7 表41）。
-- -----------------------------------------------------------------------------
CREATE TABLE t_dict (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT         COMMENT '主键',
    type        VARCHAR(50)     NOT NULL                        COMMENT '字典类型：product_category 产品分类 / fund_usage 资金用途 / industry 所属行业 / loan_term 贷款期限',
    code        VARCHAR(50)     NOT NULL                        COMMENT '字典编码，同类型内唯一',
    name        VARCHAR(50)     NOT NULL                        COMMENT '字典显示名',
    sort        INT             NOT NULL DEFAULT 0              COMMENT '排序号，升序',
    enabled     TINYINT         NOT NULL DEFAULT 1              COMMENT '是否启用：0 停用 / 1 启用',
    remark      VARCHAR(255)    DEFAULT NULL                    COMMENT '备注',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type_code (type, code),
    KEY idx_dict_type_sort (type, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据字典';


-- -----------------------------------------------------------------------------
-- 4. t_operation_log  操作日志
--    概要设计 8.3 要求"绝不把 Java 堆栈暴露给前端"，堆栈进日志表与日志文件。
-- -----------------------------------------------------------------------------
CREATE TABLE t_operation_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT         COMMENT '主键',
    user_id     BIGINT UNSIGNED DEFAULT NULL                    COMMENT '操作人 t_user.id，匿名请求为 NULL',
    phone       VARCHAR(20)     DEFAULT NULL                    COMMENT '操作人手机号，冗余字段便于日志追溯',
    module      VARCHAR(50)     DEFAULT NULL                    COMMENT '模块名',
    operation   VARCHAR(100)    DEFAULT NULL                    COMMENT '操作描述',
    method      VARCHAR(10)     DEFAULT NULL                    COMMENT 'HTTP 方法',
    uri         VARCHAR(255)    DEFAULT NULL                    COMMENT '请求路径',
    params      TEXT            DEFAULT NULL                    COMMENT '请求参数，已脱敏（密码类字段不入库）',
    ip          VARCHAR(64)     DEFAULT NULL                    COMMENT '客户端 IP',
    success     TINYINT         NOT NULL DEFAULT 1              COMMENT '是否成功：0 失败 / 1 成功',
    error_msg   VARCHAR(500)    DEFAULT NULL                    COMMENT '失败原因',
    cost_ms     INT             DEFAULT NULL                    COMMENT '耗时，毫秒',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_log_user_time (user_id, create_time),
    KEY idx_log_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '操作日志';


-- -----------------------------------------------------------------------------
-- 5. t_article  知识文章
--    ⚠️ 这张表是 P1 补建的，不在最初的 4 张表范围内，原因：
--       P1 的 M7 要交付 8 个接口，第 8 个是 PUT /api/article/{id}/audit（文章审核），
--       它定义在 SRS 表58（专家助力模块）里 —— 不在 /api/admin/** 下。
--       表40 要求"查看待审核文章列表 + 查看正文 + 审核"，没有文章表就无法实现。
--    字段按表40 的审核需求 + 表58 的接口反推，需与负责专家模块（P4）的同学核对后定稿。
-- -----------------------------------------------------------------------------
CREATE TABLE t_article (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT        COMMENT '主键',
    title        VARCHAR(200)    NOT NULL                       COMMENT '标题',
    category     VARCHAR(50)     DEFAULT NULL                   COMMENT '分类，取值参考 t_dict',
    content      TEXT            DEFAULT NULL                   COMMENT '正文。纯文本或 HTML 字符串',
    cover_url    VARCHAR(255)    DEFAULT NULL                   COMMENT '封面图地址',
    tags         VARCHAR(255)    DEFAULT NULL                   COMMENT '标签，逗号分隔',
    author_id    BIGINT UNSIGNED NOT NULL                       COMMENT '作者 t_user.id（逻辑外键）',
    author_role  VARCHAR(20)     DEFAULT NULL                   COMMENT '冗余作者角色，便于按"农户待审文章"筛选',
    status       TINYINT         NOT NULL DEFAULT 0             COMMENT '状态：0 待审核 / 1 已发布 / 2 已拒绝 / 3 已下架',
    audit_remark VARCHAR(255)    DEFAULT NULL                   COMMENT '审核意见。拒绝时必填',
    audit_by     BIGINT UNSIGNED DEFAULT NULL                   COMMENT '审核人 t_user.id',
    audit_time   DATETIME        DEFAULT NULL                   COMMENT '审核时间',
    view_count   INT             NOT NULL DEFAULT 0             COMMENT '浏览量',
    deleted      TINYINT         NOT NULL DEFAULT 0             COMMENT '逻辑删除：0 未删 / 1 已删',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_article_status_time (status, create_time),
    KEY idx_article_author (author_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识文章，农户发布需审核、专家直接发布';


-- =============================================================================
-- 种子数据
--   所有种子账号密码统一为 123456，BCrypt cost=10（与概要设计一致）
--   $2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu  ==  123456
-- =============================================================================

INSERT INTO t_user (phone, password, name, role, status, email, province, city, extend_json) VALUES
('13900000000', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '平台管理员', 'admin',  1, 'admin@rxt.com', '浙江省', '杭州市', NULL),
('13900000001', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '银行专员甲', 'bank',   1, 'bank1@rxt.com', '浙江省', '杭州市', NULL),
('13900000002', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '银行专员乙', 'bank',   1, 'bank2@rxt.com', '江苏省', '南京市', NULL),
-- 待审核专家（status=0）：审核通过后应写入 t_expert，资质信息就在 extend_json 里
('13900000003', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '张农艺师',   'expert', 0, 'expert1@rxt.com', '山东省', '潍坊市',
 '{"title":"高级农艺师","org":"潍坊市农业科学院","domain":"设施蔬菜种植","intro":"从事设施蔬菜栽培技术推广 15 年","certUrl":null}'),
('13900000004', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '李农户',     'farmer', 1, 'farmer1@rxt.com', '河南省', '周口市', NULL),
-- 第二个待审核专家：用于测试"审核两次"应被拒绝（审核结果不可撤回）
('13900000005', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '王研究员',   'expert', 0, 'expert2@rxt.com', '河南省', '郑州市',
 '{"title":"副研究员","org":"河南省农业科学院","domain":"小麦育种","intro":"主攻优质强筋小麦品种选育","certUrl":null}'),
-- 已通过的专家：用于验证"已审核的账号不能再被审核"
('13900000006', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu', '赵教授',     'expert', 1, 'expert3@rxt.com', '江苏省', '南京市',
 '{"title":"教授","org":"南京农业大学","domain":"农业经济","intro":"博士生导师，研究方向为农业产业组织","certUrl":null}');

-- 已通过的专家，要有一条对应的 t_expert 记录（表39：审核通过后写入）
INSERT INTO t_expert (user_id, real_name, title, org, domain, intro, audit_remark, audit_by, audit_time)
SELECT id, '赵教授', '教授', '南京农业大学', '农业经济', '博士生导师，研究方向为农业产业组织',
       '资质齐备，予以通过', (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW()
FROM t_user WHERE phone = '13900000006';

INSERT INTO t_dict (type, code, name, sort, remark) VALUES
('product_category', 'GRAIN',    '粮食作物',  1, NULL),
('product_category', 'VEG',      '蔬菜',      2, NULL),
('product_category', 'FRUIT',    '水果',      3, NULL),
('product_category', 'LIVESTOCK','畜禽',      4, NULL),
('product_category', 'AQUATIC',  '水产',      5, NULL),
('product_category', 'CASH',     '经济作物',  6, NULL),
('fund_usage',       'SEED',     '种子化肥',  1, NULL),
('fund_usage',       'MACHINE',  '农机具购置',2, NULL),
('fund_usage',       'GREENHOUSE','大棚建设', 3, NULL),
('fund_usage',       'LAND',     '土地流转',  4, NULL),
('fund_usage',       'BREED',    '畜禽养殖',  5, NULL),
('fund_usage',       'PROCESS',  '加工流通',  6, NULL),
('industry',         'PLANTING', '种植业',    1, NULL),
('industry',         'BREEDING', '养殖业',    2, NULL),
('industry',         'PROCESSING','农产品加工',3, NULL),
('industry',         'SERVICE',  '农业服务',  4, NULL),
('industry',         'ECOMMERCE','农村电商',  5, NULL),
('loan_term',        'M3',       '3 个月',    1, NULL),
('loan_term',        'M6',       '6 个月',    2, NULL),
('loan_term',        'M12',      '12 个月',   3, NULL),
('loan_term',        'M24',      '24 个月',   4, NULL),
('loan_term',        'M36',      '36 个月',   5, NULL);

-- 文章种子：3 篇待审核（农户发布，需审核）+ 1 篇已发布（专家直接发布）
-- 待审核的那几篇就是为了让"文章审核"页一打开就有数据可操作
INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, view_count)
SELECT '大棚番茄春季管理要点', 'VEG',
       '春季大棚番茄定植后，前 7 天要密闭保温促进缓苗，白天保持 25-28℃，夜间不低于 15℃。缓苗后开始通风，白天降到 20-25℃……',
       '番茄,大棚,春季', u.id, 'farmer', 0, 0
FROM t_user u WHERE u.phone = '13900000004';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, view_count)
SELECT '小麦返青期施肥建议', 'GRAIN',
       '返青期是小麦春季管理的第一个关键时期。对于弱苗、晚播苗，应以促为主，每亩追施尿素 8-10 公斤……',
       '小麦,施肥,返青', u.id, 'farmer', 0, 0
FROM t_user u WHERE u.phone = '13900000004';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, view_count)
SELECT '农产品电商包装的常见误区', 'CASH',
       '很多农户做电商时忽略包装成本占比，实际生鲜品类包装与损耗可占到售价的 20% 以上……',
       '电商,包装,生鲜', u.id, 'farmer', 0, 0
FROM t_user u WHERE u.phone = '13900000004';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, audit_remark, audit_by, audit_time, view_count)
SELECT '设施蔬菜连作障碍的成因与对策', 'VEG',
       '连作障碍的本质是土壤微生物区系失衡。轮作、夏季高温闷棚、增施有机肥是三条主要技术路径……',
       '设施蔬菜,连作障碍,土壤', u.id, 'expert', 1, '内容专业，予以发布',
       (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW(), 128
FROM t_user u WHERE u.phone = '13900000006';

-- 追加已发布文章（让文章列表页有内容可看）
INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, audit_remark, audit_by, audit_time, view_count)
SELECT '小麦赤霉病综合防治技术', 'planting',
       '赤霉病重在预防。抽穗扬花期是防治关键窗口，遇连阴雨要抢晴喷药。推荐戊唑醇·咪鲜胺复配，注意轮换用药，延缓抗性。',
       '小麦,病害,防治', u.id, 'expert', 1, '内容专业，予以发布',
       (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW(), 256
FROM t_user u WHERE u.phone = '13900000006';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, audit_remark, audit_by, audit_time, view_count)
SELECT '生猪养殖的常见误区与科学防疫', 'breeding',
       '很多散养户重治疗轻预防，疫苗该打不打、消毒流于形式。科学防疫要做到：程序化免疫、定期驱虫、圈舍勤消毒，发现异常早隔离早处理。',
       '生猪,防疫,养殖', u.id, 'expert', 1, '内容专业，予以发布',
       (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW(), 198
FROM t_user u WHERE u.phone = '13900000006';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, audit_remark, audit_by, audit_time, view_count)
SELECT '农产品品牌化运营：从产地到餐桌', 'market',
       '农产品同质化严重，品牌是溢价的唯一出路。可以从三件事做起：统一品控标准、讲好产地故事、建立可追溯体系，让消费者买得放心。',
       '品牌,电商,运营', u.id, 'expert', 1, '内容专业，予以发布',
       (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW(), 342
FROM t_user u WHERE u.phone = '13900000006';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, audit_remark, audit_by, audit_time, view_count)
SELECT '水稻主要病虫害绿色防控方案', 'planting',
       '绿色防控不是不打药，而是少打药、打对药。推广抗病品种、性诱剂诱杀、天敌保育和科学用药相结合，可减少农药用量 30% 以上。',
       '水稻,病虫害,绿色防控', u.id, 'expert', 1, '内容专业，予以发布',
       (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW(), 175
FROM t_user u WHERE u.phone = '13900000006';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, audit_remark, audit_by, audit_time, view_count)
SELECT '果园水肥一体化技术要点', 'planting',
       '水肥一体化是把水和肥精准送到根系，省工省肥又增产。核心是选对滴灌设备、根据生育期制定施肥方案，并定期冲洗防止堵塞。',
       '果树,水肥,滴灌', u.id, 'expert', 1, '内容专业，予以发布',
       (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW(), 221
FROM t_user u WHERE u.phone = '13900000006';

INSERT INTO t_article (title, category, content, tags, author_id, author_role, status, audit_remark, audit_by, audit_time, view_count)
SELECT '家庭农场的经营账本该怎么算', 'policy',
       '家庭农场要算清两本账：现金账和成本账。很多农场主只看收入不看折旧，年底才发现白忙一场。建议每月盘点，把人工、折旧、利息都摊进成本。',
       '家庭农场,经营,成本', u.id, 'expert', 1, '内容专业，予以发布',
       (SELECT id FROM t_user WHERE phone = '13900000000' LIMIT 1), NOW(), 143
FROM t_user u WHERE u.phone = '13900000006';
