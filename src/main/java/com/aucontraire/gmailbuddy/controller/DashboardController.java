package com.aucontraire.gmailbuddy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class DashboardController {

    @GetMapping("/")
    public RedirectView redirectToDashboard() {
        return new RedirectView("/dashboard");
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Welcome to your dashboard!";
    }
}
