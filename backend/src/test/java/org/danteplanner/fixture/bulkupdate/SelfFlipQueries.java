package org.danteplanner.fixture.bulkupdate;

import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

public interface SelfFlipQueries {

    @Query("UPDATE Row r SET r.on = NOT r.on WHERE r.id = :id")
    int not();

    @Query(value = "update rows set `on`=not(`on`)", nativeQuery = true)
    int notInParentheses();

    @Query("UPDATE Row r SET r.other = :other, r.on = NOT on")
    int notOfTheUnaliasedName();

    @Query(value = "UPDATE rows SET on_flag = !on_flag", nativeQuery = true)
    int bang();

    @Query("UPDATE Row r SET r.on = 1 - r.on WHERE r.id = :id")
    int oneMinus();

    @Query(value = """
            update rows
               set on_flag
                   = on_flag ^ 1
             where id = :id
            """, nativeQuery = true)
    int xor();

    @Query("UPDATE Row r SET r.on = CASE WHEN r.on = TRUE THEN FALSE ELSE TRUE END")
    int caseWhen();

    @Query("UPDATE Row r SET r.on = CASE r.on WHEN TRUE THEN FALSE ELSE TRUE END")
    int caseOf();

    @Query(value = "UPDATE rows SET on_flag = IF(on_flag, 0, 1)", nativeQuery = true)
    int ifOf();

    @Query(value = "INSERT INTO rows (id, on_flag) VALUES (:id, TRUE) "
            + "ON DUPLICATE KEY UPDATE on_flag = NOT on_flag", nativeQuery = true)
    int onDuplicateKey();

    @Query("UPDATE Row r SET r.on = CASE WHEN NOT r.on THEN TRUE ELSE FALSE END")
    int caseWhenNot();

    @Query("UPDATE Row r SET r.on = CASE r.on WHEN TRUE THEN FALSE WHEN FALSE THEN TRUE END")
    int caseOfBoth();

    @Query(value = "UPDATE rows SET on_flag = IF(on_flag IS TRUE, FALSE, TRUE)", nativeQuery = true)
    int ifIs();

    @Query(value = "UPDATE rows SET on_flag = (on_flag = FALSE)", nativeQuery = true)
    int equalsFalse();

    @Query("UPDATE Row r SET r.on = r.on <> TRUE")
    int notEqualsTrue();

    @Query(value = "UPDATE rows SET on_flag = on_flag != 1", nativeQuery = true)
    int bangEqualsOne();

    @Query(value = "UPDATE rows SET on_flag = on_flag XOR TRUE", nativeQuery = true)
    int xorWord();

    @Query(value = "UPDATE rows SET on_flag = ABS(on_flag - 1)", nativeQuery = true)
    int abs();

    @Query(value = "UPDATE rows SET total = (SELECT COUNT(*) FROM parts p WHERE p.row_id = :id), "
            + "on_flag = NOT on_flag WHERE id = :id", nativeQuery = true)
    int afterASubquery();

    @NativeQuery("UPDATE rows SET on_flag = NOT on_flag")
    int metaAnnotated();

    @Query("UPDATE Row r SET r.n = CASE WHEN r.n > 0 THEN r.n - 1 ELSE 0 END")
    int allowedClamp();

    @Query(value = "UPDATE rows SET read_at = IF(read_at IS NULL, :now, read_at)", nativeQuery = true)
    int allowedFirstStamp();

    @Query("UPDATE Row r SET r.total = (SELECT COUNT(p) FROM Part p WHERE p.on = NOT p.on), r.on = TRUE")
    int allowedSubquery();

    @Query("UPDATE Row r SET r.note = 'x, on = NOT on' WHERE r.id = :id")
    int allowedText();

    @Query(value = "INSERT INTO rows (id, on_flag) VALUES (:id, TRUE) ON DUPLICATE KEY UPDATE on_flag = TRUE",
            nativeQuery = true)
    int allowedSet();

    @Query("UPDATE Row r SET r.count = r.count + 1, r.on = :value WHERE r.on = NOT r.off")
    int allowedCounter();

    @Query("UPDATE Row r SET r.on = NOT r.off, r.off = CASE WHEN r.on = TRUE THEN FALSE ELSE TRUE END")
    int allowedOtherColumn();

    @Query("SELECT r FROM Row r WHERE r.on = NOT r.on")
    Object allowedRead();
}
