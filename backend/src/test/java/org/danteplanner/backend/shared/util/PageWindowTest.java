package org.danteplanner.backend.shared.util;

import org.springframework.beans.TypeMismatchException;
import org.springframework.data.domain.PageRequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageWindowTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, nullValues = "absent", value = {
            "absent|absent|0|20",
            "''|''|0|20",
            "1,2|30,x|1|30",
            " 3 | 40 |3|40",
            "+4|+5|4|5",
            "0|1|0|1",
            "0|100|0|100",
            "0|101|0|100"})
    void pageable_WhenTheTextIsReadable_IsTheWindowItNames(String page, String size, int readPage, int readSize) {
        assertThat(PageWindow.pageable(page, size)).isEqualTo(PageRequest.of(readPage, readSize));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', ignoreLeadingAndTrailingWhitespace = false, nullValues = "absent", value = {
            "x|20|page|x",
            "0x10|20|page|0x10",
            "' '|20|page|' '",
            ",1|20|page|,1",
            "2147483648|20|page|2147483648",
            "-1|20|page|-1",
            "0|x|size|x",
            "absent|0x10|size|0x10",
            "0|0|size|0",
            "0|-1|size|-1",
            "x|y|page|x",
            "-1|y|size|y",
            "-1|0|page|-1"})
    void pageable_WhenTheTextIsUnreadableOrOutOfRange_NamesTheParameterAndItsValue(
            String page, String size, String parameter, String value) {
        assertThatThrownBy(() -> PageWindow.pageable(page, size))
                .isInstanceOfSatisfying(TypeMismatchException.class, mismatch -> {
                    assertThat(mismatch.getPropertyName()).isEqualTo(parameter);
                    assertThat(String.valueOf(mismatch.getValue())).isEqualTo(value);
                });
    }
}
