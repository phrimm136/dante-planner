package org.danteplanner.backend.user.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    private Long id;

    @Column(name = "username_epithet", nullable = false, length = 50)
    private String usernameEpithet;

    @Column(name = "username_suffix", nullable = false, length = 5)
    private String usernameSuffix;
}
