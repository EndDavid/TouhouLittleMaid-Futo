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

### AI 聊天（本轮，B 方案：文本链路 + 最小气泡层）

`ai` 包 24 个文件 + 4 个网络包 + 2 个界面 + 1 个按键事件 + 1 条命令。

- **服务端链路**：`AvailableSites`（yml 站点表，只有填了 `api_key` 的才进表）/
  `SettingReader`（角色设定，扫 `config/…/settings` 8 层 + jar 的 `assets/<domain>/settings` 1 层 +
  玩家 `tlm_custom_pack`）/ `PapiReplacer`（16 个占位符）/ `Service`（HttpClient + 拼 prompt）/
  `MaidAIChatManager`（回调回主线程 + 解析 `ResponseChat` + 气泡/聊天栏）。
- **气泡层（只做 AI 需要的那一半）**：`ChatText` / `ChatTextType` / `MaidChatBubbles`
  （三槽位 + `EntityDataSerializer`，在 `InitEntities` 静态块里注册）/ `ChatBubbleManger`
  （槽位选择、过期清理、内部提示、AI 回复）/ `client/renderer/ai/ChatBubbleRenderer`（只画文本）。
- **接入点**：`EntityMaid` 加 `aiChatManager` 字段 + `CHAT_BUBBLE` 同步数据 + NBT 读写 + 5 个 getter；
  `MaidRenderer#render` 尾部画气泡；`GeneralConfig` 接 `AIConfig`；
  `EnterServerEvent` 登录时同步站点表；`TouhouLittleMaidFuto` / `…Client` 注册包。
- **新增依赖**：`libs/snakeyaml-2.4.jar`（从上游 Forge jar 的 `META-INF/jarjar` 解出，
  `implementation files(...)`）。**编辑 build.gradle 时把 `modLocalRuntime files("libs/fiber-0.23.0-2.jar")`
  误删过一次，导致 Patchouli 入口点 `NoClassDefFoundError: …fiber…ValueSerializer` —— 已恢复。**

**后续修正（同一轮）**：原先用 `CAN_CHAT_MAID_IDS`（模型必须有 settings 文件）过滤按 T 的行为，
这是 1.18.2 上游的老做法。1.20.1 是**所有女仆都能开界面**，设定为空时第一次说话自动生成一份
（`ai.AutoGenSettingEnabled`，默认 true；实现见 `AutoGenSetting` + `SettingReader#saveGeneratedSetting`，
同名文件不覆盖）。已按 1.20.1 改掉，三个 `ai.touhou_little_maid.chat.llm.*` 提示键也补齐了。

四道闸全绿；`runServer` 实测：`AI chat sites: 1 chat / 2 tts`、`loaded 6 character settings`、
八项 `MaidAIChatData` NBT 读写通、`/tlm ai_chat reload` 通。

**未做**：~~TTS（16 文件）~~（本轮已补，见下一节）/ STT（3 文件）/ Cloth 的 `GlobalAIIntegration` 与 `PartAIIntegration`
（改成手写 `MaidAISettingScreen`）/ 气泡的随机台本与图标 / `/tlm ai_chat` 的调试子命令。
**后续修正（同一轮）**：气泡渲染原来只写 `vertex → color → uv`，而 `RenderType.text` 的顶点格式是 `POSITION_COLOR_TEX_LIGHTMAP`，缺 `uv2` 会让客户端在 `BufferBuilder#endVertex` 抛 `Not filled all elements of the vertex` —— 现象就是「一发消息就崩」（`run/crash-reports/crash-2026-11-22_20.19.15-client.txt`）。已补 `uv2(LightTexture.FULL_BRIGHT)`，并把布局按上游重新对齐（左右两条各让开 5、箭头按 0/1/2 列、去掉多余的 `max(1, width/20)`）。


### AI 语音（TTS，本轮）

上游 `ai/service/tts` 的 16 个文件已全部移植，加上客户端的播放链路（4 个类 + 1 个 util
+ 2 个网络包 + 2 个 mixin）。

- **服务端**：`TTSRequest` / `TTSClient<T>` / `TTSFactory`（按 `api_type` 造客户端与请求体）
  + `fishaudio/`（9 个：client / 6 个请求小类 / request / callback）
  + `gptsovits/`（3 个）。`Service` 补上 `TTS_HTTP_CLIENT` / `getTtsClient` / `getTtsRequest`。
- **编排**：`MaidAIChatManager#onShowChatSync` 解析出 `chatText` / `ttsText` 后，
  若 `TTS_ENABLED` 且有可用 TTS 站点就走 `tts(...)`；`api_type: system` 走本地旁白，
  其余走 HTTP。**任何失败都回退到只显示气泡**，语音不会吞掉文字。
- **网络**：`TTSAudioToClientPacket`（16 格内广播，`maidId + byte[]`）/
  `TTSSystemAudioToClientPacket`（只发给主人，一段文本）。
- **客户端**：`client/sound/data/OpusAudioStream`（Opus → PCM）、`MaidAISoundInstance`
  （`EntityBoundSoundInstance` 子类）、`client/sound/OggReader`（识别 Vorbis / Opus）、
  `client/sound/SystemNarrator`、`util/OpusDecoderUtil`。
- **两个 mixin**：`client/SoundEngineMixin` 把 `SoundEngine#play` 里的
  `SoundBufferLibrary#getStream` 换成「先问实例」——因为 Forge 的
  `SoundInstance#getStream` 补丁在 Fabric 1.18.2 不存在；
  `client/NarratorChatListenerAccessor` 用 `@Accessor` 取 `narrator` 字段（同样是没有 AT）。
- **新增依赖**：`libs/concentus-1.0.2.jar`、`libs/vorbis-java-core-0.8.jar`，
  从上游 Forge jar 的 `META-INF/jarjar` 解出，`implementation files(...)`。
  `InitSounds.MAID_AI_CHAT` 注册 `maid.ai_chat`（`sounds.json` 里指向静音 ogg 且
  `stream: true`，只是让引擎走流式分支的占位）。

**验证**：`build` = BUILD SUCCESSFUL；`check-encoding.ps1` = `FFFD_CHARS=0`；
`runClient` 到主菜单出现 `Sound engine started` 且 `run/crash-reports` 为空 ——
`SoundEngineMixin` 的 `defaultRequire: 1` 意味着没打上会直接崩，所以这条日志等于「mixin 生效」。
**未验证**：真实 TTS 合成与播放（需要 key / 本地 GPT-SoVITS）、系统旁白是否出声、音质。

### 气泡样式按 1.20.1 重写（同一轮，真人反馈后）

真机截图暴露了三个问题，都出在「沿用 1.18.2 那套 3x2 小方格」上：

1. **不换行**：长回复被拉成一条横贯屏幕的白条；
2. **坐下时太高**：碰撞箱不变，但模型蹲下去了，气泡顶在头发上；
3. **贴图是旧的**：1.20.1 换成了 `textures/entity/chat_bubble/type2.png`。

`client/renderer/ai/ChatBubbleRenderer` 因此整体重写（321 行）：

- 贴图 `type2.png` / `type1.png` 从 TLMO 1.20.1 jar 复制到
  `assets/touhou_little_maid/textures/entity/chat_bubble/`（256x256，气泡主体在左上
  48x24，8 px 九宫格边距，三个 16x16 尾巴在 v=24 那一行）；
- 背景改九宫格拉伸（四角原样 / 四边单向 / 中间双向），尺寸随文字走；
- 文字用 `font.split` 折行：居中 200 px、左右两列 120 px；每行 9 px，
  气泡高 = 行数 x 9 + 上下各 5 px；
- 布局改为 1.20.1 的「右、左、右、左」交替两列向下堆叠（间距 16 px），
  奇数时最后一条居中对齐到较长那一列的底部；
- `isMaidInSittingPose()` 时基线下移 0.25 格；睡着时下移 1.2 格（= 旧的 48 px，
  保证改造前后底边位置一致）。

调试用命令 `/tlm ai_chat say <targets> <text>`：不走 HTTP，直接往女仆头顶塞气泡，
用来验收换行 / 堆叠 / 坐姿偏移 / 九宫格。`runServer` + RCON 实测通过
（`chat bubble sent to 1 maid(s)`），`runClient` 连进去后两条长文本都没有崩溃、
`run/crash-reports` 为空。

**仍需真人确认**：气泡的**视觉效果**（尾巴位置、文字与边框的间距、坐下时的落点）。
自动检查只能证明「不崩」，证明不了「好看」。

### DeepSeek 400 修复（同一轮，真人实测后）

实测日志里出现：

```
[Futo] AI chat POST https://api.deepseek.com/chat/completions
HTTP 400 Prompt must contain the word 'json' in some form to use 'response_format' of type 'json_object'
```

两个根因，都修了：

1. **`Service#getChatCompletion` 的最后一条 user 指令里没有 `json` 字样。**
   DeepSeek 在 `response_format: json_object` 下强制要求 prompt 里出现这个词。
   上游靠角色设定模板的「输出格式为 JSON 格式：`${output_json_format}`」满足它，
   玩家自写 / 自动生成的人设不一定有。已在那条指令后面补
   「只返回一个 json 对象（json_object），形如 {"chat_text":…,"tts_text":…}」。
2. **`AutoGenSetting` 把整段 JSON 当成 YAML 正文写盘。** 上游 1.20.1 的
   `AutoGenSettingCallback` 只取 `ResponseChat#getChatText()`。已补 `extractSettingBody`：
   先 GSON 解析取 `chat_text`，失败时手工从 `"chat_text":"` 取到结尾（应对截断），
   再不行才退回 `stripCodeFence`。

顺带修掉截断本身：`ChatCompletion#maxTokens` 默认 256，生成人设必被切断，
`AutoGenSetting.build` 现在显式 `maxTokens(1024)`。

**注意**：`run/config/touhou_little_maid/settings/touhou_little_maid_cirno_maid.yml` 里存的
是旧逻辑写坏的半截 JSON，已删除；下次说话会按新逻辑重新生成（同名文件不覆盖）。

### 构建修复（本轮开头）

上一轮留下的 4 个脏文件里，`ai/service/chat/openai/ChatClient.java` 被删掉了
`import ...TouhouLittleMaid;` 但仍在用 `TouhouLittleMaid.LOGGER`，导致 `compileJava` 失败。
已用 `git checkout --` 复原该 import（另三个只是文件末尾换行）。

### 气泡贴图与「未命名女仆的名字」（本轮 2026-11-23）

**一、气泡背景用错了那张图。** 上一轮把九宫格换成 1.20.1 的 `type1.png`（白底 + 粗黑边），
但 1.20.1 的 AI 回复走的是 `TextChatBubbleData.type2(...)`（白底 + 淡蓝边），
`addLLMChatText` / `addTextChatBubble` / `AutoGenSettingCallback` / `addThinkingText`
四处全是 `TYPE_2`；`TYPE_1` 只给台本气泡等少数地方。因此真人截图里仍是「旧版粗黑边」。
现在 `ChatBubbleRenderer` 用 `BACKGROUND = TYPE_2`，`TYPE_1` 仍保留常量（留给台本气泡那一轮）。

**二、未命名女仆显示 `Maid` 而不是模型译名。** 根因是没覆写 `EntityMaid#getTypeName()`：
原版回落到实体类型名。上游的做法是从服务端模型池取 `MaidModelInfo#getName()`，
本移植版没有那一层，于是新增 `entity/info/ModelDisplayNames`：直接读模组 jar 里的
`assets/<ns>/maid_model.json`，返回 `Component`（JSON 里是 `{model.xxx.name}`，
必须留到客户端翻译），查不到时退回上游同款默认键；结果用 `ConcurrentHashMap` 缓存。
`EntityMaid#getTypeName()` 覆写后，聊天栏 `<>`、名牌、墓碑、AI 人设的名字一起变正确。

**三、名字与语言一样必须由客户端带上服务端。** `SendUserChatPacket` 载荷从
`maidId + 文本 + 语言` 变成再追加 `name`；`RegenerateAISettingPacket` 同理。
原因：服务端没有玩家的语言文件，`{model.xxx.name}` 在服务端 `getString()` 出来只是键名
（RCON 实测 `Summoned new model.touhou_little_maid.cirno.name`）。
`AutoGenSetting.build` 不再自己 `maid.getName().getString()`，改用客户端送来的 `name`，
为空才退回模型 id 的可读形式。等价于 1.20.1 的 `ChatClientInfo(language, name, description)`。

**验证**：`./gradlew.bat build` BUILD SUCCESSFUL；三闸全绿
（`SCANNED_FILES=1487 BAD_FILES=0 FFFD_CHARS=0`、`entity 11/11 + block entity 11/13 ALL OK`、
`entries=54 categories=3 problems=0`）。`runServer` + RCON：
`summon ... {ModelId:"touhou_little_maid:cirno"}` 回显 `Summoned new model.touhou_little_maid.cirno.name`
（= `getTypeName()` 已生效，之前会是 `Maid`），`tlm ai_chat say` 回 `chat bubble sent to 1 maid(s)`。
**未验证**：客户端里气泡的实际观感（淡蓝边 / 尾巴位置）、聊天栏 `<>` 里中文译名的实际显示 ——
需要真人重启客户端后看一眼（服务端 RCON 只能看到键名）。
另：194 条内置模型里 193 条有 `zh_cn` 名字，唯一例外是彩蛋模型 `moesumika`，
它在模型包里本来就没有 `name` / `desc` 的中文键（上游同样如此）。

---

### 三个 AI 界面重做（本轮 2026-11-23 续）

**问题**：聊天主界面左下两个按钮打开的界面与 1.20.1 差太远，玩家看不出按钮做什么；
六个图标按钮没有悬浮提示（1.20.1 每个都有）；历史界面把模型原始 JSON 直接画出来。

**改动**

1. `FlatColorButton` 补 `setTooltips(String) / setTooltips(List)` 与 `renderToolTip`。
   1.18.2 的 `AbstractWidget#render` **不会**自己画提示（已用 `javap -c` 确认），
   因此每个界面在 `render` 末尾统一遍历 `children()` 调 `renderToolTip`。
2. `AIChatScreen` 六个按钮全部挂上上游语言键的提示；字形/语义重新对齐 1.20.1：
   左三 `≡` 历史 / `✎` 角色设定 / `⚙` 设置，右三 `Aa` 语种 / `♪` 语音 / `✦` 大语言。
   （上一版把 `⚙` 与 `✎` 对调了，这是「功能不明」的直接原因。）
3. `AIHistoryScreen` 按 `HistoryAIChatScreen` + `HistoryChatWidget` 重写：
   双侧气泡九宫格 + 尾巴 + 头像（女仆用 1.20.1 的 32×32 头像、玩家用皮肤头部 8×8 放大）、
   「x小时前」、右侧压缩摘要面板、清除（`ConfirmScreen`）/ 返回。
   贴图 `textures/gui/maid_history_chat.png` 直接从 1.20.1 jar 复制（未改一像素）。
   助手消息先 `ResponseChat` 解出 `chat_text` 再显示。
4. `AISettingEditScreen` 按 `SettingEditScreen` 重写：主人称呼（`EditBox`）+
   自定义设定（手写 `MultiLineEditBox`，折行/滚动/光标/Ctrl+ACVX/4096 字数统计）+
   右侧「导出设定 / 保存 / 保存并退出 / 返回」+「设定已保存」正弦淡出提示。
   「导出设定」改为复制 yml 文本到剪贴板（不引入 TinyFileDialogs 原生库）。
5. 新增 `AISettingBodyScreen`（原名 `AISettingEditScreen` 的只读正文界面）：
   显示 `settings/<模型>.yml` 正文 + 「重新生成人设」「复制台本」。
   上一版这个界面就是「角色设定」，本轮把它降为二级页面，从角色设定界面进入。
6. 新增 `ClearMaidAIChatHistoryPacket`（C2S，服务端复核「是主人」）；
   `SyncMaidAIHistoryPacket` 加 `gameTime` 字段、改为按旧→新发送、收到后按 entityId 找到女仆再开界面。

**1.18.2 兼容点（踩过的）**

- `enableScissor/disableScissor` 不在 `GuiComponent` 里：只能用 `RenderSystem`，且坐标是
  「帧缓冲像素 + 左下角原点」，宽度要乘 GUI 缩放。公式抄自 `ClientMaidTooltip` / `AbstractMaidScreen`。
- `SkinManager` 没有 `getInsecureSkinLocation`：改用 `AbstractClientPlayer#getSkinTextureLocation()`。
- `GuiComponent#blit` **不会**自己绑贴图，必须先 `RenderSystem.setShaderTexture`。
- 语言键：`gui.touhou_little_maid.button.maid_ai_chat_config.*`（22 条，含 3 条自造提示）
  与 `ai.touhou_little_maid.chat.setting.*.tip` 已写入 `zh_cn.json` / `en_us.json`。

**验证**：`./gradlew.bat build` BUILD SUCCESSFUL；`check-encoding.ps1` `SCANNED_FILES=1491 BAD_FILES=0 FFFD_CHARS=0`；
`check-renderers.ps1` `ALL OK`；`validate-book.ps1` `entries=54 problems=0`；
`runClient` 到主菜单出现 `Sound engine started`，`run/crash-reports` 为空（已用 `port/kill-dev-run.ps1` 停掉）。
**未验证**：三个界面的实际观感与交互（气泡对齐、悬浮提示位置、多行框输入、清除确认）—— 需要真人点一遍。

---

### 聊天气泡全量移植（本轮 2026-11-23 续二）

**起点**：AI 模块已够用，用户要求「各个时候会触发的消息弹窗全量移植」——
即 1.18.2 上游的气泡层里除 AI 之外的全部触发点。

**新增/重写**

| 文件 | 作用 |
|---|---|
| `entity/chatbubble/ChatBubbleInfo` | 台本 pojo（`bg` + `text.main/special/other`）+ `decorate` 合并 |
| `entity/chatbubble/ChatBubbleScripts` | 台本加载器：读 jar 内 `default_chat_bubble.json` 与模型 `maid_model.json` 的 `chat_bubble`，按模型缓存；`getBackground` 把旧网格图映射成 `type2.png` |
| `entity/chatbubble/MaidScriptBookManager` | 书与笔台本（安装 / 清空 / 复制 / NBT） |
| `entity/chatbubble/ChatBubbleManger` | 补齐 `tick` 三路随机、`hurt` 监听、饰品 `other`、`addDebugChatText` |
| `client/renderer/ai/IconTextureSizes` | 读 PNG IHDR 拿图标帧数（替代上游 `SizeTexture`） |
| `client/renderer/ai/ChatBubbleRenderer` | 支持图标气泡（60×20 一格、每 2 tick 换帧）+ 按模型取底图 |
| `EntityMaid` | `scriptBookManager` 字段 / NBT / `getScriptBookManager` / `mobInteract` 里的 `applyScriptBook` |
| `MaidRenderer` | 渲染前检查全局与单只开关 |
| `MaidFeedAnimalTask` / `MaidFindSitTask` / `MaidHomeMealTask` | 三处任务失败气泡 |
| `MaidCommand` | 新增 `/tlm ai_chat bubble <targets> <id>` |
| `TouhouLittleMaidFuto` / `...Client` | 注册 `ChatBubbleManger.init()`；资源重载清台本与图标缓存 |

**踩到的坑（都在编译或首跑时暴露）**

1. `ResourceLocation` 没有默认 Gson 适配器 → 台本 `"bg"` 抛 `Expected BEGIN_OBJECT but was STRING`。
   必须 `registerTypeAdapter(ResourceLocation.class, …)`。
2. 1.18.2 的 `ResourceManager#getResource` 返回 `Resource`（找不到抛 IOException），不是 1.19+ 的 `Optional`。
3. `Biome#getTemperature(BlockPos)` 在 1.18.2 是 private（上游靠 AT），改用 `getBaseTemperature()`。
4. 调试命令里判断「有没有台本」不能用对象身份比 `MaidChatBubbles.DEFAULT`（过期清理会写回新对象），
   改成看「气泡数量有没有增加」。

**验证**：`build` SUCCESSFUL；四道闸全绿（`SCANNED_FILES=1495 BAD_FILES=0 FFFD_CHARS=0`、
`ALL OK`、`entries=54 problems=0`）；`runClient` 到主菜单无崩溃报告。
`runServer` + RCON 实测（`PYTHONIOENCODING=utf-8`，否则控制台 GBK 打印颜文字会报错）：
`idle` → `icon …idle4.png`、`hurt` → `˃ʍ˂`、`morning` → `{chat_bubble.touhou_little_maid.morning.1}`、
`work` → `( ≧Д≦)`、`mute` → `icon …mute1.png`、`nosuch` → `1 without script`。
服务端日志里 9 条 ERROR 全是原版「No data fixer registered」常规提示。

**未验证**：游戏内真实观感（气泡位置、图标动画帧率、三槽位堆叠）、书与笔交互手感、
受伤 / 天气 / 作息是否真的命中（需要真人或长时间运行）。

**与 1.20.1 的差异**（README 已写）：随机台词数据仍用 1.18.2 的台本格式，
1.20.1 的 `kaomoji.json` 与 `maid_emoji/*.png` 本仓库没有；热判断换成基础温度。

---
### 图标气泡的横条纹闪烁（同日补修）

用户反馈：表情气泡随视角转动时出现横条纹式闪烁。

**根因**：`ChatBubbleRenderer#vertex` 当时硬编码 z=0.2，底图九宫格与图标四边形
共面。共面且都写深度、LEQUAL 下，两个大小不同的四边形逐像素插值出的
深度有末位差异 → 一半像素通过、一半不通过 → 横条纹，且随相机移动。
text 没事是因为 `Font#drawInBatch` 的顶点 z 固定是 0，天然和底图拉开了 0.2。

**修法**：把 z 提成常量并按层分配 —— `Z_BACKGROUND=0.2`、`Z_ARROW=0.1`、`Z_CONTENT=0.0`（图标，
文字本就是 0）。`vertex()` 与 `drawSprite()` 都加了 z 参数。
这个位姿下 z 越小越靠近镜头（证据：z=0 的文字能盖住 z=0.2 的底图），
所以层与层之间至少留 0.1（=0.0025 格，肉眼看不出错位），不要改成 0.199 这种。

验证：build SUCCESSFUL、四道闸全绿、`runClient` 到主菜单无崩溃。
观感需真人确认（我看不到屏幕）。

### 进度（成就）系统：54 条全量移植（本轮 2026-11-24）

用户要求「各个时候可能会触发的消息弹窗全量移植」之后，接的是进度系统。

**搬了什么**：上游 **Forge 1.18.2** jar 里的 54 个 `data/.../advancements/**` 逐字复制
（`base/` 10、`maid_base/` 25、`favorability/` 8、`challenge/` 9 = 52 条新搬，根目录那 2 条是本仓库原有的），
外加 `textures/advancements/backgrounds/stone.png` 与 `loot_tables/advancement/{cake,power_point}.json`。
**不需要**按 1.20.1 改写配方 id —— 本仓库配方目录就是 `recipes/altar/`，
所以 `touhou_little_maid:altar/spawn_box` 天然对得上（1.20.1 才是 `altar_recipe/spawn_box`）。

**新类**（6 个）：

| 文件 | 作用 |
|---|---|
| `item/ItemAdvancementIcon` | 八件「只用于图标」的物品，统一名字、不进物品栏 |
| `item/ItemEntityPlaceholder` | 「生成？？？」，NBT 里存 `RecipeId`，右键可生成配方产物 |
| `client/model/EntityPlaceholderIconModel` | 占位物品的零厚度扁平图标（同 `PicnicBasketIconModel`） |
| `client/renderer/item/EntityPlaceholderItemRenderer` | 按配方文件名换贴图，找不到退回 `entity_placeholder.png` |
| `entity/passive/MaidKillRecordManager` | 四个击杀计数（总 / 史莱姆 / 凋灵 / 末影龙），写进 NBT `KillRecord` |
| `event/MaidKillRecordEvent` | `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` → 交给上面那个 |

**补齐 23 处触发点**（之前 `TriggerType` 里 48 个常量只有 25 个真有人打）：
`EntityMaid`（驯服 / 雷劈 / 装备槽 / 牛奶 / 附魔金苹果 / 玻璃瓶 / 鞍 / 背包）、
`FavorabilityManager`、`MaidFarmPlantTask`、`MaidFeedAnimalTask`、`MaidFeedOwnerTask`、
`MaidFishingHook`、`EntityPowerPoint`、`MaidConfigPacket`（换模型 + 换任务）。

**顺带修掉一个真 bug**：`EntityMaid#thunderHit` 之前只置了个 `struckByLightning` 位，
**没有**上游那句「首次被雷劈 +20 生命上限」。补上之后 `maid_100_healthy`（要 100 生命）
才凑得出来。同时把 `StruckByLightning` 写进 NBT —— 否则 `refreshAttributes()`
每次读档都会把上限重建回 20，彩蛋凭空消失。

**踩到的坑（都值得记）**：

1. `ServerLivingEntityEvents` **不存在**于本工程的依赖里。fabric-entity-events 在这里解析到
   **1.4.x**（1.18.2 的 fabric-api 0.76），`ServerLivingEntityEvents` 是 1.6.0+ 才加进来的。
   等价物是 `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`（1.4.x 就有），
   签名 `(ServerLevel, Entity killer, LivingEntity killed)`。
2. `ResourceManager#getResource` 在 1.18.2 返回 `Resource` 并抛 `IOException`，
   **没有** `Optional<Resource>` 形式 —— 只能用 `hasResource(...)`。这个坑之前在气泡那轮踩过一次。
3. 用 Python 批量改 Java 文件时必须**探测每个文件自己的行尾**再加新行：
   本仓库 `MaidFeedAnimalTask.java` 是 LF，同目录另外两个任务是 CRLF。
4. 用 `grep` 验证 `TriggerType.X` 是否真有触发点时，会被**注释里的示例**骗到
   （`MaidEventTrigger` 的 javadoc 就写着 `trigger(player, TriggerType.SWITCH_TASK)`，
   而实际没人打）。要按「非注释行」或按文件白名单判。

**仍然点不亮的一条**：`base/change_maid_sound`。它要「每只女仆各自的音效包 id」这个子系统
（上游 `SetMaidSoundIdMessage` + `client/sound/CustomSoundLoader` + `MaidSoundPackGui`），
本仓库只把模型的 `use_sound_pack_id` 读进了 `MaidModelInfo`，没同步到实体、也没界面。
等做「女仆音效包」那轮再补一行判据（`TriggerType.CHANGE_MAID_SOUND` 常量已就位）。

**验证**：build SUCCESSFUL；四道闸全绿；打分包里 54 个 JSON；
`runServer` 日志 `Loaded 1195 advancements` 且**零条** `Parsing error loading custom advancement`
（判据 id 写错只会在这一行暴露）。
`/advancement grant` 在无玩家时只回 `No player was found`（选择器先于 id 解析），
因此**空服时没法**用 RCON 验证进度存在性；**有玩家在线时就可以**（见下一节）。

### 进度标签页不出现：1.18.2 的渐进式同步（同一轮补修，真人反馈驱动）

**现象**：玩家反馈进度页里只有「农牧业 / 冒险 / Minecraft」三个原版标签。

**根因（已用字节码复核）**：1.18.2 的进度是**渐进式同步**的，
`PlayerAdvancements` 只把 `visible` 集合里的条目发给客户端；
而 `visible` 的填充入口 `ensureAllVisible()` **只从「已完成的进度」出发**，
`shouldBeVisible()` 又要求「自己或子树有点亮过」。客户端那边，
标签页只由「收到的且 `parent == null`」的进度创建 —— 
所以**没点亮任何一条时，模组 4 个 root 都不会同步，标签页就不存在**。

**复现证据**：`port/sim_adv_visibility.py` 用 2026-11-24 那次会话的玩家进度
照抄 1.18.2 的算法，算出可见集正好 **214** 条，与客户端日志 `Loaded 214 advancements` 逐字吻合；
可见 root 正好是 story/adventure/husbandry 三个 —— 与玩家看到的三个标签完全对应。

**为什么上游没有**：1.20.1 的 `AdvancementVisibilityEvaluator` 把没有 parent 的节点判为 `ALWAYS_SHOW`，
配合 `VISIBILITY_DEPTH = 2` 等价于「root 往下两代可见」，且从 root 出发遍历。
数据文件本身与上游逐字相同，所以这是**版本行为差异**，不是搬错。

**修法**：新增 `mixin/PlayerAdvancementsMixin`（已登记进 `touhou_little_maid.mixins.json` 的 `mixins` 数组）：
- `shouldBeVisible` HEAD 注入：沿 parent 链向上 2 层内摸到**本模组** root 就返回 true；
- `load` TAIL 注入：从本模组每个 root 出发，按 2 层深度写入 `visible` / `visibilityChanged`。

**必须限定命名空间**：原版 `minecraft:recipes/root` 有 display 却永远完不成，
若对所有命名空间套 `ALWAYS_SHOW`，进度页会凭空多出「配方」标签。

**验证**：修复前预测 63 条之前先验了 23 条基线；实跑 `runServer` + `runClient -PjoinServer` 得到
`Loaded 63 advancements`（23 原版 + 40 条深度 0~2 且带 display 的模组进度），与预测逐字吻合；
`/reload` 后再来一次仍是 63；零 mixin 失败；RCON 授予两条后 `save-all`，
存档只多出这 4 条（没有因为「可见」而膨胀）。

**仍未做**：真人打开进度页确认那 4 个标签的观感（自动化只验证到「4 个 root 进了同步集」，
而 `AdvancementsScreen.init` → `setListener` → `onAddAdvancementRoot` 的链路已用反汇编逐行确认）。

### 史莱姆换成灵梦油库里：材质按上游 1.20.1 重绘版（本轮 2026-11-24）

**发现**：baseline 里只有两张**没被任何代码引用**的旧贴图（`textures/entity/yukkuri.png`、
`marisa_yukkuri.png`），配置项 `VanillaConfig.REPLACE_SLIME_MODEL` 只定义没接线 ——
「史莱姆替换层」整个没移植。所以本轮不是"改一张图"，而是**把替换层接上 + 用上游新材质**。

**上游改了什么**：1.20.1 把史莱姆贴图换成 `textures/bedrock/entity/reimu_yukkuri.png`（1917 B），
与 1.18.2 的 `textures/entity/yukkuri.png`（2520 B）**同为 64x64、UV 布局不变**，
但 `port/CmpPng.java`（JDK ImageIO）逐像素比出 **577/4096 = 14.1% 不同**，是真的重画了。

**为什么用手写网格**：1.20.1 的 `SimpleBedrockModel` 实体模型加载器本仓库没有。
但 reimu 的 bedrock json 与 1.18.2 的 `LayerDefinition` **逐盒等价**（UV 全是 (0,18)/(0,0)/(38,0)/
(48,14)/(50,20)/(48,0)/(54,35)，尺寸与 inflate 也一致），所以直接照抄上游 1.18.2 的网格 + 贴 1.20.1 的新图。

**新增**：`client/model/EntityYukkuriModel`、`client/renderer/entity/EntityYukkuriSlimeRender`、
`assets/touhou_little_maid/textures/bedrock/entity/reimu_yukkuri.png`、`VanillaConfig#replaceSlimeModel()`；
注册在 `TouhouLittleMaidFutoClient` 里紧跟经验球那一段（先 `registerModelLayer` 再
`EntityRendererRegistry.register(EntityType.SLIME, ...)`，覆盖原版表项）。

**验证**：build SUCCESSFUL；四道闸全绿；jar 里两个 class + 贴图都在，class 常量池路径与贴图路径逐字相同；
`runServer` + `runClient -PjoinServer` 后用 RCON summon 史莱姆，
客户端零 `Missing texture` 警告、零渲染异常（史莱姆还能把玩家打死，说明确实在 tick）。

**没做**：视觉验收（要真人看）。旧的两张图留在原地不删（上游也留在 legacy_pack 里）。
岩浆怪那一半见下一节。

### 岩浆怪换成魔理沙油库里：直接吃上游 1.20.1 的基岩模型（同一轮续）

**为什么不能照抄手写网格**：史莱姆那两版是「逐盒等价 + 只重画贴图」，岩浆怪不是 ——
贴图 64x64 → **128x128**，UV 全换（(0,18) → (45,16)、(46,14) → (45,33)、(0,49) → (0,0)…），
骨骼 9 根 → 14 个 bone。手写网格等于把 JSON 手工重排一遍，抄错一个 uv 就是贴图错位且极难定位。

**做法**：新增 `client/resource/BedrockEntityModels`（读 `models/bedrock/entity/<name>.json`），
复用仓库已有的 geckolib 管线（`Converter` → `RawGeometryTree` → `GeoBuilder` → `AnimatedGeoModel`），
渲染走 `GeoBlockRenderer.render(...)`。与 `BedrockBlockModels` 的唯一区别就是目录。

**坐标不用换算**（很容易想歪，记下来）：`GeoCube` 已经把 texel 除以 16 并翻转 x 轴；
**cube 的 `origin` 是绝对坐标，不是相对 bone pivot**（pivot 只用于绕点旋转）；
y 轴两边同向。实测量出 y ∈ [−0.13, 1.09]、x ∈ [−0.44, 0.44]、z ∈ [−0.43, 0.45]，底面就在脚底，不需要平移。

**必须继承 `EntityRenderer` 而不是 `MobRenderer`**：`LivingEntityRenderer#render`
会套 `scale(-1,-1,1)` + `translate(0,-1.501,0)`（给 y 向下的原版 EntityModel 用），
而基岩几何是 y 向上。想在 `renderToBuffer` 里抵消它也不行 —— 那个变换依赖 `scale()`
算出的压缩比（每 tick 随 squish 变）。`EntityRenderer#render` 拿到的栈只有「平移到脚下」，正好合用；
女仆渲染器（`GeoReplacedEntityRenderer`）也是这么绕开的。阴影不受影响：
javap 确认 1.18.2 的 `EntityRenderDispatcher.render` 自己按 `shadowRadius` 画，不在 render 内部。

**行为**：`getBlockLightLevel` 恒 15（自发光，同上游）；`shadowRadius = 0.25*size`；
squish 形变照抄原版 `SlimeRenderer#scale`；开关关闭 / 模型缺失时**退回原版** `MagmaCubeRenderer`。

**验证**：build SUCCESSFUL；四道闸全绿；jar 里两个 class + JSON + 贴图都在，class 常量池路径逐字一致；
客户端启动日志 `[Futo] Entity bedrock model marisa_yukkuri loaded: 13 bones`（资源重载预热时就成功）；
RCON 召唤 Size=3 与 Size=1 各一只，客户端零 missing texture、零渲染异常。

**没做**：视觉验收（要真人看）。关闭开关的退回路径没单独跑（直接委托，风险低）。

### 配方补全 + REI 联动（同一轮续）

**先盘点**：把 69 件注册物品与 47 条配方的产出逐一对照，再对比两份上游 jar，
结论是「真正缺的只有 2 条配方」：

- `entity_id_copy`（1.18.2 与 1.20.1 都有）—— 资产早就拷进来了（模型 / 贴图 / 三条语言键全在），
  缺的只是**物品注册**与右键行为，于是那条「皮革 + 纸」的配方永远无从落地
  （没物品的配方会被数据包丢弃）。
- `altar/snack_cabinet`（**只有 1.20.1 有**）—— 1.18.2 的 jar 里连类都没有，
  所以这不是「补配方」而是「补一整块方块系统」。

其余 25 件没有配方的物品，上游本来也没有（刷怪蛋 / 进度图标 / 魂符 / 残局道具 /
好感度工具 / 手办 / 照片 / P 点 / 神社 / 替身地藏 / 模型切换器 / 生成？？？）。

**`entity_id_copy` 的右键行为**：上游是 Forge 的 `PlayerInteractEvent.EntityInteract`，只在客户端执行；
Fabric 换成 `UseEntityCallback`（与 `MaidClientInteractEvents` 同一套路），
复制 `Registry.ENTITY_TYPE.getKey(...)` 到剪贴板 + 聊天栏提示。
**返回 SUCCESS 而不是 PASS** —— 否则对着女仆右键会顺手打开女仆界面。

**`snack_cabinet` 的方块系统**：`BlockSnackCabinet`（FACING + TYPE，ENTITYBLOCK_ANIMATED）
+ `TileEntitySnackCabinet`（27 格，RandomizableContainerBlockEntity，带开合音效）
+ `TileEntitySnackCabinetRenderer`（走 BedrockBlockModels，与野餐垫同一条管线）
+ `SnackCabinetType`（登记进 ChestManager，让隙间认它）。
另外从上游逐字节拷了 blockstates / models/block / loot_table / 配方。

**界面白捡**：27 格 = 原版六行箱子，`createMenu` 返回 `ChestMenu.sixRows`，
客户端不用注册 Screen（原版 MenuScreens 早把 GENERIC_9x3 绑到 ChestScreen 了）。

**踩到的 1.18.2 差异**：`ContainerOpenersCounter#incrementOpeners` 在 1.18.2 只有 4 个参数，
第 5 个 `viewDistance` 是 1.19 才加的 —— 照抄 1.20.1 会编译不过。

**还发现一个标签缺口**：配方要 `c:glass_panes`，仓库原来没有这个标签（原版 1.18.2 也没有），
于是补了 `data/c/tags/items/glass_panes.json`（列全 17 种玻璃板）。
手册校验器原本为此报 `ALTAR EMPTY c: TAG`，补完 `problems` 从 2 归零。

### REI 联动

移植上游 `compat/rei/` 四个类 + `util/JERIUtil`（源码在 `port/upstream`，1.18.2 与 1.20.1 的 jar 都有这套）。

**Fabric 没有 `@REIPluginClient` 注解** —— 那是 Forge 版扫类路径用的。
Fabric 走入口点：把 `MaidREIClientPlugin` 登记到 `fabric.mod.json` 的 `"rei_client"`，
REI 启动时自己来取；没装 REI 就没人取，所以 **REI 是可选的，不写进 depends**。

**三个 jar 必须显式上 classpath**：REI 8.4.778 的 fabric.mod.json **只声明了 minecraft**，
但它的核心类直接引用 `dev.architectury.platform.*`（84 个类）与 `me.shedaniel.math.*`，
这两个包既不在 REI jar 里也没在依赖里声明。所以补了 `architectury-4.12.94-fabric.jar`
与 `basic-math-0.6.1.jar`（都从工作区现成 jar 拷进 `libs/`，钉死版本）。

**验证**：

- build SUCCESSFUL；四道闸全绿（`SCANNED_FILES=1582`、`block entity types = 14 renderers = 12`、
  `entries=54 problems=0`）。
- **REI 真的认到了**：日志
  `[REI] Registered plugin provider MaidREIClientPlugin [touhou_little_maid] for REIClientPlugin`，
  且 `REIClientPlugin starting main-reload` 的插件列表里有它。
- **物品与配方真的在**（RCON 有玩家时）：`give ... entity_id_copy` / `give ... snack_cabinet` 都成功；
  `recipe give Dev touhou_little_maid:entity_id_copy` → `Unlocked 1 recipes`。
- **零食柜能放**：`setblock ... snack_cabinet` → `Changed the block`，
  `data get block` 读到 `Items: []` 与正确 id；
  模型日志 `[Futo] Block model snack_cabinet loaded: 3 bones, bbox y[0.000..2.000]`。
- 客户端零 missing texture、零渲染异常。

**没做**：

- 视觉验收（REI 画布观感、零食柜外观）。
- 上游 REI 的 `registerScreens` / `registerTransferHandlers` 两段：
  依赖 `CraftingTableBackpackContainerScreen` / `FurnaceBackpackContainerScreen`，
  本移植版还没有那两套背包界面。等界面做出来补 4 行注册即可。
- 零食柜 `TYPE`（空/满/半）的「蹲下点顶面往上叠半高柜」传染逻辑：
  只影响外观分层，不影响 27 格容器与隙间目标功能。

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
- **`insert_edit_into_file` 对 `EntityMaid.java`（3600 行）也是雷区**：本轮用它插一段字段声明，
  结果它把整个文件删掉 587 行（`git diff --stat` 显示 `-587`），编译错误全是 `找不到符号`。
  **恢复方式**：`git checkout -- <file>` 回到 HEAD，再用 Python 脚本按锚点精确插入，
  写完立刻 `git diff --stat <file>` —— 只应看到几十行 `+`，没有 `-`。
  规则：**超过 1000 行的文件一律只用 Python 脚本改**。
- **判断 CRLF 再读写**：`raw = open(p,'rb').read(); crlf = b'\r\n' in raw`，
  改完 `t.replace('\n','\r\n') if crlf else t`。`EntityMaid.java` / `README.md` / `HANDOFF.md`
  都是 CRLF；`TouhouLittleMaidFutoClient.java` 在库里是 LF。
- **终端回显里的 `touhoullittlemaid`（三个 l）是真故障**：编辑工具回写时出现过 3 处，
  编译会报 `程序包不存在`。用 `grep -c touhoullittlemaid <file>` 自查。
- **两份 `lang/*.json` 是「混合行尾」（约 458 行 CRLF + 1400 行 LF）**，
  `crlf = b'\r\n' in raw` 这种判断对它们**必然是 True**，于是「统一转 CRLF 再写回」
  会把整份文件算成改动（本轮 `git diff --stat` 出现了 2055 / 2017 行）。
  正确做法：只在**目标行所在位置按字节插入**，EOL 取那一行自己的：
  `j = base.index(b'\n', i) + 1; eol = b'\r\n' if base[j-2:j] == b'\r\n' else b'\n'`，
  并用 `git show <上一轮提交>:<path>` 取 base 字节，改完 `git diff --stat <上一轮提交> -- <path>`
  必须只有 `3 +++` 这种量级。
- **气泡背景必须给光照图坐标（`uv2`）**：`RenderType.text` 的格式是 `POSITION_COLOR_TEX_LIGHTMAP`（元素顺序 position → color → uv0 → uv2，已用 javap 从映射后的 1.18.2 jar 核实），少写一个就在 `endVertex` 抛 `Not filled all elements of the vertex`。`ChatBubbleRenderer#vertex` 末尾的 `uv2(LightTexture.FULL_BRIGHT)` 不是冗余，别删。上游靠 Forge AT 自建 `POSITION_TEX` 渲染类型，本仓库没有 AT，所以只能走 `RenderType.text`。
