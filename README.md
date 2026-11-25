# Touhou Little Maid: Futo（车万女仆：布都）

车万女仆（Touhou Little Maid）的 **Fabric 1.18.2 移植版**，`modid` 与上游保持一致：`touhou_little_maid`。

- 上游（Forge 1.18.2，v1.2.2）：https://github.com/TartaricAcid/TouhouLittleMaid
- Fabric 1.20.1 参考移植：https://github.com/Sh1roCu/TouhouLittleMaid-Orihime
- 本移植版：https://github.com/EndDavid/TouhouLittleMaid-Futo

---

## 环境要求

| 依赖 | 版本 / 说明 |
|---|---|
| Minecraft | 1.18.2 |
| Fabric Loader | ≥ 0.14.25 |
| Java | ≥ 17 |
| Fabric API | 必需 |
| Forge Config API Port | 必需，`>=3.2.0 <4.0.0` |

以下为可选，装上才有对应功能：

| 依赖 | 作用 |
|---|---|
| Cloth Config `>=6.0.0 <7.0.0` | 图形化配置界面 |
| Patchouli | 《记忆中的幻想乡》手册 |
| Mod Menu `>=3.0.0` | 模组列表里的配置入口 |

还有若干「装了才联动」的模组（REI、JEI、EMI、Jade、YSM 等），不装不影响主流程。

## 安装

1. 装好 Fabric Loader 与 Fabric API 的 1.18.2 版本；
2. 把 `touhoulittlemaid-fabric-1.18.2-futo-<版本>.jar` 和上表的**必需**依赖一起放进 `mods/`；
3. 启动游戏。单人 / 服务端通用（`environment: "*"`），双端需装同一版本。

## 构建

```bash
./gradlew.bat build        # 产物：build/libs/touhoulittlemaid-fabric-1.18.2-futo-<版本>.jar
./gradlew.bat runClient    # 开发客户端（固定玩家名 Dev）
./gradlew.bat runServer    # 开发服务端
```

Gradle 守护进程用 **JDK 21**（Loom 1.15 要求），编译与运行游戏用 **JDK 17**（1.18.2 要求），
两者已写进 `gradle.properties`，**不需要另外设 `JAVA_HOME`**。
工具链细节、构建产物校验与「构建通过 ≠ 能跑」的原因见
[`docs_dev/01-build-and-toolchain.md`](docs_dev/01-build-and-toolchain.md)。

## 文档

移植过程的笔记（逐模块实现、与上游的差异、踩过的坑、验证口径）都在 [`docs_dev/`](docs_dev/README.md)：

| 文件 | 内容 |
|---|---|
| [`docs_dev/01-build-and-toolchain.md`](docs_dev/01-build-and-toolchain.md) | 目录结构、构建 / 运行、Forge 兼容层、正式实例部署的坑 |
| [`docs_dev/02-core-systems.md`](docs_dev/02-core-systems.md) | Brain 与工作模式、GUI / 命令、配置系统、声音、AI 聊天与语音 |
| [`docs_dev/03-content-modules.md`](docs_dev/03-content-modules.md) | 棋类、祭坛 / 神社 / 墓碑、坐垫、隙间、手册、成就、配方 |
| [`docs_dev/04-rendering-and-models.md`](docs_dev/04-rendering-and-models.md) | GeckoLib 管线、扫帚、油库里替换、模型与动画 |
| [`docs_dev/05-progress-and-known-issues.md`](docs_dev/05-progress-and-known-issues.md) | 进度总览、测试示例、已知问题、其他记录 |

每轮迭代的进展、当前状态与待办见 [`HANDOFF.md`](HANDOFF.md)。

## 许可

- **代码**：MIT，见 [`LICENSE-MIT`](LICENSE-MIT)
- **美术 / 模型 / 声音等资源**：CC BY-NC-SA 4.0，见 [`LICENSE-CC`](LICENSE-CC)

两者均继承上游（TartaricAcid / Touhou Little Maid 及其贡献者）。

