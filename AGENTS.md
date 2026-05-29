# AGENTS.md

ZMusic 是一个**纯客户端** Minecraft 音乐 mod。同一套逻辑代码按「加载器 × MC 版本」拆成大量叶子子项目，共享一个 `zmusic-core` 核心模块，核心模块通过 JNI 桥接外部 Zig 播放引擎（zmusic-player）。

## 构建命令

构建依赖多个 JDK，由 `mise` 管理（`temurin-8/16/17/21/25`）。不要直接调 `./gradlew build`，要走 mise 任务，它会把所有 JDK 路径传给 Gradle 的 toolchain：

```bash
mise run build      # 构建主工程（除 26.1.2 外的所有版本），等价 ./gradlew clean build
mise run build-26   # 单独构建 26.1.2（Fabric + NeoForge），见下方「两套 Gradle」
mise run clean
```

构建单个版本（主工程，用仓库自带 wrapper，Gradle 8.14.5）：

```bash
./gradlew :zmusic-fabric:zmusic-fabric-1.21.11:jar
./gradlew :zmusic-forge:zmusic-forge-1.20.1:jar
```

产物在各子项目的 `build/libs/*.jar`。**本仓库没有任何测试代码**，验证手段是「能否编译出 jar」。jar 里会内嵌 `zmusic-core` 的 class、各平台原生库（`META-INF/native/`）和 `LICENSE`。

### 两套 Gradle（关键）

`26.1.2` 目标用了 Java 25 / loom 1.16.2 / NeoGradle 7.x，需要 Gradle 9.5.1，与主工程的 Gradle 8.14.5 + ForgeGradle 6 不兼容，所以被拆成独立的 composite build：

- 主工程：`settings.gradle` + `build.gradle`，仓库 wrapper 驱动。
- `build-26/`：独立的 `settings.gradle`/`build.gradle`，**通过 `scripts/gradle-26.sh` 现下载 Gradle 9.5.1 运行**（不走 wrapper）。它用 `projectDir` 把 `../zmusic-core`、`../zmusic-fabric/zmusic-fabric-26.1.2`、`../zmusic-neoforge/zmusic-neoforge-26.1.2` 挂进来复用同一份源码。

改动 `26.1.2` 相关内容时，要意识到它的依赖版本、native 打包逻辑在 `build-26/build.gradle` 里**单独维护一份**，和主 `build.gradle` 不共享。

## 架构

### 核心 / 平台分层

- `zmusic-core`（Java 8）：纯逻辑，不依赖任何 MC API。包含主入口 `ZMusic`、JNI 桥接 `ZMusicPlayer`、平台无关事件 `ClientEvent`/`PacketEvent`，以及 `SoundManager` 接口。
- `zmusic-<loader>-<mcversion>`：叶子子项目，依赖对应 MC/加载器，提供 mod 入口、`SoundManager` 实现、以及拦截原版音乐的 mixin/事件。**每个 MC 版本一份完整拷贝**，因为各版本网络 API、声音 API 差异大，无法用单份源码覆盖。

`build.gradle` 的 `isLeafPlatformProject(p)` 用正则 `zmusic-(fabric|forge|neoforge)-\d+.*` 区分「叶子项目」和「容器项目」。`zmusic-fabric`/`zmusic-forge`/`zmusic-neoforge` 是空的容器项目，没有源码。

### 三个跨平台关注点

1. **抑制原版音乐**：播放器处于 `STATE_PLAYING` 时，取消 `MUSIC`/`RECORDS` 分类的声音。
   - Fabric：mixin 注入 `SoundSystem.play*`（`mixin/SoundEvent.java`）。
   - Forge：`@SubscribeEvent` 监听 `SoundEvent.SoundSourceEvent`（`event/ForgeEvent.java`）。
   - 每 tick 把游戏的 RECORDS 音量同步给原生播放器（Fabric 走 `mixin/Tick`，Forge 走 `onTick`）。

2. **网络协议**：服务端通过插件消息通道 `zmusic:channel` 下发，格式是「1 字节前缀 + UTF-8 文本」。客户端跳过首字节解析文本，交给 `ClientEvent.onPacket`，按 `[Play]xxx` / `[Stop]` 分发。各版本网络 API 不同，实现差异最大：
   - 老 Forge（1.12）：`FMLNetworkEvent.ClientCustomPacketEvent`。
   - 新 Forge：`SimpleChannel`。
   - Fabric：`CustomPayload` + `PayloadTypeRegistry`。
   - NeoForge：`RegisterPayloadHandlersEvent`（高版本还需 mixin 兼容 Bukkit/Velocity 插件服下的 vanilla 连接，见 `ClientPacketListenerPayloadMixin`）。

3. **原生库加载**（`ZMusicPlayer.loadNativeLibrary`）：先试 `System.loadLibrary("zmusic")`，失败则从 jar 内 `META-INF/native/<platform>/` 提取，按 SHA-256 哈希命名缓存到游戏目录下的 `zmusic/`，校验后 `System.load`。游戏目录通过反射依次探测 Fabric→Forge→NeoForge→旧版 `Minecraft.mcDataDir` 获得，所以核心模块不直接依赖任何加载器。

### 原生库打包

`downloadNativeLibs` 任务从 `zmusic-dev/zmusic-player` 的 GitHub Release（tag `v${zmusicPlayerVersion}`，版本在 `gradle.properties`）拉取四个平台的 native 库，解压缓存到 `.gradle/zmusic-player/`，叶子项目打 jar 时塞进 `META-INF/native/`。**构建联网**，受 GitHub API 限流影响。

## 约定与陷阱

- **Java 版本按目标而定**：Forge 1.12–1.16 用 Java 8，Forge 1.17+ / 高版本 Fabric 用 17，1.21.x 用 21，26.1.2 用 25；`zmusic-core` 固定 Java 8 以兼容全部目标。各子项目 `build.gradle` 自带 `sourceCompatibility`。
- **新增一个 MC 版本**需要四处同步：① 复制最接近的版本目录并改源码/`build.gradle` 依赖；② 在 `settings.gradle`（或 `build-26/settings.gradle`）`include`；③ 在 `.github/workflows/dev.yml` 加 upload-artifact 步骤；④ 在 `stable.yml` 的两处 `files:` 列表（gh-release 和 mc-publish）加 jar 路径。
- `zmusic-core` 用 Lombok（`@Log4j2`/`@Getter` 等），log4j-api 是 `compileOnly`（运行时由 MC 提供）。
- 代码注释、javadoc 以中文为主，沿用现有风格。

## CI / 发布

- `dev.yml`：push 到 `dev` 或 PR 时跑 `mise run build` + `build-26`，把每个版本 jar 作为 artifact 上传。
- `stable.yml`：打 `v*` tag 触发，构建后创建 GitHub Release、发布到 CurseForge/Modrinth，再用 `scripts/sync-release.sh` 把 Release 同步到 Codeberg 和 CNB。
