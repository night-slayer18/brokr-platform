package io.brokr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.brokr")
@EntityScan(basePackages = "io.brokr.storage.entity")
@EnableJpaRepositories(basePackages = "io.brokr.storage.repository")
@EnableAsync
@EnableScheduling
@EnableRetry
@EnableCaching
public class BrokrApplication {
    public static void main(String[] args) {
        SpringApplication.run(BrokrApplication.class, args);
    }
}