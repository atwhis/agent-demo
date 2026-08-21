package com.atwhis.agentdemo.sales;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.atwhis.agentdemo.sales.persistence")
public class SalesServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SalesServiceApplication.class, args);
    }
}
