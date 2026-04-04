plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
}

dependencies {
    // Modules (auth-offline, auth-yggd, data-merge) are now separate Velocity plugins
    // and will register themselves with the main plugin at runtime. Do not include
    // them as project dependencies here so they are not bundled into the main shadow jar.
    implementation(project(":api"))
//    implementation(project(":vcinjector"))

// Exposed ORM
    implementation("org.jetbrains.exposed:exposed-core:0.58.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.58.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.58.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.58.0")
// Database drivers
    implementation("com.h2database:h2:2.1.214")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
//    VC
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-proxy:3.4.0-SNAPSHOT") // From Elytrium Repo.
//    limbo
    compileOnly("net.elytrium.limboapi:api:1.1.26")

//    mixin
    compileOnly("space.vectrix.ignite:ignite-api:1.1.0")
    compileOnly("net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7")
    compileOnly("io.github.llamalad7:mixinextras-common:0.5.0")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.5.0")

    implementation("org.spongepowered:configurate-extra-kotlin:4.2.0")
    compileOnly("org.spongepowered:configurate-hocon:4.2.0")
    compileOnly("io.netty:netty-all:4.2.5.Final")
    compileOnly("com.google.code.gson:gson:2.8.9")
    compileOnly("org.apache.logging.log4j:log4j-api:2.14.1")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.19.0")
    compileOnly("net.kyori:adventure-text-logger-slf4j:4.19.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.19.0")
    compileOnly("com.google.inject:guice:4.2.3")
    compileOnly("com.google.guava:guava:33.4.0-jre")
    compileOnly(libs.brigadier)

    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    shadowJar {
        archiveBaseName.set("HyperZoneLogin")
        archiveClassifier.set("")
        dependencies {
//            不加会导致mixin之后认不到
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))

            exclude(dependency("org.jetbrains:annotations"))
//            extra-kotlin
            include(dependency("org.spongepowered:configurate-extra-kotlin"))
//            Exposed ORM
            include(dependency("org.jetbrains.exposed:exposed-core"))
            include(dependency("org.jetbrains.exposed:exposed-dao"))
            include(dependency("org.jetbrains.exposed:exposed-jdbc"))
            include(dependency("org.jetbrains.exposed:exposed-java-time"))
//            Database drivers
            include(dependency("com.h2database:h2"))
            include(dependency("org.xerial:sqlite-jdbc"))
            include(dependency("com.mysql:mysql-connector-j"))
            include(dependency("org.mariadb.jdbc:mariadb-java-client"))
            include(dependency("com.zaxxer:HikariCP"))
//           api
            include(dependency(":api"))
//            模块 are now separate Velocity plugins and should not be bundled here
        }
    }
    build {
        dependsOn(shadowJar)
    }
}
