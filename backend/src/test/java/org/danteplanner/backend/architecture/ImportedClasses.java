package org.danteplanner.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

public final class ImportedClasses {

    private static final String ROOT = "org.danteplanner.backend";

    private ImportedClasses() {
    }

    public static JavaClasses production() {
        return Production.CLASSES;
    }

    public static JavaClasses everything() {
        return Everything.CLASSES;
    }

    private static final class Production {

        private static final JavaClasses CLASSES = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    private static final class Everything {

        private static final JavaClasses CLASSES = new ClassFileImporter().importPackages(ROOT);
    }
}
