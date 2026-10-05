package org.danteplanner.backend.support;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataFactoryTest {

    @Test
    void unsavedUser_WhenGivenARole_CarriesItWithAnIdentityNoOtherCallReceives() {
        User admin = TestDataFactory.unsavedUser(UserRole.ADMIN);
        User other = TestDataFactory.unsavedUser(UserRole.ADMIN);

        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getId()).isNotEqualTo(other.getId());
        assertThat(admin.getPublicId()).isNotEqualTo(other.getPublicId());
        assertThat(admin.getUsernameSuffix()).isNotEqualTo(other.getUsernameSuffix());
    }
}
