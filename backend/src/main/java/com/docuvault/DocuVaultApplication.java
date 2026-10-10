package com.docuvault;

import com.docuvault.shared.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DocuVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocuVaultApplication.class, args);
    }
}
