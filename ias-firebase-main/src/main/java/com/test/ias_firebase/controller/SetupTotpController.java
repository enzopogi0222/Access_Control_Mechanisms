package com.test.ias_firebase.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SetupTotpController {

    @GetMapping("/setup-totp")
    public String setupTotpPage() {
        return "setup-totp";
    }
}
