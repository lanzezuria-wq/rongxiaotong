package com.rxt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
// 显式列出两个包，不用通配符 —— P1 的 Mapper 在 com.rxt.mapper，P2 的在 com.rxt.trade.mapper。
// 以后别人新增模块，在这里加一行就行，一眼能看出哪些包被扫了。
@MapperScan({"com.rxt.mapper", "com.rxt.trade.mapper"})
public class RxtApplication {

    public static void main(String[] args) {
        SpringApplication.run(RxtApplication.class, args);
    }
}
