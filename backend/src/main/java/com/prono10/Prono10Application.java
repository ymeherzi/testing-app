package com.prono10;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Prono10Application {

    public static void main(String[] args) {
        SpringApplication.run(Prono10Application.class, args);
    }
}
