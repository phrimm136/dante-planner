package org.danteplanner.backend.shared.config;

import java.util.List;

public interface EpithetProvider {

    List<String> getEpithets();

    int getWeight(String keyword);
}
