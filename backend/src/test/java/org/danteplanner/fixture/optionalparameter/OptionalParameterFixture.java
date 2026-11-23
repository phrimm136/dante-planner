package org.danteplanner.fixture.optionalparameter;

import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.jspecify.annotations.Nullable;

@RestController
public class OptionalParameterFixture {

    @GetMapping("/bare-param")
    public void bareParam(@RequestParam(required = false) String q) {
    }

    @GetMapping("/bare-header")
    public void bareHeader(@RequestHeader(required = false) String agent) {
    }

    @GetMapping("/bare-cookie")
    public void bareCookie(@CookieValue(required = false) String token) {
    }

    @PostMapping("/bare-body")
    public void bareBody(@RequestBody(required = false) String content) {
    }

    @GetMapping("/bare-model")
    public void bareModel(@ModelAttribute BareModel model) {
    }

    @GetMapping("/unvalidated-model")
    public void unvalidatedModel(@ModelAttribute RequiredModel model) {
    }

    @GetMapping("/validated-model")
    public void validatedModel(@Valid @ModelAttribute RequiredModel model) {
    }

    @GetMapping("/declared-model")
    public void declaredModel(@ModelAttribute DeclaredModel model) {
    }

    public record BareModel(String q, int page) {
    }

    public record RequiredModel(@NotEmpty List<String> ids) {
    }

    public record DeclaredModel(@Nullable String q) {
    }

    @GetMapping("/declared")
    public void declared(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false) Optional<String> keyword,
            @RequestParam(required = false, defaultValue = "x") String sort,
            @RequestParam String required) {
    }
}
