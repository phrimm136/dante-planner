package org.danteplanner.backend.support;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

public interface UsersRoleCountContract {

    Users users();

    User persisted(User user);

    @Test
    default void countByRole_WhenAnAdminIsAdded_RisesByOne() {
        long before = users().countByRole(UserRole.ADMIN);

        persisted(UsersContract.newUser().role(UserRole.ADMIN).build());

        assertThat(users().countByRole(UserRole.ADMIN)).isEqualTo(before + 1);
    }

    @Test
    default void countByRole_WhenTheAddedAdminIsDeleted_StillCountsIt() {
        long before = users().countByRole(UserRole.ADMIN);

        persisted(UsersContract.deletedUser().role(UserRole.ADMIN).build());

        assertThat(users().countByRole(UserRole.ADMIN)).isEqualTo(before + 1);
    }

    @Test
    default void countByRole_WhenANormalUserIsAdded_LeavesTheAdminCountAlone() {
        long before = users().countByRole(UserRole.ADMIN);

        persisted(UsersContract.newUser().build());

        assertThat(users().countByRole(UserRole.ADMIN)).isEqualTo(before);
    }
}
