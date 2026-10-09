package org.danteplanner.consistency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryTest {

    @TempDir
    private Path dir;

    @ParameterizedTest
    @ValueSource(ints = {200, 299})
    void saveOk_WhenStatusIs2xxWithAVersion_IsApplied(int status) throws IOException {
        assertThat(only(line("save", "ok", "'status':" + status + ",'syncVersion':5")))
                .isEqualTo(new Save("s1", "p1", 10, 20, new Applied(5)));
    }

    @ParameterizedTest
    @ValueSource(ints = {199, 300})
    void saveOk_WhenStatusIsNot2xx_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("save", "ok", "'status':" + status + ",'syncVersion':5"));
    }

    @Test
    void saveOk_WhenTheVersionIsMissing_IsMalformed() throws IOException {
        assertMalformed(1, line("save", "ok", "'status':200"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 499})
    void saveFail_WhenStatusIs4xx_IsRejected(int status) throws IOException {
        assertThat(only(line("save", "fail", "'status':" + status)))
                .isEqualTo(new Save("s1", "p1", 10, 20, new Rejected(status)));
    }

    @ParameterizedTest
    @ValueSource(ints = {399, 500})
    void saveFail_WhenStatusIsNot4xx_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("save", "fail", "'status':" + status));
    }

    @Test
    void saveFail_WhenStatusIsMissing_IsMalformed() throws IOException {
        assertMalformed(1, line("save", "fail", "'syncVersion':5"));
    }

    @Test
    void saveUnknown_WhenNoResponseArrived_IsUnknown() throws IOException {
        assertThat(only(line("save", "unknown", null)))
                .isEqualTo(new Save("s1", "p1", 10, 20, new Unknown()));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 599})
    void saveUnknown_WhenStatusIs5xx_IsUnknown(int status) throws IOException {
        assertThat(only(line("save", "unknown", "'status':" + status)))
                .isEqualTo(new Save("s1", "p1", 10, 20, new Unknown()));
    }

    @ParameterizedTest
    @ValueSource(ints = {499, 600})
    void saveUnknown_WhenStatusIsNot5xx_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("save", "unknown", "'status':" + status));
    }

    @Test
    void deleteOk_WhenStatusIs204_IsDeleted() throws IOException {
        assertThat(only(line("delete", "ok", "'status':204")))
                .isEqualTo(new Delete("s1", "p1", 10, 20, new Deleted()));
    }

    @Test
    void deleteOk_WhenStatusIsNot204_IsMalformed() throws IOException {
        assertMalformed(1, line("delete", "ok", "'status':200"));
    }

    @Test
    void deleteFail_WhenStatusIs4xx_IsRejected() throws IOException {
        assertThat(only(line("delete", "fail", "'status':409")))
                .isEqualTo(new Delete("s1", "p1", 10, 20, new Rejected(409)));
    }

    @Test
    void deleteFail_WhenStatusIsNot4xx_IsMalformed() throws IOException {
        assertMalformed(1, line("delete", "fail", "'status':500"));
    }

    @Test
    void deleteUnknown_WhenNoResponseArrived_IsUnknown() throws IOException {
        assertThat(only(line("delete", "unknown", null)))
                .isEqualTo(new Delete("s1", "p1", 10, 20, new Unknown()));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 599})
    void deleteUnknown_WhenStatusIs5xx_IsUnknown(int status) throws IOException {
        assertThat(only(line("delete", "unknown", "'status':" + status)))
                .isEqualTo(new Delete("s1", "p1", 10, 20, new Unknown()));
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 499, 600})
    void deleteUnknown_WhenStatusIsNot5xx_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("delete", "unknown", "'status':" + status));
    }

    @Test
    void readOk_WhenStatusIs200WithAVersion_IsFound() throws IOException {
        assertThat(only(line("read", "ok", "'status':200,'syncVersion':7")))
                .isEqualTo(new Read("s1", "p1", 10, 20, new Found(7)));
    }

    @Test
    void readOk_WhenStatusIs200WithoutAVersion_IsMalformed() throws IOException {
        assertMalformed(1, line("read", "ok", "'status':200"));
    }

    @Test
    void readOk_WhenStatusIs404_IsNotFound() throws IOException {
        assertThat(only(line("read", "ok", "'status':404")))
                .isEqualTo(new Read("s1", "p1", 10, 20, new NotFound()));
    }

    @Test
    void readOk_WhenStatusIsNeither200Nor404_IsMalformed() throws IOException {
        assertMalformed(1, line("read", "ok", "'status':500"));
    }

    @Test
    void readFail_WhenStatusIsAnyOther_IsReadFailed() throws IOException {
        assertThat(only(line("read", "fail", "'status':503")))
                .isEqualTo(new Read("s1", "p1", 10, 20, new ReadFailed(503)));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404})
    void readFail_WhenStatusIs200Or404_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("read", "fail", "'status':" + status));
    }

    @Test
    void readFail_WhenStatusIsMissing_IsMalformed() throws IOException {
        assertMalformed(1, line("read", "fail", null));
    }

    @Test
    void readUnknown_WhenNoResponseArrived_IsReadFailedWithStatusZero() throws IOException {
        assertThat(only(line("read", "unknown", null)))
                .isEqualTo(new Read("s1", "p1", 10, 20, new ReadFailed(0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 599})
    void readUnknown_WhenStatusIs5xx_IsReadFailedWithStatusZero(int status) throws IOException {
        assertThat(only(line("read", "unknown", "'status':" + status)))
                .isEqualTo(new Read("s1", "p1", 10, 20, new ReadFailed(0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 499, 600})
    void readUnknown_WhenStatusIsNot5xx_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("read", "unknown", "'status':" + status + ",'syncVersion':1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "read"})
    void unknown_WhenStatusIsNull_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "unknown", "'status':null"));
    }

    @Test
    void listOk_WhenKeysArePresent_IsAListRead() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'list','invokedAt':10,'completedAt':20,'outcome':'ok',"
                + "'status':200,'keys':['p1','p2']}")))
                .isEqualTo(new ListRead("s1", 10, 20, List.of("p1", "p2")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fail", "unknown"})
    void list_WhenTheOutcomeIsNotOk_IsSkipped(String outcome) throws IOException {
        assertThat(read(json("{'session':'s1','kind':'list','invokedAt':10,'completedAt':20,'outcome':'"
                + outcome + "'}")).operations()).isEmpty();
    }

    @Test
    void listOk_WhenKeysAreMissing_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'list','invokedAt':10,'completedAt':20,'outcome':'ok'}"));
    }

    @Test
    void listOk_WhenAKeyIsNotAString_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'list','invokedAt':10,'completedAt':20,'outcome':'ok',"
                + "'keys':['p1',2]}"));
    }

    @Test
    void list_WhenTheOutcomeIsUnknownWord_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'list','invokedAt':10,'completedAt':20,'outcome':'maybe',"
                + "'keys':[]}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "read"})
    void outcome_WhenNotOkFailOrUnknown_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "maybe", "'status':200,'syncVersion':1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "read"})
    void key_WhenMissingFromAKeyedKind_IsMalformed(String kind) throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'" + kind + "','invokedAt':10,'completedAt':20,"
                + "'outcome':'unknown'}"));
    }

    @Test
    void kind_WhenNotAKnownKind_IsMalformed() throws IOException {
        assertMalformed(1, line("update", "ok", "'status':200"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{'kind':'read','key':'p1','invokedAt':10,'completedAt':20,'outcome':'unknown'}",
        "{'session':'s1','key':'p1','invokedAt':10,'completedAt':20,'outcome':'unknown'}",
        "{'session':'s1','kind':'read','key':'p1','invokedAt':10,'completedAt':20}"})
    void line_WhenAStringFieldIsMissing_IsMalformed(String singleQuoted) throws IOException {
        assertMalformed(1, json(singleQuoted));
    }

    @Test
    void session_WhenNotAString_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':1,'kind':'read','key':'p1','invokedAt':10,'completedAt':20,"
                + "'outcome':'unknown'}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'10'", "10.5", "100000000000000000000", "null"})
    void invokedAt_WhenNotALong_IsMalformed(String value) throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'read','key':'p1','invokedAt':" + value
                + ",'completedAt':20,'outcome':'unknown'}"));
    }

    @Test
    void completedAt_WhenMissing_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'read','key':'p1','invokedAt':10,'outcome':'unknown'}"));
    }

    @Test
    void completedAt_WhenBeforeInvokedAt_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'read','key':'p1','invokedAt':10,'completedAt':9,"
                + "'outcome':'unknown'}"));
    }

    @Test
    void completedAt_WhenEqualToInvokedAt_IsAccepted() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'read','key':'p1','invokedAt':10,'completedAt':10,"
                + "'outcome':'unknown'}"))).isEqualTo(new Read("s1", "p1", 10, 10, new ReadFailed(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'204'", "204.0"})
    void status_WhenNotAnInteger_IsMalformed(String value) throws IOException {
        assertMalformed(1, line("delete", "ok", "'status':" + value));
    }

    @Test
    void line_WhenNotJson_IsMalformedNamingTheLine() throws IOException {
        assertMalformed(3, line("read", "unknown", null), line("read", "unknown", null), "{not json");
    }

    @Test
    void line_WhenBlank_IsMalformedNamingTheLine() throws IOException {
        assertMalformed(2, line("read", "unknown", null), "  ", line("read", "unknown", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1]", "null", "5", "\"read\""})
    void line_WhenNotAJsonObject_IsMalformed(String text) throws IOException {
        assertMalformed(1, text);
    }

    @Test
    void line_WhenTrailedByAnotherValue_IsMalformed() throws IOException {
        assertMalformed(1, line("read", "unknown", null) + " {}");
    }

    @Test
    void read_WhenSeveralLines_KeepsTheirOrder() throws IOException {
        History history = read(line("read", "unknown", null), line("delete", "unknown", null));

        assertThat(history.operations()).containsExactly(
                new Read("s1", "p1", 10, 20, new ReadFailed(0)),
                new Delete("s1", "p1", 10, 20, new Unknown()));
    }

    @Test
    void read_WhenTheFileIsEmpty_IsAnEmptyHistory() throws IOException {
        assertThat(read().operations()).isEmpty();
    }

    @Test
    void read_WhenTheFileIsMissing_ThrowsUnchecked() {
        Path missing = dir.resolve("missing.jsonl");

        assertThatThrownBy(() -> History.read(missing))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("missing.jsonl");
    }

    private static String line(String kind, String outcome, String extra) {
        String tail = extra == null ? "" : "," + extra;
        return json("{'session':'s1','kind':'" + kind + "','key':'p1','invokedAt':10,'completedAt':20,'outcome':'"
                + outcome + "'" + tail + "}");
    }

    private static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }

    private Operation only(String line) throws IOException {
        List<Operation> operations = read(line).operations();
        assertThat(operations).hasSize(1);
        return operations.get(0);
    }

    private History read(String... lines) throws IOException {
        Path file = dir.resolve("history.jsonl");
        Files.writeString(file, String.join("\n", lines), StandardCharsets.UTF_8);
        return History.read(file);
    }

    private void assertMalformed(int lineNumber, String... lines) throws IOException {
        Path file = dir.resolve("history.jsonl");
        Files.writeString(file, String.join("\n", lines), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> History.read(file))
                .isInstanceOf(HistoryFormatException.class)
                .hasMessageStartingWith("line " + lineNumber + ":");
    }
}
