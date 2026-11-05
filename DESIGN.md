---
name: "融销通"
description: "现代农业门户与专业浅色工作台，以农业绿色连接交易、联保融资和专家服务。"
colors:
  accent: "#285b43"
  accent-strong: "#1b4230"
  accent-soft: "#eaf0e5"
  primary-light-3: "#558a6f"
  primary-light-5: "#8ab29b"
  primary-light-7: "#c0d7c7"
  primary-light-8: "#d6e5d9"
  primary-light-9: "#edf4ef"
  warning: "#986516"
  danger: "#b44435"
  bg: "#f6f6f2"
  surface: "#fff"
  surface-soft: "#f0f2eb"
  ink: "#202c27"
  text-regular: "#46534a"
  muted: "#647068"
  placeholder: "#6d786f"
  line: "#e0e4dc"
  field-border: "#d8e0d9"
  focus: "#6b997b"
  sidebar: "#eef0e8"
  sidebar-active: "#dfe8d6"
  card-hover-border: "#93b29b"
typography:
  display:
    fontFamily: "'Songti SC', 'Noto Serif CJK SC', serif"
    fontSize: "clamp(42px, 4.4vw, 64px)"
    fontWeight: 500
    lineHeight: 1.4
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "28px"
    fontWeight: 650
    lineHeight: 1.35
    letterSpacing: "-0.02em"
  title:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "16px"
    fontWeight: 500
    lineHeight: 1.65
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "14px"
    lineHeight: 1.6
  label:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "13px"
    lineHeight: 1.8
  wordmark:
    fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif"
    fontSize: "23px"
    fontWeight: 650
    lineHeight: 1.2
    letterSpacing: "0.03em"
rounded:
  tag: "4px"
  control: "8px"
  panel: "14px"
  dialog: "16px"
  circle: "50%"
spacing:
  "4": "4px"
  "6": "6px"
  "8": "8px"
  "10": "10px"
  "12": "12px"
  "14": "14px"
  "16": "16px"
  "18": "18px"
  "20": "20px"
  "24": "24px"
  "28": "28px"
  "32": "32px"
  "36": "36px"
  "40": "40px"
  "44": "44px"
  "48": "48px"
  "52": "52px"
  "56": "56px"
  "64": "64px"
components:
  button-link:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.surface}"
    rounded: "{rounded.control}"
    padding: "10px 18px"
  button-link-hover:
    backgroundColor: "{colors.accent-strong}"
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.surface}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
  button-primary-hover:
    backgroundColor: "{colors.primary-light-3}"
  button-primary-active:
    backgroundColor: "{colors.accent-strong}"
  button-primary-disabled:
    backgroundColor: "{colors.primary-light-5}"
    textColor: "{colors.surface}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-regular}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
  button-icon:
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "8px"
  field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-regular}"
    rounded: "{rounded.control}"
    padding: "1px 11px"
  chip-primary:
    backgroundColor: "{colors.primary-light-9}"
    textColor: "{colors.accent}"
    rounded: "{rounded.tag}"
    padding: "0 9px"
    height: "24px"
  product-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
  product-card-body:
    padding: "18px"
  nav-active:
    textColor: "{colors.accent}"
    padding: "27px 0 25px"
  group-seat-filled:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent}"
    rounded: "{rounded.circle}"
    width: "34px"
    height: "34px"
---

# Design System: 融销通

## Overview

**Creative North Star: "现代农业门户与专业浅色工作台"**

以农业绿色连接农产品交易、三人联保和专家服务。门户以全宽梯田摄影、居中衬线标题和可用搜索建立农业识别；业务工作台用浅中性色、清晰的系统中文正文、细分隔和状态文字支持办理任务。

同一品牌覆盖公共门户、个人业务、银行和平台管理。页面密度按任务调整：门户给内容留白，表格与表单保持可扫描的结构；绿色集中用于品牌、主操作、当前导航和正常状态，待处理与拒绝各有语义颜色。

**Key Characteristics:**

- 农业绿色、白色与浅中性色构成共同基底。
- 门户标题用中文衬线，业务文字用系统中文无衬线。
- 平面卡片与细边界组织内容，深度主要来自色面和留白。
- 三人联保用真实成员席位表达成组条件。

本文件是已实现系统的扫描记录，前置 YAML 是规范 token 层；它记录源代码中的可复用值，不把每个页面的一次性数值提升为全站规范。主要来源为 `rxt-frontend/src/styles/global.css`、`src/styles/surfaces.css`、`src/styles/workspace.css` 及共用组件。方向承接 `PRODUCT.md` 与 `.impeccable/surfaces/platform.md`，页面策略留在方向合同中。

### 本轮参考与取舍（2026-11-05）

本轮保留原有农业品牌与业务结构。门户采用摄影构图；交易、融资、问答和银行增加分区导航。全部页面共享边界、面板与表单样式。沿用 Element Plus 的真实组件源码，不引入额外组件库或外部字体。

| 实际访问的网站 | 观察与产品关系 | 本项目取舍 |
| --- | --- | --- |
| [AgriDigital](https://www.agridigital.io/) | 农业供应链题材；首屏摄影背景、居中大标题和主入口 | 作为静态构图参考，使用项目已有授权梯田照片。菜单复演被100%加载层遮挡，动态行为未核验，不声称1:1还原。 |
| [Agreena](https://agreena.com/) | 农业实景与按受众分类的服务导航；滚动后呈现方案内容 | 借鉴题材与服务分区；不采用其碳信用营销承诺。 |
| [Shopify](https://www.shopify.com/) | 商品场景、清晰主操作与分区内容；滚动后介绍工具 | 保留可用搜索、商品与角色入口；不增加促销或虚构业绩。 |
| [Linear](https://linear.app/) | 低噪声导航与工作区示例；内容依业务步骤组织 | 用于业务页层级分析；保持项目浅色工作台。 |
| [Supabase](https://supabase.com/) | 功能入口分组、标题与状态层级；模块点击进入对应功能 | 保留真实业务分组；不引入开发工具题材和深色全站皮肤。 |

实际截图、DOM与侦察记录位于 `rxt-frontend/ref/`，本轮运行验证位于 `.impeccable/review/lsf-20261105/`。LSF 的 source/prototype/paired 严格对账尚未通过，不能将本轮称为完整LSF流程认证。AI Studio停在首次条款确认；本轮保留既有文案，没有代用户接受条款或调用外部模型。

## Colors

色彩以克制的田野绿配合带轻微绿意的中性色，颜色值以 YAML 为准。

### Primary

- **田野绿** (`accent`)：品牌图形、主操作、活动导航、正常状态；也映射 Element Plus 的 primary 和 success。
- **深叶绿** (`accent-strong`)：门户按钮悬停、业务主按钮按下、重点价格与深色强调文字。
- **浅叶绿** (`accent-soft`)：头像、已占用联保席位和流程说明。
- **组件绿色阶** (`primary-light-3/5/7/8/9`)：沿用现有 Element Plus 主题；依次承接主按钮悬停、禁用、浅边界和浅底色。不是另一套品牌色。

### Neutral

- **纸白与浅田底色** (`surface`、`bg`、`surface-soft`)：白色内容面板置于轻微绿色背景，区分工作区和内容。
- **墨绿正文** (`ink`)、**常规正文** (`text-regular`)、**灰绿辅助文字** (`muted`、`placeholder`)：分别服务标题正文、输入值和说明占位。
- **细分隔** (`line`、`field-border`)：卡片边界与输入边界；**侧栏底色与当前项** (`sidebar`、`sidebar-active`)：工作台空间与位置提示。
- **可见焦点与卡片悬停边界** (`focus`、`card-hover-border`)：辅助交互，不替代主色。

待处理用 `warning`（收获土黄），拒绝和错误用 `danger`（陶土红）。它们是功能状态色，需配合文字或图标，不是页面装饰色。Element Plus 其它状态浅色保持组件库默认派生值，不能从本文件推断其已统一重设。

**The Semantic Green Rule.** 绿色承接品牌、当前操作和正常状态；待处理与拒绝保留独立语义和文字，不用绿色填满整个工作台。

## Typography

**Display Font:** `Songti SC` → `Noto Serif CJK SC` → `serif`。门户首屏与认证场景标题使用这一中文衬线栈；知识介绍标题只设置 `Songti SC, serif`。

**Body Font:** `-apple-system` → `BlinkMacSystemFont` → `PingFang SC` → `Microsoft YaHei` → `sans-serif`。品牌字标、业务标题、正文、输入和导航共用这一系统无衬线栈。

**Character:** 使用设备已有的中文字体；当前未通过 `@font-face` 加载统一字体文件。`font-synthesis: none` 避免浏览器合成字形；非标准字重（如 650、550）由可用字体映射，跨设备字形和字重会有差异。

### Hierarchy

- **Display**：门户主标题为 YAML 的 `display`；中屏覆盖到（52px），手机到（40px）。认证场景标题使用 `clamp(30px, 3.6vw, 48px)`、字重（500）、行高（1.4）。
- **Headline**：业务页标题为 `headline`；手机缩为（24px）。门户区段标题使用（28px / 600），手机（24px）。
- **Title**：商品名采用 `title`，最多两行；手机缩为（14px）。对话框标题为（20px / 600），业务卡片标题常用（18px / 550）。
- **Body**：全局（14px / 1.6）；门户主说明为（16px / 1.9），手机（14px）；文章内容为（16px / 2），手机（15px）。页标题说明最大宽度（65ch）。
- **Label**：帮助文案为 `label`；状态标签使用组件库（12px / 500）。品牌副标题为（11px / 400），手机（10px）。不对中文施加全大写转换。
- **Numeric**：金额、销量及统计使用 `font-variant-numeric: tabular-nums`，仍继承正文字体；不存在独立等宽字体家族。

## Layout

公共容器最大宽度（1280px），桌面左右内边距（40px）；页头最大宽度（1440px）、最小高度（80px）。门户首屏为全宽照片与居中内容；服务入口三列，商品四列、间距（24px）；联保与专家列表三列。个人中心使用（188px）导航列与（44px）列距。

工作台桌面固定侧栏宽（236px），工作区左偏移相同，内边距（30px 36px 56px）；桌面内容页标题与内容间隔（28px）。常用间距是 YAML 中已观测的 px 步长，不存在已实现的 rem 倍比或统一 CSS 间距变量。

### Responsive behavior

| 范围 | 已实现行为 |
| --- | --- |
| 大于 1100px | 门户桌面导航；工作台固定侧栏；商品四列；联保与专家三列。 |
| 768–1100px | 门户导航折叠；公共容器边距 28px；商品三列，联保与专家两列；工作台改为 236px 抽屉，内容无左偏移、边距 28px。 |
| 不大于 767px | 门户页头 72px，公共边距 20px；首屏与服务入口单列、商品两列间距 12px；联保与专家单列；个人导航横向滚动；工作台内容边距 24px 16px，导航仍为抽屉。 |
| 不小于 1441px | 认证场景上边距60px；背景摄影铺满左栏，标题位于场景下半部。 |

手机表单标签置于字段上方，复杂详情为单列；表格和 SKU 编辑保留容器内滚动，SKU 表最小宽（560px）。对话框最大宽为 `calc(100vw - 32px)`。分页在手机隐藏每页数量与跳页，保留页码。

购物车在不大于（1100px）时使用“选择框／64px 图片／商品名”网格，将数量、单价、小计分行并标明文字；结算在不大于（767px）时用“52px 图片／商品信息”两列，数量和小计另起一行。联保摘要和成员席位允许换行。这些规则保护任务内容，不把桌面表格整体缩小。

手机按钮、图标按钮、输入、选择、复选框及数量输入以最小（44px）高度为主要触控规则。桌面业务按钮最小（36px），首页搜索按钮（44px），输入包装（40px）；不得把这些最小值当作所有控件的最终固定高度。

## Elevation & Depth

业务卡片、商品卡片和汇总区以白底、浅色底和（1px）边界区分层次，静态卡片与卡片悬停均不抬升、不加投影。输入仍使用 Element Plus 的内描边型 `box-shadow`，弹层保留组件库深度；“平面卡片”不表示全站完全没有阴影。

### Shadow Vocabulary

- **输入边界**：`0 0 0 1px var(--el-border-color) inset`；焦点变为 `var(--el-color-primary)`，错误变为 `var(--el-color-danger)`。这是描边，不是浮起效果。
- **结构遮罩**：工作台抽屉使用 `#1b2d2460` 遮罩，层级（110），侧栏（120）；门户固定页头层级（100）。对话框等弹层深度由现有 Element Plus 样式负责，本次未建立新的投影阶梯。

**The Flat Card Rule.** 卡片靠边界、间距和内容建立层级；悬停改变边界，不把业务卡片变成悬浮面板。

## Shapes

常规面板与商品卡片使用 `panel` 圆角；输入使用 `control`，按钮与导航沿用现有6px到9px的局部圆角；标签使用 `tag`；对话框使用 `dialog`。头像与联保成员席位为正圆。主要边界是（1px）实线；空席位为虚线。

门户首屏采用全宽照片、深色遮罩和居中文字。桌面最小高度620px，手机540px。照片保留来源说明，搜索宽度不超过580px。商品图片继续使用4:3比例。

## Components

### Buttons

明确、轻量，主操作用实心绿。门户链接型按钮使用 `button-link`，悬停转深绿、按下下移（1px）；业务 Element Plus 主按钮使用 `button-primary`，悬停转 `primary-light-3`、按下转深绿。两者的悬停行为不同，应沿用既有组件，不能统一推断为一种。

业务次按钮为白底、常规文字、细边界，悬停使用浅绿底和主色文字。图标按钮透明底，悬停为浅底色。文字链接用主色，悬停下划线。业务按钮过渡为（0.18s），禁用使用组件库 disabled 状态，加载态阻止按钮继续触发；实际提交应继续绑定页面 loading 状态。

通用原生控件 `:focus-visible` 为（3px）`focus` 外描边，外偏移（4px）；Element Plus 按钮保留组件自身（2px）`primary-light-5` 外描边与（1px）偏移，输入采用内部焦点边界。不要宣称所有组件共享完全相同的焦点样式。

### Chips

状态标签沿用 Element Plus：主色浅底、浅色边界、主色文字，默认高度（24px）、水平内边距（9px），与 YAML `chip-primary` 对应。warning、danger 等按语义切换，标签本身不是可点击按钮；筛选用现有 radio/tab 控件，不把静态标签伪装成交互。

### Cards / Containers

商品卡片为整卡链接；图片失效或缺失均显示文本占位。主体内边距（18px），手机（13px）；商品名最多两行，价格突出数字，分类、销量、农户为辅助信息。悬停边界转 `card-hover-border`，照片仅放大（1.025），无抬升。

表格面板常规内边距（24px），手机（16px 12px）；订单、融资和文章卡片常规（28px），手机（18px）。表格标题字重（500）、行内上下留白（16px），手机数据行（12px），不依靠厚重色块区分每行。

### Inputs / Fields

白底、轻边界、控件圆角，默认包装内边距（1px 11px），输入值用常规正文色、光标用主色。占位文字不是标签的替代。焦点内描边转主色，校验错误转错误色并保留组件库的错误文字；禁用由组件库提供背景、文字、边界及禁止光标。

首页搜索为原生输入与按钮组合，整体圆角（10px），输入键盘焦点使用（2px）主色外描边；不要将这套局部搜索样式替代全部业务字段。

### Navigation

门户桌面导航以灰绿文字起始，悬停变主色，当前项增加（2px）底边；手机菜单在页头下展开，当前项用浅绿底。路由切换关闭菜单。

工作台侧栏当前项用独立浅绿底、深绿文字与字重（600），并显示箭头；不大于（1100px）由按钮打开抽屉，遮罩可关闭。账户导航沿用当前项浅底色，手机横向滚动。导航按钮使用描述性名称与 `aria-expanded`，当前桌面导航/工作台导航提供 `aria-current`。

### Brand and member seats

品牌由现有内联 SVG 苗芽／田垄线条和“融销通”字标构成，不另造图标或字体标志。默认标记（38×38px），手机（32×32px）；完整字标带“农业融销服务平台”副标题，紧凑版省略副标题；深绿认证场景使用现有白色反相版本。

三人联保用成员席位表达真实占用：已有人为浅绿实线圆，待加入为虚线空圆，单席（34×34px）、间距（6px），支持换行。3 人是成组条件；可见席位按现有成员数和容量计算，默认至少 3 席、容量回退为 10 人，并非永远只有 3 个席位。成组/剩余条件同时有文字与颜色。不增加虚构成员或审批成绩。

### Request and submission states

`RequestState` 按加载 → 错误 → 空数据的顺序呈现：加载为三行骨架并带 `role="status"`；错误有 `role="alert"`、原因提示及“重新加载”按钮；空数据有独立文案。白色反馈区最小高度（190px）、内边距（30px）。具体无权限、过期、表单校验及成功反馈沿用各页已有权限/请求/提交逻辑，不能据此假定每种状态已单独截图。

### Motion and media

门户首屏为静态摄影，移除原来的循环缩放与分项入场。商品照片保留既有轻微缩放，业务抽屉保留现有开合过渡。`prefers-reduced-motion: reduce` 下关闭动画、过渡及平滑滚动。

田野照片为 **Longsheng Rice Terraces November 2017 014**，作者 **King of Hearts**，来源 [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Longsheng_Rice_Terraces_November_2017_014.jpg)，许可 [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0)。本地文件为 `rxt-frontend/public/images/longsheng-terraces.jpg`；署名、原始链接与许可元数据见同目录 `credits.json`，用户入口为 `/images/credits.html` 与全站页脚。照片是农业场景素材，不声称平台经营该产地；变更素材或创作衍生版本时保留相应许可信息。

商品图是项目演示素材，呈现真实已有字段；用户上传图片保留其数据来源。文档不为演示商品图补造作者或许可。品牌 SVG 来源为本项目 `BrandMark.vue`，组件图标继续沿用已安装的 Element Plus 图标。

## Do's and Don'ts

### Do:

- Do 复用前置 tokens 和现有 Element Plus 主题；同名 CSS 变量是运行时来源。
- Do 让主操作、状态和辅助说明保持层级；金额和数量使用等宽数字。
- Do 沿用手机单列、工作台抽屉和表格容器内滚动，并分别检查长标题、数量控件和操作区域。
- Do 为加载、空结果和失败分别呈现反馈；错误提供实际重试入口。
- Do 保留摄影署名、许可入口和演示素材说明。

### Don't:

- Don't 使用大面积高饱和背景或装饰渐变替代已确认的浅色农业品牌。
- Don't 将门户摄影和衬线标题铺到业务表格与全部卡片。
- Don't 用随机人物评价、虚构审批事件、实际放款或平台业绩填充画面。
- Don't 将照片所示产地表述为平台实际经营范围；不要移除演示金融说明。
- Don't 为新页面加载未经确认的中文 Web 字体，或声称不同系统的字体和字重完全一致。
