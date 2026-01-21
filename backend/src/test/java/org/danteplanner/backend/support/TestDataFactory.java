package org.danteplanner.backend.support;

import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.service.token.JwtTokenService;

import java.time.Instant;
import java.util.UUID;

public class TestDataFactory {

    private static final String VALID_CONTENT = """
        {
            "title":"Test",
            "category":"5F",
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
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix(UUID.randomUUID().toString().substring(0, 5))
                .build();
        return userRepository.save(user);
    }

    public static User createAdmin(UserRepository userRepository, String email) {
        User admin = User.builder()
                .email(email)
                .provider("google")
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
                .provider("google")
                .providerId("google-" + UUID.randomUUID())
                .usernameEpithet("W_CORP")
                .usernameSuffix("m" + UUID.randomUUID().toString().substring(0, 4))
                .role(UserRole.MODERATOR)
                .build();
        return userRepository.save(moderator);
    }

    public static Planner createTestPlanner(PlannerRepository plannerRepository, User owner, boolean published) {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .title("Test Planner")
                .category("5F")
                .status(published ? "published" : "draft")
                .content(VALID_CONTENT)
                .syncVersion(1L)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(published)
                .savedAt(Instant.now())
                .build();
        return plannerRepository.save(planner);
    }

    public static String generateAccessToken(JwtTokenService jwtTokenService, User user) {
        return jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
    }
}
