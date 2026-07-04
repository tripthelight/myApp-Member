package com.myapp.member.api;

import com.myapp.member.domain.user.dto.AdminSummaryResponseDTO;
import com.myapp.member.domain.user.dto.AdminUserResponseDTO;
import com.myapp.member.domain.user.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/admin/summary")
    public AdminSummaryResponseDTO adminSummaryApi() {
        return userService.readAdminSummary();
    }

    @GetMapping("/admin/users")
    public List<AdminUserResponseDTO> adminUsersApi() {
        return userService.readAdminUsers();
    }
}
