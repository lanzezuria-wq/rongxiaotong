package com.rxt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@MapperScan("com.rxt.mapper")
public class RxtApplication {

    public static void main(String[] args) {
        SpringApplication.run(RxtApplication.class, args);
    }
}
