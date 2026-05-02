package com.example.tfgbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TfgBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TfgBackendApplication.class, args);
    }

}
