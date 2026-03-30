package org.danteplanner.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.entity.ContentEntityType;
import org.danteplanner.backend.entity.PlannerContentIndex;
import org.danteplanner.backend.repository.PlannerContentIndexRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlannerIndexService.
 * Tests JSON content extraction and index entry generation.
 */
@ExtendWith(MockitoExtension.class)
class PlannerIndexServiceTest {

    @Mock
    private PlannerContentIndexRepository contentIndexRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Captor
    private ArgumentCaptor<List<PlannerContentIndex>> entriesCaptor;

    private PlannerIndexService indexService;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        indexService = new PlannerIndexService(contentIndexRepository, objectMapper);
        plannerId = UUID.randomUUID();
    }

    @Test
    void reindexWithFullContent() {
        String content = """
                {
                  "equipment": {
                    "sinner1": {
                      "identity": { "id": "10101" },
                      "egos": {
                        "ZAYIN": { "id": "20101" },
                        "TETH": { "id": "20102" }
                      }
                    },
                    "sinner2": {
                      "identity": { "id": "10201" },
                      "egos": {
                        "HE": { "id": "20201" }
                      }
                    }
                  },
                  "selectedGiftIds": ["g001", "g002"],
                  "observationGiftIds": ["g003"],
                  "comprehensiveGiftIds": ["g004"],
                  "floorSelections": [
                    { "giftIds": ["g005"], "themePackId": "tp001" },
                    { "giftIds": ["g006"], "themePackId": "tp002" }
                  ]
                }
                """;

        indexService.reindex(plannerId, content);

        verify(contentIndexRepository).deleteByPlannerId(plannerId);
        verify(contentIndexRepository).saveAll(entriesCaptor.capture());

        List<PlannerContentIndex> entries = entriesCaptor.getValue();

        long identities = entries.stream().filter(e -> e.getEntityType() == ContentEntityType.IDENTITY).count();
        long egos = entries.stream().filter(e -> e.getEntityType() == ContentEntityType.EGO).count();
        long gifts = entries.stream().filter(e -> e.getEntityType() == ContentEntityType.EGO_GIFT).count();
        long themePacks = entries.stream().filter(e -> e.getEntityType() == ContentEntityType.THEME_PACK).count();

        assertEquals(2, identities);
        assertEquals(3, egos);
        assertEquals(6, gifts);
        assertEquals(2, themePacks);
        assertEquals(13, entries.size());
    }

    @Test
    void reindexDeduplicates() {
        String content = """
                {
                  "selectedGiftIds": ["g001", "g002"],
                  "floorSelections": [
                    { "giftIds": ["g001", "g003"] }
                  ]
                }
                """;

        indexService.reindex(plannerId, content);

        verify(contentIndexRepository).saveAll(entriesCaptor.capture());
        List<PlannerContentIndex> entries = entriesCaptor.getValue();

        long giftCount = entries.stream()
                .filter(e -> e.getEntityType() == ContentEntityType.EGO_GIFT)
                .count();
        assertEquals(3, giftCount, "g001 should appear only once despite being in both arrays");

        assertTrue(entries.stream().anyMatch(e -> e.getEntityId().equals("g001")));
        assertTrue(entries.stream().anyMatch(e -> e.getEntityId().equals("g002")));
        assertTrue(entries.stream().anyMatch(e -> e.getEntityId().equals("g003")));
    }

    @Test
    void reindexWithEmptyContent() {
        indexService.reindex(plannerId, "{}");

        verify(contentIndexRepository).deleteByPlannerId(plannerId);
        verify(contentIndexRepository, never()).saveAll(any());
    }

    @Test
    void reindexWithNullContent() {
        indexService.reindex(plannerId, null);

        verify(contentIndexRepository).deleteByPlannerId(plannerId);
        verify(contentIndexRepository, never()).saveAll(any());
    }

    @Test
    void reindexWithBlankContent() {
        indexService.reindex(plannerId, "   ");

        verify(contentIndexRepository).deleteByPlannerId(plannerId);
        verify(contentIndexRepository, never()).saveAll(any());
    }

    @Test
    void reindexWithMalformedJson() {
        indexService.reindex(plannerId, "{not valid json!!!");

        verify(contentIndexRepository).deleteByPlannerId(plannerId);
        verify(contentIndexRepository, never()).saveAll(any());
    }

    @Test
    void deleteIndex() {
        indexService.deleteIndex(plannerId);

        verify(contentIndexRepository).deleteByPlannerId(plannerId);
        verifyNoMoreInteractions(contentIndexRepository);
    }

    @Test
    void reindexHandlesMissingEquipment() {
        String content = """
                {
                  "selectedGiftIds": ["g001"],
                  "floorSelections": [
                    { "giftIds": ["g002"], "themePackId": "tp001" }
                  ]
                }
                """;

        indexService.reindex(plannerId, content);

        verify(contentIndexRepository).saveAll(entriesCaptor.capture());
        List<PlannerContentIndex> entries = entriesCaptor.getValue();

        assertTrue(entries.stream().noneMatch(e -> e.getEntityType() == ContentEntityType.IDENTITY));
        assertTrue(entries.stream().noneMatch(e -> e.getEntityType() == ContentEntityType.EGO));
        assertEquals(2, entries.stream().filter(e -> e.getEntityType() == ContentEntityType.EGO_GIFT).count());
        assertEquals(1, entries.stream().filter(e -> e.getEntityType() == ContentEntityType.THEME_PACK).count());
    }

    @Test
    void reindexHandlesNullThemePackId() {
        String content = """
                {
                  "floorSelections": [
                    { "giftIds": ["g001"], "themePackId": null },
                    { "giftIds": ["g002"], "themePackId": "tp001" }
                  ]
                }
                """;

        indexService.reindex(plannerId, content);

        verify(contentIndexRepository).saveAll(entriesCaptor.capture());
        List<PlannerContentIndex> entries = entriesCaptor.getValue();

        long themePacks = entries.stream()
                .filter(e -> e.getEntityType() == ContentEntityType.THEME_PACK)
                .count();
        assertEquals(1, themePacks, "Null themePackId should not produce an entry");

        assertTrue(entries.stream().anyMatch(
                e -> e.getEntityType() == ContentEntityType.THEME_PACK && e.getEntityId().equals("tp001")));
    }
}
