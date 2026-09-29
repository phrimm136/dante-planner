package org.danteplanner.privilegedroutefixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.admin.service.AdminService;

@RestController
public class StrayAdminHandlerFixture {

    private final AdminService adminService;

    public StrayAdminHandlerFixture(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/api/fixture/user/{targetId}/role")
    public String role(@PathVariable Long targetId) {
        return adminService.getUserRole(targetId).getValue();
    }
}
