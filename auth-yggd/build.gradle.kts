plugins {
    alias(libs.plugins.kotlin)
}

group = "icu.h2l.login"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Build as standalone plugin; api is provided at runtime by the main plugin
    compileOnly(project(":api"))
//    VC
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-proxy:3.4.0-SNAPSHOT") // From Elytrium Repo.
    compileOnly("io.netty:netty-all:4.2.5.Final")
// Exposed ORM
    compileOnly("org.jetbrains.exposed:exposed-core:0.58.0")
//    config
    compileOnly("org.spongepowered:configurate-hocon:4.2.0")
    compileOnly("org.spongepowered:configurate-extra-kotlin:4.2.0")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}