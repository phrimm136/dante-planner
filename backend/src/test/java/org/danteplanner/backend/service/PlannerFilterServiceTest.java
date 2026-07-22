package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerFilterService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.entity.PlannerEntityFilter;
import org.danteplanner.backend.planner.entity.PlannerKeywordFilter;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlannerFilterService.
 * Tests JSON content extraction and filter row generation.
 */
@ExtendWith(MockitoExtension.class)
class PlannerFilterServiceTest {

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Captor
    private ArgumentCaptor<List<PlannerEntityFilter>> entriesCaptor;

    @Captor
    private ArgumentCaptor<List<PlannerKeywordFilter>> keywordsCaptor;

    private PlannerFilterService filterService;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        filterService = new PlannerFilterService(entityFilterRepository, keywordFilterRepository, objectMapper, eventPublisher);
        plannerId = UUID.randomUUID();
    }

    @Test
    void rebuildFilters_WhenFullContent_IndexesAllEntities() {
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
                  "selectedGiftIds": ["9001", "9002"],
                  "observationGiftIds": ["9003"],
                  "comprehensiveGiftIds": ["9004"],
                  "floorSelections": [
                    { "giftIds": ["9005"], "themePackId": "1001" },
                    { "giftIds": ["9006"], "themePackId": "1002" }
                  ]
                }
                """;

        filterService.rebuildFilters(plannerId, content, null);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository).saveAll(entriesCaptor.capture());

        List<PlannerEntityFilter> entries = entriesCaptor.getValue();

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
    void rebuildFilters_WhenDuplicateGiftIds_Deduplicates() {
        String content = """
                {
                  "selectedGiftIds": ["9001", "9002"],
                  "floorSelections": [
                    { "giftIds": ["9001", "9003"] }
                  ]
                }
                """;

        filterService.rebuildFilters(plannerId, content, null);

        verify(entityFilterRepository).saveAll(entriesCaptor.capture());
        List<PlannerEntityFilter> entries = entriesCaptor.getValue();

        long giftCount = entries.stream()
                .filter(e -> e.getEntityType() == ContentEntityType.EGO_GIFT)
                .count();
        assertEquals(3, giftCount, "9001 should appear only once despite being in both arrays");

        assertTrue(entries.stream().anyMatch(e -> e.getEntityId().equals(9001)));
        assertTrue(entries.stream().anyMatch(e -> e.getEntityId().equals(9002)));
        assertTrue(entries.stream().anyMatch(e -> e.getEntityId().equals(9003)));
    }

    @Test
    void rebuildFilters_WhenEmptyContent_DeletesAndSkipsSave() {
        filterService.rebuildFilters(plannerId, "{}", null);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).saveAll(any());
        verify(keywordFilterRepository, never()).saveAll(any());
    }

    @Test
    void rebuildFilters_WhenNullContent_DeletesAndSkipsSave() {
        filterService.rebuildFilters(plannerId, null, null);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).saveAll(any());
        verify(keywordFilterRepository, never()).saveAll(any());
    }

    @Test
    void rebuildFilters_WhenBlankContent_DeletesAndSkipsSave() {
        filterService.rebuildFilters(plannerId, "   ", null);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).saveAll(any());
        verify(keywordFilterRepository, never()).saveAll(any());
    }

    @Test
    void rebuildFilters_WhenMalformedJson_DeletesAndSkipsSave() {
        filterService.rebuildFilters(plannerId, "{not valid json!!!", null);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(entityFilterRepository, never()).saveAll(any());
        verify(keywordFilterRepository, never()).saveAll(any());
    }

    @Test
    void rebuildFilters_WhenSelectedKeywords_SavesKeywordRows() {
        filterService.rebuildFilters(plannerId, "{}", Set.of("Burn", "Sinking"));

        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).saveAll(keywordsCaptor.capture());

        List<PlannerKeywordFilter> keywords = keywordsCaptor.getValue();
        assertEquals(2, keywords.size());
        assertTrue(keywords.stream().anyMatch(k -> k.getKeyword().equals("Burn")));
        assertTrue(keywords.stream().anyMatch(k -> k.getKeyword().equals("Sinking")));
        assertTrue(keywords.stream().allMatch(k -> k.getPlannerId().equals(plannerId)));
    }

    @Test
    void clearFilters_WhenCalled_DeletesByPlannerId() {
        filterService.clearFilters(plannerId);

        verify(entityFilterRepository).deleteByPlannerId(plannerId);
        verify(keywordFilterRepository).deleteByPlannerId(plannerId);
        verifyNoMoreInteractions(entityFilterRepository, keywordFilterRepository);
    }

    @Test
    void rebuildFilters_WhenMissingEquipment_IndexesRemaining() {
        String content = """
                {
                  "selectedGiftIds": ["9001"],
                  "floorSelections": [
                    { "giftIds": ["9002"], "themePackId": "1001" }
                  ]
                }
                """;

        filterService.rebuildFilters(plannerId, content, null);

        verify(entityFilterRepository).saveAll(entriesCaptor.capture());
        List<PlannerEntityFilter> entries = entriesCaptor.getValue();

        assertTrue(entries.stream().noneMatch(e -> e.getEntityType() == ContentEntityType.IDENTITY));
        assertTrue(entries.stream().noneMatch(e -> e.getEntityType() == ContentEntityType.EGO));
        assertEquals(2, entries.stream().filter(e -> e.getEntityType() == ContentEntityType.EGO_GIFT).count());
        assertEquals(1, entries.stream().filter(e -> e.getEntityType() == ContentEntityType.THEME_PACK).count());
    }

    @Test
    void rebuildFilters_WhenNullThemePackId_SkipsThemePackEntry() {
        String content = """
                {
                  "floorSelections": [
                    { "giftIds": ["9001"], "themePackId": null },
                    { "giftIds": ["9002"], "themePackId": "1001" }
                  ]
                }
                """;

        filterService.rebuildFilters(plannerId, content, null);

        verify(entityFilterRepository).saveAll(entriesCaptor.capture());
        List<PlannerEntityFilter> entries = entriesCaptor.getValue();

        long themePacks = entries.stream()
                .filter(e -> e.getEntityType() == ContentEntityType.THEME_PACK)
                .count();
        assertEquals(1, themePacks, "Null themePackId should not produce an entry");

        assertTrue(entries.stream().anyMatch(
                e -> e.getEntityType() == ContentEntityType.THEME_PACK && e.getEntityId().equals(1001)));
    }
}
