# 开发文档（docs_dev）

这里放移植过程的记录：逐模块的实现笔记、与上游的差异、踩过的坑、验证口径。
根目录 [`README.md`](../README.md) 只保留面向使用者与构建者的内容，不再承载这些笔记。

| 文件 | 标题 | 内容 |
|---|---|---|
| [`01-build-and-toolchain.md`](01-build-and-toolchain.md) | 构建与工程 | 目录结构、构建 / 运行、Forge 兼容层、正式实例部署的坑 |
| [`02-core-systems.md`](02-core-systems.md) | 核心系统 | Brain 与工作模式、GUI / 命令、配置系统、声音、AI 聊天与语音 |
| [`03-content-modules.md`](03-content-modules.md) | 内容模块 | 棋类、祭坛 / 神社 / 墓碑、坐垫、隙间、手册、成就、配方 |
| [`04-rendering-and-models.md`](04-rendering-and-models.md) | 渲染与模型 | GeckoLib 管线、扫帚、油库里替换、模型与动画 |
| [`05-progress-and-known-issues.md`](05-progress-and-known-issues.md) | 进度与已知问题 | 进度总览、测试示例、已知问题、其他记录 |

## 各文件包含的章节

### [`01-build-and-toolchain.md`](01-build-and-toolchain.md)

- 目录结构
- 构建 / 运行
- 工程与 Forge 兼容层
- 「一放置就崩溃」：渲染器注册丢了，但服务端毫无异常
- 装进正式实例：两处「开发环境能跑、真 jar 崩」的坑

### [`02-core-systems.md`](02-core-systems.md)

- Brain 行为与工作模式
- 物品栏 / GUI / 命令
- 配置系统（Forge Config API Port）
- 声音
- 配置：Forge Config API Port 到底给了什么，我们用了多少
- 界面的「世界压暗层」：1.18.2 与 1.20 的一个静默差异
- 日程表按钮：从「只有白班」到能切夜班
- AI 聊天（LLM 对话）
- AI 语音（TTS）
- 配置界面：Cloth Config 全量移植，以及「哪些项真的生效」

### [`03-content-modules.md`](03-content-modules.md)

- 棋类（中国象棋 / 五子棋 / 国际象棋）
- 祭坛 / 神社 / 墓碑 / 女仆床 / 稻草人
- 女仆妖精（EntityFairy）
- 经验球、P 点与灵力
- 神社庭灯（maid_beacon）
- 受伤、盾牌与主人误伤
- 饮食系统（工作餐 / 回血餐）
- 六个右键物品交互
- 坐垫（自定义坐垫）
- 隙间（无线 I/O 饰品）
- 娱乐方块：键盘 / 书架 / 电脑
- 新生成女仆的随机外观：从 6 个写死的模型到 193 个
- 魂符（smart_slab）：首次进服送的那张「已驯服女仆」
- 手册进度：54 词条 / 3 分类，全部补齐
- 野餐篮与野餐垫：一个物品，三种身份
- 模型切换器：红石切模型，以及它为什么不需要 `canConnectRedstone`
- 进度（成就）系统：54 条全量移植
- 配方补全与 REI 联动
- Jade 联动（女仆 / 神社灯提示行）

### [`04-rendering-and-models.md`](04-rendering-and-models.md)

- 渲染（GeckoLib3 管线）
- 魔理沙的扫帚
- 扫帚：下扫帚的键，以及不再抢走准星
- 扫帚：浮动、碰撞箱，还有那句提示
- 扫帚浮动为什么看着生硬：两端都在算同一条正弦
- 史莱姆换成灵梦油库里（材质按上游 1.20.1 重绘后的那张）
- 岩浆怪换成魔理沙油库里（直接吃上游 1.20.1 的基岩模型）

### [`05-progress-and-known-issues.md`](05-progress-and-known-issues.md)

- 进度总览
- 测试示例
- 已知问题
- 其他记录

## 另有

- [`../HANDOFF.md`](../HANDOFF.md) —— 按轮次记录的进展、当前状态与待办（每轮更新，最后写）。
- [`../port/`](../port) —— 历史移植脚本与守卫脚本（`check-encoding` / `check-dangling` / `check-renderers` / `check-jar` / `deploy-jar` / `kill-dev-run`）。
- [`../port/upstream/`](../port/upstream) —— 上游 Forge 1.18.2 源码，只读参考，不参与编译。

## 约定

- 只记录**经过游戏内验证**的结论；被推翻的假设一律不留。
- 每条「与上游不同」都写明原因 —— 那些大多是 1.18.2 缺少 Forge 补丁导致的，不是偏好。
- 这些文件按 CRLF + UTF-8 存储，编辑时不要整体重排行尾（见 `../.github/copilot-instructions.md`）。
- 文件名一律 ASCII，标题与正文用中文 —— `port/*.ps1` 是纯 ASCII 脚本，中文路径在 PS 5.1 里会坏。
