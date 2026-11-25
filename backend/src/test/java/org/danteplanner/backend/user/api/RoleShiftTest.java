package org.danteplanner.backend.user.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleShiftTest {

    @ParameterizedTest
    @CsvSource({
            "NORMAL, NORMAL, SAME",
            "NORMAL, MODERATOR, PROMOTED",
            "NORMAL, ADMIN, PROMOTED",
            "MODERATOR, NORMAL, DEMOTED",
            "MODERATOR, MODERATOR, SAME",
            "MODERATOR, ADMIN, PROMOTED",
            "ADMIN, NORMAL, DEMOTED",
            "ADMIN, MODERATOR, DEMOTED",
            "ADMIN, ADMIN, SAME"})
    void of_WhenRolesAreCompared_NamesTheDirection(UserRole previous, UserRole next, RoleShift shift) {
        assertThat(RoleShift.of(previous, next)).isEqualTo(shift);
    }
}
