package com.aucontraire.gmailbuddy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "Welcome to your dashboard!";
    }
}

