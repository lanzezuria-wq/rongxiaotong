# 融销通 · 后端

农业金融服务平台。**基于数字经济的农产品融销一体平台** ——
农户既能卖农产品（销），也能通过**联保小组**获得农业贷款（融），
还能获得农业专家的知识文章、问答与预约指导（专家助力）。

```
springbo/
├── demo1/          后端（Spring Boot 4.1.1 + Java 17），包名 com.rxt
│   └── src/main/resources/sql/   建表脚本（3 个，顺序执行）
├── rxt-frontend/   前端（Vue 3 + Vite + Element Plus），P4 负责
├── tools/          联调辅助脚本（P3 端到端验证）
└── docs/           设计与交付文档
    ├── 项目交付总文档.md     ← 交付总览：进度 / 文件清单 / 数据库 / 提交命令（先看这个）
    ├── P4-前端任务说明.md    ← 给 P4 的交接文档
    ├── P3-项目文档.md        ← 后端统一参考手册（技术栈/包结构/DB/接口/设计/坑）
    └── P2-实施方案.md        ← P2 的方案（历史存档）
```

---

## 一、四个人的交付范围

| 角色 | 模块 | 后端接口 | 状态 |
|---|---|---|---|
| **P1** | 后端地基 + M1 用户体系 + M7 系统管理 | 15 个 | ✅ 已合入 master |
| **P2** | M2 首页 + M3 农产品交易 | 25 个 | ✅ 已合入 master |
| **P3** | M4 融资服务 + M6 融资管理 + M5 专家助力 | 23 个 | ✅ 已完成 |
| **P4** | 前端 18 个页面 + 联调 | — | ⏳ 进行中 |

合计 **63 个 HTTP 接口**（含 1 个健康检查）、**19 张表**、**168 个 Java 文件**。

> 📖 **想快速了解整体情况（进度 / 每个文件的作用 / 数据库 / 怎么提交），
> 看 [`docs/项目交付总文档.md`](docs/项目交付总文档.md)**；
> **想了解后端的设计决策与"为什么"**，看 [`docs/P3-项目文档.md`](docs/P3-项目文档.md)。
> 本 README 只讲"怎么把它跑起来"。

---

## 二、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | |
| Maven | 3.9+ | |
| MySQL | 8.0 | 需已启动 |
| Node.js | 20.19+ / 22.12+ | Vite 8 的要求 |

数据库连接写在 `demo1/src/main/resources/application.yml`：

```yaml
url: jdbc:mysql://127.0.0.1:3306/rxt_db?...
username: root
password: "123456"      # ← 换机器要改这里
```

---

## 三、启动步骤

### 1. 初始化数据库（只需做一次）

**三个脚本，顺序不能反**（后面的依赖前面的表和种子账号）：

```bash
mysql -uroot -p < demo1/src/main/resources/sql/init.sql
mysql -uroot -p < demo1/src/main/resources/sql/init_trade.sql
mysql -uroot -p < demo1/src/main/resources/sql/init_p3.sql
```

| 脚本 | 归属 | 内容 |
|---|---|---|
| `init.sql` | P1 | 建库 + 5 张表 + 7 个种子账号 + 22 条字典 + 4 篇示例文章 |
| `init_trade.sql` | P2 | 6 张表 + 1 个演示买家 + 8 个商品 + 2 个订单 |
| `init_p3.sql` | P3 | 8 张表 + 给 2 张 P1 表补 4 列 + 6 个账号 + 融资/小组/提问/预约样例 |

三个脚本**都是幂等的**（先 `DROP` 自己的表再建；补列用存储过程判断存在性；
种子账号用 `INSERT IGNORE`），可以反复执行。

> **为什么不合并成一个脚本？** 因为那样同一份 DDL 会存在两处，
> 四个人并行开发时谁改了列却只改一处就会静默不一致。
> 每个模块一个脚本、只 DROP 自己的表，职责边界清楚 ——
> 三个人各自独立地做出了同一个选择。

### 2. 启动后端

在 IDEA 里运行 `com.rxt.RxtApplication`，或命令行：

```bash
cd demo1
mvn spring-boot:run
```

验证：<http://localhost:8080/api/ping> 应返回

```json
{"code":0,"data":{"status":"UP","application":"rxt-backend"},"message":"服务运行正常","success":true}
```

接口文档（Swagger UI）：<http://localhost:8080/swagger-ui.html>
（先调登录拿 Token，点右上角 Authorize 填进去，后续接口会自动带 Header）

### 3. 启动前端

```bash
cd rxt-frontend
npm install          # 首次需要
npm run dev          # http://localhost:5173
```

> 若后端不在 8080：`VITE_API_TARGET=http://127.0.0.1:9000 npm run dev`

---

## 四、测试账号

密码统一 `123456`（BCrypt cost=10）；短信验证码在产品演示环境**固定 `111111`**。

| 手机号 | 姓名 | 角色 | 状态 | 用途 |
|---|---|---|---|---|
| 13900000000 | 平台管理员 | admin | 正常 | 后台全部功能（M7） |
| 13900000001 / 02 | 银行专员甲 / 乙 | bank | 正常 | **M6 银行审批** |
| 13900000003 / 05 | 张农艺师 / 王研究员 | expert | **待审核** | 测专家审核流程 |
| 13900000004 | 李农户 | farmer | 正常 | **已在成型的演示小组里**，可直接给银行端演示审批；P2 的商品也属于他 |
| 13900000006 | 赵教授 | expert | 正常 | 已审核专家，`t_expert.id = 1` |
| 13900000008 | 陈买家 | farmer | 正常 | P2 补的演示买家 |
| 13900000011 / 12 | 王大山 / 陈秀兰 | farmer | 正常 | P3 补：演示小组成员 |
| 13900000013 | 刘四海 | farmer | 正常 | P3 补：**有一条待匹配申请**，可直接加入小组 |
| 13900000014 | 孙小满 | farmer | 正常 | P3 补：没有申请，测"从零提交" |
| 13900000015 / 16 | 钱牧之 / 周文博 | expert | 正常 | P3 补：测专家列表筛选 |

> **手机号分段约定**（新增账号请避开已用段位）：
> `0~06` P1 ｜ `08` P2 ｜ `11~14` P3 农户、`15~16` P3 专家 ｜ `07/09/10` 留空备用

---

## 五、测试

```bash
cd demo1
mvn test
```

| 测试类 | 用例数 | 验证内容 |
|---|---|---|
| `RxtApplicationTests` | 1 | Spring 容器 + 数据源能起来 |
| `UserServiceM1Test` | 14 | P1：Service 层业务规则（`@Transactional` 自动回滚） |
| `ApiIntegrationTest` | 26 | P1/P2：端到端（MockMvc 走完整 Security 过滤器链） |
| `P3ApiIntegrationTest` | 53 | P3：M4/M6/M5 全链路 + 业务边界 + 回归 |

**合计 94 个用例，实测全绿。**

### 还有一个真实进程级的验收脚本

```bash
python tools/p3-e2e-test.py
```

打真实后端 + 真实 MySQL，**113 项断言**覆盖 P3 的 23 个接口，
额外覆盖中文文案、查询参数编码等 MockMvc 不方便验的点。
前置条件见脚本头部注释（**跑之前要重跑三个 SQL 脚本重置数据**）。

> 两者并存是刻意的：JUnit 进 `mvn test` 做日常回归（不用起服务），
> 脚本用于"部署完之后到底通不通"的验收。

---

## 六、六个必须知道的坑

### 版本坑（照抄网上教程必错）

1. **Spring Security 7 移除了 `.and()`**，只能用 Lambda DSL；
   `antMatchers` → `requestMatchers`、`@EnableGlobalMethodSecurity` → `@EnableMethodSecurity`。
   本仓库用 Spring Boot 4.1.1，配套的是 Security **7.1.x**（由父 pom 管理，不要手写版本号）。
2. **springdoc 必须用 3.x**。文档里写的 2.3.x 只支持 Boot 3；
   在 Boot 4 上引 2.x 的表现是"应用能启动，但 `/swagger-ui.html` 一直 404"。
3. **MyBatis-Plus 的分页不注册插件就不生效**，而且**不报错**——
   会把全表查出来塞进 `Page`，`total` 还是对的，肉眼看接口完全正常。
   必须注册 `PaginationInnerInterceptor`（见 `config/MybatisPlusConfig`），
   并单独引 `mybatis-plus-jsqlparser`（分页插件靠它解析 SQL）。
4. **依赖版本交给父 pom 管理**，只有 Boot 没管理的（如 `mysql-connector-j`、
   `hutool-all`、`jjwt`、`mybatis-plus-*`、`springdoc`）才在 `demo1/pom.xml` 里显式写版本。

### 数据与序列化坑

5. **`t_financing_apply.usage` 是 MySQL 关键字。**
   建表脚本里写了反引号所以 DDL 正常，但 MyBatis-Plus 自动生成的 INSERT **不带反引号**
   → 运行期直接 `SQLSyntaxErrorException`（编译和启动都发现不了）。
   修法：实体字段加 `@TableField("`usage`")`。
   **以后加列名是 SQL 关键字的字段，记得同样处理。**
6. **响应体字段是按字母序输出的**，不是 VO 里的声明顺序。
   所以取值一定要按字段名，不要按位置；
   另外注意详情类接口里可能有**同名字段**（例如 `GET /api/financing/{id}` 里
   申请自己的 `status` 和嵌套 `group.status`），"取第一个"会拿错。

### 本机环境坑（换机器可忽略）

- `JAVA_HOME` 被设成了无效路径，直接跑 `mvn` 会报
  `找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher`，
  需要显式指定 JDK 17 + Maven，且**必须用 `F:/...` 这种 Windows 风格路径**。
- surefire 跨盘会崩（`'other' has different root`），用 `mvn test -DforkCount=0`；
  注意此时 `-DargLine=` 失效，要把配置作为 **Maven 系统属性**传。

---

## 七、文档索引

| 文档 | 内容 |
|---|---|
| [`docs/项目交付总文档.md`](docs/项目交付总文档.md) | **交付总览与索引（先看这个）**：四人分工与当前进度、仓库目录说明、**168 个 Java 文件逐文件说明**、**19 张表逐字段说明**、11 个种子账号、P4 待办摘要、**git 提交命令**、如何跑起来 |
| [`docs/P4-前端任务说明.md`](docs/P4-前端任务说明.md) | **给 P4 的交接文档**：11 个与 P3 对接的页面逐页接口对照、字段映射、状态机、5 个会返工的坑、错误码、验收清单 |
| [`docs/P3-项目文档.md`](docs/P3-项目文档.md) | **后端统一参考手册**：技术栈与版本坑、架构与包结构、数据库设计与冗余列策略、63 个接口总览、P3 的两条状态机与关键设计、安全与权限模型、统一约定、11 处契约偏差与待定稿清单、构建运行测试、协作规范与文件归属、已知问题与后续计划 |
| [`docs/P2-实施方案.md`](docs/P2-实施方案.md) | P2 的方案：为什么继续用单模块、`t_cart` 表为什么自己建、订单状态机的 7 条铁律、发现的 6 个文档问题 |
| Swagger UI | 运行后端后访问 `http://localhost:8080/swagger-ui.html`，字段级定义 |
| 任务包 `08-OpenAPI接口契约.yaml` | 权威接口契约（注意第九节的偏差清单） |

---

## 八、协作纪律（四个人都会碰到）

真正的冲突风险不在各自的子包里，而在**公共文件**上。三条纪律：

1. **只做追加式修改。** 在 `ResultCode` / `SecurityConfig` / `RxtApplication`
   这类公共文件里，**只往自己的段位/规则块加东西，绝不动别人的行**。
   加自己的行冲突概率接近零，改别人的行必然冲突。
2. **每个模块一个建表脚本，只 DROP 自己的表。绝不改别人的脚本**
   —— 那是最容易把别人数据删干净的地方。
3. **开工前先 `git pull`，改完尽快提交**，提交信息写清范围，例如
   `P3: M4 融资申请 + 联保小组状态机`、`P3: M5 文章/专家/提问/预约`。

> 公共文件清单（改动前先扫一眼谁在读它）：
> `RxtApplication` / `common/ResultCode` / `config/SecurityConfig` /
> `controller/ArticleController` / `entity/Article`（P2 的首页在读）/ `application.yml`

---

## 九、技术栈

**后端**：Spring Boot 4.1.1 / Java 17 / Spring Security 7.1 / jjwt 0.12.6 /
MyBatis-Plus 3.5.15 / MySQL 8 / Lombok / springdoc 3.1.1 / Hutool

**前端**：Vue 3.5 / Vite 8 / Vue Router 4 / Element Plus 2.14 / axios 1.20

---

## 十、提交代码

```bash
cd springbo
git status --short            # 看清将要提交什么（.gitignore 已排除 target/ 与 node_modules/）
git add -A
git commit -m "P3: 简要写明模块与范围"
git pull --rebase origin master   # 先拉，避免推送被拒
git push origin master
```

推送需要 CodeHub 的凭据。**本机凭据已保存在 Windows 凭据管理器里，直接 push 即可。**

> ⚠️ 实测：**华为云账号密码不能用于 git 推送**（会返回 `HTTP Basic: Access denied`）。
> 必须用仓库级的 **HTTPS 密码**：CodeHub 仓库页 →「设置」→「HTTPS 密码」→ 生成。
> 提示输入时 Username 填 `pgx1181`，Password 填那串 HTTPS 密码。
> **不要把密码写进 `.git/config` 的 remote URL。**

> 完整版（含凭据处理、冲突处理、撤销、自查命令）见
> [`docs/项目交付总文档.md` 第六章](docs/项目交付总文档.md)。
