/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.7/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    java
    application
}

java {
    // Use Java 21.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation("io.vertx:vertx-core:4.4.0")
    implementation("io.vertx:vertx-web:4.4.0")
    implementation("io.vertx:vertx-web-client:4.4.0")
    // Add Micrometer and Prometheus dependencies
    implementation("io.micrometer:micrometer-core:1.12.3")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.3")
    //implementation("io.vertx:vertx-micrometer-metrics:4.4.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // Define the main class for the application.
    mainClass = "Main"
}

tasks.jar {
    archiveFileName.set("app.jar")
    manifest {
        attributes["Main-Class"] = application.mainClass.get() // or specify your main class directly
    }

    // Include all runtime dependencies into the JAR file
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) })

    // Optionally, include your compiled classes (if not already included by default)
    from(sourceSets.main.get().output)

    // Ensure the JAR is built as a single fat JAR
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}