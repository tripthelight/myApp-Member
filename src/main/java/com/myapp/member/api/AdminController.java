package com.myapp.member.api;

import com.myapp.member.domain.user.dto.AdminSummaryResponseDTO;
import com.myapp.member.domain.user.dto.AdminUserResponseDTO;
import com.myapp.member.domain.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    @PatchMapping("/admin/users/{username}/lock")
    public ResponseEntity<Map<String, Long>> lockUserApi(@PathVariable String username) {
        Long id = userService.lockUserByAdmin(username);
        return ResponseEntity.ok(Collections.singletonMap("userEntityId", id));
    }

    @PatchMapping("/admin/users/{username}/unlock")
    public ResponseEntity<Map<String, Long>> unlockUserApi(@PathVariable String username) {
        Long id = userService.unlockUserByAdmin(username);
        return ResponseEntity.ok(Collections.singletonMap("userEntityId", id));
    }

    @DeleteMapping("/admin/users/{username}")
    public ResponseEntity<Boolean> deleteUserApi(@PathVariable String username) {
        userService.deleteUserByAdmin(username);
        return ResponseEntity.ok(true);
    }
}
