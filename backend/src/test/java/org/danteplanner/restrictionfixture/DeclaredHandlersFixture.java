package org.danteplanner.restrictionfixture;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.shared.restriction.Restricted;
import org.danteplanner.backend.shared.restriction.RestrictedByState;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;

@RestController
@RequestMapping("/api/fixture")
public class DeclaredHandlersFixture {

    @PostMapping("/restricted")
    @Restricted
    public String restricted() {
        return "reached";
    }

    @PatchMapping("/by-state")
    @RestrictedByState
    public String byState() {
        return "reached";
    }

    @RequestMapping(value = "/exempt", method = RequestMethod.POST)
    @RestrictionExempt(reason = "own setting")
    public String exempt() {
        return "reached";
    }

    @GetMapping("/read")
    public String read() {
        return "reached";
    }
}
