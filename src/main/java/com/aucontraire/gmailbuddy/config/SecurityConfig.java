package com.aucontraire.gmailbuddy.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Enumeration;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver) throws Exception {
        http
                .addFilterBefore((request, response, chain) -> {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication != null) {
                        logger.debug("Authentication found: {}", authentication.getName());
                    } else {
                        logger.debug("No authentication found in SecurityContext");
                    }
                    chain.doFilter(request, response);
                }, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/login**", "/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization ->
                                authorization.authorizationRequestResolver(customAuthorizationRequestResolver))
                        .defaultSuccessUrl("/dashboard", true)
                )
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(config -> config.disable()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                );
        return http.build();
    }


    @Bean
    public OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver defaultResolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request);
                if (authorizationRequest != null) {
                    // Generate state and store it in session
                    String state = UUID.randomUUID().toString();
                    HttpSession session = request.getSession(false); // Avoid creating a session unnecessarily
                    if (session != null) {
                        session.setAttribute("oauth2State", state);
                        logger.debug("Session attributes:");
                        Enumeration<String> attributeNames = session.getAttributeNames();
                        while (attributeNames.hasMoreElements()) {
                            String attributeName = attributeNames.nextElement();
                            logger.debug("{}: {}", attributeName, session.getAttribute(attributeName));
                        }
                    } else {
                        logger.warn("HttpSession is null. State will not be stored.");
                    }
                    return OAuth2AuthorizationRequest.from(authorizationRequest)
                            .state(state)
                            .build();
                }
                return null;
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return defaultResolver.resolve(request, clientRegistrationId);
            }
        };
    }
}
