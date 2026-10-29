package org.danteplanner.backend.shared.freshness;

import java.time.Duration;

import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.constraints.NotNull;

import org.hibernate.validator.constraints.time.DurationMin;

@ConfigurationProperties(prefix = "freshness.read")
@Validated
public record FreshnessReadSettings(@NotNull @DurationMin(millis = 1) @DefaultValue("250ms") Duration waitBound) {
}
