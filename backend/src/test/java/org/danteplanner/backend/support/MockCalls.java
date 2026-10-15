package org.danteplanner.backend.support;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

public final class MockCalls {

    private MockCalls() {
    }

    public static List<String> inOrder(Object... mocks) {
        return Arrays.stream(mocks)
                .flatMap(mock -> Mockito.mockingDetails(mock).getInvocations().stream())
                .sorted(Comparator.comparingInt(Invocation::getSequenceNumber))
                .map(invocation -> invocation.getMethod().getName())
                .toList();
    }
}
