# Touhou Little Maid: Futo（车万女仆：布都）

车万女仆（Touhou Little Maid）的 **Fabric 1.18.2 移植版**。`modid` 保持上游一致：`touhou_little_maid`。

- 上游（Forge 1.18.2，v1.2.2）：https://github.com/TartaricAcid/TouhouLittleMaid
- Fabric 1.20.1 参考移植：https://github.com/Sh1roCu/TouhouLittleMaid-Orihime
- Forge Config API Port 1.18.2：https://github.com/Fuzss/forge-config-api-port/tree/1.18.2
- 许可：CC-BY-NC-SA（继承上游）

> 本文只记录**经过游戏内验证**的结论；被推翻的假设一律不留。
> 每条「与上游不同」都写明原因 —— 那些是 1.18.2 缺少 Forge 补丁导致的，不是偏好。

---

## 目录结构

```
touhou_little_maid_futo/
├─ build.gradle / gradle.properties / settings.gradle
├─ libs/                            ← ForgeConfigAPIPort / cloth-config / night-config / fiber
├─ src/main/java/
│  ├─ net/minecraftforge/…          ← Forge → Fabric 兼容层（Futo shim）
│  └─ com/github/tartaricacid/touhoulittlemaid/
├─ src/main/resources/              ← fabric.mod.json / mixins / assets / data
└─ port/upstream/                   ← 上游 1.18.2 源码（1110 文件，仅参考，不参与编译）
```

---

## 构建 / 运行

```powershell
cd 'D:\codename_EndDavid\hello java\touhou_little_maid_futo'
.\gradlew.bat build        # 产物：build/libs/touhoulittlemaid-fabric-1.18.2-futo-*.jar
.\gradlew.bat runClient    # 启动开发客户端（固定玩家名 Dev）
```

Gradle 守护进程用 **JDK 21**（Loom 1.15 要求），编译与运行游戏用 **JDK 17**（1.18.2 要求）。
两者已在 `gradle.properties` 钉死，**不需要设 `JAVA_HOME`**：

```properties
org.gradle.java.home=D:/codename_EndDavid/hello java/jdk-21.0.2
org.gradle.java.installations.paths=D:/codename_EndDavid/hello java/jdk-21.0.2,C:/Program Files/Java/jdk-17
loom_version=1.15.5
```

**`gradlew build` 通过 ≠ 能跑。** Mixin 只在运行期应用，注入失败时构建仍是绿的
（曾因此漏掉一个 `@Invoker` 写错）。任何涉及 mixin / 注册 / 初始化的改动都必须真的
`runClient` 一次并读 `run/logs/latest.log`。
**`gradlew runClient` 能跑 ≠ 打包后能跑。** 开发环境跑在 **named（Mojang）** 映射下，
正式环境被 Fabric Loader 重映射到 **intermediary**，写死在字符串里的类名 / 方法名 / 字段名
**不会被重映射**。所以每次动过反射、或新增了依赖 `Class#getDeclaredMethod(name, …)` 的代码，
必须把 `build/libs/` 的 jar 装进启动器实测一次。见下文兼容层的「反射只能按参数类型找」。


---

## 进度总览

| 模块 | 状态 |
|---|---|
| 工具链 / Forge 兼容层 | 完成 |
| 渲染（GeckoLib3 魔改 + 模型包 + 动画） | 完成 |
| Brain 行为（活动 / 记忆 / 感知器 / 日程） | 完成 |
| 工作模式 | **20 个**，仅缺 `board_games`（依赖 `EntitySit`） |
| 女仆背包（7 种）+ 物品栏 | 完成 |
| 主界面 GUI + 左侧任务列表 | 完成 |
| 顶部三页签（主界面 / 任务设置 / 女仆配置） | 完成（服务端换菜单式切换） |
| 女仆配置界面（8 项子配置） | 完成 |
| 任务设置界面（默认提示 / 攻击名单） | 完成 |
| 饮食系统 | 完成（工作餐 / 回血餐；HOME_MEAL 待野餐垫） |
| 配置系统（Forge Config API Port） | 完成，`AIConfig` 待补 |
| 声音（语音包 / 环境音 / 频率控制） | 完成 |
| 弹幕攻击 / 御币 / 钓鱼 | 完成 |
| 灵力（Power / P 点） | 完成（Mixin 挂在玩家实体上） |
| 经验球吸收 / 附魔之瓶 / 图标替换 | 完成 |
| 女仆妖精（旧模型） | 完成 |
| 手持物品渲染 | 完成（两条模型管线都走通） |
| **祭坛多方块 + 祭坛合成** | **完成** |
| **神社 / 胶片 / 女仆床 / 稻草人** | **完成** |

| **墓碑（死亡收纳）+ 祭坛召唤箱** | **完成** |
| **帕秋莉手册（书内容 + 侧边页签）** | **完成**（36 条目 / 3 分类） |
| 骑乘模型倒置 | 未开始（钓鱼改岸钓绕开） |
| 自定义附魔（滞弹 / 速弹 / 末影之末） | 未开始 |
| 模组联动（JEI / REI / EMI / Jade / YSM / Iris…） | 未开始 |
| **睡觉的落点（碰撞箱 + 渲染）** | **完成**（`pinToBed` 两格中点 + 渲染侧补一次半身长） |

---

## 工程与 Forge 兼容层

- **工具链**：Gradle 9.3 + Loom 1.15.5 + Loader **0.14.25**（勿升，整合包要求）
  + Fabric API 0.77.0+1.18.2 + Java 17，使用 `officialMojangMappings()`
  （与上游 Parchment 命名基本一致，便于整包搬运）。
- **兼容层** `net/minecraftforge/`：`Event` / `SubscribeEvent` / `IEventBus` + 反射式
  `SimpleEventBus`、`DeferredRegister` / `RegistryObject` / `ForgeRegistries`、
  `ModLoadingContext` / `DistExecutor` / `ModList` / `OnlyIn`、`IItemHandler` 全套、
  `RecipeMatcher`（Kuhn 增广路重写）。
- **Access-Widener 不可用**：Loom 1.15 要求表头 `classTweaker …`，Loader 0.14.25 要求
  `accessWidener …`，一个文件无法同时满足。改用 `util/MaidReflection` 反射构造 vanilla
  私有构造器（`MemoryModuleType` / `SensorType` / `Activity` / `Schedule` / `PoiType`）。
  `StructureTemplate#palettes` 用 `@Accessor` 打开。
- **反射只能按参数类型找，绝不能按方法名找。**
  开发环境（`runClient`）跑在 **named / Mojang** 映射下，`SpawnPlacements#register`
  就叫 `register`；正式环境被 Fabric Loader 重映射到 **intermediary**，同一个方法变成
  `method_5684` 之类的编号。**Loom 只重映射常量池里的类名 / 字段名 / 方法引用，
  不会重映射写死在字符串里的名字** —— 于是 `getDeclaredMethod("register", …)`
  开发环境成功、正式环境抛 `NoSuchMethodException`：

  ```
  java.lang.IllegalStateException: Failed to register spawn placement for
      touhou_little_maid:fairy (unsupported Minecraft version?)
    Caused by: java.lang.NoSuchMethodException: net.minecraft.class_1317.register(…)
  ```

  表现就是「终端 `runClient` 正常、装进启动器立刻崩在入口点」。正确写法是遍历
  `getDeclaredMethods()`、**只比对参数类型**（`Class` 字面量会随常量池一起被重映射）。
  构造函数没有这个问题 —— 构造器在字节码里没有名字。

  > Mixin 侧不受影响：Loom 打包时会把 `@Inject(method=…)` / `@At(target=…)` 的字符串
  > 直接重映射成 intermediary（核对过 jar 内的 mixin 类，`NaturalSpawnerMixin` 里已是
  > `method_34994`），所以**不需要 refmap**，`mixins.json` 里也没有 `refmap` 字段。
- **编码守卫**：`port/check-encoding.ps1` 扫描全项目 U+FFFD。
  **大文件禁用 `insert_edit_into_file`** —— 它会静默吞行、合并行、把中文变成 U+FFFD。
  正确做法：纯 ASCII 的 `.ps1`（Windows PowerShell 5.1 按 ANSI 解析无 BOM 脚本，
  中文字面量会语法错误）+ 括号配对定位 + 中文 javadoc 另存 UTF-8 片段拼接
  + 写后断言（大括号平衡 / 必需符号 / 零 U+FFFD）。
- **日志开关**：`AnimationManager#DEBUG_ANIM_STATE` 默认 `false`。打开后每次主控制器
  状态切换打一行，是「模型不切换」类问题的判据（若日志出现 `sit -> idle` 而画面仍坐着，
  问题就在动画/模型层而非状态层）。默认关闭是因为正常走动 + 停下就写两行，持续污染日志。

---

## 渲染（GeckoLib3 管线）

### 关键结构

- **GeckoLib3 全量移植**（174 个类）去 Forge 化，实际改动仅 8 处
  （`ForgeRegistries.X.tags().createTagKey` → `TagKey.create`、移除 `RenderLivingEvent` 挂点、
  `Vector3f.setX` → `set`、自实现版本比较、capability → 静态查表）。
- **动画状态机** `AnimationRegister` + `AnimationState` + `Priority` + `AnimationManager`，
  优先级与上游一致（死亡 / 睡觉 / 游泳 / 爬梯 → 载具 / 坐下 → 受击 / 跳跃 → 跑 / 走 → 待机），
  另有持物 / 挥击 / 使用 / 护甲 / 乘客 / 并行层控制器。
- **per-entity 动画实例缓存**（`GeckoMaidEntity.getOrCreate`，按 id + 实例身份校验）。
  渲染器每帧取一次实例，若每次 `new`，动画进度永远停在 0。
  **必须按 id 索引而不是 `Map<Mob,…>`** —— 1.18.2 的 `Entity#equals/hashCode` 就是实体 id，
  区块重载后旧实例的动画对象会被「认领」给新实例，而旧实例已不再 tick ⇒ 谓词永不求值 ⇒ 模型定格。
  已补 `forceUpdate` / `MaidState`（头/身朝向变化或换模型时强制求值一次）。
- **模型包**：内置 7 个包展开到 `assets/`（`touhou_little_maid` 905 文件、`littlemaid_peco` 336、
  `touhou_little_maid_old` 84、`next_update_model` 69、`authors_and_credits` 36、
  `geckolib` 24、`minecraft_15th` 12）。启动日志：
  `[Futo] Loaded 194 maid models (5 gecko prebuilt), 5 animation files`。
- **旧版模型（`is_gecko = false`，178 个）** 加载时由 `GeckoModelLoader#normalizeBones`
  把骨骼名映射成 Gecko 命名（`head→Head`、`body→UpBody`、`armRight→RightArm`…）并补合成 `Root`，
  以复用默认动画集。这是**近似**：旧版骨架缺少 `AllBody` / `UpperBody` / `AllHead` 等补偿层，
  凡是「靠上层骨骼补偿」的动画都会走形（见下文三条）。
- **动画合并优先级**：模型包自带的同名动画优先，默认动画只补齐缺失名字。
  写反会导致骨骼名对不上 →「站着不摆臂」「坐下没反应」。

### 眨眼与表情贴片

模型把表情做成 **0 厚度贴片骨骼**（`blink` / `hurtBlink`）。模型包 `maid_model.json`
用 `animation/maid/default/head/*.js` 登记它们，但**那些 `.js` 在 jar 里根本不存在**
（1.18.2 jar 里 `.js` 数为 0，1.20.1 为 2，且都是 `authors_and_credits` 的展示动画）。
上游是在 `MaidBaseAnimation` / `MaidExtraAnimation` 里用 Java **硬编码**的（`javap -c` 可核对）：

```
MaidExtraAnimation$4（head/hurt.js）
    hurtBlink.setHidden(!maid.onHurt());        // onHurt() 就是 hurtTime > 0

MaidBaseAnimation$2（head/blink.js）
    if (mob.isSleeping()) { blink.setHidden(false); return; }
    remainder = (ageInTicks + |uuid.leastBits| % 10) % 60;
    blink.setHidden(!(55 < remainder && remainder < 60));
```

已按此在 `MaidRenderer#updateOverlayBones`（`renderEarly` 里，动画 tick 之后、绘制之前）重现。
骨骼名兼容 4 种拼法：`blink` / `blink2` / `_blink` / `_bink`（后两个是上游笔误，必须兼容）。

**0 厚度立方体不要翻法线。** 1.18.2 的实体着色器用法线算亮度
（`minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color)`）：

| 法线方向 | lightAccum |
|---|---|
| 朝上 `(0,1,0)` | **1.000** |
| 朝前 `(0,0,-1)`（正确值） | 0.739 |
| 朝侧 `(±1,0,0)` | 0.496 |
| 朝下 `(0,-1,0)` | 0.400 |

`IGeoRenderer#renderCube` 里曾有一段对 0 厚度立方体的「法线修补」，判断的是**变换后**的法线，
而变换含实体朝向与骨骼旋转 —— 于是同一块面在转身后时翻时不翻。翻错最多亮 35%，
表现为「眨眼时那一层脸突然变亮」（表情贴片 / 裙摆 / 翅膀 / 发丝全是 0 厚度）。**该补丁已删除。**
上游 `simplebedrockmodel` 用每面固定法线数组、从不翻转，所以它天然没这个问题。

### 手持物品

两条模型管线各有一套层，合成一个 `GeckoLayerMaidHeld`，按「有没有 `*HandLocator`」分流：

| 模型类型 | 定位骨骼 | 物品变换常量 | 上游来源 |
|---|---|---|---|
| 基岩（Gecko） | `RightHandLocator` / `LeftHandLocator` 的完整父级链 | `translate(0, -0.0625, -0.1)` + `rotX(-90°)` | `GeckoLayerMaidHeld#renderArmWithItem` |
| 旧版（1.10 / 1.12） | `RightArm` / `LeftArm` | `rotX(-90°)` + `rotY(180°)` + `translate(±1/16, 0.125, -0.525)` | `LayerMaidHeldItem#renderArmWithItem` |

数值全部逐字来自上游。旧版模型的 `armRight` / `armLeft` 是**顶层骨骼**（无 `parent`，
与 `body` 平级，`pivot` 为 `[-3,17.5,0]` / `[3,17.5,0]`），所以上游只变换这一根就够。
我们把骨骼改名成 `RightArm` 并补了合成 `Root`，因此走 `Root → RightArm` 链即可，
`Root` 携带的 `sit` 位移正好等价于上游「整个模型下移」。

`MaidRenderer` 为实现该层补了 `IGeoEntityRenderer` 的三个方法
（`getGeoEntity` / `addGeoLayerRenderer` / `geoRender`）；
`ILocationModel` 补了 `boneHierarchy(String)` 默认方法（旧版模型没有 locator 骨骼）。

未移植的上游分支：`SlashBladeCompat`、`RenderFixer.isCarryOnRender`（CarryOn）、
`TacCompat.addItemTranslate`（TACZ）、`armPositioningBone`（现有 189 个模型无一使用）。

### 旧版模型的「使用」与「举械」姿态

上游对旧版模型**根本不用基岩动画**，而是 `MaidBaseAnimation` 里的 Java 硬编码，
其中「使用中」只有两条规则（`javap -c` 核对 1.18.2）：

```
getArmSwing()      // arm/swing.js
    !mainHandItem.isEmpty() && isSwingingArms()
        → armLeft(−1.396 rad, 0.785 rad) = (−80°, +45°)
          armRight(−1.396 rad, −0.174 rad) = (−80°, −10°)     // 躯干与头部完全不动

getArmDefault()    // arm/default.js
    isUsingItem() && usedItemHand == OFF_HAND  → armLeft.x  = init − 80°, armLeft.y  = init + 25°
    isUsingItem() && usedItemHand == MAIN_HAND → armRight.x = init − 80°, armRight.y = init − 20°
```

即：**举械（弓/弩/御币/三叉戟）双臂一起抬；其余物品只抬正在使用的那一只。**
旧版骨架的臂骨只有 pivot、没有初始旋转，所以 `initX = initY = 0`，落地就是上表常数。

移植办法：`animation/maid_legacy.animation.json` 放三支常量姿态，
`GeckoModelLoader#buildAnimations` 只在 `!isGeckoModel()` 时合并该文件，Gecko 模型完全不受影响：

| 动画名 | 骨骼 | 数值 | 对应上游 |
|---|---|---|---|
| `armed_pose` | `LeftArm` / `RightArm` | `[-80, 45, 0]` / `[-80, -10, 0]` | `getArmSwing` |
| `use_offhand` | `LeftArm` | `[-80, 25, 0]` | `getArmDefault` 左手分支 |
| `use_mainhand` | `RightArm` | `[-80, -20, 0]` | `getArmDefault` 右手分支 |

**`armed_pose` 必须由 `use` 控制器全程驱动，不能交给 `hold_*`。**
上游只在 `isSwingingArms()` 为真时抬臂，而这个标志由远程攻击行为的 `start` / `stop` 写入，
天然覆盖「蓄力 → 松手 → 冷却 → 下一次蓄力」整段。若改由 `hold_*` 在 `use` 结束后接手，
会掉进默认动画集里 `hold_mainhand:bow` 那段 2 秒关键帧（手臂 `0°→-94°→+20.7°`，
同 `:spear`；`:crossbow` 是单臂姿态），表现为「每射一次手臂甩一下」。
同时 `LEGACY_ARMED_HOLD_ANIMATIONS` 把旧版模型的
`hold_*:bow / :crossbow / :spear / :charged_crossbow` 全部拦掉。

**盾牌归 `getArmDefault`，不能退回 `hold_offhand:shield`。** 那支动画是给 Gecko 骨架写的
1 秒挥臂（X 从 `+38.8°` 摆到 `+10°`），旧版骨架没有 `UpperBody` / `AllBody` 补偿层，
播出来就是「盾先往身后甩、再挂在大腿外侧」。

上游的两处缺失已补：

- `MaidShootTargetTask#stop` / `MaidTridentTargetTask#stop` **没有**复位
  `setSwingingArms(false)` —— 标志一旦置真便永远为真，必须补上。
- 原版 `CrossbowAttack` 只写 `setChargingCrossbow`、**从不**写 `setSwingingArms`，
  弩在冷却期会被判成「不在攻击」。已在 `MaidCrossbowAttack#start / #stop` 补上。

### 旧版模型的坐船与摇翅

**不能吃 Gecko 的 `boat`。** `boat` 靠 `Root` 绕 X 转 `-90°`，再靠 `AllBody` / `DownBody`
把身体折回来做出「半躺在船里」。旧版骨架的 `Root` 是加载时**合成**的、没有骨盆枢轴补偿，
也没有 `DownBody` / `AllBody`，整条链下来只剩那个 `-90°` —— 人整个翻过去。
上游对旧版模型的载具姿态只有 `ridingPosture`（双腿前屈 65°、左右各外撇 15°，
加 `setupRotations` 里的 `translate(0, 0.3, 0)`），已移植成 `chair`。
修法是让旧版模型跳过 `boat`（与已有的 `!isLegacyModel` 守卫同款），落到 `chair`。

**摇翅是 Java 曲线，不是关键帧。** 上游 `MaidBaseAnimation#getWingDefault()`：

```java
wingLeft .setRotateAngleY((float) (-Math.cos(ageInTicks * 0.3) * 0.2 + initY));
wingRight.setRotateAngleY((float) ( Math.cos(ageInTicks * 0.3) * 0.2 + initY));
wingLeft / wingRight.setHidden(maid.isSleeping());
```

幅度 0.2 rad（±11.5°）、周期约 21 tick。它挂在一条**并不存在的伪路径**
`animation/maid/default/wing/default.js` 上（默认模型包 `tlm_custom_pack` 里只有
`arm.js` / `float.js`）。本移植版逐帧写骨骼（`GeckoMaidEntity#updateWing`），仅对旧版模型生效 ——
Gecko 模型走 `maid.animation.json`，那 91 支里没有任何一支摇翅膀。芙兰朵露与蕾米莉亚
都是旧版模型，骨骼里确实有 `wingLeft` / `wingRight`（这两个名字**不在**改名表里）。

### 坐船后「固定看向一侧」

上游重写了 `rideTick`：

```java
public void rideTick() {
    super.rideTick();
    if (this.getVehicle() != null) {
        this.setYHeadRot(vehicle.getYRot());
        this.setYBodyRot(vehicle.getYRot());
    }
}
```

原版 `Entity#rideTick` 的顺序是 `setDeltaMovement(ZERO) → tick() → positionRider(this)`，
乘客的 `tick()` 照常执行（`yBodyRotO` / `yHeadRotO` 正常刷新，插值平滑）。但**没有任何一步**
把载具朝向写给乘客：`yBodyRot` 只被 `Mob#aiStep` 的「跟随移动方向」改写，而乘客的 AI 已停；
`Boat#clampRotation` 只写 `yRot`、不碰 `yBodyRot` / `yHeadRot`。
于是模型整体偏航（`Mth.rotLerp(yBodyRotO, yBodyRot)`）冻在上船那一刻。

抱着她时载具是玩家，必须跟随玩家的 `yBodyRot` 而不是 `yRot`，所以
`updateCarriedPosition()` 仍放在这段**之后**：能解析出载运者时它会把三个朝向一起覆盖掉；
解析不到（船 / 矿车）时直接返回，只保留 `rideTick` 的同步。

### 模型自带的护甲片必须按装备槽隐藏

上游 `MaidArmorAnimation#getArmorDefault` 有一条规则：没穿对应部位护甲时隐藏模型自带的护甲片。

```java
if (chestPlateLeft != null) chestPlateLeft.setHidden(!maid.hasChestPlate());
// helmet / chestPlate / chestPlateMiddle / chestPlateRight / leggings×4 / boots×2 同理
```

`setHidden` 是**整棵子树**语义。芙兰朵露的红色缎带与棒子挂在
`armLeft → chestPlateLeft → bone257 → {bow2, bone258}` 这条链上，默认没穿胸甲 →
上游看不到这两件。缺这条规则就会一直显示、并贴在左手上，第一人称抱着时直接糊到摄像机。
已在 `MaidRenderer#updateArmorBones` 按上游逐条补齐，用双参
`setHidden(selfHidden, skipChildRendering)` 保证子骨骼一起跳过。
反向变体（`_helmet` / `_chestPlate` …）以下划线开头、不在改名表里，按原名查。

### 抱着的姿态：旧版那 0.3 的真实方向

上游 `EntityMaidRenderer#setupRotations`（1.18.2 与 1.20.1 一致）：

```java
if (mob.getVehicle() instanceof Player && !this.mainInfo.isGeckoModel()) {
    poseStack.translate(-0.375, 0.8325, 0.375);
    poseStack.mulPose(Vector3f.ZN.rotationDegrees(65));
    poseStack.mulPose(Vector3f.YN.rotationDegrees(-80));
}
```

**Y 恒为 0.8325**。旧版那点额外位移不在这一段，而是
`MaidBaseAnimation#ridingPosture` 末尾的 `GlWrapper.translate(0, 0.3, 0)`。

用 `javap` 反编译 `LivingEntityRenderer#render` 得到调用顺序：

```
pushPose → setupRotations → scale(-1,-1,1) → scale(k) → translate(0,-1.501,0)
        → prepareMobModel → setupAnim → renderToBuffer
```

`GlWrapper.translate` 在 **`setupAnim`** 里执行，排在 `scale(-1,-1,1)` **之后**，
即作用在**已被 Y 翻转的模型空间**里，等效位移要再翻一次号：

```
Δ = Rz(-65) · S(-1,-1,1) · (0, 0.3, 0) = Rz(-65) · (0, -0.3, 0) = (-0.272, -0.127, 0)
```

所以它是**向下、并朝 -X 偏 0.272 格**。实现：主 translate 恒为 `0.8325`（与上游逐字一致），
旧版单独应用 `LEGACY_RIDING_LIFT`，写在两次 `mulPose` **之后**并**取负号**。

> 附带一条：`scale(k)`（`renderEntityScale`）在 `setupRotations` **之后**，
> 因此只缩放模型本体，不缩放这里的位移 —— 模型包调小比例不会连带改变抱持偏移。

### 旧版模型的裙摆：只能改模型，代码侧没有落点

坐下时裙摆要跟着抬起来，否则 -65° 的大腿会直接穿过裙子。旧版模型里这条规则挂在
一根叫 `sittingRotationSkirt` 的骨骼上：

- `GeckoModelLoader#LEGACY_BONE_ALIASES` 把它改名成 `Skirt`（子骨骼的 `parent` 一起改）；
- `maid_legacy.animation.json` 的 `sit` / `chair` 已按 `Skirt` 写好 `-32.5°`（= -0.567 rad）。

模型里没有这根骨骼就**再没有别的地方能转**：`InnerAnimations#sitSkirtRotation`
查的是 `sittingRotationSkirt` 这个名字，而旧版模型在加载时已经被改名成 `Skirt`，
那条 Java 分支永远取不到骨骼、直接 `return`。全库 254 个模型里 116 个带这根骨骼。

布都（`mononobe_no_futo.json`，1.10.0 几何）原本就缺它，已按上游旧版模型的写法补上：

| 项 | 值 |
|---|---|
| 新骨骼 | `sittingRotationSkirt`，`parent: body`，`pivot: [0, 12, 0]` |
| 裙摆 5 层 | 从 `body` 原样挪进来（`uv` [0,62] / [0,70] / [0,79] / [0,89] / [0,100]） |
| `clothFront` / `clothFront2` / `clothBack` / `clothBack2` | `parent` 由 `body` 改成 `sittingRotationSkirt` |

枢轴取 12：最高那层裙摆的上沿，与大转子的高度一致，坐下时裙摆与大腿同轴转。
94 个 cube 的 `origin` / `size` / `uv` 一个没动（`port/check-futo-skirt.ps1` 的断言），
而 `sit` / `chair` 只在坐姿播放，所以站立时的外形完全不变。

---

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

未移植：`board_games`（棋类，需要 `EntitySit` 娱乐方块子系统）。

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

---

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

---

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

### 密度与并发

原版 `Mob#baseTick` 的触发是 `random.nextInt(1000) < ambientSoundTime++`，
`resetAmbientSoundTime()` 把计数重置为 `-getAmbientSoundInterval()`。
默认 `interval = 80` 时平均间隔只有约 125 tick（≈6 秒），而且 `playAmbientSound()`
**完全没有并发控制** —— 场上三五只时每 6 秒各冒一句，听起来像「一直在说话」。

上游靠客户端概率过滤（`MaidSoundFreqEvent`：`soundFreq × 全局频率`），但默认值是
`1.0 × 100%`、**不过滤**；真正压住密度的是它那套自定义 SoundEngine 通道。

本移植版改用两层控制（`EntityMaid`）：

| 机制 | 实现 | 效果 |
|---|---|---|
| 单只间隔 | `getAmbientSoundInterval()` → 240 | 期望 `240+45 ≈ 285 tick ≈ 14 秒` |
| 全局节流 | 重写 `playAmbientSound()`，两次语音至少隔 60 tick | **同屏多少只，同一时刻只有一个在说话** |

全局节流用 `level.getGameTime()` 做时间轴；跨维度时差值为负 → 判为「不同时间轴」直接放行。

### 频率配置必须接到播放路径上

上游的频率过滤是**按声音实例**做的，拦的是**所有**女仆语音，不只是待机碎碎念 ——
因为在上游这些声音全走同一个自定义 SoundEngine 通道。本移植版收成一个入口：

```java
private boolean shouldPlayVoice() {
    double freq = MaidSubConfig.getSoundFreq(this)                 // 单只 0~1
                * MaidConfig.globalMaidSoundFrequency() / 100.0D;  // 全局 0~100
    return freq >= 1.0D || this.getRandom().nextDouble() < freq;
}
```

| 语音 | 接入方式 |
|---|---|
| 待机 / 工作模式环境音 | `playAmbientSound()` 里显式判 `shouldPlayVoice()` |
| 捡到东西 `maid.ai.item_get` | 改成 `playMaidVoice(...)` |
| 被驯服 `maid.ai.tamed` | 同上 |
| 受伤 / 着火受伤 | `getHurtSound()` 在频率不通过时返回 `null` |
| 死亡 | `getDeathSound()` 同上 |

> 受伤 / 死亡用「返回 `null`」而不是重写 `playHurtSound`：
> `Mob#playHurtSound` 里先调了 private 的 `resetAmbientSoundTime()`，重写会把它一起丢掉，
> 女仆挨打后会立刻开始碎碎念。而 `LivingEntity#playHurtSound` 取到 `null` 本来就直接跳过。

**不走**这个判定的：方块放置声、挤奶声、弩的射击声、落座音效 —— 那些是环境 / 交互动静。

坐下 / 起立音效照抄上游 `SwitchSittingEvent`：
`playSound(SoundEvents.ITEM_PICKUP, 0.2F, ((rand-rand)*0.7+1)*2)` ——
是原版拾取音效（不是 `maid.ai.item_get`），音调升一个八度。

---

## 祭坛 / 神社 / 墓碑 / 女仆床 / 稻草人

### 祭坛（多方块 + 祭坛合成）

一整座祭坛由约 20 个**独立的 `BlockAltar` 方块**组成，每个都有自己的 `TileEntityAltar`。
彼此靠这些字段保持一致：

| 字段 | 作用 |
|---|---|
| `blockPosList` | 整座祭坛的所有坐标 |
| `canPlaceItemPosList` | 顶部 `y == 2` 那圈原木 —— 能放贡品的位置 |
| `storageState` | 建坛前这里原本是什么方块，拆坛时用它还原 |
| `isRender` | **只有**位置等于 `posStart.subtract(getCenterPos(direction))` 的那个为真，否则 8×5×8 的模型会被画二十遍 |
| `direction` | 整座结构朝向，渲染时据此旋转 |

- 用御币右键红色羊毛搭建（`ItemHakureiGohei#useOn` → `MultiBlockManager`）。
  `isMatch` 把模板里的 `oak_log` 放宽成「任意 `#minecraft:logs`」，所以深色橡木 / 樱花原木都能搭。
  这也是必须走 `IMultiBlock` 接口而不是原版结构判定的原因。
- 结构模板 `data/touhou_little_maid/structures/altar_{north,south,east,west}.nbt`。
  1.18.2 的 `StructureTemplate#palettes` 是 private（上游靠 Forge AT 打开），走 `@Accessor`。
- **产物是实体，不是物品。** `AltarRecipe#spawnOutputEntity` 两条路径：
  有 `extraData` 就用 NBT 反序列化（可能是带满身数据的女仆、带乘客的盒子）；
  没有就 `entityType.spawn`（例如召唤闪电）。
  `isItemCraft` 为真（`entityType == EntityType.ITEM`）时结果栈写在 `extraData` 的 `"Item"` 里。
- `copyInput` + `copyTag` 把匹配到的某个材料的某个子标签抄到产物实体上 ——
  **神社复活与「胶片 → 女仆」都靠这条通路**，否则复活出来的女仆会丢掉名字 / 模型 / 好感度。
- `RecipeMatcher` 是 Forge 独占，用 Kuhn 增广路在兼容层里重写。
  注意它必须**跳过 `Ingredient.EMPTY` 补位**（上游把材料数组补齐到 6 条），
  否则「只写 3 条原料的配方」永远匹配不上。
- **`RecipeType.register(String)` 在 1.18.2 里把参数当完整 id**（内部 `new ResourceLocation(arg)`），
  所以必须传 `"touhou_little_maid:altar_crafting"`。

| 上游 Forge 独占 | 本移植版 | 影响 |
|---|---|---|
| `Block#onBlockExploded` | `Block#wasExploded`（在方块变空气**之前**调用） | 还原出来的方块可能扛过爆炸，比上游更结实 |
| `initializeClient(Consumer<IBlockRenderProperties>)` | 无对应物 | 破坏祭坛时用石头粒子而非原方块贴图 |
| `getSoundType(BlockState, LevelReader, BlockPos, Entity)` | 只有无参 `getSoundType(BlockState)` | 拿不到坐标，祭坛统一石头音效 |
| `BlockEntity#getTileData()` 旁路 tag | 直接写进 `saveAdditional` / `load` | 落盘结果逐字段等价 |
| `EntityType#getRegistryName()` | `ForgeRegistries.ENTITIES.getKey(type)` | 语义相同 |

`getCentrePos` 由上游的硬编码 `/ 8` 改成按「贡品那一层的实际坐标数」求平均 ——
当前模板下结果完全相同，但模板改动时不会再算偏。
### 1.20.1 的模型与纹理已回填

上游 1.20.1 把一批方块从「原版 JSON 模型」换成了 **GeckoLib 基岩模型**，纹理也从 16/32 px
升到 64/128/512 px，于是出现了「同一个祭坛，新版像摆件、旧版像灰盒子」的差别。
这一轮把能给的部分全换过来了。

**能直接换的，逐个字节覆盖：**

| 类别 | 文件 |
|---|---|
| 物品模型 | `models/item/*.json` 共 85 个（全部重写，1.20.1 版是一行紧凑 JSON） |
| 物品纹理 | `textures/item/*.png` 共 99 个，含新加的 `.mcmeta` 动画（保护类饰品、相机、无线 IO…） |
| 神社庭灯 | `models/block/maid_beacon_{down,up}.json`（`texture_size 128`，UV 按 128 记）+ 128×128 贴图 |
| 稻草人 | `models/block/scarecrow_{lower,upper}.json` + 64/128 px 贴图 |

**要改代码才能换的：祭坛与神社。**
1.20.1 里 `models/block/altar.json` 和 `shrine.json` 只剩一个 `particle` 字段（「灰尘粒子用什么贴图」），
几何全在 `models/bedrock/block/altar.json`（87 根骨骼 / 220 个 cube / 512×512 贴图）与
`shrine.json`（1 根骨骼 / 9 个 cube / 128×128）里。

本移植没有对应的方块渲染器，所以新增了两个小类：

- `client/resource/BedrockBlockModels`：按名字懒加载并缓存 `AnimatedGeoModel`。
  **只做解析，不做骨骼改名、也不补 `Root`** —— `GeckoModelLoader` 那两步是给旧版女仆模型
  复用 `maid.animation.json` 用的；方块模型名是 `new`/`A`/`B`，改名表根本不命中。
- `client/renderer/tileentity/GeoBlockRenderer`：把 `AnimatedGeoModel` 当静态模型画。
  `IGeoRenderer` 的方法几乎全是 `default`，只需要提供「当前 buffer」和「贴图」，
  逐骨骼矩阵 / 逐面顶点 / 面法线全部复用实体那条已经调好的实现 ——
  尤其是 `renderCube` 里那段「不要翻转扁平立方体法线」的修正，重写一遍必然重新踩坑。

原来 Java 里烘焙的 `AltarModel` / `ShrineModel`（`EntityModelLayer` + 手写 `ModelPart`）已删除。

**三个 1.18.2 侧必须补的坑：**

1. **`render_type` 字段 1.18.2 不认。** 它是 1.19.4 才进原版的，1.18.2 的 `BlockModel`
   会直接忽略。神社庭灯 / 稻草人的模型写着 `"render_type": "cutout"`，
   不显式登记的话镂空处会渲染成黑色实心 —— 必须
   `BlockRenderLayerMap.INSTANCE.putBlock(block, RenderType.cutout())`。
   这是**唯一**一处 `grep` 不到 `ItemBlockRenderTypes` 的原因：本项目此前一个都没有。
2. **模型是懒加载的，构造函数里不能读文件。** `BlockEntityRenderer` 在首次资源重载
   **之前**就被创建，那时 `ResourceManager` 还读不到 mod 自带资源。
   所以 `TileEntityAltarRenderer` / `TileEntityShrineRenderer` 的构造函数留空，
   改为 `render()` 里 `BedrockBlockModels.get(name)`，并在资源重载监听器里
   `clear()` 缓存 + 预热一次（预热会把「模型写错」变成启动日志里的一条 error，
   而不是游戏里「方块看不见」）。
3. **UV 两套写法都要支持。** 1.12.0 几何用 `"uv": [x,y]` 盒式 UV，1.21.0 几何
   （神社）用 `"uv": {"north": {"uv": [..], "uv_size": [..]}, ...}` 逐面 UV。
   本移植的 `UvUnion` / `UvFaces` / `FaceUv` 本来就两套都实现了，所以不用改。

**祭坛上悬空旋转的贡品**（1.20.1 新增，1.18.2 没有）：

```java
double time = (System.currentTimeMillis() + te.getBlockPos().asLong()) % 3600;
poseStack.translate(0.5, 1.25 + Math.sin(time / 1800 * Math.PI) * 0.1, 0.5);
poseStack.mulPose(Vector3f.YP.rotationDegrees((float) time / 10));
```

`sin` 周期 3600 ms（3.6 秒一次上下，振幅 0.1 格），Y 轴 3.6 秒转满 360°。
把 `asLong()` 掺进时间基准是为了让每座祭坛的相位不同 —— 否则一排祭坛上的物品会齐步升降。

**放 / 取贡品的音效**（同属 1.20.1 新增）：`ITEM_FRAME_ADD_ITEM` / `ITEM_FRAME_REMOVE_ITEM`，
音源 `PLAYERS`。拆祭坛时补了一声 `BEACON_DEACTIVATE`（`SoundSource.BLOCKS`，音量 1.5）。

**没换的**（各自原因不同）：

| 文件 | 原因 |
|---|---|
| `models/block/maid_bed_{head,foot}.json` | 1.20.1 也只剩 `particle` 字段，几何在 `models/bedrock/block/maid_bed/<颜色>.json`（7 种颜色）。床的渲染器还是 1.18.2 那套原版模型，换上基岩模型要重写渲染器并处理「7 种颜色怎么选」，留到女仆床重做时一起 |
| `models/bedrock/block/{bookshelf,computer,keyboard,picnic_mat,snack_cabinet,statue_base,cchess,gomoku,wchess}.json` | 对应的方块本身还没移植；文件已一并放进资源目录，等方块移植时直接接上 |
| `sounds.json` / `lang/*.json` | 1.20.1 版会覆盖掉本移植自己加的条目，必须逐键合并而不是整体覆盖 |
| `textures/entity/**` | 女仆模型走的是模型包（`maid_model.json` + 资源包），不属于方块贴图体系 |

`camera_2d.png`（→ `camera.png`，改成 16×160 的动画贴图）与
`block/scarecrow.png`（→ 拆成 `scarecrow_lower` / `scarecrow_upper`）已随上游删除。

---


### 神社（胶片复活）

- `BlockShrine` + `TileEntityShrine`，单格、只收 `ItemFilm`。
- 空手右键复活：需 `getHealth() >= getMaxHealth() / 2 + 1`，成功后把玩家血量降到 1/4。
- 潜行空手取回胶片，破坏方块弹胶片，`getRenderShape → ENTITYBLOCK_ANIMATED`。
- `ItemFilm` 刻录的是 `EntityMaid#saveWithoutId` 快照，并**刻意抹掉**
  背包 / 饰品 / 装备 / 经验 / 血量 / 坐标 / 乘客 / 状态效果等字段 ——
  那些东西在女仆死亡时已经作为掉落物散出去了，再复制一份进胶片等于凭空刷物品。

### 墓碑（死亡收纳）

- `EntityTombstone`（72 格）+ `MaidWorldData`（`SavedData`，存档名与上游一致
  `touhou_little_maid_world_data`）。
- `EntityMaid#dropEquipment` 把护甲 / 双手 / 物品栏 / 饰品 / 隐藏栏合成一个
  `CombinedInvWrapper` 抽干，加上背包本身、背包额外数据、胶片，写进墓碑与全局记录。
  用 `alreadyDropped` 防重复。
- 另加 `remove(RemovalReason)` 覆写：`/kill` 走的是 `remove(KILLED)`、**不会**经过 `die()`，
  因此不会调 `dropEquipment` —— 上游为此专门补了一条。
- 墓碑把「掉出世界」的坐标夹到 `getMinBuildHeight() + 5 … getMaxBuildHeight()` 之内，
  否则在虚空 / 基岩里死亡会生成一个永远摸不到的墓碑。
- 它重写了 `move` / `push` / `hurt` / `thunderHit` / `refreshDimensions` 全部为空，
  `canCollideWith` 返回 false，`repositionEntityAfterLoad` 返回 false ——
  避免被水流冲走、被活塞推、被雷劈、读档后掉下去。
- 只认主人，或手里拿着 NTR 物品的管理员（`EntityMaid.getNtrItem()`，上游就是 `static`）。
- `IMaidBackpack#onSpawnTombstone` 钩子已加，但熔炉 / 储罐背包的 `IBackpackData`
  还没移植，所以是「无额外数据背包」的等价空实现。

| 上游 Forge 独占 | 本移植版 |
|---|---|
| `NetworkHooks.getEntitySpawningPacket` | `ClientboundAddEntityPacket` |
| `PetBedDrop.hasPetBedPos`（第三方宠物床兼容） | 省略（无该模组） |

### 祭坛召唤箱（`spawn_box`）

`EntityBox`：三层开箱（`FIRST_STAGE 64` / `SECOND_STAGE 60` / `THIRD_STAGE 0`），
开完掉 2~4 张纸，乘客（女仆）全程挂隐身效果，所以看起来是「盒子自己动」。
`entity.box` 音效在 `sounds.json` 与 `entity/box.ogg` 里本来就有。

### 女仆床与睡觉

`BlockMaidBed` 是双格（FOOT + HEAD）、碰撞箱只有 0~9/16（女仆是「躺」在上面）、
可以弹跳（落上去反弹 66%）、活塞免疫（`PushReaction.DESTROY`）。
`MaidBedTask` 只认**床头**（`PART == HEAD`）的 POI，注册在 `Activity.REST`，`Pair.of(5, …)`。

**1.18.2 必须自己做的两步**（Forge 补丁把它们塞进了 vanilla）：

1. `startSleeping` 里对「是床的方块」`setValue(OCCUPIED, true)` + `setBlock(…, 3)`。
   女仆床不是 `BedBlock` 子类，原版那步不会执行，不做的话床永远不显示被占用。
2. `onMaidSleep()` 每 tick 重申一次坐标、清零速度并静音（上游 `baseTick` 里就有，**两端都要跑**）。
   少了它女仆会从床面滑下去。

**`checkBedExists` 必须打 mixin。** 原版 `LivingEntity#tick()` 里有：

```java
if (this.isSleeping() && !this.checkBedExists()) this.stopSleeping();
```

`checkBedExists()` 的判定是 `getBlockState(pos).getBlock() instanceof BedBlock`，而女仆床不是
`BedBlock` 子类 —— 女仆刚躺下、下一 tick 就被判「床没了」，`MaidBedTask` 又把她按回去，
表现就是**反复仰卧起坐**。Forge 把这里换成了 `Block#isBed` 钩子，Fabric 侧没有可覆写的方法，
`checkBedExists` 又是 private。所以新增 `mixin/LivingEntityMaidBedMixin` 注入 HEAD + cancellable，
只对 `BlockMaidBed` 返回 `true`（玩家睡原版床完全不受影响）。

**睡姿：旧版模型必须跳过 JSON 的 `sleep`。** 躺平由 `LivingEntityRenderer#setupRotations` 完成：

```
Y(sleepDirectionToRotation(bedFacing)) → Z(getFlipDegrees() = 90) → Y(270)
```

进入模型空间时身体已经是平的，模型空间的 Y 轴此时指向**水平**方向。而
`maid.animation.json` 里 `sleep` 的 `Root` 是 `[-13.26, -77.80, 23.76]` ——
那个 -77.8° 的 Y 旋转此时不再表示朝向，而是**绕身体长轴翻滚**（侧躺），
并且绕 `Root` 原点旋转、把身体推离床心。
上游对旧版模型根本不吃 JSON 动画，睡觉走 `MaidBaseAnimation` 的硬编码逻辑
（head 抬 15°、隐藏帽子/尾巴/翅膀、切换睡眠贴图，**无任何躯干翻滚**）。
修法与 `boat` 同款：`AnimationRegister` 里给旧版模型注册一个 HIGHEST 的 `empty` 状态
（动画集里本来就有的空动画），让四肢完全交给 `InnerAnimations`。

**落点要同时管「碰撞箱」和「渲染」两条链路**，只改一条就会看出偏差：

- `EntityMaid#pinToBed`：碰撞箱落在两格床的**中点**（床头格中心沿 `FACING` 反退半格），
  竖直用 `headPos.getY() + 0.6875`（11/16，比 9/16 的床面高 2/16），与 vanilla
  `LivingEntity#setPosToBed` 同值。上游 1.18.2 写的 `0.8` 会浮起来，1.20.1 改的 `0.5625` 又会陷进去。
- `GeoReplacedEntityRenderer#render`：它覆写了 `render()` 且**不调 super**，所以 vanilla
  `LivingEntityRenderer#render` 里那段 `translate(-dir * (getEyeHeight(Pose.STANDING) - 0.1f))`
  一次都不会执行，必须自己补一次，且**只补一次**（两处都写就是双倍位移）。
  补的量取**站立**身长的一半。

> **不要用 `getBbHeight()` 算这个量。** 它返回的是**当前姿态**的碰撞箱，而 `Pose.SLEEPING`
> 只有 `0.2 × 0.2`，半身长会塌成 0.1，位移几乎为零 —— 这也是好几次改数值都「看不出变化」的原因。
> 用 `getDimensions(Pose.STANDING).height * 0.5F`。

### 稻草人

双格 `HorizontalDirectionalBlock`，上半格碰撞箱用 `VoxelShapeUtils.rotateHorizontal`
从朝北那一份转 90/180/270°。POI 注册 `scarecrow`（下半格）。
`EntityFairy#checkFairySpawnRules` 查 `InitPoi.SCARECROW`，`MiscConfig.SCARECROW_RANGE`
（默认 48）格内不刷妖精。注意用的是 `getInSquare`（方形）而不是 `getInRange`（球形）。

### 帕秋莉手册

- 56 个 `patchouli_books/` JSON 与上游逐字节相同，但 `data/.../book.json` 上游**没有打包**，已补（version 5）。
- 侧边页签：`MaidSideTabButton` + `AbstractMaidScreen.addSideTabButtons()`，位置
  `leftPos+251 / topPos+37`，图标 UV `(193,111)`。框架在 `maid_gui_side.png` 里其实是
  **两个独立的 21×25 圆角格**（列 107–130 与 132–155，中间第 131 列是空的），所以只画 21×25。
  点击触发 `OpenPatchouliBookEvent`，未装 Patchouli 时聊天栏给下载提示（与上游一致）。
- **一个坏的 spotlight 页会让整本书作废。** `BookContentsBuilder#load → #build →
  BookEntry#build → page#build`，spotlight 页会把 `item` / `items` 解析成 `Ingredient`，
  一个不存在的物品 ID 直接抛 `JsonParseException` → `BookContents.empty` → 「Loading error!」。
  而 entry 的 `icon` **不是**致命的（`BookIcon#from` 只 warn 并回退成空 `StackIcon`）。
  所以产物未移植的页要用 `port/book_pages_held_back/` 暂存，不能用「图标占位」蒙过去。
- 当前 36 条目 / 3 分类，`port/validate-book.ps1` 校验 0 问题。
- 创造模式页签用 `FabricItemGroupBuilder#appendItems` —— **它会替换默认填充**（反编译
  `FabricItemGroupBuilder$1#fillItemList` 确认不调 `super`），所以回调里必须自己遍历
  `Registry.ITEM` + `fillItemCategory`。

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

### 生成规则（三层，缺一层就会「满地妖精」或「一只都没有」）

1. **能不能被抽中** —— `mixin/NaturalSpawnerMixin` 往 `NaturalSpawner#mobsAt` 的返回值追加一条权重：
   只在 `MobCategory.MONSTER` 上加；权重 = `MiscConfig.MaidFairySpawnProbability`（默认 70，
   僵尸是 100，末影人 10），**0 = 完全不生成**；维度在 `MaidFairyBlacklistDimension` 里就不加；
   **该群系原有的刷怪表里有僵尸才加** —— 这条同时排除了蘑菇岛之类没有敌对生物的群系。
2. **这个位置能不能刷** —— `EntityFairy#checkFairySpawnRules`，通过
   `MaidReflection.registerSpawnPlacement` 注册进 `SpawnPlacements`
   （`ON_GROUND` + `MOTION_BLOCKING_NO_LEAVES`），之后再查稻草人驱散。
3. **长什么样** —— `finalizeSpawn` 随机 18 种发色之一，5% 概率把名字设成 `rick`（彩蛋贴图）。

> `mobsAt` 是刷怪循环的热路径，注入处做了**按原刷怪表对象身份的缓存**
> （`ConcurrentHashMap`，`WeightedRandomList` 没有重写 `equals/hashCode`，即 identity 语义）。
> 命中缓存后零分配；权重配置变化时整体失效。

### 掉落

`IHasPowerPoint#dropPowerPoint` 在 `deathTime == 20`（死亡动画播完那一 tick）掉落，
总量 = `MaidFairyPowerPoint × 100`（默认 16 P），再按 1/3/5/7/13/34/89/185/285/385/485
的档位**拆成几堆**。上游**没有**妖精的战利品表（纯代码驱动），本移植版相同。

### 模型

只移植了旧模型（`EntityFairyModel`，手写网格，128×64 贴图，18 张发色贴图在
`textures/entity/maid_fairy/`）。配置 `UseNewMaidFairyModel` 保留但暂不生效 ——
新模型是 `simplebedrockmodel` 的 JSON 模型，工作区里只有贴图
（`textures/entity/new_maid_fairy/*.png`，128×128）**没有 JSON**。

---

## 经验球、P 点与灵力

### 渲染替换

`ReplaceExperienceOrbRenderer` 覆盖 `EntityType.EXPERIENCE_ORB` 的渲染器，贴图
`textures/entity/point_item.png`，按 `ExperienceOrb#getIcon()`（0~10）取格子，经验越高点越大。
`EntityPowerPointRenderer` 同理（`power_point.png`，带上下浮动）。

覆盖原版渲染器用 Fabric 的 `EntityRendererRegistry.register`：它在
`EntityRenderers.<clinit>` 的 **RETURN** 处才把模组注册项回放进 `PROVIDERS`，
所以能盖掉原版那一项 —— **在 `ClientModInitializer` 里注册即可，不需要 Mixin。**

### 实体 / 物品 / 拾取

- `power_point` 实体：`sized(0.5, 0.5)`、`clientTrackingRange(6)`、`updateInterval(20)`，
  像经验球一样轻微朝玩家飘，`throwTime` 内不可拾取，6 分钟后消失，可存盘。
- `power_point` 物品：右击沿视线丢出（俯仰压低 20°），落地变实体。
  因为没有移植 `EntityThrowPowerPoint`，丢出动作直接给实体初速度，少一个实体类型、手感一致。
- 数值换算：**4P = 1XP**；总 P 值按 1/3/5/7/13/34/89/185/285/385/485 拆堆。
- `EntityMaid#pushEntities` 每 tick 检查身边可拾取实体并分派：`ItemEntity` → 可用物品栏；
  `AbstractArrow`（`Pickup.ALLOWED`）→ 物品栏；`ExperienceOrb`（`tickCount > 2`）→ 女仆经验；
  `EntityPowerPoint` → 4P→1XP 折算后加经验。
  四者都先走「经验修补」（从护甲 / 双手 / 背包随机挑一件带经验修补且已损耗的，
  按 1 经验 = 2 耐久修补，消耗的经验减半计入），剩下的才计入女仆经验。
- 拾取类型对应上游 `PickType`，存在 `DATA_PICKUP_TYPE`（字符串），读档损坏时回退 `all`。

### 女仆的经验值必须是同步数据

上游的 `DATA_EXPERIENCE` 是 `SynchedEntityData.defineId(..., INT)`。
写成普通字段会有三层后果：**不同步**（界面经验条读客户端实体，永远是 0）、
**不持久**（读档归零）、**精度**（经验球本来就是整数）。
现在与上游对齐：同步 `int` + NBT 键 `MaidExperience`。
死亡时按 `isAlwaysExperienceDropper() = true` / `getExperienceReward() = getExperience()`
把她攒的经验掉出来。

### 灵力（Power）

**1.00 灵力 = 100 P 点。** 这是整套系统唯一的换算比：

| 位置 | 单位 | 例子 |
|---|---|---|
| 配置 `MaidFairyPowerPoint` | 灵力 | `0.16` |
| 实体 `EntityPowerPoint#getValue()` | P 点 | `16` |
| 玩家 `PowerCapability#get()` | 灵力 | `0.16` |

上限 `MAX_POWER = 5.00`（500 P）。满了还继续拾取时，溢出部分按 4P = 1XP 折算成经验
（上游的整数算法，避免浮点累积误差）。

上游用 `Capability` + `ICapabilitySerializable` 挂在玩家身上，Fabric 没有 capability，改成：

| 文件 | 作用 |
|---|---|
| `capability/PowerCapability` | 纯数据（`add` / `min` / `set` / `get` / `dirty`），与上游逐行一致 |
| `capability/IPower` | 标记接口 + `IPower.getPower(Player)` 静态入口 |
| `mixin/PlayerPowerMixin` | 给 `Player` 加 `@Unique` 字段并实现 `IPower`（懒初始化） |
| `mixin/ServerPlayerPowerMixin` | 存档读写（NBT 键 `FutoPower`） |
| `event/PowerEvents` | 死亡扣减、重生复制、脏数据同步 |
| `network/PowerSyncPacket` | S2C 同步（只在 dirty 时发） |
| `client/event/ShowPowerEvent` | 手持御币时 HUD 左上角显示 `×N.NN` |

**为什么字段长在 `Player` 上而不是外部 `Map<UUID, Float>`：** 玩家对象在切维度 / 重生 / 重连时
会被替换，外部表要么漏清理、要么在新对象上读到旧值；而且客户端（`RemotePlayer`）也需要同一份数据。

**死亡惩罚拆成两步**（上游在 `PlayerEvent.Clone` 里一次做完）：
① `ALLOW_DEATH` 在**旧**玩家身上扣 `PlayerDeathLossPowerPoint`（默认 1.00）；
② `COPY_FROM` 一律把旧值抄给新玩家。`keepInventory true` 时不扣。
> wiki 上「死亡后**无论如何都会失去所有** P 点」的说法已过时：代码扣的是配置值，默认只扣 1.00。

### 关于 wiki 那句「P 点越高，御币发出的弹幕越强」

**上游代码里没有这个机制。** 在 1.20.1 全树 grep `POWER_CAP`，读取灵力的地方只有六处：
同步包、信标存取、P 点拾取、`/power` 命令、`BlockAltar`（建坛消耗）、HUD 显示。
弹幕相关的 `TaskDanmakuAttack` / `DanmakuShoot` / `ItemHakureiGohei` **完全不读灵力**。
本移植版按代码实现，未加该机制。

### 玻璃瓶换附魔之瓶（上游 `GetExpBottleEvent`）

手拿**玻璃瓶**右击自己的女仆，把经验换成附魔之瓶：**12 XP = 1 瓶**
（WIKI 说附魔之瓶掉 3~11 XP，所以取 12 才不亏）。
不潜行一次换 1 瓶（拿 64 个空瓶单击也不会一次兑光）；潜行换到手上瓶子用完为止。

这个分支**必须排在 `openMaidGui` 之前**，否则拿玻璃瓶右击会直接开界面，玩家永远换不到经验。
上游靠 `InteractMaidEvent` 的注册顺序做到，本移植版在 `mobInteract` 里显式放在背包交互之前。

### 附魔之瓶 / 不死图腾的图标替换

原版 `experience_bottle` 的 `#inventory` 模型换成 `item/point_item`，不死图腾换成
`item/life_point`。开关在 `VanillaConfig`：`ReplaceXPBottleTexture` / `ReplaceTotemTexture`。
这是 **1.20.1 才有的功能**，按「向 1.20.1 对齐」补上（贴图与 JSON 资源本来就在）。

实现方式与上游不同，原因在 `client/init/InitSpecialItemRender` 的类注释：

| | 上游（Forge 1.20.1） | 本移植版（Fabric 1.18.2） |
|---|---|---|
| 钩子 | `ModelEvent.BakingCompleted` | `ModelLoadingRegistry#registerVariantProvider` |
| 做法 | 塞 `ReplaceableBakedModel` 包装器，每帧按配置决定 | 直接返回另一个 `UnbakedModel` |
| 代价 | 可游戏内热切换 | **只能加载时决定**，改配置需 F3+T |

好处是不需要写那个把十几个 `BakedModel` 方法全部转发的包装类。

---

## 神社庭灯（maid_beacon）

既是装饰与光源（上半格亮度 15），也是 P 点的存储器与女仆增益道具。
右击**上半格**开界面；界面里能存取灵力、选增益效果、决定溢出 P 点是删还是留。

- **两格高**：下半格 `position=down` 只有灯座模型、没有方块实体；上半格
  `position=up_n_s` / `up_w_e` 才是本体。朝向由放置时玩家朝向决定。
- **两格互锁**：`updateShape` 把另一半变成空气；物品由方块实体的 `onRemove` 弹出（带存储的 P 点）。
  拆任意一半都只掉一个庭灯，既不丢数据也不会复制。
- **掉落不走战利品表**：上游也没给 `maid_beacon` 战利品表，掉落完全由 `onRemove` 负责 ——
  再挂表就会掉两份。
- **活塞免疫**：1.18.2 没有 `Block#getPistonPushReaction`（Forge 补丁），
  改用自定义 `Material`（`PushReaction.BLOCK`，其余参数同 `Material.WOOD`）。
- 方块实体每 80 tick：先扣效果消耗，再吸收半径内的 `EntityPowerPoint`（`P / 100` 换算），
  满储或「删除溢出」时把 P 点销毁。效果间隔 100 tick、等级 II、范围 8 格，只作用于**存活的女仆**。
- 上游 NBT 写在 `getTileData()` / `getPersistentData()`；本移植版存档走标准
  `saveAdditional` / `load`，**物品形态**仍保留 `StorageData.ForgeData.*` 层级，
  便于读取上游存档里的庭灯。
- 界面不是 Menu 而是纯客户端 `Screen`：服务端下发 `OPEN_GUI`（带坐标），客户端从
  客户端侧方块实体读数据。C2S 三个动作：选效果 / 切换溢出 / 存取灵力，服务端复核
  「坐标处确有庭灯」「距离 ≤ 8 格」「效果序号在 `-1~4`」，负数 / NaN 数量直接丢弃。
- 1.18.2 的 `Screen` 没有 `renderables` 字段（1.19+ 才有），效果按钮由界面自己记列表；
  用 E 关闭走 `KeyMapping#matches(int, int)`。

配置项（`MiscConfig`，键名与上游一致）：`ShrineLampEffectCost`（0.9 灵力/小时）、
`ShrineLampMaxStorage`（100）、`ShrineLampMaxRange`（6 格）。

**获得途径**：祭坛配方 `recipes/altar/craft_maid_beacon.json`（已移植）。

---

## 受伤、盾牌与主人误伤

### 主人（及同队玩家）打自己的女仆 → 伤害 1/5、封顶 2 点

上游 `EntityMaid#hurt` 的第一条分支，逐字移植。判定用 `isAlliedTo` 而不是 `isOwnedBy`：
`TamableAnimal` 重写了它，所以**主人本人**与**主人的队友**都会命中。

### 弹幕能被盾牌挡下（1.18.2 独有）

1.20.1 用伤害类型标签（`BYPASSES_SHIELD` / `BYPASSES_ARMOR`）判断，1.18.2 **都没有**，
只有 `DamageSource#isBypassArmor()` 一个布尔。而
`LivingEntity#isDamageSourceBlocked` 第一句就是 `if (source.isBypassArmor()) return false;`
（字节码核对过）—— 「无视护甲」与「可被格挡」共用一个标志位，**无法共存**。

上游弹幕必须无视护甲，所以格挡只能在 `EntityDanmaku#onHitEntity` 里显式做（`tryBlockWithShield`）：

1. 目标 `isBlocking()`；
2. 目标正面朝向来源（`受害者→来源` 与视线点积 > 0）；
3. 挡下后按 `Player#hurtCurrentlyUsedShield` 的规则磨盾：单次伤害 ≥ 3 才掉耐久，
   消耗 `1 + floor(damage)`，播 `SHIELD_BLOCK`。

> 不用 `LivingEntity#hurtCurrentlyUsedShield`：它在 vanilla 里是**空实现**
> （`Code: 0: return`），真正扣耐久写在 `Player` 的重写里。女仆不是 `Player`，
> 走原版那条路等于「挡成功但不掉耐久」。

### 女仆的自动举盾

挨了投掷物时自动举盾 5 秒（`passiveUseShieldTick = 100`），前提是副手拿着盾。
注意这一下**仍然吃满伤害**：举盾发生在伤害结算之后。
`isBlocking()` 也做了重写：上游用 Forge 的 `ToolActions.SHIELD_BLOCK`，1.18.2 没有，
退回 `ShieldItem` 判定。

---

## 饮食系统（工作餐 / 回血餐）

### 吃东西与喝药的入口是「AI 主动 `startUsingItem`」

女仆不是玩家，**没有任何按键会让她吃或喝**。上游里这两个动作只由
`IMaidMeal#onMaidEat` 里的一句 `maid.startUsingItem(hand)` 引发：

```java
maid.startUsingItem(hand);   // ← 全部动画与进食逻辑的起点
```

一旦进入「使用物品」状态，剩下全是原版流程：每 tick 递减 `useItemRemaining`、
到 0 时 `completeUsingItem()` → `Item#finishUsingItem` → `LivingEntity#eat`，
动画由 `predicateUse` 看到 `isUsingItem()` 决定，粒子 / 音效走 `ItemStack#onUseTick`。
**移植时一行都不用写。** 反之，若没有这条路径，`predicateUse` 的整段分支就是死代码。

### 三类餐

| 类型 | 行为 | 位置 / 优先级 | 触发条件 |
|---|---|---|---|
| `WORK_MEAL` | `MaidWorkMealTask` | `Activity.WORK`，priority 7 | 工作时间 + 没睡觉 + Task 允许吃饭 + 工作餐冷却走完 |
| `HEAL_MEAL` | `MaidHealSelfTask` | `Activity.CORE`，priority 3 | 缺失生命 ≥ 2 + 没睡觉 + Task 允许吃饭 |
| `HOME_MEAL` | `MaidHomeMealTask` | —— | **未移植**：依赖野餐垫 + POI + `EntitySit` |

`HEAL_MEAL` 挂 CORE 而不是 WORK：工作 / 空闲 / 休息三种活动下都会触发。

### 工作餐的两段式取食

1. 先看**手上**拿着的东西能不能吃，能就直接吃；
2. 否则挑一只空手（主手优先，两只手都占着就抢副手），从**背包**里取一份能吃的换到手上；
3. 原本手上的东西存进隐藏栏 `hideInv`（1 格），吃完时 `completeUsingItem()` 再还回手上。

`hideInv` 必须写进存档（NBT 键 `MaidHideInventory`）：换手期间区块被卸载，
藏起来的那把剑不能凭空消失。

### 好感度自带的冷却才是「不会一直吃」的原因

`Type.WORK_MEAL` 点数 1、冷却 **2 分钟**；`Type.HOME_MEAL` 冷却 1 分钟。
`FavorabilityManager#canAdd(typeName)` 不通过时行为根本不会启动。

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
| 好感度用 Forge capability 挂在实体上 | 实体自己的字段 + NBT 子标签 `FavorabilityManagerCounter`（键名相同） |
| 升级时发 advancement trigger | 没有成就系统，省略 |
| 心里粒子发 `SpawnParticleMessage` | 直接用 `ServerLevel#sendParticles` |
| 吃完发 `MaidAfterEatEvent` | 在 `EntityMaid#eat` 里直接做同一件事 |
| 正则黑名单在配置加载时展开成 id 表 | 按需用 `Pattern` 匹配（配置项本来只有个位数），编译结果带缓存 |
| `stack.getFoodProperties(maid)` | 1.18.2 只有无参 `Item#getFoodProperties()` |
| `foodAfterEat.getContainerItem()` | 1.18.2 只有 `getCraftingRemainingItem()`，且要先问 `hasCraftingRemainingItem()` |
| `Entity#isStruckByLightning()` | vanilla 没有，改为在 `thunderHit` 里记一个位 |

已接线的配置项（此前全部没被读过）：`MaidWorkMealsBlockList` / `MaidHealMealsBlockList`、
两个 `*BlockListRegEx`、`MaidEatenReturnContainerList`
（形如 `[["minecraft:beetroot_soup","minecraft:bowl"]]`，先看物品自带的
`getCraftingRemainingItem()`，为空才查这张表）。

---

## 六个右键物品交互

上游把这六个功能各写成一个 `InteractMaidEvent` 订阅者；本移植版在
`EntityMaid#mobInteract` 里显式串起来。六项匹配**不同物品**、彼此互斥，
所以它们之间的先后不影响结果；真正有顺序要求的是它们与「潜行分支」的相对位置。

| # | 物品 | 条件 | 行为 | 对应上游 |
|---|---|---|---|---|
| 1 | 鞍 | 直接右击 | 女仆成为玩家的**乘客**；再右击一次放下 | `SaddleMaidEvent` |
| 2 | 药水（可饮） | **Shift** + 右击 | 效果作用在女仆身上，消耗并返还玻璃瓶 | `ApplyPotionEffectEvent` |
| 3 | 奶桶 | **Shift** + 右击 | 清除药水效果，返还桶 | 同上 |
| 4 | 金苹果 | **Shift** + 右击 | 走 vanilla `Mob#eat` | `ApplyGoldenAppleEvent` |
| 5 | 指南针 | 直接右击 | 客户端画出她的工作区域（30 秒） | `MaidAreaClickEvent` |
| 6 | 空命名牌 | 直接右击 | 打开改名界面（带「名字常显」开关） | `UseNameTagEvent` |

1. **它们必须排在潜行分支之前。** 上游 `SwitchSittingEvent`（潜行右键切坐姿）优先级是
   `LOWEST`，所以「Shift + 药水」「Shift + 金苹果」必须先被各自分支吃掉，
   否则会变成「喝药水的同时坐下」。`DismountMaidEvent`（`LOW`）介于两者之间。
2. **`isRideable` 是「用鞍抱起」的总开关**。存在同步数据里、存档标签 `MaidIsRideable`
   （与上游同名），默认 `true`，由主界面左下角第三个按钮
   （`leftPos+51, topPos+206`，UV `84,0`，与上游逐像素一致）切换。
   关掉后鞍只会在服务端被拦下，女仆也坐不上船 / 矿车。走 `MaidConfigPacket` 的 `RIDEABLE`。
3. **指南针不需要网络包**。上游工作点在 `SchedulePos` 里、客户端拿不到，所以要发
   `SyncMaidAreaMessage`；本移植版的工作中心 / 半径 / 居家开关本来就在 `SynchedEntityData` 里。
   区域显示是纯客户端行为（`client/render/MaidAreaRenderer`）。

命名牌改名是唯一需要 C2S 的：`setCustomName` 不是同步数据，而且改名要消耗命名牌。
提交走 `MaidConfigPacket` 的 `NAME_TAG`，服务端复核「归属自己 + 手上还拿着命名牌」，
名字截断到 32 字符。界面是 `client/screen/NameTagScreen`
（`EditBox` + 常显开关，勾/叉图标直接用原版信标的 `textures/gui/container/beacon.png`）。

> **鞍的定位**：乘客位置由**载具**的 `Entity#positionRider` 决定，1.18.2 的默认实现是
> 「乘客站在载具原点」—— 女仆会整个人塞进玩家身体里，看起来像「用了鞍以后女仆消失了」。
> 上游为此写了 `Entity` 级 mixin；本移植版改成在 `EntityMaid#rideTick()` 里 `super` 之后
> 覆盖一次位置（玩家身前 0.75 格、抬高 0.15 格；玩家下坠时下沉 0.3 格）。
> 原因：本项目没有配置 mixin 注解处理器（不生成 refmap），而 `positionRider` 是**重载方法**、
> `@Inject` 必须写完整描述符，描述符里的方法名在正式环境不会被重映射到 intermediary。

### 交互提示（准星指向女仆）

`client/overlay/MaidTipsOverlay`：准星指向女仆时，在准星右上方画「手持物图标 + 提示图标 + 一行说明」。
文本与图标本来就在资源里，缺的只是渲染这一层。显示条件（与上游一致）：

1. **第一人称**、非旁观者；
2. `minecraft.hitResult` 真的命中一只**活着的**女仆；
3. 玩家与女仆不在**同一个载具**上（否则准星来回命中两者，提示会闪）；
4. 手持物在提示表里，且女仆**归属自己**。

| 手持物 | 提示 |
|---|---|
| 玻璃瓶 | 右击取出经验，Shift 右击全部取出 |
| 指南针 / 金苹果 / 药水 / 奶桶 / 命名牌 / 拴绳 / 鞍 | 各自的说明 |
| 剪刀（女仆有背包时） | 移除女仆的背包 |
| NTR 物品（**别人**的女仆） | 将其他人的女仆转换成你自己的 |

**一处刻意的加强：** 玻璃瓶额外要求 `maid.getExperience() >= 12`。上游不看经验值，
「0 经验也弹『右击取出经验』」是一句谎话。

关掉某一项：`config/touhou_little_maid-common.toml` 的 `[render]` 段，
例如 `EnableGlassBottleTip = false`。

> 1.18.2 的 HUD 钩子是 `HudRenderCallback#onHudRender(MatrixStack, float)`。
> 自绘时要自己关 `GL_DEPTH_TEST` 并开混合，否则提示会被实体 / 方块挡住。

---

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

钓鱼流程：扫描岸边 → 走到 5 格内 → 抛钩 → 等待（100~600 tick，受饵钓缩短）→
水花粒子 → 咬钩音效 → 收杆，战利品直接弹到身边，鱼竿 -1 耐久。
离钩超 16 格或换掉鱼竿立刻收回。

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

---

## 已知问题

- **未移植的子系统**：`EntitySit` / `EntityChair` 娱乐方块（椅子、野餐垫、连坐棋类都依赖它）、
  `MaidModelDetailsGui`、图标缓存、彩蛋模型、声音包 GUI。完整清单见上方「进度总览」。
- **`AltarRecipeComponent` 仍是占位实现**，目前只解析物品与标签两种。
- **部分祭坛配方未收录**，因为产物本身还没移植：书柜 / 扫帚 / 中国象棋 / 凿子 / 电脑 / 五子棋 /
  河童罗盘 / 键盘 / 野餐篮 / 红狐卷轴 / 小号 / 西洋棋 / 白狐卷轴 / 无线 IO / 仆人铃。
- **23 个帕秋莉页面**暂存在 `port/book_pages_held_back/`，等对应功能补齐再放回去。

---

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

