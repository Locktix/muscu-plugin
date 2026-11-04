plugins {
    java
}

group = "fr.muscu"
version = "1.4.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi = "io.papermc.paper:paper-api:26.3.build.238-stable"

dependencies {
    compileOnly(paperApi)

    // Tests : JUnit + MockBukkit (faux serveur Paper 26.3)
    testImplementation(paperApi)
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.3:4.119.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("paper-plugin.yml") {
        expand("version" to version)
    }
}

tasks.jar {
    archiveFileName.set("Muscu-${project.version}.jar")
}
