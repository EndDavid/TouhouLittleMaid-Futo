# 进度与已知问题

> 移植过程笔记。摘自根目录 `README.md`（2026-11-25 拆分），返回索引：[`docs_dev/README.md`](README.md)。

## 进度总览

| 模块 | 状态 |
|---|---|
| 工具链 / Forge 兼容层 | 完成 |
| 渲染（GeckoLib3 魔改 + 模型包 + 动画） | 完成 |
| Brain 行为（活动 / 记忆 / 感知器 / 日程） | 完成 |
| 工作模式 | **20 个**，与上游 `TaskManager` 的登记表逐项一致（`board_games` 已在其中） |
| 女仆背包（7 种）+ 物品栏 | 完成 |
| 主界面 GUI + 左侧任务列表 | 完成 |
| 顶部三页签（主界面 / 任务设置 / 女仆配置） | 完成（服务端换菜单式切换） |
| 女仆配置界面（8 项子配置） | 完成 |
| 任务设置界面（默认提示 / 攻击名单） | 完成 |
| 饮食系统 | 完成（工作餐 / 回血餐 / 家庭餐，三类均已注册） |
| 配置系统（Forge Config API Port） | 完成（含 `AIConfig`） |
| 声音（语音包 / 环境音 / 频率控制） | 完成 |
| 弹幕攻击 / 御币 / 钓鱼 | 完成 |
| 灵力（Power / P 点） | 完成（Mixin 挂在玩家实体上） |
| 经验球吸收 / 附魔之瓶 / 图标替换 | 完成 |
| 女仆妖精（旧模型） | 完成 |
| 手持物品渲染 | 完成（两条模型管线都走通） |
| **祭坛多方块 + 祭坛合成** | **完成** |
| **神社 / 胶片 / 女仆床 / 稻草人** | **完成** |

| **墓碑（死亡收纳）+ 祭坛召唤箱** | **完成** |
| **坐垫（58 种模型 + 物品 3D 图标 + 碰撞箱显示器 + 换肤界面）** | **完成** |
| **帕秋莉手册（书内容 + 侧边页签）** | **完成**（54 条目 / 3 分类） |
| **搜索 / 定位（小号 / 仆从铃 / 狐狸卷轴 / 河童罗盘）** | **完成**（含 `SchedulePos` 三坐标作息） |
| **棋类（象棋 / 五子棋 / 国际象棋：各 3×3 多方块 + 对局 AI）** | **完成**（三块棋盘全部可玩） |
| 骑乘模型倒置 | **完成**（钓鱼已回归上游的「坐船 / 坐垫再抛钩」） |
| 自定义附魔（滞弹 / 速弹 / 末影之末） | **完成**（挂在御币上，效果走弹幕链路） |
| **聊天气泡（随机台本 + 受伤 + 饰品 + 书与笔 + 表情图标）** | **完成** |
| **AI 聊天（LLM 对话 + 头顶气泡 + TTS 语音 + 设置界面）** | **完成**（STT 未做） |
| **进度（成就）：54 条 + 9 件图标物品 + 23 处触发点** | **完成**（`change_maid_sound` 待音效包子系统） |
| 模组联动（REI / Jade / Patchouli / Cloth Config / ModMenu） | **完成**（JEI / EMI / YSM / Carry On / Iris 等未做；详见 `03` 的「Jade 联动」章节） |
| **睡觉的落点（碰撞箱 + 渲染）** | **完成**（`pinToBed` 两格中点 + 渲染侧补一次半身长） |

## 测试示例

所有用例都假设女仆**已驯服**、且执行过
`/tlm pickup @e[type=touhou_little_maid:maid,limit=1] true`。
`/give` 只能给玩家，所以物品用 `summon minecraft:item` 丢在她脚下让她自己捡。

```mcfunction
# ---- 近战 / 弓 / 弹幕 ----
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:iron_sword
/tlm task @e[type=touhou_little_maid:maid,limit=1] attack

/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:bow
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:arrow",Count:32b}}
/tlm task @e[type=touhou_little_maid:maid,limit=1] ranged_attack

# 弹幕（1 只僵尸 → 单发；6 只 → 32 发扇）
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with touhou_little_maid:hakurei_gohei
/tlm task @e[type=touhou_little_maid:maid,limit=1] danmaku_attack
/summon minecraft:zombie ~6 ~ ~

# ---- 背包类 ----
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:shears
/tlm task @e[type=touhou_little_maid:maid,limit=1] shears

# 挤奶（必须留一个空槽放奶桶；背包满时她不动 —— 上游行为）
/summon minecraft:cow ~ ~ ~
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:bucket",Count:4b}}
/tlm task @e[type=touhou_little_maid:maid,limit=1] milk

# 采蜜（honey_level 必须 = 5；/setblock 会顺带注册 POI，没反应就等 1~2 秒）
/setblock ~2 ~ ~ minecraft:beehive[honey_level=5]
/tlm task @e[type=touhou_little_maid:maid,limit=1] honey

# ---- 农耕类 ----
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:wheat_seeds",Count:16b}}
/tlm task @e[type=touhou_little_maid:maid,limit=1] farm

# 甘蔗：5 格沙 + 旁边一条水 + 3 段高甘蔗；背包里也放点甘蔗（收完自补种）
/fill ~2 ~ ~ ~2 ~ ~4 minecraft:sand
/fill ~1 ~ ~ ~1 ~ ~4 minecraft:water
/fill ~2 ~1 ~ ~2 ~3 ~4 minecraft:sugar_cane
/tlm task @e[type=touhou_little_maid:maid,limit=1] sugar_cane

# 西瓜 / 南瓜（瓜 + 连着的瓜藤，两者都要有）
/setblock ~2 ~ ~ minecraft:melon
/setblock ~3 ~ ~ minecraft:attached_melon_stem[facing=west]
/tlm task @e[type=touhou_little_maid:maid,limit=1] melon

# 可可（丛林原木侧面，age >= 2）
/setblock ~2 ~ ~ minecraft:jungle_log
/setblock ~2 ~1 ~ minecraft:cocoa[age=2,facing=north]
/tlm task @e[type=touhou_little_maid:maid,limit=1] cocoa

# ---- 灭火 / 钓鱼 ----
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with touhou_little_maid:extinguisher
/tlm task @e[type=touhou_little_maid:maid,limit=1] extinguishing
/setblock ~ ~ ~ minecraft:fire

# 灭火器的额外用法：攻击模式下副手持灭火器，打火免疫生物会补一层持续灼伤
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.offhand with touhou_little_maid:extinguisher
/summon minecraft:blaze ~2 ~ ~

/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:fishing_rod
/tlm task @e[type=touhou_little_maid:maid,limit=1] fishing
```

| 任务 | 判定 | 备注 |
|---|---|---|
| `farm` | 成熟作物 | 无锄头则只把 Age 归零（作物继续长）并补种 |
| `sugar_cane` | 从下往上第 2 段起 | 连带上方一节一起收，留最下面 1 段继续长 |
| `melon` | 连着瓜藤的整块瓜 | 精准采集 → 掉完整瓜并补种 |
| `cocoa` | `age >= 2` | 可达性放宽到 3×3×2 |
| `grass` | 草 / 花 / 高草 | 只收不种 |
| `snow` | 雪片 | 持铲子才掉雪球并扣耐久；空手直接清除 |

灭火三种触发任一成立即喷（白色烟雾，寿命 3 秒）：主人着火 / 自己着火 /
2 格内有着火的驯服生物。生效内容为移除 5×3×5 内的火焰方块、2 格内生物 `clearFire()`，
但火免疫的敌对生物反而受伤（每 5 tick 2 点魔法）—— 上游用来对付烈焰人。

钓鱼流程（与上游 1.20.1 一致的两段式）：

1. **WORK**：`MaidFindSitTask` 在 16 格内找**船**或**女仆坐垫**，走过去坐下；
   找不到时冒出气泡 `chat_bubble.touhou_little_maid.inner.fishing.no_sit`。
2. 坐下后活动自动切到 **RIDE_WORK**（`MaidUpdateActivityFromSchedule`），
   `MaidRideFindWaterTask` 螺旋扫描水面 → 抛钩 → 等待（100~600 tick，受饵钓缩短）→
   水花粒子 → 咬钩音效 → 收杆，战利品直接弹到身边，鱼竿 -1 耐久。
   离钩超 16 格或换掉鱼竿立刻收回。

> 早期版本因为 GeckoLib 管线在「骑乘 + 非人形模型」时把模型上下翻转，绕开成
> 「岸上直接抛钩」（`MaidFishingCastTask`）。骑乘倒置修好后那个类已删除 ——
> 两者同时挂在 WORK 会让女仆永远优先岸钓，坐船钓鱼根本不会发生。
**卡住时看日志。** 这条链路一旦不成立是完全静默的，所以 `MaidRideFindWaterTask`
会在**状态发生变化**时打一条（同一个状态不重复打）：

```
[Futo] fishing maid=<uuid> at=<pos> -> cast at BlockPos{x=0, y=100, z=0}
[Futo] fishing maid=<uuid> at=<pos> -> no water within 6 blocks
[Futo] fishing maid=<uuid> at=<pos> -> main hand is not a fishing rod: 1 air
[Futo] fishing maid=<uuid> at=<pos> -> water at BlockPos{...} is out of range or restriction
```

四种分别对应：正常抛钩 / 6 格内没水 / 主手不是鱼竿 / 水超出 16 格活动范围。
**一条都没有**就说明 `RIDE_WORK` 根本没激活 —— 即女仆不是真正的乘客
（只是站在船或坐垫上，`Vehicle` 为空），或者当前日程不是上班时间。
`MaidUpdateActivityFromSchedule` 另外打一条骑乘活动日志，**这一条决定上面那四种到底会不会发生**：

```
[Futo] ride maid=<uuid> schedule=work rider=tlm_ride_work isWork=true  task=touhou_little_maid:fishing mainHand=1 fishing_rod
[Futo] ride maid=<uuid> schedule=rest rider=tlm_ride_rest isWork=false task=touhou_little_maid:fishing mainHand=1 fishing_rod
```

**只有 `isWork=true` 才会抛钩。** 这不是 bug，是上游设计：`RIDE_WORK` 是唯一挂着
`MaidRideFindWaterTask` 的活动。非工作时段（`schedule=rest` / `idle`）她坐在船上只是发呆，
而且对 `workPointTask()` 为真的任务（含钓鱼）会被**主动赶下船** —— 实测切到 18000 tick 后
`boat.Passengers` 立刻变空。所以「坐在船里却一直不钓鱼」的第一嫌疑就是**当前不是上班时段**，
把她自己的日程表调成工作时段（或用 `/tlm` 改 `MaidScheduleMode`）再试。

**还有一条自愈。** `MaidRideFindWaterTask` 的启动前提是 `maid.fishing == null`，
而这个字段**不落盘**、只在 `MaidFishingHook#remove` 里清空。钩子若在没走到 `remove` 的路径上被回收
（区块卸载、`/kill`、客户端侧先移除），引用会永久残留 —— 表现为「坐进船里以后再也不抛钩」，
换任务 / 下车 / 重上船都救不回来，只有重进存档才行。现在每次尝试启动时若发现钩子已
`!isAlive()` 就顺手清掉，代价只有一次 `isAlive()` 调用。
#### 「站得远了就永远不抛钩」：`posCheck` 与搜索的语义不一致（已修）

日志里最典型的形态是这样，而且它**每 5 tick 重复一次**、永远不会变成 `cast at`：

```
[Futo] ride ... isWork=true task=touhou_little_maid:fishing mainHand=1 fishing_rod
[Futo] fishing ... at=BlockPos{x=4, y=101, z=2} -> water at BlockPos{x=-1, y=100, z=-1} is out of range or restriction
```

原因在 `searchForDestination` 与 `posCheck` 用的**不是同一种距离**：

| | 依据 | 覆盖范围 |
|---|---|---|
| 搜索 | `BlockPos` 的整数偏移 `setWithOffset(centre, x, y-1, z)`，`i < searchRange` | x/z 可达 5 、y 可达 -1…+2 |
| 判定 | `maid.distanceToSqr(new Vec3(水块最小角))`，阈值 searchRange * searchRange = 36 | 浮点，且**从方块角落算** |

#### 鱼竿放副手也要能钓鱼（本移植版放宽）

上游 1.20.1 全链路只看 `getMainHandItem()`：抛钩、收线、扣耐久、界面条件说明都是。
玩家把鱼竿放副手是很自然的习惯（主手留武器 / 工具），后果是**她坐在船里完全不动，
而且没有任何提示** —— `has_fishing_rod` 条件只影响界面上的说明文字，不会弹消息。
这个坑极难自诊断：看起来和「抛钩逻辑坏了」一模一样。

现在统一走 `util/FishingRodUtil`：**主手优先、副手兜底**。四条链路全部改用它：

| 位置 | 原来 | 现在 |
|---|---|---|
| `TaskFishing#getConditionDescription` | 主手 `instanceof FishingRodItem` | `FishingRodUtil.hasRod` |
| `MaidRideFindWaterTask#start` | 主手取竿 | `getRod`（任一手） |
| `MaidFishingHook#shouldStopFishing` | 主手是竿才不收线 | `hasRod`（任一手） |
| `MaidFishingHook#onNibble` / `hurtRod` | 主手扣耐久 | 按**实际持竿的那只手**扣 |

主手有鱼竿时行为与上游逐字一致，所以这是纯放宽，不会让任何原本可用的组合失效。
扣耐久必须和取竿用同一个 `InteractionHand`，否则会出现「用副手的竿钓鱼、扣主手物品耐久」。

实测（`runServer` + RCON，主手 `stone_sword`、副手 `fishing_rod`）：

```
[Futo] ride ... isWork=true task=touhou_little_maid:fishing
[Futo] fishing ... at=BlockPos{x=0, y=101, z=0} -> cast at BlockPos{x=2, y=100, z=0}
HandItems: [{id: "minecraft:stone_sword", ...}, {id: "minecraft:fishing_rod", tag: {Damage: 2}}]
```

最后一行是关键：钩子抛出、且耐久扣在**副手**那根竿上（`Damage` 0 → 2，岸上收杆扣 2）。

> 排查记录：这个坑让本移植版误判了好几轮 —— 服务端日志一切正常、鱼钩也生成了，
> 但玩家看不到任何动作。教训是**先问清「你把鱼竿放哪只手了」**，再怀疑代码。
> 真正因此修掉的独立 bug 见上一节（`posCheck` 与搜索语义不一致）。

两者不等价：女仆站在方块角落（浮点坐标 x.5）时，偏 5 格、y 偏 1 的水块
折成浮点平方距离可以到 43+。于是**刚搜到的水立刻被判「超出范围」→ `waterPos` 清空 →
下一轮又搜到同一片水**，死循环。上游没暴露这个问题是因为它总在船上、船紧贴水边；
本移植版的船放在草地上时就会稳定复现，而且玩家只能看到「她坐在船里一动不动」。

修法是让判定与搜索同一语义：改用 `Vec3.atCenterOf(waterPos)`（方块中心，而不是角落），
阈值放宽到 `(searchRange + 2) * (searchRange + 2)²`（覆盖上面那张表的全部可能，并容忍浮点误差）。
自测：女仆距船 11 格、水在搜索半径边缘时，修复前永远 `out of range`，修复后正常 `cast at` 且鱼钩持续存活。

#### 客户端看不到浮标的三处「一票否决」（已修）

`MaidFishingHook` 继承的是 `Entity` 而不是 `Projectile`（1.18.2 的 `Projectile` 构造函数是包私有的），
所以 owner 要自己维护。早期实现里有三处「拿不到 owner 就自我了断」，任何一处触发都是
**服务端日志照写 `cast at`、客户端什么都看不到**：

1. `tick()` 里 `maid == null` 就 `discard()`（上游继承 `Projectile`，不会走到这条分支）；
2. `recreateFromPacket` 里 owner 解析失败就 `kill()`；
3. `MaidFishingHookRenderer#render` 里 `maid == null` 就 `return`（连浮标都不画）。

现在分别是：客户端不丢弃自己、只记一条 warn、渲染器**永远画浮标**（鱼线依赖女仆手部坐标，
拿不到就跳过）。这样即使 owner 解析失败，玩家至少能看见抛出去的钩子。

顺带确认过 `ClientboundAddEntityPacket(Entity, int)` 的第二个参数确实写进 `data` 字段
（`javap -c` 里 `iload_2` 直接进构造器），与 `Projectile#recreateFromPacket` 的 `getData()` 对得上。

**鱼钩要求她仍然坐着。** `MaidFishingHook#shouldStopFishing` 恢复成上游的五个条件
（活着 / 仍在骑乘 / 任务是钓鱼 / 主手是鱼竿 / 距离 < 16 格）。
早期版本为了岸钓把「仍在骑乘」去掉了，现在必须加回来，否则玩家把她从船上抱走之后
鱼钩会继续留在水里、她站在岸上也还在「钓鱼」。

### `feed` 的挑选顺序（HIGH → LOW → LOWEST）

| 情况 | 结果 |
|---|---|
| 你有「有害 + 剩余 > 3 秒」的状态，背包有牛奶 | 立刻喂（HIGH） |
| 食物回复的饱食度**正好等于**你缺的饥饿值 | 喂（HIGH） |
| 食物回复的饱食度**小于**你缺的 | 喂（LOW） |
| 食物回复的饱食度**大于**你缺的 | 只有在你血量 < 50% 时才喂（LOWEST） |

所以「满饥饿 + 满血 + 拿牛排」她不会喂 —— 设计如此。
`feed_animal` 同时实现了 `IAttackTask`：范围内动物超过配置上限（默认 50）时她会拿武器去杀，
这是上游防卡服的设计。想复现就 `/summon` 60 只牛，再给她一把剑。

## 已知问题

- **仍未移植**：`MaidModelDetailsGui`（女仆自己的换模型界面，与模型切换器无关 ——
  后者已用独立 Screen 实现，见文末「模型切换器」一节）、图标缓存、彩蛋模型、声音包 GUI。
  完整清单见上方「进度总览」。
- **祭坛配方渲染（`AltarRecipeComponent`）已可用**：28 个 `altar_recipe` 页会画出真实的
  六个材料槽、灵力消耗、产物槽与产物实体。上游那句「产物是盒子时就改画女仆」的特判
  （`InitEntities.BOX` → `InitEntities.MAID`）在盒子移植完成后已经补回。
- **祭坛配方的 `c:` 标签必须自己补。** 这个环境里没有任何模组提供 `c:` 约定标签
  （Fabric API 0.77.0 的全部模块里 `data/c/tags/` 一个文件都没有，已用 zip 逐个确认），
  而上游写的是 Forge 自带的 `forge:gems/diamond` / `forge:ingots/gold` / `forge:dyes/*`。
  移植时只补了 9 个，另外 12 个（diamonds / emeralds / lapis / redstone_dusts /
  gold_ingots / iron_ingots 加六种染料）漏了。缺标签的 `Ingredient` 物品列表为空，
  `RecipeMatcher` 永远匹配不上，**27 个祭坛配方里有 16 个因此完全做不出来**
  —— 包括神社复活的 `reborn_maid` 和召唤女仆的 `spawn_box` 这两个最核心的配方。
  现已补齐，`validate-book.ps1` 也加了这条静态检查。
- **部分祭坛配方未收录**，因为产物本身还没移植：
  书柜 / 扫帚 / 中国象棋 / 电脑 / 五子棋 / 键盘 / 野餐篮 / 西洋棋 / 无线 IO。
- **「搜索 / 定位」四件套已全部可用**（`trumpet` / `servant_bell` / `fox_scroll` ×2 / `kappa_compass`）：
  前三个告诉你**她在哪儿**，河童的罗盘决定**她该在哪儿**。
  - 之前缺的是它们共用的那块地基：`MaidWorldData#addInfo` **从未被调用过**，
    记录表永远是空的，所以仆从铃、小号、狐狸卷轴三件全都「什么也找不到」。
    上游靠 `EntityMaid#onRemovedFromWorld` 写记录，而那是 **Forge 对 `Entity` 的补丁**
    （`javap` 确认 vanilla 1.18.2 的 `Entity` 既无 `onAddedToWorld` 也无 `onRemovedFromWorld`，
    Fabric API 0.77 的 `fabric-entity-events-v1` 里也没有 `ServerEntityEvents`）。
    替代方案是 `event/MaidWorldDataTicker`：每 60 tick 刷新一次所有女仆的坐标，
    读取时再按「维度一致 + 区块未加载」过滤，同时清掉「区块已加载但她不在实体表里」
    的幽灵记录（否则铃会指着一个已经不存在的坐标）。因为改成了周期性刷新，
    `addInfo` 必须从**追加**改成**覆盖**，否则存档每 3 秒膨胀一条重复记录。
  - 河童的罗盘补齐了真正的 `entity/passive/SchedulePos`：工作 / 娱乐 / 睡觉**三个点**
    加维度。早期版本把它们压成一个同步字段 `DATA_HOME_POS`，于是日程表在
    `WORK` / `IDLE` / `REST` 之间切换时活动范围不会跟着挪 —— 三个点里只有一个生效。
    现在 `SchedulePos` **不进同步数据**，客户端要看区域时由 `MaidAreaPacket` 按需发一份
    （上游 `SyncMaidAreaMessage` 同款）。`MaidAreaRenderer` 也因此重新变成
    「三个圆心各自独立」，两点不重合时还会画白色连线。
  - **手持罗盘时就会画出三个圈**（`client/render/CompassAreaRenderer`）。
    这是**上游 1.20.1 的 `CompassRenderEvent`，1.18.2 没有**，属于按 1.20.1 补齐：
    设点时坐标还在罗盘的 NBT 里、没有任何女仆牵涉其中，光靠 `MaidAreaRenderer`
    （入口是「拿原版指南针右击女仆」）什么都看不到 —— 玩家记下工作点后，
    完全不知道那个圈有多大、会不会把床圈进去。
    它不需要网络包（手持物品栈本来就同步），但 `ItemKappaCompass#useOn` 写完 NBT 后
    必须 `inventoryMenu.broadcastChanges()`，否则要等下一次背包全量同步圈才冒出来。
    画法与 `MaidAreaRenderer` 共用 `renderTripletLines` / `renderTripletTexts`，
    半径 `+0.1 / 原值 / -0.1`、白线只在两点不重合时画、标签重合时逐级抬高等细节只有一份实现。
    注意 `getPoint` 有回退链（工作 → 娱乐 → 工作），所以**只设一个点时三个圈是重合的**，
    这是上游行为，靠标签抬升错开显示。
  - 开 Home 的两个拒绝条件照抄上游：记录在**别的维度**、或离三个点**超过 32 格**。
    两种情况都会弹 `CheckSchedulePosScreen`（上游 `CheckSchedulePosGui`）——
    用界面而不是聊天栏，是因为玩家此刻正开着女仆界面，聊天栏被挡住看不见。
  - 「潜行右击女仆清空作息点」是**河童的罗盘**的动作
    （`ItemKappaCompass#interactLivingEntity`）；原版指南针只负责把坐标发给客户端的
    区域渲染（上游 `MaidAreaClickEvent`）。早期版本把两者混在一起，
    导致「潜行 + 原版指南针」会顺手关掉她的居家模式。
- **6 个帕秋莉页面**暂存在 `port/book_pages_held_back/`，等对应功能补齐再放回去
  （棋类与娱乐方块 3 页 / 扫帚 1 页 / 野餐与娱乐 1 页 / 无线 IO 1 页）。

## 其他记录

- `EntityMaid#createAttributes()` **不覆写 `MOVEMENT_SPEED`**，用 vanilla 默认的 `0.7`。
  写 `0.35` 会让每个 AI 任务的移动速度整体减半（`speed × MOVEMENT_SPEED`）。
  `MAX_HEALTH` 也不要写死，`FavorabilityManager#refreshAttributes()` 会按好感度重设。
- `getStandingEyeHeight` 上游是 `size.height * (isMaidInSittingPose() ? 0.65F : 0.85F)`，
  坐姿时降低，让「看向玩家」的判定点仍在头部。
- 物品说明的「女仆饰品 / 需要放置在女仆的饰品栏中」用 Fabric 的 `ItemTooltipCallback`
  实现（上游是 Forge 的 `ItemTooltipEvent`），用 `BaubleManager` 判定，
  这样 `minecraft:totem_of_undying` 也能覆盖。
- **Mixin invoker 的坑**：`@Invoker` 的参数列表以目标类开头时会被当成**静态** invoker。
  `private void breakItem(ItemStack)` 的正确写法是
  `@Invoker("breakItem") void futo$breakItem(ItemStack stack);` ——
  `futo$` 前缀是必须的（invoker 会在目标类里生成同名方法）。
  目标方法带 `@Environment(CLIENT)` 时，mixin 必须放在 `client` 块里。