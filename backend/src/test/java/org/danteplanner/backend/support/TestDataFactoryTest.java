package org.danteplanner.backend.support;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    @Test
    void user_WhenTwoDefaultUsersShareOneFakeUsers_BothAreAdded() {
        FakeUsers users = new FakeUsers();

        assertThatCode(() -> {
            users.add(TestDataFactory.user().build());
            users.add(TestDataFactory.user().build());
        }).doesNotThrowAnyException();
    }

    @Test
    void user_WhenIdentityFieldsAreSetToNull_CarriesTheNulls() {
        User user = TestDataFactory.user().provider(null).providerId(null).email(null).build();

        assertThat(user.getProvider()).isNull();
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getEmail()).isNull();
    }
}
