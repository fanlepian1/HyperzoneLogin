/*
 * This file is part of HyperZoneLogin, licensed under the GNU Affero General Public License v3.0 or later.
 *
 * Copyright (C) ksqeib (庆灵) <ksqeib@qq.com>
 * Copyright (C) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

import icu.h2l.gradle.needPackageCompileOnly
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin)
    id("icu.h2l.runtime-dependencies")
}

dependencies {
    // Modules (auth-offline, auth-yggd, data-merge) are now separate Velocity plugins
    // and will register themselves with the main plugin at runtime. Do not include
    // them as project dependencies here so they are not bundled into the main shadow jar.
    implementation(project(":api"))
//    implementation(project(":vcinjector"))

// Exposed ORM / runtime-loaded libraries
    needPackageCompileOnly(libs.exposedCore)
    needPackageCompileOnly(libs.exposedJdbc)
// Database drivers / runtime-loaded libraries
    needPackageCompileOnly(libs.sqliteJdbc)
    needPackageCompileOnly(libs.mysql)
    needPackageCompileOnly(libs.mariadb)
    needPackageCompileOnly(libs.hikari)
//    VC
    compileOnly(libs.velocityApi)
    compileOnly(libs.velocityProxy) // From Elytrium Repo.
//    limbo
    compileOnly(libs.limboApi)

    needPackageCompileOnly(libs.configurateExtraKotlin)
    needPackageCompileOnly(libs.configurateHocon)
    compileOnly(libs.nettyAll)
    compileOnly(libs.gson)
    compileOnly(libs.log4jApi)
    compileOnly(libs.adventureTextSerializerGson)
    compileOnly(libs.adventureTextLoggerSlf4j)
    compileOnly(libs.adventureTextMinimessage)
    compileOnly(libs.guice)
    compileOnly(libs.guava)
    compileOnly(libs.brigadier)

    annotationProcessor(libs.velocityApi)

    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
}

tasks {
    named<Jar>("jar") {
        archiveBaseName.set("HyperZoneLogin")
        archiveClassifier.set("")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val apiProject = project(":api")
        val apiSourceSets = apiProject.extensions.getByType(SourceSetContainer::class.java)
        dependsOn(apiProject.tasks.named("classes"))
        from(apiSourceSets.named("main").get().output)
    }
}
