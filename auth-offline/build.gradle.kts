plugins {
    alias(libs.plugins.kotlin)
}

group = "icu.h2l.login"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // The API is provided by the main plugin at runtime. Use compileOnly so
    // the submodule is built as a standalone Velocity plugin and doesn't
    // bundle the API classes (avoids duplication in the main shadow jar).
    compileOnly(project(":api"))
    // Direct reference to the main plugin module so we can call its API
    // (e.g. HyperZoneLoginMain.getInstance().registerModule(...)) without reflection.
    compileOnly(project(":openvc"))
//    VC
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    // Exposed ORM
    compileOnly("org.jetbrains.exposed:exposed-core:0.58.0")
//    config
    compileOnly("org.spongepowered:configurate-hocon:4.2.0")
    compileOnly("org.spongepowered:configurate-extra-kotlin:4.2.0")
//    limbo
    compileOnly("net.elytrium.limboapi:api:1.1.26")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}