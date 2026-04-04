import org.gradle.jvm.tasks.Jar
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin)
    id("maven-publish")
}


group = "icu.h2l.login"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
//    VC
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-proxy:3.4.0-SNAPSHOT")
// Limbo is optional; implementations may provide a bridge adapter. Do not
// require the limbo API here at compile time.
    // Netty is needed by API types (connection/player extensions) but only at compile time
    compileOnly("io.netty:netty-all:4.2.5.Final")
// Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.58.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Publish configuration to allow `api` to be published to the local Maven repository
// Use `./gradlew :api:publishToMavenLocal` to publish.
publishing {
    publications {
        create<org.gradle.api.publish.maven.MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = "api"
            version = project.version.toString()
            from(components["java"])

            // include sources JAR for better IDE support when consumed from mavenLocal
            val sourcesJar = tasks.register("sourcesJar", org.gradle.jvm.tasks.Jar::class) {
                archiveClassifier.set("sources")
                from(sourceSets.main.get().allSource)
            }
            artifact(sourcesJar)
        }
    }

    repositories {
        mavenLocal()
    }
}
