package org.danteplanner.backend.support;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
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

    private static final String VALID_CONTENT = """
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

    public static User createTestUser(UserRepository userRepository, String email) {
        User user = User.builder()
                .email(email)
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix(UUID.randomUUID().toString().substring(0, 5))
                .build();
        return userRepository.save(user);
    }

    public static User createAdmin(UserRepository userRepository, String email) {
        User admin = User.builder()
                .email(email)
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix("a" + UUID.randomUUID().toString().substring(0, 4))
                .role(UserRole.ADMIN)
                .build();
        return userRepository.save(admin);
    }

    public static User createModerator(UserRepository userRepository, String email) {
        User moderator = User.builder()
                .email(email)
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix("m" + UUID.randomUUID().toString().substring(0, 4))
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
                    PlannerContent.builder()
                            .title(title)
                            .category(category)
                            .status(status != null ? status
                                    : (published ? PlannerStatus.SAVED : PlannerStatus.DRAFT))
                            .content(content)
                            .contentSchemaVersion(schemaVersion)
                            .gameContentVersion(contentVersion)
                            .selectedKeywords(selectedKeywords)
                            .build(),
                    PlannerPublication.builder().build(),
                    PlannerModeration.builder().build());
            if (published) {
                planner.togglePublished();
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
