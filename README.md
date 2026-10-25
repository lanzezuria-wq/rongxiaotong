# 融销通

连接农产品交易、三人联保融资与农业专家服务的课程项目。后端使用 Spring Boot / Spring Security / MyBatis-Plus / MySQL，前端使用 Vue 3 / Vite / Element Plus。

当前版本在原有团队项目基础上，完善了 P1 的认证、并发审核、操作审计，并统一改版现有 29 个前端页面，新增 1 个操作日志页面。首页统计来自当前数据库，金融审批、验证码与支付仍为明确标注的演示能力。

## 本次改进

- 改密和账号状态变更递增凭证版本，旧 Token 失效；重新启用账号也不会恢复旧凭证。
- 登录失败计数在账号行锁内更新；只有配置的可信代理可以影响来源 IP 解析。
- 专家、文章审核以原状态作为更新条件，避免并发结果相互覆盖；通用用户管理不能跳过专家审核。
- 用户状态、审核、字典写入与成功审计在同一事务内；失败的管理请求单独记录。日志只保存允许的对象标识，不保存请求正文或 Token。
- 字典被商品或融资申请使用时，不允许删除或改动类型、编码。
- 补齐 HTTP 415 响应，拆分开发与生产配置，生产环境必须注入独立密钥。
- 门户、账户与管理界面统一设计，覆盖手机与平板；首页各区域独立加载和重试；跨标签同步登录状态。
- 按需引入实际使用的组件与图标，主要 JS 构建分块从约 846 kB 降为约 347 kB。该数值仅指主要分块，不是整页总下载量。

设计规范见 [DESIGN.md](DESIGN.md)，本次实现、测试与面试说明见 [改版交付说明](docs/PORTFOLIO-UPGRADE.md)，实际检查范围见 [验收记录](docs/VALIDATION.md)。

## 目录与团队分工

| 目录 | 内容 |
| --- | --- |
| `demo1/` | Java 17 / Spring Boot 后端 |
| `rxt-frontend/` | Vue 3 前端，29 个原有页面与操作日志页 |
| `demo1/src/main/resources/sql/` | 原始演示初始化脚本及本次增量迁移 |
| `docs/` | 原团队文档与本次交付说明 |
| `.github/workflows/verify.yml` | 后端 MySQL 回归、前端测试与构建 |

原团队：P1 黄国俊负责 M0 / M1 / M7；P2 刘德安负责 M2 / M3；P3 潘国兴负责 M4 / M5 / M6；P4 胡超凡负责前端与联调。本次后续改进与原团队贡献分别记录，不将原有业务归为个人独立开发。

## 本地运行

需要 JDK 17、Maven 3.9+、MySQL 8 和 Node.js 22.12+。配置使用默认 `dev` profile；若有被忽略的本地 `config/` 配置，注意它可能覆盖类路径配置，必要时用命令行参数明确指定连接。

**新建、可丢弃的演示数据库**按下列顺序初始化：

```bash
mysql -uroot -p < demo1/src/main/resources/sql/init.sql
mysql -uroot -p < demo1/src/main/resources/sql/init_trade.sql
mysql -uroot -p < demo1/src/main/resources/sql/init_p3.sql
mysql -uroot -p rxt_db < demo1/src/main/resources/sql/migrate_portfolio.sql
```

前三份是重置脚本，含 DROP TABLE，不能用于已有业务数据的升级。已有数据库只需在备份后运行本次 `migrate_portfolio.sql`，该脚本仅追加必要字段，可以重复执行。迁移后，旧格式 Token 需要重新登录。

设置本机数据库连接：

```bash
export RXT_DB_URL='jdbc:mysql://127.0.0.1:3306/rxt_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export RXT_DB_USER=root
export RXT_DB_PASSWORD='你的本机数据库密码'
mvn -f demo1/pom.xml spring-boot:run
```

另开终端启动前端：

```bash
cd rxt-frontend
npm ci
npm run dev
```

访问前端 `http://localhost:5173`；后端健康检查为 `http://localhost:8080/api/ping`。后端端口不同时，前端用 `VITE_API_TARGET=http://127.0.0.1:目标端口 npm run dev` 指定代理。

构建和运行 JAR：

```bash
mvn -f demo1/pom.xml -DskipTests package
java -jar demo1/target/rxt-backend-0.0.1-SNAPSHOT.jar
```

## 演示账号

以下只用于原始演示库，密码均为 `123456`。开发环境注册验证码为 `111111`。

| 手机号 | 身份 | 用途 |
| --- | --- | --- |
| 13900000000 | 平台管理员 | 用户、专家、文章、字典及操作日志 |
| 13900000001 | 银行专员 | 小组审批与统计 |
| 13900000004 | 农户 | 商品销售与已有融资记录 |
| 13900000008 | 买家 | 购买他人商品与订单结算 |
| 13900000006 | 已审核专家 | 资料与问答 |
| 13900000003 | 待审核专家 | 演示管理员审核后登录 |
| 13900000013 | 待匹配申请的农户 | 创建招募中的联保小组 |
| 13900000014 | 无申请的农户 | 从头填写融资申请 |

登录页角色快捷填入仅在开发环境或 `VITE_DEMO_MODE=true` 构建中显示。用户资料和业务记录以当前数据库为准，操作演示后状态可能改变。

## 测试

后端集成测试依赖 MySQL 与上述演示表结构。必须使用单独的测试数据库或独立 MySQL 实例，不对个人业务数据库运行初始化脚本。

```bash
mvn -f demo1/pom.xml test \
  -Dspring.datasource.url='jdbc:mysql://127.0.0.1:测试端口/rxt_db?serverTimezone=Asia/Shanghai' \
  -Dspring.datasource.username=测试用户 \
  -Dspring.datasource.password=测试密码
cd rxt-frontend
npm test
npm run build
```

本次在独立 MySQL 8.0.37 实例验证：后端 105 个用例全部通过（原有 94 + 新增 11），前端 3 个用例全部通过。新增用例包括改密失效、重新启用后的旧凭证失效、HTTP 415、审核边界、审计权限和脱敏、字典引用保护、代理链、并发失败计数与并发审核。

CI 使用自己的 MySQL 服务容器，分别执行后端测试打包、前端测试构建。配置文件已提供，远端流水线需提交到 GitHub 后才会触发，不能把本地验证当作远端检查通过。

## Docker 本地演示

复制 `.env.example` 为 `.env`，替换数据库密码和至少 32 字节的独立签名密钥，再运行：

```bash
docker compose up --build
```

打开 `http://localhost:8088`。该配置明确使用演示 profile，端口只绑定本机；它不是实际金融业务的生产部署。数据库持久化到专用卷，仅空卷首次启动时初始化。已有卷升级时应单独执行增量迁移，不能删卷作为升级方式。

## 部署配置边界

真实部署必须选择 `prod`，并设置 `RXT_DB_URL`、`RXT_DB_USER`、`RXT_DB_PASSWORD`、`RXT_JWT_SECRET`。缺少或过短的密钥会阻止启动；默认演示密钥只能用于开发。可信代理通过 `RXT_TRUSTED_PROXIES` 显式配置 IP 或 CIDR，默认不信任转发头。生产 profile 关闭接口文档和固定验证码，短信服务未接入前不开放注册。

当前仍未接入真实支付、短信、征信与放款；IP 限流为单实例内存实现；Token 继续以 localStorage 保存。Docker 配置本轮仅完成解析检查，未运行容器环境。

## GitHub 与历史资料

仓库：[lanzezuria-wq/rongxiaotong](https://github.com/lanzezuria-wq/rongxiaotong)。本地 `github` remote 指向 GitHub，`origin` 保留原华为云 CodeHub，提交前先确认目标。改版在 `codex/rxt-portfolio-upgrade` 分支完成，本地实现不代表已推送或合并。

`docs/项目交付总文档.md`、`docs/P3-项目文档.md` 等保留原团队交付记录，涉及当时完成度、接口数、平台统计与限制的描述属于历史版本。本次事实以代码、测试报告与改版交付说明为准。

照片来源、作者与许可完整列在 `rxt-frontend/public/images/credits.html` 和相邻 JSON 文件，页面页脚可直接打开署名页。演示商品摄影不冒充真实商品实拍。
