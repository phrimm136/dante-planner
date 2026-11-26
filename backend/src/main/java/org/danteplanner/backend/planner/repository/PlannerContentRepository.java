package org.danteplanner.backend.planner.repository;

import org.danteplanner.backend.planner.entity.PlannerContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface PlannerContentRepository extends JpaRepository<PlannerContent, UUID> {

    @Modifying
    @Query("DELETE FROM PlannerContent c WHERE c.plannerId IN :plannerIds")
    void deleteAllByPlannerIds(@Param("plannerIds") Collection<UUID> plannerIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PlannerContent c SET
                c.title = :#{#write.title()},
                c.status = :#{#write.status()},
                c.category = :#{#write.category()},
                c.selectedKeywords = :#{#write.keywords()},
                c.content = :#{#write.json()},
                c.contentSchemaVersion = :#{#write.schemaVersion()},
                c.gameContentVersion = :#{#write.gameContentVersion()},
                c.deviceId = :#{#write.device().columnValue()},
                c.syncVersion = :#{#write.syncVersion()},
                c.lastModifiedAt = :#{#write.savedAt()}
            WHERE c.plannerId = :plannerId AND c.syncVersion = :readVersion AND c.deletedAt IS NULL
            """)
    int replaceAtVersion(@Param("plannerId") UUID plannerId, @Param("readVersion") long readVersion,
            @Param("write") ContentWrite write);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerContent c SET c.content = :json, c.lastModifiedAt = :now "
            + "WHERE c.plannerId = :plannerId AND c.syncVersion = :readVersion AND c.deletedAt IS NULL")
    int migrateContent(@Param("plannerId") UUID plannerId, @Param("readVersion") long readVersion,
            @Param("json") String json, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PlannerContent c SET c.deletedAt = :now, c.lastModifiedAt = :now "
            + "WHERE c.plannerId = :plannerId AND c.deletedAt IS NULL")
    int markDeleted(@Param("plannerId") UUID plannerId, @Param("now") Instant now);

    default PlannerContent insert(PlannerContent row) {
        return save(row);
    }
}
