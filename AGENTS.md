# AGENTS.md

此文件为 AI 智能体提供项目开发指导。

## 项目概览

ZMusic Mod 是 ZMusic 的 Minecraft 客户端模组工程，采用多加载器架构，支持 Fabric、NeoForge 和 Forge 三条版本线。

当前仓库采用三套独立构建：

```text
zmusic-shared  -> 共享常量，发布到本地 Maven 供各 loader 引用
zmusic-modern  -> Fabric / NeoForge 现代版本线
zmusic-legacy  -> Forge 经典版本线（1.7.10 - 1.12.2）
```

公共配置（`modId`、`modName`、`modVersion` 等）集中在根目录 `zmusic.properties`，各构建的 `build.gradle` 从该文件读取。

## 模块结构和职责

### zmusic-shared

* 共享常量（`ZMusicConstants.MOD_ID` 等），通过 Maven 坐标 `me.zhenxin:zmusic-shared` 发布

### zmusic-modern

* `zmusic-fabric-1.16.5`
* `zmusic-fabric-1.21.8`
* `zmusic-fabric-26.1`
* `zmusic-neoforge-1.21.1`

### zmusic-legacy

* `zmusic-forge-1.7.10`
* `zmusic-forge-1.12.2`

## 开发命令

### 初始化

```bash
mise install
```

### 常用命令

```bash
# 发布共享常量到本地 Maven（修改 shared 后必须先执行）
./zmusic-shared/gradlew publishToMavenLocal

# 现代版本线
./zmusic-modern/gradlew build

# 老版本线
./zmusic-legacy/gradlew build
```

## Java 和工具链约束

* 根目录 `mise.toml` 只指定默认开发 Java 为 `21`
* `zmusic-modern` 中 `Fabric 26.1` 使用 Java 25 toolchain
* `zmusic-legacy` 使用 `RetroFuturaGradle`，以 Java 21 启动 daemon，并自动解析 Java 8 toolchain

不要在仓库里写死本机绝对路径形式的 JDK 配置。

## 重要约束

1. `zmusic-shared` 与 `zmusic-modern / zmusic-legacy` 之间通过 Maven 坐标衔接，不直接跨构建 `project()` 依赖
2. `zmusic-shared` 保持 Java 8 兼容
3. Minecraft 版本号按普通字符串处理，不要假设一定是 `1.x`
4. 生成目录如 `run/`、`.gradle/`、`build/` 不应提交
5. 禁止使用全限定类名，统一通过 `import` 引入类型

## JavaDoc 规范

新增或修改 Java 类型时，类级 JavaDoc 使用以下模板：

```java
/**
 * {描述}
 *
 * @author 真心
 * @since {日期} {时间}
 */
```

注释重点解释"为什么这样做"，不要机械复述代码表面行为。

## 提交前检查

至少执行和改动对应的构建命令，确保对应子构建可编译。
