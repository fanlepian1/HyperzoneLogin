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

package icu.h2l.api.dependency;

import java.util.List;

/**
 * Runtime dependency manifests used by HyperZoneLogin plugins.
 */
public final class HyperRuntimeLibraries {
    public static final HyperDependency EXPOSED_CORE = dependency("org.jetbrains.exposed", "exposed-core", "0.58.0", "0g/Uv8QdNKCoGHEhmhIdHUSnXo+EZyVMUOQKprdQcZ0=");
    public static final HyperDependency EXPOSED_JDBC = dependency("org.jetbrains.exposed", "exposed-jdbc", "0.58.0", "sk0veCWzl8wXdVTZm6MvSegNCMQHKrHRu3cwqGRodX0=");
    public static final HyperDependency HIKARI_CP = dependency("com.zaxxer", "HikariCP", "5.0.1", "JtSSOX5ndbQpZzeokZvwQEev5YJ/3SwItFV1lUNrOis=");
    public static final HyperDependency SQLITE_JDBC = dependency("org.xerial", "sqlite-jdbc", "3.47.1.0", "QWTZU0euq0K3VMq7eUW5PlTyndsu3tln2+l5tARctoU=");
    public static final HyperDependency MYSQL_CONNECTOR_J = dependency("com.mysql", "mysql-connector-j", "8.4.0", "13lih30BB3fP+ZcBXakO5onw9Lt2hINA4UiPK4MzKvU=");
    public static final HyperDependency PROTOBUF_JAVA = dependency("com.google.protobuf", "protobuf-java", "3.25.1", "SKjlihqPgu/xQaejiNON/nfXpI1eV8kGbuN/GRR+IN8=");
    public static final HyperDependency MARIADB_JAVA_CLIENT = dependency("org.mariadb.jdbc", "mariadb-java-client", "3.4.1", "9g5LKC8fS9t08KJkNrpweKXkgLb2cC9qe0XZul5gSiQ=");
    public static final HyperDependency WAFFLE_JNA = dependency("com.github.waffle", "waffle-jna", "3.3.0", "MByziUAmGKCo5/tWxLR+IPH5x3T5mj1yckNbri0yNYA=");
    public static final HyperDependency JNA = dependency("net.java.dev.jna", "jna", "5.13.0", "ZtT4GaBipRodVie//CP6xV0Wd/Dgof66FEqr3WcKZLs=");
    public static final HyperDependency JNA_PLATFORM = dependency("net.java.dev.jna", "jna-platform", "5.13.0", "R017iPbpcAm27B2YwwJN2VwjGHxl2r+8NTMbysPRc90=");
    public static final HyperDependency JCL_OVER_SLF4J = dependency("org.slf4j", "jcl-over-slf4j", "2.0.7", "QYBnV+HSba5dbbLKfUpRdu7S1ucJzYZWTUoR2rBgF0I=");
    public static final HyperDependency CONFIGURATE_EXTRA_KOTLIN = dependency("org.spongepowered", "configurate-extra-kotlin", "4.2.0", "LmbFtyK0pP3XAu6dEeYfHpO/YavjuofyBkDcrMDU38k=");
    public static final HyperDependency CONFIGURATE_HOCON = dependency("org.spongepowered", "configurate-hocon", "4.2.0", "/xN1mkZZmBB/iJgVB1G50M93tbX/ubXsFszqnPIpiT4=");
    public static final HyperDependency CONFIGURATE_CORE = dependency("org.spongepowered", "configurate-core", "4.2.0", "BsHp93iaGJrwwBVuvp9GnafMZ0Iz9D6BM8gxMe3Z9+A=");
    public static final HyperDependency GEANTYREF = dependency("io.leangen.geantyref", "geantyref", "1.3.16", "fx1ZEJLVFCtqqnz1n5TEx01X2+7wOy+CYpSfjza6xuM=");
    public static final HyperDependency KYORI_OPTION = dependency("net.kyori", "option", "1.1.0", "l7abSxff4CIXyRMa00JWTLya69BMdetoljm194/UsRw=");
    public static final HyperDependency KOTLINX_COROUTINES_CORE_JVM = dependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.1", "BpxZiGMyMOB07A05Mh7DzapFR8SekLqTbGPY/JHIwA0=");
    public static final HyperDependency H2 = dependency("com.h2database", "h2", "2.1.214", "1iPNwPYdIYz1SajQnxw5H/kQlhFrIuJHVHX85PvnK9A=");

    public static final List<HyperDependency> SHARED = List.of(
        EXPOSED_CORE,
        EXPOSED_JDBC,
        HIKARI_CP,
        SQLITE_JDBC,
        MYSQL_CONNECTOR_J,
        PROTOBUF_JAVA,
        MARIADB_JAVA_CLIENT,
        WAFFLE_JNA,
        JNA,
        JNA_PLATFORM,
        JCL_OVER_SLF4J,
        CONFIGURATE_EXTRA_KOTLIN,
        CONFIGURATE_HOCON,
        CONFIGURATE_CORE,
        GEANTYREF,
        KYORI_OPTION,
        KOTLINX_COROUTINES_CORE_JVM
    );
    public static final List<HyperDependency> DATA_MERGE_PRIVATE = List.of(H2);

    private HyperRuntimeLibraries() {
    }

    private static HyperDependency dependency(String groupId, String artifactId, String version, String checksum) {
        return new HyperDependency(groupId, artifactId, version, checksum);
    }
}

