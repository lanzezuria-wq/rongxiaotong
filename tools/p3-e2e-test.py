#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
融销通 P3 端到端接口验证脚本（111 项断言）。

不依赖任何第三方库（只用 urllib），直接打真实后端 + 真实 MySQL，
按业务顺序走一遍 P3 的 23 个接口，并断言关键业务规则。

--------------------------------------------------------------------------
为什么要写成"真实请求"而不是只用单元测试？
--------------------------------------------------------------------------
因为 P3 的绝大部分 bug 会出现在"跨层"的地方：MyBatis-Plus 生成 SQL 的字段名、
逻辑删除条件、事务边界、Security 的路径匹配、JSON 序列化格式……
这些用 mock 掉的单元测试一个都测不出来。

实测踩到过一个：t_financing_apply.usage 是 MySQL 关键字，
MyBatis-Plus 生成的 INSERT 不带反引号 → 运行期直接 SQL 语法错误。
（已用 @TableField("`usage`") 修掉，见 FinancingApply 实体的字段注释。）

--------------------------------------------------------------------------
与 JUnit 版（P3ApiIntegrationTest，53 个用例）的分工
--------------------------------------------------------------------------
  · 本脚本 —— 需要真开后端进程 + 真库，验证"跑起来之后到底对不对"，
              额外覆盖中文文案、百分号编码的查询参数等 MockMvc 不方便验的点。
  · JUnit  —— 进 mvn test，作为日常回归，不需要手动起服务。

--------------------------------------------------------------------------
用法
--------------------------------------------------------------------------
  1) 起数据库并**按顺序**执行三个建表脚本：
       mysql -uroot -p < demo1/src/main/resources/sql/init.sql
       mysql -uroot -p < demo1/src/main/resources/sql/init_trade.sql
       mysql -uroot -p < demo1/src/main/resources/sql/init_p3.sql
  2) 起后端（application.yml 的数据源指向上面那个库）：
       cd demo1 && mvn spring-boot:run
  3) 跑本脚本：
       python tools/p3-e2e-test.py

退出码 0 = 全部通过，1 = 有失败项（失败清单打印在最后）。

⚠️ 脚本会写数据（提交申请、建小组、审批、发文章…）。它期望的是
   "刚执行完三个脚本"的初始状态；重复跑会有几条断言失败
   （例如"已有进行中申请时重复提交"会因为上一轮的申请还在而误报）。
   跑之前请重跑那三个 SQL 脚本。

⚠️ 端口固定在 8080、地址 127.0.0.1。要换的话改下面的 BASE。
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8080/api"

PASS, FAIL = [], []


def call(method, path, token=None, body=None):
    """发一个请求，返回 (http_status, json_body)。"""
    # 查询串里会有中文（province=河南省），必须百分号编码，
    # 否则 http.client 会用 ascii 编码请求行并抛 UnicodeEncodeError
    safe_path = urllib.parse.quote(path, safe="/?&=%")
    url = BASE + safe_path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def check(name, cond, detail=""):
    if cond:
        PASS.append(name)
        print("  PASS  " + name)
    else:
        FAIL.append(name)
        print("  FAIL  " + name + ("   -> " + str(detail) if detail else ""))


def login(phone):
    st, res = call("POST", "/user/login", body={"phone": phone, "password": "123456"})
    if st != 200 or res.get("code") != 0:
        raise SystemExit("登录失败 %s：%s %s" % (phone, st, res))
    return res["data"]["token"]


def register(phone, role="farmer"):
    return call("POST", "/user/register", body={
        "phone": phone, "password": "123456", "confirmPassword": "123456",
        "verifyCode": "111111", "role": role})


def future(days=2):
    return (datetime.now() + timedelta(days=days)).strftime("%Y-%m-%d %H:%M:%S")


def first_apply_id(token, status=0):
    """取某用户指定状态的申请 id。空列表时返回 None 而不是抛异常。"""
    st, res = call("GET", "/financing/list?status=%d" % status, token)
    records = (res.get("data") or {}).get("records") or []
    return records[0]["id"] if records else None


def group_total(group_id, token):
    """从小组详情（单对象接口）读 totalAmount，避免解析数组里的嵌套结构。"""
    st, res = call("GET", "/financing/group/%d" % group_id, token)
    return float(res["data"]["totalAmount"])


print("=" * 78)
print("融销通 P3 端到端验证")
print("=" * 78)

# ---------------------------------------------------------------------------
print("\n[0] 准备：登录各类账号")
farmer_a = login("13900000013")     # 刘四海：已有一条「待匹配」申请（120000）
farmer_b = login("13900000014")     # 孙小满：没有申请，用来测"从零提交"
farmer_demo = login("13900000004")  # 李农户：已在演示小组 LB-DEMO-0001 里
bank = login("13900000002")
expert = login("13900000006")       # 赵教授：已审核通过，t_expert.id = 1
admin = login("13900000000")
check("0.1 六种角色账号均可登录", all([farmer_a, farmer_b, farmer_demo, bank, expert, admin]))

# ---------------------------------------------------------------------------
print("\n[1] 越权与鉴权")
st, res = call("GET", "/financing/list")                    # 不带 Token
check("1.1 未登录访问 /financing/list 返回 401", st == 401, "%s %s" % (st, res))
st, res = call("GET", "/bank/stats", token=farmer_a)         # 农户访问银行接口
check("1.2 农户访问 /bank/stats 返回 403 且 code=1007",
      st == 403 and res.get("code") == 1007, "%s %s" % (st, res))
st, res = call("GET", "/expert/list")                        # 游客访问专家列表
check("1.3 游客可访问 /expert/list（permitAll）",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))

# ---------------------------------------------------------------------------
print("\n[2] M4 提交融资申请")
st, res = call("POST", "/financing/apply", token=farmer_a, body={
    "amount": 80000, "usage": "SEED", "industry": "PLANTING",
    "province": "河南省", "city": "周口市", "loanTerm": "M12",
    "description": "重复提交测试"})
check("2.1 已有进行中申请时重复提交 → 3001",
      st == 400 and res.get("code") == 3001, "%s %s" % (st, res))

st, res = call("POST", "/financing/apply", token=farmer_b, body={
    "amount": 5000, "usage": "SEED", "industry": "PLANTING",
    "province": "山东省", "city": "潍坊市", "loanTerm": "M12", "description": "金额越界"})
check("2.2 金额低于 10000 → 参数校验 1001",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("POST", "/financing/apply", token=farmer_b, body={
    "amount": 90000, "usage": "NOT_A_REAL_CODE", "industry": "PLANTING",
    "province": "山东省", "city": "潍坊市", "loanTerm": "M12", "description": "字典校验"})
check("2.3 资金用途不在字典里 → 1001 且提示可选值",
      st == 400 and res.get("code") == 1001 and "可选" in res.get("message", ""),
      "%s %s" % (st, res))

st, res = call("POST", "/financing/apply", token=bank, body={
    "amount": 90000, "usage": "SEED", "industry": "PLANTING",
    "province": "山东省", "city": "潍坊市", "loanTerm": "M12", "description": "角色不对"})
check("2.4 银行角色提交融资申请 → 3004（仅农户）",
      st == 403 and res.get("code") == 3004, "%s %s" % (st, res))

# 契约枚举（小写 expand/planting/12）也应当被接受 —— 验证"字典编码 + 契约枚举"并集校验
st, res = call("POST", "/financing/apply", token=farmer_b, body={
    "amount": 120000, "usage": "expand", "industry": "planting",
    "province": "河南省", "city": "周口市", "loanTerm": "12",
    "description": "验证契约枚举也能通过（小写 expand/planting/12）"})
check("2.5 契约枚举值（expand/planting/12）也能提交成功",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))
apply_b = res["data"]["id"] if res.get("code") == 0 else None
check("2.6 提交申请返回 id 与 matchedGroups 数组",
      apply_b is not None and isinstance(res["data"].get("matchedGroups"), list),
      res.get("data"))

st, res = call("GET", "/financing/list", token=farmer_b)
check("2.7 我的申请列表返回分页结构（records/total/page/size/pages）",
      res.get("code") == 0 and all(k in res["data"] for k in
                                   ("records", "total", "page", "size", "pages")),
      res.get("data"))
check("2.8 列表里带 statusName 中文状态名",
      res["data"]["records"] and res["data"]["records"][0].get("statusName") == "待匹配",
      res["data"]["records"][:1])

st, res = call("GET", "/financing/%d" % apply_b, token=farmer_b)
check("2.9 申请详情含 description / group / approval 三个详情字段",
      res.get("code") == 0 and "description" in res["data"]
      and "group" in res["data"] and "approval" in res["data"], res.get("data"))

st, res = call("GET", "/financing/%d" % apply_b, token=farmer_a)
check("2.10 别人的申请详情 → 1007 越权拦截",
      st == 403 and res.get("code") == 1007, "%s %s" % (st, res))

# ---------------------------------------------------------------------------
print("\n[3] M4 联保小组：创建 → 加入 → 成型")
st, res = call("POST", "/financing/create-group", token=farmer_b,
               body={"industry": "planting", "province": "河南省", "city": "周口市"})
check("3.1 发起新小组成功并返回 id",
      st == 200 and res.get("code") == 0 and res["data"].get("id"),
      "%s %s" % (st, res))
group_id = res["data"]["id"] if res.get("code") == 0 else None

st, res = call("GET", "/financing/group/%d" % group_id, token=farmer_b)
check("3.2 小组详情：发起人自动成为第 1 个成员",
      res.get("code") == 0 and res["data"]["currentMembers"] == 1
      and len(res["data"]["members"]) == 1, res.get("data"))
check("3.3 小组编号已按 LB-日期-主键 回填",
      (res["data"].get("groupNo") or "").startswith("LB-"), res["data"].get("groupNo"))
check("3.4 小组状态为「招募中」且 remainingToForm=2",
      res["data"]["status"] == 0 and res["data"]["statusName"] == "招募中"
      and res["data"]["remainingToForm"] == 2, res["data"])

st, res = call("GET", "/financing/groups?industry=PLANTING&province=河南省", token=farmer_a)
ids = [g["id"] for g in res.get("data", [])]
check("3.5 农户 a 能查到该招募中的小组（同省同行业）",
      res.get("code") == 0 and group_id in ids, ids)

st, res = call("GET", "/financing/groups", token=farmer_b)
ids = [g["id"] for g in res.get("data", [])]
check("3.6 自己已在的小组会被排除（不提示重复加入）",
      group_id not in ids, ids)

st, res = call("POST", "/financing/join", token=farmer_a,
               body={"groupId": group_id, "applyId": 999999})
check("3.7 加入时用不存在的申请 → 404/1008",
      res.get("code") == 1008, "%s %s" % (st, res))

st, res = call("POST", "/financing/join", token=farmer_a,
               body={"groupId": group_id, "applyId": apply_b})
check("3.8 拿别人的申请加入 → 1007",
      st == 403 and res.get("code") == 1007, "%s %s" % (st, res))

# 刘四海（farmer_a）自己有一条待匹配申请，用它加入
apply_a = first_apply_id(farmer_a)
st, res = call("POST", "/financing/join", token=farmer_a,
               body={"groupId": group_id, "applyId": apply_a})
check("3.9 农户 a 成功加入小组", st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("POST", "/financing/join", token=farmer_a,
               body={"groupId": group_id, "applyId": apply_a})
check("3.10 重复加入同一小组 → 400 且提示「已在该小组中」",
      st == 400 and "已在该联保小组中" in res.get("message", ""), "%s %s" % (st, res))

st, res = call("GET", "/financing/group/%d" % group_id, token=farmer_b)
check("3.11 小组人数变为 2，仍未成型",
      res["data"]["currentMembers"] == 2 and res["data"]["status"] == 0
      and res["data"]["remainingToForm"] == 1, res["data"])
# 不写死金额，而是校验"不变量"：totalAmount 必须等于成员申请金额之和。
# 这比断言具体数字更有价值 —— 它验证的是冗余列与明细表始终一致。
member_sum = sum(float(m["applyAmount"]) for m in res["data"]["members"])
check("3.12 小组 totalAmount == 成员申请金额之和（冗余列与明细一致）",
      float(res["data"]["totalAmount"]) == member_sum,
      "totalAmount=%s, member_sum=%s" % (res["data"]["totalAmount"], member_sum))

# 注册第三个农户，凑满 3 人 → 验证"成型"的联动
st, res = register("13900000099")
token_c = login("13900000099") if st == 200 else None
check("3.13 注册新农户账号（验证码 111111）成功", st == 200 and res.get("code") == 0,
      "%s %s" % (st, res))
st, res = call("POST", "/financing/apply", token=token_c, body={
    "amount": 60000, "usage": "GREENHOUSE", "industry": "PLANTING",
    "province": "河南省", "city": "周口市", "loanTerm": "M24",
    "description": "第三户，用于验证小组成型"})
check("3.14 新农户提交申请成功，且被匹配到该小组",
      res.get("code") == 0 and group_id in [g["id"] for g in res["data"]["matchedGroups"]],
      res.get("data"))
apply_c = res["data"]["id"]
call("POST", "/financing/join", token=token_c, body={"groupId": group_id, "applyId": apply_c})

st, res = call("GET", "/financing/group/%d" % group_id, token=farmer_b)
check("3.15 3 人到位 → 小组自动成型为「已匹配」（status=1）",
      res["data"]["currentMembers"] == 3 and res["data"]["status"] == 1
      and res["data"]["statusName"] == "已匹配", res["data"])
check("3.16 成型后 remainingToForm=0",
      res["data"]["remainingToForm"] == 0, res["data"]["remainingToForm"])

# ⚠️ 不用详情接口断言申请状态：详情里嵌了 group 对象，而 group 也有 status 字段，
# 响应字段按字母序输出、group 排在 status 前面，"取第一个"会拿到小组的状态。
# 用列表的 status 筛选来验证，既无歧义又顺带验了筛选功能。
st, res = call("GET", "/financing/list?status=2", token=farmer_b)
check("3.17 成型后组内申请被联动推进为「待银行审批」（status=2）",
      res.get("code") == 0 and any(r["id"] == apply_b for r in res["data"]["records"]),
      res.get("data"))

st, res = call("GET", "/financing/groups?province=河南省", token=token_c)
check("3.18 成型后的小组不再出现在「可加入」列表里",
      group_id not in [g["id"] for g in res.get("data", [])], res.get("data"))

# ---------------------------------------------------------------------------
print("\n[4] M6 银行端：列表 / 统计 / 审批")
st, res = call("GET", "/bank/group/list", token=bank)
check("4.1 银行端列表默认只返回已成型的，且带成员名单",
      res.get("code") == 0 and len(res["data"]) >= 2
      and all(len(g["members"]) >= 1 for g in res["data"]), res.get("data"))
check("4.2 银行端列表成员带姓名（批量回填，非 N+1）",
      all(m.get("username") for g in res["data"] for m in g["members"]))
check("4.3 银行端列表不含招募中的小组",
      all(g["status"] >= 1 for g in res["data"]), [g["status"] for g in res["data"]])

st, res = call("GET", "/bank/stats", token=bank)
check("4.4 统计：无数据时 totalApprovedAmount 为 0 而不是 null",
      res.get("code") == 0 and res["data"]["totalApprovedAmount"] is not None
      and float(res["data"]["totalApprovedAmount"]) == 0.0, res.get("data"))
check("4.5 统计：待审批数 = 2（演示小组 + 新建小组）",
      res["data"]["pendingGroups"] == 2, res["data"])

st, res = call("GET", "/bank/group/list", token=bank)
demo_group = None
for g in res["data"]:
    if g["groupNo"] == "LB-DEMO-0001":
        demo_group = g["id"]
check("4.6 演示小组 LB-DEMO-0001 存在且已成型", demo_group is not None)

st, res = call("PUT", "/bank/approve", token=bank,
               body={"groupId": demo_group, "result": "REJECTED"})
check("4.7 拒绝但没填理由 → 1001（跨字段校验）",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("PUT", "/bank/approve", token=bank,
               body={"groupId": demo_group, "result": "MAYBE"})
check("4.8 审批结果取值非法 → 1001",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("PUT", "/bank/approve", token=bank,
               body={"groupId": group_id, "result": "APPROVED"})
check("4.9 Mock 模式：只传 groupId+result 即可通过审批（自动生成三项）",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("GET", "/bank/group/list?status=3", token=bank)
approved = [g for g in res["data"] if g["id"] == group_id]
check("4.10 审批后小组状态为「已通过」且出现在 status=3 列表",
      len(approved) == 1 and approved[0]["status"] == 3, res.get("data"))

st, res = call("GET", "/financing/%d" % apply_b, token=farmer_b)
appr = res["data"]["approval"] or {}
check("4.11 农户侧看到审批结果（含信用分/利率/核定额度）",
      res["data"]["status"] == 3 and appr.get("result") == "APPROVED"
      and appr.get("creditScore") is not None and appr.get("interestRate") is not None
      and appr.get("decisionAmount") is not None, appr)
# 校验 Mock 生成的三项都落在契约规定的范围内 —— 这是"自动生成"唯一可能出错的地方
grp_total = group_total(group_id, bank)
score, rate, amount = appr.get("creditScore"), float(appr.get("interestRate")), float(appr.get("decisionAmount"))
check("4.12 Mock 生成的三项都在契约范围内（信用分 0~100、利率 3.85~7.00、额度为总额 80%~100%）",
      0 <= score <= 100 and 3.85 <= rate <= 7.00
      and grp_total * 0.80 <= amount <= grp_total * 1.00,
      "score=%s rate=%s amount=%s grp_total=%s" % (score, rate, amount, grp_total))

ok_all = True
for tok, aid in ((farmer_a, apply_a), (token_c, apply_c)):
    st, res = call("GET", "/financing/%d" % aid, tok)
    if res["data"]["status"] != 3:
        ok_all = False
check("4.13 组内另两条申请也一起变为「已通过」", ok_all)

st, res = call("PUT", "/bank/approve", token=bank,
               body={"groupId": group_id, "result": "REJECTED", "remark": "重复审批"})
check("4.14 重复审批同一小组 → 3006（结果不可撤回）",
      st == 400 and res.get("code") == 3006, "%s %s" % (st, res))

st, res = call("GET", "/bank/stats", token=bank)
check("4.15 统计：已通过 1 个、待审批 1 个、核定额度合计 > 0",
      res["data"]["approvedGroups"] == 1 and res["data"]["pendingGroups"] == 1
      and float(res["data"]["totalApprovedAmount"]) > 0, res["data"])

st, res = call("DELETE", "/financing/apply/%d" % apply_b, token=farmer_b)
check("4.16 已审批的申请不能取消 → 3003",
      st == 400 and res.get("code") == 3003, "%s %s" % (st, res))

# 拒绝路径：用演示小组走一次 REJECTED
st, res = call("PUT", "/bank/approve", token=bank,
               body={"groupId": demo_group, "result": "REJECTED",
                     "remark": "成员征信不足，建议补充材料后重新组队"})
check("4.17 拒绝审批（填了理由）成功", st == 200 and res.get("code") == 0, "%s %s" % (st, res))
st, res = call("GET", "/bank/group/list?status=4", token=bank)
check("4.18 拒绝后小组进入 status=4 列表",
      any(g["id"] == demo_group for g in res["data"]), res.get("data"))

# ---------------------------------------------------------------------------
print("\n[5] M5 文章：发布 / 详情 / 点赞 / 评论")
st, res = call("POST", "/article", token=expert, body={
    "title": "大棚番茄越冬期温湿度管理",
    "category": "planting",
    "content": "越冬期是番茄管理最难的一段时间。" * 20,
    "tags": ["番茄", "大棚", "越冬", "番茄"]})
check("5.1 专家发布文章直接生效，返回 id",
      st == 200 and res.get("code") == 0 and res["data"].get("id"), "%s %s" % (st, res))
art_expert = res["data"]["id"] if res.get("code") == 0 else None

st, res = call("POST", "/article", token=farmer_a, body={
    "title": "我家大棚今年的收成记录",
    "category": "VEG",
    "content": "今年种了三个棚的番茄，亩产比去年高了两成。",
    "tags": ["收成", "记录"]})
check("5.2 农户发布文章（用字典分类 VEG）成功",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))
art_farmer = res["data"]["id"] if res.get("code") == 0 else None

st, res = call("POST", "/article", token=farmer_a, body={
    "title": "分类乱填测试", "category": "根本不存在的分类", "content": "测试内容"})
check("5.3 分类既不在契约枚举也不在字典 → 1001",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("POST", "/article", token=bank, body={
    "title": "银行不该能发文", "category": "planting", "content": "测试内容"})
check("5.4 银行角色发布文章 → 1007",
      st == 403 and res.get("code") == 1007, "%s %s" % (st, res))

st, res = call("GET", "/article/%d" % art_expert)
check("5.5 游客可读已发布文章详情",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))
check("5.6 详情含 tags 数组（去重后 3 个）、summary、authorName",
      res["data"]["tags"] == ["番茄", "大棚", "越冬"]
      and res["data"].get("summary") and res["data"].get("authorName") == "赵教授",
      (res["data"].get("tags"), res["data"].get("authorName")))
check("5.7 游客 isLiked=0 且 comments 为空数组",
      res["data"]["isLiked"] == 0 and res["data"]["comments"] == [], res["data"])

st, res = call("GET", "/article/%d" % art_farmer)
check("5.8 游客访问「待审核」的农户文章 → 4002（不泄露存在性细节）",
      st == 400 and res.get("code") == 4002, "%s %s" % (st, res))
st, res = call("GET", "/article/%d" % art_farmer, token=farmer_a)
check("5.9 作者本人可看自己待审核的文章",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))
st, res = call("GET", "/article/%d" % art_farmer, token=admin)
check("5.10 管理员可看任何状态的文章", st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("PUT", "/article/%d/like" % art_expert, token=farmer_a)
check("5.11 点赞成功，返回 isLiked=1 和 likeCount",
      res.get("code") == 0 and res["data"]["isLiked"] == 1
      and res["data"]["likeCount"] >= 1, res.get("data"))
before = res["data"]["likeCount"]
st, res = call("PUT", "/article/%d/like" % art_expert, token=farmer_a)
check("5.12 再点一次取消点赞，isLiked=0 且计数回退",
      res["data"]["isLiked"] == 0 and res["data"]["likeCount"] == before - 1,
      res.get("data"))

call("PUT", "/article/%d/like" % art_expert, token=farmer_b)
st, res = call("GET", "/article/%d" % art_expert, token=farmer_a)
check("5.13 详情里 isLiked 按当前用户区分（A 未赞、B 已赞）",
      res["data"]["isLiked"] == 0 and res["data"]["likeCount"] >= 1
      and len(res["data"]["comments"]) == 0, res["data"])

st, res = call("POST", "/article/%d/comment" % art_expert, token=farmer_a,
               body={"content": "请问越冬期夜温控制在多少合适？"})
check("5.14 发表评论成功", st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("POST", "/article/%d/comment" % art_expert, token=farmer_a,
               body={"content": "x" * 201})
check("5.15 评论超过 200 字 → 1001",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("POST", "/article/%d/comment" % art_farmer, token=farmer_b,
               body={"content": "给待审核文章评论"})
check("5.16 给未发布文章评论 → 4002",
      st == 400 and res.get("code") == 4002, "%s %s" % (st, res))

st, res = call("GET", "/article/%d" % art_expert, token=farmer_a)
check("5.17 详情返回评论列表且回填了评论人姓名与评论数",
      len(res["data"]["comments"]) == 1
      and res["data"]["comments"][0]["username"] == "刘四海"
      and res["data"]["commentCount"] >= 1, res["data"]["comments"])
check("5.18 浏览量自增（≥2）", res["data"]["viewCount"] >= 2, res["data"]["viewCount"])

# ---------------------------------------------------------------------------
print("\n[6] M5 专家列表与详情")
st, res = call("GET", "/expert/list")
check("6.1 专家列表含已审核专家（3 位），返回分页结构",
      res.get("code") == 0 and res["data"]["total"] == 3, res.get("data"))
check("6.2 专家项含 specialty（映射自 domain）与 avatar 字段",
      all("specialty" in e and "avatar" in e for e in res["data"]["records"]),
      res["data"]["records"][:1])
check("6.3 按评分倒序（赵教授 4.90 第一）",
      res["data"]["records"][0]["realName"] == "赵教授", res["data"]["records"][0])
check("6.4 未通过审核的专家不出现在列表里（张农艺师 status=0）",
      all(e["realName"] != "张农艺师" for e in res["data"]["records"]),
      [e["realName"] for e in res["data"]["records"]])

st, res = call("GET", "/expert/list?specialty=电商")
check("6.5 按专业领域模糊筛选生效",
      res["data"]["total"] == 1
      and res["data"]["records"][0]["realName"] == "周文博", res.get("data"))

expert_id = call("GET", "/expert/list")[1]["data"]["records"][0]["id"]  # 赵教授
st, res = call("GET", "/expert/%d" % expert_id)
check("6.6 专家详情含 intro 与 recentArticles",
      res.get("code") == 0 and res["data"].get("intro") is not None
      and isinstance(res["data"].get("recentArticles"), list), res.get("data"))
check("6.7 详情里的近期文章就是刚才专家发的那篇（作者名已回填）",
      any(a["id"] == art_expert and a["authorName"] == "赵教授"
          for a in res["data"]["recentArticles"]), res["data"]["recentArticles"])

st, res = call("GET", "/expert/99999")
check("6.8 不存在的专家 → 404/1008", res.get("code") == 1008, "%s %s" % (st, res))

# ---------------------------------------------------------------------------
print("\n[7] M5 在线提问")
st, res = call("POST", "/question", token=farmer_a, body={
    "expertId": expert_id, "title": "越冬期夜温控制在多少合适？",
    "content": "看到那篇文章说夜温不低于 15 度，但我的棚保温差，只能到 12 度，有影响吗？"})
check("7.1 定向提问成功并返回 id", st == 200 and res.get("code") == 0, "%s %s" % (st, res))
q_direct = res["data"]["id"] if res.get("code") == 0 else None

st, res = call("POST", "/question", token=farmer_b, body={
    "title": "农产品线上包装怎么降成本？",
    "content": "想做生鲜电商，但包装和损耗加起来占售价 20% 以上，有没有实用办法？"})
check("7.2 公开提问（不填 expertId）成功", st == 200 and res.get("code") == 0, "%s %s" % (st, res))
q_public = res["data"]["id"] if res.get("code") == 0 else None

st, res = call("POST", "/question", token=farmer_a, body={
    "expertId": 99999, "title": "无效专家", "content": "测试"})
check("7.3 向不存在的专家提问 → 404/1008", res.get("code") == 1008, "%s %s" % (st, res))

st, res = call("GET", "/question/list", token=farmer_a)
check("7.4 农户视角只看到自己提的问题（1 条）且 expertName 已回填",
      res.get("code") == 0 and len(res["data"]) == 1
      and res["data"][0]["expertName"] == "赵教授", res.get("data"))

st, res = call("GET", "/question/list", token=expert)
# 专家视角 = 点名问我的 + 公开提问 + 我自己提的。种子数据里已有 2 条
# （1 条点名问赵教授、1 条公开且已被周文博回答），加上本次新增的 2 条 = 4 条
ids_seen = [q["id"] for q in res["data"]]
check("7.5 专家视角能看到「点名问我的 + 公开提问」（种子 2 条 + 新增 2 条 = 4 条）",
      res.get("code") == 0 and len(res["data"]) == 4
      and q_direct in ids_seen and q_public in ids_seen, ids_seen)

st, res = call("GET", "/question/list?status=0", token=expert)
check("7.6 专家按 status=0 筛选待回答（3 条：2 条新增 + 1 条种子待答）",
      len(res["data"]) == 3, [q["id"] for q in res["data"]])

st, res = call("PUT", "/question/%d/answer" % q_direct, token=farmer_a,
               body={"answer": "农户不该能回答"})
check("7.7 农户回答提问 → 4001（不是已审核专家）",
      st == 400 and res.get("code") == 4001, "%s %s" % (st, res))

st, res = call("PUT", "/question/%d/answer" % q_public, token=farmer_b,
               body={"answer": "农户不该能回答"})
check("7.8 农户（非专家）回答公开提问 → 4001",
      st == 400 and res.get("code") == 4001, "%s %s" % (st, res))

st, res = call("PUT", "/question/%d/answer" % q_direct, token=expert,
               body={"answer": "12 度明显偏低。建议加二层膜或小拱棚，把夜温提到 15 度以上；"
                                "如果实在做不到，就选耐低温品种并适当稀植。"})
check("7.9 指定专家回答成功", st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("PUT", "/question/%d/answer" % q_direct, token=expert,
               body={"answer": "再答一次"})
check("7.10 重复回答 → 4003",
      st == 400 and res.get("code") == 4003, "%s %s" % (st, res))

st, res = call("GET", "/question/list", token=farmer_a)
check("7.11 提问方看到答案与 answeredAt，answerByName 已回填",
      res["data"][0]["status"] == 1 and res["data"][0]["answer"]
      and res["data"][0]["answeredAt"] and res["data"][0]["answerByName"] == "赵教授",
      res["data"][0])

st, res = call("GET", "/expert/%d" % expert_id)
check("7.12 回答后专家的 answerCount 已累加",
      res["data"]["answerCount"] == 1, res["data"].get("answerCount"))

st, res = call("POST", "/question", token=expert, body={
    "expertId": expert_id, "title": "向自己提问", "content": "测试"})
check("7.13 专家向自己提问 → 4006（脏数据拦截）",
      st == 400 and res.get("code") == 4006, "%s %s" % (st, res))

# ---------------------------------------------------------------------------
print("\n[8] M5 专家预约")
st, res = call("POST", "/appointment", token=farmer_a, body={
    "expertId": expert_id, "type": "online", "appointmentTime": future(3),
    "phone": "13900000013", "description": "想请教大棚保温改造方案"})
check("8.1 线上预约缺 meetingLink → 1001（跨字段必填）",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("POST", "/appointment", token=farmer_a, body={
    "expertId": expert_id, "type": "offline", "appointmentTime": future(3),
    "phone": "13900000013", "description": "想请教大棚保温改造方案"})
check("8.2 线下预约缺 meetingPlace → 1001",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("POST", "/appointment", token=farmer_a, body={
    "expertId": expert_id, "type": "online",
    "appointmentTime": "2020-01-01 10:00:00",
    "phone": "13900000013", "description": "过去时间",
    "meetingLink": "https://meeting.example.com/x"})
check("8.3 预约过去的时间 → 4004",
      st == 400 and res.get("code") == 4004, "%s %s" % (st, res))

st, res = call("POST", "/appointment", token=farmer_a, body={
    "expertId": expert_id, "type": "online", "appointmentTime": "不是时间",
    "phone": "13900000013", "description": "格式错误",
    "meetingLink": "https://meeting.example.com/x"})
check("8.4 时间格式非法 → 1001 且提示格式（不是 500）",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("POST", "/appointment", token=expert, body={
    "expertId": expert_id, "type": "online", "appointmentTime": future(3),
    "phone": "13900000006", "description": "预约自己",
    "meetingLink": "https://meeting.example.com/x"})
check("8.5 专家预约自己 → 4006",
      st == 400 and res.get("code") == 4006, "%s %s" % (st, res))

st, res = call("POST", "/appointment", token=farmer_a, body={
    "expertId": expert_id, "type": "online", "appointmentTime": future(3),
    "phone": "13900000013", "description": "想请教大棚保温改造方案，顺便看下棚膜选型。",
    "meetingLink": "https://meeting.example.com/rxt-p3-test"})
check("8.6 线上预约成功并返回 id", st == 200 and res.get("code") == 0, "%s %s" % (st, res))
appt_id = res["data"]["id"] if res.get("code") == 0 else None

st, res = call("POST", "/appointment", token=farmer_b, body={
    "expertId": expert_id, "type": "offline", "appointmentTime": future(5),
    "phone": "13900000014", "description": "想请专家到田间看看土壤板结问题。",
    "meetingPlace": "山东省潍坊市寿光市某村 3 号大棚"})
check("8.7 线下预约成功", st == 200 and res.get("code") == 0, "%s %s" % (st, res))
appt_id2 = res["data"]["id"] if res.get("code") == 0 else None

st, res = call("GET", "/appointment/list", token=farmer_a)
# 刘四海在种子数据里已有一条预约（约的是钱牧之），所以按 id 精确定位本次新建的那条，
# 不能取 [0]（排序按预约时间倒序，会串）
mine = [a for a in res["data"] if a["id"] == appt_id]
check("8.8 农户视角看到自己发起的预约，含 expertName 与中文状态名",
      len(mine) == 1 and mine[0]["expertName"] == "赵教授"
      and mine[0]["status"] == "PENDING" and mine[0]["statusName"] == "待处理"
      and mine[0]["typeName"] == "线上", res.get("data"))

st, res = call("GET", "/appointment/list", token=expert)
check("8.9 专家视角看到约给自己的预约（2 条），待处理排前",
      len(res["data"]) == 2 and res["data"][0]["status"] == "PENDING", res.get("data"))

st, res = call("PUT", "/appointment/%d/handle" % appt_id, token=farmer_a,
               body={"status": "ACCEPTED"})
check("8.10 农户处理预约 → 4001（非专家）",
      st == 400 and res.get("code") == 4001, "%s %s" % (st, res))

st, res = call("PUT", "/appointment/%d/handle" % appt_id2, token=admin,
               body={"status": "ACCEPTED"})
check("8.11 管理员处理他人预约 → 4001/1007 被拦",
      st in (400, 403) and res.get("code") in (4001, 1007), "%s %s" % (st, res))

st, res = call("PUT", "/appointment/%d/handle" % appt_id, token=expert,
               body={"status": "MAYBE"})
check("8.12 处理结果取值非法 → 1001",
      st == 400 and res.get("code") == 1001, "%s %s" % (st, res))

st, res = call("PUT", "/appointment/%d/handle" % appt_id, token=expert,
               body={"status": "ACCEPTED"})
check("8.13 专家接受预约成功", st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("PUT", "/appointment/%d/handle" % appt_id, token=expert,
               body={"status": "REJECTED"})
check("8.14 已处理的预约不能再改 → 4005",
      st == 400 and res.get("code") == 4005, "%s %s" % (st, res))

st, res = call("PUT", "/appointment/%d/handle" % appt_id2, token=expert,
               body={"status": "REJECTED"})
check("8.15 专家拒绝另一条预约成功", st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("GET", "/appointment/list", token=farmer_a)
mine = [a for a in res["data"] if a["id"] == appt_id]
check("8.16 农户侧状态已更新为已接受（handledAt 有值）",
      len(mine) == 1 and mine[0]["status"] == "ACCEPTED" and mine[0]["handledAt"], mine)

# ---------------------------------------------------------------------------
print("\n[9] 回归：P1 / P2 的既有接口未被破坏")
st, res = call("GET", "/article/list?status=0", token=admin)
check("9.1 P1 文章列表仍可用（待审核列表非空）",
      res.get("code") == 0 and res["data"]["total"] >= 1, res.get("data", {}).get("total"))
st, res = call("PUT", "/article/%d/audit" % art_farmer, token=admin,
               body={"status": "APPROVED"})
check("9.2 P1 文章审核仍可用（让 5.8 的待审文章转为已发布）",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))
st, res = call("GET", "/article/%d" % art_farmer)
check("9.3 审核通过后游客可读该文章（与原逻辑一致）",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))

st, res = call("GET", "/admin/dict/industry", token=farmer_a)
check("9.4 P1 字典查询仍对所有登录用户开放",
      res.get("code") == 0 and len(res["data"]) >= 5, res.get("data"))

st, res = call("GET", "/user/info", token=farmer_a)
check("9.5 P1 用户信息接口仍可用", res.get("code") == 0, res.get("data"))

st, res = call("GET", "/home/banners")
check("9.6 P2 首页轮播仍可游客访问", st == 200 and res.get("code") == 0, "%s %s" % (st, res))
st, res = call("GET", "/product/list")
check("9.7 P2 商品列表仍可游客访问（P1/P2/P3 三个模块的表不冲突）",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))
st, res = call("GET", "/product/list", token=farmer_demo)
check("9.8 P2 商品列表带登录态也正常（种子商品属于李农户）",
      st == 200 and res.get("code") == 0, "%s %s" % (st, res))

# ---------------------------------------------------------------------------
print("\n" + "=" * 78)
print("结果：%d 通过 / %d 失败" % (len(PASS), len(FAIL)))
if FAIL:
    print("\n失败清单：")
    for f in FAIL:
        print("  - " + f)
print("=" * 78)
sys.exit(1 if FAIL else 0)
