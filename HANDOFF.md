# 交接单（HANDOFF）
> **用法**：新开对话后，第一条消息只写「读 HANDOFF.md，然后继续」。
> **不要整份读 `README.md`**（3400+ 行，光读一遍就几十万 token）。需要细节时按小节
> `grep -n "^### " README.md` 找到行号，再 `sed -n 'A,Bp' README.md` 取那一段。
---
## 1. 当前状态：模型切换器 / 帕秋莉手册 / 三个附魔 / 坐船钓鱼 均已提交
上游对应 `block/BlockModelSwitcher`、`tileentity/TileEntityModelSwitcher`、
`item/ItemModelSwitcher`、两个 Screen、两个数据包。手册条目 `other/model_switcher` 已搬回，
`entries` 由 53 变 54 —— 帕秋莉手册**全部补齐**。
### 已通过的闸（本轮实测）
| 检查 | 结果 |
|---|---|
| `./gradlew.bat build` | BUILD SUCCESSFUL |
| `port/check-renderers.ps1` | 11/11、13 block entity → 11 renderer、ALL OK |
| `port/book_pages_held_back/validate-book.ps1` | `entries=54 categories=3 problems=0` |
| `port/check-encoding.ps1` | `BAD_FILES=0` |
| `runServer` + RCON | 红石切换 / 边沿 / 回绕 / 空列表 / 拆方块掉物品，全部断言通过 |
| `runClient` 到主菜单 | 无 `Missing model` / `missing texture`，`crash-reports` 为空 |
详细结论写在 README 文末「模型切换器」一节，**不要重新推导**。

### 之后又完成的两块（都在 `main` 上）
- **帕秋莉手册封板**：54 条目 / 3 分类，条目集 = 1.18.2 Forge 的 52 条全集 + 本模组特有的
  `other/cchess`、`other/wchess`。语言键 153 个，`en_us` 与 `zh_cn` 完全一致；
  43 个 `altar_recipe` 页（41 个配方）与 4 个 `crafting` 页引用的配方全部存在；
  手册贴图 18 处引用全部命中。与 1.20.1 的差异只剩三处**有意为之**（棋盘分页、
  `world_spawn` 缺 allay 页、多一条 `make_custom_chatbubble_text`）。
- **三个御币附魔**（`impeding` / `speedy` / `enders_ender`）：登记在 `WEAPON` 分类下并
  各自重写 `canEnchant` 只认御币（`EnchantmentCategory#create` 是 Forge 补丁，枚举无法扩展）。
  另加 `mixin/EnchantmentHelperMixin`：原版附魔台候选列表只读 `category.canEnchant`，
  该 mixin 在 `RETURN` 处剔掉「附魔自己不同意」的条目。实测 `sword[tlm=false]`、
  `book[tlm=true]`（书本特例保留 → 村民交易照常）；代价是御币放进附魔台刷不出来。
- **坐船钓鱼**：`TaskFishing#createBrainTasks` 改回上游的 `MaidFindSitTask`，
  抛钩仍在 RIDE_WORK；临时的 `MaidFishingCastTask`（岸钓绕行）已删除。

- **坐垫换肤系统**：`AbstractModelGui` 已泛型化为 `<T extends LivingEntity, E extends IModelInfo>`
  （与上游一致），配套新增 `IModelInfo`；`CustomModelPack` / `SkinGuiNumber` 一并泛型化，
  `ChairModelRegistry.ChairPack` 删除、改用共用的 `CustomModelPack<ChairModelInfo>`。
  新增 `client/screen/ChairModelGui` + 两个包 `network/OpenChairGuiPacket`（S2C 开界面，
  由服务端在 `EntityChair#interact` 里发起）与 `network/ChairModelPacket`（C2S 写四个同步字段，
  含 `CHAIR_CHANGE_MODEL`/创造模式判定、`ejectPassengers`、`change_chair_model` 判据）。

### 仍然要真人验证的
1. 模型切换器界面（见下一小节）。
2. 附魔的实际**战斗效果**：给御币附上三个附魔，看弹幕是否变快 / 命中是否施加缓慢 / 能否打末影人。
   附魔台本身已验证（`gohei` 在 lvl 12/20/25/30/35/50 分别刷出 impeding、enders_ender、speedy，
   剑恒为空），剩下的是弹幕链路的运行时表现。
3. 钓鱼：放一条船在水边，`/tlm task <maid> fishing`，看她是否「坐进船 → 抛钩」。
   **服务端已验证**：`[Futo] fishing ... -> cast at ...`，船与坐垫两条路都通，
   且钩子会持续存活（`Pos` 逐次变化）。若客户端看不到浮标，看日志里这两行：
   `[Futo] ride`（活动是否为 `tlm_ride_work`）与 `[Futo] fishing`（抛钩/缺水/缺竿/超范围）。
   **主手、副手都认**（见 `util/FishingRodUtil`）：上游只看主手，副手放竿会静默地什么都不做。

### 还没做的一件事
**两个界面的人工实测。** 坐垫换肤（蹲下右键坐垫 → 翻页 / 点格子 / 缩略图居中）与
模型切换器界面（右击方块 → 加 / 删 / 排序模式 → 选模型与朝向）。
坐垫侧的服务端链路已用 RCON 验过（四个同步字段可写可读、`ModelId` 落盘正确），
代码侧的数据包线程模型与服务端复核都已就位，风险只在 UI 布局。

模型切换器还要确认两件事：关界面后红石脉冲仍能切换；「手持物品右击女仆」把 tooltip 从「未绑定」变成「已绑定」。
---
## 2. 省 token 的操作约定（上个会话烧掉几十块换来的）
1. **一个对话只做一个特性**，做完就 commit。对话越长，每一轮要重发的上下文越多；
   新对话 + 干净工作区 = 每轮只带当前特性，成本能低一个数量级。
2. **不要整份读大文件**。`EntityMaid.java`（3300 行）、两份 `lang`、`README.md` 都是雷区。
   用 `grep -n` 定位，`sed -n 'A,Bp'` 取窗口。
3. **终端输出先落盘再筛**：`cmd > /tmp/x.log 2>&1; tail -20 /tmp/x.log`。
   一次几百行回显会原样进上下文，而且会打掉后续所有缓存命中。
4. **批量探索交给搜索子代理**，别让几十个无关搜索结果污染主上下文。
5. 需要「等构建」时轮询产物（`grep -q 'BUILD SUCCESSFUL' /tmp/x.log`），**别 sleep**。
---
## 3. 本仓库改文件的硬约束（踩过的坑）
- **`insert_edit_into_file` 对 `README.md` / 两份 `lang` 这类大文件不可靠**：
  实测会「把要替换的段落再插一遍」或整份回显。改用 Python 脚本做精确替换
  （`io.open(..., newline='')` 保留 CRLF），写后立刻 `git diff --stat` 自查。
- **`sed -i` 会吃掉 CRLF**（MSYS 的 sed 按文本模式处理），一次 `sed -i` 就能把 3342 行
  全算成改动。要改就得用 Python，或者 `git checkout --` 回滚重来。
- **写完中文立刻查 U+FFFD**：`create_file` 与长 heredoc 都出现过个别汉字变成替换符，
  `python -c` 里用 `'\ufffd' in s` 断言，或直接跑 `check-encoding.ps1`。
- 开发进程清理只用 `port/kill-dev-run.ps1`（它按 loom 的 `launch.cfg` 路径匹配，
  不会误伤启动器里的游戏）；服务端优先 `python port/rcon.py "stop"` 优雅关闭。
- 起 `runServer` 前先确认没有上次遗留的服务端占着 `run/world/session.lock`
  （现象是 `Failed to start the minecraft server` + `IOException`，
  但 RCON 端口其实还通 —— 那说明旧服务端还活着）。
