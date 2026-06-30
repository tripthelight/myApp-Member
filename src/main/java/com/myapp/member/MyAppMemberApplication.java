package com.myapp.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyAppMemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAppMemberApplication.class, args);
    }

}
