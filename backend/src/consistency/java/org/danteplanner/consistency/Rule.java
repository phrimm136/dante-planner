package org.danteplanner.consistency;

public interface Rule {

    Verdict judge(History history);
}
