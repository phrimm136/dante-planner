package org.danteplanner.backend.support;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.entity.User;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeUsersContractTest implements UsersContract, UsersRoleCountContract {

    private final FakeUsers users = new FakeUsers();

    @Override
    public Users users() {
        return users;
    }

    @Override
    public User persisted(User user) {
        return users.add(user);
    }

    @Override
    public Long liveSentinelId() {
        return users.add(User.builder()
                .id(Users.SENTINEL_USER_ID)
                .email("[deleted]")
                .provider(AuthProviderType.SYSTEM)
                .providerId("DELETED_USER_SENTINEL")
                .build()).getId();
    }

    @Override
    public Class<? extends RuntimeException> duplicateKeyFailure() {
        return IllegalStateException.class;
    }

    @Test
    void add_WhenTheIdIsTaken_IsRejectedByThePrimaryKey() {
        User user = users.add(UsersContract.newUser().build());

        assertThatThrownBy(() -> users.add(UsersContract.newUser().id(user.getId()).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate users.PRIMARY id " + user.getId());
    }

    @Test
    void add_WhenThePublicIdIsTaken_IsRejectedByThePublicIdKey() {
        UUID publicId = UUID.randomUUID();
        users.add(UsersContract.newUser().publicId(publicId).build());

        assertThatThrownBy(() -> users.add(UsersContract.newUser().publicId(publicId).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate users.idx_user_public_id " + publicId);
    }
}
