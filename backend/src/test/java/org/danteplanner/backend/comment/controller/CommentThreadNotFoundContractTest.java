package org.danteplanner.backend.comment.controller;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.service.UsersApiAdapter;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * The comment thread of a planner the caller may not see answers exactly what the published detail
 * read answers for it: 404 PLANNER_NOT_FOUND.
 */
class CommentThreadNotFoundContractTest {

    private static final Long OWNER_ID = 1L;
    private static final Long STRANGER_ID = 2L;

    private final PlannerRepository plannerRepository = mock(PlannerRepository.class);
    private final PlannerCommentRepository commentRepository = mock(PlannerCommentRepository.class);
    private final UserService userService = mock(UserService.class);

    private MockMvc mockMvc;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        CommentQueryService queryService = new CommentQueryService(
                commentRepository, mock(PlannerCommentVoteRepository.class), new UsersApiAdapter(userService, null),
                new PlannerAccessGuard(userService, new UsersApiAdapter(userService, null), plannerRepository), new CommentAccessValidator());
        mockMvc = standaloneSetup(new CommentController(queryService, null, null, new ByIdReadGuard()))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new ApiExceptionHandler(mock(CookieUtils.class)))
                .build();

        User owner = User.builder()
                .id(OWNER_ID)
                .email("owner@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("owner-1")
                .usernameEpithet("OWNER")
                .usernameSuffix("own01")
                .role(UserRole.NORMAL)
                .build();
        plannerId = UUID.randomUUID();
        Planner unpublished = TestDataFactory.planner(owner)
                .id(plannerId)
                .category("5F")
                .content("{}")
                .contentVersion(1)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(false)
                .build();
        when(plannerRepository.findAggregate(plannerId)).thenReturn(Optional.of(unpublished));
        when(commentRepository.findByPlannerId(plannerId)).thenReturn(List.of());
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a non-owner reading an unpublished planner's thread gets the detail read's 404")
    void getComments_WhenNonOwnerReadsUnpublishedThread_AnswersPlannerNotFound() throws Exception {
        signInAs(STRANGER_ID);

        mockMvc.perform(get("/api/planner/{id}/comments", plannerId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Planner not found with id: " + plannerId));
    }

    @Test
    @DisplayName("an anonymous reader of an unpublished planner's thread gets the detail read's 404")
    void getComments_WhenAnonymousReadsUnpublishedThread_AnswersPlannerNotFound() throws Exception {
        mockMvc.perform(get("/api/planner/{id}/comments", plannerId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLANNER_NOT_FOUND"));
    }

    @Test
    @DisplayName("the owner still reads their own unpublished thread")
    void getComments_WhenOwnerReadsOwnUnpublishedThread_Succeeds() throws Exception {
        signInAs(OWNER_ID);

        mockMvc.perform(get("/api/planner/{id}/comments", plannerId).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private static void signInAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
