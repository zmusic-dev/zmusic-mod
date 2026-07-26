# AGENTS.md

ZMusic 是一个**纯客户端** Minecraft 音乐 mod。同一套逻辑代码按「加载器 × MC 版本」拆成大量叶子子项目，共享一个 `zmusic-core` 核心模块，核心模块通过 JNI 桥接外部 Zig 播放引擎（zmusic-player）。

## 构建命令

构建依赖多个 JDK，由 `mise` 管理（`temurin-8/16/17/21/25`）。不要直接调 `./gradlew build`，要走 mise 任务，它会把所有 JDK 路径传给 Gradle 的 toolchain：

```bash
mise run build      # 构建 Fabric / Forge / NeoForge 全部版本
mise run build:fabric
mise run build:forge
mise run build:neoforge
mise run clean
```

构建单个版本时，用仓库自带 wrapper（Gradle 9.5.1）和 `builds/<loader>` 入口，并通过 `-Pzmusic.project` 只挂载目标叶子项目：

```bash
./gradlew --project-dir builds/fabric :zmusic-fabric-1.21.11:jar -Pzmusic.project=zmusic-fabric-1.21.11
./gradlew --project-dir builds/forge :zmusic-forge-1.20.4:jar -Pzmusic.project=zmusic-forge-1.20.4
./gradlew --project-dir builds/neoforge :zmusic-neoforge-26.1.2:jar -Pzmusic.project=zmusic-neoforge-26.1.2
```

产物在各子项目的 `build/libs/*.jar`。**本仓库没有任何测试代码**，验证手段是「能否编译出 jar」。jar 里会内嵌 `zmusic-core` 的 class、各平台原生库（`META-INF/native/`）和 `LICENSE`。

### 多入口 Gradle 构建（关键）

仓库 wrapper 统一使用 Gradle 9.5.1。CI 和本地构建按加载器拆成三个独立 Gradle 入口：

- `builds/fabric`
- `builds/forge`
- `builds/neoforge`

这些入口通过 `projectDir` 指回现有源码目录，不移动源码。CI 使用 matrix 给每个叶子项目传 `-Pzmusic.project=<project>`，避免配置和构建无关版本。旧的 `build-26/` 和 `scripts/gradle-26.sh` 已移除。

## 架构

### 核心 / 平台分层

- `zmusic-core`（Java 8）：纯逻辑，不依赖任何 MC API。包含主入口 `ZMusic`、JNI 桥接 `ZMusicPlayer`、平台无关事件 `ClientEvent`/`PacketEvent`、`ZMPK` 协议编解码，以及 `SoundManager` 接口。
- `zmusic-<loader>-<mcversion>`：叶子子项目，依赖对应 MC/加载器，提供 mod 入口、`SoundManager` 实现、以及拦截原版音乐的 mixin/事件。**每个 MC 版本一份完整拷贝**，因为各版本网络 API、声音 API 差异大，无法用单份源码覆盖。

`build.gradle` 的 `isLeafPlatformProject(p)` 用正则 `zmusic-(fabric|forge|neoforge)-\d+.*` 区分「叶子项目」和「容器项目」。`zmusic-fabric`/`zmusic-forge`/`zmusic-neoforge` 是空的容器项目，没有源码。

### 三个跨平台关注点

1. **抑制原版音乐**：播放器处于 `STATE_PLAYING` 时，取消 `MUSIC`/`RECORDS` 分类的声音。
   - Fabric：mixin 注入 `SoundSystem.play*`（`mixin/SoundEvent.java`）。
   - Forge：`@SubscribeEvent` 监听 `SoundEvent.SoundSourceEvent`（`event/ForgeEvent.java`）。
   - 每 tick 把游戏的 RECORDS 音量同步给原生播放器（Fabric 走 `mixin/Tick`，Forge 走 `onTick`）。

2. **网络协议**：Plugin 与 Mod 统一使用 `zmusic:packet` 通道和 `ZMPK + version + JSON` 二进制帧。`zmusic-core` 的 `ProtocolCodec` 负责帧校验，`ClientEvent` 处理握手、播放、停止和状态回报；该协议不兼容旧 `zmusic:channel` 与 `[Play]` / `[Stop]` 文本消息。各版本网络 API 不同，实现差异最大：
   - 老 Forge（1.12）：`FMLNetworkEvent.ClientCustomPacketEvent`。
   - 新 Forge：`SimpleChannel`。
   - Fabric：`CustomPayload` + `PayloadTypeRegistry`。
   - NeoForge：`RegisterPayloadHandlersEvent`（高版本还需 mixin 兼容 Bukkit/Velocity 插件服下的 vanilla 连接，见 `ClientPacketListenerPayloadMixin`）。

3. **原生库加载**（`ZMusicPlayer.loadNativeLibrary`）：先试 `System.loadLibrary("zmusic")`，失败则从 jar 内 `META-INF/native/<platform>/` 提取，按 SHA-256 哈希命名缓存到游戏目录下的 `zmusic/`，校验后 `System.load`。游戏目录通过反射依次探测 Fabric→Forge→NeoForge→旧版 `Minecraft.mcDataDir` 获得，所以核心模块不直接依赖任何加载器。

### 原生库打包

`downloadNativeLibs` 任务从 `starhui-dev/zmusic-player` 的 GitHub Release（tag `v${zmusicPlayerVersion}`，版本在 `gradle.properties`）拉取四个平台的 native 库，解压缓存到 `.gradle/zmusic-player/`，叶子项目打 jar 时塞进 `META-INF/native/`。**构建联网**，受 GitHub API 限流影响。

## 约定与陷阱

- **Java 版本按目标而定**：Forge 1.12–1.16 用 Java 8，Forge 1.17+ / 高版本 Fabric 用 17，1.21.x 用 21，26.1.2 用 25；`zmusic-core` 固定 Java 8 以兼容全部目标。各子项目 `build.gradle` 自带 `sourceCompatibility`。
- **新增一个 MC 版本**需要同步：① 复制最接近的版本目录并改源码/`build.gradle` 依赖；② 在对应 `builds/<loader>/settings.gradle` 加项目；③ 在 `.github/workflows/dev.yml` matrix 加条目；④ 在 `stable.yml` 的两处 `files:` 列表（gh-release 和 mc-publish）加 jar 路径。
- `zmusic-core` 用 Lombok（`@Log4j2`/`@Getter` 等），log4j-api 是 `compileOnly`（运行时由 MC 提供）。
- 代码注释、javadoc 以中文为主，沿用现有风格。

## CI / 发布

- `dev.yml`：push 到 `dev` 或 PR 时按加载器 × 版本 matrix 并发构建，每个 job 只上传自己的 jar。
- `stable.yml`：打 `v*` tag 触发，构建后创建 GitHub Release、发布到 CurseForge/Modrinth，再用 `scripts/sync-release.sh` 把 Release 同步到 Codeberg 和 CNB。
