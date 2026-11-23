package org.danteplanner.backend.shared.config;

import java.util.List;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.danteplanner.backend.shared.security.Viewer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewerArgumentResolverTest {

    private static final int VIEWER = 0;
    private static final int MEMBER = 1;
    private static final int PRINCIPAL = 2;

    private final ViewerArgumentResolver resolver = new ViewerArgumentResolver();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unused")
    private static void handler(Viewer viewer, Viewer.Member member, Long userId) {
    }

    private static Authentication signedIn(Object principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    static List<Authentication> authenticationsWithoutAUserId() {
        return List.of(
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))),
                signedIn(null),
                signedIn("someone"));
    }

    @Test
    void supportsParameter_WhenTheTypeIsAViewerOrAMember_IsTrueOnlyForThose() throws Exception {
        assertThat(resolver.supportsParameter(parameter(VIEWER))).isTrue();
        assertThat(resolver.supportsParameter(parameter(MEMBER))).isTrue();
        assertThat(resolver.supportsParameter(parameter(PRINCIPAL))).isFalse();
    }

    @Test
    void resolveArgument_WhenAUserIsSignedIn_BindsTheMember() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(signedIn(7L));

        assertThat(resolve(VIEWER)).isEqualTo(new Viewer.Member(7L));
        assertThat(resolve(MEMBER)).isEqualTo(new Viewer.Member(7L));
    }

    @Test
    void resolveArgument_WhenThereIsNoAuthentication_BindsAGuestAndRefusesAMember() throws Exception {
        assertThat(resolve(VIEWER)).isEqualTo(new Viewer.Guest());
        assertThatThrownBy(() -> resolve(MEMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a member route was reached without a signed-in user");
    }

    @ParameterizedTest
    @MethodSource("authenticationsWithoutAUserId")
    void resolveArgument_WhenThePrincipalIsNotAUserId_BindsAGuestAndRefusesAMember(Authentication authentication)
            throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(resolve(VIEWER)).isEqualTo(new Viewer.Guest());
        assertThatThrownBy(() -> resolve(MEMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a member route was reached without a signed-in user");
    }

    private Object resolve(int index) throws Exception {
        return resolver.resolveArgument(parameter(index), null, null, null);
    }

    private static MethodParameter parameter(int index) throws Exception {
        return new MethodParameter(ViewerArgumentResolverTest.class.getDeclaredMethod(
                "handler", Viewer.class, Viewer.Member.class, Long.class), index);
    }
}
