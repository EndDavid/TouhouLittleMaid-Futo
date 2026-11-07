# Touhou Little Maid: Futo（车万女仆：布都）

车万女仆（Touhou Little Maid）的 **Fabric 1.18.2 移植版**。`modid` 保持上游一致：`touhou_little_maid`。

- 上游（Forge 1.18.2，v1.2.2）：https://github.com/TartaricAcid/TouhouLittleMaid
- Fabric 1.20.1 参考移植：https://github.com/Sh1roCu/TouhouLittleMaid-Orihime
- Forge Config API Port 1.18.2：https://github.com/Fuzss/forge-config-api-port/tree/1.18.2
- 许可：CC-BY-NC-SA（继承上游）

> 本文只记录**经过游戏内验证**的结论。中途被推翻的假设一律不留（踩坑过程的最终结论见「版本陷阱」一节）。

---

## 目录结构

```
touhou_little_maid_futo/
├─ build.gradle / gradle.properties / settings.gradle
├─ libs/                            ← ForgeConfigAPIPort / cloth-config / night-config
├─ src/main/java/
│  ├─ net/minecraftforge/…          ← Forge → Fabric 兼容层（Futo shim）
│  └─ com/github/tartaricacid/touhoulittlemaid/
├─ src/main/resources/              ← fabric.mod.json / mixins / assets / data
└─ port/upstream/                   ← 上游 1.18.2 源码（1110 个文件，仅参考，不参与编译）
```

---

## 构建 / 运行

```powershell
cd 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
.\gradlew.bat build        # 产物：build/libs/touhoulittlemaid-fabric-1.18.2-futo-*.jar
.\gradlew.bat runClient    # 启动开发客户端（固定玩家名 Dev）
```

Gradle 守护进程用 **JDK 21**（Loom 1.15 要求），编译与运行游戏用 **JDK 17**（1.18.2 要求）。
两者已在 `gradle.properties` 里钉死，**不需要设 `JAVA_HOME`**：

```properties
org.gradle.java.home=D:/codename_EndDavid/hello java/jdk-21.0.2
org.gradle.java.installations.paths=D:/codename_EndDavid/hello java/jdk-21.0.2,C:/Program Files/Java/jdk-17
loom_version=1.15.5
```

报错原文与排查步骤见文末「版本陷阱」第 1 条。

---

## 进度总览

| 模块 | 状态 |
|---|---|
| 工具链 / Forge 兼容层 | 完成 |
| 渲染（GeckoLib3 魔改 + 模型包 + 动画） | 完成 |
| Brain 行为（活动 / 记忆 / 感知器 / 日程） | 完成 |
| 工作模式 | **20 个**，仅缺 `board_games`（棋类，依赖 `EntitySit`） |
| 女仆背包（7 种）+ 物品栏 | 完成 |
| 主界面 GUI + 左侧任务列表 | 完成 |
| **顶部三页签（主界面 / 任务设置 / 女仆配置）** | **完成**（服务端换菜单式切换） |
| **女仆配置界面（8 项子配置）** | **完成**（含语音频率、开门、爬梯） |
| **任务设置界面（默认提示 / 攻击名单）** | **完成**（攻击名单随菜单下发） |
| 饮食系统 | **完成**（工作餐 / 回血餐； HOME_MEAL 待野餐垫） |
| 配置系统（Forge Config API Port） | 完成，`AIConfig` 待补 |
| 声音（语音包 / 环境音 / 频率控制） | 完成 |
| 弹幕攻击 / 御币 | 完成 |
| 钓鱼 | 完成（岸钓） |
| **灵力（Power / P 点）** | 完成（Mixin 挂在玩家实体上） |
| 经验球 吸收 / 附魔之瓶 | **完成** |
| **女仆妖精（*EntityFairy*）** | 完成（旧模型；新模型缺 JSON，见渲染一节） |
| 网络层 / 容器 / 存档兼容 | 完成 |
| 祭坛 / 多方块 / 结构 / 进度 / 手册 | 未开始 |
| 自定义附魔（滞弹 / 速弹 / 末影之末） | 未开始 |
| 手持物品渲染 | **完成**（`GeckoLayerMaidHeld`，两条管线都走通） |
| 骑乘模型倒置 | 未开始 |
| 模组联动（JEI / REI / EMI / Patchouli / Jade / YSM / Iris / 等） | 未开始 |

---

## 工程与 Forge 兼容层

- **工具链**：Gradle 9.3 + Loom 1.15.5 + Loader **0.14.25** + Fabric API 0.77.0+1.18.2 + Java 17，
  使用 `officialMojangMappings()`（与上游 Parchment 命名基本一致，便于整包搬运）。
- **兼容层**：`Event` / `SubscribeEvent` / `IEventBus` + 反射式 `SimpleEventBus`、
  `DeferredRegister` / `RegistryObject` / `ForgeRegistries`、
  `ModLoadingContext` / `DistExecutor` / `ModList` / `OnlyIn`、`IItemHandler` 全套（含 `setStackInSlot`）。
- **Access-Widener 不可用的变通**：Loom 1.15 要求 AW 表头 `classTweaker …`，
  Loader 0.14.25 要求 `accessWidener …`，一个文件无法同时满足。
  因此改用 `util/MaidReflection` 反射构造 vanilla 的私有构造器
  （`MemoryModuleType` / `SensorType` / `Activity` / `Schedule`），AW 文件保留但不启用。
- **编码守卫**：`check-encoding.ps1`（纯 ASCII 脚本）扫描全项目的 U+FFFD 损坏。
  详见「版本陷阱」第 7 条。

---

## 渲染（GeckoLib3 管线）

### 关键结构

- **GeckoLib3 全量移植**（174 个类）去 Forge 化，实际改动仅 8 处
  （`ForgeRegistries.X.tags().createTagKey` → `TagKey.create`、移除 `RenderLivingEvent` 挂点、
  `Vector3f.setX` → `set`、自实现 `major.minor.patch` 版本比较、capability → 静态查表）。
- **动画状态机** `AnimationRegister` + `AnimationState` + `Priority` + `AnimationManager`，
  优先级与上游一致（死亡 / 睡觉 / 游泳 / 爬梯 → 载具 / 坐下 → 受击 / 跳跃 → 跑 / 走 → 待机），
  另有持物 / 挥击 / 使用 / 护甲 / 乘客 / 并行层控制器。
- **per-entity 动画实例缓存**（`GeckoMaidEntity.getOrCreate`，按实体 id + 实例身份校验）。
  这是动画能动的关键：渲染器每帧取一次实例，若每次 `new`，动画进度永远停在 0。
  **必须按 id 索引而不是 `Map<Mob,…>`** —— 1.18.2 的 `Entity#equals/hashCode` 就是实体 id，
  区块重载后旧实例的动画对象会被"认领"给新实例，而旧实例已不再 tick ⇒ 动画谓词永不求值 ⇒
  模型定格。已补 `forceUpdate` / `MaidState`（头/身朝向变化或换模型时强制求值一次）。
- **模型包**：内置 7 个包已展开到 `assets/`（`touhou_little_maid` 905 文件、
  `littlemaid_peco` 336、`touhou_little_maid_old` 84、`next_update_model` 69、
  `authors_and_credits` 36、`geckolib` 24、`minecraft_15th` 12）。
  启动日志：`[Futo] Loaded 194 maid models (5 gecko prebuilt), 5 animation files`。
- **旧版模型（`is_gecko = false`，共 178 个）** 在加载时由 `GeckoModelLoader#normalizeBones`
  把骨骼名映射成 Gecko 命名（`head→Head`、`body→UpBody`、`armRight→RightArm`…）并补合成 `Root`，
  从而复用默认动画集。这是**近似**：旧版骨架缺少 `AllBody` / `UpperBody` / `AllHead` 等中间层。
- **动画合并优先级**：模型包自带的同名动画优先，默认动画只补齐缺失名字
  （上游 `if (!containsKey(name))`）。写反会导致骨骼名对不上 →「站着不摆臂」「坐下没反应」。

### 已经踩平的两个渲染问题

**1. 眨眼调度的来源**

模型把表情做成 **0 厚度贴片骨骼**（`blink` / `hurtBlink`）。模型包 `maid_model.json`
用 `animation/maid/default/head/*.js` 登记它们，但**那些 `.js` 在 jar 里根本不存在**
（1.18.2 jar 里 `.js` 数为 0，1.20.1 为 2，且都是 `authors_and_credits` 的展示动画）。
上游是在 `MaidBaseAnimation` / `MaidExtraAnimation` 里用 Java **硬编码**的，`javap -c` 可核对：

```
MaidExtraAnimation$4（head/hurt.js）
    hurtBlink.setHidden(!maid.onHurt());        // onHurt() 就是 hurtTime > 0

MaidBaseAnimation$2（head/blink.js）
    if (mob.isSleeping()) { blink.setHidden(false); return; }
    remainder = (ageInTicks + |uuid.leastBits| % 10) % 60;
    blink.setHidden(!(55 < remainder && remainder < 60));
```

已按此在 `MaidRenderer#updateOverlayBones`（`renderEarly` 里，动画 tick 之后、绘制之前）重现。
骨骼名兼容 4 种拼法：`blink` / `blink2` / `_blink` / `_bink`（后两个是上游笔误，但必须兼容）。

**2. 眨眼时脸部变亮 —— 法线被「视口相关」地翻转**

`IGeoRenderer#renderCube` 里曾有一段对 0 厚度立方体的「法线翻转修补」：

```java
if ((size.y==0 || size.z==0) && normal.x() < 0) normal.mul(-1,1,1);
if ((size.x==0 || size.z==0) && normal.y() < 0) normal.mul(1,-1,1);
if ((size.x==0 || size.y==0) && normal.z() < 0) normal.mul(1,1,-1);
```

它判断的是**变换后**的法线，而变换里含实体朝向与骨骼旋转 —— 于是同一块面在女仆转身后
时而被翻转、时而不被翻转（视口相关，不是几何属性）。而 1.18.2 的实体着色器正是用法线算亮度：

```glsl
vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
```

| 法线方向 | lightAccum |
|---|---|
| 朝上 `(0,1,0)` | **1.000** |
| 朝前 `(0,0,-1)`（正确值） | 0.739 |
| 朝侧 `(±1,0,0)` | 0.496 |
| 朝下 `(0,-1,0)` | 0.400 |

翻错后最多亮 35%。表情贴片、裙摆、翅膀、发丝全是 0 厚度立方体 → 只有「那一层」变亮，
实心的头和头发不受影响 —— 与观察到的现象完全吻合。**该补丁已删除。**

上游（`simplebedrockmodel` 的 `BedrockCubeBox#compile`）用**每面固定的法线数组**、从不翻转，
所以它天然没有这个问题 —— 这就是它「通用」的原因，不需要按模型特调。

### 与上游的渲染差异（均为有意保留）

- **手持物品渲染：已实现**（`client/renderer/entity/geckolayer/GeckoLayerMaidHeld`）
  上游对两条模型管线各有一套层，本移植版合成一个层，按「有没有 `*HandLocator`」分流：

  | 模型类型 | 定位骨骼 | 物品变换常量 | 上游来源 |
  |---|---|---|---|
  | 基岩（Gecko） | `RightHandLocator` / `LeftHandLocator` 的完整父级链 | `translate(0, -0.0625, -0.1)` + `rotX(-90°)` | `GeckoLayerMaidHeld#renderArmWithItem` |
  | 旧版（1.10 / 1.12） | `RightArm` / `LeftArm` | `rotX(-90°)` + `rotY(180°)` + `translate(±1/16, 0.125, -0.525)` | `LayerMaidHeldItem#renderArmWithItem` |

  **这些数值没有一个是我推的**，全部逐字来自上游 —— 作者已经调好了。
  之前「暂缓」是因为误以为要自己算坐标系；实际上不需要。

  关键数据：导出 `cirno.json` / `hakurei_reimu.json` 的骨骼表后可确认，
  旧版模型的 `armRight` / `armLeft` 是**顶层骨骼**（无 `parent` 字段，与 `body` 平级，
  `pivot` 分别是 `[-3,17.5,0]` / `[3,17.5,0]`）。所以上游 `BedrockModel#translateToHand`
  只变换这一根骨骼就够 —— 不是简化，是骨架本来就平铺。
  我们加载时把骨骼改名成 `RightArm` 并补了合成 `Root`，因此用同一个
  `RenderUtils#prepMatrixForLocator` 走 `Root → RightArm` 链即可，
  `Root` 携带的 `sit` 位移正好等价于上游「整个模型下移」。

  为了让层能挂上去，`MaidRenderer` 补了 `IGeoEntityRenderer` 的三个方法
  （`getGeoEntity` / `addGeoLayerRenderer` / `geoRender`），
  `ILocationModel` 补了 `boneHierarchy(String)` 默认方法（旧版模型没有 locator 骨骼）。

  未移植的上游分支：`SlashBladeCompat`（拔刀剑专用渲染）、
  `RenderFixer.isCarryOnRender`（CarryOn 把物品改成方块）、
  `TacCompat.addItemTranslate`（TACZ 枪械微调）、
  `armPositioningBone`（现有 189 个模型里没有一个用到这个字段）。
- **骑乘模型倒置**：`GeoReplacedEntityRenderer#setupRotations` 对「骑乘 + 非人形」处理不正确。
  钓鱼因此改用岸钓（`MaidFishingCastTask`），绕开该路径。
- **旧版模型的「使用」动画：`use_*` 有专属替代，其余降级为「持物」**
  `maid.animation.json` 的 `use_mainhand:bow` / `:crossbow` / `:gohei` / `:spear` 会扭
  `UpBody` 与 `Head`（弓是 `UpBody` yaw −77.49°、`Head` `[51.26, 70.44, 44.34]`），靠
  `AllBody` / `UpperBody` / `AllHead` / `Arm` 做补偿 —— 旧版骨架这些层全没有，
  照搬会「拉弓时头歪 70°」。上游对旧版模型根本不用基岩动画，而是 `MaidBaseAnimation`
  那套 Java 硬编码，其中「使用中」只有两条规则（`javap -c` 核对 1.18.2）：

  ```
  getArmDefault()   // arm/default.js
      isUsingItem() && usedItemHand == OFF_HAND  → armLeft.x  = init − 80°, armLeft.y  = init + 25°
      isUsingItem() && usedItemHand == MAIN_HAND → armRight.x = init − 80°, armRight.y = init − 20°
  getArmSwing()     // arm/swing.js
      !mainHandItem.isEmpty() && isSwingingArms()
          → armLeft( −1.396 rad, 0.785 rad ) = ( −80°, +45° )
            armRight(−1.396 rad, −0.174 rad) = ( −80°, −10° )      // 躯干与头部完全不动
  ```

  第二条就是上游「双手持械」姿态的来源，触发者是 `MaidShootTargetTask` /
  `MaidTridentTargetTask` 的 `setSwingingArms(true)` 与 `MaidAttackStrafingTask`（弹幕模式）。

  移植办法：`animation/maid_legacy.animation.json` 把 `use_mainhand:` / `use_offhand:` 的
  `bow` / `crossbow` / `gohei` / `spear` 与 `hold_*:charged_crossbow` **同名覆盖**成
  只含 `LeftArm` / `RightArm` 的 `[-80,45,0]` / `[-80,-10,0]`；`GeckoModelLoader#buildAnimations`
  只在 `!isGeckoModel()` 时合并这个文件，Gecko 模型完全不受影响。`AnimationManager` 用
  `LEGACY_USE_ANIMATIONS` 白名单放行这几支；其余（吃 / 喝 / 望远镜 / 盾）走
  `armed_pose` 之外的另一档 —— 只抬**正在使用的那只手**（见下面「举盾格挡的姿态」一节）。
  它们**不能**退回 `hold_mainhand:*`：那些动画带 `UpBody` / `Head` / `UpperBody` 关键帧，
  在平级骨架上会拧头，而且 `hold_offhand:shield` 本身就是一条 1 秒的挥臂曲线。

  代价：无。这一档姿势与上游 `MaidBaseAnimation#getArmDefault` 的数值一致
  （左 `-80°/+25°`、右 `-80°/-20°`），所以旧版模型吃 / 喝 / 举盾时抬的就是
  「正在用的那只手」，另一只手继续待机摆动。

- **旧版模型的举械姿态：由 `isSwingingArms()` 全程驱动，不经过 `hold_*`**
  上游对旧版模型根本不用基岩动画，`getArmSwing` 的条件只有一条 ——
  `!mainHandItem.isEmpty() && maid.isSwingingArms()`，与「有没有目标」「是不是在蓄力」
  都无关。也就是说这个标志由攻击行为的 `start` / `stop` 写入，**天然覆盖
  蓄力 → 松手 → 冷却 → 下一次蓄力整段**，手臂全程不动。

  移植时如果改成「`use` 结束就交给 `hold_*`」，会掉进两个坑：

  | 坑 | 原因 |
  |---|---|
  | 每射一次手臂甩一下 | 默认动画集里 `hold_mainhand:bow` 不是静态姿势，而是手臂 `0°` → `-94°` → `+20.7°` 的关键帧，`animation_length: 1000`；`hold_mainhand:spear` 同理，`hold_mainhand:crossbow` 则是单臂姿态 |
  | 待机也举着械 | 若把「任务是不是远程任务」当条件 —— 而任务（工作模式）是**长期状态**，与「此刻是否在攻击」无关 |

  所以实现方式改为：`animation/maid_legacy.animation.json` 里放一支 `armed_pose`
  （`LeftArm [-80,45,0]`、`RightArm [-80,-10,0]`，即上游 `-1.396 / 0.785 / -0.174` 弧度），
  `AnimationManager#predicateUse` 在 `isUsingItem()` **之前**判断
  `isLegacyModel() && isSwingingArms()` 就一直播它；同时
  `LEGACY_ARMED_HOLD_ANIMATIONS` 把旧版模型的 `hold_*:bow / :crossbow / :spear /
  :charged_crossbow` 全部拦掉（它们本来就不属于旧版模型），让未攻击时的手臂回落到
  `main` 的待机姿态。

  另外补了两处上游的缺失：

  - `MaidShootTargetTask#stop` / `MaidTridentTargetTask#stop` 都**没有**复位
    `setSwingingArms(false)` —— 标志一旦置真便永远为真，必须补上，否则第一次攻击之后
    旧版模型会一直举着手。`MaidAttackStrafingTask` / `MaidAttackTridentTask` 上游是有复位的。
  - 原版 `CrossbowAttack` 行为只写 `setChargingCrossbow`，**从不**写 `setSwingingArms`，
    所以弩在冷却期会被判成「不在攻击」。已在 `MaidCrossbowAttack#start / #stop` 里补上，
    使弓 / 弩 / 御币 / 三叉戟四类远程武器的旧版模型表现一致。

---

## Brain 行为与工作模式

上游 TLM 1.18.2 **不用 Goal**，全部是 `Activity` + `MemoryModuleType` + `SensorType` +
`Behavior` + `Schedule`；`goalSelector` 只保留 `FloatGoal`（游泳行为未移植，落水靠它兜底）。

### 20 个工作模式

| 类别 | 任务 |
|---|---|
| 无依赖 | `idle` / `attack` / `danmaku_attack` |
| 远程 | `ranged_attack`（弓）/ `crossbow_attack`（弩）/ `trident_attack`（三叉戟） |
| 背包 | `shears` / `milk` / `honey` / `torch` / `feed` / `feed_animal` |
| 农耕 | `farm` / `sugar_cane` / `melon` / `cocoa` / `grass` / `snow` |
| 其它 | `extinguishing`（灭火）/ `fishing`（钓鱼） |

未移植：`board_games`（棋类，需要 `EntitySit` 娱乐方块子系统）。

**远程类的共同点**：目标搜索走 `EntityMaid#canSee`，射程由各 Task 的 `canSee` 重写，
默认读配置（弓 48 / 弩 64 / 弹幕 64 / 三叉戟 48）。

**远程类的走位**：弓 / 弩 / 弹幕 / 三叉戟都用 `MaidRangedWalkToTarget`（**不是**原版的
`SetWalkTargetFromAttackTargetIfTargetOutOfReach`）。区别在于原版那个只有进入近战距离
（约 2 格）才停止写 `WALK_TARGET`，而 `MaidAttackStrafingTask` 的前置条件是
`WALK_TARGET` 必须**不存在** —— 用原版会让女仆一路贴脸，同时把走位任务永久挡住。
`MaidRangedWalkToTarget` 改成「`canSee` 且距离 < 工作半径 × 0.65 就清掉 `WALK_TARGET`」。
近战 `attack` 仍用原版那个（近战就该走到面前）。

### 底层设施

- `MaidPathFindingBFS` + `CenterOffsetBlockPosSet` + `ICachedEvaluator`：一次 BFS、多次惰性查询。
  农耕 / 插火把每 tick 要扫上百个候选方块，逐个 `createPath` 会卡服。
  注意 `canPathReach(pos)` 的语义是「<b>脚</b>能否站在 `pos` 或 `pos.above()`」——
  所以实心的西瓜 / 南瓜永远不可达，`MaidFarmMoveTask#checkPathReach` 退化到四邻格判定。
- `MaidMoveToBlockTask`（抽象基类）：螺旋扫描 + 可达性判定 + 主人距离约束 + 最近工作点缓存。
- `EntityMaid` 方块操作：`canPlaceBlock` / `canDestroyBlock` / `destroyBlock` /
  `dropResourcesToMaidInv`（掉落直接进背包）/ `placeItemBlock` / `getBrainSearchPos` /
  `getDestroyProgress`（重写 `Player#getDestroyProgress` 的公式，因为女仆不是 `Player`）。
- `MaidBlockPlaceContext`：1.18.2 的 `BlockPlaceContext(Level, Player, …)` 是 `protected`。

### 弹幕攻击

- 实体 `EntityDanmaku` **继承 `ThrowableProjectile`**（它的三个构造函数都是 `protected`，
  可用原版完整投掷物管线）。注意 `Projectile` 本身是包私有构造 —— 鱼钩因此只能 `extends Entity`。
- 形态按「周围**同类型**可攻击且可见目标数」三档：≤1 单发（有多重射击 → 3 发 15° 扇）/
  ≤5 八发 60° 扇 / >5 三十二发 120° 扇。谓词是上游 1.20.1 的 `enemyEntityTest`：
  `canAttack && sameType && canSee`。少了 `sameType` 会导致「只打一只也放一圈」。
- 渲染：`textures/entity/danmaku.png` 416×128 = 13 列（颜色）× 4 行（形状），每块 32×32，
  起址 `(32 × color, 32 × type)`；billboard 四边形 + `itemEntityTranslucentCull`。
- 友伤保护顺序（与上游 1.20.1 一致，最后一条为本移植版加强）：
  ① 女仆弹幕不伤玩家 → ② 女仆弹幕不伤**任何**女仆 → ③ 同主驯服生物不互伤 →
  ④ `MAID_RANGED_ATTACK_IGNORE` 名单 → ⑤ 命中。
  ② 是必须的：上游只比 `ownerUUID`，而它可能为 `null`（未驯服 / `Owner` 丢失），那时会漏判。
- 撞到**没有碰撞体积**的方块不消失；存活 200 tick 后自动消失。

---

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

穿脱规则与上游一致：手拿背包物品右键 = 装上（旧背包掉给玩家）；手拿剪刀右键 = 脱下
（剪刀 -1 耐久）；换装后 20 tick 冷却，避免主副手各处理一次导致「来回换装」。
换小背包时超出容量的物品按上游规则掉到地上。

### 主界面

- 贴图取自上游 Forge 1.18.2 发布 jar（`maid_gui_main/side/button/backpack/task.png`），
  尺寸 256×256，槽位坐标与上游逐一对应。
- **版式约定（踩过坑）**：`renderBg` 里的 `PoseStack` **没有**被平移到 GUI 原点 → 纹理用
  `leftPos + x` 绝对坐标；`renderLabels` 里的**已经**平移过 → 文字用相对坐标。
- **压暗层与 Tooltip 都须手动调用**：1.18.2 的 `AbstractContainerScreen#render` 既不调
  `renderBackground` 也不调 `renderTooltip(PoseStack,int,int)`（字节码确认）。顺序固定为
  压暗 → `super.render` → 槽位提示 → 按钮提示。
- **左侧任务列表**：面板 `(0,0) 92×251`、每页 12 条、行高 19。一次性建完所有按钮，
  翻页只切 `visible`（上游是每页 `init()` 重建，要求 `clearWidgets` 必被调用，更脆）。
  当前任务的图标与名称必须在 `super.render` **之后**画，否则被开关按钮底图盖掉。
- **空槽提示图标**（上游 `Slot#getNoItemIcon`）：护甲 2×2 用原版 `item/empty_armor_slot_*`、
  副手用 `empty_armor_slot_shield`，这五个 sprite 本来就在原版 `blocks` 图集里，直接走原版逻辑即可。
  主手 `slot/empty_mainhand_slot.png` 与背包第 6 格 `slot/empty_back_show_slot.png` 是模组贴图，
  1.18.2 的 Fabric 没有 Forge 的 `TextureStitchEvent`、也没有图集定义 JSON（1.19.3 才有），
  进不了图集 → 由 `MaidMainScreen#drawManualSlotHints` 在 `renderBg` 里按贴图路径直接画。
  注意 1.18.2 的 `AbstractContainerScreen#renderSlot` 是 **private**，不能重写来补画。

  切换走 C2S 包由服务端写入（任务 id 是同步数据，本地先改会被下一次同步覆盖）。
- **只读同步值、只写意图**：`homeModeEnable` / `pickup` / `task` 都是 `SynchedEntityData`。
  客户端点了按钮只发包，服务端校验 `isOwnedByPlayer` 后写。

### `/tlm` 命令（权限等级 2）

| 命令 | 作用 |
|---|---|
| `/tlm task <targets> [id]` | 查看 / 设置工作模式 |
| `/tlm schedule <targets> [day\|night\|all]` | 查看 / 设置日程 |
| `/tlm home <targets> <true\|false>` | 居家模式（只在工作范围内活动） |
| `/tlm pickup <targets> <true\|false>` | 拾取开关（总开关） |
| `/tlm pickup <targets> type <item\|xp\|all>` | 拾取类型：只捡物品 / 只捡经验与 P 点 / 全捡 |
| `/tlm model <targets> ["<id>"]` | 查看 / 设置模型（可省略命名空间） |
| `/tlm backpack <targets> [<id>]` | 查看 / 设置背包类型 |
| `/tlm inv <targets> [clear [void]]` | 打印物品栏 / 清空（掉地上 / 直接销毁） |
| `/tlm info <targets>` | 诊断：tame / owner / task / sitting / home / 工作范围 |
| `/tlm power <players> [set\|add\|min <0~5>]` | 查看 / 修改灵力（对应上游 `/power`） |
| `/tlm claim <targets>` | 重新认主 |

> **`/data merge` 改不动工作模式。** `MaidTask` / 日程 / 居家 / 拾取 / 模型都是
> `SynchedEntityData`，NBT 里同名字段只在读存档时解析。请用 `/tlm`。

---

## 配置系统（Forge Config API Port）

上游 TLMO 1.20.1 就依赖它，因此本移植版直接复用 `net.minecraftforge.common.ForgeConfigSpec`，
配置代码按上游原样搬运，不自己造轮子。

### 依赖的四个 jar

| 位置 | jar | 作用 |
|---|---|---|
| `libs/` | `ForgeConfigAPIPort-v3.2.4-1.18.2-Fabric.jar` | `ForgeConfigSpec` / `ModLoadingContext` |
| `libs/` | `cloth-config-6.5.133-fabric.jar` | 配置界面（`suggests`，非 `depends`） |
| `libs/` | `core-3.6.5.jar` / `toml-3.6.5.jar` | night-config，从 Port 的 `META-INF/jars/` 解出来的 |

后两个**必须单独作为 `modImplementation`**：Loom 只对「正规模块坐标」展开 JiJ，
对 `files()` 本地文件不展开，否则启动报
`requires any version of com_electronwill_night-config_core, which is missing!`。
不能用 `compileOnly`（那只有编译期可见）。也不能用 `include`（它只接受模块坐标，
报 `not a module component and has no capabilities`）。

### 配置项

首次启动后生成 `run/config/touhou_little_maid-common.toml` 与
`saves/<world>/serverconfig/touhou_little_maid-server.toml`。

| 类 | 内容 |
|---|---|
| `config/GeneralConfig` | COMMON 聚合入口 |
| `config/subconfig/MaidConfig` | 主体，约 35 项（工作范围、射程、驯服物品、黑名单…） |
| `config/subconfig/MiscConfig` | 妖精 / 灵力 / 神社灯 / 稻草人 |
| `config/subconfig/ChairConfig` / `VanillaConfig` / `RenderConfig` | 占位（子系统未移植） |
| `config/ServerConfig` | SERVER（下载包 URL、AI 耗时检测） |

`AIConfig` 未移植：依赖尚未移植的 `ai.manager.setting.AvailableSites`。

### 必须遵守的约定

**不要直接调 `MAID_WORK_RANGE.get()`。** `ForgeConfigSpec.Value#get()` 在配置尚未加载时
会抛 `IllegalStateException`，而女仆实体的静态初始化、渲染层读取都可能早于配置加载。
`MaidConfig` 提供了 `workRange()` / `danmakuRange()` 等安全辅助方法，未加载时回退上游默认值。

已接线：`MAID_NON_HOME_RANGE`(8) / `BOW_RANGE`(48) / `CROSS_BOW_RANGE`(64) /
`DANMAKU_RANGE`(64) / `TRIDENT_RANGE`(48) / `FEED_ANIMAL_MAX_NUMBER`(50) /
驯服·诱惑·NTR 物品 / 背包黑名单 / 攻击与远程忽略名单 / `OWNER_MAX_MAID_NUM`。

---

## 声音

上游的语音不是随 jar 发布的，而是由「声音包」提供：服务端拦截路径以 `maid` 开头的音效，
改发 `PlayMaidSoundMessage(soundEvent, soundPackId, entityId)`，客户端用 `MaidSoundInstance` +
`SoundCache` 直接从声音包 ogg 取音频。那套自定义 SoundEngine 尚未移植。

本移植版改成用原版机制等价实现：从上游 1.20.1 jar 的 `littlemaid_peco` 包提取
**332 个 ogg**（7.5 MB）到 `assets/touhou_little_maid/sounds/maid/{ai,mode,environment}/`，
把 `sounds.json` 里每个键的 `sounds` 数组从「单个静音把位」改成**该键的全部变体**，
由原版随机挑选。

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

### 密度与并发

原版 `Mob#baseTick` 的触发是 `random.nextInt(1000) < ambientSoundTime++`，
`resetAmbientSoundTime()` 把计数重置为 `-getAmbientSoundInterval()`。
默认 `interval = 80` 时平均间隔只有约 125 tick（≈6 秒），而且 `playAmbientSound()`
**完全没有并发控制** —— 场上三五只女仆时每 6 秒各冒一句，听起来像「一直在说话」。

上游靠客户端概率过滤（`MaidSoundFreqEvent`：`soundFreq × 全局频率`），但默认值是
`1.0 × 100%`，**不过滤**；真正压住密度的是它那套自定义 SoundEngine 通道。

本移植版改用两层控制（`EntityMaid`）：

| 机制 | 实现 | 效果 |
|---|---|---|
| 单只间隔 | `getAmbientSoundInterval()` → 240 | 期望 `240+45 ≈ 285 tick ≈ 14 秒` |
| 全局节流 | 重写 `playAmbientSound()`，两次语音至少隔 60 tick | **同屏多少只女仆，同一时刻只有一个在说话** |

全局节流用 `level.getGameTime()` 做时间轴；跨维度时差值为负 → 判定为「不同时间轴」直接放行。

坐下 / 起立音效照抄上游 `SwitchSittingEvent`：
`playSound(SoundEvents.ITEM_PICKUP, 0.2F, ((rand-rand)*0.7+1)*2)` ——
是原版拾取音效（不是语音包的 `maid.ai.item_get`），音调升一个八度。

---

## 测试示例

所有用例都假设女仆**已驯服**、且执行过
`/tlm pickup @e[type=touhou_little_maid:maid,limit=1] true`。
`/give` 只能给玩家，所以物品用 `summon minecraft:item` 丢在她脚下让她自己捡。

### 无依赖 / 远程

```
# 近战
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:iron_sword
/tlm task @e[type=touhou_little_maid:maid,limit=1] attack

# 弓（箭丢脚下，靠 pickup 捡进背包；附「无限」后不消耗）
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:bow
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:arrow",Count:32b}}
/tlm task @e[type=touhou_little_maid:maid,limit=1] ranged_attack

# 弹幕（1 只僵尸 → 单发；6 只 → 32 发扇）
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with touhou_little_maid:hakurei_gohei
/tlm task @e[type=touhou_little_maid:maid,limit=1] danmaku_attack
/summon minecraft:zombie ~6 ~ ~

# 逃跑：空闲模式下把苦力怕放到她附近
```

### 背包类

```
# 剪羊毛
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:shears
/tlm task @e[type=touhou_little_maid:maid,limit=1] shears

# 挤奶（必须留一个空槽放奶桶，背包满时她不动 —— 上游行为）
/summon minecraft:cow ~ ~ ~
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:bucket",Count:4b}}
/tlm task @e[type=touhou_little_maid:maid,limit=1] milk
/tlm inv  @e[type=touhou_little_maid:maid,limit=1]

# 采蜜（honey_level 必须 = 5；/setblock 的蜂箱会顺带注册 POI，没反应就等 1~2 秒）
/setblock ~2 ~ ~ minecraft:beehive[honey_level=5]
/tlm task @e[type=touhou_little_maid:maid,limit=1] honey

# 喂食（挑食规则很严格，见下表）
/effect give @s minecraft:poison 30
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:milk_bucket",Count:1b}}
/tlm task @e[type=touhou_little_maid:maid,limit=1] feed

# 喂养动物
/summon minecraft:cow ~2 ~ ~
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:wheat",Count:16b}}
/tlm task @e[type=touhou_little_maid:maid,limit=1] feed_animal
```

`feed` 的挑选顺序是 `HIGH → LOW → LOWEST`：

| 情况 | 结果 |
|---|---|
| 你有「有害 + 剩余 > 3 秒」的状态，背包有牛奶 | 立刻喂（HIGH） |
| 食物回复的饱食度**正好等于**你缺的饥饿值 | 喂（HIGH） |
| 食物回复的饱食度**小于**你缺的 | 喂（LOW） |
| 食物回复的饱食度**大于**你缺的 | 只有在你血量 < 50% 时才喂（LOWEST） |

所以「满饥饿 + 满血 + 拿牛排」她不会喂 —— 设计如此。

`feed_animal` 同时实现了 `IAttackTask`：**范围内动物超过配置上限（默认 50）时她会拿武器去杀**，
这是上游防卡服的设计。想复现就 `/summon` 60 只牛，再给她一把剑。

### 农耕类

```
# 普通作物
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

# 除草 / 除雪
/tlm task @e[type=touhou_little_maid:maid,limit=1] grass
/fill ~2 ~ ~ ~4 ~ ~2 minecraft:snow[layers=1]
/tlm task @e[type=touhou_little_maid:maid,limit=1] snow
```

| 任务 | 判定 | 备注 |
|---|---|---|
| `farm` | 成熟作物 | 无锄头则只把 Age 归零（作物继续长）并补种 |
| `sugar_cane` | 从下往上第 2 段起 | 连带上方一节一起收，留最下面 1 段继续长 |
| `melon` | 连着瓜藤的整块瓜 | 精准采集 → 掉完整瓜并补种 |
| `cocoa` | `age >= 2` | 可达性放宽到 3×3×2 |
| `grass` | 草 / 花 / 高草 | 只收不种 |
| `snow` | 雪片 | 持铲子才掉雪球并扣耐久；空手直接清除 |

> 收瓜曾经「完全没反应」，原因不在 `canHarvest` 而在可达性：`canPathReach(pos)` 的语义是
> 「**脚**能否站在 `pos` 或 `pos.above()`」，西瓜 / 南瓜是整块实心方块，`ground.above()`
> 永远站不进去。现在 `MaidFarmMoveTask#checkPathReach` 退化到四邻格判定。

### 灭火 / 钓鱼

```
# 灭火（主手必须是灭火器；找空旷处或先 /gamerule doFireTick false）
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with touhou_little_maid:extinguisher
/tlm task @e[type=touhou_little_maid:maid,limit=1] extinguishing
/setblock ~ ~ ~ minecraft:fire

# 灭火器的额外用法：攻击模式下副手持灭火器，打火免疫生物会补一层持续灼伤
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.offhand with touhou_little_maid:extinguisher
/summon minecraft:blaze ~2 ~ ~

# 钓鱼（岸钓：走到水边抛钩，不要求坐船）
/item replace entity @e[type=touhou_little_maid:maid,limit=1] weapon.mainhand with minecraft:fishing_rod
/tlm task @e[type=touhou_little_maid:maid,limit=1] fishing
```

灭火三种触发条件任一成立即喷（白色烟雾，寿命 3 秒）：主人着火 / 自己着火 / 2 格内有着火的
驯服生物。生效内容为移除 5×3×5 内的火焰方块、2 格内生物 `clearFire()`，但火免疫的敌对生物
反而受伤（每 5 tick 2 点魔法）—— 上游用来对付烈焰人。

钓鱼流程：扫描岸边 → 走到 5 格内 → 抛钩 → 等待（100~600 tick，受饵钓缩短）→ 水花粒子 →
咬钩音效 → 收杆，战利品直接弹到身边，鱼竿 -1 耐久。离钩超 16 格或换掉鱼竿立刻收回。

---

## 经验球与 P 点（M3）

女仆击杀敌对生物产生的经验不再以原版绿色小球呈现，而是换成东方风格的「点」贴片；
同时附带 **P 点** 掉落物（唯一常规来源是下面「女仆妖精」一节）。

### 客户端

- `ReplaceExperienceOrbRenderer` 覆盖 `EntityType.EXPERIENCE_ORB` 的渲染器，
  贴图取 `textures/entity/point_item.png`，按 `ExperienceOrb#getIcon()`（0~10）
  从横排图集里取对应格子。经验值越高，点越大。
- `EntityPowerPointRenderer` 渲染 P 点实体，贴图 `textures/entity/power_point.png`，
  同样按 `EntityPowerPoint#getIcon()` 取格子，并带一点上下浮动。
- 覆盖原版渲染器用的是 Fabric 的 `EntityRendererRegistry.register`：
  它在 `EntityRenderers.<clinit>` 的 **RETURN** 处才把模组注册项回放进
  `PROVIDERS`，所以能盖掉原版那一项（在 `ClientModInitializer` 里注册即可，不需要 Mixin）。

### 实体 / 物品

- `touhou_little_maid:power_point` 实体：`sized(0.5, 0.5)`、`clientTrackingRange(6)`、
  `updateInterval(20)`，会像原版经验球一样轻微朝玩家飘，`throwTime` 内不可拾取，
  6 分钟后消失，可存盘。
- `touhou_little_maid:power_point` 物品：右击沿视线丢出（俯仰压低 20°），
  落地后变成实体。因为没有移植上游的 `EntityThrowPowerPoint`，丢出动作直接给
  P 点实体初速度，少一个实体类型、手感一致。
- 数值换算：**4P = 1XP**；总 P 值按 1/3/5/7/13/34/89/185/285/385/485 的档位拆分。

### 女仆拾取

`EntityMaid#pushEntities` 现在每 tick 检查身边可拾取实体，分派到：

| 实体 | 行为 |
|---|---|
| `ItemEntity` | 塞进可用物品栏（原有逻辑） |
| `AbstractArrow` | `Pickup.ALLOWED` 的箭 / 光谱箭塞进物品栏 |
| `ExperienceOrb` | 加女仆经验（`tickCount > 2` 才生效，与上游一致） |
| `EntityPowerPoint` | 4P→1XP 折算后加经验 |

四者都先走「经验修补」：从护甲 / 双手 / 背包里随机挑一件带经验修补且已损耗的物品，
按原版比率修补（1 经验 = 2 耐久，消耗的经验减半计入），剩下的才计入女仆经验。

拾取类型对应上游 `PickType`（`item` / `xp` / `all`），存在同步数据
`DATA_PICKUP_TYPE` 里（字符串），读档损坏时回退 `all` 而不是抛异常：

```mcfunction
# 只看当前拾取类型相关的开关状态
/tlm info @e[type=touhou_little_maid:maid,limit=1]
# 只捡物品（不捡经验 / P 点）
/tlm pickup @e[type=touhou_little_maid:maid,limit=1] type item
# 只捡经验 / P 点
/tlm pickup @e[type=touhou_little_maid:maid,limit=1] type xp
# 全捡（默认）
/tlm pickup @e[type=touhou_little_maid:maid,limit=1] type all
```

`/tlm pickup <targets> true|false` 仍然是总开关，`type` 只在总开关打开时生效。

### 灵力（Power）与 P 点的关系

**1.00 灵力 = 100 P 点。** 这是整套系统唯一的换算比，贯穿配置、掉落物与玩家数值三处：

| 位置 | 单位 | 例子 |
|---|---|---|
| 配置 `MaidFairyPowerPoint` | 灵力 | `0.16` |
| 实体 `EntityPowerPoint#getValue()` | P 点 | `16` |
| 玩家 `PowerCapability#get()` | 灵力 | `0.16` |

玩家上限 `PowerCapability.MAX_POWER = 5.00`（= 500 P）。**满了还继续拾取时，溢出的部分按 4P = 1XP 折算成经验**
（上游 `EntityPowerPoint#playerTouch` 的整数算法，避免浮点累积误差）。

### 灵力的实现方式（无 Forge capability）

上游用 `Capability<PowerCapability>` + `ICapabilitySerializable` 挂在玩家身上。
Fabric 没有 capability 系统，本移植版改成三个小件：

| 文件 | 作用 |
|---|---|
| `capability/PowerCapability` | 纯数据（`add` / `min` / `set` / `get` / `dirty`），与上游逐行一致 |
| `capability/IPower` | 标记接口 + `IPower.getPower(Player)` 静态入口 |
| `mixin/PlayerPowerMixin` | 给 `Player` 加一个 `@Unique` 字段并实现 `IPower`（懒初始化） |
| `mixin/ServerPlayerPowerMixin` | 存档读写（NBT 键 `FutoPower`） |
| `event/PowerEvents` | 死亡扣减、重生复制、脏数据同步 |
| `network/PowerSyncPacket` | S2C 同步（只在 dirty 时发，与上游一致） |
| `client/event/ShowPowerEvent` | 手持御币时在 HUD 左上角显示 `×N.NN` |

**为什么字段长在 `Player` 上而不是外部 `Map<UUID, Float>`：** 玩家对象在切维度 / 重生 / 重连时会被替换，
外部表要么漏清理、要么在新对象上读到旧值；而且客户端（`RemotePlayer`）也需要同一份数据画 HUD。

**死亡惩罚拆成两步**（上游是在 `PlayerEvent.Clone` 里一次做完）：

1. `ServerPlayerEvents.ALLOW_DEATH` —— 在**旧**玩家身上扣掉 `PlayerDeathLossPowerPoint`（默认 1.00）；
2. `ServerPlayerEvents.COPY_FROM` —— 无论如何把旧值抄给新玩家。

`/gamerule keepInventory true` 时不扣（与上游一致）。
> wiki 上「死亡后**无论如何都会失去所有** P 点」的说法已过时：代码扣的是配置值，默认只扣 1.00。
> 想等价于「全丢」就把配置调到 5.00。

```
# 查看 / 修改灵力（对应上游 /power get|set|add|min）
/tlm power @s
/tlm power @s set 5
/tlm power @s add 1.5
/tlm power @s min 2
```

### 关于 wiki 那句「P 点越高，御币发出的弹幕越强」

**上游代码里没有这个机制。** 在 1.20.1 全树 grep `POWER_CAP` / `getCapability(PowerCapabilityProvider…)`，
读取灵力的地方只有六处：同步包、信标存取、P 点拾取、`/power` 命令、`BlockAltar`（祭坛建造消耗）、
以及 HUD 显示。**弹幕相关的 `TaskDanmakuAttack` / `DanmakuShoot` / `ItemHakureiGohei` 完全不读灵力。**

那条 wiki 描述要么是更早版本的遗留，要么是把「祭坛建造要消耗灵力」记串了。本移植版按代码实现，未加该机制。

---

### 女仆的经验值：必须是同步数据

**这一条解释了「女仆吸收经验球以后好像什么都没发生」。**

上游的 `DATA_EXPERIENCE` 是
`SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.INT)`，
本移植版早期错写成了普通字段 `private float experience;`，后果有三层：

| 后果 | 表现 |
|---|---|
| 不同步 | 界面左侧那条经验条读的是**客户端**实体的值，而客户端那份永远是 0 —— 吸收完也归零，看起来像「没吸收」 |
| 不持久 | 读档后归零，攒的经验白攒 |
| `float` | 经验球的值本来就是整数；经验修补按「1 XP = 2 耐久」折算也只取整数 |

现在三者都与上游对齐：同步 `int` + NBT 键 `MaidExperience`（与 `EXPERIENCE_TAG` 同名）。
死亡时还会按 `isAlwaysExperienceDropper() = true` / `getExperienceReward() = getExperience()`
把她攒的经验掉出来 —— 否则带几百经验掉进岩浆就全没了。

用 `/tlm info` 可以直接看：

```mcfunction
/tlm info @e[type=touhou_little_maid:maid,limit=1]
#   exp=36 favorability=2 level=0 expBottles=3
```

### 玻璃瓶换附魔之瓶（上游 `GetExpBottleEvent`）

手拿**玻璃瓶**右击自己的女仆，会把她的经验换成附魔之瓶：**12 XP = 1 瓶**
（上游 `PER_BOTTLE_XP`；WIKI 说附魔之瓶掉 3~11 XP，所以取 12 才不亏）。

- **不潜行**：一次换 1 瓶。手上拿着 64 个空瓶单击也不会把经验全兑光。
- **潜行**：一次换到手上瓶子用完（或经验不够为止）。

这个分支**必须排在 `openMaidGui` 之前**：否则手上拿着玻璃瓶右击会直接开界面，
玩家永远换不到经验。上游是靠 `InteractMaidEvent` 的注册顺序做到的，
本移植版没有事件总线，因此在 `mobInteract` 里显式放在背包交互之前。

### 附魔之瓶 / 不死图腾的图标替换（上游 1.20.1 `InitSpecialItemRender`）

原版 `minecraft:experience_bottle` 的 `#inventory` 模型被换成
`touhou_little_maid:item/point_item`（那张蓝色「点」），不死图腾换成 `item/life_point`。
两个开关在 `VanillaConfig`：`ReplaceXPBottleTexture` / `ReplaceTotemTexture`。

> 这是 **1.20.1 才有的功能**（1.18.2 的 jar 里没有这个类），按「向 1.20.1 对齐」的要求补上。
> 贴图与模型 JSON 本来就在资源里，缺的只是替换这一步。

1.18.2 的实现方式与上游不同，原因写在 `client/init/InitSpecialItemRender` 的类注释里：

| | 上游（Forge 1.20.1） | 本移植版（Fabric 1.18.2） |
|---|---|---|
| 钩子 | `ModelEvent.BakingCompleted`（烘焙完之后） | `ModelLoadingRegistry#registerVariantProvider`（加载阶段） |
| 做法 | 往 `bakedTopLevelModels` 塞一个 `ReplaceableBakedModel` 包装器，每帧按配置决定用哪个 | 直接返回另一个 `UnbakedModel` |
| 代价 | 可以在游戏内热切换 | **只能在加载时\u51B3\u5B9A**\uFF0C\u6539\u914D\u7F6E需重进游戏 / F3+T |

好处是不需要写那个把十几个 `BakedModel` 方法全部转发的包装类；
坏处是配置不能在运行时热生效 —— 图标替换本来也不需要。


## 语音频率与交互提示

**根因：频率过滤只拦了「自言自语」，其余语音照播；而全局那个配置项压根没被读过。**

上游 1.20.1 的过滤是**按声音实例**做的，拦的是 **所有** 女仆语音
（`client/event/MaidSoundFreqEvent`）：

```java
if (event.getSound() instanceof MaidSoundInstance maidSound) {
    double f = maid.getConfigManager().getSoundFreq()          // 单只女仆 0~1
             * MaidConfig.GLOBAL_MAID_SOUND_FREQUENCY.get() / 100;   // 全局 0~100
    if (f < 1 && Math.random() > f) event.setSound(null);
}
```

也就是说它拦的不只是待机碎碎念，还包括**捡到东西的「哎嘿」、被驯服的欢呼、
受伤 / 死亡的叫声** —— 因为在上游这些声音全都走同一个自定义 SoundEngine 通道。

本移植版有三处偏差，合起来\u5C31\u662F\u300C\u628A 0% 拖到底还是能听见她说话」：

| 偏差 | 后果 |
|---|---|
| 只在 `playAmbientSound()` 里拦 | 捡东西 / 驯服 / 受伤 / 死亡语音**完全不受控** |
| `MAID_ITEM_GET`、`MAID_TAMED` 直接 `playSound(...)` | 同上，而且这两种是触发最频繁的 |
| `GlobalMaidSoundFrequency` 定义了却没人读 | 改 `touhou_little_maid-common.toml` 里那一项**完全无效**，看起来就像「硬编码」 |

现在收成一个入口：

```java
private boolean shouldPlayVoice() {
    double freq = MaidSubConfig.getSoundFreq(this)          // 单只 0~1
                * MaidConfig.globalMaidSoundFrequency() / 100.0D;   // 全局 0~100
    return freq >= 1.0D || this.getRandom().nextDouble() < freq;
}
```

接入点：

| 语音 | 接入方式 |
|---|---|
| 待机 / 工作模式环境音 | `playAmbientSound()` 里显式判 `shouldPlayVoice()` |
| 捡到东西 `maid.ai.item_get` | 改成 `playMaidVoice(...)` |
| 被驯服 `maid.ai.tamed` | 同上 |
| 受伤 / 着火受伤 | `getHurtSound()` 在频率不通过时返回 `null` |
| 死亡 | `getDeathSound()` 同上 |

> 为什么受伤 / 死亡用「返回 `null`」而不是重写 `playHurtSound`：
> `Mob#playHurtSound` 里先调了 `resetAmbientSoundTime()`（private），
> 重写就把它一起丢了，女仆挨打后会立刻开始碎碎念。
> 而 `LivingEntity#playHurtSound` 取到 `null` 本来就直接跳过播放。

**不走**这个判定的：方块放置声、挤奶声、弩的射击声、落座音效 ——
那些是环境 / 交互动静，不是「她在说话」。

调低之后能听到的效果（把单只设 0%，或全局设 0%）：

- 待机时完全安静
- 捡东西不再有「哎嘿」
- 受伤 / 死亡无声
- 但弩上弦、挤奶、放方块的声音照旧

### 交互提示（手持某物指向女仆）

上游 1.20.1 `client/overlay/MaidTipsOverlay`：准星指向女仆时，在准星右上方画出
「手持物图标 + 提示图标 + 一行说明」，例如拿着空玻璃瓶指向有经验的女仆会显示
**「右击取出经验，Shift右击全部取出」**\u3002

文本与图标本来就在资源里（`assets/touhou_little_maid/lang/*.json` 的
`overlay.touhou_little_maid.*.tips`、`textures/gui/maid_tips_icon.png`），
缺的只是渲染这一层，现在补齐了（`client/overlay/MaidTipsOverlay`）。

显示条件（与上游一致）：

1. **第一人称**、非旁观者；
2. 准星 `minecraft.hitResult` 真的命中一只**活着的**女仆；
3. 玩家与女仆不在**同一个载具**上（否则准星来回命中两者，提示会闪）；
4. 手持物在提示表里，且女仆**归属自己**。

| 手持物 | 提示 |
|---|---|
| 玻璃瓶 | 右击取出经验，Shift右击全部取出 |
| 指南针 / 金苹果 / 药水 / 奶桶 / 命名牌 / 拴绳 / 鞍 | 各自的说明 |
| 剪刀（女仆有背包时） | 移除女仆的背包 |
| NTR 物品（**别人**的女仆） | 将其他人的女仆转换成你自己的 |

**一处刻意的加强：** 玻璃瓶额外要求 `maid.getExperience() >= 12`。
上游不看经验值，「0 经验也弹出『右击取出经验』」是一句谎话 —— 点了没反应很容易被当成 bug。

关掉某一项：`config/touhou_little_maid-common.toml` 的 `[render]` 段，
例如 `EnableGlassBottleTip = false`。

> 1.18.2 的 HUD 钩子是 Fabric 的 `HudRenderCallback#onHudRender(MatrixStack, float)`；
> 上游用的 `IGuiOverlay` + `GuiGraphics` 是 Forge / 1.20 的东西。
> 自绘时要自己关 `GL_DEPTH_TEST` 并开混合，否则提示会被实体 / 方块挡住。


---

## 女仆妖精（EntityFairy）

P 点的**唯一常规来源**。会飞的远程敌对生物：贴到 6 格内悬停、蓄力 1 秒、丢弹幕；
90% 瞄准单发，10% 三发 30° 扇形（伤害更高，惩罚站桩）。

| 属性 | 值 |
|---|---|
| 生命 / 护甲 / 跟随距离 | `Monster` 基础值 / 1 / 35 |
| 移动 / 飞行速度 | 0.25 / 0.4 |
| 近战攻击力 | 1.5（实际只用远程） |
| 碰撞箱 / 追踪距离 | 0.6 × 1.5 / 10 格 |
| 弹幕伤害 | 难度 1 / 1.5 / 2 + 命中系数 |
| 免疫 | 坠落伤害 |

### 生成规则（三层，缺一层就会出现「满地妖精」或「一只都没有」）

1. **能不能被抽中** —— `mixin/NaturalSpawnerMixin` 往 `NaturalSpawner#mobsAt` 的返回值里追加一条权重：
   - 只在 `MobCategory.MONSTER` 上加；
   - 权重 = `MiscConfig.MaidFairySpawnProbability`（默认 70，僵尸是 100，末影人是 10），**0 = 完全不生成**；
   - 维度在 `MaidFairyBlacklistDimension` 里就不加（默认下界 / 末地 / 暮色森林）；
   - **该群系原有的刷怪表里有僵尸才加** —— 这条同时排除了蘑菇岛之类没有敌对生物的群系。
2. **这个位置能不能刷** —— `EntityFairy#checkFairySpawnRules`，通过 `MaidReflection.registerSpawnPlacement`
   注册进 `SpawnPlacements`（`ON_GROUND` + `MOTION_BLOCKING_NO_LEAVES`），
   之后再查稻草人驱散（见下）。
3. **刷出来长什么样** —— `finalizeSpawn` 随机 18 种发色之一，5% 概率把名字设成 `rick`（彩蛋贴图）。

> `mobsAt` 是刷怪循环的热路径，所以注入处做了**按原刷怪表对象身份的缓存**（`ConcurrentHashMap`，
> `WeightedRandomList` 没有重写 `equals/hashCode`，所以就是 identity 语义）。
> 命中缓存后零分配；权重配置变化时整体失效。

### 稻草人驱散（钩子已留，方块未移植）

上游在 `checkFairySpawnRules` 里查 `InitPoi.SCARECROW` 的 POI，`SCARECROW_RANGE`（默认 48）格内不刷。
本移植版**稻草人方块还没做**，因此改为可插拔钩子：

```java
EntityFairy.setRepellentCheck((level, pos) -> /* 附近有稻草人就返回 true */);
```

稻草人落地时在 `InitBlocks` 里注册这一行即可，不需要回来改妖精。

### 掉落

`IHasPowerPoint#dropPowerPoint` 在 `deathTime == 20`（死亡动画播完那一 tick）掉落，
总量 = `MaidFairyPowerPoint × 100`（默认 16 P），再按 1/3/5/7/13/34/89/185/285/385/485 的档位**拆成几堆**。

> 上游**没有**妖精的战利品表（`IHasPowerPoint` 是纯代码驱动）。本移植版相同 ——
> 缺表时 `LootTables#get` 返回 `LootTable.EMPTY`，不会崩。

### 模型

**只移植了旧模型**（`EntityFairyModel`，手写网格，128×64 贴图，18 张发色贴图在
`textures/entity/maid_fairy/`）。配置 `UseNewMaidFairyModel` 保留但暂不生效 ——
新模型是 `simplebedrockmodel` 的 JSON 模型，工作区里只有它的贴图
（`textures/entity/new_maid_fairy/*.png`，128×128，含 `mail_fairy_rick.png`），**没有 JSON**。
补齐 JSON 后接上 `NewEntityFairyRenderer` 即可，选择逻辑本身不用改。


## 受伤、盾牌与主人误伤

### 主人（及同队玩家）打自己的女仆 → 伤害 1/5、封顶 2 点

上游 `EntityMaid#hurt` 的第一条分支，逐字移植：

```java
if (source.getEntity() instanceof Player player && this.isAlliedTo(player)) {
    amount = Mth.clamp(amount / 5.0F, 0.0F, 2.0F);
    return super.hurt(source, amount);
}
```

判定用 `isAlliedTo` 而不是 `isOwnedBy`：`TamableAnimal` 重写了它，
所以**主人本人**与**主人的队友**都会命中 —— 与上游「主人和同 Team 玩家」的说法一致。
没有这一条，一次全力跳劈就能误杀自己的女仆。

### 弹幕能被盾牌挡下

**这是 1.18.2 独有的坑。** 1.20.1 用伤害类型标签判断能不能格挡：

| 1.20.1 | 1.18.2 |
|---|---|
| `BYPASSES_SHIELD`（独立标签） | **不存在** |
| `BYPASSES_ARMOR`（独立标签） | 只有 `DamageSource#isBypassArmor()` 一个布尔 |

而 1.18.2 的 `LivingEntity#isDamageSourceBlocked` 第一句就是
`if (source.isBypassArmor()) return false;`（字节码核对过）——
也就是说「无视护甲」和「可被格挡」共用一个标志位，**无法共存**。

上游弹幕必须无视护甲，所以格挡只能像原版那样在 `EntityDanmaku#onHitEntity` 里
显式做一遍（`tryBlockWithShield`），伤害源仍留给 `EntityDamageSourceDanmaku`：

1. 目标 `isBlocking()`（举盾）；
2. 目标正面朝向来源（`受害者→来源` 与视线点积 > 0），
   与 `isDamageSourceBlocked` 的「来源水平指向受害者」是等价写法；
3. 挡下后按 `Player#hurtCurrentlyUsedShield` 的规则磨盾：
   单次伤害 ≥ 3 才掉耐久，消耗 `1 + floor(damage)`，并播 `SHIELD_BLOCK` 音效。

> 为什么不用 `LivingEntity#hurtCurrentlyUsedShield`：它在 vanilla 里是**空实现**
> （`Code: 0: return`），真正的耐久消耗写在 `Player` 的重写里。女仆不是 `Player`，
> 走原版那条路等于「挡成功但不掉耐久」。

### 女仆的自动举盾

上游还会在**挨了投掷物**时自动举盾 5 秒（`passiveUseShieldTick = 100`），
前提是副手拿着盾（`canUseShield()`）。注意这一下**仍然吃满伤害**：
举盾发生在伤害结算之后，从下一 tick 起才算举盾状态，之后的投掷物才会被挡。
倒计时在 `EntityMaid#tick` 里递减，到 1 时 `stopUsingItem()`。

`isBlocking()` 也做了重写：上游用 Forge 的 `ToolActions.SHIELD_BLOCK`，
1.18.2 没有这个接口，退回 `ShieldItem` 判定。

### 举盾格挡的姿态（旧版模型）

上游对**旧版（非 Gecko）模型**根本没有「使用物品」的基岩动画，它用的是
`MaidBaseAnimation` 里两段 Java 硬编码：

| 场景 | 上游方法 | 效果 |
|---|---|---|
| 举械（弓 / 弩 / 御币 / 三叉戟，`isSwingingArms()` 为真） | `getArmSwing` | **双臂** `X = -80`，左手 `Y = +45`、右手 `Y = -10` |
| 其余「正在使用」的物品（**含盾牌**） | `getArmDefault` | 只抬**正在用的那只手**：左手 `(-80, +25)`，右手 `(-80, -20)` |

`getArmDefault` 的公式是 `initX - 80` / `initY ± 20~25`，而旧版骨架的
`armLeft` / `armRight` 只有 pivot、没有初始旋转，所以 `initX = initY = 0`，
落地值就是上表那两组常数。

本移植版把旧版模型也放进了 GeckoLib 管线，这两段 Java 动画没有搬，改为在
`maid_legacy.animation.json` 里写等价姿势，由 `AnimationManager#predicateUse` 选择：

| 动画名 | 骨骼 | 数值 | 对应上游 |
|---|---|---|---|
| `armed_pose` | `LeftArm` / `RightArm` | `[-80, 45, 0]` / `[-80, -10, 0]` | `getArmSwing` |
| `use_offhand` | `LeftArm` | `[-80, 25, 0]` | `getArmDefault` 左手分支 |
| `use_mainhand` | `RightArm` | `[-80, -20, 0]` | `getArmDefault` 右手分支 |

> **曾经的错误：** 旧版模型「使用物品」时退回 `hold_*:xxx`。
> 那些 `hold_*:shield` / `hold_*:eat` 是给 Gecko 骨架写的（`hold_offhand:shield`
> 是一条 1 秒的挥臂动画，X 从 `+38.8` 摆到 `+10`），旧版骨架没有
> `UpperBody` / `AllBody` 补偿层，播出来就是「盾先往身后甩、再挂在大腿外侧」——
> 也就是「举盾格挡时盾扭到身后」的直接成因。现在改为只抬使用中的那只手，
> 另一只手保持待机 / 走路摆动（与 `getArmDefault` 的语义一致）。


### 顶部三页签 / 女仆配置界面 / 任务设置界面

**页签切换 = 服务端换菜单**，不是在同一个界面里换内容。上游 `MaidTabs` 点一下只发
`ToggleTabMessage(entityId, tabId)`，服务端按 tab 调 `EntityMaid#openMaidGui(player, tab)`
重新开一个容器。因此每个页签必须有**独立的 `MenuType` + 独立 Screen**：

| tab | `TabIndex` | 菜单 | Screen | 底图 |
|---|---|---|---|---|
| 主界面 | `MAIN = 0` | `maid_main` | `MaidMainScreen` | `maid_gui_main.png` |
| 任务设置 | `TASK_CONFIG = 1` | `task_config_default` / `task_config_attack` | `DefaultTaskConfigScreen` / `AttackTaskConfigScreen` | `default_task_config.png` / `attack_task_config.png` |
| 女仆配置 | `MAID_CONFIG = 2` | `maid_config` | `MaidConfigScreen` | `maid_gui_config.png` |

- 三个 Screen 共用一个基类 `AbstractMaidScreen`（立绘 / 四条状态条 / 页签 / 左侧任务列表 /
  玩家物品栏 / 空槽提示），子类只覆写 `renderAddition` 与 `initAdditionWidgets`。
- **页签按钮（`MaidTabButton`，24×26）**：图标在 `maid_gui_side.png` 的 `(uv, 47)` 16×16、偏移 `(4,6)`；
  「选中」态画 `(uv, 21)` 的按下底图。选中沿用上游的取巧写法 —— 把 `active` 置 `false`，
  既画按下态又不响应点击 / 不弹提示，不需要额外状态位。
- **切页签时不发 `ClientboundContainerClosePacket`**：`ServerPlayer#openMenu` 第一句是
  `if (containerMenu != inventoryMenu) closeContainer()`，而 `closeContainer` 会先发关闭包。
  客户端 `handleContainerClose` → `LocalPlayer#clientSideCloseContainer` → `Minecraft#setScreen(null)`
  会先拆掉界面（鼠标被 `grabMouse` 并重置到窗口中心），紧接着的 `ClientboundOpenScreenPacket`
  又把界面建回来（`releaseMouse` 把光标吸到屏幕正中）—— 表现就是「点页签时鼠标跳到画面正中央、
  界面像是重新打开了一次」。上游走 Forge 的 `NetworkHooks.openGui`，它只调 `doCloseContainer()`
  （拆旧菜单、不发包）。现在 `EntityMaid#openMaidGui` 里先显式 `doCloseContainer()` 再 `openMenu()`，
  `openMenu` 的判据因此不成立，客户端只收到一次打开包，界面原地替换、鼠标不动。

- **服务端复核 tab 取值**：网络包是玩家可控的，不能直接把整数透传去选菜单。
- **女仆配置界面的 8 行**（`MaidConfigButton`，164×13，步距 13，起点 `leftPos+86, topPos+52`）：

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

  点击热区是分开的：只有 `x+120..x+130`（左箭头）与 `x+154..x+164`（右箭头）会触发，
  中间一大片是死区 —— 点标题文字不会意外改值。
- **这 8 项全是 `SynchedEntityData`**（`MaidSubConfig` 集中管理，NBT 存在 `MaidSubConfig` 子标签里，
  字段名与上游逐字对应）。客户端只发意图（`MaidConfigPacket` 的动作码 `SHOW_BACKPACK` ~ `PICK_TYPE`），
  服务端 `isOwnedByPlayer` 校验后写入。
- **语音频率是真的接到播放路径上的**：上游在客户端用 `PlaySoundEvent` 概率丢弃，
  Fabric 1.18.2 没有该事件 → 改为在服务端 `EntityMaid#playAmbientSound()`
  按 `random.nextFloat() >= soundFreq` 直接拦掉。环境音本来就是服务端下发
  `ClientboundSoundPacket`，服务端不播客户端连包都收不到，效果等价且更省带宽。
- **开门 / 开栅栏门 / 爬梯是真的接到寻路与移动上的**（只加开关不接线的 UI 等于没有功能）：

  | 开关 | 接线点 | 做法 |
  |---|---|---|
  | 能否开门 | `MaidNodeEvaluator#getCachedBlockType` | 关闭时把 `DOOR_WOOD_CLOSED` 降级成 `BLOCKED`（不会计划穿门，也不会站门前撞） |
  | 能否开栅栏门 | 同上 | 同上（`FenceGateBlock`） |
  | 开门动作 | `MaidInteractWithDoor`（CORE 行为） | 走过节点时开门 / 开栅栏门，走远且没同类在用时关上 |
  | 能否主动爬梯 | `MaidNodeEvaluator#getNeighbors` | 开启时把梯子格当作可走节点，路径才会竖直上去 |
  | 爬梯动作 | `EntityMaid#travel` → `applyClimbMotion` | 按「下一节点在上一格还是下一格」给竖直速度，并清零水平漂移 |

  > 1.18.2 的寻路在算节点时走的是 `getCachedBlockType → getBlockPathType(10 参)`，
  > **三参的 `getBlockPathType` 不参与实际寻路**，覆写它不生效。
  > 另外原版只有 `Player` 会在 `aiStep` 里处理攀爬，所有 `Mob` 都不会主动上下梯子 ——
  > 寻路认得梯子还不够，必须自己给竖直速度。上游靠 Forge 给 `LivingEntity` 打的补丁
  > 加 `MaidClimbTask`，Fabric 没有那个补丁，这里换成了 `travel` 里的等价处理。
- **任务设置界面**：上游 `MaidTaskConfigGui` 本身几乎是空的，内容由 Task 提供
  （`IMaidTask#getTaskConfigGuiProvider`）。本移植版按「有没有实现 `IAttackTask`」二分：
  `attack` / `ranged_attack` / `crossbow_attack` / `danmaku_attack` / `trident_attack` /
  `feed_animal` 走攻击名单页，其余走「当前工作任务没有任何配置项……」提示页。
- **攻击名单（`AttackListData`）随菜单数据下发**，不单开通道：它只在打开任务设置界面时用到，
  而菜单数据本来就只发给打开界面的那一个玩家。编辑期间改的是菜单里那份副本，
  **关闭界面时整体提交一次** —— 每改一条就发包会让服务端反复 `refreshBrain`，
  每次都要停掉所有正在跑的 Brain 行为。
- **攻击名单只覆盖默认判定**：名单里出现过的生物按名单里的关系，没出现的仍走
  `DefaultMonsterType.getMonsterType()`。空名单 = 完全按原版规则，也就是默认值。
  读档时坏条目**逐条跳过**（玩家手填 id，拼错一个不该让其余几十条一起失效）。

  切换走 C2S 包由服务端写入（任务 id 是同步数据，本地先改会被下一次同步覆盖）。

---

## 饮食系统（工作餐 / 回血餐）

### 结论先说：吃东西与喝药的入口是「AI 主动 `startUsingItem`」

女仆不是玩家，**没有任何按键会让她吃或喝**。上游里「吃 / 喝」这两个动作只由
`IMaidMeal#onMaidEat` 里的一句 `maid.startUsingItem(hand)` 引发：

```java
// DefaultMaidWorkMeal / DefaultMaidHealSelfMeal 都是这个套路
maid.startUsingItem(hand);   // ← 全部动画与进食逻辑的起点
```

一旦进入「使用物品」状态，剩下全是原版流程，**移植时一行都不用写**：

| 环节 | 谁负责 |
|---|---|
| 每 tick 递减 `useItemRemaining` | `LivingEntity#tick → updatingUsingItem → updateUsingItem` |
| 递减到 0 时消耗物品 | 同上，末尾调 `completeUsingItem()` |
| 真正吃掉（数量 -1、加药水效果、回血） | `Item#finishUsingItem` → `ItemStack#finishUsingItem` → `LivingEntity#eat` |
| 吃东西动画 | `AnimationManager#predicateUse` 看到 `isUsingItem()` 为真 |
| 粒子 / 音效 | `ItemStack#onUseTick` → `triggerItemUseEffects` |

> **反过来推：** 若没有这条路径，女仆永远不会 `startUsingItem`，
> `predicateUse` 的整段 `isUsingItem()` 分支就是死代码 —— 吃东西 / 喝药的动画
> 一次都不会播。这正是「忘了原版怎么触发」时最容易漏掉的一环。

### 三类餐与触发行为（上游 `MaidMealType`）

| 类型 | 行为 | 位置 / 优先级 | 触发条件 |
|---|---|---|---|
| `WORK_MEAL` | `MaidWorkMealTask` | `Activity.WORK`，priority 7 | 工作时间 + 没睡觉 + 当前 Task 允许吃饭（`IMaidTask#enableEating`）+ 工作餐冷却已走完 |
| `HEAL_MEAL` | `MaidHealSelfTask` | `Activity.CORE`，priority 3 | 缺失生命 ≥ 2 + 没睡觉 + 当前 Task 允许吃饭 |
| `HOME_MEAL` | `MaidHomeMealTask` | —— | **未移植**：依赖野餐垫方块 + `TileEntityPicnicMat` + POI + `EntitySit` |

`HEAL_MEAL` 挂在 CORE 而不是 WORK，是因为「快死了先吃点」比手头的事更要紧 ——
工作 / 空闲 / 休息三种活动下都会触发。这也是上游的选择。

### 「工作餐」的两段式取食流程

`MaidWorkMealTask#start` 与上游逐行对应：

1. 先看**手上**拿着的东西能不能吃，能就直接 `onMaidEat`；
2. 否则挑一只空手（主手优先，两只手都占着就抢副手），从**背包**里取一份能吃的换到手上；
3. 原本手上的东西**不能丢地上** —— 存进隐藏栏 `hideInv`（1 格），
   等吃完时 `completeUsingItem()` 再还回手上。

`hideInv` 必须写进存档（NBT 键 `MaidHideInventory`）：换手期间区块被卸载，
藏起来的那把剑不能凭空消失。

### 好感度：`Type` 自带的冷却才是「不会一直吃」的原因

`Type.WORK_MEAL` 的点数是 1、冷却 **2 分钟**；`Type.HOME_MEAL` 冷却 1 分钟。
`FavorabilityManager#canAdd(typeName)` 不通过时行为根本不会启动，
所以女仆不会站在那儿每 2 秒吃一次。

| 好感度 | 等级 | 最大生命 | 攻击力 |
|---|---|---|---|
| 0 | L0 | 20 | 2 |
| 64 | L1 | 30 | 3 |
| 192 | L2 | 40 | 4 |
| 384 | L3 | 80 | 6 |

> 上游 `Type.WCHESS_WIN` 的类型名被误写成 `"CChessWin"`（与 `CCHESS_WIN` 重名，
> 后注册的覆盖先注册的）。这里**照抄这个 bug**，否则以后接上棋类时行为和上游不一致。

### 与上游的差异

| 上游 | 本移植版 |
|---|---|
| 好感度用 Forge capability 挂在实体上 | 实体自己的字段 + NBT 子标签 `FavorabilityManagerCounter`（键名与上游相同） |
| 升级时发 advancement trigger | 没有成就系统，省略 |
| 心里粒子发 `SpawnParticleMessage` 网络包 | 直接用 `ServerLevel#sendParticles(ParticleTypes.HEART, …)` |
| 吃完发 `MaidAfterEatEvent`，由 `DefaultEatenEvent` 返还容器 | 在 `EntityMaid#eat` 里直接做同一件事 |
| 正则黑名单在配置加载时展开成 id 表 | 按需用 `Pattern` 匹配当前物品注册名（配置项本来只有个位数），编译结果带缓存 |
| `stack.getFoodProperties(maid)` | 1.18.2 只有无参的 `Item#getFoodProperties()`（带 `LivingEntity` 参数的重载到 1.19 才有） |
| `foodAfterEat.getContainerItem()` | 1.18.2 vanilla 只有 `Item#getCraftingRemainingItem()`，且要先问 `hasCraftingRemainingItem()` |
| `Entity#isStruckByLightning()`（Forge 加的） | vanilla 没有，改为在 `thunderHit` 里自己记一个位 |

### 已接线的配置项

配置**早就有了**（`MaidWorkMealsBlockList` 等 6 项 + `MaidEatenReturnContainerList`），
但此前没有任何代码读它们。现在这些配置项全部生效：

| 配置项 | 作用 |
|---|---|
| `MaidWorkMealsBlockList` / `MaidHealMealsBlockList` | 精确黑名单（默认排除河豚 / 毒土豆 / 腐肉 / 蜘蛛眼） |
| `MaidWorkMealsBlockListRegEx` / `MaidHealMealsBlockListRegEx` | 正则黑名单 |
| `MaidEatenReturnContainerList` | 吃完返还物，形如 `[["minecraft:beetroot_soup","minecraft:bowl"]]`。先看物品自带的 `getCraftingRemainingItem()`，为空才查这张表 |

### 怎么测

```mcfunction
# 1. 先确认女仆能捡东西（否则塞不进背包）
/tlm pickup @e[type=touhou_little_maid:maid,limit=1] true

# 2. 把食物丢她脚下，等她捡进背包
/execute at @e[type=touhou_little_maid:maid,limit=1] run summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:cooked_beef",Count:8b}}

# 3. 切成「空闲」以外的任意工作模式，等待 0~50 tick
/tlm task @e[type=touhou_little_maid:maid,limit=1] farm

# 4. 看背包格数是否减少（吃掉了）
/tlm inv @e[type=touhou_little_maid:maid,limit=1]

# 回血餐：先把她打残（主人打自己女仆伤害会除以 5 并封顶 2，慢慢来）
/damage @e[type=touhou_little_maid:maid,limit=1] 6 minecraft:generic
```

**观察点**：手臂抬起 → 咀嚼音效 / 食物粒子 → 食物数量 -1 → 若原本手里拿着别的东西，
吃完后那件东西回到手上（而不是掉在地上）。

> `WORK_MEAL` 有 2 分钟冷却，所以「吃一次之后就不再吃」是**正常**的；
> 想连测就 `/tlm info` 看时间，或换一只女仆。



### 其余六个右键物品交互（指南针 / 金苹果 / 药水 / 奶桶 / 命名牌 / 鞍）

上游把这六个功能各自写成一个 `InteractMaidEvent` 订阅者；本移植版没有事件总线，
因此在 `EntityMaid#mobInteract` 里显式串起来。这六项各自匹配**不同的物品**，彼此互斥，
所以它们之间的先后不影响结果（下表编号只为对照方便）；真正有先后要求的是它们与「潜行分支」的相对位置。

| # | 物品 | 条件 | 行为 | 对应上游 |
|---|---|---|---|---|
| 1 | 鞍 | 直接右击 | 女仆成为玩家的**乘客**（抱在怀里）；再右击一次放下 | `SaddleMaidEvent` |
| 2 | 药水（可饮） | **Shift** + 右击 | 效果作用在女仆身上，消耗药水并返还玻璃瓶 | `ApplyPotionEffectEvent` |
| 3 | 奶桶 | **Shift** + 右击 | 清除女仆身上的药水效果，返还桶 | 同上 |
| 4 | 金苹果 / 附魔金苹果 | **Shift** + 右击 | 走 vanilla `Mob#eat`，回复量与效果都来自 `Foods` | `ApplyGoldenAppleEvent` |
| 5 | 指南针 | 直接右击 | 在客户端画出她的工作区域（30 秒） | `MaidAreaClickEvent` |
| 6 | 空命名牌 | 直接右击 | 打开改名界面（带「名字常显」开关） | `UseNameTagEvent` |

三个实现上的注意点：

1. **真正有顺序要求的是「它们必须排在潜行分支之前」**。上游 `SwitchSittingEvent`（潜行右键切坐姿）
   的优先级是 `LOWEST`，所以「Shift + 药水」「Shift + 金苹果」必须先被各自的分支吃掉，
   否则会变成「喝药水的同时坐下」。`DismountMaidEvent`（`LOW`）介于两者之间 ——
   抱着女仆时 Shift 右击先尝试放她下来，没东西可放才切坐姿。
2. **`isRideable` 是「用鞍抱起」的总开关**。上游 `MaidMountEvent` 在 `EntityMountEvent` 上拦
   「不可骑乘」的女仆，本移植版在 `startRiding` 之前等价地判一次。
   这个开关存在 `EntityMaid` 的同步数据里、存档标签叫 `MaidIsRideable`（与上游同名），
   默认 `true`，由主界面左下角第三个按钮（`leftPos+51, topPos+206`，UV `84,0`）切换 ——
   位置与上游 `AbstractMaidContainerGui#addRideButton` 逐像素一致。
   关掉之后鞍只会在服务端被拦下，不做任何事；女仆也坐不上船 / 矿车。
   走的是 `MaidConfigPacket` 的 `RIDEABLE` 动作码，客户端只发意图。
3. **指南针不需要网络包**。上游工作点在 `SchedulePos` 里、客户端拿不到，
   所以要发 `SyncMaidAreaMessage`；本移植版的工作中心 / 半径 / 居家开关本来就在
   `SynchedEntityData` 里，客户端直接读就行，区域显示因此是纯客户端行为
   （`client/render/MaidAreaRenderer` + `client/render/RenderHelper`）。

命名牌（改名）是唯一需要**客户端 → 服务端**包的：`setCustomName` 不是同步数据，
而且改名要消耗命名牌。提交走 `MaidConfigPacket` 的 `NAME_TAG` 动作码，
服务端会复核「女仆归属自己 + 主手还拿着命名牌」，名字截断到 32 字符。
界面是 `client/screen/NameTagScreen`（`EditBox` + 「名字常显」按钮，
勾 / 叉图标直接用原版信标的 `textures/gui/container/beacon.png`）。

> **鞍的定位技巧：** 乘客的位置由**载具**的 `Entity#positionRider` 决定，
> 1.18.2 的默认实现是「乘客站在载具原点」—— 女仆会整个人塞进玩家身体里，
> 看起来像「用了鞍以后女仆消失了」。上游为这件事写了一个 `Entity` 级 mixin；
> 本移植版改成在 `EntityMaid#rideTick()` 里 `super` 之后覆盖一次位置
> （玩家身前 0.75 格、抬高 0.15 格；玩家下坠时下沉 0.3 格，与上游同款处理）。
> 原因：本项目没有配置 mixin 注解处理器（不生成 refmap），而 `positionRider` 是
> **重载方法**、`@Inject` 必须写完整描述符，描述符里的方法名在正式环境不会被重映射到
> intermediary，运行期会「找不到方法」。

