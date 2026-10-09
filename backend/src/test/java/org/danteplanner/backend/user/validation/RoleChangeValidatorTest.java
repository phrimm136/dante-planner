package org.danteplanner.backend.user.validation;

import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleChangeValidatorTest {

    @Mock
    private UserRepository userRepository;

    private RoleChangeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RoleChangeValidator(userRepository);
    }

    @Test
    void requireCanChangeRole_WhenNewRoleOutranksActor_ThrowsForbidden() {
        assertRejected(() -> validator.requireCanChangeRole(TestDataFactory.unsavedUser(UserRole.MODERATOR),
                TestDataFactory.unsavedUser(UserRole.NORMAL), UserRole.ADMIN))
                .withCode("FORBIDDEN")
                .satisfies(rejection -> assertThat(rejection.getMessage())
                        .isEqualTo("Cannot grant role higher than your own"));
    }

    @Test
    void requireCanChangeRole_WhenTargetIsOfEqualRank_ThrowsForbidden() {
        assertRejected(() -> validator.requireCanChangeRole(TestDataFactory.unsavedUser(UserRole.ADMIN),
                TestDataFactory.unsavedUser(UserRole.ADMIN), UserRole.MODERATOR))
                .withCode("FORBIDDEN")
                .satisfies(rejection -> assertThat(rejection.getMessage())
                        .isEqualTo("Cannot modify user of equal or higher rank"));
    }

    @Test
    void requireCanChangeRole_WhenAnAdminDemotesThemselfBesideAnotherActiveAdmin_Allows() {
        User admin = TestDataFactory.unsavedUser(UserRole.ADMIN);
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(2L);

        assertThatCode(() -> validator.requireCanChangeRole(admin, admin, UserRole.NORMAL))
                .doesNotThrowAnyException();
    }

    @Test
    void requireCanChangeRole_WhenTheLastActiveAdminDemotesThemself_ThrowsLastAdministrator() {
        User admin = TestDataFactory.unsavedUser(UserRole.ADMIN);
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(1L);

        assertRejected(() -> validator.requireCanChangeRole(admin, admin, UserRole.NORMAL))
                .withCode("LAST_ADMINISTRATOR")
                .satisfies(rejection -> assertThat(rejection.getMessage())
                        .isEqualTo("Another active administrator must remain"));
    }

    @Test
    void requireCanChangeRole_WhenNoAdministratorIsDemoted_AllowsWithoutCounting() {
        assertThatCode(() -> validator.requireCanChangeRole(TestDataFactory.unsavedUser(UserRole.ADMIN),
                TestDataFactory.unsavedUser(UserRole.NORMAL), UserRole.MODERATOR))
                .doesNotThrowAnyException();
    }

    @Test
    void requireAnotherActiveAdministrator_WhenOneActiveAdminRemains_ThrowsLastAdministrator() {
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(1L);

        assertRejected(() -> validator.requireAnotherActiveAdministrator())
                .withCode("LAST_ADMINISTRATOR");
    }

    @Test
    void requireAnotherActiveAdministrator_WhenTwoActiveAdminsRemain_Allows() {
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(2L);

        assertThatCode(() -> validator.requireAnotherActiveAdministrator()).doesNotThrowAnyException();
    }
}
