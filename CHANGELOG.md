# Changelog

## v3.7.1

- 修复 Forge 版在 SCP Lockdown 滑动门等模组触发空声音事件时，可能因读取声音分类导致客户端崩溃的问题。
- 修复 Fabric 版在收到空声音实例时可能触发同类崩溃的问题。

English:

- Fixed a Forge client crash when mods such as SCP Lockdown sliding doors emit a sound event with a null sound instance.
- Fixed the same class of Fabric crash when a null sound instance is passed to the sound hook.

## v3.7.0

- 将模组运行时切换到原生 `zmusic-player`。
- 新增 Minecraft 26.1、26.1.1、26.1.2 支持，由 26.1.2 构建覆盖。
- 将 Fabric、Forge、NeoForge 拆分为独立 Gradle 构建组，并使用矩阵构建减少稳定版构建时间。
- 新增 Forge 1.20.4 构建。
- 新增模组图标元数据。
- 修复 26.x jar 缺少 core classes 的问题。
- 修复 NeoForge 26.1.2 构建和 NeoForm setup 问题，使用 26.1.2.70 beta 依赖并修复 setup cache。
- 重构稳定版发布流程，支持 GitHub Releases、Modrinth、CurseForge、Codeberg 和 CNB 使用同一份发布产物与更新日志。

English:

- Switched the mod runtime to the native `zmusic-player`.
- Added Minecraft 26.1, 26.1.1, and 26.1.2 support through the 26.1.2 builds.
- Split Fabric, Forge, and NeoForge into dedicated Gradle build groups with matrix builds for faster stable releases.
- Added Forge 1.20.4 build support.
- Added mod icon metadata.
- Fixed 26.x jars missing bundled core classes.
- Fixed NeoForge 26.1.2 build and NeoForm setup issues by using the 26.1.2.70 beta dependency and repairing the setup cache.
- Reworked stable release publishing so GitHub Releases, Modrinth, CurseForge, Codeberg, and CNB use the same artifacts and release notes.
