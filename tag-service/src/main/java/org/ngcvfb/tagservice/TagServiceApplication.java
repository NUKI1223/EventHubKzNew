package org.ngcvfb.tagservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class TagServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TagServiceApplication.class, args);
    }
}
