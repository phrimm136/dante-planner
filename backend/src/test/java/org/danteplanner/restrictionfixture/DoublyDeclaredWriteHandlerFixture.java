package org.danteplanner.restrictionfixture;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.shared.restriction.Restricted;
import org.danteplanner.backend.shared.restriction.RestrictedByState;

@RestController
@RequestMapping("/api/fixture")
public class DoublyDeclaredWriteHandlerFixture {

    @PutMapping("/doubly")
    @Restricted
    @RestrictedByState
    public String doublyDeclared() {
        return "reached";
    }
}
