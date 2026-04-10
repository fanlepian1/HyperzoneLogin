# Third-Party Notices

## LuckPerms

HyperZoneLogin 的运行时动态依赖加载实现参考并改编自 [LuckPerms](https://github.com/LuckPerms/LuckPerms) 的依赖加载与 Velocity classpath 注入实现。

- 原项目许可证：MIT License
- 原作者：lucko (Luck) 及 contributors

本仓库中下列文件包含基于 LuckPerms 改编的源码，并在文件头中保留了对应的 MIT 许可声明：

- `api/src/main/java/icu/h2l/api/dependency/HyperDependency.java`
- `api/src/main/java/icu/h2l/api/dependency/HyperDependencyDownloadException.java`
- `api/src/main/java/icu/h2l/api/dependency/HyperDependencyClassPathAppender.java`
- `api/src/main/java/icu/h2l/api/dependency/HyperDependencyRepository.java`
- `api/src/main/java/icu/h2l/api/dependency/HyperDependencyManager.java`
- `api/src/main/java/icu/h2l/api/dependency/VelocityHyperDependencyClassPathAppender.java`

这些改编代码仅用于 HyperZoneLogin 的运行时库下载、校验与类路径注入功能。

## bStats

HyperZoneLogin 会在 `velocity` 主插件启动时下载 `bStats` 的原始 Maven 工件，并在本地缓存中重定位（relocate）后再注入类路径，以避免与其他插件的 `org.bstats` 包冲突。

- 上游项目：[`bStats-Metrics`](https://github.com/Bastian/bStats-Metrics)
- 原项目许可证：MIT License
- 原作者：Bastian Oppermann 及 contributors

## jar-relocator

HyperZoneLogin 使用 [`jar-relocator`](https://github.com/lucko/jar-relocator) 在运行时对下载完成的 `bStats` 工件执行包路径重定位。

- 原项目许可证：Apache License 2.0
- 原作者：Luck 及 contributors

