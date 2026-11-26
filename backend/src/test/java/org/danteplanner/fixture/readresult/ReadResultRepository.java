package org.danteplanner.fixture.readresult;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ReadResultRepository extends Repository<ReadResultRepository.Row, Long>, ReadResultRepositoryFragment {

    Optional<Row> findByName(String name);

    Row save(Row row);

    @Modifying
    @Query("UPDATE Row r SET r.name = r.name WHERE r.name = :name")
    int findAndTouch(String name);

    @Query("SELECT r FROM Row r WHERE r.name = :name")
    Optional<Row> selectNamed(String name);

    @Query(value = "SELECT * FROM rows WHERE name = :name FOR UPDATE", nativeQuery = true)
    Optional<Row> findLockedByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Row> findWithLockByName(String name);

    long deleteByName(String name);

    default Optional<Row> findOrSave(String name) {
        return Optional.of(save(new Row(name)));
    }

    record Row(String name) {
    }
}
