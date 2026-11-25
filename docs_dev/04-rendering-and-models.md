# 渲染与模型

> 移植过程笔记。摘自根目录 `README.md`（2026-11-25 拆分），返回索引：[`docs_dev/README.md`](README.md)。

## 渲染（GeckoLib3 管线）

### 并行层动画不是「TACZ 专用」，`FOX` 就靠它隐藏

`GeckoMaidEntity#registerControllers` 里那 8 个 `pre_parallel_*` 与 8 个 `parallel_*`，
上游是无条件注册的；本移植版曾误以为它们是 TACZ（枪械）联动专用，在没有 TACZ 时跳过了。
这个判断是错的：模型包里的 `parallel*` 动画负责的是**低血量表现与隐藏部件**，与枪无关。

以酒狐（`geckolib:winefox`）为例，`parallel1`（`loop: hold_on_last_frame`）写的是：

```
AllBody : scale = (血量 / 最大血量) < 0.2 ? 0 : 1
FOX     : scale = (血量 / 最大血量) < 0.2 ? 1 : 0
```

也就是说模型里那套「狐狸坐骑」子树平时 **scale 0**，只有血量掉到 20% 以下才显现；
`parallel3` 同理把 `Mask` 的 scale 设成 0。少了 parallel1，`FOX` 就永远停在模型自带的
默认缩放 1 —— 于是**站着时脚下也有一只狐狸**，而坐姿下那只（`sit` 里给 `Root` 下移）本来就是设计如此。

改回无条件注册后，实测（临时打印 `FOX` 骨骼的 scale）：

```
hp=20/20  ->  foxScale=0.00   rootY=0.00      （站着，狐狸隐藏）
hp=0/20   ->  foxScale=1.00   rootY=-12.1     （低血量/死亡，狐狸显现）
```


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

## 魔理沙的扫帚
一件物品 + 一个实体：右击方块摆出扫帚，玩家骑上去，然后**必须再有一只女仆也坐上来**
才能飞。少了女仆扫帚就只是往下掉 —— 这条判定就是「女仆是灵力的来源」这个设定
在代码里的样子，也是 HUD 那句提示存在的原因。
### 「双端都读得到按键」这一点，决定了整个实现的形状
**1.18.2 上游**的做法是在 `travel` 里直接读客户端：
```java
if (level.isClientSide) {
    // 不要问我为什么客户端数据能跑到服务端来
    // 一定是玄学
    keyForward = keyForward();   // Minecraft.getInstance().options.keyUp.isDown()
    ...
}
```
服务端永远读到 `false`，于是只能靠客户端自己飞、服务端被动跟着「玩家上报的坐骑坐标」走。
上游自己也知道这不干净，所以留了那句「一定是玄学」。
**1.20.1 上游**换成了读 `player.zza` / `player.xxa` / 跳跃键。这一套**在 1.18.2 同样成立**，
而且完全不需要客户端类。证据链（对着映射后的 jar 逐条读字节码，不是推断）：
1. 客户端每 tick 发 `ServerboundPlayerInputPacket`，字段是
   `xxa` / `zza` / `isJumping` / `isShiftKeyDown`；
2. `ServerGamePacketListenerImpl#handlePlayerInput` 把四个值交给
   `ServerPlayer#setPlayerInput(xxa, zza, jumping, shift)`；
3. `setPlayerInput` 的函数体是
   ```
   0: aload_0
   1: invokevirtual  isPassenger:()Z
   4: ifeq  56          ← 不是乘客就直接 return
   ...
   23: putfield  xxa:F
   42: putfield  zza:F
   47: putfield  jumping:Z
   53: invokevirtual  setShiftKeyDown:(Z)V
   ```
   也就是**「骑着的时候才写入」**——而骑扫帚恰好就是 `isPassenger()`。
结论：骑着的时候双端都能读到真实按键，于是控制逻辑可以放在服务端算。
本移植版直接采用这个方案，`travel` 里不再出现任何 `net.minecraft.client.*`。
> 这条同时解释了为什么 1.18.2 上游那四行客户端引用「看起来能工作」：
> 不是玄学，是**客户端在算、服务端没算**，而原版允许玩家上报自己坐骑的坐标。
### 跳跃键要一个 Mixin 访问器
`player.zza` / `xxa` / `isShiftKeyDown()` 都是公开的，但**跳跃状态不是**：
1.18.2 里它是 `LivingEntity` 的 `protected boolean jumping`，没有任何公开读取方法
（1.20.1 上游也是靠自己的 `LivingEntityAccessor` 才拿到的）。所以本移植版加了
```java
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean tlmIsJumping();
}
```
只是绕开访问修饰符，不改变任何行为。它注册在 `mixins`（双端）而不是 `client` 下 ——
服务端也要读这个字段。
### 与 1.20.1 的两处差异
1. **没有 `IBroomControl` 接口层。** 它在上游存在的意义是让附属模组用
   `ILittleMaid.registerBroomControl` 插入自己的飞行方式。本移植版还没有
   `ILittleMaid` 扩展注册表，写一个「只有内置实现、永远只有一个元素」的扩展点只会是假的。
   行为本身与 `PlayerBroomControl` 一致。
2. **没有 `HasCustomInventoryScreen` / `getRiddenSpeed` / `onGround()` 重写。**
   这三个 API 在 1.18.2 都不存在（前一个是 1.19.4+ 的接口，后两个分别是 1.19 与 1.20 才有）。
   速度乘子直接在 `travel` 里算，效果一致。
另外 `positionRider` 在 1.18.2 是**单参**的（带 `MoveFunction` 的那个重载是 private），
所以覆写点写 `positionRider(Entity)` 并在里面直接 `setPos`。
### 两处容易漏的细节
- **无敌的落体伤害。** 扫帚继承 `AbstractEntityFromItem`（即 `LivingEntity`），
  所以必须重写 `causeFallDamage` 返回 `false`，并在 `tickRidden` 里把
  `fallDistance` 清零 —— 否则一落地就摔死。
- **两个乘客时碰撞箱要长高。** `makeBoundingBox` 在有第二个乘客时把 `maxY` 加 1，
  否则「人 + 女仆」叠在一起的真实高度远超 0.5625 格，飞过单格空隙时上半身会穿过去。
  和上游一样只影响实体碰撞，不影响选框。
### 实体类型注册的两个坑
- **必须注册属性表。** `LivingEntity` 的构造函数里就有 `setHealth(getMaxHealth())`，
  没注册 `FabricDefaultAttributeRegistry` 就是 NPE。坐垫当年就是这么崩的
  （表现是「一切到坐垫页签就崩」，因为页签图标会构造一个假实体）。
- **`EntityType.Builder#build(String)` 要写全名。** 上游写的是 `.build("broom")`，
  于是原版那条 `No data fixer registered for broom` 日志里只有 `broom` 而没有命名空间
  （本移植版其它实体都是 `touhou_little_maid:xxx`）。这里改成全名，日志就一致了。

## 扫帚：下扫帚的键，以及不再抢走准星
两处都按上游 1.20.1 补齐。1.18.2 上游在这个子系统的这两块上是缺的（或者说法不一样），
所以这里以上游 1.20.1 为准。
### 1. 下扫帚需要一个单独的键（默认 C）
扫帚上 **空格是上升、Shift 是下降**
```java
// EntityBroom#rideControl
if (keyJump)        vertical = 0.25f;    // 空格
else if (keySneak)  vertical = -0.2f;    // Shift
```
原版那套「按 Shift 下车」正好被下降占用了，于是**必须另给一个键**。
上游 1.20.1 给的是默认 C、可在「选项 → 控制」里改，走一条 C2S 包到服务端 `stopRiding()`。
本移植版最初把这一整块漏了，结果骑上去就下不来。
| 文件 | 作用 |
| --- | --- |
| `network/DismountBroomPacket` | C2S：动作 id + 扫帚实体 id；服务端复核「你骑的就是它、它真的是扫帚」再 `stopRiding()` |
| `client/input/DismountBroomKey` | `KeyMapping`（GLFW_C，分类 `key.category.touhou_little_maid`） |
**为什么必须发包而不是客户端自己 `stopRiding()`：**乘客表、位置修正都在服务端，
客户端单方面下车只会让本地模型掉下去、下一 tick 被拉回来。
**为什么不在 shift 上做「长按下车」：**那会把下降变成不可用，而上游就是这么设计的，
照抄即可，不要自作聪明。
与上游的框架差异：注册走 Fabric 的 `KeyBindingHelper`，触发走
`ClientTickEvents.END_CLIENT_TICK` + `consumeClick()`（上游订阅 `InputEvent.Key` 并在
**松开**时才发包；`consumeClick()` 本身就是「一次按下只返回一次」，等价且不会长按连发）。
上游用 `KeyConflictContext.IN_GAME` 限定只在游戏内生效，Fabric 没有这个概念，
所以自己判「没开界面 + 鼠标被抓取 + 窗口是活动的」—— 少了这一步，在聊天栏打一个带 c
的单词就会把人从扫帚上甩下来。
### 2. 骑扫帚时不该抢走准星
现象：骑着扫帚对着前方的方块，**中键取方块却取到「魔理沙的扫帚」**，
右击 / 左击也打在扫帚自己身上，够不到前面的东西。
原因很直接：射线从玩家眼睛出发，而扫帚的碰撞箱 `1.375 × 0.5 × 0.5625`
就在脚底下、并且向前伸出将近一格，于是它抢在前面的方块之前被命中。
上游 1.20.1 的修法只有三行，注释写得也很直白（「防止其碰撞箱影响正常交互」）：
```java
@Override
public boolean isPickable() {
    boolean hasPlayer = this.getPassengers().stream().anyMatch(e -> e instanceof Player);
    return hasPlayer ? false : super.isPickable();
}
```
**关键是「只在有玩家乘客时为 false」**，不是一直 false —— 没人骑的时候还要能右键它上车、
Shift + 左键把它打掉（那条路径在 `AbstractEntityFromItem#applyHitEntityLogic`），
所以那种情况必须交回父类。
> 注意 1.18.2 上游的 `EntityBroom` **没有**这个覆写。也就是说这个 bug 在 1.18.2 原版里
> 大概是存在的，1.20.1 才修。本移植版与 1.20.1 对齐。
### 3. 潜行键被原版当成了「下车」
这是补上 C 键之后才暴露的第二个问题：**左 Shift 和 C 都能下车**，于是下降彻底没了。
原因不在扫帚，在原版：
```java
// Player#rideTick（1.18.2 字节码：isClientSide → wantsToStopRiding → stopRiding → setShiftKeyDown(false)）
public void rideTick() {
    if (!this.level.isClientSide && this.wantsToStopRiding() && this.isPassenger()) {
        this.stopRiding();
        this.setShiftKeyDown(false);
        return;
    }
    ...
}

// Player#wantsToStopRiding
protected boolean wantsToStopRiding() { return isShiftKeyDown(); }
```
也就是说**原版规则是「按着潜行键就从载具上下来」**（马、船、矿车都一样）。
扫帚把潜行占用成「下降」之后，这条规则直接把下降顶掉了：按一下 Shift 人就掉下去，
`keySneak ? -0.2f` 那行一次都执行不到。

上游 1.20.1 的修法是一条 Mixin 注入（`mixin/PlayerMixin#tlmWantsToStopRiding`）：
```java
@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
private void tlmWantsToStopRiding(CallbackInfoReturnable<Boolean> cir) {
    Player player = (Player) (Object) this;
    if (player.isShiftKeyDown() && player.getVehicle() instanceof EntityBroom) {
        cir.setReturnValue(false);
    }
}
```
本移植版逐字对齐，落在 `mixin/PlayerBroomMixin`，注册在**公共** mixin 列表
（`Player#rideTick` 里那条判断带着 `!isClientSide`，真正生效的是服务端）。

**为什么不能用别的写法：**
- `wantsToStopRiding()` 是 `protected`，扫帚改不了别人身上的判定；
- 在 `EntityBroom#tickRidden` 里每 tick 调 `player.setShiftKeyDown(false)` 不行 ——
  服务端每 tick 都会被 `ServerboundPlayerInputPacket` 写回真实按键
  （`ServerPlayer#setPlayerInput` 的四个写入全在 `if (isPassenger())` 里面），
  而且那样下降自己也就读不到潜行了；
- 改 `EntityBroom#canRide` 没用 —— 那是「上车」判定，不是「下车」判定。

注入点核对过 1.18.2 的字节码：`Player` 里 `wantsToStopRiding` 只被 `rideTick` 调用一次，
返回值就是 `isShiftKeyDown()`，与 1.20.1 完全同形，所以这条上游补丁可以原样搬过来。
### 验证的边界
Mixin 这种改动没法靠「跑一遍程序」证明 —— 它改的是原版方法的返回值。
这次的办法是**打开 Mixin 的导出开关，把改写后的 `Player` 类 dump 出来看字节码**：
```
JAVA_TOOL_OPTIONS=-Dmixin.debug.export=true gradlew runClient -PjoinServer
```
导出到 `run/.mixin.out/class/.../Player.class`，反汇编后是这样：
```
public boolean wantsToStopRiding();
   0: new  CallbackInfoReturnable("wantsToStopRiding", true)
  14: invokespecial handler$zzi000$touhou_little_maid$tlmWantsToStopRiding
  21: ifeq 29
  24: return cir.getReturnValueZ()
  29: invokevirtual isShiftKeyDown()Z
  33: ireturn
```
注入确实落在方法头，取消时走 `getReturnValueZ()`，未取消则照旧返回 `isShiftKeyDown()`。
同时客户端带着这条 Mixin 连上本地服务端跑过一次，`run/crash-reports` 为空、日志里没有 mixin 报错。

**不能自动验证的部分**：真人按键。骑上扫帚按住左 Shift 应该**稳定下降、人还在扫帚上**，
按 C 才是下来；对着前面的方块按中键应该取到方块而不是扫帚。这三条留给你实测。

## 扫帚：浮动、碰撞箱，还有那句提示

这一节的早期版本把「浮动要怎么写」「抖动为什么发生」当成结论写下来过，
但后续实测推翻了其中两条（浮动加在 `aiStep` 末尾会晚一个 tick、抖动若只是把箱子改大反而更糟）。
现在只保留**经实测确认**的部分，推导过程见下一节。

| 项 | 结论 |
| --- | --- |
| 静止浮动 | 上游那行 `0.003 * sin(tickCount * PI / 18)` 有效，但必须放在 `travel()` 里、`rideControl()` 之前；写在 `aiStep` 末尾会晚一个 tick |
| 「长高一格的物理箱」 | 只在 `Entity#collide` 里生效（`EntityBroomPhysicalBoxMixin`），真实 `makeBoundingBox()` 不得随乘客数变化 |
| 下车提示 | 原版 `mount.onboard` 写死 `options.keyShift`，必须整段接管 `handleSetEntityPassengersPacket` |

### 3. 那句提示：`mount.onboard` 里的键名是原版写死的

屏幕上那句「Press Left Shift to Dismount / 按下左 Shift 来脱离」不是本模组的文字，
是原版 `ClientPacketListener#handleSetEntityPassengersPacket` 里的：

```java
this.minecraft.gui.setOverlayMessage(
        new TranslatableComponent("mount.onboard", this.minecraft.options.keyShift.getTranslatedKeyMessage()), false);
```

只要玩家刚成为某个载具的乘客，原版就弹这句，而且**写死用 `options.keyShift`**。
扫帚上潜行键是「下降」（`PlayerBroomMixin` 又让它不再触发下车），所以这句话对扫帚是错的。

上游 1.20.1 的改法是整段接管这个方法（`mixin/client/ClientPacketListenerMixin`）：
把原版逻辑重抄一遍，只把 `options.keyShift` 换成 `DismountBroomKey` 那个键。
本移植版逐句对齐，只有两处 1.18.2 的框架差异：

- 1.18.2 没有 `Component.translatable(...)`，用 `new TranslatableComponent(...)`（与原版 1.18.2 自己的写法一致）；
- 1.18.2 的 `Minecraft` 没有 `getNarrator()`，上游那句 `getNarrator().sayNow(...)` 只能省略。

其余照搬：先 `PacketUtils.ensureRunningOnSameThread`、用 `hasIndirectPassenger` 记下「本来是不是乘客」、
`ejectPassengers()` 再逐个 `startRiding(vehicle, true)`、只有「玩家本来不是乘客」时才弹提示。
于是骑上扫帚看到的是「Press C to Dismount / 按下 C 来脱离」，
而且键名跟着按键绑定走 —— 玩家把 C 改成别的键，这句话也跟着变。

顺带一个容易踩的细节：`DismountBroomKey` 那个 `KeyMapping` 是 `init()` 里注册的，
而这个 mixin 可能先于 `init()` 被调用（例如还没注册键位就收到了乘客包），
所以 `getDismountKey()` 允许返回 `null`，调用方遇到 `null` 就**交回原版**（也就是还是老提示），
而不是 NPE。

## 扫帚浮动为什么看着生硬：两端都在算同一条正弦

上一轮把浮动加进了 `EntityBroom#tickRidden`，公式与上游 1.20.1 一字不差，
但看起来一顿一顿的。原因不在那行 `Math.sin`，在**它在哪个位置被调用**。

### 上游的结构：只有一侧在算位置

1.20.1 的 `LivingEntity#travelRidden`（这次从 1.21.1 的 NeoForge 源码包里逐行核对过）：

```java
private void travelRidden(Player player, Vec3 vec3) {
    Vec3 input = this.getRiddenInput(player, vec3);
    this.tickRidden(player, input);          // 浮动、朝向：两端都跑
    if (this.isControlledByLocalInstance()) {
        this.setSpeed(this.getRiddenSpeed(player));
        this.travel(input);                  // 只有这一侧真的 move
    } else {
        this.calculateEntityAnimation(false);
        this.setDeltaMovement(Vec3.ZERO);    // 另一侧：速度清零，不动
        this.tryCheckInsideBlocks();
    }
}
```

两件事同时成立：

1. `tickRidden` 在分支**之前**，所以「叠一条正弦速度」两端都会做；
2. 但只有「本地操纵」的那一侧会 `travel()` → `move()`，
   另一侧的速度当场被清零，位置完全来自客户端上行的 `ServerboundMoveVehiclePacket`。

### 本移植版之前错在哪

`EntityBroom#travel` 里无条件 `rideControl(player)` + `move(...)`，
也就是**服务端也在按自己的 `tickCount` 相位算同一条正弦、也在挪扫帚**。

而 1.18.2 的 `Entity#lerpTo(double, double, double, float, float, int, boolean)`
字节码只有两句 —— `setPos` 和 `setRot`，**没有任何插值**
（1.16 那种 `lerpSteps` 平滑在 `Entity` 上已经没了，只剩 `LivingEntity` 自己那一套）。
客户端这边上报也确实是每 tick 一次：`LocalPlayer#tick` 里的字节码是
「`getRootVehicle()` 不等于自己且 `isControlledByLocalInstance()` 为真 →
`send(ServerboundMoveVehiclePacket)`，否则 `sendPosition()`」。
于是客户端每收到一次位置包，就被硬按到服务端那条相位不同的正弦上：

- 客户端：`y = A·sin(ω·t)`
- 服务端：`y = A·sin(ω·(t+Δ))`，`Δ` 是两个实体的 `tickCount` 差，
  再加上服务端读到的 `zza` / `isShiftKeyDown` 总比客户端晚一个包

结果是每 tick 在两条正弦之间来回跳 —— 幅度不大但方向乱，
肉眼看到的就是「生硬」，而不是平滑的上下漂。

### 改法

删掉 `aiStep()` 覆写，把「`travelRidden` → `tickRidden` → `travel`」压进 `travel` 一个方法，
顺序与上游一致：

```java
@Override
public void travel(Vec3 vec3) {
    Entity controlling = this.getControllingPassenger();
    if (controlling instanceof Player player && this.isAlive()) {
        this.tickRidden(player);                        // 浮动 + 朝向：两端都跑
        if (!this.isControlledByLocalInstance()) {
            this.setDeltaMovement(Vec3.ZERO);           // 非本地操纵：不模拟
            this.tryCheckInsideBlocks();
            return;
        }
        if (this.hasPassenger(e -> e instanceof EntityMaid)) {
            this.rideControl(player);
            this.move(MoverType.SELF, this.getDeltaMovement());
            return;
        }
    }
    // ...原有的「没有玩家 + 女仆就往下掉」
}
```

`tryCheckInsideBlocks()` 1.18.2 已经有（`checkInsideBlocks()` 外面套一层崩溃报告），可以直接用，
不需要像 1.17 那样自己包 try/catch。

顺手修掉的还有浮动的相位：原来 `tickRidden` 放在 `aiStep` 末尾，
等于「先 `move` 再叠速度」，比上游整整晚一个 tick；
现在它与上游一样，在同一个 tick 里「叠速度 → 减速 → 移动」。
公式本身没动，`0.003 * sin(tickCount * PI / 18)`、周期 36 tick，
配合 `rideControl` 无输入时的 `scale(0.75)`，稳态位移大约 ±4 厘米，
和上游是同一条曲线。

### 验证的边界

能证明的：`javap` 出来的 `travel` 字节码顺序就是
`tickRidden` → `isControlledByLocalInstance` →（false）`setDeltaMovement(ZERO)` + `tryCheckInsideBlocks`
→（true）`rideControl` + `move`，与上游 `travelRidden` 的分支逐条对应；
`aiStep` 的覆写已经消失。

冒烟测试跑过一次：服务端召唤一把挂着女仆的扫帚，从 y=70 落到 y=57 稳定落地、
`OnGround=1b`、日志无异常 —— 也就是说「没有玩家操纵」那条老路径没有被这次改动破坏。

**没法自动验证的**：真人骑上去的手感。1.18.2 没有 `/ride`，服务端塞不上玩家，
而浮动只在「有玩家操纵」时才有。这条仍然要你实测：
现在扫帚静止悬停时应该是一条平滑的正弦，而不是之前那种碎抖。

## 史莱姆换成灵梦油库里（材质按上游 1.20.1 重绘后的那张）

原版史莱姆被替换成「灵梦油库里」。上游 1.20.1 在这次替换里**重画了贴图**，本轮把那张新图搬了过来。

### 上游到底改了什么

| | 1.18.2 Forge | 1.20.1（Fabric / Forge 一致） |
|---|---|---|
| 史莱姆贴图 | `textures/entity/yukkuri.png`（2520 B） | `textures/bedrock/entity/reimu_yukkuri.png`（1917 B，**重绘**） |
| 岩浆怪贴图 | `textures/entity/marisa_yukkuri.png`（2520 B，64×64） | `textures/bedrock/entity/marisa_yukkuri.png`（1753 B，**128×128，几何也换了**） |
| 模型 | 手写 `LayerDefinition` | `models/bedrock/entity/*.json` |

史莱姆那张是**纯材质改动**：尺寸仍是 64×64，UV 布局一个字节都没动，只有内容被重画。
用 `port/CmpPng.java`（JDK `ImageIO`，不依赖 PIL）逐像素比对：
**4096 个像素里有 577 个不同（14.1%）**，差异分布在整个上半身，alpha 覆盖率不变（1072 个非透明像素）。

### 本仓库原有状态

baseline 里只有两张**从没被引用**的旧贴图（`textures/entity/yukkuri.png`、
`marisa_yukkuri.png`），以及一个**只定义没接线**的配置项：
@code 块里写着「尚未移植」，`VanillaConfig.REPLACE_SLIME_MODEL` 在整个 `src/main/java` 里搜不到第二处。
也就是说这次不是「改材质」，而是**把整个替换层接上，并且用上游的新材质**。

### 实现

| 文件 | 作用 |
|---|---|
| `client/model/EntityYukkuriModel` | 灵梦油库里的网格（照抄上游 1.18.2 的 `LayerDefinition`） |
| `client/renderer/entity/EntityYukkuriSlimeRender` | 覆盖原版 `EntityType.SLIME` 的渲染器，贴图指向 1.20.1 的新图 |
| `textures/bedrock/entity/reimu_yukkuri.png` | 上游 1.20.1 重绘的那张（1917 B） |
| `VanillaConfig#replaceSlimeModel()` | 把原本没接线的开关接上，关掉就退回原版 `SlimeRenderer` |

注册照抄仓库里经验球替换那套（`TouhouLittleMaidFutoClient`）：先 `EntityModelLayerRegistry.registerModelLayer`，
再 `EntityRendererRegistry.register(EntityType.SLIME, ...)` —— Fabric 的这个 API 就是往 `EntityRenderers` 的 Map 里 put，
后注册覆盖原版那一项。

### 为什么用手写网格，而不是搬 1.20.1 的 bedrock JSON

1.20.1 的 `SimpleBedrockModel` 那套实体模型加载器**本仓库还没有**（只有方块的 `BedrockBlockModels`）。
但两边的几何是**逐盒等价**的，UV 一个不差：

| bone | 1.18.2 `texOffs` | 1.20.1 `uv` | 尺寸 |
|---|---|---|---|
| bone 主体 | (0,18) | (0,18) | 8×8×8 |
| bone 外层 | (0,0) | (0,0) | 9×9×9 |
| bone 耳朵 | (38,0) | (38,0) | 2×4×2，inflate -0.35，一只 mirror |
| bone 头饰 | (0,0) | (0,0) | 2×3×0 |
| bone2 / bone3 | (48,14) | (48,14) | 7×4×1，inflate -0.2 |
| bone4 / bone5 | (50,20) | (50,20) | 6×4×1，inflate -0.3 |
| bone6 / bone7 | (48,0) | (48,0) | 2×7×6 |
| bone8（尾巴） | (54,35) | (54,35) | 4×9×1 |

贴图尺寸都是 64×64，所以 1.20.1 的新图**可以直接贴这套网格**。既然几何等价，用手写网格是成本更低、
外观完全一致的等价替代（这也是仓库约定里「打不通的接口走等价替代并写明原因」的那种情况）。

### 缩放与开关

`scale` 照抄原版 `SlimeRenderer`：先 `scale(0.999, 0.999, 0.999)` 再按 `squish` 与体型算压缩比例，
所以油库里的大小、弹跳时的「挤压」手感与原版史莱姆完全一致；`shadowRadius` 跟着体型走。
`ReplaceSlimeModel = false` 时渲染器把调用原样转给内部的 `SlimeRenderer`。

### 验证

- `./gradlew.bat build` = BUILD SUCCESSFUL；四道闸全绿（`SCANNED_FILES=1560 BAD_FILES=0`、
  `entity types = 11 renderers = 11 / ALL OK`、`entries=54 problems=0`）。
- 打包产物里：`EntityYukkuriModel.class`、`EntityYukkuriSlimeRender.class` 都在，
  贴图 `assets/touhou_little_maid/textures/bedrock/entity/reimu_yukkuri.png` 也在（1917 B），
  且 class 常量池里的路径与它逐字相同。
- `runServer` + `runClient -PjoinServer`，进世界后用 RCON 
  `execute at Dev run summon minecraft:slime ~2 ~ ~2 {Size:3}` 召唤史莱姆：
  - 客户端**零** `Missing texture` / `Unable to load` 警告；
  - 零渲染异常、零 `crash-report`；
  - 史莱姆确实在 tick（日志里有 `Dev was slain by Slime`）。
- 模型层是不是真的烘焙了：渲染器的构造函数里就调 `context.bakeLayer(LAYER)`，
  Minecraft 重建 `EntityRenderers` 时会调用每个注册项；没抛 `Unable to find model layer`，
  即 layer 名与注册一致。

### 验证边界

- **没有做视觉验收**。自动化只能证明「渲染器构造成功 + 贴图存在且路径正确 + 无缺失贴图警告」，
  证明不了「画面里那只油库里长得对」。这一条要真人进游戏看。
- **岩浆怪那一半已于后续一轮补齐**（见下一节）。它不能照抄手写网格，改走 bedrock JSON。
- 旧的 `textures/entity/yukkuri.png` 与 `textures/entity/marisa_yukkuri.png` 留在原地未删（上游 1.20.1 也把它们
  留在 `legacy_pack` 里）；目前没有任何代码引用它们。

## 岩浆怪换成魔理沙油库里（直接吃上游 1.20.1 的基岩模型）

史莱姆那一半是「换材质」，这一半是**换整套外观**，所以实现方式也不同。

### 为什么不能照抄手写网格

| | 1.18.2 | 1.20.1（本实现） |
|---|---|---|
| 贴图 | 64×64（2520 B） | **128×128**（1753 B） |
| 几何 | 手写 `LayerDefinition`，9 根骨骼 | `marisa_yukkuri.json`，14 个 bone |
| UV | (0,18) / (46,14) / (0,49) / (48,0) … | (45,16) / (45,33) / (0,42) / (0,61) / (64,33) … **全换** |

史莱姆那两版是「逐盒等价、只重画贴图」，所以能照抄 1.18.2 的网格贴 1.20.1 的图。
岩浆怪不是：UV 一个都对不上，手写网格等于把 JSON 手工重排一遍，抄错一个 uv 就是贴图错位、而且极难定位。

### 做法：复用仓库已有的 geckolib 管线

新增 `client/resource/BedrockEntityModels`，读 `models/bedrock/entity/<name>.json`。
它和 `BedrockBlockModels`（方块）用的是同一条管线：

`Converter.fromJsonString` → `RawGeometryTree#parseHierarchy` → `GeoBuilder#constructGeoModel` → `AnimatedGeoModel`

这套管线只处理几何，不读 `geometry.<identifier>`、也不要求有动画，所以「方块」与「实体」的唯一区别就是目录。
渲染走 `GeoBlockRenderer.render(...)`（内部是 `renderRecursively`），几何与贴图都保持上游原样。

**坐标不需要额外换算**，这一点值得写下来，因为很容易想歪：

- `GeoCube` 构造时已经把 texel 除以 16、并把 x 轴翻成 Minecraft 方向；
- **cube 的 `origin` 是绝对模型坐标，不是相对于 bone 的 pivot** —— pivot 只用于绕点旋转；
- y 轴两边同向（向上），所以模型画出来的位置就是 JSON 里写的绝对位置。

按这条规则量出来：y ∈ [−0.13, 1.09]、x ∈ [−0.44, 0.44]、z ∈ [−0.43, 0.45]（单位：格），
底面就在脚底平面上，**不需要竖直平移**。

### 为什么继承 `EntityRenderer` 而不是 `MobRenderer`

`LivingEntityRenderer#render` 会给矩阵栈套上 `scale(-1, -1, 1)` 和 `translate(0, -1.501, 0)`，
那是给「y 向下」的原版 `EntityModel` 准备的。基岩几何是 y 向上、原点在脚底，直接画会上下颠倒。

想用 `MobRenderer` 就得在 `renderToBuffer` 里做逆变换，而那个逆变换依赖 `scale()` 算出来的压缩比例
（每个 tick 都随 `squish` 变化），**无法静态抵消**。

`EntityRenderer#render` 拿到的矩阵栈只有「平移到实体脚下」，正是基岩几何要的坐标系 ——
女仆渲染器（`GeoReplacedEntityRenderer`）也是这么绕开 `LivingEntityRenderer` 的。

阴影不受影响：它由 `EntityRenderDispatcher` 按渲染器的 `shadowRadius` / `shadowStrength` 绘制，
与是否继承 `MobRenderer` 无关（已用 javap 在 1.18.2 的 `EntityRenderDispatcher` 里确认调用点在 dispatcher 而不是 render 内部）。

### 其余行为

- `getBlockLightLevel` 恒为 15 —— 岩浆怪自发光，上游那半个渲染器也这么写；
- `shadowRadius = 0.25 * size`，跟着体型走；
- `squish` 形变逐字照抄原版 `SlimeRenderer#scale`（先 `scale(0.999)`，再按 `squish / (size*0.5+1)` 算压缩），
  所以弹跳手感与原版一致；
- `ReplaceSlimeModel = false`、或模型文件缺失/解析失败时，一律**退回原版** `MagmaCubeRenderer` ——
  宁可画成普通岩浆怪，也不要让它凭空消失。

### 验证

- `./gradlew.bat build` = BUILD SUCCESSFUL；四道闸全绿（`SCANNED_FILES=1563 BAD_FILES=0`、
  `entity types = 11 renderers = 11 / ALL OK`、`entries=54 problems=0`）。
- jar 里 `EntityMarisaYukkuriSlimeRender.class`、`BedrockEntityModels.class`、
  `models/bedrock/entity/marisa_yukkuri.json`、`textures/bedrock/entity/marisa_yukkuri.png` 都在，
  且 class 常量池里的贴图路径与模型名与文件逐字相同。
- 客户端启动日志出现 `[Futo] Entity bedrock model marisa_yukkuri loaded: 13 bones` ——
  这是资源重载时的**预热**打出来的，说明 JSON 在启动阶段就解析成功（不是等召唤时才试）。
- `runServer` + `runClient -PjoinServer`，用 RCON 召唤 `{Size:3}` 与 `{Size:1}` 两只岩浆怪：
  客户端**零** `Missing texture` / `Unable to load`、零渲染异常、零 crash-report。

### 验证边界

- **没有做视觉验收**。自动化只证明到「模型解析成功 + 贴图存在且路径正确 + 渲染无异常」，
  证明不了「画面里那只魔理沙油库里朝向、比例都对」。这一条要真人进游戏看。
- 关闭 `ReplaceSlimeModel` 的退回路径没有单独跑过（代码是直接委托给内部的 `MagmaCubeRenderer`，风险很低）。
- 旧的两张 64×64 贴图（`textures/entity/yukkuri.png`、`textures/entity/marisa_yukkuri.png`）仍留在原地，
  目前没有任何代码引用它们。
