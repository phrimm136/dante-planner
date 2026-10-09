package org.danteplanner.consistency;

public sealed interface Operation permits Save, Delete, Read, ListRead {
}
