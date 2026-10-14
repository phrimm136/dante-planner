package org.danteplanner.restrictionfixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.shared.restriction.Restricted;

@RestController
@RequestMapping("/api/fixture")
public class RestrictedReadHandlerFixture {

    @GetMapping("/restricted-read")
    @Restricted
    public String restrictedRead() {
        return "reached";
    }
}
