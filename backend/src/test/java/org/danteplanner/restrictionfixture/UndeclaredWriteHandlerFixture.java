package org.danteplanner.restrictionfixture;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fixture")
public class UndeclaredWriteHandlerFixture {

    @PostMapping("/undeclared")
    public String undeclared() {
        return "reached";
    }

    @RequestMapping("/any-method")
    public String anyMethod() {
        return "reached";
    }
}
