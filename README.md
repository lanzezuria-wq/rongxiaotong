# 融销通 · P1 交付物

农业金融服务平台。本仓库包含 **P1（后端地基 + 用户体系 + 系统管理）** 的完整实现。

```
springbo/
├── demo1/          后端（Spring Boot 4.1.1 + Java 17），包名 com.rxt
└── rxt-frontend/   前端（Vue 3 + Vite + Element Plus）
```

---

## 一、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 本机已验证 |
| Maven | 3.9+ | |
| MySQL | 8.0 | 需已启动，root 密码见下 |
| Node.js | 20.19+ / 22.12+ | Vite 8 的要求 |

数据库连接写在 `demo1/src/main/resources/application.yml`：

```yaml
url: jdbc:mysql://127.0.0.1:3306/rxt_db?...
username: root
password: huangguojun     # ← 换机器要改这里
```

---

## 二、启动步骤

### 1. 初始化数据库（只需做一次）

```bash
mysql -uroot -p < demo1/src/main/resources/sql/init.sql
```

脚本是**幂等**的（先 `DROP TABLE IF EXISTS` 再建），可以反复执行。
它会创建 `rxt_db` 库、5 张表、7 个种子账号、22 条字典数据、4 篇待审文章。

### 2. 启动后端

在 IDEA 里直接运行 `com.rxt.RxtApplication`，或命令行：

```bash
cd demo1
mvn spring-boot:run
```

启动后访问 <http://localhost:8080/api/ping>，应返回：

```json
{"code":0,"data":{"status":"UP","application":"rxt-backend"},"message":"服务运行正常","success":true}
```

接口文档（Swagger UI）：`http://localhost:8080/swagger-ui.html`

### 3. 启动前端

```bash
cd rxt-frontend
npm install          # 首次需要
npm run dev
```

访问 <http://localhost:5173> 即可。

> **如果后端不在 8080**（比如被占用换了端口）：
> ```bash
> VITE_API_TARGET=http://127.0.0.1:9000 npm run dev
> ```

---

## 三、测试账号

密码统一 `123456`（BCrypt cost=10）。

| 手机号 | 姓名 | 角色 | 状态 | 用途 |
|---|---|---|---|---|
| 13900000000 | 平台管理员 | admin | 正常 | 登后台，测 M7 全部接口 |
| 13900000001 | 银行专员甲 | bank | 正常 | 角色权限验证 |
| 13900000002 | 银行专员乙 | bank | 正常 | |
| 13900000003 | 张农艺师 | expert | **待审核** | 测专家审核流程 |
| 13900000004 | 李农户 | farmer | 正常 | 测普通用户视角 |
| 13900000005 | 王研究员 | expert | **待审核** | 测专家审核流程 |
| 13900000006 | 赵教授 | expert | 正常 | 已审核样例 |

短信验证码在演示环境**固定为 `111111`**（配置项 `rxt.mock.verify-code`）。

---

## 四、P1 交付范围

### 后端 13 个接口

**M1 用户体系（5 个）** —— `UserController`

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/user/register` | 注册。农户→直接登录；专家→待审核 |
| POST | `/api/user/login` | 登录，返回 24h JWT |
| GET | `/api/user/info` | 当前用户信息 |
| PUT | `/api/user/info` | 更新资料（部分更新） |
| PUT | `/api/user/password` | 修改密码 |

**M7 系统管理（8 个）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/user/list` | 用户列表（分页/角色/状态/关键词） |
| PUT | `/api/admin/user/{id}/status` | 启用 / 禁用 |
| PUT | `/api/admin/expert/{id}/audit` | 专家审核 |
| GET | `/api/admin/dict/{type}` | 按类型查字典（**所有登录用户可用**） |
| POST | `/api/admin/dict` | 新增字典项 |
| PUT | `/api/admin/dict/{id}` | 编辑字典项 |
| DELETE | `/api/admin/dict/{id}` | 删除字典项 |
| GET | `/api/article/list` | 文章列表（公开） |
| PUT | `/api/article/{id}/audit` | **文章审核** |

> ⚠️ 最后两个接口的路径**不在 `/api/admin/**` 下**。按 SRS 表58 它们归属专家助力模块，
> 但「文章审核」是管理职能。这导致两个后果：
> 1. 后端 `SecurityConfig` 必须为 `PUT /api/article/{id}/audit` **单独**配一条管理员规则，
>    `/api/admin/**` 那条覆盖不到它；
> 2. 它和 `GET /api/article/list` 与 P4 同学的交付范围有重叠，**需要组内确认归属**。

### 前端 8 个页面

| 路由 | 页面 | 权限 |
|---|---|---|
| `/login` | 登录 | 公开 |
| `/register` | 注册 | 公开 |
| `/user/profile` | 个人中心 | 登录用户 |
| `/user/password` | 修改密码 | 登录用户 |
| `/admin/users` | 用户管理 | 管理员 |
| `/admin/expert-audit` | 专家审核 | 管理员 |
| `/admin/article-audit` | 文章审核 | 管理员 |
| `/admin/dict` | 数据字典 | 管理员 |

---

## 五、测试

```bash
cd demo1
mvn test
```

| 测试类 | 用例数 | 验证内容 |
|---|---|---|
| `RxtApplicationTests` | 1 | Spring 容器 + 数据源能起来 |
| `UserServiceM1Test` | 14 | Service 层业务规则（@Transactional，跑完回滚） |
| `ApiIntegrationTest` | 26 | **端到端**：MockMvc 走完整 Security 过滤器链 |

> MockMvc 是进程内的，不开真实端口，但除了 Tomcat 之外所有环节都是真实的
> （Security 过滤器 → Controller 参数校验 → Service → MySQL → 全局异常 → JSON 序列化）。
> 它能验证「401 时返回的到底是不是统一 Result 格式」这类只有跑起来才看得出的问题。

---

## 六、已知的文档冲突（需组内定稿）

实现时遇到三处文档自相矛盾的地方，**当前选择已写在代码注释里**：

| # | 冲突 | 当前实现 |
|---|---|---|
| 1 | 错误码：概要设计 7.1.1 说 `1004=资源不存在`，但 5.2.2 表里 `1004=验证码错误` | 以 **5.2.2 为准**；「资源不存在」另给补充码 **1008** |
| 2 | 没有「账号被锁定」的错误码 | 暂时复用 **1005**（操作过于频繁，HTTP 429） |
| 3 | 角色：文档说「农户与买家共用 farmer」，但权限矩阵又把买家单列 | 现为 `REGISTER_ROLES = {farmer, expert}`；买家若要独立需加值 |

另外文档技术栈矩阵有两处**版本写错**，已按实际兼容矩阵修正：

- 「Spring Security 6.x」→ 实际是 **Security 7.1**（Boot 4.1.x 配套）。Security 7 的
  `.and()` 链式写法已移除，必须用 Lambda DSL；`antMatchers` → `requestMatchers`。
  **照抄网上的 Security 6 教程必错。**
- 「springdoc-openapi 2.3.x」→ 2.x 只支持 Boot 3，Boot 4.x 必须用 **springdoc 3.1.1**。

---

## 七、技术栈

**后端**：Spring Boot 4.1.1 / Java 17 / Spring Security 7.1 / jjwt 0.12.6 /
MyBatis-Plus 3.5.15 / MySQL 8 / Lombok / springdoc 3.1.1 / Hutool

**前端**：Vue 3.5 / Vite 8 / Vue Router 4 / Element Plus 2.14 / axios 1.20
