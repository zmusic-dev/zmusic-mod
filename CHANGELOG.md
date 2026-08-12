# Changelog

## v3.8.0

- 将内置播放器升级为基于 Rust、Rodio、CPAL 和 Symphonia 的 `zmusic-player` 1.0.0-alpha.4。
- 新增 Linux ARM64、Windows ARM64 和 Android ARM64 支持；发布包现在内置 7 个平台的原生库。
- 新增 Minecraft 26.2 支持，提供 Fabric 和 NeoForge 构建。
- 将播放、停止和音量操作移出 Minecraft 客户端线程，避免网络加载阻塞游戏。
- 新增原生播放器状态、缓冲和错误日志，便于定位播放失败。
- 修复 Forge 1.20.1 客户端启动崩溃、音频线程冻结及映射差异导致的 `NoSuchMethodError`。
- 修复 Forge 1.20.4 模组元数据中的 Minecraft 和 Forge 版本范围。

English:

- Upgraded the bundled player to `zmusic-player` 1.0.0-alpha.4, powered by Rust, Rodio, CPAL, and Symphonia.
- Added Linux ARM64, Windows ARM64, and Android ARM64 support; release jars now bundle native libraries for seven platforms.
- Added Minecraft 26.2 support with Fabric and NeoForge builds.
- Moved play, stop, and volume operations off the Minecraft client thread to prevent network loading from blocking the game.
- Added native player state, buffering, and error diagnostics for playback failures.
- Fixed Forge 1.20.1 startup crashes, sound-thread freezes, and mapping-related `NoSuchMethodError` failures.
- Fixed the Minecraft and Forge version ranges in the Forge 1.20.4 mod metadata.

## v3.7.1

- 将模组运行时切换到原生 `zmusic-player`。
- 新增 Minecraft 26.1、26.1.1、26.1.2 支持，由 26.1.2 构建覆盖。
- 将 Fabric、Forge、NeoForge 拆分为独立 Gradle 构建组，并使用矩阵构建减少稳定版构建时间。
- 新增 Forge 1.20.4 构建。
- 新增模组图标元数据。
- 修复 26.x jar 缺少 core classes 的问题。
- 修复 NeoForge 26.1.2 构建和 NeoForm setup 问题，使用 26.1.2.70 beta 依赖并修复 setup cache。
- 修复 Forge 版在 SCP Lockdown 滑动门等模组触发空声音事件时，可能因读取声音分类导致客户端崩溃的问题。
- 修复 Fabric 版在收到空声音实例时可能触发同类崩溃的问题。
- 重构稳定版发布流程，支持 GitHub Releases、Modrinth、CurseForge、Codeberg 和 CNB 使用同一份发布产物与更新日志。

English:

- Switched the mod runtime to the native `zmusic-player`.
- Added Minecraft 26.1, 26.1.1, and 26.1.2 support through the 26.1.2 builds.
- Split Fabric, Forge, and NeoForge into dedicated Gradle build groups with matrix builds for faster stable releases.
- Added Forge 1.20.4 build support.
- Added mod icon metadata.
- Fixed 26.x jars missing bundled core classes.
- Fixed NeoForge 26.1.2 build and NeoForm setup issues by using the 26.1.2.70 beta dependency and repairing the setup cache.
- Fixed a Forge client crash when mods such as SCP Lockdown sliding doors emit a sound event with a null sound instance.
- Fixed the same class of Fabric crash when a null sound instance is passed to the sound hook.
- Reworked stable release publishing so GitHub Releases, Modrinth, CurseForge, Codeberg, and CNB use the same artifacts and release notes.
