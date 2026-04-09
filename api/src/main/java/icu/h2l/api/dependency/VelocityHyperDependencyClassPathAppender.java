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

import com.velocitypowered.api.proxy.ProxyServer;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Adapted from LuckPerms' Velocity classpath appender.
 */
public final class VelocityHyperDependencyClassPathAppender implements HyperDependencyClassPathAppender {
    private final ProxyServer proxy;
    private final Object plugin;

    public VelocityHyperDependencyClassPathAppender(ProxyServer proxy, Object plugin) {
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void addJarToClasspath(Path file) {
        this.proxy.getPluginManager().addToClasspath(this.plugin, file);
    }
}

