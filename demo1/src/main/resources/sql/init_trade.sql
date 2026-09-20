-- =============================================================================
-- 融销通 · P2 数据库初始化脚本  init_trade.sql
-- 数据库：rxt_db（沿用 P1 建的库）      字符集：utf8mb4 / utf8mb4_unicode_ci
-- 覆盖范围：P2 交付边界（M2 首页 + M3 农产品交易）
--
-- 执行顺序：先 init.sql（P1 的表 + 用户），再本脚本。
-- 脚本可反复执行：会先 DROP 六张 P2 表再重建。
--
-- [设计说明 —— 与任务包 06-数据库初始化脚本.sql 的差异，均为有意为之]
--   1. 任务包 17 张表里**没有 t_cart**，但接口契约有 4 个购物车接口。
--      本脚本补建 t_cart，字段依据 CartVO + POST /cart 的契约反推。
--   2. 审计字段统一用 create_time / update_time（跟 P1 的 t_user 一致），
--      不用任务包的 created_at / updated_at —— 同一个库里两套命名太乱。
--   3. 商品分类字段用 category_code（与任务包建表脚本一致），
--      任务清单里写的 category_id 是笔误。取值来自 t_dict 的 type=product_category。
--   4. 不建外键约束，只做逻辑外键（和 P1 一致，避免和逻辑删除打架）。
-- =============================================================================

USE rxt_db;

SET NAMES utf8mb4;

-- 先删后建，保证脚本可反复执行（按依赖倒序删）
DROP TABLE IF EXISTS t_order_item;
DROP TABLE IF EXISTS t_order;
DROP TABLE IF EXISTS t_cart;
DROP TABLE IF EXISTS t_address;
DROP TABLE IF EXISTS t_product_demand;
DROP TABLE IF EXISTS t_product;

-- =============================================================================
-- 1. 商品表
-- =============================================================================
CREATE TABLE t_product (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  farmer_id     BIGINT UNSIGNED NOT NULL              COMMENT '发布者（农户）用户 id，逻辑外键 → t_user.id',
  name          VARCHAR(50)     NOT NULL              COMMENT '商品名称，≤50 字',
  category_code VARCHAR(50)     NOT NULL              COMMENT '分类编码，取值 t_dict.type=product_category',
  price         DECIMAL(10,2)   NOT NULL              COMMENT '单价（元），精度 0.01',
  stock         INT             NOT NULL DEFAULT 0    COMMENT '总库存，≥0',
  unit          VARCHAR(10)     DEFAULT NULL          COMMENT '计量单位：斤/箱/盒/袋',
  description   TEXT                                  COMMENT '商品描述',
  images        VARCHAR(1000)   DEFAULT NULL          COMMENT '图片 URL 的 JSON 数组，1~6 张',
  sku_list      VARCHAR(2000)   DEFAULT NULL          COMMENT '规格 JSON：[{spec,price,stock}]，无规格商品为空',
  view_count    INT             NOT NULL DEFAULT 0    COMMENT '浏览量，查详情 +1',
  sales_count   INT             NOT NULL DEFAULT 0    COMMENT '销量，订单完成时累加',
  status        TINYINT         NOT NULL DEFAULT 1    COMMENT '状态：0 下架 / 1 上架 / 2 已删除',
  create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_farmer (farmer_id),
  KEY idx_category (category_code),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- =============================================================================
-- 2. 求购需求表（M3 附属，任务包标注"可延后"）
-- =============================================================================
CREATE TABLE t_product_demand (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT UNSIGNED NOT NULL              COMMENT '发布求购的用户 id',
  product_name VARCHAR(50)     NOT NULL              COMMENT '求购品名',
  category_code VARCHAR(50)    DEFAULT NULL          COMMENT '期望分类，t_dict.type=product_category',
  min_price    DECIMAL(10,2)   DEFAULT NULL          COMMENT '可接受最低价',
  max_price    DECIMAL(10,2)   DEFAULT NULL          COMMENT '可接受最高价',
  quantity     INT             DEFAULT NULL          COMMENT '求购数量',
  unit         VARCHAR(10)     DEFAULT NULL          COMMENT '单位',
  description  TEXT                                  COMMENT '补充说明',
  expire_at    DATETIME        DEFAULT NULL          COMMENT '有效期',
  status       TINYINT         NOT NULL DEFAULT 1    COMMENT '状态：0 已关闭 / 1 有效',
  create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='求购需求表';

-- =============================================================================
-- 3. 购物车表  ⚠️ 任务包遗漏，本脚本补建
--    只存"谁要买什么、买几个"；商品名/图/价格/库存都是查询时 join t_product 实时取，
--    否则商品改价后购物车还显示旧价。
-- =============================================================================
CREATE TABLE t_cart (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT UNSIGNED NOT NULL              COMMENT '归属用户 id',
  product_id  BIGINT UNSIGNED NOT NULL              COMMENT '商品 id，逻辑外键 → t_product.id',
  sku_spec    VARCHAR(100)    NOT NULL DEFAULT ''   COMMENT '规格描述，无规格商品为空串（配合唯一索引）',
  quantity    INT             NOT NULL DEFAULT 1    COMMENT '数量，1~999',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 同一用户 + 同一商品 + 同一规格只保留一条，重复加购走数量累加而不是插新行。
  -- 注意 sku_spec 用 NOT NULL DEFAULT '' 而不是 NULL —— MySQL 唯一索引不约束 NULL，
  -- 用 NULL 的话同一无规格商品能被重复插入多行。
  UNIQUE KEY uk_user_product_sku (user_id, product_id, sku_spec),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车';

-- =============================================================================
-- 4. 收货地址表
-- =============================================================================
CREATE TABLE t_address (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT UNSIGNED NOT NULL              COMMENT '归属用户 id',
  receiver    VARCHAR(50)     NOT NULL              COMMENT '收货人姓名',
  phone       VARCHAR(11)     NOT NULL              COMMENT '收货人手机号',
  province    VARCHAR(20)     DEFAULT NULL          COMMENT '省',
  city        VARCHAR(20)     DEFAULT NULL          COMMENT '市',
  district    VARCHAR(20)     DEFAULT NULL          COMMENT '区县',
  detail      VARCHAR(255)    NOT NULL              COMMENT '详细地址',
  is_default  TINYINT         NOT NULL DEFAULT 0    COMMENT '是否默认地址：0 否 / 1 是（同一用户至多一条为 1）',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址';

-- =============================================================================
-- 5. 订单表
-- =============================================================================
CREATE TABLE t_order (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no        VARCHAR(32)     NOT NULL              COMMENT '订单号：RXT+yyyyMMddHHmmss+6位随机',
  buyer_id        BIGINT UNSIGNED NOT NULL              COMMENT '买家用户 id',
  farmer_id       BIGINT UNSIGNED NOT NULL              COMMENT '卖家（农户）用户 id，发货权限归属',
  total_amount    DECIMAL(10,2)   NOT NULL              COMMENT '订单总额',
  status          TINYINT         NOT NULL DEFAULT 0    COMMENT '0 待付款 / 1 待发货 / 2 已发货 / 3 已完成 / 4 已取消',
  address_id      BIGINT UNSIGNED DEFAULT NULL          COMMENT '收货地址 id（下单时快照引用）',
  express_company VARCHAR(50)     DEFAULT NULL          COMMENT '快递公司，发货时填',
  express_no      VARCHAR(50)     DEFAULT NULL          COMMENT '快递单号，发货时填',
  paid_at         DATETIME        DEFAULT NULL          COMMENT '支付时间',
  shipped_at      DATETIME        DEFAULT NULL          COMMENT '发货时间',
  received_at     DATETIME        DEFAULT NULL          COMMENT '收货时间',
  cancelled_at    DATETIME        DEFAULT NULL          COMMENT '取消时间',
  expire_at       DATETIME        DEFAULT NULL          COMMENT '待付款超时时间，创建时置 now+15 分钟',
  create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_buyer (buyer_id),
  KEY idx_farmer (farmer_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单';

-- =============================================================================
-- 6. 订单明细表
--    price / product_name 是**下单时的快照**，不是外键查出来的 ——
--    否则卖家改价或改名，历史订单的金额和品名会跟着变。
-- =============================================================================
CREATE TABLE t_order_item (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id     BIGINT UNSIGNED NOT NULL              COMMENT '订单 id，逻辑外键 → t_order.id',
  product_id   BIGINT UNSIGNED NOT NULL              COMMENT '商品 id',
  product_name VARCHAR(50)     DEFAULT NULL          COMMENT '商品名快照',
  sku_spec     VARCHAR(100)    DEFAULT NULL          COMMENT '规格快照',
  price        DECIMAL(10,2)   NOT NULL              COMMENT '成交单价快照',
  quantity     INT             NOT NULL              COMMENT '购买数量',
  subtotal     DECIMAL(10,2)   NOT NULL              COMMENT '小计 = price * quantity',
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细';

-- =============================================================================
-- 种子数据
-- =============================================================================

-- 买家账号：P1 种子用户里只有"李农户"一个 farmer，演示"买家下单 → 卖家发货 → 买家收货"
-- 需要两个不同的账号，所以这里补一个。角色仍用 farmer（SRS：农户与买家共用 farmer）。
-- 密码 123456，哈希与 P1 种子账号完全相同。
INSERT IGNORE INTO t_user (phone, password, name, role, status, province, city, district, email, extend_json)
VALUES ('13900000008', '$2a$10$DkHWviYNMEyP0hAXGFFkruu6F7wnvYyU1UWlxE4AfGhvC1mVgcQlu',
        '陈买家', 'farmer', 1, '湖北省', '武汉市', '洪山区', 'buyer@rxt.test',
        JSON_OBJECT('note', 'P2 演示用买家账号'));

-- 用变量取 id，避免写死自增值（init.sql 重跑后 id 可能变）
SET @farmer_id = (SELECT id FROM t_user WHERE phone = '13900000004');
SET @buyer_id  = (SELECT id FROM t_user WHERE phone = '13900000008');

-- 商品：李农户发布 8 个，覆盖 6 个分类
INSERT INTO t_product
  (farmer_id, name, category_code, price, stock, unit, description, images, sku_list, view_count, sales_count, status)
VALUES
  (@farmer_id, '五常稻花香大米 25kg', 'GRAIN', 168.00, 100, '袋',
   '黑龙江五常产地直发，2026 年新米，颗粒饱满，米香浓郁。', '["https://picsum.photos/seed/rice/600/400"]',
   '[{"spec":"25kg/袋","price":168.00,"stock":100},{"spec":"10kg/袋","price":72.00,"stock":150}]',
   328, 46, 1),

  (@farmer_id, '山东烟台红富士苹果 10斤', 'FRUIT', 45.50, 200, '箱',
   '脆甜多汁，果径 80mm 以上，产地冷链直发。', '["https://picsum.photos/seed/apple/600/400"]',
   '[{"spec":"10斤/箱","price":45.50,"stock":200},{"spec":"5斤/箱","price":24.00,"stock":300}]',
   512, 128, 1),

  (@farmer_id, '有机小白菜 5斤', 'VEG', 19.90, 150, '斤',
   '不打农药，当日采摘当日发，适合清炒做汤。', '["https://picsum.photos/seed/veg/600/400"]',
   NULL, 176, 63, 1),

  (@farmer_id, '散养土鸡蛋 30枚', 'LIVESTOCK', 39.90, 80, '盒',
   '山林散养，蛋黄色泽金黄，无腥味。', '["https://picsum.photos/seed/egg/600/400"]',
   '[{"spec":"30枚/盒","price":39.90,"stock":80},{"spec":"60枚/盒","price":76.00,"stock":40}]',
   289, 91, 1),

  (@farmer_id, '洪湖大闸蟹 8只装', 'AQUATIC', 288.00, 30, '盒',
   '洪湖湖区养殖，公蟹 4 两、母蟹 3 两，顺丰包活。', '["https://picsum.photos/seed/crab/600/400"]',
   '[{"spec":"8只/盒（公4母4）","price":288.00,"stock":30}]',
   431, 22, 1),

  (@farmer_id, '云南小粒咖啡豆 1kg', 'CASH', 88.00, 60, '袋',
   '海拔 1500 米种植，中深烘焙，现磨手冲皆宜。', '["https://picsum.photos/seed/coffee/600/400"]',
   '[{"spec":"1kg/袋（整豆）","price":88.00,"stock":60},{"spec":"1kg/袋（现磨）","price":92.00,"stock":45}]',
   203, 37, 1),

  (@farmer_id, '赣南脐橙 20斤', 'FRUIT', 69.90, 120, '箱',
   '江西赣州正宗脐橙，皮薄无籽，酸甜比佳。', '["https://picsum.photos/seed/orange/600/400"]',
   '[{"spec":"20斤/箱","price":69.90,"stock":120},{"spec":"10斤/箱","price":38.00,"stock":180}]',
   367, 84, 1),

  (@farmer_id, '东北非转基因大豆 50kg', 'GRAIN', 220.00, 50, '袋',
   '黑龙江非转基因黄豆，颗粒均匀，适合做豆腐和榨油。', '["https://picsum.photos/seed/soybean/600/400"]',
   NULL, 95, 11, 1),

  (@farmer_id, '新疆库尔勒香梨 10斤', 'FRUIT', 59.90, 150, '箱',
   '库尔勒核心产区直发，皮薄肉细、汁多味甜，冷藏后口感更佳。', '["https://picsum.photos/seed/pear/600/400"]',
   '[{"spec":"10斤/箱","price":59.90,"stock":150},{"spec":"5斤/箱","price":32.00,"stock":200}]',
   245, 57, 1),

  (@farmer_id, '云南高山娃娃菜 5斤', 'VEG', 15.80, 200, '斤',
   '高原昼夜温差大，口感清甜脆嫩，适合上汤、清炒。', '["https://picsum.photos/seed/babycabbage/600/400"]',
   NULL, 132, 74, 1),

  (@farmer_id, '内蒙古羔羊肉 3斤', 'LIVESTOCK', 128.00, 60, '斤',
   '草原散养羔羊，肉质细嫩无膻味，顺丰冷链锁鲜。', '["https://picsum.photos/seed/lamb/600/400"]',
   '[{"spec":"3斤/份","price":128.00,"stock":60}]',
   198, 36, 1),

  (@farmer_id, '舟山带鱼 5斤', 'AQUATIC', 98.00, 80, '斤',
   '东海舟山渔场直供，肉质肥厚，红烧清蒸皆宜。', '["https://picsum.photos/seed/hairtail/600/400"]',
   NULL, 156, 48, 1),

  (@farmer_id, '宁夏中宁枸杞 1kg', 'CASH', 68.00, 120, '袋',
   '中宁核心产区，颗粒饱满，可直接泡水或入汤。', '["https://picsum.photos/seed/goji/600/400"]',
   '[{"spec":"1kg/袋","price":68.00,"stock":120},{"spec":"500g/袋","price":36.00,"stock":180}]',
   289, 92, 1),

  (@farmer_id, '山西沁州黄小米 10斤', 'GRAIN', 49.90, 180, '袋',
   '沁州黄小米，米油厚、易熬出米油，养胃好选择。', '["https://picsum.photos/seed/millet/600/400"]',
   '[{"spec":"10斤/袋","price":49.90,"stock":180},{"spec":"5斤/袋","price":26.00,"stock":240}]',
   214, 68, 1),

  (@farmer_id, '海南贵妃芒 8斤', 'FRUIT', 39.90, 160, '箱',
   '三亚直发，皮薄核小、香甜多汁，树上熟采摘。', '["https://picsum.photos/seed/mango/600/400"]',
   NULL, 176, 81, 1),

  (@farmer_id, '温县铁棍山药 10斤', 'VEG', 42.00, 140, '箱',
   '垆土铁棍山药，粉糯绵密，炖汤蒸食皆宜。', '["https://picsum.photos/seed/yam/600/400"]',
   NULL, 167, 55, 1),

  (@farmer_id, '林下散养土鸡 1只', 'LIVESTOCK', 88.00, 90, '只',
   '林地散养 180 天以上，现杀现发，煲汤鲜美。', '["https://picsum.photos/seed/chicken/600/400"]',
   '[{"spec":"1只（约2.5斤）","price":88.00,"stock":90}]',
   203, 43, 1),

  (@farmer_id, '阳澄湖大闸蟹 6只装', 'AQUATIC', 228.00, 45, '盒',
   '阳澄湖湖区养殖，公母各半，顺丰包活到家。', '["https://picsum.photos/seed/crab2/600/400"]',
   '[{"spec":"6只/盒（公3母3）","price":228.00,"stock":45}]',
   312, 29, 1);

-- 收货地址：买家一个默认地址，卖家一个
INSERT INTO t_address (user_id, receiver, phone, province, city, district, detail, is_default)
VALUES
  (@buyer_id,  '陈买家', '13900000008', '湖北省', '武汉市', '洪山区', '珞喻路 1037 号华中科技大学东九楼 501', 1),
  (@farmer_id, '李农户', '13900000004', '山东省', '潍坊市', '寿光市', '稻田镇崔岭西村 27 号', 1);

-- 演示订单：一个待付款、一个已完成，方便前端"我的订单"页面有数据可调试
SET @addr_buyer = (SELECT id FROM t_address WHERE user_id = @buyer_id AND is_default = 1);
SET @p_rice     = (SELECT id FROM t_product  WHERE name = '五常稻花香大米 25kg');
SET @p_apple    = (SELECT id FROM t_product  WHERE name = '山东烟台红富士苹果 10斤');

-- 订单 1：待付款（15 分钟超时未付，用来演示超时取消）
INSERT INTO t_order
  (order_no, buyer_id, farmer_id, total_amount, status, address_id, expire_at)
VALUES
  (CONCAT('RXT', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), '100001'),
   @buyer_id, @farmer_id, 336.00, 0, @addr_buyer, DATE_ADD(NOW(), INTERVAL 15 MINUTE));
SET @order1 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, product_name, sku_spec, price, quantity, subtotal)
VALUES (@order1, @p_rice, '五常稻花香大米 25kg', '25kg/袋', 168.00, 2, 336.00);

-- 订单 2：已完成（走完全流程，用来展示历史订单和状态名）
INSERT INTO t_order
  (order_no, buyer_id, farmer_id, total_amount, status, address_id,
   express_company, express_no, paid_at, shipped_at, received_at)
VALUES
  (CONCAT('RXT', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), '100002'),
   @buyer_id, @farmer_id, 45.50, 3, @addr_buyer,
   '顺丰速运', 'SF1234567890',
   DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @order2 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, product_name, sku_spec, price, quantity, subtotal)
VALUES (@order2, @p_apple, '山东烟台红富士苹果 10斤', '10斤/箱', 45.50, 1, 45.50);

-- =============================================================================
-- 校验：执行完应该能看到 8 个商品 / 2 个订单 / 2 个订单明细 / 2 个地址
-- =============================================================================
SELECT '商品' AS 表, COUNT(*) AS 条数 FROM t_product
UNION ALL SELECT '订单', COUNT(*) FROM t_order
UNION ALL SELECT '订单明细', COUNT(*) FROM t_order_item
UNION ALL SELECT '收货地址', COUNT(*) FROM t_address
UNION ALL SELECT '购物车', COUNT(*) FROM t_cart;
