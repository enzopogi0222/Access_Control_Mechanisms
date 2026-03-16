package com.test.ias_firebase.controller;

import com.test.ias_firebase.service.TotpService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final TotpService totpService;

    public DashboardController(TotpService totpService) {
        this.totpService = totpService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        boolean totpEnrolled = authentication != null
                && authentication.isAuthenticated()
                && totpService.isEnrolled(authentication.getName());
        model.addAttribute("totpEnrolled", totpEnrolled);
        return "dashboard";
    }
}
