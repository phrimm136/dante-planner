package org.danteplanner.backend.shared.sanitize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.moderation.dto.BanRequest;
import org.danteplanner.backend.planner.dto.ImportPlannersRequest;
import org.danteplanner.backend.planner.dto.LegacyPublishRequest;
import org.danteplanner.backend.planner.dto.UpdatePlannerRequest;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real request DTOs, read the way the controllers read them: the payload a hostile caller would
 * send arrives sanitized, on every route that persists a title or a planner document.
 */
class RequestBoundarySanitizationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String HOSTILE_TITLE = "<script>alert('xss')</script><b>My plan</b>";
    private static final String SAFE_TITLE = "My plan";

    private static final String HOSTILE_DOCUMENT = """
        {"sectionNotes":{"intro":{"content":{"type":"doc","content":[{"type":"text",\
        "text":"click","marks":[{"type":"link","attrs":{"href":"javascript:alert(1)"}}]}]}}}}""";

    private static final String LINK_PATH =
            "/sectionNotes/intro/content/content/0/marks/0/attrs/href";

    @Test
    @DisplayName("an upsert arrives with its title stripped and its document neutralized")
    void upsertRequest_WhenPayloadIsHostile_ArrivesSanitized() throws Exception {
        UpsertPlannerRequest request =
                MAPPER.treeToValue(hostileUpsertPayload(), UpsertPlannerRequest.class);

        assertThat(request.title()).isEqualTo(SAFE_TITLE);
        assertThat(linkIn(request.content())).isEqualTo("#");
    }

    @Test
    @DisplayName("a partial update arrives with its title stripped and its document neutralized")
    void updateRequest_WhenPayloadIsHostile_ArrivesSanitized() throws Exception {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("title", HOSTILE_TITLE);
        payload.put("content", HOSTILE_DOCUMENT);
        payload.put("syncVersion", 1);

        UpdatePlannerRequest request = MAPPER.treeToValue(payload, UpdatePlannerRequest.class);

        assertThat(request.title()).isEqualTo(SAFE_TITLE);
        assertThat(linkIn(request.content())).isEqualTo("#");
    }

    @Test
    @DisplayName("the superseded publish route sanitizes the document it carries")
    void legacyPublishRequest_WhenPayloadIsHostile_ArrivesSanitized() throws Exception {
        ObjectNode payload = hostileUpsertPayload();
        payload.put("published", true);

        LegacyPublishRequest request = MAPPER.treeToValue(payload, LegacyPublishRequest.class);

        assertThat(request.title()).isEqualTo(SAFE_TITLE);
        assertThat(linkIn(request.content())).isEqualTo("#");
        assertThat(linkIn(request.toUpsertRequest().content())).isEqualTo("#");
    }

    @Test
    @DisplayName("a bulk import sanitizes every planner it carries")
    void importRequest_WhenAnElementIsHostile_ArrivesSanitized() throws Exception {
        ArrayNode planners = MAPPER.createArrayNode();
        planners.add(hostileUpsertPayload());
        ObjectNode payload = MAPPER.createObjectNode();
        payload.set("planners", planners);

        ImportPlannersRequest request =
                MAPPER.treeToValue(payload, ImportPlannersRequest.class);

        UpsertPlannerRequest imported = request.planners().getFirst();
        assertThat(imported.title()).isEqualTo(SAFE_TITLE);
        assertThat(linkIn(imported.content())).isEqualTo("#");
    }

    @Test
    @DisplayName("a moderation reason arrives with its markup stripped")
    void banRequest_WhenReasonCarriesHtml_ArrivesAsText() throws Exception {
        BanRequest request = MAPPER.readValue(
                "{\"reason\":\"<img src=x onerror=alert(1)><b>spam ring</b>\"}", BanRequest.class);

        assertThat(request.reason()).isEqualTo("spam ring");
    }

    @Test
    @DisplayName("an identifier opted out of sanitization arrives byte for byte")
    void optedOutComponent_WhenUpsertIsRead_ArrivesUnchanged() throws Exception {
        UpsertPlannerRequest request =
                MAPPER.treeToValue(hostileUpsertPayload(), UpsertPlannerRequest.class);

        assertThat(request.id()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(request.category()).isEqualTo("md6");
    }

    private static ObjectNode hostileUpsertPayload() {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("id", "11111111-1111-1111-1111-111111111111");
        payload.put("category", "md6");
        payload.put("title", HOSTILE_TITLE);
        payload.put("content", HOSTILE_DOCUMENT);
        payload.put("contentVersion", 6);
        payload.put("plannerType", "MIRROR_DUNGEON");
        return payload;
    }

    private static String linkIn(String document) throws Exception {
        JsonNode parsed = MAPPER.readTree(document);
        return parsed.at(LINK_PATH).asText();
    }
}
