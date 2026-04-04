Data Merge module
=================

This module is packaged as a standalone Velocity plugin `hzl-data-merge`.

Usage
-----

1. Build: `./gradlew :data-merge:build`
2. Place the produced jar into your Velocity `plugins/` folder alongside the main `HyperZoneLogin` plugin.
3. The plugin will register its submodule with the main plugin at runtime.

Notes
-----
- The module references `api` and other modules at compile time, but they are not bundled into the jar.

