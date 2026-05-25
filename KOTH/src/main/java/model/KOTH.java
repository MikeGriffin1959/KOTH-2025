package model;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"config", "controllers", "helpers", "model", "services"})
public class KOTH {
    public static void main(String[] args) {
        SpringApplication.run(KOTH.class, args);
    }
}