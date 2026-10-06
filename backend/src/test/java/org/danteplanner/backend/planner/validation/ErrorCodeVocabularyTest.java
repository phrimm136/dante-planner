package org.danteplanner.backend.planner.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import org.danteplanner.backend.architecture.ImportedClasses;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeVocabularyTest {

    private static final Path VOCABULARY = Path.of("../testdata/planner-error-codes.json");
    private static final String BACKEND = "backend";
    private static final String RULE = "rule";
    private static final String SEPARATOR = "_";

    private static final Set<String> NON_EMITTING_READERS = Set.of(
            ErrorCode.class.getName(),
            PlannerValidationException.class.getName());

    private static final JavaClasses PRODUCTION = ImportedClasses.production();

    @Test
    void errorCode_WhenComparedWithTheVocabulary_HoldsExactlyItsCodes() throws IOException {
        Set<String> enumCodes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(enumCodes).isEqualTo(new TreeSet<>(codes().keySet()));
    }

    @Test
    void errorCode_WhenEachConstantIsRead_CarriesItsOwnNameAsCode() {
        assertThat(ErrorCode.values()).allSatisfy(code -> assertThat(code.getCode()).isEqualTo(code.name()));
    }

    @Test
    void emittedCodes_WhenProductionIsScanned_AreTheCodesTheVocabularyAssignsToTheBackend() throws IOException {
        Set<String> assigned = codes().entrySet().stream()
                .filter(entry -> emittedBy(entry.getValue()).contains(BACKEND))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(emittedByProduction()).isEqualTo(assigned);
    }

    @Test
    void vocabulary_WhenRead_AssignsEveryCodeToAtLeastOneRuntime() throws IOException {
        assertThat(codes()).allSatisfy((code, entry) -> assertThat(emittedBy(entry)).as(code).isNotEmpty());
    }

    @Test
    void ruleCodes_WhenRead_NameTheirProblemFromTheClosedSetThenTheirSubject() throws IOException {
        JsonNode vocabulary = vocabulary();
        Set<String> problems = StreamSupport.stream(vocabulary.path("problems").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());

        assertThat(codes()).allSatisfy((code, entry) -> {
            if (RULE.equals(entry.path("kind").asText())) {
                String prefix = entry.path("problem").asText() + SEPARATOR + entry.path("subject").asText();
                assertThat(problems).as(code).contains(entry.path("problem").asText());
                assertThat(code.equals(prefix) || code.startsWith(prefix + SEPARATOR)).as(code).isTrue();
            }
        });
    }

    private static Set<String> emittedByProduction() {
        Set<String> constants = Arrays.stream(ErrorCode.values()).map(Enum::name).collect(Collectors.toSet());
        return PRODUCTION.get(ErrorCode.class).getFieldAccessesToSelf().stream()
                .filter(access -> access.getAccessType() == JavaFieldAccess.AccessType.GET)
                .filter(access -> !NON_EMITTING_READERS.contains(access.getOriginOwner().getName()))
                .map(JavaAccess::getTarget)
                .map(AccessTarget::getName)
                .filter(constants::contains)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> emittedBy(JsonNode entry) {
        return StreamSupport.stream(entry.path("emittedBy").spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
    }

    private static Map<String, JsonNode> codes() throws IOException {
        Map<String, JsonNode> codes = new TreeMap<>();
        vocabulary().path("codes").properties().forEach(entry -> codes.put(entry.getKey(), entry.getValue()));
        return codes;
    }

    private static JsonNode vocabulary() throws IOException {
        return new ObjectMapper().readTree(VOCABULARY.toFile());
    }
}
