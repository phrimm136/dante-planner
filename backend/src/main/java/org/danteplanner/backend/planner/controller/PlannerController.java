package org.danteplanner.backend.planner.controller;

import org.danteplanner.backend.planner.dto.PlannerConfigResponse;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/planner/md")
public class PlannerController {

    private final int schemaVersion;
    private final int mdCurrentVersion;
    private final List<Integer> mdAvailableVersions;
    private final List<Integer> rrAvailableVersions;

    public PlannerController(
            @Value("${planner.schema-version}") int schemaVersion,
            @Value("${planner.md.current-version}") int mdCurrentVersion,
            @Value("${planner.md.available-versions}") String mdAvailableVersionsRaw,
            @Value("${planner.rr.available-versions}") String rrAvailableVersionsRaw) {
        this.schemaVersion = schemaVersion;
        this.mdCurrentVersion = mdCurrentVersion;
        this.mdAvailableVersions = parseVersionList(mdAvailableVersionsRaw);
        this.rrAvailableVersions = parseVersionList(rrAvailableVersionsRaw);
    }

    private static List<Integer> parseVersionList(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
    }

    @RateLimited(RateLimitPolicy.PUBLIC_READ)
    @GetMapping("/config")
    public ResponseEntity<PlannerConfigResponse> getConfig() {
        return ResponseEntity.ok(PlannerConfigResponse.builder()
                .schemaVersion(schemaVersion)
                .mdCurrentVersion(mdCurrentVersion)
                .mdAvailableVersions(mdAvailableVersions)
                .rrAvailableVersions(rrAvailableVersions)
                .build());
    }
}
