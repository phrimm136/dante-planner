package org.danteplanner.restrictionfixture;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.shared.restriction.RestrictionExempt;

@RestController
@RequestMapping("/api/fixture")
public class BlankExemptionFixture {

    @DeleteMapping("/blank")
    @RestrictionExempt(reason = " ")
    public String blankReason() {
        return "reached";
    }
}
