package org.danteplanner.backend.shared.sse;

import java.util.List;
import java.util.UUID;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.databind.json.JsonMapper;

import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.controller.SseController;
import org.danteplanner.backend.shared.exception.ApiExceptionHandler;
import org.danteplanner.backend.shared.exception.StreamLifecycleExceptionAdvice;
import org.danteplanner.backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class SseSubscribeDrainRaceTest {

    private static final Long USER_ID = 7L;
    private static final UUID DEVICE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final NotificationPreferenceProvider.Preferences PREFERENCES =
            new NotificationPreferenceProvider.Preferences(true, true, false);

    @Test
    void subscribe_WhenTheDrainLandsDuringThePreferenceLookup_DoesNotThrow() {
        SseService service = serviceDrainedDuringPreferenceLookup();

        assertThatCode(() -> service.subscribe(USER_ID, DEVICE_ID))
                .as("a subscribe the shutdown drain overtakes after registration")
                .doesNotThrowAnyException();
    }

    @Test
    void subscribeRequest_WhenTheDrainLandsDuringThePreferenceLookup_IsNotAnUnexpectedError() throws Exception {
        SseService service = serviceDrainedDuringPreferenceLookup();
        ApiExceptionHandler apiExceptionHandler = new ApiExceptionHandler();
        MockMvc mockMvc = standaloneSetup(new SseController(service))
                .setControllerAdvice(apiExceptionHandler, new StreamLifecycleExceptionAdvice())
                .setCustomArgumentResolvers(new FixedPrincipal(), new FixedDevice())
                .build();

        List<ILoggingEvent> errors;
        MockHttpServletResponse response;
        try (LogCapture capture = LogCapture.onThisThread(ApiExceptionHandler.class)) {
            response = mockMvc.perform(get("/api/sse/subscribe").accept(MediaType.TEXT_EVENT_STREAM))
                    .andReturn().getResponse();
            errors = capture.at(Level.ERROR);
        }

        assertThat(errors.stream().map(SseSubscribeDrainRaceTest::describe).toList())
                .as("errors logged; response status=%d contentType=%s body=%s",
                        response.getStatus(), response.getContentType(), response.getContentAsString())
                .isEmpty();
    }

    private static String describe(ILoggingEvent event) {
        return event.getFormattedMessage()
                + (event.getThrowableProxy() == null ? ""
                        : " <- " + event.getThrowableProxy().getClassName()
                                + ": " + event.getThrowableProxy().getMessage());
    }

    private static SseService serviceDrainedDuringPreferenceLookup() {
        SseService[] holder = new SseService[1];
        NotificationPreferenceProvider drainingLookup = userId -> {
            try (GenericApplicationContext context = new GenericApplicationContext()) {
                holder[0].onApplicationEvent(new ContextClosedEvent(context));
            }
            return PREFERENCES;
        };
        holder[0] = new SseService(JsonMapper.builder().build(), drainingLookup, new SyncTaskExecutor());
        return holder[0];
    }

    private static final class FixedPrincipal implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return USER_ID;
        }
    }

    private static final class FixedDevice implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(DeviceId.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return DEVICE_ID;
        }
    }
}
