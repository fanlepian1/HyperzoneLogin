Auth Yggdrasil module
======================

This module is packaged as a standalone Velocity plugin `hzl-auth-yggd`.

Usage
-----

1. Build: `./gradlew :auth-yggd:build`
2. Place the produced jar into your Velocity `plugins/` folder alongside the main `HyperZoneLogin` plugin.
3. The plugin will register its submodule with the main plugin at runtime.

Notes
-----
- Depends on the `api` project at compile time but not bundled into the jar.

