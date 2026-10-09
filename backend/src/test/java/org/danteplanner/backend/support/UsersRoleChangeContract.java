package org.danteplanner.backend.support;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.user.api.LastAdministratorException;
import org.danteplanner.backend.user.api.RoleChange;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public interface UsersRoleChangeContract {

    Users users();

    User persisted(User user);

    void leaveNoAdministrator();

    <T> T inTransaction(Supplier<T> work);

    @Test
    default void changeRole_WhenAnotherActiveAdminRemains_AllowsTheSelfDemotion() {
        leaveNoAdministrator();
        User admin = persisted(UsersContract.newUser().role(UserRole.ADMIN).build());
        persisted(UsersContract.newUser().role(UserRole.ADMIN).build());

        RoleChange change = inTransaction(() -> users().changeRole(admin.getId(), admin.getId(), UserRole.MODERATOR));

        assertThat(change.previousRole()).isEqualTo(UserRole.ADMIN);
        assertThat(users().findById(admin.getId()).role()).isEqualTo(UserRole.MODERATOR);
    }

    @Test
    default void changeRole_WhenTheActorIsTheLastAdmin_ThrowsLastAdministrator() {
        leaveNoAdministrator();
        User admin = persisted(UsersContract.newUser().role(UserRole.ADMIN).build());

        assertThatThrownBy(() -> inTransaction(
                () -> users().changeRole(admin.getId(), admin.getId(), UserRole.MODERATOR)))
                .isInstanceOf(LastAdministratorException.class)
                .hasMessage("Another active administrator must remain");
        assertThat(users().findById(admin.getId()).role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    default void changeRole_WhenTheOnlyOtherAdminIsWithdrawn_ThrowsLastAdministrator() {
        leaveNoAdministrator();
        User admin = persisted(UsersContract.newUser().role(UserRole.ADMIN).build());
        persisted(UsersContract.deletedUser().role(UserRole.ADMIN).build());

        assertThatThrownBy(() -> inTransaction(
                () -> users().changeRole(admin.getId(), admin.getId(), UserRole.MODERATOR)))
                .isInstanceOf(LastAdministratorException.class)
                .hasMessage("Another active administrator must remain");
        assertThat(users().findById(admin.getId()).role()).isEqualTo(UserRole.ADMIN);
    }
}
