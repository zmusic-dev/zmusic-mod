<div align="center">

![][banner]

![][java]
![][license]

</div>

## 反馈

* 提交 [Issues](../../issues)

## 开发

```shell
git clone https://github.com/starhui-dev/zmusic-mod
cd zmusic-mod
mise trust
mise install
mise run build
```

也可以使用 `mise run build:fabric`、`mise run build:forge` 或 `mise run build:neoforge` 单独构建一个加载器矩阵。

## 通信协议

Mod 通过 `zmusic:packet` 通道与 ZMusic Plugin 通信，使用 `ZMPK + version + JSON` 二进制帧完成握手、播放、停止、状态、进度和错误上报。该协议不兼容旧 `zmusic:channel` 与 `[Play]` / `[Stop]` 文本消息，Plugin 与 Mod 必须同时升级。

## 开源协议

本项目使用 [GPL-3.0](LICENSE) 协议开放源代码

```text
ZMusic
Copyright (C) 2023 RealHeart
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```

## 鸣谢

* [JetBrains](https://www.jetbrains.com/zh-cn/)
* [FabricMC](https://fabricmc.net/)
* [AllMusic Mod](https://github.com/Coloryr/AllMusic_M)

[banner]: https://socialify.git.ci/starhui-dev/zmusic-mod/image?description=1&forks=1&issues=1&language=1&name=1&owner=1&pulls=1&stargazers=1&theme=Auto

[java]: https://img.shields.io/badge/java-17-blue?style=for-the-badge

[license]: https://img.shields.io/github/license/starhui-dev/zmusic-mod?style=for-the-badge
