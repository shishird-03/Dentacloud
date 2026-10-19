package com.dentacloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@EnableJpaRepositories(considerNestedRepositories = true) // modules keep entities and repositories together
public class DentaCloudApplication {
    public static void main(String[] args) {
        SpringApplication.run(DentaCloudApplication.class, args);
    }
}
