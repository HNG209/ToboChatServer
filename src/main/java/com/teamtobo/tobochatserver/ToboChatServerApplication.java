package com.teamtobo.tobochatserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ToboChatServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToboChatServerApplication.class, args);
    }

}
