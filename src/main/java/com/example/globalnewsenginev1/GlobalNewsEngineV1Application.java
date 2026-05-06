package com.example.globalnewsenginev1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GlobalNewsEngineV1Application {

    public static void main(String[] args) {
        SpringApplication.run(GlobalNewsEngineV1Application.class, args);
    }

}
