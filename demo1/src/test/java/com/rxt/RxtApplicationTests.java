package com.rxt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒烟测试：只验证 Spring 容器能正常启动（数据源、Mapper、Security 都能装配起来）。
 * 如果这个方法报错，说明配置有问题，不要急着写业务代码。
 */
@SpringBootTest
class RxtApplicationTests {

    @Test
    void contextLoads() {
    }
}
