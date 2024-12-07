package com.aucontraire.gmailbuddy.controller;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth2")
public class OAuth2Controller {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Controller.class);

    @GetMapping("/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam("state") String state,
            @RequestParam("code") String code,
            HttpSession session
    ) {
        String storedState = (String) session.getAttribute("oauth2State");
        logger.debug("Stored state: {}", storedState);
        logger.debug("Received state: {}", state);

        if (storedState == null || !storedState.equals(state)) {
            logger.warn("State mismatch or expired session. Reinitiate OAuth.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Session expired or invalid state. Please try again.");
        }

        // Proceed with token exchange and further processing
        return ResponseEntity.ok("OAuth flow completed successfully");
    }

}
