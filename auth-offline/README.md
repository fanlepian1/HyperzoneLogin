Auth Offline module
===================

This module is now packaged as a standalone Velocity plugin named `hzl-auth-offline`.

Usage
-----

1. Build the plugin: `./gradlew :auth-offline:build`
2. Put `auth-offline-1.0.0-SNAPSHOT.jar` (or produced artifact) into your Velocity `plugins/` folder alongside the main `HyperZoneLogin` plugin.
3. The module will attempt to find the `hyperzonelogin` main plugin and call `registerModule(...)` to integrate with it. Ensure the main plugin is present and enabled.

Notes
-----
- The module depends on the `api` project at compile time but does not bundle it. The main `openvc` plugin provides the API at runtime.
- If the main plugin isn't present at plugin initialization, this module will log a warning and try to function when the main plugin becomes available.

