package org.danteplanner.consistency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryTest {

    private static final String WRITE_UNAVAILABLE = "'status':503,'code':'WRITE_TEMPORARILY_UNAVAILABLE'";
    private static final String READ_UNAVAILABLE = "'status':503,'code':'READ_TEMPORARILY_UNAVAILABLE'";

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
        assertThat(only(line("read", "fail", "'status':429")))
                .isEqualTo(new Read("s1", "p1", 10, 20, new ReadFailed(429)));
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
    @ValueSource(strings = {"publish", "unpublish"})
    void visibilityOk_WhenStatusIs200_IsChanged(String kind) throws IOException {
        assertThat(only(line(kind, "ok", "'status':200"))).isEqualTo(visibility(kind, new Changed()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "unpublish"})
    void visibilityOk_WhenStatusIsNot200_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "ok", "'status':204"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "unpublish"})
    void visibilityOk_WhenStatusIsMissing_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "ok", null));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 499})
    void publishFail_WhenStatusIs4xx_IsRejected(int status) throws IOException {
        assertThat(only(line("publish", "fail", "'status':" + status)))
                .isEqualTo(new Publish("s1", "p1", 10, 20, new Rejected(status)));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 499})
    void unpublishFail_WhenStatusIs4xx_IsRejected(int status) throws IOException {
        assertThat(only(line("unpublish", "fail", "'status':" + status)))
                .isEqualTo(new Unpublish("s1", "p1", 10, 20, new Rejected(status)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "unpublish"})
    void visibilityFail_WhenStatusIsNot4xx_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "fail", "'status':399"));
        assertMalformed(1, line(kind, "fail", "'status':500"));
        assertMalformed(1, line(kind, "fail", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "unpublish"})
    void visibilityUnknown_WhenNoResponseArrived_IsUnknown(String kind) throws IOException {
        assertThat(only(line(kind, "unknown", null))).isEqualTo(visibility(kind, new Unknown()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "unpublish"})
    void visibilityUnknown_WhenStatusIs5xx_IsUnknown(String kind) throws IOException {
        assertThat(only(line(kind, "unknown", "'status':500"))).isEqualTo(visibility(kind, new Unknown()));
        assertThat(only(line(kind, "unknown", "'status':599"))).isEqualTo(visibility(kind, new Unknown()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"publish", "unpublish"})
    void visibilityUnknown_WhenStatusIsNot5xx_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "unknown", "'status':200"));
        assertMalformed(1, line(kind, "unknown", "'status':499"));
        assertMalformed(1, line(kind, "unknown", "'status':600"));
    }

    @Test
    void publicReadOk_WhenStatusIs200WithAVersion_IsFound() throws IOException {
        assertThat(only(line("public-read", "ok", "'status':200,'syncVersion':7")))
                .isEqualTo(new PublicRead("s1", "p1", 10, 20, new Found(7)));
    }

    @Test
    void publicReadOk_WhenStatusIs200WithoutAVersion_IsMalformed() throws IOException {
        assertMalformed(1, line("public-read", "ok", "'status':200"));
    }

    @Test
    void publicReadOk_WhenStatusIs404_IsNotFound() throws IOException {
        assertThat(only(line("public-read", "ok", "'status':404")))
                .isEqualTo(new PublicRead("s1", "p1", 10, 20, new NotFound()));
    }

    @Test
    void publicReadOk_WhenStatusIsNeither200Nor404_IsMalformed() throws IOException {
        assertMalformed(1, line("public-read", "ok", "'status':500"));
    }

    @Test
    void publicReadFail_WhenStatusIsAnyOther_IsReadFailed() throws IOException {
        assertThat(only(line("public-read", "fail", "'status':429")))
                .isEqualTo(new PublicRead("s1", "p1", 10, 20, new ReadFailed(429)));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404})
    void publicReadFail_WhenStatusIs200Or404_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("public-read", "fail", "'status':" + status));
    }

    @Test
    void publicReadFail_WhenStatusIsMissing_IsMalformed() throws IOException {
        assertMalformed(1, line("public-read", "fail", null));
    }

    @Test
    void publicReadUnknown_WhenNoResponseArrived_IsReadFailedWithStatusZero() throws IOException {
        assertThat(only(line("public-read", "unknown", null)))
                .isEqualTo(new PublicRead("s1", "p1", 10, 20, new ReadFailed(0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 599})
    void publicReadUnknown_WhenStatusIs5xx_IsReadFailedWithStatusZero(int status) throws IOException {
        assertThat(only(line("public-read", "unknown", "'status':" + status)))
                .isEqualTo(new PublicRead("s1", "p1", 10, 20, new ReadFailed(0)));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 499, 600})
    void publicReadUnknown_WhenStatusIsNot5xx_IsMalformed(int status) throws IOException {
        assertMalformed(1, line("public-read", "unknown", "'status':" + status + ",'syncVersion':1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "publish", "unpublish"})
    void writeFail_WhenStatusIs503WithTheWriteUnavailableCode_IsRejected503(String kind) throws IOException {
        Operation expected = switch (kind) {
            case "save" -> new Save("s1", "p1", 10, 20, new Rejected(503));
            case "delete" -> new Delete("s1", "p1", 10, 20, new Rejected(503));
            default -> visibility(kind, new Rejected(503));
        };

        assertThat(only(line(kind, "fail", WRITE_UNAVAILABLE))).isEqualTo(expected);
    }

    @Test
    void readFail_WhenStatusIs503WithTheReadUnavailableCode_IsReadFailed503() throws IOException {
        assertThat(only(line("read", "fail", READ_UNAVAILABLE)))
                .isEqualTo(new Read("s1", "p1", 10, 20, new ReadFailed(503)));
        assertThat(only(line("public-read", "fail", READ_UNAVAILABLE)))
                .isEqualTo(new PublicRead("s1", "p1", 10, 20, new ReadFailed(503)));
    }

    @Test
    void listFail_WhenStatusIs503WithTheReadUnavailableCode_IsSkipped() throws IOException {
        assertThat(read(line("list", "fail", READ_UNAVAILABLE)).operations()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "publish", "unpublish"})
    void writeFail_WhenStatusIs503WithTheReadUnavailableCode_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "fail", READ_UNAVAILABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "public-read", "list"})
    void readFail_WhenStatusIs503WithTheWriteUnavailableCode_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "fail", WRITE_UNAVAILABLE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "read", "publish", "unpublish", "public-read", "list"})
    void fail_WhenStatusIs503WithoutAnUnavailableCode_IsMalformedNamingTheLine(String kind)
            throws IOException {
        assertMalformed(2, line(kind, "unknown", null), line(kind, "fail", "'status':503"));
        assertMalformed(2, line(kind, "unknown", null), line(kind, "fail", "'status':503,'code':'DEADLOCK'"));
        assertMalformed(2, line(kind, "unknown", null), line(kind, "fail", "'status':503,'code':5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "read", "publish", "unpublish", "public-read"})
    void unknown_WhenStatusIsNull_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "unknown", "'status':null"));
    }

    @Test
    void listOk_WhenKeysArePresent_IsAListRead() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'list','list':'owner','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200,'page':0,'size':20,'totalElements':2,'keys':['p1','p2']}")))
                .isEqualTo(new ListRead("s1", ListRead.Listing.OWNER, 10, 20, 0, 20, 2, List.of("p1", "p2")));
    }

    @Test
    void listOk_WhenTheListIsTheCatalog_IsACatalogRead() throws IOException {
        assertThat(only(json("{'session':'anon','kind':'list','list':'catalog','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200,'page':1,'size':5,'totalElements':9,'keys':['p1']}")))
                .isEqualTo(new ListRead("anon", ListRead.Listing.CATALOG, 10, 20, 1, 5, 9, List.of("p1")));
    }

    @Test
    void listOk_WhenTheListIsUnnamed_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'list','list':'inbox','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200,'page':0,'size':20,'totalElements':0,'keys':[]}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "totalElements"})
    void listOk_WhenAPageFactIsMissing_IsMalformed(String fact) throws IOException {
        String facts = Stream.of("'page':0", "'size':20", "'totalElements':0")
                .filter(entry -> !entry.startsWith("'" + fact + "'"))
                .collect(Collectors.joining(","));
        assertMalformed(1, json("{'session':'s1','kind':'list','list':'owner','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200," + facts + ",'keys':[]}"));
    }

    @Test
    void threadReadOk_WhenStatusIs200_IsShown() throws IOException {
        assertThat(only(line("thread-read", "ok", "'audience':'owner','status':200,'comments':{},'masked':[]")))
                .isEqualTo(new ThreadRead("s1", "p1", ThreadRead.Audience.OWNER,
                        10, 20, new Shown(Map.of(), Set.of())));
    }

    @Test
    void threadReadOk_WhenItShowsARevisionAndAMaskedComment_CarriesBoth() throws IOException {
        assertThat(only(line("thread-read", "ok", "'audience':'owner','status':200,'comments':{'c1':3},'masked':['c2']")))
                .isEqualTo(new ThreadRead("s1", "p1", ThreadRead.Audience.OWNER,
                        10, 20, new Shown(Map.of("c1", 3L), Set.of("c2"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'masked':[]", "'comments':{}", "'comments':{'c1':'3'},'masked':[]",
            "'comments':{},'masked':[3]"})
    void threadReadOk_WhenCommentsOrMaskedAreMissingOrMistyped_IsMalformed(String facts) throws IOException {
        assertMalformed(1, line("thread-read", "ok", "'audience':'owner','status':200," + facts));
    }

    @Test
    void threadRead_WhenTheAudienceIsPublic_IsAPublicRead() throws IOException {
        assertThat(only(line("thread-read", "ok", "'audience':'public','status':404")))
                .isEqualTo(new ThreadRead("s1", "p1", ThreadRead.Audience.PUBLIC, 10, 20, new NotFound()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'status':404", "'audience':'author','status':404"})
    void threadRead_WhenTheAudienceIsMissingOrUnknown_IsMalformed(String facts) throws IOException {
        assertMalformed(1, line("thread-read", "ok", facts));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201})
    void commentOk_WhenStatusIs2xxWithAnId_IsChanged(int status) throws IOException {
        assertThat(only(line("comment", "ok", "'status':" + status + ",'comment':'c1','revision':2")))
                .isEqualTo(new CommentWrite("s1", "p1", "c1", 2, 10, 20, new Changed()));
    }

    @Test
    void commentFail_WhenTheCreateWasRefused_IsRejectedWithoutAnId() throws IOException {
        assertThat(only(line("comment", "fail", "'status':403,'comment':'','revision':1")))
                .isEqualTo(new CommentWrite("s1", "p1", "", 1, 10, 20, new Rejected(403)));
    }

    @Test
    void commentUnknown_WhenNoResponseArrived_IsUnknown() throws IOException {
        assertThat(only(line("comment", "unknown", "'comment':'','revision':1")))
                .isEqualTo(new CommentWrite("s1", "p1", "", 1, 10, 20, new Unknown()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'comment':'','revision':1", "'comment':'c1','revision':0", "'revision':1",
            "'comment':'c1'"})
    void commentOk_WhenTheIdOrRevisionIsMissingOrInvalid_IsMalformed(String facts) throws IOException {
        assertMalformed(1, line("comment", "ok", "'status':200," + facts));
    }

    @Test
    void settingsSaveOk_WhenStatusIs200_IsChangedWithItsValue() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'settings-save','value':true,'invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200}")))
                .isEqualTo(new SettingsSave("s1", true, 10, 20, new Changed()));
    }

    @Test
    void settingsSave_WhenTheValueIsMissing_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'settings-save','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200}"));
    }

    @Test
    void settingsReadOk_WhenStatusIs200_IsSeenWithItsValue() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'settings-read','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200,'value':false}")))
                .isEqualTo(new SettingsRead("s1", 10, 20, new Seen(false)));
    }

    @Test
    void settingsReadFail_WhenStatusIs503WithTheReadUnavailableCode_IsReadFailed503() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'settings-read','invokedAt':10,'completedAt':20,"
                + "'outcome':'fail'," + READ_UNAVAILABLE + "}")))
                .isEqualTo(new SettingsRead("s1", 10, 20, new ReadFailed(503)));
    }

    @Test
    void settingsReadOk_WhenTheValueIsMissing_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'settings-read','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200}"));
    }

    @Test
    void threadReadOk_WhenStatusIs404_IsNotFound() throws IOException {
        assertThat(only(line("thread-read", "ok", "'audience':'owner','status':404")))
                .isEqualTo(new ThreadRead("s1", "p1", ThreadRead.Audience.OWNER, 10, 20, new NotFound()));
    }

    @Test
    void threadReadFail_WhenStatusIs503WithTheReadUnavailableCode_IsReadFailed503() throws IOException {
        assertThat(only(line("thread-read", "fail", "'audience':'owner'," + READ_UNAVAILABLE)))
                .isEqualTo(new ThreadRead("s1", "p1", ThreadRead.Audience.OWNER, 10, 20, new ReadFailed(503)));
    }

    @Test
    void deactivateOk_WhenStatusIs200_IsChanged() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'deactivate','invokedAt':10,'completedAt':20,'outcome':'ok',"
                + "'status':200}")))
                .isEqualTo(new Deactivate("s1", 10, 20, new Changed()));
    }

    @Test
    void reactivateOk_WhenNoStatusIsRecorded_IsChanged() throws IOException {
        assertThat(only(json("{'session':'s1','kind':'reactivate','invokedAt':10,'completedAt':20,'outcome':'ok'}")))
                .isEqualTo(new Reactivate("s1", 10, 20, new Changed()));
    }

    @Test
    void reactivate_WhenAStatusIsRecorded_IsMalformed() throws IOException {
        assertMalformed(1, json("{'session':'s1','kind':'reactivate','invokedAt':10,'completedAt':20,"
                + "'outcome':'ok','status':200}"));
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
    @ValueSource(strings = {"save", "delete", "read", "publish", "unpublish", "public-read"})
    void outcome_WhenNotOkFailOrUnknown_IsMalformed(String kind) throws IOException {
        assertMalformed(1, line(kind, "maybe", "'status':200,'syncVersion':1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "read", "publish", "unpublish", "public-read"})
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

    private static Operation visibility(String kind, VisibilityResult result) {
        return kind.equals("publish")
                ? new Publish("s1", "p1", 10, 20, result)
                : new Unpublish("s1", "p1", 10, 20, result);
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
