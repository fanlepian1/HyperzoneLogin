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

package icu.h2l.api.util

import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.kotlin.dataClassFieldDiscoverer
import org.spongepowered.configurate.objectmapping.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path



object ConfigLoader {
    /**
     * Unified configuration loader for HOCON files.
     * @param dataDirectory The directory containing the configuration file
     * @param fileName The name of the configuration file (e.g., "remap.conf")
     * @param nodePath The path within the configuration file. Use an array of keys for nested structures.
     * @param header Intended header for the file
     * @param defaultProvider Provides the default config object if reading fails or file empty
     * @param postLoadHook A hook that gets invoked immediately after loading/getting the config. Returning a non-null instance overrides the default/loaded result.
     * @param forceSaveHook Whether to save the file back, usually true if first creation, but can be customized
     */
    inline fun <reified T : Any> loadConfig(
        dataDirectory: Path,
        fileName: String,
        nodePath: Array<String> = emptyArray(),
        header: String = "",
        defaultProvider: () -> T,
        noinline postLoadHook: ((ConfigurationNode, T, Boolean) -> T)? = null,
        noinline forceSaveHook: ((ConfigurationNode, Boolean) -> Boolean) = { _, firstCreation -> firstCreation }
    ): T {
        val path = dataDirectory.resolve(fileName)
        val fileNotExists = Files.notExists(path)
        val loader = HoconConfigurationLoader.builder()
            .defaultOptions { opts: ConfigurationOptions ->
                opts.shouldCopyDefaults(true)
                    .header(header)
                    .serializers { s ->
                        s.registerAnnotatedObjects(
                            ObjectMapper.factoryBuilder().addDiscoverer(dataClassFieldDiscoverer()).build()
                        )
                    }
            }
            .path(path)
            .build()
        val node = loader.load()
        val targetNode = if (nodePath.isEmpty()) node else node.node(*nodePath)

        val firstCreation = fileNotExists || targetNode.virtual()

        val loaded = runCatching { targetNode.get(T::class.java) }.getOrNull() ?: defaultProvider()

        val finalConfig = postLoadHook?.invoke(targetNode, loaded, firstCreation) ?: loaded
        val shouldSave = forceSaveHook(targetNode, firstCreation)

        if (shouldSave) {
            targetNode.set(finalConfig)
            ConfigCommentTranslatorProvider.getOrNull()?.let { translator ->
                translateNodeComments(node, translator)
            }
            loader.save(node)
        }
        return finalConfig
    }

    @PublishedApi
    internal fun translateNodeComments(node: CommentedConfigurationNode, translator: ConfigCommentTranslator) {
        val comment = node.comment()
        if (comment != null) {
            val translated = translator.translate(comment.trim())
            if (translated != null) {
                node.comment(translated)
            }
        }
        if (node.isMap) {
            node.childrenMap().values.forEach { child -> translateNodeComments(child, translator) }
        } else if (node.isList) {
            node.childrenList().forEach { child -> translateNodeComments(child, translator) }
        }
    }
}
