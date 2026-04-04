plugins {
    alias(libs.plugins.kotlin)
}

group = "icu.h2l.login"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Build as a standalone Velocity plugin; reference API at compile time only
    compileOnly(project(":api"))
    // The auth modules are separate plugins; keep compileOnly if you reference them
    compileOnly(project(":auth-yggd"))
    compileOnly(project(":auth-offline"))

    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    compileOnly("org.jetbrains.exposed:exposed-core:0.58.0")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:0.58.0")

    compileOnly("org.spongepowered:configurate-hocon:4.2.0")
    compileOnly("org.spongepowered:configurate-extra-kotlin:4.2.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}