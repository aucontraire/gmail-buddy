package com.aucontraire.gmailbuddy.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class EnvironmentConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory("./")
                    .filename(".env")
                    .ignoreIfMissing()
                    .load();

            Map<String, Object> dotenvProperties = new HashMap<>();
            for (DotenvEntry entry : dotenv.entries()) {
                dotenvProperties.put(entry.getKey(), entry.getValue());
            }

            // addLast ensures shell-exported env vars and -D system properties take precedence over .env values
            environment.getPropertySources().addLast(new MapPropertySource("dotenv", dotenvProperties));
        } catch (Exception e) {
            System.err.println("Warning: Could not load .env file: " + e.getMessage());
        }
    }
}
