package org.danteplanner.backend.planner.service;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cron trigger and the read-only boundary must sit on the same method: a separate entry point
 * calling {@code reconcile()} on {@code this} misses the proxy, so the scheduled pass runs
 * unannotated — on the primary, and without one snapshot for every audit to disagree against.
 */
class PlannerDriftReconcilerSchedulingTest {

    private static List<Method> declaredMethods() {
        return Arrays.asList(PlannerDriftReconciler.class.getDeclaredMethods());
    }

    @Test
    void scheduledMethods_WhenDeclaredOnTheReconciler_AreReconcileAlone() {
        assertThat(declaredMethods())
                .filteredOn(method -> method.isAnnotationPresent(Scheduled.class))
                .extracting(Method::getName)
                .containsExactly("reconcile");
    }

    @Test
    void reconcile_WhenAnnotated_CarriesBothScheduledAndReadOnlyTransactional() throws NoSuchMethodException {
        Method reconcile = PlannerDriftReconciler.class.getMethod("reconcile");

        assertThat(reconcile.getAnnotation(Scheduled.class)).isNotNull();
        assertThat(reconcile.getAnnotation(Transactional.class)).isNotNull();
        assertThat(reconcile.getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
