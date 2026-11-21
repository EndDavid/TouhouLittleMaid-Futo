# 交接单（HANDOFF）
> **用法**：新开对话后，第一条消息只写「读 HANDOFF.md，然后继续」。
> **不要整份读 `README.md`**（3400+ 行，光读一遍就几十万 token）。需要细节时按小节
> `grep -n "^### " README.md` 找到行号，再 `sed -n 'A,Bp' README.md` 取那一段。
---
## 1. 当前状态：模型切换器已落地并提交
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
### 还没做的一件事
**界面的人工实测。** 需要真人：右击方块开界面 → 加 / 删 / 排序模式 → 选模型与朝向 →
关界面后红石脉冲仍能切换；以及「手持物品右击女仆」把 tooltip 从「未绑定」变成「已绑定」。
代码侧的数据包线程模型与服务端复核已经就位（见 README 那一节末尾），风险只在 UI 布局。
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
