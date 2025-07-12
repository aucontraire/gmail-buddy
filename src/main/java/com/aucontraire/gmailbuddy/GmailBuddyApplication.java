package com.aucontraire.gmailbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GmailBuddyApplication {

    public static void main(String[] args) {
        SpringApplication.run(GmailBuddyApplication.class, args);
    }

}
