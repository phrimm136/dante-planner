package org.danteplanner.backend.planner.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.dto.PlannerConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * REST controller for planner configuration.
 *
 * <p>Exposes the public planner configuration (schema and content versions)
 * used by clients when creating planners.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner/md")
@Slf4j
public class PlannerController {

    @Value("${planner.schema-version}")
    private Integer schemaVersion;

    @Value("${planner.md.current-version}")
    private Integer mdCurrentVersion;

    @Value("${planner.md.available-versions}")
    private String mdAvailableVersions;

    @Value("${planner.rr.available-versions}")
    private String rrAvailableVersions;

    /**
     * Get planner configuration including current content versions.
     *
     * <p>This endpoint is public and does not require authentication.
     * Returns current MD version and available RR versions.</p>
     *
     * @return the planner configuration
     */
    @GetMapping("/config")
    public ResponseEntity<PlannerConfigResponse> getConfig() {
        List<Integer> mdVersions = Arrays.stream(mdAvailableVersions.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
        List<Integer> rrVersions = Arrays.stream(rrAvailableVersions.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();

        PlannerConfigResponse response = PlannerConfigResponse.builder()
                .schemaVersion(schemaVersion)
                .mdCurrentVersion(mdCurrentVersion)
                .mdAvailableVersions(mdVersions)
                .rrAvailableVersions(rrVersions)
                .build();

        return ResponseEntity.ok(response);
    }
}
