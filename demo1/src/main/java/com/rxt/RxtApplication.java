package com.rxt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
// 显式列出各个包的 Mapper，不用通配符 —— 一眼能看出哪些包被扫了，也避免误扫到不该扫的东西。
//   com.rxt.mapper          P1（用户/专家/字典/文章）
//   com.rxt.trade.mapper    P2（商品/购物车/订单/地址）
//   com.rxt.finance.mapper  P3（融资申请/联保小组/小组成员/银行审批）
//   com.rxt.assist.mapper   P3（文章评论/点赞/提问/预约）
// ⚠️ 新增模块时在这里加一行，否则 Mapper 扫不到，启动会报
//    "Consider defining a bean of type 'xxxMapper' in your configuration"。
@MapperScan({
        "com.rxt.mapper",
        "com.rxt.trade.mapper",
        "com.rxt.finance.mapper",
        "com.rxt.assist.mapper"
})
public class RxtApplication {

    public static void main(String[] args) {
        SpringApplication.run(RxtApplication.class, args);
    }
}
