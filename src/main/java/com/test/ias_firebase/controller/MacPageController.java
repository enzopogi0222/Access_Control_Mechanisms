package com.test.ias_firebase.controller;

import com.test.ias_firebase.model.Role;
import com.test.ias_firebase.service.TotpService;
import com.test.ias_firebase.service.UserRoleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MacPageController {

    private final TotpService totpService;
    private final UserRoleService userRoleService;

    public MacPageController(TotpService totpService, UserRoleService userRoleService) {
        this.totpService = totpService;
        this.userRoleService = userRoleService;
    }

    @GetMapping("/mac")
    public String mac(Authentication authentication, Model model) {
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        String uid = authenticated ? authentication.getName() : null;
        boolean totpEnrolled = authenticated && totpService.isEnrolled(uid);
        Role role = uid != null ? userRoleService.getRole(uid) : Role.user;
        boolean isAdmin = role == Role.admin;

        model.addAttribute("totpEnrolled", totpEnrolled);
        model.addAttribute("role", role.name());
        model.addAttribute("isAdmin", isAdmin);
        return "mac";
    }
}

