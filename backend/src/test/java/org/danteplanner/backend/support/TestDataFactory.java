package org.danteplanner.backend.support;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import java.util.concurrent.atomic.AtomicLong;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerContentLifecycle;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.auth.token.JwtTokenService;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class TestDataFactory {

    /**
     * Planner content that passes {@code PlannerContentValidator}: twelve equipped sinners, a
     * deployment order over them, buff ids and a gift selection drawn from the Combustion pool,
     * and one floor selection.
     */
    public static final String VALID_CONTENT = """
        {
            "selectedKeywords":[],
            "selectedBuffIds":[100,201],
            "selectedGiftKeyword":"Combustion",
            "selectedGiftIds":["9001"],
            "equipment":{
                "01":{"identity":{"id":"10101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20101","threadspin":4}}},
                "02":{"identity":{"id":"10201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20201","threadspin":4}}},
                "03":{"identity":{"id":"10301","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20301","threadspin":4}}},
                "04":{"identity":{"id":"10401","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20401","threadspin":4}}},
                "05":{"identity":{"id":"10501","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20501","threadspin":4}}},
                "06":{"identity":{"id":"10601","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20601","threadspin":4}}},
                "07":{"identity":{"id":"10701","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20701","threadspin":4}}},
                "08":{"identity":{"id":"10801","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20801","threadspin":4}}},
                "09":{"identity":{"id":"10901","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"20901","threadspin":4}}},
                "10":{"identity":{"id":"11001","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21001","threadspin":4}}},
                "11":{"identity":{"id":"11101","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21101","threadspin":4}}},
                "12":{"identity":{"id":"11201","uptie":4,"level":45},"egos":{"ZAYIN":{"id":"21201","threadspin":4}}}
            },
            "deploymentOrder":[0,1,2,3,4,5],
            "floorSelections":[{"themePackId":"1001","difficulty":0,"giftIds":["9002"]}],
            "sectionNotes":{}
        }
        """.trim().replace("\n", "").replace(" ", "");

    /**
     * Makes a caller's label unique.
     *
     * <p>Every integration class shares one database, so a fixed address collides the moment two
     * classes, or two methods, create "their" user. Sub-addressing keeps the label readable in a
     * failure message while making the row this test's own, which is what lets the suite drop the
     * global truncation that used to buy the same guarantee by deleting everyone else's rows.</p>
     *
     * @param requested the caller's readable label
     * @return an address no other test will produce
     */
    // Seeded past the all-digit range so a generated suffix never collides with a literal
    // someone typed into a fixture.
    private static final AtomicLong SUFFIX_SEQUENCE = new AtomicLong(10L * 36 * 36 * 36 * 36);

    // Seeded past any id a fixture would plausibly hard-code.
    private static final AtomicLong USER_ID_SEQUENCE = new AtomicLong(9_000_000L);

    /**
     * A user id no other class in this JVM will use. Services key Redis entries by user id
     * ({@code uinv:<id>}), and classes sharing the per-fork Redis therefore share that namespace:
     * a literal id claims a name any other class may also claim.
     *
     * @return an id unique within this JVM
     */
    public static Long nextUserId() {
        return USER_ID_SEQUENCE.getAndIncrement();
    }

    /**
     * A suffix unique by construction rather than by luck. {@code username_suffix} is UNIQUE, and
     * the column holds five characters: a random four of them is a 65k space, which a suite
     * creating thousands of users in one database collides in.
     */
    public static String uniqueSuffix(String prefix) {
        int width = 5 - prefix.length();
        String encoded = Long.toString(SUFFIX_SEQUENCE.getAndIncrement(), 36);
        if (encoded.length() > width) {
            encoded = encoded.substring(encoded.length() - width);
        }
        return prefix + "0".repeat(width - encoded.length()) + encoded;
    }

    private static String uniqueEmail(String requested) {
        int at = requested.indexOf('@');
        String local = at < 0 ? requested : requested.substring(0, at);
        String domain = at < 0 ? "example.com" : requested.substring(at + 1);
        return local + "+" + UUID.randomUUID() + "@" + domain;
    }

    public static User createTestUser(UserRepository userRepository, String email) {
        User user = User.builder()
                .email(uniqueEmail(email))
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix(uniqueSuffix(""))
                .build();
        return userRepository.save(user);
    }

    /**
     * An in-memory user for the unit tier. Nothing there reaches a database, so the fields only
     * have to be well-formed; a fixture that will be persisted goes through
     * {@link #createTestUser} instead, which sub-addresses it.
     *
     * @param id the id the user answers with
     * @return an unsaved user
     */
    public static User unsavedUser(Long id) {
        return User.builder()
                .id(id)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();
    }

    public static User createAdmin(UserRepository userRepository, String email) {
        User admin = User.builder()
                .email(uniqueEmail(email))
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix(uniqueSuffix("a"))
                .role(UserRole.ADMIN)
                .build();
        return userRepository.save(admin);
    }

    public static User createModerator(UserRepository userRepository, String email) {
        User moderator = User.builder()
                .email(uniqueEmail(email))
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix(uniqueSuffix("m"))
                .role(UserRole.MODERATOR)
                .build();
        return userRepository.save(moderator);
    }

    public static Planner createTestPlanner(PlannerRepository plannerRepository, User owner, boolean published) {
        return planner(owner).published(published).save(plannerRepository);
    }

    public static PlannerBuilder planner(User owner) {
        return new PlannerBuilder(owner);
    }

    /**
     * Fluent aggregate builder: assembles the planner core plus its content,
     * publication, and moderation satellites in one call chain.
     */
    public static class PlannerBuilder {
        private final User owner;
        private UUID id = UUID.randomUUID();
        private String title = "Test Planner";
        private String category = "5F";
        private PlannerStatus status;
        private String content = VALID_CONTENT;
        private Integer schemaVersion = 1;
        private Integer contentVersion = 6;
        private PlannerType plannerType = PlannerType.MIRROR_DUNGEON;
        private Set<String> selectedKeywords;
        private boolean published;
        private Instant firstPublishedAt;

        private PlannerBuilder(User owner) {
            this.owner = owner;
        }

        public PlannerBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public PlannerBuilder title(String title) {
            this.title = title;
            return this;
        }

        public PlannerBuilder category(String category) {
            this.category = category;
            return this;
        }

        public PlannerBuilder status(PlannerStatus status) {
            this.status = status;
            return this;
        }

        public PlannerBuilder content(String content) {
            this.content = content;
            return this;
        }

        public PlannerBuilder schemaVersion(Integer schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public PlannerBuilder contentVersion(Integer contentVersion) {
            this.contentVersion = contentVersion;
            return this;
        }

        public PlannerBuilder plannerType(PlannerType plannerType) {
            this.plannerType = plannerType;
            return this;
        }

        public PlannerBuilder selectedKeywords(Set<String> selectedKeywords) {
            this.selectedKeywords = selectedKeywords;
            return this;
        }

        public PlannerBuilder published(boolean published) {
            this.published = published;
            return this;
        }

        public PlannerBuilder firstPublishedAt(Instant firstPublishedAt) {
            this.firstPublishedAt = firstPublishedAt;
            return this;
        }

        public Planner build() {
            Planner planner = Planner.builder()
                    .id(id)
                    .user(owner)
                    .plannerType(plannerType)
                    .build();
            planner.attach(
                    PlannerContentLifecycle.asPersisted(PlannerContent.builder()
                            .title(title)
                            .category(category)
                            .status(status != null ? status
                                    : (published ? PlannerStatus.SAVED : PlannerStatus.DRAFT))
                            .content(content)
                            .contentSchemaVersion(schemaVersion)
                            .gameContentVersion(contentVersion)
                            .selectedKeywords(selectedKeywords)
                            .build()),
                    PlannerPublication.builder().build(),
                    PlannerModeration.builder().build());
            if (published) {
                planner.publish();
            }
            if (firstPublishedAt != null) {
                planner.getPublication().setFirstPublishedAt(firstPublishedAt);
            }
            return planner;
        }

        public Planner save(PlannerRepository plannerRepository) {
            return plannerRepository.save(build());
        }
    }

    public static String generateAccessToken(JwtTokenService jwtTokenService, User user) {
        return jwtTokenService.generateAccessToken(user.getId(), user.getRole());
    }
}
