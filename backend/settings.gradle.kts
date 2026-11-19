pluginManagement {
    // A plugin's own dependencies (spring-core, commons-compress) live on Maven Central, which
    // the portal only redirects to; when the redirect fails, plugin resolution fails with it.
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "backend"
