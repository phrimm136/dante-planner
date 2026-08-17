package org.danteplanner.backend.user.service;

import org.springframework.dao.DataIntegrityViolationException;

import org.danteplanner.backend.support.IntegrityViolations;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which integrity violation the username generator retries against a new suffix.
 *
 * <p>The violated key is read off the exception structurally, so each case here goes through
 * {@link IntegrityViolations#duplicateEntry} rather than a message a test author typed.</p>
 */
class UsernameSuffixCollisionTest {

    @Test
    @DisplayName("the suffix key is recognized when MySQL qualifies it with its table")
    void usernameSuffixCollision_WhenKeyQualifiedByTable_IsDetected() {
        assertThat(UserService.isUsernameSuffixCollision(
                IntegrityViolations.duplicateEntry("users.uk_users_username_suffix"))).isTrue();
    }

    @Test
    @DisplayName("the suffix key is recognized when MySQL reports it bare")
    void usernameSuffixCollision_WhenKeyUnqualified_IsDetected() {
        assertThat(UserService.isUsernameSuffixCollision(
                IntegrityViolations.duplicateEntry("uk_users_username_suffix"))).isTrue();
    }

    @Test
    @DisplayName("a lost race on the provider identity is not a suffix collision")
    void usernameSuffixCollision_WhenAnotherKeyViolated_IsNotDetected() {
        assertThat(UserService.isUsernameSuffixCollision(
                IntegrityViolations.duplicateEntry("users.uk_provider_provider_id"))).isFalse();
    }

    @Test
    @DisplayName("a violation naming no key is not claimed as a suffix collision")
    void usernameSuffixCollision_WhenNoConstraintNamed_IsNotDetected() {
        assertThat(UserService.isUsernameSuffixCollision(new DataIntegrityViolationException(
                "Duplicate entry 'abcde' for key 'users.uk_users_username_suffix'"))).isFalse();
    }
}
