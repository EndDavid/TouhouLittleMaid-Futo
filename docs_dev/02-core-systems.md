# 核心系统

> 移植过程笔记。摘自根目录 `README.md`（2026-11-25 拆分），返回索引：[`docs_dev/README.md`](README.md)。

## Brain 行为与工作模式

上游 1.18.2 **不用 Goal**，全部是 `Activity` + `MemoryModuleType` + `SensorType` + `Behavior` +
`Schedule`；`goalSelector` 只保留 `FloatGoal`（游泳行为未移植，落水靠它兜底）。

### 20 个工作模式

| 类别 | 任务 |
|---|---|
| 无依赖 | `idle` / `attack` / `danmaku_attack` |
| 远程 | `ranged_attack`（弓）/ `crossbow_attack`（弩）/ `trident_attack`（三叉戟） |
| 背包 | `shears` / `milk` / `honey` / `torch` / `feed` / `feed_animal` |
| 农耕 | `farm` / `sugar_cane` / `melon` / `cocoa` / `grass` / `snow` |
| 其它 | `extinguishing`（灭火）/ `fishing`（钓鱼） |

**远程类的走位**用 `MaidRangedWalkToTarget`，**不是**原版的
`SetWalkTargetFromAttackTargetIfTargetOutOfReach`。后者只在进入近战距离（约 2 格）才停止写
`WALK_TARGET`，而 `MaidAttackStrafingTask` 的前置条件是 `WALK_TARGET` 必须**不存在** ——
用原版会让女仆一路贴脸，同时把走位任务永久挡住。新实现是
「`canSee` 且距离 < 工作半径 × 0.65 就清掉 `WALK_TARGET`」。近战 `attack` 仍用原版那个。

### 底层设施

- `MaidPathFindingBFS` + `CenterOffsetBlockPosSet` + `ICachedEvaluator`：一次 BFS、多次惰性查询。
  农耕 / 插火把每 tick 要扫上百个候选方块，逐个 `createPath` 会卡服。
  **`canPathReach(pos)` 的语义是「脚能站在 `pos` 或 `pos.above()`」** ——
  所以实心的西瓜 / 南瓜永远不可达，`MaidFarmMoveTask#checkPathReach` 必须退化到四邻格判定。
- `MaidMoveToBlockTask`（抽象基类）：螺旋扫描 + 可达性判定 + 主人距离约束 + 最近工作点缓存。
- `EntityMaid` 方块操作：`canPlaceBlock` / `canDestroyBlock` / `destroyBlock` /
  `dropResourcesToMaidInv`（掉落直接进背包）/ `placeItemBlock` / `getBrainSearchPos` /
  `getDestroyProgress`（重写 `Player#getDestroyProgress` 的公式，因为女仆不是 `Player`）。
- `MaidBlockPlaceContext`：1.18.2 的 `BlockPlaceContext(Level, Player, …)` 是 `protected`。

### 弹幕攻击

- `EntityDanmaku` **继承 `ThrowableProjectile`**（三个构造函数都是 `protected`，可用原版投掷物管线）。
  注意 `Projectile` 本身是包私有构造 —— 鱼钩因此只能 `extends Entity`。
- 形态按「周围**同类型**可攻击且可见目标数」三档：≤1 单发（有多重射击 → 3 发 15° 扇）/
  ≤5 八发 60° 扇 / >5 三十二发 120° 扇。谓词是上游 1.20.1 的 `enemyEntityTest`：
  `canAttack && sameType && canSee`。少了 `sameType` 会导致「只打一只也放一圈」。
- 渲染：`textures/entity/danmaku.png` 416×128 = 13 列（颜色）× 4 行（形状），每块 32×32，
  起址 `(32 × color, 32 × type)`；billboard 四边形 + `itemEntityTranslucentCull`。
- 友伤保护顺序（①③④⑤ 同上游 1.20.1，②为本移植版加强）：
  ① 女仆弹幕不伤玩家 → ② 不伤**任何**女仆 → ③ 同主驯服生物不互伤 →
  ④ `MAID_RANGED_ATTACK_IGNORE` 名单 → ⑤ 命中。
  ② 是必须的：上游只比 `ownerUUID`，而它可能为 `null`（未驯服 / Owner 丢失）时会漏判。
- 撞到**没有碰撞体积**的方块不消失；存活 200 tick 后自动消失。

#### 三个自定义附魔

| 附魔 id | 名称 | 等级 | 作用 |
|---|---|---|---|
| `impeding` | 阻碍 | I–IV | 命中后施加缓慢，时长 `(20 + level * 10) * 20` tick |
| `speedy` | 极速 | I–II | 初速 ×`(level + 1)`，即两倍 / 三倍 |
| `enders_ender` | 末影终结者 | I | 置位 `hurtEnderman`，弹幕能伤末影人 / 末影龙 |

三者都**只作用于御币**：`TaskDanmakuAttack#performRangedAttack` 读等级，
经 `DanmakuShoot` 传给 `EntityDanmaku`，在 `onHitEntity` 里结算。

**没有 `GOHEI` 附魔分类的原因。** 上游写的是
`EnchantmentCategory.create("gohei", item -> item instanceof ItemHakureiGohei)`，
而 `EnchantmentCategory#create` 是 **Forge 给原版类打的补丁**，1.18.2 原版没有。
而且 `EnchantmentCategory` 是枚举：常量在类初始化时定死，外部既不能新增常量，
也没法继承它（枚举构造函数是包私有的）。所以改成 TLMO Fabric 1.20.1 的做法 ——
把三个附魔登记在 `WEAPON` 分类下，各自重写 `canEnchant(ItemStack)` 只认御币。

**最后一块补丁：附魔台。** 原版 `EnchantmentHelper#getAvailableEnchantmentResults`
读的是 `category.canEnchant(item)`，**不调** `Enchantment#canEnchant`。
因为它读的是分类，候选列表**两个方向**都是错的，`mixin/EnchantmentHelperMixin` 在 `RETURN` 处各补一刀：

1. **减法**：分类允许、但附魔自己不同意的条目剔除 → 剑 / 斧不会再被列出这三个附魔；
2. **加法**：分类拒绝、但附魔自己同意的条目补上 → 御币放进附魔台能刷出它们。

加法只对「分类判否 且 `canEnchant` 判真」生效。原版附魔的 `canEnchant` 就是
`category.canEnchant(stack.getItem())`，两个条件不可能一假一真，所以对**任何原版附魔
都是空操作**。等级选取循环逐行对齐原版字节码（`96 → 103 → 115 → 126 → 137/158`）：
从 `getMaxLevel()` 往下试到 `getMinLevel()`，第一个落在
`[getMinCost(lv), getMaxCost(lv)]` 区间内的等级胜出。书本特例单独跳过，避免重复。

实测（临时 debug 类读的真实返回值，验完已删；`[]` 里只列本模组的三个附魔）：

```
lvl=5   gohei=[]                                          sword=[]
lvl=12  gohei=[impeding=1]                                sword=[]
lvl=20  gohei=[impeding=2 enders_ender=1]                 sword=[]
lvl=25  gohei=[speedy=1 enders_ender=1]                   sword=[]
lvl=30  gohei=[impeding=3 speedy=1 enders_ender=1]        sword=[]
lvl=35  gohei=[impeding=4 speedy=2 enders_ender=1]        sword=[]
lvl=50  gohei=[enders_ender=1]                            sword=[]
```

剑那一列恒为空，说明减法是精确的。`lvl=5 / 12 / 25 …` 的跳变来自 `getMaxCost`
默认值 `getMinCost + 5`：每个附魔只在自己那段花费区间里出现，这与上游一致
（上游也是这套 `getMinCost/getMaxCost`，只是它还有 `GOHEI` 分类，
而本移植版靠分类之外的 `canEnchant` 补上同样的效果）。

## 物品栏 / GUI / 命令

### 女仆背包（7 种）

| 物品 id | 可用格数 | 备注 |
|---|---|---|
| `maid_backpack_small` | 12 | |
| `maid_backpack_middle` | 24 | |
| `maid_backpack_big` | 36（全格） | |
| `crafting_table_backpack` | 18 | 工作台功能待做 |
| `ender_chest_backpack` | 6（不扩展） | 末影箱功能待做 |
| `furnace_backpack` | 18 | 烧炼数据待移植 |
| `tank_backpack` | 18 | 流体数据待移植 |

穿脱规则同上游：手拿背包右键 = 装上（旧背包掉给玩家）；手拿剪刀右键 = 脱下（剪刀 -1 耐久）；
换装后 20 tick 冷却。这个冷却是**必需的** —— 原版对主手和副手各调一次 `mobInteract`，
没有冷却会「来回换装」。换小背包时超出容量的物品按上游规则掉到地上。

### 主界面

- 贴图取自上游 Forge 1.18.2 发布 jar，尺寸 256×256，槽位坐标与上游逐一对应。
- **版式约定（踩过坑）**：`renderBg` 里的 `PoseStack` **没有**被平移到 GUI 原点 → 纹理用
  `leftPos + x` 绝对坐标；`renderLabels` 里的**已经**平移过 → 文字用相对坐标。
- **压暗层与 Tooltip 都须手动调用**：1.18.2 的 `AbstractContainerScreen#render` 既不调
  `renderBackground` 也不调 `renderTooltip(PoseStack,int,int)`（字节码确认）。顺序固定为
  压暗 → `super.render` → 槽位提示 → 按钮提示。
- **左侧任务列表**：面板 `(0,0) 92×251`、每页 12 条、行高 19。一次性建完所有按钮，
  翻页只切 `visible`（上游是每页 `init()` 重建，要求 `clearWidgets` 必被调用，更脆）。
  当前任务的图标与名称必须在 `super.render` **之后**画，否则被开关按钮底图盖掉。
- **空槽提示图标**（上游 `Slot#getNoItemIcon`）：护甲 2×2 用原版 `item/empty_armor_slot_*`、
  副手用 `empty_armor_slot_shield`，这五个 sprite 本来就在原版 `blocks` 图集里，走原版逻辑即可。
  主手 `slot/empty_mainhand_slot.png` 与背包第 6 格 `slot/empty_back_show_slot.png` 是模组贴图，
  1.18.2 的 Fabric 没有 `TextureStitchEvent`、也没有图集定义 JSON（1.19.3 才有），进不了图集 →
  由 `MaidMainScreen#drawManualSlotHints` 在 `renderBg` 里按贴图路径直接画。
  1.18.2 的 `AbstractContainerScreen#renderSlot` 是 **private**，不能重写来补画。
- **只读同步值、只写意图**：`homeModeEnable` / `pickup` / `task` 都是 `SynchedEntityData`。
  客户端点了按钮只发包，服务端校验 `isOwnedByPlayer` 后写。

### 顶部三页签

**页签切换 = 服务端换菜单**，不是在同一个界面里换内容。上游 `MaidTabs` 点一下只发
`ToggleTabMessage(entityId, tabId)`，服务端按 tab 调 `EntityMaid#openMaidGui(player, tab)`
重新开容器。因此每个页签必须有**独立的 `MenuType` + 独立 Screen**：

| tab | `TabIndex` | 菜单 | Screen | 底图 |
|---|---|---|---|---|
| 主界面 | `MAIN = 0` | `maid_main` | `MaidMainScreen` | `maid_gui_main.png` |
| 任务设置 | `TASK_CONFIG = 1` | `task_config_default` / `task_config_attack` | `DefaultTaskConfigScreen` / `AttackTaskConfigScreen` | `default_task_config.png` / `attack_task_config.png` |
| 女仆配置 | `MAID_CONFIG = 2` | `maid_config` | `MaidConfigScreen` | `maid_gui_config.png` |

- 三个 Screen 共用基类 `AbstractMaidScreen`（立绘 / 四条状态条 / 页签 / 左侧任务列表 /
  玩家物品栏 / 空槽提示），子类只覆写 `renderAddition` 与 `initAdditionWidgets`。
- **页签按钮（`MaidTabButton`，24×26）**：图标在 `maid_gui_side.png` 的 `(uv, 47)` 16×16、
  偏移 `(4,6)`；「选中」态画 `(uv, 21)` 的按下底图。选中沿用上游的取巧写法 ——
  把 `active` 置 `false`，既画按下态又不响应点击 / 不弹提示。
- **切页签时不发 `ClientboundContainerClosePacket`**：`ServerPlayer#openMenu` 第一句是
  `if (containerMenu != inventoryMenu) closeContainer()`，而 `closeContainer` 会先发关闭包。
  客户端收到后会先拆掉界面（`grabMouse` 把光标重置到窗口中心），紧接着的
  `ClientboundOpenScreenPacket` 又把界面建回来（`releaseMouse` 把光标吸到屏幕正中）——
  表现就是「点页签时鼠标跳到画面正中央、界面像是重新打开了一次」。
  上游走 Forge 的 `NetworkHooks.openGui`，它只调 `doCloseContainer()`（拆旧菜单、不发包）。
  现在 `EntityMaid#openMaidGui` 里先显式 `doCloseContainer()` 再 `openMenu()`，客户端只收到一次打开包。
- **服务端复核 tab 取值**：网络包是玩家可控的，不能直接把整数透传去选菜单。

### 女仆配置界面的 8 行

`MaidConfigButton`，164×13，步距 13，起点 `leftPos+86, topPos+52`：

| 行 | 字段 | 值 |
|---|---|---|
| 显示背包 | `BackpackShow` | 开启 / 关闭 |
| 显示背部物品 | `BackItemShow` | 开启 / 关闭 |
| 显示聊天气泡 | `ChatBubbleShow` | 开启 / 关闭 |
| 女仆声音频率 | `SoundFreq` | 0%~100%，左右箭头各 ±10% |
| 拾取类型 | `PickupType` | 物品 / 经验 / 全部 |
| 能否开门 | `OpenDoor` | 开启 / 关闭 |
| 能否开栅栏门 | `OpenFenceGate` | 开启 / 关闭 |
| 能否主动爬梯 | `ActiveClimbing` | 开启 / 关闭 |

点击热区是分开的：只有 `x+120..x+130`（左箭头）与 `x+154..x+164`（右箭头）触发，
中间一大片是死区 —— 点标题文字不会意外改值。
这 8 项全是 `SynchedEntityData`（`MaidSubConfig` 集中管理，NBT 存在 `MaidSubConfig` 子标签，
字段名与上游逐字对应），客户端只发意图，服务端 `isOwnedByPlayer` 校验后写入。

**开门 / 开栅栏门 / 爬梯是真的接到寻路与移动上的**（只加开关不接线的 UI 等于没有功能）：

| 开关 | 接线点 | 做法 |
|---|---|---|
| 能否开门 | `MaidNodeEvaluator#getCachedBlockType` | 关闭时把 `DOOR_WOOD_CLOSED` 降级成 `BLOCKED` |
| 能否开栅栏门 | 同上 | 同上（`FenceGateBlock`） |
| 开门动作 | `MaidInteractWithDoor`（CORE 行为） | 走过节点时开门 / 开栅栏门，走远且没同类在用时关上 |
| 能否主动爬梯 | `MaidNodeEvaluator#getNeighbors` | 开启时把梯子格当作可走节点，路径才会竖直上去 |
| 爬梯动作 | `EntityMaid#travel` → `applyClimbMotion` | 按「下一节点在上一格还是下一格」给竖直速度，并清零水平漂移 |

> 1.18.2 的寻路在算节点时走的是 `getCachedBlockType → getBlockPathType(10 参)`，
> **三参的 `getBlockPathType` 不参与实际寻路**，覆写它不生效。
> 原版只有 `Player` 会在 `aiStep` 里处理攀爬，所有 `Mob` 都不会主动上下梯子 ——
> 寻路认得梯子还不够，必须自己给竖直速度。

### 任务设置界面

上游 `MaidTaskConfigGui` 本身几乎是空的，内容由 Task 提供（`IMaidTask#getTaskConfigGuiProvider`）。
本移植版按「有没有实现 `IAttackTask`」二分：`attack` / `ranged_attack` / `crossbow_attack` /
`danmaku_attack` / `trident_attack` / `feed_animal` 走攻击名单页，其余走提示页。

**攻击名单（`AttackListData`）随菜单数据下发**，不单开通道：它只在打开该界面时用到，
而菜单数据本来就只发给打开界面的那一个玩家。编辑期间改的是菜单里那份副本，
**关闭界面时整体提交一次** —— 每改一条就发包会让服务端反复 `refreshBrain`，
每次都要停掉所有正在跑的 Brain 行为。
名单只覆盖默认判定：出现的生物按名单里的关系，没出现的仍走
`DefaultMonsterType.getMonsterType()`；空名单 = 完全按原版规则。读档时坏条目**逐条跳过**。

### 状态效果列表

界面左侧、任务列表之外的空白处列出女仆身上每个状态效果：`效果名 等级 剩余时间`，
正面绿色 / 负面红色 / 中立蓝色（上游 `drawEffectInfo` + `getPotionColor`）。
等级只在 II~X 级时显示；剩余时间走原版 `StringUtil.formatTickDuration`。
任务列表展开时这块不画 —— 两者位置完全重叠。

**为什么必须自己写网络包。** 原版只把**玩家自己**的状态效果同步到客户端
（`ClientPacketListener#handleUpdateMobEffect` 的接收方写死 `this.minecraft.player`）。
别的实体挂了什么效果，客户端永远收不到 —— 在客户端上 `maid.getActiveEffects()` 恒为空。

| 方向 | 常量 | 载荷 |
|---|---|---|
| C2S | `MaidEffectPacket.REQUEST` | 实体 id |
| S2C | `MaidEffectPacket.SYNC` | 实体 id + 条数 + 每条四元组 |

节奏同上游：界面打开期间 `containerTick` 每 20 tick 请求一次
（`AbstractContainerScreen#tick` 是 final，只有它会调 `containerTick`）。
是「拉」不是「推」—— 效果会自然到期、也会被药水 / 庭灯改写，靠推送太贵。
服务端两项复核：实体 id 确实是女仆，且 `isOwnedByPlayer(请求者)`。
客户端还挡掉 `size > 64` 的包，防止伪造的「20 亿条」打爆内存。

快照存在 `EntityMaid#effects`（`List<MaidEffectData>`），不在 Screen 上：
三个页签是三个独立 Screen，切页签会重建实例，挂在实体上才不会闪空白。
四元组只带 `descriptionId / amplifier / duration / category`，不发 `MobEffectInstance` 本体。

> 顺带补了一个原版缺失的语言键：上游对无限时长用 `effect.duration.infinite`，
> 但 1.18.2 的 15 个原版语言文件里**都没有**这个键（1.19 才加）。
> 已在 `assets/touhou_little_maid/lang/*.json` 补为 `∞`。

### `/tlm` 命令（权限等级 2）

| 命令 | 作用 |
|---|---|
| `/tlm task <targets> [id]` | 查看 / 设置工作模式 |
| `/tlm schedule <targets> [day\|night\|all]` | 查看 / 设置日程 |
| `/tlm home <targets> <true\|false>` | 居家模式 |
| `/tlm pickup <targets> <true\|false>` | 拾取总开关 |
| `/tlm pickup <targets> type <item\|xp\|all>` | 拾取类型 |
| `/tlm model <targets> ["<id>"]` | 查看 / 设置模型（可省略命名空间） |
| `/tlm backpack <targets> [<id>]` | 查看 / 设置背包类型 |
| `/tlm inv <targets> [clear [void]]` | 打印物品栏 / 清空（掉地上 / 直接销毁） |
| `/tlm info <targets>` | 诊断：tame / owner / task / sitting / home / 工作范围 / exp |
| `/tlm power <players> [set\|add\|min <0~5>]` | 查看 / 修改灵力 |
| `/tlm claim <targets>` | 重新认主 |

> **`/data merge` 改不动工作模式。** `MaidTask` / 日程 / 居家 / 拾取 / 模型都是 `SynchedEntityData`，
> NBT 里同名字段只在读存档时解析。请用 `/tlm`。

## 配置系统（Forge Config API Port）

上游 TLMO 1.20.1 就依赖它，本移植版直接复用 `net.minecraftforge.common.ForgeConfigSpec`，
配置代码按上游原样搬运。

### 依赖的四个 jar

| 位置 | jar | 作用 |
|---|---|---|
| `libs/` | `ForgeConfigAPIPort-v3.2.4-1.18.2-Fabric.jar` | `ForgeConfigSpec` / `ModLoadingContext` |
| `libs/` | `cloth-config-6.5.133-fabric.jar` | 配置界面（`suggests`） |
| `libs/` | `core-3.6.5.jar` / `toml-3.6.5.jar` | night-config，从 Port 的 `META-INF/jars/` 解出来 |

后两个**必须单独作为 `modImplementation`**：Loom 只对「正规模块坐标」展开 JiJ，
对 `files()` 本地文件不展开，否则启动报
`requires any version of com_electronwill_night-config_core, which is missing!`。
不能用 `compileOnly`（只有编译期可见），也不能用 `include`（只接受模块坐标）。

**同一条规则也适用于别处的 JiJ**：Patchouli 的 Fabric 版把 Fiber 作为
`META-INF/jars/fiber-0.23.0-2.jar` 嵌在里面，Loom 看不到，启动即
`NoClassDefFoundError: …fablabs/api/fiber/…ConfigType`。已把该 jar 解到 `libs/` 并加
`modLocalRuntime files(...)`。

### 配置类

首次启动生成 `run/config/touhou_little_maid-common.toml` 与
`saves/<world>/serverconfig/touhou_little_maid-server.toml`。

| 类 | 内容 |
|---|---|
| `config/GeneralConfig` | COMMON 聚合入口 |
| `config/subconfig/MaidConfig` | 主体，约 35 项（工作范围、射程、驯服物品、黑名单…） |
| `config/subconfig/MiscConfig` | 妖精 / 灵力 / 神社灯 / 稻草人 |
| `config/subconfig/VanillaConfig` | 图标替换开关（有效） |
| `config/subconfig/ChairConfig` / `RenderConfig` | 占位（子系统未移植） |
| `config/ServerConfig` | SERVER（下载包 URL、AI 耗时检测） |

`AIConfig` 未移植：依赖尚未移植的 `ai.manager.setting.AvailableSites`。

**不要直接调 `MAID_WORK_RANGE.get()`。** `ForgeConfigSpec.Value#get()` 在配置尚未加载时会抛
`IllegalStateException`，而女仆实体的静态初始化、渲染层读取都可能早于配置加载。
`MaidConfig` 提供 `workRange()` / `danmakuRange()` 等安全取值器，未加载时回退上游默认值。

已接线：`MAID_NON_HOME_RANGE`(8) / `BOW_RANGE`(48) / `CROSS_BOW_RANGE`(64) /
`DANMAKU_RANGE`(64) / `TRIDENT_RANGE`(48) / `FEED_ANIMAL_MAX_NUMBER`(50) /
驯服·诱惑·NTR 物品 / 背包黑名单 / 攻击与远程忽略名单 / `OWNER_MAX_MAID_NUM`。

## 声音

上游语音不随 jar 发布，而是由「声音包」提供：服务端拦截路径以 `maid` 开头的音效，
改发 `PlayMaidSoundMessage`，客户端用 `MaidSoundInstance` + `SoundCache` 直接从 ogg 取。
上游的自定义 SoundEngine 未移植，改成用原版机制等价实现：从上游 1.20.1 jar 的
`littlemaid_peco` 包提取 **332 个 ogg**（7.5 MB）到
`assets/touhou_little_maid/sounds/maid/{ai,mode,environment}/`，把 `sounds.json` 里每个键的
`sounds` 数组从「单个静音把位」改成**该键的全部变体**，由原版随机挑选。

| 键 | 变体 | 键 | 变体 |
|---|---|---|---|
| `maid.mode.idle` | **78** | `maid.ai.death` | 25 |
| `maid.mode.range_attack` | 29 | `maid.ai.find_target` | 18 |
| `maid.mode.torch` | 28 | `maid.ai.tamed` | 15 |
| `maid.mode.furnace` | 23 | `maid.ai.item_get` | 15 |
| `maid.environment.morning` | 23 | `maid.ai.hurt` | 13 |
| `maid.environment.night` | 18 | `maid.ai.hurt_fire` | 12 |
| `maid.mode.attack` | 14 | `maid.mode.farm` / `feed` | 6 / 15 |

剩下 11 个键（`shears` / `milk` / `feed_animal` / `extinguishing` / `snow` / `break` /
`brewing` / `danmaku_attack` / `game_win` / `game_lost` / `ai_chat`）在 peco 包里**没有音频**，
保持静音 —— 不是遗漏。

### 密度与并发：与上游完全一致（不做节流）

原版 `Mob#baseTick` 的触发是 `random.nextInt(1000) < ambientSoundTime`，
`resetAmbientSoundTime()` 把计数重置为 `-getAmbientSoundInterval()`（javap 确认过：
`if_icmpge` 跳过，也就是 `nextInt < ambientSoundTime` 才发声）。

女仆的真实间隔是 **120**，不是 `Mob` 的 80 —— `Animal#getAmbientSoundInterval()`
覆写成了 120（javap：`bipush 120`），而女仆走的是 `TamableAnimal → Animal` 这条链。
加上概率回爬的约 45 tick，期望间隔 ≈165 tick ≈ **8 秒**。

> 这个数字是实测出来的：临时在 `playAmbientSound()` 里打一行
> `getAmbientSoundInterval()` 跑 `runServer`，输出 `interval=120`。
> 此前文档写的 80 / 6 秒是错的 —— 只看了 `Mob` 没看 `Animal`。

**上游没有改这个节奏，本移植版现在也没有。** 逐个核对过：

| | 上游 1.18.2 | 上游 1.20.1 | 本移植版（现在） |
|---|---|---|---|
| 覆写 `getAmbientSoundInterval()`（SRG `m_8100_`） | 否 | 否 | **否** |
| 覆写 `playAmbientSound()` 做节流 | 否 | 否 | 否（只加频率判定） |
| 覆写 `getVoicePitch()`（SRG `m_6100_`） | 是：`1.0F + rand*0.1F` | 同 | 同 |

> 曾经的错误做法（已删除）：把 `getAmbientSoundInterval()` 改成 240、再加一条
> 「任意两只女仆的语音之间至少隔 60 tick」的全局节流。两条都是上游没有的发明，
> 单只就从约 6 秒拉长到约 14 秒、多只还会互相压制，现象就是「女仆太安静、
> 和上游不一样」。判断「上游到底怎么写的」只能靠 javap：
> `javap -p EntityMaid | grep m_8100_` 在两个上游 jar 里都是**空**。

### 频率配置接到播放路径上

上游的频率过滤是**按声音实例**做的（客户端 `PlaySoundEvent` 只拦 `MaidSoundInstance`），
拦的是**所有**女仆语音，不只是待机碎碎念。1.18.2 的字节码：

```
double f = maid.getConfigManager().getSoundFreq()
         * MaidConfig.GLOBAL_MAID_SOUND_FREQUENCY.get() / 100.0;
if (f < 1 && !maidSound.isTestSound() && Math.random() > f) event.setSound(null);
```

本移植版没有自定义 `SoundInstance`，把同一个判定提到**服务端发声处** ——
被拦掉时客户端连包都收不到，等价且更省带宽。判据与上游逐字对应：

```java
private boolean shouldPlayVoice() {
    double freq = MaidSubConfig.getSoundFreq(this)                 // 单只 0~1
                * MaidConfig.globalMaidSoundFrequency() / 100.0D;  // 全局 0~100
    return freq >= 1.0D || this.getRandom().nextDouble() <= freq;   // 上游：random > f 才丢
}
```

| 语音 | 接入方式 |
|---|---|
| 待机 / 工作模式环境音 | `playAmbientSound()` 里显式判 `shouldPlayVoice()` |
| 攻击模式 `maid.ai.find_target` 等 | 同上（都经 `IMaidTask#getAmbientSound` → `playAmbientSound`） |
| 捡到东西 `maid.ai.item_get` | `playMaidVoice(...)` |
| 被驯服 `maid.ai.tamed` | 同上 |
| 受伤 / 着火受伤 | `getHurtSound()` 在频率不通过时返回 `null` |
| 死亡 | `getDeathSound()` 同上 |

> 受伤 / 死亡用「返回 `null`」而不是重写 `playHurtSound`：
> `Mob#playHurtSound` 里先调了 private 的 `resetAmbientSoundTime()`，重写会把它一起丢掉，
> 女仆挨打后会立刻开始碎碎念。而 `LivingEntity#playHurtSound` 取到 `null` 本来就直接跳过。

**不走**这个判定的：方块放置声、挤奶声、弩的射击声、落座音效（环境 / 交互动静），
以及 `maid.ai.ai_chat`（上游的 `MaidAISoundInstance` 继承的是
`AbstractTickableSoundInstance`，不是 `MaidSoundInstance`，上游同样不过滤）。

坐下 / 起立音效照抄上游 `SwitchSittingEvent`：
`playSound(SoundEvents.ITEM_PICKUP, 0.2F, ((rand-rand)*0.7+1)*2)` ——
是原版拾取音效（不是 `maid.ai.item_get`），音调升一个八度。

配置值的写入链路（逐段核对过，报文顺序与服务端读取一致
`action, entityId, payload`）：`MaidConfigScreen` 左右箭头各 ±10% →
`MaidClientPacketSender#sendSoundFreq` → `MaidConfigPacket.SOUND_FREQ` →
`handleSoundFreq` → `MaidSubConfig#setSoundFreq`（夹到 0~1）→ 同步数据 → `shouldPlayVoice()`。

### 实测：节奏与频率门都真的生效

临时在 `playAmbientSound()` 里插一行
`[SNDTEST] uuid=... interval=... freq=... play=...`（测完即撤），
`runServer` + RCON 召唤三只女仆，世界内有：

```
[SNDTEST] uuid=29ce... interval=120 freq=0.25 play=false   <- 上一轮存档里的那只，跨重启保留
[SNDTEST] uuid=db05... interval=120 freq=0.0  play=false   <- SoundFreq 0.0：恒定静音
[SNDTEST] uuid=abcd... interval=120 freq=1.0  play=true    <- 默认 1.0：恒定放行
```

三件事同时被这一组日志证实：

1. **节奏是 120**（不是 80），与上游同链一致；
2. **频率门生效**：`0.0` 恒 false、`1.0` 恒 true，`0.25` 按概率出现；
3. **配置值跨存档保留**：`freq=0.25` 那只是上一轮用 `data merge` 写入、再
   `save-all flush` + 区块卸载重载后读回来仍是 0.25 的同一只，服务端重启后仍为 0.25。

用 `data` 命令做的往返验证（同一只女仆）：

```
summon ... {Invulnerable:1b,NoAI:1b}
data get    ... MaidSubConfig.SoundFreq   -> 1.0f        (默认)
data merge  ... {MaidSubConfig:{SoundFreq:0.25f}}
data get    ... MaidSubConfig.SoundFreq   -> 0.25f
save-all flush / forceload remove+add     (区块重载)
data get    ... MaidSubConfig.SoundFreq   -> 0.25f
```

**没验的**：听感。间隔变成约 8 秒、音调从 1.3 回到 1.0 是否「听起来和上游一样」，
仍然需要真人在客户端里待几分钟。

## 配置：Forge Config API Port 到底给了什么，我们用了多少

这一节是对「Forge 走的是 XX，Fabric 没这个特性」这句话的一次清算。答案是：
**配置这一块根本不存在「Fabric 没这个」** —— FCAP 把 Forge 的整套配置 API 原样搬了过来，
包括事件。问题出在我们自己身上。

### 先说结论：查出来三件事

| # | 问题 | 后果 |
|---|---|---|
| 1 | 自带了一份 `net.minecraftforge.fml.config.ModConfig`，与 FCAP 的同名类**重复** | 谁先被类加载器选中取决于加载顺序；选中我们的空壳时，FCAP 的 `ConfigTracker` 调 `getConfigData()` 会抛 `NoSuchMethodError` |
| 2 | `net.minecraftforge.fml.ModLoadingContext` 攒了一个队列交给 `FutoConfigRegistrar`，而后者方法体只有 `// TODO` | 用上游写法 `ModLoadingContext.get().registerConfig(...)` 注册的配置**静默消失** |
| 3 | 每个取值点都包了 `try/catch`，理由是「配置未加载时 `get()` 会抛 `IllegalStateException`」 | 这个前提是**错的**；而且它会连真正的异常（值类型不匹配、默认值非法）一起吞掉，玩家看到的是「配置怎么改都没用」却没有任何日志 |

### 1. 重复类

`net.minecraftforge` 这个包名是我们自己造的兼容层，而 FCAP 也用同一个包名 ——
两份 jar 里出现同一个全限定名时，Fabric 不会报错，只会由类加载顺序决定谁生效。
当时 `ModConfig` 正好两边都有：我们那份只有 `enum Type`（够编译），
FCAP 那份有 `getConfigData()`、`getFullPath()`、`acceptSyncedConfig()` 等一整套。

现在那份空壳已经删掉，`ModConfig` / `IConfigSpec` 全部来自 FCAP。
统计脚本（`comm -12`）确认构建产物与 FCAP 的 `net/minecraftforge/**` **零交集**。

> 这也解释了一个隐性风险：之所以之前没炸，是因为主入口走的是 FCAP 的
> `net.minecraftforge.api.ModLoadingContext`，那条路径不经过我们的空壳。
> 一旦有人把上游文件原样搬过来，就会踩到。

### 2. 注册路径

`net.minecraftforge.fml.ModLoadingContext` 现在只是一层转发：

```java
public void registerConfig(ModConfig.Type type, IConfigSpec<?> spec) {
    net.minecraftforge.api.ModLoadingContext.registerConfig(TouhouLittleMaid.MOD_ID, type, spec);
}
```

FCAP 的签名多一个 modId，本移植版只有这一个模组，直接填即可。
这样上游那一行可以**逐字保留**：

```java
ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GeneralConfig.init());
```

主入口也改成了这个写法。之前那种「主入口调 FCAP、其它地方调兼容层」的两套路径是最坏的组合：
一套能用、一套是死的，而看不出区别。

### 3. `ConfigValue#get()` 不会抛异常

反编译 FCAP 3.2.4 的 `ForgeConfigSpec$ConfigValue#get()`，第一段是：

```java
if (spec.childConfig == null) return defaultSupplier.get();
```

也就是说**配置未加载时返回的是定义时写的默认值**。Forge 1.18.2 自己的实现结构相同。
所以那十来个 `try/catch` 全是死代码。已经一并删除（`config/` 包里现在 `catch (` 出现 **0** 次），
注释也改成了实际的依据。

`MaidConfig.workRange()` 这类辅助方法保留了下来，但理由换成了对的：
**上游读的就是这些默认值**，把默认值写在调用点上，方便逐处对照。

### FCAP 提供而我们还空着的东西

FCAP 把 Forge 的配置**事件**也搬过来了，只是换成 Fabric 的事件总线：

```java
ModConfigEvent.LOADING   : Event<Loading>    → void onModConfigLoading(ModConfig)
ModConfigEvent.RELOADING : Event<Reloading>  → void onModConfigReloading(ModConfig)
```

上游在这个时机做两件事：

1. `MaidMealRegConfigEvent` —— 把「正则餐食黑名单」展开成物品 id 表。
   **不需要移植**：`MaidMealManager#isRegexBlockList` 改成查询时直接匹配，
   结果等价且省掉一次「遍历全部注册物品」。
2. `ClientPackDownloadEvent` —— 按 `ClientPackDownloadUrls` 下载客户端资源包。
   **尚未移植**（涉及 HTTP、解压、进度界面，与声音包同属「全部移植完再补」那一类）。

现在 `event/ConfigLifecycleEvents` 接上了这个事件，用途是**把「未移植」说出来**：
玩家填了 url 却什么也没发生时，日志里至少有一行字解释原因。
沉默是这里最糟的选择 —— 这项配置在本移植版里同样存在（配置可互换），
一个「填了没反应」的配置项比没有更难排查。

顺带确认了一件容易踩错的事：**`SERVER` 类型的配置不在 `config/`**，
而在 `<世界目录>/serverconfig/`（单人存档是 `saves/<name>/serverconfig/`）。
这和 Forge 完全一致。把手写的 toml 放进 `config/` 是读不到的 —— 我一开始就这么踩了一次。

### 运行时验证

不是靠读代码推断的，是从日志里读出来的：

```
[Futo] config ... phase=reload type=COMMON mod=touhou_little_maid file=touhou_little_maid-common.toml
[Futo] config ... phase=load   type=SERVER mod=touhou_little_maid file=touhou_little_maid-server.toml
ClientPackDownloadUrls is set (1 entry, load phase) but the client pack download has not been ported yet
```

第一行证明 COMMON 配置被 FCAP 加载并（因为文件里有错/缺项）触发了一次 reload；
第二行证明 SERVER 配置从 `serverconfig/` 读到了；
第三行证明我的监听器拿到的是**真实配置值**（`1 entry`，而不是默认的空数组）。

### 兼容层里还剩什么

清理后 `net/minecraftforge/**` 从 44 个类降到 43 个，删掉的都是「看着像 Forge、其实是空壳」的：

- `fml/config/ModConfig`（重复类，见上）
- `fml/FutoConfigRegistrar`（TODO 空壳）
- `fml/javafmlmod/FMLJavaModLoadingContext`、`fml/event/lifecycle/FMLCommonSetupEvent`、
  `fml/loading/LoadingModList`、`fml/FutoEventBuses`、`fml/event/config/ModConfigEvent`
  （零引用的生命周期桩）
- `common/loot/LootModifier`、`common/loot/GlobalLootModifierSerializer`
  以及 `ForgeRegistries` 里对应的两个字段（战利品走的是 Fabric 的 `LootTableEvents`，
  这条 Forge 路径从未被使用）

仍然保留的是**真的在做事**的那些：`DistExecutor` / `FMLEnvironment` / `EffectiveSide`
是真判断，`DeferredRegister` / `RegistryObject` 是真正的两段式注册映射，
`items/**` 是 Forge 物品栏语义的完整实现（隙间的 `InvWrapper` 也在这个包里）。

> 判据很简单：**零引用 + 方法体是空 / TODO** 才删。有引用的一律保留，
> 哪怕实现只有一行 —— 它至少是真的在返回某个值。

## 界面的「世界压暗层」：1.18.2 与 1.20 的一个静默差异
隙间（无线 IO）的界面打开时，世界不压暗 —— 底图直接叠在亮着的世界上。这不是漏画，
而是**照抄了一句从 1.20 带来的注释**：
```java
// 上游 renderBg 里先调 renderBackground 再画底图，
// 而 1.18.2 的 AbstractContainerScreen#render 已经调过一次 —— 会重复画一遍模糊底。
// 这里省掉那一句。
```
这句是**反的**。`AbstractContainerScreen#render` 里补 `renderBackground` 是 **1.19** 的改动；
1.18.2 的 `render` 只做 `renderBg` + `renderLabels` + 槽位与物品，**一次压暗都不画**。
所以「省掉那一句」不是去重，是把它彻底删掉了。
修法只有一行，放进 `render` 的最前面：
```java
@Override
public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
    this.renderBackground(poseStack);   // 必须早于 super.render，否则会盖住底图
    super.render(poseStack, mouseX, mouseY, partialTicks);
    this.renderTooltip(poseStack, mouseX, mouseY);
}
```
### 为什么这个 bug 值得写进 README
它属于**「注释里写了错误的前提」**这一类。代码本身没有任何可疑之处（少调一次
`renderBackground` 看起来很合理），编译不报错，运行不抛异常，测试也测不到 ——
只有真人打开界面看一眼才发现。同类的还有前几轮的 `Direction#toYRot()` 记错
（把 NORTH 当成 0 而不是 180）。
所以这一轮把整个 `client/` 下的容器界面过了一遍：
```
grep -rln "extends AbstractContainerScreen" src/main/java   | while read f; do grep -q "renderBackground" "$f" || echo "MISSING: $f"; done
```
现在输出为空。`AbstractMaidScreen` 早就手补过（女仆主界面/配置界面都继承它），
`WirelessIOContainerGui` 是唯一漏的那个 —— 因为只有它是**独立**于
`AbstractMaidScreen` 的容器界面。

## 日程表按钮：从「只有白班」到能切夜班

上一轮读者实测反馈：女仆界面左下角那块「WORK」下面的横条是空的（1.20.1 那里有一个
太阳/云的小图），而且**没有任何办法把女仆改成夜班** —— 于是「白天休息」这条路径
永远走不到，只挂在 IDLE 上的野餐垫、娱乐方块也就永远看不到。

### 缺的是哪一块

作息<b>机制</b>早就移植好了：`MaidSchedule`（DAY / NIGHT / ALL）、
`InitEntities` 里三张 `ScheduleBuilder`、`MaidBrain#registerSchedule`，
以及 `EntityMaid#setSchedule`（写同步数据 + `refreshBrain` 重建活动表）。
**缺的只是「让玩家点得到」这一层**：

| 缺的东西 | 现在补上的位置 |
| --- | --- |
| 界面上的按钮 | `client/screen/widget/ScheduleButton`（61x13，`leftPos+9, topPos+187`） |
| 客户端发包 | `MaidClientPacketSender#sendSchedule` |
| 动作码与载荷 | `MaidConfigPacket.SCHEDULE = 16`，载荷是 `MaidSchedule` 的枚举 |
| 服务端处理 | `MaidConfigPacket#handleSchedule` |

贴图不用改：`maid_gui_button.png` 与 1.20.1 的那份**逐字节相同**，
`(82, 43 + 14 * ordinal)` 三个 61x13 状态图都在（解码后实测 793/793、780/793、793/793
个不透明像素），所以按钮自己带着「白班 / 夜班 / 全天」的图案，不需要另外画文字。

### 服务端那四步，少一步就有怪现象

`handleSchedule` 与上游 `MaidConfigMessage#handle` 里那一段逐句对应：

1. `setSchedule` —— 写 `SynchedEntityData` 并 `refreshBrain`（作息换了，活动表也要换）；
2. `getSchedulePos().restrictTo(maid)` —— 把活动范围挪到新作息对应的点；
3. 开着居家模式时给她一个走过去的目标，否则她会杵到下一次日程刷新；
4. 触发 `switch_schedule` 判据。

第 2 步是最容易漏的：只改 `MaidSchedule` 不动 `SchedulePos`，
活动范围仍按旧作息算 —— 表现为「切了夜班，白天照样在外面干活」。

### 三张作息表（供对照实测）
### 顺带修掉的一个真 bug：读档后大脑仍按白班跑

`readAdditionalSaveData` 里原本直接写同步数据：

```java
this.entityData.set(SCHEDULE_MODE, MaidSchedule.valueOf(tag.getString("MaidScheduleMode")));
```

**这是错的。** 大脑（含它的 `Schedule`）是在 `Mob` 的构造函数里按「默认 DAY」建好的 ——
`Mob#makeBrain` 早于读档执行，所以只改 `SynchedEntityData` 不会重建活动表：
GUI 显示「夜班」、按钮也是夜班，但大脑仍按白班排班（白天照常干活、18:00-22:00 才去坐着）。
上游读档用的是 `setSchedule(...)`（它内部 `refreshBrain`），本移植版漏了这一点。

改为 `setSchedule(...)` 后，读档 → 重建活动表 → 夜班真的生效。
验证方式是让带 `{MaidScheduleMode:"NIGHT"}` 的女仆走一遍读档路径：
`/time set 1000` 时她下车站着（夜班的 06:00-14:00 是睡觉段），
`/time set 9000` 时她坐下并吃掉一个面包（14:00-18:00 才是娱乐段）。
如果大脑还是白班，9000 这个点在白班属于「工作」段，她根本不会坐。


| 模式 | 06:00-14:00 | 14:00-18:00 | 18:00-22:00 | 22:00-06:00 |
| --- | --- | --- | --- | --- |
| 白班 DAY | 工作 | 工作 | 娱乐 | 睡觉 |
| 夜班 NIGHT | 睡觉 | 娱乐 | 工作 | 工作 |
| 全天 ALL | 工作 | 工作 | 工作 | 工作 |

所以**白天想测 IDLE，切夜班再把时间调到 14:00~18:00**（`/time set 9000`）即可，
不必等到晚上。

> 实测（干净坐标，避开存档里已有的建筑）：夜班女仆在 `time 9000` 走向野餐垫、
> 坐在 `EntitySit` 上、把面包从 5 个吃到 4 个；在 `time 1000` 被 `EntitySit` 主动请下车
> 并站回地面。所以「白天不坐」在 06:00-14:00 是**设计如此**，不是缺失。

### 验证边界

编译通过、按钮已接进 `AbstractMaidScreen#init`（`syncButtonState` 里跟同步值，
`renderButtonTooltips` 里给动态提示：当前时刻 + 各时段是否在进行）。
**点击本身没法自动验证** —— 需要真人开女仆界面点一下，看三件事：
按钮显示当前作息、点一下轮转、女仆的活动范围随之改变。

## AI 聊天（LLM 对话）

对着自己的女仆按聊天键（默认 T）输入一句话，服务端把它连同角色设定与对话历史发给
OpenAI 兼容接口，回复以**头顶气泡 + 聊天栏**两种形式送到玩家眼前。

`ai` 包共 42 个文件（其中 TTS 16 个，见下面的「AI 语音」一节）。
上游 44 个里只剩 STT 3 个未移植。

### 数据流

```
客户端  按 T  [ 准星指着自己的女仆 && 她的模型有角色设定 ]
          -> SendUserChatPacket   maidId / 文本 / 当前语言显示名
服务端  复核 [ 实体是女仆 && 活着 && 是自己的 ]
          -> MaidAIChatManager#chat
              站点表 [ 必须有 api_key ] -> 角色设定 [ 必须有对应 model_id ]
              -> 拼 ChatCompletion [ system 设定 + 历史 + 末尾语言强调 ]
              -> HttpClient.sendAsync [ 异步，20 秒超时 ]
          <- 回调 -> MinecraftServer#submit 回主线程
              -> 解析 ResponseChat{chat_text, tts_text}
              -> ChatBubbleManger.addAiChatText [ 气泡 + 聊天栏 ]
                  同步字段 CHAT_BUBBLE -> 客户端渲染
```

### 两个数据源都在「运行时可改」

| 文件 | 内容 | 何时读 |
|---|---|---|
| `assets/touhou_little_maid/config/available_sites.yml`（jar 内置） | 站点模板 | 启动时 |
| `config/touhou_little_maid/available_sites.yml`（玩家） | 填 `api_key` 的地方 | 启动时 / `/tlm ai_chat reload` |
| `config/touhou_little_maid/settings/*.yml`（玩家，最多 8 层） | 角色设定 | 同上 |
| `assets/<domain>/settings/*.yml`（模型包，1 层） | 角色设定 | 同上 |

**只有填了 `api_key` 的站点才会进 `CHAT_SITES`。**
一份干净的配置里 `CHAT_SITES` 是空的，聊天时提示
`ai.touhou_little_maid.chat.api_key.empty` —— 这是预期行为。

内置模板里 `player2` 的 `api_key` 是字面量 `empty`（上游同款），所以**默认会走 player2 本地站点**。
没装 player2 桌面端就会连接失败并提示错误，这也是上游行为。想用云端站点请自己填 key。

读完后会**把合并结果写回玩家配置文件**（上游同样如此），第一次进游戏就能看到一份完整模板。

### 角色设定：决定「这只女仆能不能聊天」

```yaml
meta:
  author: 作者
  model_id: [touhou_little_maid:hakurei_reimu]
setting: |
  你是博丽灵梦……
  现在是 ${game_time}，天气 ${weather}，你在 ${dimension}。
  主人叫 ${owner_name}。
```

支持的占位符（与上游逐条一致）：

`${game_time}` `${weather}` `${dimension}` `${mainhand_item}` `${offhand_item}`
`${inventory_items}` `${output_json_format}` `${chat_language}` `${tts_language}`
`${healthy}` `${owner_healthy}` `${armor_items}` `${effects}` `${biome}`
`${owner_name}` `${custom_setting}`

`setting` 里 `#` 开头的行与空行会被丢掉，其余按行拼接。
**所有女仆都能按 T 打开聊天界面**，与 1.20.1 一致。真正缺设定时不是拒绝聊天，
而是**第一次说话时自动生成一份**（`ai.AutoGenSettingEnabled`，默认开启）：

```
说话 -> getSetting() 为空
     -> 气泡『角色设定为空，正在生成角色设定……』
     -> 发一次特殊 prompt（要求只返回 setting 正文，JSON 契约与普通聊天相同）
     -> 成功后写 config/touhou_little_maid/settings/<model_id>.yml 并 reload
     -> 气泡『角色设定文件已经自动生成完毕』
     -> 玩家再说一次就能正常聊（生成期间重复说话不会重复请求）
```

生成物就是普通设定文件（`meta.author` / `meta.model_id` / `setting: |`），可以直接手改。
**同名文件已存在时绝不覆盖** —— 玩家手写的人设永远优先。

内置只有 geckolib 的两个设定（`winefox` / `zhiban`），其余模型第一次说话会走上面这条路径；
不想让模型自动写人设就把 `ai.AutoGenSettingEnabled` 关掉，那时会提示『这个模型还没有角色设定』。

自动生成的响应同样是一个 `{"chat_text":…,"tts_text":…}` 的 JSON，**正文只取 `chat_text`**
（上游 1.20.1 的 `AutoGenSettingCallback` 也只取这一项）。两处与上游的差异：

- `max_tokens` 从默认的 256 提到 1024。256 会把 JSON 从中间切断，
  之前就出现过 `settings/<model>.yml` 里只写进半句话的事故；
- 万一还是被截断（GSON 解析失败），会退化为「手工从 `"chat_text":"` 之后取到结尾」，
  再不行才退回「去掉代码围栏的原文」。

### 输出契约：模型必须返回 JSON

请求里带 `response_format: {"type":"json_object"}`，并要求模型只返回
`{"chat_text": "…", "tts_text": "…"}`。两者都为空或解析失败都会给主人发一条红字错误。

`tts_text` 用于 TTS 合成（见下一节「AI 语音」），**字段不能删**。
`${output_json_format}` 就是用 `ResponseChat` 的空实例序列化出来的，
少一个字段模型就不会返回它，将来的 TTS 还得再改 prompt。

#### 为什么最后一条 user 消息里必须出现 `json`

DeepSeek（以及一些兼容 OpenAI 的网关）有一条硬校验：只要请求里带了
`response_format: json_object`，**prompt 里就必须出现 `json` 字样**，否则直接拒绝：

```

HTTP 400 Prompt must contain the word 'json' in some form to use 'response_format' of type 'json_object'
```

上游不额外加这句话，因为它的角色设定模板里本来就有「输出格式为 JSON 格式：${output_json_format}」。
但**玩家自己写的人设**、以及**自动生成的人设**不一定有那句话，于是第一次能对话、之后必挂（HTTP 400）。
本移植版因此在最后那条「请用 X 语言回复」的指令里补了一句
「只返回一个 json 对象（json_object）……」，见 `Service#getChatCompletion`。
这是与上游的**有意差异**，原因就是上面的校验。

### 与上游的差异（都是框架差异）

| 位置 | 上游 | 本移植版 | 原因 |
|---|---|---|---|
| 说话 / 保存 / 请求设置的包 | `SimpleChannel` + 7 个 `IMessage` | 4 个独立 `ResourceLocation` 通道 + 5 个类 | Fabric 没有 `SimpleChannel` |
| 按 T 的拦截 | `InputEvent.KeyInputEvent`（GLFW 回调里就判定） | `ClientTickEvents.START_CLIENT_TICK` | Fabric 没有按键事件 |
| 每只女仆的设置界面 | Cloth Config（`PartAIIntegration`） | 手写 `MaidAISettingScreen` | 没有移植 Cloth 集成 |
| 气泡渲染类型 | 自建「关混合」的 `RenderType` | `RenderType.text` + 顶点补 `color` | 上游靠 Forge AT 打开 `RenderStateShard` 内部构造器，本移植版没有 AT |
| 角色设定的来源 | 扫 `tlm_custom_pack/*.zip` | 扫 jar 的 `assets/<domain>/settings` + 玩家 `tlm_custom_pack` | 内置模型包在 jar 里、不在游戏目录 |
| 聊天键的组合键判断 | `KeyModifier.getActiveModifier()` | 省略 | Fabric 无等价物 |
| 设定为空 | 1.18.2：提示 no_setting 后什么都不做 | 1.20.1 的行为：自动生成一份并落盘 | 按用户要求贴近 TLMO |

**按键顺序很关键**: 先判 [ 准星指向的女仆能不能聊 ]，再 `consumeClick()`。
反过来的话，玩家指着普通方块按 T 时点击已被吃掉，聊天栏就打不开了。

### 聊天气泡：全量移植（含随机台本 / 受伤 / 饰品 / 书与笔）

1.18.2 上游的气泡层现在<b>整块都搬过来了</b>，不再是「只做 AI 需要的那一半」：

| 触发时机 | 台词键 | 代码位置 |
|---|---|---|
| 每 60 秒（按 UUID 打散）· 待机 | `idle` | `ChatBubbleManger#addMainChatText` |
| 每 60 秒 · 睡觉 | `sleep` | 同上 |
| 每 60 秒 · 工作 | `work` + **当前任务 id**（`fishing` / `farmer` …） | `addWorkChatText` |
| 每 60 秒 · 讨食 | `beg` | `addSpecialChatText` |
| 每 60 秒 · 早上（0–3000 刻） | `morning` | 同上 |
| 每 60 秒 · 傍晚（12000–15000 刻） | `night` | 同上 |
| 每 60 秒 · 雨 / 雪 / 冷 / 热 | `rain` / `snow` / `cold` / `hot` | 同上（`SoundUtil.isRainBiome` / `isSnowyBiome` 同款判定） |
| **受伤时立刻** | `hurt` | `MaidDamageEvent` 监听（`ChatBubbleManger#init` 里注册） |
| 每 60 秒 · 饰品自定义 | `IMaidBauble#getChatBubbleId()`（静音饰品 = `mute`） | `addOtherChatText` |
| AI 回复 / 内部提示 | —— | 沿用上一轮（`addAiChatText` / `addInnerChatText`） |
| 任务失败提示 | `chat_bubble...inner.*` | `MaidFeedAnimalTask`（动物超限）/ `MaidFindSitTask`（找不到坐具）/ `MaidHomeMealTask`（手满、垫子空） |

**三路随机的偏移量**取自 `UUID` 的高/低位，与上游逐字一致：同一刻只有「该说话」的那一部分女仆会说话，
不会全服女仆一起开口。

#### 台本从哪来（三级合并）

1. **默认台本** `assets/touhou_little_maid/tlm_custom_pack/default_chat_bubble.json`
   —— 模组自带的那几句（含表情图标与颜文字）；
2. **模型台本** `maid_model.json` 里该模型的 `chat_bubble` 字段（内联对象或
   `命名空间:路径` 指向另一个 json 都支持），按 `decorate` 规则与默认合并
   （`replace: true` 时整段替换）；
3. **玩家台本**：拿成书 / 书与笔右击女仆写入（潜行右击清空、拿空书与笔右击复制）。
   书页格式是「类型 + 两个换行 + 若干条台词」，随实体 NBT 落盘。

读取由 `entity/chatbubble/ChatBubbleScripts` 负责：**直接读模组 jar 里的
`maid_model.json`**（不用上游那套 `ServerCustomPackLoader`，理由与服务端模型池相同），
按模型 id 缓存，`/reload` 与换资源包时清缓存。

#### 图标气泡（表情图）

台本里写成 `%命名空间:贴图路径%` 的条目是图标。贴图是 **60×20 一帧、竖直堆叠**的动画
（`idle1.png` = 60×160 → 8 帧），渲染时每 2 tick 换一帧（`(tickCount / 2) % frames`，与上游一致）。

**不复用上游的 `SizeTexture`**：那要靠替换纹理管理器实现（需要 mixin / AT）。
本移植版改成 `client/renderer/ai/IconTextureSizes`：从资源管理器读 PNG 的 IHDR
（8 字节签名 + 4 字节长度 + `IHDR` + 宽 + 高，共 24 字节），按贴图缓存，读不到就当单帧。

#### 图标为什么会「横条纹闪烁」：分层 Z

底图（九宫格拼出的若干四边形）与图标（另一个 60×20 四边形）如果画在同一个 z 上，
就是共面几何：数学上深度相同，但两个大小不同的四边形在光栅化时逐像素插值出的深度
有末位差异，配合 `RenderType` 的 `LEQUAL_DEPTH_TEST` 且写深度，结果就是
**一半像素通过、一半不通过** —— 表现就是一条条横向条纹，玩家转动视角时条纹还会
跟着移动闪烁。

文字从来没出过这个问题：`Font#drawInBatch` 的顶点 z 固定是 `0`，恰好和底图的 `0.2`
拉开了距离（1.18.2 上游也是 `drawBg` 传 0.2f、`drawIcon` 传 0）。
现在把这条隐式约定写成显式常量，每层各一个 z：

| 层 | 常量 | z | 与底图的间距 |
|---|---|---|---|
| 底图九宫格 | `Z_BACKGROUND` | 0.2 | —— |
| 尾巴 | `Z_ARROW` | 0.1 | 0.0025 格（2.5 mm，看不出错位） |
| 文字 / 图标 | `Z_CONTENT` | 0.0 | 0.005 格 |

这个位姿下（`scale(-0.025, -0.025, +0.025)` 且已过 `cameraOrientation()`）
**z 越小越靠近镜头**，证据就是 z=0 的文字能盖住 z=0.2 的底图。
层与层之间别改成「只差一点」（例如 0.199）—— 那等于又回到共面附近。

#### 底图：旧网格图自动换成九宫格

默认台本里写的仍是 1.18.2 那三张 60×40 的旧网格图（`default1/2/3.png`），
它们与九宫格格式不兼容（用九宫格九切会糊成一团）。所以
`ChatBubbleScripts#getBackground` 的规则是：

* 台本写的是那三张旧图 → 换成 1.20.1 的 `textures/entity/chat_bubble/type2.png`；
* 其它贴图原样使用（第三方模型包若自带现代九宫格底图就照用）。

#### 显示开关

渲染前看两个条件，缺一不可（与 1.20.1 `EntityMaidRenderer` 同一处判断）：

* 全局 `MaidConfig.GLOBAL_MAID_SHOW_CHAT_BUBBLE`（默认 true）；
* 这只女仆自己的 `MaidSubConfig.isShowChatBubble`（女仆配置界面里的「显示聊天气泡」）。

#### 与 1.20.1 的差异

| 项 | 1.20.1 | 这里 |
|---|---|---|
| 随机台词数据 | 数据包 `chat_bubble/kaomoji.json` + `textures/chat_bubble/maid_emoji/*.png`，带权重配置 | 沿用 1.18.2 的「台本里直接写贴图或颜文字」，等价能力、不同来源；1.20.1 那批 `maid_emoji` 与 `kaomoji.json` 本仓库没有 |
| 热判断 | `Biome#getTemperature(BlockPos)`（靠 AT） | `Biome#getBaseTemperature()`：山地这类按高度降温的群系会判得略热，阈值 0.95 不变 |
| 受伤监听注册 | `@EventBusSubscriber` 自动 | `ChatBubbleManger#init()` 里手工 `MinecraftForge.EVENT_BUS.register`（本仓库的兼容层，与各保护饰品相同） |

过期判定**两端都做**：服务端清是不再同步旧内容，客户端清是因为同步包有延迟。

### 气泡的样式（按 1.20.1 重写）

1.18.2 上游那套是「3x2 张 20x20 的小方格拼起来」，**文字不换行**：一句长回复会拉成一条
横贯屏幕的白条（真人截图里就是这样）。现在按 TLMO 1.20.1 重写：

| 项 | 旧实现（1.18.2 上游） | 现在（= 1.20.1） |
|---|---|---|
| 贴图 | `textures/chat_bubble/bg/default1.png`（3x2 网格） | `textures/entity/chat_bubble/type2.png`（256x256，内容在左上 48x24，8 px 九宫格边距） |
| 绘制 | 左右两张 + 中间重复 N 张 | 九宫格：四角原样、四边单向拉伸、中间双向拉伸 |
| 换行 | 不换行 | `font.split`：居中 200 px、左右两列 120 px |
| 多行 | 不可能 | 每行 9 px；气泡高度 = 行数 x 9 + 上下各 5 px |
| 布局 | 1 条居中、2 条左右、3 条再来一条居中偏上 | 「右、左、右、左……」交替两列向下堆叠（间距 16 px），奇数时最后一条居中，对齐到较长那一列的底部 |
| 尾巴 | 20x20 的小三角 | 16x16，u = 0 / 16 / 32（右 / 中 / 左），v = 24 |
| 坐姿 | 无偏移（气泡顶在头发上） | 基线下移 **0.25 格**（1.20.1 的 `isMaidInSittingPose` 分支） |
| 睡姿 | 下移 48 px | 换算成 0.9 格，底边与改造前一致 |

**用哪张图？用 `type2`。** 1.20.1 里 AI 回复（`ChatBubbleManager#addLLMChatText`）、
内部提示（`addTextChatBubble`）、自动生成人设（`AutoGenSettingCallback`）、
思考中（`addThinkingText`）**全部**走 `TextChatBubbleData.type2(...)`，
`type1.png`（粗黑边那张）只留给「台本气泡」等少数地方。
第一版误用了 `type1`，看上去仍是旧版的粗黑边，已改。

`ChatTextType` 里没有「内部提示」这一档，加值会改动同步数据的枚举序，所以现在
**所有气泡一律用 `type2`**；等做「台本气泡」那一轮，背景要像上游那样跟着气泡数据
一起同步（`TextChatBubbleData` 带一个 `bg` 字段），而不是写死在渲染器里。

### 未命名女仆的名字：模型译名，不是「Maid」

玩家没给女仆起名时，原版 `Entity#getName()` 会回落到 `getTypeName()`，而它默认返回
**实体类型名**（`Maid`）。上游覆写了 `EntityMaid#getTypeName()`，改成「她当前模型的译名」：

```java
// 1.18.2 上游 EntityMaid#getTypeName()
Optional<MaidModelInfo> info = ServerCustomPackLoader.SERVER_MAID_MODELS.getInfo(getModelId());
return info.map(i -> ParseI18n.parse(i.getName()))
           .orElseGet(() -> new TextComponent(getType().getDescriptionId()));
```

于是 `<博丽灵梦> 你好啊主人` 这种聊天栏消息、名牌、墓碑、AI 人设里的「名字」都是模型译名。
本移植版缺那一层服务端模型池（见 `ServerModelPool` 的注释），改用
`entity/info/ModelDisplayNames`：直接读模组 jar 里的 `assets/<命名空间>/maid_model.json`，
按 `model_id` 找 `name`，找不到就用上游同款的默认键 `{model.<命名空间>.<路径>.name}`。

| 项 | 做法 |
|---|---|
| 数据来源 | `ModContainer#findPath("assets/<ns>/maid_model.json")`，开发环境与生产环境一致 |
| 缓存 | `ConcurrentHashMap`（客户端渲染线程与服务端线程都会调） |
| 返回值 | `Component`：JSON 里通常是 `{model.xxx.name}`，必须留到客户端按玩家语言解析 |
| 兜底 | 模型 id 为空 → 实体类型名；id 合法但查不到 → 默认翻译键 |
| 覆盖面 | 194 条内置模型里 193 条在 `zh_cn` 有对应名字；唯一的例外是彩蛋模型 `moesumika`，它在模型包里就既没 `name` 也没 `desc` 的中文键（上游同样如此） |

**为什么聊天包要多带一个 `name` 字段**：`SendUserChatPacket` 现在填 `maidId + 文本 + 语言 + 女仆名`。
名字和语言一样只能由客户端提供 —— 服务端没有玩家的语言文件，
`{model.xxx.name}` 在服务端解析出来只是键名本身（RCON 里看到的就是
`Summoned new model.touhou_little_maid.cirno.name`）。这条等价于 1.20.1 的
`ChatClientInfo(language, name, description)`，`RegenerateAISettingPacket` 同理。

**与上游的差异**：1.18.2 的 `getTypeName()` 里还有 `MaidTypeNameEvent` 与 YSM 模型名两个分支，
本移植版没有那条 Forge 事件、也没有 YSM 联动，所以只保留「默认模型名」这一层。

### 三个界面：与 1.20.1 对齐 + 按钮悬浮提示

上一版的聊天主界面右下三个按钮是「有字形、没提示」，而左侧两个按钮打开的界面和 1.20.1 差得太远
（历史界面是一整屏纯文本、还把模型的原始 JSON 直接画出来；角色设定界面只有四个平铺按钮），
玩家看不出按钮用途。本轮按 1.20.1 重做这三处：

| 界面 | 1.20.1 对应类 | 现在的样子 |
|---|---|---|
| 历史聊天记录 | `HistoryAIChatScreen` + `HistoryChatWidget` | 双侧气泡 + 尾巴 + 头像 + 「x小时前」+ 右侧压缩摘要面板 + 清除 / 返回 |
| 角色设定 | `SettingEditScreen` | 左上「主人称呼」单行框、下方「自定义设定」多行框（自动折行 / 滚动 / 4096 字数统计）、右侧一列按钮 |
| 设定正文（本移植版独有） | —— | 只读显示 `settings/<模型>.yml` 正文，提供「重新生成人设」「复制台本」 |

**按钮悬浮提示**：1.20.1 的 `FlatColorButton` 自带 `setTooltips`，1.18.2 没有 `GuiGraphics`，
所以改成「界面在 `render` 末尾统一调 `renderToolTip`」——这也是 1.18.2 里
`AbstractContainerScreen` 画提示的时机（`AbstractWidget#render` 不会自己画提示，已用字节码确认）。
聊天主界面六个按钮的文案全部取自上游语言文件：
`history.tip` / `setting.tip` / `config.tip` / `llm.tip` / `tts.tip` / `language.tip`。

**字形与语义重新对齐 1.20.1**：左三 = `≡` 历史 / `✎` 角色设定 / `⚙` 设置；右三 = `Aa` 语种 /
`♪` 语音 / `✦` 大语言。上一版把 `⚙` 与 `✎` 的含意对调了，所以「看起来像设置的按钮」打开的是
角色设定编辑器 —— 这也是「功能不明」的来源之一。

#### 历史界面为什么要先解 JSON

历史队列里存的是模型的**原始响应**（`{"chat_text":"…","tts_text":"…"}`）。
1.20.1 的 `transformMessage` 用 `new ResponseChat(text).getChatText()` 取出正文再显示；
本移植版上一版漏了这一步，于是截图里那一整串 JSON 就原样画了出来。现在同样先解析，
解析失败（纯文本 / 被截断）才原样显示，避免丢消息。

#### 与 1.20.1 的差异

| 项 | 1.20.1 | 这里 |
|---|---|---|
| 压缩摘要 | `HistorySummaryManager` 定期调 LLM 压缩长对话 | **未做**，面板固定显示「当前还没有压缩摘要」（与上游尚未压缩时同貌） |
| 工具调用气泡 | 有（`Role.TOOL`） | 无（本移植版没有工具调用） |
| 「导出设定」 | TinyFileDialogs 弹系统保存框 | 复制同样的 yml 文本到剪贴板（不引入原生库） |
| 女仆立绘预览 | 角色设定界面右下方画一只 | 未画（省一次实体渲染，不影响功能） |
| 多行输入框 | 原版 `MultiLineEditBox` | 手写 `client/gui/widget/MultiLineEditBox`（1.18.2 没有这个类），支持折行 / 滚动 / 光标 / Ctrl+ACVX / 字数统计 |

#### 新增的网络包

`ClearMaidAIChatHistoryPacket`（C2S）：历史界面「清除历史聊天记录」→ 服务端复核「是主人」后
只清历史队列（站点 / 模型 / 温度 / 主人称呼 / 自定义设定都不动）。
`SyncMaidAIHistoryPacket` 的载荷加了 `gameTime`（历史界面「x小时前」的唯一来源），
并改为**按旧→新**发送（队列是 `offerFirst`，迭代顺序是新→旧）。
### 调试命令

两个入口都不走 HTTP，用来验收气泡本身（换行宽度、多条堆叠、坐姿下移、九宫格贴图、
表情图标），真人验 AI 回复时不必为每一句等一次网络往返。

```
/tlm ai_chat say <targets> <text...>
```

直接把文本塞进选中女仆头顶的气泡。

```
/tlm ai_chat bubble <targets> <id>
```

按台本键名取词：把 `main` / `special` / `other` 三段里这个键的候选混在一起随机挑一条，
并回显挑中的内容（`picked: icon …` / `picked: ( ≧Д≦)` / `picked: {chat_bubble…}`）。
`id` 的候选是 `idle` / `sleep` / `work` / `morning` / `night` / `rain` / `snow` /
`cold` / `hot` / `hurt` / `beg` / `mute`（命令有补全）。

### 存档字段

`MaidAIChatData` 下 8 项，与上游同名同序。
`MaidAIDataSerializable` 的 `encode` / `decode` 由存档与两个网络包共用一份，所以不会错位：

```
ChatSiteName / ChatModel / ChatTemperature / TtsSiteName /
TtsModel / TtsLanguage / OwnerName / CustomSetting
```

`ChatTemperature = -1` 表示「跟随全局配置」（默认值，见 `MaidAIChatData#getChatTemperature`）。

### 全局配置（`AIConfig`，10 项）

真正生效的是 `ai.ChatEnabled` / `ai.ChatTemperature` / `ai.ChatProxyAddress` /
`ai.MaidMaxHistoryChatSize`。

`ai.AutoGenSettingEnabled`（默认 true）是 1.20.1 才有的项（1.18.2 上游没有），
本移植版按「行为贴近 TLMO」补上。

`ai.TtsEnabled` / `ai.TtsLanguage` / `ai.TtsProxyAddress` 已经生效（见「AI 语音」一节）。
`ai.SttUrl` / `ai.SttProxyAddress` **填了没有效果**（STT 未移植），
保留是为了配置可与上游互换。

### `/tlm ai_chat reload`

重读站点表 + 角色设定，并把「哪些模型能聊天」与「有哪些站点可选」广播给全部在线玩家。
没有它就只能重启服务器。上游同样提供这条命令。

### 验证到什么程度

四道闸（`build` / `check-encoding` / `check-renderers` / `validate-book`）全绿。

服务端实测（`runServer` + `port/rcon.py`）：

```
[Futo] AI chat sites: 1 chat / 2 tts (only those with an api key count)
[Futo] loaded 6 character settings for AI chat

summon touhou_little_maid:maid 6 100 6            -> Summoned
data get ... MaidAIChatData
  -> {TtsSiteName:"", OwnerName:"", ChatModel:"", TtsModel:"",
      TtsLanguage:"", ChatSiteName:"", ChatTemperature:-1.0d, CustomSetting:""}
data merge ... {MaidAIChatData:{ChatSiteName:"player2",ChatModel:"default",
      ChatTemperature:0.7d,OwnerName:"...",CustomSetting:"...",TtsLanguage:"zh_cn"}}
data get ... -> 八个字段全部按写入值读回（中文以 UTF-8 存取正常）

tlm ai_chat reload -> message.touhou_little_maid.ai_chat.reload_success
```

`run/config/touhou_little_maid/available_sites.yml` 生成正确：5 个站点，`api_type` 已补齐
（`deepseek` 原本缺这一项，被补成 `openai`）。

**没验证到的**（需要真人 + 真实 API key）：

- 真实 LLM 请求与回复（玩家自行填 `api_key` 后用 `runClient` 试）；
- 聊天界面 / 设置界面的交互，以及头顶气泡的实际绘制位置；
- 「按 T 拦截而不弹原版聊天栏」这一步（已按 `Minecraft.tick()` 的顺序推演，但需要真按键）。

### 气泡渲染为什么用 `RenderType.text`

上游自建了一种「关混合、不模糊」的渲染类型，但它需要构造
`RenderStateShard.TransparencyStateShard` 与 `RenderType.CompositeState`。
这两个在 vanilla 里是 **protected** 内部类构造器，上游靠 Forge 的 AT 打开了它们。
本移植版没有 AT（见 `build.gradle` 末尾的说明），因此改用原版公开的 `RenderType.text`：
`POSITION_COLOR_TEX_LIGHTMAP` + 半透明混合。气泡贴图本身带 alpha，视觉上与上游一致。

**代价**：顶点格式含 `COLOR` 与 `LIGHTMAP`，所以 `vertex()` 必须依次写
`vertex -> color -> uv -> uv2`。少任何一个都会在 `BufferBuilder#endVertex` 抛 `Not filled all elements of the vertex`。
异常点在渲染循环里，表现是「一放女仆就崩」。

## AI 语音（TTS）

AI 回复里的 `tts_text` 现在会被真的念出来。链路与上游一致，只有「怎么让原版声音引擎
播放内存里的字节」这一步因为 1.18.2 Fabric 没有对应的 Forge 补丁，改用了一个 mixin。

### 数据流

```
服务端  解析 ResponseChat{chat_text, tts_text}
          -> ai.TtsEnabled && 存在可用 tts 站点（必须有 api_key）？
               ├─ api_type: system -> TTSSystemAudioToClientPacket ［只发给主人］
               └─ 其它             -> Service.getTtsClient / getTtsRequest
                                      -> HttpClient.sendAsync ［异步，20 秒超时］
                                      -> TTSAudioToClientPacket ［16 格内广播］
          -> 无论成败都照常显示气泡（语音失败不吞掉文字）
客户端  收包 -> MaidAISoundInstance（EntityBoundSoundInstance 子类）
          -> OggReader 判断容器：Opus 走 OpusAudioStream，Vorbis 走原版 OggAudioStream
          -> SoundEngine 流式通道播放
```

### 支持的站点类型

| `api_type` | 地址写法 | 备注 |
|---|---|---|
| `fish-audio` | base 地址，自动补 `/tts` | 固定 `format: opus` + `opus_bitrate: 24`；`reference_id` 取站点的模型名 |
| `gpt-sovits` | 完整端点（如 `http://127.0.0.1:9880/tts`） | 参考音频 / prompt 从站点的 `extra_args` 读 |
| `system` | 不需要地址 | 客户端旁白朗读，不经过任何 HTTP |

### 与上游的差异

| 位置 | 上游（Forge 1.18.2） | 本移植版 | 原因 |
|---|---|---|---|
| 播放内存音频 | 覆写 `SoundInstance#getStream` | `SoundEngineMixin` 重定向 `SoundBufferLibrary#getStream` | `SoundInstance#getStream` 是 Forge 打在 `SoundInstance` 上的补丁方法，Fabric 原版接口没有 |
| 系统旁白 | AT 打开 `NarratorChatListener#narrator` | `NarratorChatListenerAccessor`（`@Accessor`） | 同上：本仓库没有 AT |
| `OggReader` | 还负责读自定义音色包（文件 / zip） | 只保留「识别 Vorbis / Opus」 | 音色包属于「女仆音色包」子系统 |
| `OpusAudioStream#read` | 按帧读，整帧写入缓冲区 | 整段 PCM + 读指针 | 上游末尾会混进上一帧的残留字节（一声杂音），且 `size < frameSize` 时会误判为播放结束 |

### 新增依赖

`libs/concentus-1.0.2.jar`（纯 Java 的 Opus 解码器）与 `libs/vorbis-java-core-0.8.jar`
（ogg 容器解析）。两者都从上游 Forge jar 的 `META-INF/jarjar` 里解出来，用
`implementation files(...)` 引入 —— 与 `snakeyaml` 同一套做法：离线可用、版本钉死。

### 音效注册

`InitSounds.MAID_AI_CHAT` 注册的 `maid.ai_chat` 在 `sounds.json` 里指向
`touhou_little_maid:maid/empty`（约 2 KB 的静音 ogg）且带 `"stream": true`。
它只是**占位**：`stream: true` 让声音引擎走流式分支，真正的字节由
`MaidAISoundInstance#createStream()` 提供。解码失败时退回这段静音，而不是崩溃。

### 验证到什么程度

- `./gradlew.bat build` = BUILD SUCCESSFUL；`check-encoding.ps1` = `FFFD_CHARS=0`。
- `runClient` 到主菜单：`Sound engine started` 正常打印、`run/crash-reports` 为空 ——
  说明 `SoundEngineMixin` 已成功应用到 `SoundEngine#play`（`defaultRequire: 1`，失败会直接崩溃）。
- **未验证**：真实 TTS 站点的合成与播放（需要玩家的 `api_key` 或本地 GPT-SoVITS）、
  系统旁白在 Windows 上是否真的出声、Opus 解码后的音质。

## 配置界面：Cloth Config 全量移植，以及「哪些项真的生效」
上游在 Forge 上把配置界面交给 `ConfigGuiHandler`，Fabric 上没有这个东西，所以这一章记的是
**三件事**：界面从哪进、每一项有没有消费者、以及怎么在没有人点鼠标的情况下证明界面能开。
### 1. 三个入口
| 入口 | 条件 | 实现 |
|---|---|---|
| 模组列表里的「配置」齿轮 | 装了 **ModMenu** | `fabric.mod.json` 的 `modmenu` 入口点 → `compat/modmenu/ModMenuApiImpl` |
| `打开模组配置界面` 按键 | 永远可用 | `client/input/OpenConfigKey`，**默认未绑定**（避免和已有按键冲突） |
| 单只女仆的 AI 设置 | 右键女仆 → AI 设置 | `PartAIIntegration`（装了 Cloth 时），否则退回手写 `MaidAISettingScreen` |
齿轮那一路在**没装 Cloth Config 时返回 `null`**（ModMenu 就不画齿轮），而不是画一颗点了会崩的齿轮 ——
与上游一致。按键那一路在没装 Cloth 时打开 `client/gui/mod/ClothConfigScreen`，
明确告诉你「需要安装 Cloth Config」并给下载链接，而不是「点了没反应」。
### 2. 六个分类，69 个条目
`MenuIntegration` 按上游 1.18.2 逐项对齐：
| 分类 | 条目数 | 内容 |
|---|---|---|
| 女仆 | 31 | 音量频率、气泡开关、3 个物品下拉框、作息/工作范围、四种武器射程、喂食上限、换模型、五子棋主人限制、主人女仆上限、悦灵替换、背包黑名单、攻击忽略、三种餐食黑名单 ×（正则 + 具体）、吃完返还容器、枪械三段射程 |
| 坐垫 | 2 | 换肤许可、任何人可破坏 |
| 杂项 | 12 | 妖精灵力/生成概率/维度黑名单、死亡扣除灵力、魂符、手册、神社庭灯三项、OptiFine 警告、稻草人范围、新妖精模型 |
| 原版替换 | 4 | 史莱姆、经验球、不死图腾、附魔之瓶 |
| 渲染 | 12 | 11 种「手持物指向女仆」的提示开关 + YSM 轮盘 |
| 全局 AI | 8 | 聊天/TTS 开关、温度、历史长度上限、TTS 语言下拉框、自动生成角色设定、token 上限、打开 `available_sites.yml` 的链接 |
「女仆」那 31 条里有 3 条物品下拉框**直接用物品图标**渲染（`DropdownMenuBuilder.ofItemObject`），
所以列表里的物品按**注册名**排序 —— 上游按 `Item::toString` 排，那个字符串带对象哈希，
每次启动顺序都不一样，下拉框里根本找不到东西。
### 3. 「有界面项但没有子系统」的五项，明说了
审计方法：把六个配置类里的每个字段名拿去全仓库 grep，排除 `config/` 与 `compat/cloth/` 自身。
结果：**除下列 5 项外，每一项都有真实消费者**。
| 配置项 | 缺什么 |
|---|---|
| `ReplaceAllayPercent` | 悦灵替换（1.19 才有悦灵；上游 1.18.2 里这一项也是死的） |
| `MaidGunLongDistance` / `Medium` / `Near` | `compat/tacz` 枪械联动（TACZ 没移植） |
| `EnableYsmRouletteTip` | Yes Steve Model 联动（YSM 没移植） |
这 5 项**保留在界面上**（删掉会让 `.toml` 与上游不再可互换），但 tooltip 里多一行
「本移植版尚未实现对应功能，此项暂不生效」。玩家改完发现没反应时才不会以为是 bug。
另外 `STTUrl` / `STTProxyAddress` 不进界面（`ai/service/stt` 未移植），但仍在 toml 里。
### 4. 保存必须显式落盘
`setSaveConsumer` 只改内存中的值。上游靠 Forge 自己的配置界面顺手 `save()`，
本移植版是自建界面，所以在 `getConfigBuilder()` 里挂了
`setSavingRunnable(() -> { GeneralConfig.save(); ServerConfig.save(); })`。
两个类各加了一个静态 `spec` 引用与 `save()`，`isLoaded()` 为假时静默跳过。
### 5. 没有人工点击时，怎么证明界面能开
「配置界面能不能打开」天然要人点一下，但构建这张表的每一步都可能在运行时炸
（物品下拉框拿到 `null`、语言表为空时 NPE、`Utils.getConfigFolder()` 在客户端拿不到目录）。
所以加了一个**受 JVM 参数门控**的自检：`compat/cloth/ClothConfigSelfTest`。
- 只有 `-Dfuto.cloth.selftest=true` 时才激活；`Boolean.getBoolean` 为假时右侧整体短路，
  这个类**连加载都不会发生**；
- 触发点：第一次出现标题画面（此时物品注册表与语言表都就绪，和玩家点开时状态一致）；
- 依次做三件事：构建整张表 → 校验六个分类都在并统计条目 → `setScreen(builder.build())`
  真跑一遍 Cloth 的 `init()` → 改一个值、`save()`、去 TOML 里读回来，然后还原。
驱动方式（已写进 `build.gradle` 的 `runs.client`）：
```bash
./gradlew.bat runClient -PclothSelftest
```
不带 `-PclothSelftest` 时行为与以前完全一致。
**实测结果**（dev 客户端，带真名 `Dev`）：
```
[Futo] cloth selftest: category config.touhou_little_maid.maid      has 31 entries
[Futo] cloth selftest: category config.touhou_little_maid.chair     has  2 entries
[Futo] cloth selftest: category config.touhou_little_maid.misc      has 12 entries
[Futo] cloth selftest: category config.touhou_little_maid.vanilla   has  4 entries
[Futo] cloth selftest: category config.touhou_little_maid.render    has 12 entries
[Futo] cloth selftest: category config.touhou_little_maid.global_ai has  8 entries
[Futo] cloth selftest: 6 categories, 69 entries in total
[Futo] cloth selftest: screen init OK, current=GlobalizedClothConfigScreen
[Futo] cloth selftest: save/flush OK (MaidWorkRange = 13)
[Futo] cloth selftest: DONE
```
`MaidWorkRange = 13` 是从 `run/config/touhou_little_maid-common.toml` 里读回来的，
证明「改值 → 保存 → 落盘」这条链路真的通。自检结束后写回 12，不污染开发环境。
### 6. 顺带补上的两个「配置定义但没人读」
| 配置 | 之前 | 现在 |
|---|---|---|
| `CloseOptifineWarning` | 定义了，零引用 | `client/event/ShowOptifineScreen` + `client/gui/mod/OptifineScreen`：装了 OptiFine 时在**第一次**标题画面弹警告，倒计时 10 秒才能点「继续」 |
| `UseNewMaidFairyModel` | 定义了，零引用；渲染器固定旧模型 | `EntityFairyRenderer#render` 按开关委派给 `NewEntityFairyRenderer`（新模型）或走旧模型 |
`UseNewMaidFairyModel` 默认 **true**，所以女仆妖精的外观变了：从 1.18.2 的旧网格换成
上游 1.18.2 的新网格（`NewEntityFairyModel`，128×128 贴图、18 种发色 + rick 彩蛋）。
**为什么不用 1.20.1 的 `models/bedrock/entity/new_maid_fairy.json`**：那份几何**没有任何动画**
（1.20.1 的渲染器里一个 `setCustomAnimations` 都没有），换成它等于把「走路甩胳膊、
悬空扇翅膀、每 60 tick 眨一次眼」全部丢掉。1.18.2 的手写网格 `setupAnim` 里带着这些动作，
而且用的就是仓库里已有的那批贴图。
两个模型层都要登记（`EntityFairyModel.LAYER` 与 `NewEntityFairyModel.LAYER`）：
`EntityFairyRenderer` 的构造函数里同时 `new` 了 `NewEntityFairyRenderer`，
即使配置关着也会去 `bakeLayer` 新模型那一层。
### 7. 验证边界
| 项目 | 结果 |
|---|---|
| `./gradlew.bat build` | SUCCESSFUL（40,665,359 B） |
| `check-encoding.ps1` | `SCANNED_FILES=1598 BAD_FILES=0 FFFD_CHARS=0` |
| `check-renderers.ps1` | `entity 11/11`、`block entity 14/12`、`ALL OK` |
| `validate-book.ps1` | `entries=54 categories=3 problems=0` |
| 配置界面自检 | 6 分类 / 69 条目 / init OK / save 落盘 OK |
| RCON 召唤两只女仆妖精 | 0 `Missing texture`、0 `Unable to load model`、0 渲染异常、0 crash-report |
**没做**：真人视觉验收（新妖精模型长得对不对、OptiFine 警告页排版）。自动化只能证明
「模型层烘焙成功 + 贴图路径存在 + 渲染循环不抛异常」。
没做的功能：图标缓存（`ModelIconCache` 在 1.18.2 上游也不进界面，本移植版的模型 GUI
没有缓存分支）、悦灵替换、TACZ 枪械、YSM 联动。
