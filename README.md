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

- **行尾是 CRLF，且 `core.autocrlf=false`。** `git ls-files --eol` 显示 `i/crlf attr/-text` ——
  **git 不做事先转换**，文件里是什么字节就提交什么字节。于是「用哪种 API 写文件」直接决定 diff 大小：
  `insert_edit_into_file` 写 **LF**、`[System.IO.File]::WriteAllLines` 写 CRLF、
  `WriteAllText(…, -join "`n")` 写 LF。写错一次，`git diff` 就把整个文件算成「全改」，
  一行的改动变成上千行噪音（本章就踩了两次：`README.md` 与两份 `lang`）。

  正确姿势是**先读原始字节、只在目标位置插入、再原样写回**；或事后跑
  `port/fix-eol.ps1` 统一行尾。注意 `lang/en_us.json`、`lang/zh_cn.json` 的**原始行尾是 mixed**，
  整体转换同样会造出近两千行 diff —— 只有字节级插入才能保住「1 行」的 diff。

---

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
| 模组联动（JEI / REI / EMI / Jade / YSM / Iris…） | 未开始 |
| **睡觉的落点（碰撞箱 + 渲染）** | **完成**（`pinToBed` 两格中点 + 渲染侧补一次半身长） |

---

## 棋类（中国象棋 / 五子棋 / 国际象棋）

`BlockCChess` + `TileEntityCChess` + `BlockJoy` / `TileEntityJoy`（基类）+
`MaidGameRecordManager` 效果见下。

### 三层结构

1. **`BlockJoy` / `TileEntityJoy`**：娱乐方块的共同部分——
   空手右击 = 坐下（坐下的不是玩家本体，而是专门的哑实体 `EntitySit`）；
   方块实体只记一个 `SitId`。将来的键盘 / 书架 / 电脑都按这一层接。
2. **`BlockCChess`**：3×3 多方块，只有中心格带方块实体。空手右击选子 /
   落子，点棋盘侧面的标记区重开。
3. **`TileEntityCChess`**：整盘棋就是一个 FEN 字符串，加回合数与三个终局标记。

### 谁在算棋（为什么放在客户端）

玩家落子→服务端把 FEN 发给该玩家→客户端在**后台线程**跑
`Search#searchMain(1000)`（建议标准：纯 CPU 活，放服务端会卡整个世界的 tick）→
结果用 `CChessToServerPacket` 交回。代价是两个布尔值可被伪造，
服务端在 `BlockCChess#maidMove` 里把「女仆输」那一支重新验一遍（上游同款）。

### 三个 1.18.2 才有的坑

| 现象 | 原因 |
| --- | --- |
| `Component.translatable` / `literal` 编译不过 | 那是 1.19+ 的工厂方法；1.18.2 只有构造器 |
| 女仆胜场 sync 不到客户端 | `SynchedEntityData#set` 用 `ObjectUtils.notEqual(新, 旧)` 判脏；`CompoundTag` 比内容，原地改完塞回会被当成没变 |
| 座位实体必须注册空渲染器 | 未注册类型的 `getRenderer` 返回 `null`，渲染循环不处处判空 |

### 棋盘上的棋子为什么偏一格：不是贴图，是棋子的符号

现象：格线、河流、九宫都正常，但棋子整体像是「偏了一格」，右边一列明显压在
格线外面。用户当时补了一句「其实不是偏一格，而是其中一个被转了 180°」——
这句话是对的，错的是<b>棋子的坐标符号</b>，不是贴图。

棋盘的姿态（上游 1.18.2 与 1.20.1 两份渲染器在这一段逐字相同）是

```
mulPose(ZN(180));
mulPose(YN(facing.toYRot()));
if (facing == NORTH || facing == SOUTH) mulPose(YN(180));
```

而点击那边是 `click = Vec3#yRot(facing.toYRot()) * (hit - 中心)`，
即从世界转进点击坐标系。把两者复合起来：

| 朝向 | `2θ + 180°` | `click ∘ pose` |
| --- | --- | --- |
| SOUTH 0° | 180° | `R(180)` |
| WEST 90° | 180° | `R(180)` |
| NORTH 180° | 540° ≡ 180° | `R(180)` |
| EAST 270° | 540° ≡ 180° | `R(180)` |

四个朝向恒等。也就是说<b>棋盘是相对点击坐标系转了 180° 的东西</b>，
所以把格点坐标（`CChessUtil#getClickPosition` 的逆运算结果）直接当本地坐标用，
棋子会被送到中心对称的另一个交叉点上。

看起来像「偏一格」而不是「明显转了 180°」，是因为贴图里的格线在 uv 矩形里
并不居中（u 方向一头留 5 像素、另一头留 19 像素）：整片格点转 180° 之后，
一头正好错开大半格、另一头还压在格线上，于是只有<b>一侧</b>看得出问题。

修法：棋子坐标取负（中心偏移也一起取负，否则不对称的模型会被推开两倍偏移量），
姿态收进 `applyFacing`：

```java
poseStack.mulPose(Vector3f.YP.rotationDegrees(facing.toYRot()));
if (facing.getAxis() == Direction.Axis.Z) {
    poseStack.mulPose(Vector3f.YP.rotationDegrees(180.0F));
}
```

写成 `YP` 而不是照拄 `YN`：那句 `ZN(180)` 除了翻 Y，还把 x 取了负，
而 `GeoCube` 在 `createFromPojoCube` 里<b>已经对 x 取过一次负</b>
（`origin.x = -(x + sizeX) / 16`）。把 `M = Z·R(-θ)` 用 `Z` 共轭到基岩空间
就是 `Z·R(-θ)·Z = R(θ)`。

#### 上一版的「v 方向反了」是错药（已撤销）

`BedrockBlockModels#flipHorizontalFaces` 曾把水平面的 v 在各自 uv 矩形内镜像一次，
理由是「`GeoCube` 的 up 面把贴图上边接到 +z，而 simplebedrockmodel 接到 −z」。
这个理由是错的，而且<b>单轴镜像得到的必然是镜像字</b> —— 所以那一版改完字反而更难认。

两套库的 box-UV 其实完全一样：`GeoQuad` 的非 mirror 分支与
`BedrockCubeBox#compile` 都是「第一个顶点拿 (u2, v1)」，up 面取的是同一个 uv 矩形。
差别只在渲染姿态，也就是上面那一段。`flipHorizontalFaces` 已删除。

#### 怎么核对的（两个脚本，都不靠眼睛）

- `port/chess_piece_align_probe.py`：把两条管线（`GeoCube` + `GeoQuad` /
  `BedrockCubeBox#compile`）连同各自的姿态都算一遍，量「每个棋子中心到最近一条
  画出来的格线」的最大距离。四个朝向都是 **0.0219 格**，小于一个贴图像素
  （0.047 格），即纯粹的量化误差。
- `port/chess_uv_frame_probe.py`：把「点击坐标系里的同一点采到哪个 texel」
  在两套管线里逐点比较。四个朝向、格点两角、两条重开竖板中心，
  `(u, v)` 完全一致 —— 棋盘贴图的朝向与上游等价，不需要动任何 uv。

顺带记下上一轮为什么会得出「格线就在格点上、没偏」的错误结论：
`port/chess_grid_probe.py` 的 `to_world_x` 假设「u 随 x 增大」，
而 `GeoQuad` 的 up 面是 <b>u2 在 loX</b>（u 随 x 减小）。
方向假设反了，一格的偏移就被抵消成了 0.02 格的「误差」。

### 摆放：先查清模型的原点在哪

上游 1.18.2 画的是原版 `EntityModel`（顶点空间 **Y 向下**），本移植版改用基岩几何
（**Y 向上**），两者对 `translate(0.5, 1.5, 0.5)` 的含义完全不同。照抄上游数字的
后果是棋盘整体悬空 **1.372 格**（「高了一格又六个像素」，6 像素 = 0.375 格）。

但反过来的「一律抬 `-minY`」也是错的 —— 那是祭坛 / 神社的做法，它们的最低点
确实在方块底面以下。象棋的 `cchess.json` 最低点是 `-0.121875`，看着一样，
其实那是**两侧带字的小挡板**（`bone` / `bone2`）**故意垂到地下**的部分。
两次独立核对：

- 1.18.2 上游手写的 `CChessModel` 展开后，主棋盘盒是 `y 22..24 px`，
  配合 `translate(0.5, 1.5, 0.5)` + `ZN(180)` 得到世界坐标 **0..0.125**；
  两块挡板则是 `-0.128..0.122`。
- 本移植版直接量基岩几何：主棋盘盒 `0..0.125`，挡板 `-0.121875..0.128125`，
  逐位一致。

也就是说模型的原点已经对齐方块底面，**棋盘不需要任何抬升**。
抬 `-minY` 正好把整张桌子抬高 `0.121875` 格 ≈ **2 像素**。

| 位置 | 公式 | 实测（象棋） |
| --- | --- | --- |
| 棋盘基准 | `0`（模型原点即方块底面） | 0 |
| 棋盘表面 | `maxY` | 0.128125 |
| 棋子 | 居中 `-(minX+maxX)/2`、`-(minZ+maxZ)/2`，底面贴板面再抬 `-minY` | lift 0.000275 |

棋盘表面取 `maxY` 而不是主盒顶面的 0.125，是因为挡板比板面高 0.003 格：
让棋子高那么一丝看不出来（0.05 像素），还顺带避开了与板面完全共面的 z-fighting。

### 棋子上的字：那不是病态 cube，是汉字的贴片

`cchess_pieces.json` 里每个棋子都有一个

```json
{"origin": [-32, 31.9, -32], "size": [64, 0, 64], "inflate": -30.75}
```

`size.y` 为 0 意味着它是**一个平面**；`inflate` 为负且 `size` 为 0 时，
「`origin - inflate` 到 `origin + size + inflate`」这套公式的下界反而大于上界，
于是一张本该贴在棋子顶面的薄片被拉成 y 从 0.07 到 3.92 格的细长柱子
（棋子包围盒因此报 3.9156 格，而它真实高度只有 0.0753 格）。

**早先的判断「这是垃圾数据、渲染时看不见，直接丢掉」是错的。**
这 14 个平面就是棋子顶面的汉字：每个 cube 带一个独立 uv，
box-UV 的 up 面（`uv.x + size.z, uv.y`，大小 `size.x × size.z`）框住的
正好是贴图里的一整字。已把每个 uv 框按 `cchess_pieces.png` 逐格取图核对
（`port/chess_uv_probe.py`）：每个框里都是一个完整的字，ink 占比 18~38%。

所以 `GeoCube` 现在的做法是**把这条轴塌成一个平面**，取 + 侧边界
（`origin + inflate` = 1.15 px，正好落在棋子顶面），并丢掉与它完全重合的负向面
（否则同一张贴图会画两遍、后者覆盖前者，字变成镜像）。

只有「`size` 为 0 **且** `inflate` 为负」这一个组合才这样处理。
全库这种 cube 一共 28 个，全部在 `cchess_pieces.json`（该文件在
`models/entity` 与 `models/bedrock/block` 各有一份）。
其余 1390 个零厚度 cube（`inflate >= 0`，绝大多数在女仆模型上）区间本来就正常，
一个字节都不碰 —— 动它们等于给所有角色换脸。
修改前后祭坛 / 神社的包围盒逐位相同（`y[-0.125..5.928]` / `y[0.000..1.439]`）。

顺带修的另一个独立原因：棋子**没有底面对齐**时会沉进棋盘 0.2 格、只露出顶面 ——
看起来就是「一块扁平圆盘贴在木板上」。

### 上游那句 `ZN(180)` 不能照抄

上游棋盘渲染里有 `mulPose(ZN(180))`，那是为了把原版 `EntityModel`
（顶点空间 **Y 向下**）翻成 Y 向上。本移植版画的是基岩几何，它本来就是
Y 向上 —— 再翻一次就是「整张桌子底面朝上」。表现出来就是：

- 张面上的**格线消失**（从上方看到的是模型底面的 UV 区域，那里几乎是空白）；
- 棋子看到的也是底面，整片偏暗；
- 整体**偏暗**（看到的是背面）。

这与 {@code BedrockBlockModels} 类注释里已经记录过的同一课完全一致
（祭坛 / 神社都去掉了这句），结论是：<b>基岩方块模型一律不要 ZN(180)</b>。

另外棋子现在不再从某个角逐个步进（那要 1.365 / 1.370 / 0.304 三个魔数
同时都对上方向，任一个偏了整盘都会歪），而是每个交叉点直接算出它在方块内的
本地坐标，公式与 {@code CChessUtil#getClickPosition} 互为逆运算 —— 画出来的棋子与点得到的格子
不可能错位。

### 一个构造器异常会静默吃掉整只女仆

这个陷坑值得单独记一笔。一次 `defineSynchedData` 的 NPE 让女仆在加载时构造失败，
而区块加载器 `EntityStorage#loadEntities` 的异常表是

```
Exception table:
   from    to  target type
      23    63    66   Class java/lang/Exception
```

也就是说它 **catch 了 `Exception` 并丢弃**。于是：实体没装进世界 → 下一次保存时那个区块
就真的没有它了，而且射程中没有任何可见报错。当时的现象完全符合：
只有被打开过的那个存档、且只有已加载区块里的女仆消失，未加载区块里的两只还在。

**教训：不要在 `Entity` 子类的构造器链里引用实例字段。**
`Entity` 的构造器会调 `defineSynchedData()`（虚方法），那时子类字段全是 `null`；
一旦抛异常，丢的不是一次渲染而是**存档里的实体**。

### 五子棋与国际象棋：同一套骨架，三份不同的「格线」

两块新棋盘（`BlockGomoku` / `TileEntityGomoku`、`BlockWChess` / `TileEntityWChess`）
复用了象棋那三层结构，差异只在引擎与盘面几何：

| | 中国象棋 | 五子棋 | 国际象棋 |
|---|---|---|---|
| 对战数据 | FEN（`xqwlight.Position`） | `int[15][15]`（无序列化入口） | FEN（`api/game/chess.Position`） |
| 存档字段 | 1 个字符串 | 15 条 `IntArrayTag` | 1 个字符串 |
| 谁先手 | 玩家红方 | 玩家黑方 | 玩家白方 |
| 算棋位置 | 客户端（`Search#searchMain`，1 秒） | 客户端（`ZhiZhangAIService`） | 客户端（`Search#searchMain`，1 秒） |
| 和局判据 | 六十回合 / 长打 | 棋盘填满 | 五十回合 / 长打 |
| 桌子尺寸 | 3×3 格（模型 3×3） | **3×3 格（模型只有 2×2）** | 3×3 格（模型 3×3） |

三块棋盘共用 `client/renderer/tileentity/BoardRenderUtil`：`applyFacing` 与
「终局提示文字」都只有一份。上一轮象棋「右侧棋子不在格线上 / 字是倒的」
就是这段姿态里少了一句半圈 —— 各写一遍就一定会有一块写错。

#### 五子棋的棋子为什么不取负、也不跟着棋盘转

象棋那条「取负」不能照抄。差别只有一步：点击向量有没有先按朝向转过。

| | 中国象棋 | 五子棋 |
|---|---|---|
| 点击向量 | `hit.getLocation()...yRot(facing.toYRot() * DEG)`，**先转进点击坐标系** | 原始方块内坐标，**不转** |
| 姿态 | `YP(facing.toYRot())`，Z 轴朝向再补 `+180` | 同左 |
| 棋子坐标 | 取负 | **不取负，且不放进姿态** |

象棋转了点击向量，所以「姿态相对点击坐标系」恒等于半圈 → 棋子取负。
五子棋没转，所以姿态相对世界坐标就是 `applyFacing` 转的那个角度。

注意 `Direction#toYRot()` 的值：**SOUTH = 0、WEST = 90、NORTH = 180、EAST = 270**
（`2d data value * 90`，NORTH 是 2 不是 0）。所以姿态对四个朝向实际转的是
**NORTH 0 / SOUTH 180 / EAST 270 / WEST 90** 度 —— 一开始按 NORTH = 0 算，
结果恰好把两个 Z 轴朝向的结论写反了：代码是对的、文档是错的。

**只把负号删掉是不够的**：棋子仍在 `applyFacing` 里面，所以 SOUTH 会由对变错、
EAST/WEST 会把两个轴对调。`port/gomoku_align_probe.py` 把三种写法都算了一遍
（每种朝向 × 两种旋转手性，比较「棋子最终落在哪个格点」与「点击这个格子返回哪个下标」）：

| 写法 | NORTH | SOUTH | EAST | WEST |
|---|---|---|---|---|
| 放在姿态里 + 取负（上一版） | **对** | 14/15 错 | 14/15 错 | 14/15 错 |
| 放在姿态里 + 不取负 | 14/15 错 | **对** | 14/15 错 | 14/15 错 |
| **放在姿态外 + 不取负（现在）** | **对** | **对** | **对** | **对** |

上一版在南向棋盘上把 `k` 映成 `14-k` —— 报告里的「点一角、棋子落到另一角」
（截图那张正是 WEST 朝向）就是它。

#### 女仆坐错了棋盒：`getClockWise()` 与「盒在对角线」不相容

女仆永远执白（`GomokuToServerPacket` 把颜色强制成 `Point.WHITE`），玩家执黑，
所以她应该坐在**白棋盒**那一侧。上游写的是 `facing.getClockWise()`，但
`gomoku.json` 里两个棋盒在**对角线**上、关于方块中心对称：

```
whiteBox  x -1.375..-1.0625  z 0.5..0.8125     中心 (-1.21875,  0.65625)
blackBox  x  1.0625..1.375   z -0.8125..-0.5   中心 ( 1.21875, -0.65625)
```

「顺时针一格」和「白盒在哪一侧」之间没有必然关系，算下来
**四个朝向全都会坐到黑盒那一侧**（`port/gomoku_seat_probe.py`）：

| 朝向 | 姿态角度 | 白盒在世界坐标 | 旧座位 | 旧座位挨着 | 新座位 |
|---|---|---|---|---|---|
| NORTH | 0° | (-1.22, +0.66) | EAST | **黑** | WEST |
| SOUTH | 180° | (+1.22, -0.66) | WEST | **黑** | EAST |
| EAST | 270° | (-0.66, -1.22) | SOUTH | **黑** | NORTH |
| WEST | 90° | (+0.66, +1.22) | NORTH | **黑** | SOUTH |

修法不是打表：把白棋盒的中心按渲染器用的同一个旋转送一遍，再选它最靠近的水平方向
（`GomokuUtil#seatDirection`）。模型或姿态以后再改，座位会自己跟上。

实测（真实服务端 + RCON；`summon ... {MaidTask:"touhou_little_maid:board_games"}`，
读坐下后 `touhou_little_maid:sit` 实体的坐标。棋盘在 4,-60,4，中心 4.5 / 4.5）：

```
facing=north  sit Pos [3.0, -59.9, 4.5]   -> WEST    ok
facing=south  sit Pos [6.0, -59.9, 4.5]   -> EAST    ok
facing=east   sit Pos [4.5, -59.9, 3.0]   -> NORTH   ok
facing=west   sit Pos [4.5, -59.9, 6.0]   -> SOUTH   ok
```

#### 悬空 2 像素：同一个 `surface` 被减了两次

```java
poseStack.translate(0.5, surface, 0.5);   // 原点已经在桌面高度
float pieceLift = surface - pb[1];        // ← 又减了一次 surface
```

`surface`（= `main` 骨骼的 `maxY`，0.125625）在父级已经用过一次，
子级只需要把棋子底面 `pb[1]`（0.015625）抬到 0，也就是 `-pb[1]`。
写成 `surface - pb[1]` 等于抬了两遍，棋子底面落在 `0.125625 + 0.110 = 0.25125`，
比桌面高 0.1256 格 ≈ **2.0 像素**——正是「悬空一点」。

现在 `pieceLift = -pb[1] + 0.001`，日志里直接打出结果可以核对：

```
piece bbox y[0.015625..0.046875]  lift=-0.014625
surface=0.125625   stoneBottom=0.126625 (should equal surface)
```

多出的 0.001 是避免与格线层共面的 z-fighting（0.016 像素，看不见）。

#### 格线是量出来的，三块棋盘用的是同一条结论

`port/board_grid_probe.py` 干的事和象棋那支 `chess_grid_probe.py` 一样：
把棋盘 up 面的 box-UV 矩形解出来，扫竖线与横线的位置，换算成方块坐标，
再和**点击判定**用的格点对照。两块新盘的结果：

- **国际象棋**：`wchess.json` 的板子 `x/z ±1.5`（3 格）对应贴图 `u 48..96`；
  8×8 的格子区从 `u 56` 开始、到 `u 88` 结束，正好换算成世界 `-1.0..1.0`。
  这与 `WChessUtil#getClickPosition` 的 `floor((x + 1) / 0.25)` 完全吻合
  （第 0 格从 -1 起、中心 -0.875，间距 0.25），所以**不需要任何手调偏移**。
- **五子棋**：格线不是贴图，而是单独一层 cube（`uv [605,0]`，151×151 texel 铺满 2 格）。
  按**不透明像素**数一遍，15 条线落在 rect 内第 5、15、25 … 145 个 texel 上 ——
  间距 10 texel、两端各留 5 texel、关于中心完全对称。折算就是
  `间距 = 2/151×10 = 0.132450`、`第 0 条 = -1 + 2/151×5.5 = -0.927152`，
  也就是 `util/GomokuUtil` 里那两个常数。

**为什么不再沿用上游那九个偏移三元组**：上游 `BlockGomoku#getChessPos` 给九宫格
每一格各写了一套 `(xOffset, xStartOffset, xIndexOffset)`，彼此并不自洽 ——
同为第 4 号交叉点，从 `CENTER` 算出来是 0.115、从 `LEFT_CENTER` 算出来是 0.1014，
差 0.0136 格；累到盘面另一头差 **0.042 格 ≈ 0.65 像素**，而一颗棋子裸宽只有 1.5 像素。
更糟的是判定框只有 0.07 宽、还从格线往一侧偏，**近一半面积是点不到的死区**。
现在点击与渲染共用 `GomokuUtil` 的同一组常数，把点击点四舍五入到最近的交叉点即可。

#### 竖直方向：`main` 与「整张桌子」不是一回事

| 棋盘 | 竖直基准 | 表面（棋子贴在这个高度） | 实测 |
|---|---|---|---|
| 国际象棋 | 模型原点即底面，偏移 0 | `wchess.json` 的 `maxY` | 0.125 |
| 五子棋 | 偏移 0（桌腿埋在地下 −1.89） | **只量 `main` 骨骼**的 `maxY` | 0.125625 |

五子棋这一条是坑：整张桌子的包围盒 `maxY` 是 **0.292**，因为两个棋盒（`blackBox` /
`whiteBox` 那 8+8 个 cube）比桌面高出一截。拿它当表面，所有棋子会浮在半空 ——
`measureBone(model, "main")` 量到的才是桌面 + 那层格线。

棋子本身（`gomoku_piece.json` / `wchess_pieces.json`）同样靠 `measureBone` 量：
五子棋底面 `0.015625`、国际象棋底面 `0`，抬到表面高度即可；国际象棋额外乘上游
`WChessPiecesModel` 里的 `scale(0.9)`（棋子裸宽 0.2375 放进 0.25 的格子会顶满）。

#### 两个防作弊上的差异

- **五子棋回包是一个裸坐标**，上游直接 `setChessData(x, y, type)`。改过的客户端能把
  坐标写成 999（服务端 `ArrayIndexOutOfBounds`，方块实体的存档也被写脏）、能往已有子的
  点再放一颗、能用 `BLACK` 冒充玩家。`GomokuToServerPacket` 因此逐条验：坐标在
  15×15 内、该点为空、颜色**强制**为白（女仆永远执白是规则，不是请求）。
- **`ZhiZhangAIService` 是有状态的**（局面 / 最佳点 / 回合数全在实例字段里）。
  上游把四档棋力写成四个 `static final` 单例，两个客户端同时开局会互相覆盖。
  `MaidGomokuAI#getService` 因此每次新建实例 —— 搜索本身是纯 CPU 的，代价可忽略。

### 残局道具（三件）

`gomoku_board_state` / `cchess_board_state` / `wchess_board_state`，共用
`item/ItemBoardState` 与一块 NBT：

```
BoardStateData    棋局本身（五子棋是 GomokuCodec 的一行，两种象棋是 FEN）
BoardStateDesc    描述的语言键（不是明文）
BoardStateAuthor  署名
```

与上游一致：**不放进任何创造模式物品栏**，只能从要塞图书馆与
制图师小屋的箱子里拿到（上游那两条全局战利品修饰器）。

拿着它右击对应的棋盘就把棋局换成它记录的那一局（上游同款，且这一步在
「只能空手下棋」之前）；按住 Shift 看详情时，它还会在 tooltip 里把棋局画出来。

#### 数据包：`data/<namespace>/board_states/*.json`

```json
[
  {
    "tags": ["library"],
    "display": { "description": "board_state...moon", "author": "欢乐小游戏" },
    "data": "eNpjYKAvYMTPZ8SreAgAdP8w0twTAAhFAAk, 8, 8_9",
    "weight": 1
  }
]
```

逐 pack 读取再合并，所以资源包可以只写自己的几条。模组自带 5+5+5 条。

#### 三个真实踩过的坑

1. **Gson 2.8.9 写不进 `record`。** 上游 1.20.1 的 `BoardStateRecord` 是 record，照抄过来
   之后服务端直接起不来：

   ```
   AssertionError (GSON 2.8.9): java.lang.IllegalAccessException:
       Can not set final java.util.List field BoardStateRecord.tags to java.util.ArrayList
   ```

   1.18.2 自带 Gson 2.8.9，靠反射设字段填对象，record 的字段是 `final`；
   Gson 支持 record 是 2.10 才有的。改成普通类（方法名不变）。
2. **那个异常是 `Error` 不是 `Exception`。** 重载监听器跑在 ForkJoinPool 工作线程上，
   `catch (Exception)` 接不住 `AssertionError`，于是一个写错的数据包把**整个服务端启动**
   带走了。现在改成 `catch (Throwable)` 并记日志。
3. **战利品注入比数据监听器先跑。** Fabric 的 `LootTableEvents.MODIFY` 回调里
   读到的三张表还是空的（`gomoku=0 xiangqi=0 chess=0`），于是池子根本没加进去 ——
   现象就是「箱子里永远掉不出残局道具」。所以回调里发现表为空就
   自己调一次 `BoardStateDataReload.load(resourceManager)`，彻底摆脱顺序。

#### 为什么没用上游那个自定义战利品函数

上游写了 `board_state_randomly` 战利品函数，它要注册一个
`LootItemFunctionType`；Fabric 没有「注册函数类型」的事件，只能再开一个 Mixin 往
`LootItemFunctions` 的私有表里塞东西。这里换成**直接用原版的 `SetNbtFunction`**：
把每条数据编译成一个带固定 NBT 的条目，权重就是数据自己的 `weight`，
抽签交给原版的战利品池。为了保持上游「先等概率挑物品、再按权重挑残局」
的分布，每件物品的条目权重都被归一化到同一个总和
（`NORMALIZER`），否则「条目多的棋」会被抽得更勤。

#### 手册

`entries/other/` 下现在有三页（`cchess` / `gomoku` / `wchess`），各自带
一张图与对应的祭坛配方页。配方补了 `craft_gomoku` 与 `craft_wchess`
（原本只有 `craft_cchess`）。所以三张棋盘都能在祭坛上做出来了。

#### 创造模式的木棍：导出与排残局

上游把它只放在五子棋上（`BlockGomoku#onCreativePlayerClick`），
本移植版同样只给五子棋 —— 两张象棋棋盘没有这个分支。
理由也很实在：象棋的局面是一个 FEN，手写就行；
五子棋的格式是 deflate + base64，人不可能手搓，才需要一个导出器。

两个用法（都需要创造模式，且木棍拿在主手）：

| 点哪里 | 发生什么 |
|---|---|
| 棋盒（对角线上那两堆棋子） | 把当前棋局编码成一行字符串发到聊天栏，**点一下就复制** |
| 交叉点 | 落一颗「轮到谁的颜色」，然后把回合翻给对面 |

导出的那行字符串就是 `board_states/*.json` 里 `data` 字段的内容，
直接贴进数据包就能变成一条残局。

**排残局是「直接编辑」，不检查落子点是否为空** —— 落在已有棋子上是覆盖。
这是上游行为：排的时候难免要改一颗子的颜色，反复点同一个点
就能在黑 / 白之间来回切。代价是 `ChessCounter` 会被超累加（它只是「回合数」的展示）。

1.18.2 差异（两处，都在注释里记了）：
`ComponentUtils#copyOnClickText` 是 1.19+ 的工厂方法，这里用
`Style#withClickEvent` + `withHoverEvent` + `withInsertion` 拼出来；
`Player#sendSystemMessage` 同样是 1.20+，1.18.2 的等价写法是
`displayClientMessage(x, false)`（actionBar = false 就是 SYSTEM 聊天栏）。
`COPY_TO_CLIPBOARD` 在 1.18.2 允许服务端下发（`ClickEvent$Action` 枚举初始化里是
`iconst_1`），否则客户端会把点击事件剔掉。

### 已知限制

- **木棍工具只有五子棋有**（上游同款），且它只产出
  `board_states/*.json` 里的 `data` 字符串 —— 不会直接变成一件残局道具。
  想要得到物品，要把它写进数据包（然后靠战利品或 `/loot` 拿）。
- **三块棋盘的页面都只有一页**：上游的 `gomoku` 页用四个
  `altar_recipe` 把三张棋盘的配方堆在一起，这里拆成了三页。
- **中心格离开视锥时整张棋盘会消失**：1.18.2 的方块实体没有
  `getRenderBoundingBox`，与雕像渲染盒是同一个已知限制（要修得 Mixin `LevelRenderer`）。

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
| `models/bedrock/block/{bookshelf,computer,keyboard,picnic_mat,cchess,gomoku,wchess}.json` | 已在用（键盘 / 书架 / 电脑 / 野餐垫 / 三张棋盘都走基岩管线）。`snack_cabinet`、`statue_base` 是仍未移植的方块 |
| `sounds.json` / `lang/*.json` | 1.20.1 版会覆盖掉本移植自己加的条目，必须逐键合并而不是整体覆盖 |
| `textures/entity/**` | 女仆模型走的是模型包（`maid_model.json` + 资源包），不属于方块贴图体系 |

`camera_2d.png`（→ `camera.png`，改成 16×160 的动画贴图）与
`block/scarecrow.png`（→ 拆成 `scarecrow_lower` / `scarecrow_upper`）已随上游删除。

---

### 物品图标消失：`forge:separate_transforms` 在 1.18.2 是空模型

1.20.1 的 6 个物品模型（`camera` / `extinguisher` / `hakurei_gohei` / `sanae_gohei` /
`maid_beacon` / `snack_cabinet`）用的是 **Forge 专属的 `forge:separate_transforms` 加载器**：

```json
{
  "loader": "forge:separate_transforms",
  "base":            { "parent": "..._in_hand" },   // 手持 3D 模型
  "perspectives": { "gui": { "parent": "item/generated", "textures": { "layer0": "..." } } }
}
```

原版 `ModelBakery` **不认识 `loader` 字段**，而顶层又没有 `parent` / `textures` / `elements`，
于是把它烘焙成一个**空模型**。表现就是「拿在手上是好的、物品栏里的图标没了」——
手持模型来自本移植自己的 `InHandModelUnbaked`，而那张平面图标正好来自这个空模型。

修法是把这 6 个文件翻译成等价的原版写法（就是它们 `perspectives.gui` 那一支）：

```json
{ "parent": "item/generated", "textures": { "layer0": "touhou_little_maid:item/<同名>" } }
```

上游文件已存档在 `port/upstream-forge-item-models/`，转换脚本 `port/convert-forge-item-models.ps1`
（会断言「没有残留 loader」且「每个 layer0 都指向真实 png」）。

顺带：`item/sanae_gohei.json` 原来有一句 `"parent": "hakurei_gohei"`（缺 namespace），
解析成 `minecraft:hakurei_gohei` 后是不存在的模型，是启动日志里那条
`Unable to load model: 'minecraft:hakurei_gohei'` 的来源。随本次转换一并消失。

### 基岩方块模型的朝向：`ZN(180)` 必须去掉，竖直偏移必须量出来

换模型之前这两个渲染器画的是原版 `EntityModel`，顶点空间 **Y 向下**，所以有一句
`mulPose(ZN.rotationDegrees(180))` 把它翻上来；另外还有手调的 `+1.5`（神社）/
`-1.5`（祭坛）去凑各自模型的坐标原点。

基岩几何（`GeoCube`）本身就是 **Y 向上**的 —— 女仆就是这条路画出来的 —— 于是那句
`ZN(180)` 反而把方块翻了个底朝天，手调偏移也不再对应新模型的原点。
祭坛更糟：它的骨骼里有 `rotation: [90, 87.5, 90]` 这种大角度旋转，
**直接读 JSON 的 `origin` 得到的包围盒完全是错的**（原始值说 y 从 −1.42 到 5.88，
真实值是从 −0.125 到 5.93，X 也从「不对称」变成对称的 ±4.278）。
照原始值猜偏移，祭坛就会整个沉进地里。

所以改成 **量**：`GeoBlockRenderer#measureBounds` 用一个只记录坐标的 `VertexConsumer`
走一遍真实渲染路径（复用 `renderRecursively`，同一套骨骼矩阵），得到模型在方块空间里的
真实包围盒；`BedrockBlockModels#floorOffset` 返回 `-minY`，把模型最低点摆到方块底面。

启动日志会打出来，两个模型的实际测量值：

```
[Futo] Block model altar  loaded: 87 bones, bbox x[-4.278..4.278] y[-0.125..5.928] z[-4.889..4.278] offset=0.000 (model minY=-0.125)
[Futo] Block model shrine loaded:  1 bones, bbox x[-0.685..0.686] y[0.000..1.439] z[-0.501..0.501] floorOffset=-0.000
```

神社的 minY 实测正好 0.000 —— 两种规则都得 0；祭坛的 minY 是 −0.125，但按「只压不抬」的规则偏移也是 0。
「竖直偏移必须从模型量出来」这条对后面要接的 9 个基岩方块模型（书架 / 电脑 / 键盘 /
野餐垫 / 零食柜 / 雕像底座 / 象棋 / 五子棋 / 国际象棋）同样适用。

---

### 祭坛为什么还要再降 3 格

修好朝向后祭坛仍然整体高 3 格，原因在多方块的锚点，不在模型：

```java
// MultiBlockAltar
private static final BlockPos SOUTH_POS = new BlockPos(-4, -3, 0);   // 四个方向的 Y 都是 -3
BlockPos currentCenterPos = posStart.subtract(getCenterPos(direction));
```

`posStart` 是结构的**最底层**（所以贡品原木挂在 `blockInfo.pos().getY() == 2`），
而负责渲染的那个方块实体被放在 `posStart.subtract(getCenterPos(...))`，
也就是比最底层**高 3 格**的地方。基岩模型的原点正好在结构底面，
直接画在锚点上自然整体高 3 格 —— 所以 `setTranslateAndPose` 的 Y 从 `0` 改成 `-3`。

这个 −3 不是凑出来的数：它就是 `getCenterPos` 返回值的 Y 分量取反。
水平分量（x/z）不动 —— 实测是对的，它们只决定绕哪个点旋转、朝哪一面。

> 顺带一提，上一步把 `-1.5` 改成 `0` 时其实已经少了这 3 格补偿，
> 但因为同时去掉了 `ZN(180)`，两个错误互相抵消了一部分，看起来只是「高 3 格」。
> 现在的组合是：**无 Z 翻转** + **Y = −3**（锚点高度）+ **`floorOffset` = 0**（y = 0 就是方块底面，见下一节），
> 三者各自有独立依据，不再互相打补丁。

---

### 微小悬空：`floorOffset` 只能往下压，不能往上抬

降 3 格之后祭坛还差一点点才贴地，量了一下才明白问题出在「把包围盒下限对齐地面」这个规则本身。

数一下祭坛模型 220 个立方体的竖直分布：

- **17 个未旋转立方体底部正好在 y = 0** —— 包括全部 6 根 `pillar*` 柱子、`bone56` 的 10 个、
  `bone62` 的 8 个。这些就是落地的部分，而 6 根柱子在 0 / 1 / 2 三层各一个 cube，
  正好对应结构模板里被替换成隐形祭坛方块的那三层原木。
- **42 个立方体在 0 以下**，最低到 −0.125，都是带旋转的装饰件。

也就是说 **y = 0 就是方块底面**，那 42 个是故意往地里埋的。按 `-minY` 抬升 0.125 格
（2 像素）反而把整台抬离地面 —— 正好是「看起来悬空但说不清差多少」的量。

所以规则改成 **`-max(0, minY)`：只往下压，绝不往上抬**。
模型若整体浮在 0 以上（原点比几何低）才需要压下去；低于 0 的一律视为故意埋入。

| 模型 | 实测 `minY` | 旧规则 `-minY` | 新规则 `-max(0,minY)` |
|---|---|---|---|
| 祭坛 | −0.125 | **0.125（悬空 2px）** | **0** |
| 神社 | 0.000 | 0 | 0 |

启动日志把两者分开打印，避免再混淆「模型最低点」和「实际应用的偏移」：

```
[Futo] Block model altar  loaded: 87 bones, bbox … y[-0.125..5.928] … offset=0.000 (model minY=-0.125)
```

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

- 资源清单：`assets` 侧 58 个 JSON（54 条目 + 3 分类 + 1 模板），`data` 侧 `book.json`（version 5）；
  `book.json` 上游 jar 里其实**有**打包，早先「上游没有」的结论来自只解包 `assets/` 的提取脚本。
  条目 = 1.18.2 Forge 的 52 条全集 + 本模组特有的 `other/cchess`、`other/wchess`；
  `overview/world_spawn` 比 1.20.1 少一页 `replace_allay.png` —— 1.18.2 没有 allay，那一页讲不通。
- 侧边页签：`MaidSideTabButton` + `AbstractMaidScreen.addSideTabButtons()`，位置
  `leftPos+251 / topPos+37`，图标 UV `(193,111)`。框架在 `maid_gui_side.png` 里其实是
  **两个独立的 21×25 圆角格**（列 107–130 与 132–155，中间第 131 列是空的），所以只画 21×25。
  点击触发 `OpenPatchouliBookEvent`，未装 Patchouli 时聊天栏给下载提示（与上游一致）。
- **一个坏的 spotlight 页会让整本书作废。** `BookContentsBuilder#load → #build →
  BookEntry#build → page#build`，spotlight 页会把 `item` / `items` 解析成 `Ingredient`，
  一个不存在的物品 ID 直接抛 `JsonParseException` → `BookContents.empty` → 「Loading error!」。
  而 entry 的 `icon` **不是**致命的（`BookIcon#from` 只 warn 并回退成空 `StackIcon`）。
  所以产物未移植的页要用 `port/book_pages_held_back/` 暂存，不能用「图标占位」蒙过去。
- 当前 **54 条目 / 3 分类**，`port/book_pages_held_back/validate-book.ps1` 校验 0 问题。
  它按帕秋莉的规则静态检查 `spotlight` 物品、词条 / 分类图标、`entity` 页，
  另外还检查祭坛配方的 `recipe_id` 是否存在、`c:` 标签是否真的定义了、
  以及 ingredients / output 里引用的物品与实体 id 是否已注册。
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
| `HOME_MEAL` | `MaidHomeMealTask` | `tlm_ride_idle`，priority 5 | 坐在野餐垫上（`EntitySit` 的 `OnHomeMeal`）+ 家庭餐冷却走完 |

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

---

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

---

## 坐垫（自定义坐垫）

上游的「坐垫」是一整套「可摆放、可回收、可骑」的装饰家具：**一个物品承载所有模型**，
右键地面生成实体，蹲下左键拆除后又变回同样数据的物品。本轮整套搬过来了。

### 数据流

坐垫有四个属性，全部写在物品 NBT 里（`ItemChair.Data`）：
`ModelId` / `MountedHeight` / `TameableCanRide` / `IsNoGravity`。
生成实体时抄进 `SynchedEntityData`，拆除时再抄回物品 —— 所以拆了重放不会丢模型。

`MountedHeight` 在 JSON 里是**像素**，`ChairModelInfo#decorate` 转成格：
`(mounted_height - 3) * 0.0625`。那个 `- 3` 是上游的基准偏移，不是笔误，
`cushion` 写 `mounted_height: 3` 落地就是 0。

### 模型包

`assets/<ns>/maid_chair.json`，结构与女仆的 `maid_model.json` 完全同构。
内置三个包，顺序照上游 `DefaultPackConstant.CHAIR_SORT`：
`touhou_little_maid` → `geckolib` → `next_update_model`，实测 **58 个模型**。

几何与动画直接走女仆那条管线（`GeckoModelLoader#buildGeoModel(ResourceLocation)`），
所以旧版 `1.10.0` 与 Gecko 两种格式都能读。**但绝不合入 `maid.animation.json`** ——
两套骨架毫无关系，合进去只会让坐垫按女仆的骨骼名去找根本不存在的骨。
坐垫只用自己的 `chair.animation.json` 兜底。

坐垫的动画约定是「八条并行轨道」：`parallel0` ~ `parallel7` 各一条控制器同时播
（`GeckoChairEntity` 注册 8 条）。这样模型作者可以把「轮子转」「猫尾摆」拆到不同轨道，
不必在同一时间轴上对拍。

### 渲染

实体侧只有**一个**渲染器：`EntityChairRenderer` 直接继承 `GeoReplacedEntityRenderer`。
上游分了 `EntityChairRenderer` + `GeckoEntityChairRenderer` 两层，是因为它要同时支持
「旧版基岩模型 + JS 动画」和「Gecko 模型」两条路径；本移植没有 JS 引擎、旧版坐垫模型
在加载时也已统一转成 `GeoModel`，两条路在 `GeoReplacedEntityRenderer` 里本来就合一了，
再拆一层只是空壳。

物品图标侧是 `client/renderer/item/ChairItemRenderer`，用 Fabric 的
`BuiltinItemRendererRegistry` 把**一个真实体**画进物品槽。上游用的是 Forge 的
`IItemRenderProperties#getItemStackRenderer()`，Fabric 1.18.2 没有对应机制。

> **`models/item/chair.json` 必须保留 `"parent": "builtin/entity"`。**
> 少了它，原版烘焙器会生成一个空模型，并且**不会**调用 `BuiltinItemRendererRegistry`
> 注册的渲染器 —— 表现就是物品栏里彻底空白（不是「白方块」，是空槽）。

坐标与缩放逐字照抄上游：先按 `render_item_scale` 整体缩放，再把实体摆在
`(1/scale - 0.125, 0.25, 0.75)`。那三个数字是在模型空间里手调出来的居中值，换掉就偏。

### 三处 API 差异（`AbstractEntityFromItem` 里逐条写明）

| 上游写法 | 1.18.2 实际 | 处理 |
|---|---|---|
| `!EffectiveSide.get().isServer()` | Fabric 没有 `EffectiveSide` | 用 `level.isClientSide()` |
| `attackable()` 返回 `false` | 原版叫 `isAttackable()`；上游那个**没有 `@Override`**，从来没生效过 | **不移植** —— 照抄会让玩家左键打不到坐垫，「蹲下左键拆除」直接失效 |
| `getPickedResult(HitResult)` | 1.18.2 是 `getPickResult()`，**无参**（`HitResult` 参数是 1.19 才加的） | 按无参签名重写 |

第二条值得单独记一笔：**上游那个方法是死代码**。它在 1.18.2 的映射里找不到同名父类方法，
所以只是个普通方法，既不参与覆盖也不会被调用。这类「看起来是覆盖、其实没生效」的写法
在搬运时最容易原样搬成 bug —— 判断依据只能是去 vanilla 里核对方法签名。

### 与女仆的接线

- `MaidFindSitTask` 原来只认 `Boat`，现在 `EntityChair` 也认。这里保留着原有的
  **上游 bug 修复**：上游拿 `NEAREST_VISIBLE_LIVING_ENTITIES` 去筛，而 `EntityChair` /
  `Boat` 都不是 `LivingEntity`，那个记忆里永远不可能出现它们，于是「女仆对椅子毫无反应」。
- `EntityChair#pushEntities` 会让走近的 `TamableAnimal` 自动坐下（只有 `tameable_can_ride`
  为真的坐垫才有这个行为，课桌 / 麻将桌之类没有）。
- 创造模式单开 `chair` 页签（`InitItems.CHAIR_TAB`），由一个物品铺出全部模型变体，
  与上游 `MaidGroup.CHAIR_TAB` 一致。
- `chair_show`（碰撞箱显示器）：蹲下手持时所有坐垫改画红色线框，用来对齐多方块家具。
  这个分流写在 `EntityChairRenderer#render` 最前面，位置与上游一致；物品图标渲染期间
  会临时把 `renderHitBox` 关掉，否则图标里会多一个红框。

### 换肤界面（`ChairModelGui`）
蹲下右键坐垫后，服务端发 S2C 包 `touhou_little_maid:open_chair_gui`（载荷是实体 id），
客户端据此打开 `ChairModelGui`。网格里点一格则发 C2S 包 `touhou_little_maid:chair_model`
（实体 id + model_id + 坐高 + 可骑 + 无重力），服务端写进四个同步字段。
**为什么不用菜单（`MenuType`）**：这个界面没有物品槽，只有一张模型网格。上游同样是纯
`Screen` 加自定义包（`OpenChairGuiMessage` / `ChairModelMessage`），比容器界面少一整层。
**为什么「开界面」由服务端发起**：谁真的蹲着右键了这个坐垫，只有服务端说了算；
客户端自己开界面等于允许伪造。所以 `EntityChair#interact` 里那句发包带着 `!level.isClientSide`。
**`AbstractModelGui` 泛型化**：上游本来就是
`AbstractModelGui<T extends LivingEntity, E extends IModelInfo>`。本移植版最初把 `E`
钉死成 `MaidModelInfo`，于是坐垫只能靠创造模式物品栏换模型。现在分成四层：
| 类型 | 角色 |
| --- | --- |
| `IModelInfo` | 四条公共查询：`getModelId` / `getName` / `getDescription` / `getRenderItemScale` |
| `CustomModelPack<E>` | 模型包元数据（页签名 / 描述 / 作者 / 版本 / 图标），两边共用 |
| `SkinGuiNumber<E>` | 分页与网格换算（每页 7 个页签 / 每行 11 格 / 一屏 5 行） |
| `AbstractModelGui<T, E>` | 布局与交互；子类只实现「一格画什么」「点了做什么」 |
坐垫那边因此不再需要自己的包类型：`ChairModelRegistry.ChairPack` 已删除，
`allPacks()` 直接返回 `List<CustomModelPack<ChairModelInfo>>`，字段名与 `maid_model.json` 一致。
**服务端复核（逐条对齐上游字节码）**：`allowed = ChairConfig.CHAIR_CHANGE_MODEL || player.isCreative()`；
不允许时只回一句 `message.touhou_little_maid.change_model.disabled`，其它什么都不做；
允许时写入四项，`tameableCanRide` 变 `false` 且坐垫上有人则 `ejectPassengers()`，
最后打一次进度判据 `change_chair_model`（「我刚买的家具啊」）。
**界面渲染期间临时关掉碰撞箱红框**：打开这个界面必须蹲着，而「蹲下 + 手持 `chair_show`」
正是红框模式的触发条件 —— 不关的话大立绘与网格里 55 个缩略图会全变成红色线框。
与 `ChairItemRenderer` 的处理同源，用 `finally` 恢复。
**静态校验的范围**：三个包各有一条 `pack_name`、图标文件都在，58 个 `model_id` 互不重复，
58 份几何与 58 张贴图全部存在。但界面本身（翻页、点格子、缩略图是否居中）需要真人看一眼。

### 必须注册属性表（Forge 有兜底，Fabric 没有）

`EntityChair` 继承 `LivingEntity`，而 `LivingEntity` 的构造函数里第一件事就是
`setHealth(getMaxHealth())` —— `getMaxHealth()` 读 `Attributes.MAX_HEALTH`，
`AttributeMap` 找不到实例时会回落到构造时传进来的 `AttributeSupplier`。
**Fabric 那边这个 supplier 来源于原版 `DefaultAttributes` 那张表**，
没注册就是 `null`：

```
java.lang.NullPointerException: Cannot invoke "AttributeSupplier.getValue(Attribute)"
    because "this.supplier" is null
  at AttributeMap.getValue
  at LivingEntity.getMaxHealth
  at LivingEntity.<init>
  at AbstractEntityFromItem.<init>
  at EntityChair.<init>
  at ChairItemRenderer.getChair
```

所以必须 `FabricDefaultAttributeRegistry.register(CHAIR, EntityChair.createAttributes())`。
机制可以用 `javap` 核对：`DefaultAttributeRegistryMixin` 的目标类是 `DefaultAttributes`
（`class_5135`，注解是 `@Shadow @Final @Mutable Map field_23730`，注入点是静态初始化的 `TAIL`），
配合 `DefaultAttributeRegistryAccessor` —— 也就是说 `register` 是**直接写进原版那张表**，
再被 `LivingEntity` 构造函数里原有的 `DefaultAttributes.getSupplier(type)` 取出来，
不需要任何 `LivingEntity` 侧 mixin。

**上游不需要写这一步**，所以搬代码时极易漏：Forge 给未注册的实体兜了默认属性表，
Fabric 没有。凡直接或间接继承 `LivingEntity` 的类型都要注册，漏掉的表现是
**构造实体的那一刻就崩** —— 坐垫这次是「一切到坐垫页签就崩」，因为页签图标会把坐垫物品
渲染一次、从而构造一个假实体。

顺带一条同源的防御：物品图标渲染器抛异常**没有任何一层会接住**，会直接崩游戏。
`ChairItemRenderer` 因此接一次 `Throwable`、打一条带堆栈的 ERROR、然后把 3D 图标整个关掉 ——
图标画不出来顶多是「物品栏里少个模型」，不该升级成崩溃；那条日志保证问题不会被静默吞掉。

### 坐在坐垫上比原版高 0.3 格：`ridingPosture` 的位移只接了一半

现象：所有坐垫，女仆坐上去的落点都比上游高一点（约 0.3 格）。

根因在 `MaidBaseAnimation#ridingPosture()` 的末尾：

```java
private static void ridingPosture(ModelRendererWrapper legLeft, ModelRendererWrapper legRight) {
    legLeft .setRotateAngleX(-1.134f); legLeft .setRotateAngleZ(-0.262f);
    legRight.setRotateAngleX(-1.134f); legRight.setRotateAngleZ( 0.262f);
    GlWrapper.translate(0, 0.3, 0);          // ← 关键
}
```

它的调用条件是 **`mob.isPassenger()`** —— 也就是坐垫、船、扫帚、被玩家抱着**全都算**。
（`sittingPosture()` 内部也是先调它，所以ground sit 也带这 0.3。）

本移植版把这句接在了 `MaidRenderer#setupRotations` 里，但**只接在「被玩家抱着」那条分支**：

```java
if (!maid.isCarried()) {
    return;                       // ← 坐垫 / 船 在这里就返回了，0.3 从来没生效
}
```

于是「抱着」是对的，「坐坐垫」少了 0.3 格。`InnerAnimations` 里那句
`// GlWrapper.translate(0, 0.3, 0) 由 MaidRenderer#setupRotations 负责` 正是这个约定的记录，
问题只是渲染器那一半没覆盖全部乘客。

修法：把位移提到 `isCarried()` 判断之前，对**任何** `isPassenger()` 的旧版模型生效。
非「被抱着」的分支没有 Z 轴旋转（`super` 只做了 `Ry(180 - yaw)`），而 Y 方向平移与绕 Y 的旋转
可交换，所以直接 `translate(0, -0.3, 0)` 即可 —— 符号取负是因为 geckolib 的
`RenderUtils` 是 `translate(-x/16, +y/16, +z/16)`，**模型空间没有 Y 翻转**，负 Y 就是向下。

> 别把这条位移搬进 `maid_legacy.animation.json` 的 `Root.position`：那样「被抱着」分支
> 会重复应用一次（它同时是乘客），抱持姿态会立刻沉 0.6 格。位移必须只由渲染器负责一次。

**残留的极小偏差**：地面坐下（`sit` 状态）在 `maid_legacy.animation.json` 里写的是
`Root.position [0, -3.5, 0]`（0.219 格），而上游是 0.3 格，差 0.081 格（约 1.3 像素）。
这一处没有动 —— 它已被视觉验证过，而改动的收益低于打乱已验证几何的风险。
## 隙间（无线 I/O 饰品）

「隙间」是唯一一件**既当饰品又当工具**的物品：拿在手上能绑箱子、开配置界面；
放进女仆的饰品栏，她就每 5 秒与那个箱子之间搬一次东西。上游如此，本移植版照搬。

### 数据布局（与上游逐字一致，存档可互换）

| NBT 键 | 类型 | 含义 |
|---|---|---|
| `ItemIOMode` | boolean | `true` = 女仆 → 箱子；缺省 = 箱子 → 女仆 |
| `ItemFilterMode` | boolean | `true` = 黑名单；缺省 = 白名单 |
| `ItemFilterList` | Compound | 9 格过滤清单（`ItemStackHandler` 的序列化形式） |
| `BindingPos` | Compound | 绑定的箱子坐标 |
| `SlotConfigData` | byte[] | 38 个「冻结」标记，见下 |

### 38 个开关里为什么最后两个是「手」

`SlotConfigData` 前 36 项按背包槽位号索引，**第 36、37 项代表她的两只手**。
而女仆的「可用物品栏」是 `背包槽 + 双手` 的**拼接**，所以双手在其中的下标是
`getSlots() - 2` 与 `getSlots() - 1`。搬运前要把配置表尾部那两项搬到拼接后的尾部：

```java
tmp[maidSlots - 2] = tmp[38 - 2];
tmp[maidSlots - 1] = tmp[38 - 1];
```

大背包 36 格 + 2 手正好 38，看起来可以省略；小背包只有 6 格 + 2 手 = 8，搬一次才对齐。
这也是「换背包之后双手的开关还能记住」的实现方式 —— 记的是「手」，不是「第 37 格」。

> 上游这里直接 `slotConfigTmp[SLOT_NUM - 2]` 取值。如果存档里写了短于 38 的数组
> （手改 NBT、或从更早的版本带过来），就会在女仆 tick 里抛 `ArrayIndexOutOfBoundsException`，
> 服务端每 5 秒刷一次堆栈。本移植版先补齐到 38 位再搬。

### 槽位配置永远是「女仆侧」的规则

两个方向的差别只有生效点不同，这条写错就会「箱子里的东西搬不动」：

- **女仆 → 箱子**：按配置跳过**女仆**的槽位；往箱子里塞时不看配置（配置描述的是女仆的物品栏）；
- **箱子 → 女仆**：箱子侧不看配置；塞进女仆物品栏时**遵守**配置。

过滤清单则两个方向语义完全一致：白名单 = 只搬清单里的（清单空则一件都不搬），
黑名单 = 清单里的不搬（清单空则全都搬）。清单比较用 `sameItemStackIgnoreDurability`，
否则「清单里放一把剑」拦不住任何一把用过的剑。

### 箱子物品栏的来源：Forge Capability → 原版 `Container`

上游走 `te.getCapability(ITEM_HANDLER_CAPABILITY)`。Fabric 1.18.2 没有能给出
`IItemHandler` 的能力系统（`fabric-transfer` 的 `Storage<ItemVariant>` 语义不同、
且按「面」暴露，拿不到上游需要的「按槽位号读写」），所以改成让每种箱子自己交出一个原版
`Container`，再由 `net.minecraftforge.items.wrapper.InvWrapper` 包成 `IItemHandler`。

`InvWrapper` 每次真写入后都会 `setChanged()` —— 原版容器的 `setItem` 只改内存列表、
不标脏区块，少了这一步，「女仆把物品塞进箱子」有可能下线后丢失。

**大箱子**必须覆写 `getContainer`：`ChestBlockEntity` 自身只有 27 格，拼起来那 54 格
是 `ChestBlock#getContainer` 现拼的 `CompoundContainer`
（Forge 侧的箱子 Capability 也是这么做的）。

### 界面：一个菜单 + 一个纯客户端 Screen

- 主界面是 `WirelessIOContainer`（菜单）：9 格过滤清单 + 玩家物品栏 + 三个按钮；
- 「槽位配置」是独立的 `Screen`：它要画 38 个方格，塞不进 176×166 的界面，
  而且没有物品格，做成菜单反而要多一条同步链路。

**手持格必须挡住。** 界面是「右击空气」打开的，手上那件物品本身就是这个界面；
若允许把它拖进过滤清单或丢出去，NBT 会在同一件物品的两份引用之间来回写 ——
这是一条刷物品路径。`clicked` 里拦掉「点手持那一格」与 `ClickType.SWAP`（数字键换手），
与上游一致。

### 1.18.2 的四处替换

- `IForgeMenuType.create(...)` → `ExtendedScreenHandlerType` +
  `ExtendedScreenHandlerFactory`（界面打开时除了菜单 id 还要带「手上这件物品」，
  扩展类型正好用来塞这段数据）；
- `MenuProvider` + `NetworkHooks.openScreen` → `ExtendedScreenHandlerFactory` +
  `ServerPlayer#openMenu`；
- `I18n.get`（客户端类）→ `TranslatableComponent`：公共类里不静态引用客户端类型；
- `RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS`（Forge）→ `WorldRenderEvents.LAST`
  （Fabric，注入在 `WorldRenderer#renderChunkDebugInfo`，是同一段「世界的最后一遍」）。

线框还做了两处小改动：外扩 0.002 格（与方块表面共面会 z-fighting，表现为线框一半闪没了），
线宽 2.0（1.0 宽的线在 1080p 下只有 1 像素，远看几乎看不见）。

### 搬运算法被抽成了 `WirelessIOTransfer`

上游把两段循环写在饰品类里，于是它们**只能在游戏里验证** —— 而「女仆 → 箱子」这条路径
恰恰是最难复现的：专用服务器上没有玩家在线时，区块几秒后就不再 tick，女仆随之停摆，
测什么都是「没动」。抽出来之后它只吃两个 `IItemHandler`，可以脱离世界跑遍所有分支：

```
gradlew verifyWirelessIo
```

29 条断言，覆盖两个方向 × 白/黑名单 × 空清单 × 冻结槽位 × 堆叠溢出 × 不可堆叠物品 ×
短数组兜底 × 小背包的双手移位。被打进 jar 的那份代码就是被检查的那份：
`verify` sourceSet 只额外加测试入口，不改 `main`。

顺带发现的一件事：跑这套检查必须先在进程里调一次 `Bootstrap.bootStrap()` ——
`ItemStack` 的静态初始化会去碰物品注册表，碰不到就抛
`IllegalArgumentException: Not bootstrapped`。这与「创建一个空世界」是两件事。

### 已知限制

- **只能绑箱子与木桶**（`VanillaChestType` / `BarrelChestType`）。潜影盒、漏斗、熔炉虽然
  也是 `Container`，但上游不把它们当隙间的目标，这里保持同样的范围。铁箱子之类需要
  `ILittleMaid#addChestType` 扩展点，而本移植版还没有扩展体系。
- **箱子被玩家打开时不动手**：原版的开箱计数不为 0 时插入的物品，可能在玩家关闭界面时
  被原版逻辑覆盖回去。
- **搬运是「整格搬运」**：一轮里每一格最多搬一次（堆叠溢出会落到下一格），
  没有「每 tick 搬一个」的节流 —— 与上游一致。
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

---
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
---
## 娱乐方块：键盘 / 书架 / 电脑
这三块是「女仆闲置时会自己跑去坐着涨好感度」的家具，与三张棋盘共用同一套骨架。
### 复用而不是新写
| 层 | 棋盘 | 娱乐方块 |
| --- | --- | --- |
| 方块基类 | `BlockJoy` | `BlockJoy`（同） |
| 方块实体基类 | `TileEntityJoy`（记 `SitId`） | `TileEntityJoy`（同） |
| 座位实体 | `EntitySit` | `EntitySit`（同） |
| 兴趣点 | `joy_block` | `joy_block`（同） |
| 渲染形状 | `ENTITYBLOCK_ANIMATED` | `ENTITYBLOCK_ANIMATED`（同） |
差别只有三处，全都是**方块那一侧**的常量：
| 方块 | 碰撞箱 | 座位偏移 | 座位额外朝向 | 好感度类型 |
| --- | --- | --- | --- | --- |
| 键盘 | `4,0,4 → 12,10,12` | `(0.5, 0.625, 0.5)` | `0` | `Keyboard` |
| 书架 | `1,0,1 → 15,5,15` | `(0.5, 0.375, 0.5)` | `-90` | `BookShelf` |
| 电脑 | 四段拼合（显示器 + 键盘托） | `(0.5, 1, 0.5)` | `180` | `Computer` |
### `MaidJoyTask` 与 `MaidBoardGameTask` 的分工
两者代码几乎一样，上游也是两份。区别只有两点，**都不能合并**：
1. **挂在哪个活动上。** `MaidJoyTask` 挂在 **IDLE**（权重 7，在「撒娇 5」之后、
   「四处漫步 20」之前）——「没事干的时候去玩玩」。
   `MaidBoardGameTask` 由 `board_games` 任务挂在 **WORK** 上 ——「这是她的工作」。
2. **认哪个基类。** `MaidJoyTask` 接受任意 `BlockJoy`；`MaidBoardGameTask` 只认
   `IBoardGameBlock`。反过来合并的话，女仆会在**工作模式下跑去坐键盘**。
还有一处**不能照抄**的地方：`isOccupied` 在查不到方块实体时必须返回 `true`。
棋盘是 3×3 多方块，八个边角格也带着同一个 `joy_block` 兴趣点，但它们没有方块实体 ——
返回 `false` 的话女仆会挑中一个边角、走过去、坐下失败，然后永远坐不到棋盘上。
娱乐方块全是单格，这条逻辑对它们没有副作用，所以继续共用。
### 顺手补的资源
- 三份战利品表（`loot_tables/blocks/{keyboard,bookshelf,computer}.json`）
- 三个祭坛配方（`4×木板 + note_block/book/lever + c:diamonds`，`power 0.1`）
上游写的是 `forge:gems/diamond`，这里按本移植版的约定换成 `c:diamonds`
（原因见前面「祭坛配方」那节：`c:` 在本环境里完全由我们自己维护）。
---
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
---
## 新生成女仆的随机外观：从 6 个写死的模型到 193 个

上一版 `EntityMaid#finalizeSpawn` 里是一张写死的 `RANDOM_MODEL_IDS`（灵梦 / 魔理沙 / 琪露诺 /
咲夜 / 芙兰 / 酒狐）。上游不是这么做的：它扫模型包，把「所有可用 model_id」都放进池子，
刷怪蛋 / 祭坛 / 魂符生成时随机挑一个。

### 上游的数据来源，以及它为什么会失效

上游是 `entity/info/ServerCustomPackLoader` + `ServerMaidModels`：扫描游戏目录下的
`tlm_custom_pack/`，解析每个包的 `assets/<domain>/maid_model.json`，收集 `model_id`。
问题在于上游的<b>内置模型是资源包</b>
（jar 里的 `assets/touhou_little_maid/tlm_custom_pack/touhou_little_maid-1.0.0.zip`），
而服务端的资源管理器根本不加载 `assets` —— 所以干净专用服务端上这个池子是空的。
上游自己在 `finalizeSpawn` 里留了一句「这里居然可能为 0」，就是撞上过这件事。

### 本移植版换了数据来源

`entity/info/ServerModelPool` 直接读<b>模组自己的</b> `assets/<domain>/maid_model.json`：
`FabricLoader#getModContainer` + `ModContainer#findPath` 在开发环境与生产环境都返回真实 NIO 路径，
于是可以遍历 `assets/` 下每个域，单人 / 专用服务端行为一致。只读 JSON 的 `model_id` 与
`easter_egg` 两个键（后者非空则跳过，与上游那句 `if (getEasterEgg() == null)` 对应），
不把 `client.resource.pojo.MaidModelInfo` 拖进服务端。

读不到任何东西时回退到原来那 6 个，保证 `finalizeSpawn` 永远有值可选。

### 实测

```
[Futo] random model pool: 193 models
```

193 = `touhou_little_maid` 143 + `touhou_little_maid_old` 34 + `authors_and_credits` 8
+ `geckolib` 4 + `minecraft_15th` 4（彩蛋模型已被排除 1 个）。
连续 `summon` 六只，读到的 `ModelId` 分别是
`yasaka_kanako` / `miyadeguchi_mizuchi` / `satsuki_rin` / `horikawa_raiko` /
`usami_sumireko` / `touhou_little_maid_old:konngara` —— 六个互不相同，而且跨了域。

### 三条生成路径都吃这张表

| 路径 | 走 `finalizeSpawn` 吗 | 结果 |
| --- | --- | --- |
| 刷怪蛋 | 是（原版 `SpawnEggItem` → `EntityType#spawn` → `Mob#finalizeSpawn`，dataTag 为空） | 随机 |
| 祭坛合成 | 是（`AltarRecipe#finalizeSpawn` 传 `SPAWN_EGG` + null dataTag） | 随机 |
| 魂符 INIT | 是（`ItemSmartSlab#spawnNewMaid` 同样传 null dataTag） | 随机 |
| `/summon` 带 `ModelId` | dataTag 非空 → 不覆盖 | 指定值 |


## 魂符（smart_slab）：首次进服送的那张「已驯服女仆」

上游有这样一个物品：新玩家第一次进世界时会拿到一张魂符，右击地面就能直接放出一只
已经属于自己、随机外观的女仆 —— 相当于「开局即送」。本移植版之前整块缺失。

### 三件物品，一个类

| 类型 | 物品 id | 右击地面 | 右击女仆 |
| --- | --- | --- | --- |
| `INIT` | `smart_slab_init` | 现场生成一只全新女仆并认主 | —— |
| `EMPTY` | `smart_slab_empty` | —— | 把她收进魂符（实体 `discard`） |
| `HAS_MAID` | `smart_slab_has_maid` | 把那只女仆放出来 | —— |

用完之后一律换回 `EMPTY` 并给 1 秒冷却。三件共用 `ItemSmartSlab`，
名字与提示共用 `item.touhou_little_maid.smart_slab` 一条键（上游也是靠 `getDescriptionId()` 这么做的）。
只有 `INIT` 进创造模式物品栏，另两个是中间态 —— 上游 `InitCreativeTabs` 也只 accept 了 INIT。

### 「只发一次」靠的是进度，不是自己记标志位

上游把赠礼做成一条隐藏进度：`advancements/give_smart_slab.json` 的判据命中后，
奖励战利品表 `loot_tables/give_smart_slab.json` 给出一个 `smart_slab_init`。
本移植版照搬，连 Fabric 侧缺的那块也补上了：

- **判据**：`GiveSmartSlabConfigTrigger`，`test` 里读 `MiscConfig.giveSmartSlab()`（`GiveSoulSpell`，默认开）；
- **战利品函数**：`touhou_little_maid:set_init_maid_owner` —— 上游是 Forge 的
  `DeferredRegister<LootItemFunctionType>`，1.18.2 直接 `Registry.register(Registry.LOOT_FUNCTION_TYPE, ...)`；
- **触发点**：`EnterServerEvent` 在 `ServerPlayConnectionEvents.JOIN` 里与「送书」一起敲一下。

好处是<b>持久化天然正确</b>：进度存在 `world/advancements/<uuid>.json` 里，
送过就不会再送 —— 这正是「按玩家数据文件是否存在判断新玩家」的等价实现，但不用自己碰文件。

实测（`runServer` + RCON）：

```
首次登录 -> 背包里出现 1 个 smart_slab_init
          其 NBT InitMaidOwner = [I; 940439953, ...]
          与 data get entity Dev UUID 逐项相同      （战利品函数生效）
save-all flush -> world/advancements/<uuid>.json 里出现 touhou_little_maid:give_smart_slab
重启服务器再登录 -> 背包里仍然是 1 个            （不重复发放）
```

### 与上游的四处差异

1. **没有 `MaidAndItemTransformEvent`。** 那是给附属模组改数据的 Forge 事件；本移植版没有扩展注册表。
2. **「收女仆」走 `Item#interactLivingEntity`，不是 `InteractMaidEvent`。** Fabric 1.18.2 本来就有这个钩子，
   相机 `ItemCamera` 也是这么写的，语义一致且少一层事件。
3. **数量上限用按需统计。** 上游读玩家 capability，这里与驯服路径共用 `countOwnedMaid`。
4. **`onEntityItemUpdate` 的「发光 + 无敌 + 不落虚空」没有对应钩子。**
   防火改用 `Item.Properties#fireResistant()`（与照片、胶片一致）；
   发光与虚空保护需要每 tick 扫实体，代价与收益不成比例，暂不实现 —— 这一点与 `AbstractStoreMaidItem` 里已有的说明一致。

另外加了一处上游没有的防御：放女仆时主人 UUID 先读 `Owner`（原版 `TamableAnimal` 写的），
读不到再回退本移植版自己的 `FutoOwner`，两者都没有就当作「不是你的女仆」拒绝。
宁可拒绝，也不要把别人的女仆放出来。


## 手册进度：54 词条 / 3 分类，全部补齐

判据是 `port/book_pages_held_back/validate-book.ps1`。它按帕秋莉的失败模式静态检查：
`spotlight` 的物品、词条与分类图标、`entity` 页、`altar_recipe` 的 `recipe_id`、
`c:` 标签，以及 ingredients / output 里引用的物品与实体 id：

```
entries=54 categories=3 problems=0
```

另外三项**不在**那个脚本的检查范围内，这轮单独核过：

| 项 | 怎么核 | 结果 |
| --- | --- | --- |
| 每个页引用的 `text` / `title` / `link_text` 语言键 | 遍历 54 个词条的所有页，比对 `zh_cn` / `en_us` | 0 缺失 |
| `image` 页的贴图 | 把 `ns:path` 还原成 `assets/<ns>/<path>` 查文件 | 19 张全在 |
| `crafting` 页的配方 | 查 `data/touhou_little_maid/recipes/*.json` | 见下 |

**`crafting` 页这轮修掉了一个真缺口**：`overview/gohei_and_danmaku` 引用
`touhou_little_maid:hakurei_gohei` 与 `sanae_gohei` 两条**工作台**配方，而本移植版只有
祭坛配方。客户端日志里每次都刷这两行：

```
[WARN] (patchouli) Recipe touhou_little_maid:hakurei_gohei (of type minecraft:crafting) not found
[WARN] (patchouli) Recipe touhou_little_maid:sanae_gohei (of type minecraft:crafting) not found
```

按上游 1.18.2 / 1.20.1 的同名配方补上（`forge:rods/wooden` → `c:wooden_rods`、
`forge:gems/diamond` → `c:diamonds`）。补完后客户端连服，这两行不再出现。

**`multiblock` 页不需要 JSON。** `overview/multiblocks_altar` 引用
`touhou_little_maid:altar`，但上游 jar 里两个版本都**没有** `multiblocks/altar.json` ——
它是在代码里注册的（`compat/patchouli/MultiblockRegistry#init`，由 `PatchouliCompat#init` 调），
本移植版逐字照搬了这段。

最后一条 `other/model_switcher.json` 原本扣在 `port/book_pages_held_back/`，
因为它引用的 `model_switcher` 方块/物品当时还没移植。整块子系统已经补完
（见下文「模型切换器」一节），词条搬回手册，`entries` 由 53 变 54。

它和其余词条一样只有 `spotlight` + `text` 两页 —— 上游 `data/.../recipes/` 里
**没有**这个物品的配方，所以没有 `crafting` 页，不是漏了。


## 「一放置就崩溃」：渲染器注册丢了，但服务端毫无异常
### 现象
扫帚放下去客户端立刻崩：
```
java.lang.NullPointerException: Cannot invoke
"net.minecraft.client.renderer.entity.EntityRenderer.shouldRender(...)"
because "entityRenderer" is null
    at EntityRenderDispatcher.shouldRender(EntityRenderDispatcher.java:121)
    at LevelRenderer.renderLevel(LevelRenderer.java:1276)
    at GameRenderer.renderLevel(GameRenderer.java:1034)
```
堆栈里既没有 `EntityBroom` 也没有 `ItemBroom` —— 实际原因就藏在第一行：
`renderers.get(entity.getType())` 返回了 **null**，也就是这个实体类型**没有客户端渲染器**。
### 为什么服务端查不出来
这是这次最值得记的一点。我当时的排查顺序是错的：
1. 起 `runServer`，RCON `summon touhou_little_maid:broom` → **一切正常**，
   实体生成了，也 tick 了，`OwnerUUID` 读得出来，没有任何异常。
2. 于是判断「服务端没问题，崩在客户端」。
这个判断本身没错，但结论是「客户端渲染器有问题」，而真正的原因更简单：
**渲染器那一行根本没写进文件**。
`EntityBroom.createType()` 的注册在 `InitEntities`，`EntityRendererRegistry.register(...)`
那一行本该在 `TouhouLittleMaidFutoClient`。后者在一次脚本化编辑里被静默回滚了，
而**少了它不会有任何编译错误、没有任何日志**：`EntityType` 与渲染器是两张完全独立的表，
只在一处相交 ——
```java
// EntityRenderDispatcher#getRenderer
return (EntityRenderer<? super T>) this.renderers.get(pEntity.getType());   // 缺了就是 null
// EntityRenderDispatcher#shouldRender
return entityrenderer.shouldRender(pEntity, pCamera, pCamX, pCamY, pCamZ);  // 这里才 NPE
```
而且这次不是「服务端没暴露」，而是**所有离线/服务端检查都天生看不到它**：
实体注册、属性表、AI、网络同步、掉落物，全都不碰渲染器表。
> 顺带一个反讽：同一份文件里，座位实体那一段的注释早就写着
> 「未注册类型的 `getRenderer` 返回 null，渲染循环不处处判空」——
> 我把这条规律写下来了，却没给自己加一道检查。
> **「知道某个坑」和「为它设一道闸」是两件事。**
### 闸：`port/check-renderers.ps1`
不依赖任何运行环境，直接扫全树比对两张表：
| 来源 | 匹配 |
| --- | --- |
| `public static final EntityType<...> NAME =` | 实体类型声明 |
| `public static final BlockEntityType<...> NAME =` | 方块实体类型声明 |
| `EntityRendererRegistry.register(... InitEntities.NAME` | 实体渲染器 |
| `BlockEntityRendererRegistry.register(... InitBlocks.NAME` | 方块实体渲染器 |
三条判据：
1. 每个实体类型都必须有渲染器；
2. 每个方块实体类型都必须有渲染器 —— **除非**它的方块 `getRenderShape()` 返回 `MODEL`
   （目前只有神社庭灯：它靠 `blockstates/maid_beacon.json` 画，方块实体只存数据）；
3. 反向也要查：渲染器指向一个**不存在**的字段就是「悬空注册」，
   说明字段被重命名或删掉了、客户端还指着旧名字。
当前输出：
```
entity types = 11  renderers = 11
block entity types = 11  renderers = 10
ALL OK
```
（11 个方块实体类型里 10 个要渲染器，第 11 个是 `MAID_BEACON_TE`，在白名单里并注明了原因。）
### 端到端验证：让 `runClient` 自己连上来
静态检查只能证明「注册语句存在」，不能证明「真的画得出来」。
所以 `build.gradle` 里加了一个可选开关：
```
gradlew runServer                # 一个终端
gradlew runClient -PjoinServer   # 另一个终端，自动连 127.0.0.1:25565
```
`--server` / `--port` 是原版启动参数，1.18.2 的 `Main` 认它，进服后会自动连接
（不是 1.20 才有的 `--quickPlayMultiplayer`）。不带 `-PjoinServer` 时行为与以前完全一致。
然后用 RCON 在玩家脚边生成实体，看客户端会不会崩：
```
summon touhou_little_maid:broom -1 64 -8
```
> 第一次试的时候我把扫帚生成在 `y=-60`（以为世界是超平坦），结果那是个正常世界，
> 玩家在 `y=63`，扫帚在深地下 —— 这时**不能**因为「没崩」就下结论。
> 后来确认了：`shouldRender` 是 `LevelRenderer` 对**每个被追踪实体**都会调的，
> 与是否在视锥内、是否朝着它无关，所以即使看不见也会走到崩溃点。
> 但为了不留疑问，还是把它挪到了脚边重测了一遍。
结果：客户端带着扫帚正常渲染，`run/crash-reports` 为空。
### 这条经验的一般化
一个崩溃点离原因越远，就越需要**结构性检查**而不是「再仔细看一遍代码」。
这一轮之前我已经栽过两次同类问题（`Direction#toYRot()` 记错、
`renderBackground` 的注释前提写反），三次的共同点是：
**代码本身看起来完全正常，错误在「某处少了一行」或「某处的前提是假的」。**
所以现在的闸是这三道，改动后一起跑：
| 闸 | 查什么 |
| --- | --- |
| `port/check-renderers.ps1` | 注册表与渲染器表是否一一对应 |
| `port/book_pages_held_back/validate-book.ps1` | 手册里引用的物品 / 配方 / 标签是否都真的存在 |
| `gradlew verifyWirelessIo` | 隙间搬运规则（脱离世界跑） |

---
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

## 野餐篮与野餐垫：一个物品，三种身份

第六个「野餐与娱乐」子系统。野餐篮在原模组里同时是**方块物品 / 9 格容器 / tooltip 组件**，
野餐垫是它的落地形态（一个 5×5 多方块）。这次两半一起落地，`MaidMealType.HOME_MEAL`
也从「只留枚举值」变成了真的有行为。

### 1. 一个物品，三种身份

`ItemPicnicBasket` 同时继承 `BlockItem` 并实现 `ExtendedScreenHandlerFactory`：

| 操作 | 走哪条路 |
| --- | --- |
| 右击地面 | `BlockItem#useOn` → 放下 `picnic_mat`，篮里 9 格搬进中心方块 |
| 右击空气 | `use` 里 `ServerPlayer#openMenu(this)` → 9 格界面（上游是 `NetworkHooks.openGui`） |
| 鼠标悬停 | `getTooltipImage` 返回 `ItemContainerTooltip`，客户端换成 `ClientItemContainerTooltip` 画一排图标 |

三者的数据源是同一份：物品 NBT 里的 `PicnicBasketContainer` 子标签，
内容就是 `ItemStackHandler#serializeNBT()` 的输出 —— 与上游逐字相同，
所以「篮子 ⇄ 垫子」来回搬不会丢东西。

界面上那道防刷物品的闸门照抄上游：`slotId == 36 + selected`（当前手持那一格）与
`ClickType.SWAP` 都直接 return，每次点击后再 `setContainer` 把 NBT 写回物品。

### 2. 5×5 多方块，但只有中心格有内容

放下野餐篮会一次性铺 25 格。**食物、四张座位、渲染全部以中心格为准**，
其余 24 格只负责占位与 1/16 格高的碰撞箱。每格都有自己的方块实体，
但它们的 `centerPos` 都指回中心，所以拆掉任意一格都能找回中心去取食物。

四张座位的偏移是写死的常量（`2,2` / `-1,2` / `-1,-1` / `2,-1`），
它们对应模型里那四块小垫子的位置，**不是四个角**，不能按对称性去改。
女仆坐下时朝向按「从中心指向自己」算，于是四个方向各自朝外。

### 3. 拆一格只掉一个篮子：`onRemove` 递归与一次性闸门

上游的 `handlePicnicMatRemove` 会把其余 24 格改成空气，而 1.18.2 里每改一格都会再触发一次
`onRemove`（Forge 那边只有 `playerWillDestroy` 与 `onBlockExploded` 两个入口，
不会有这个递归）。如果照抄，**一次拆除会掉出 25 个野餐篮**。

所以这里加了 `TileEntityPicnicMat#claimBasketDrop()`：整个结构里「掉篮子」这件事
只允许成功一次。字段不入存档 —— 它与这一份结构共存亡。
座位实体（`EntitySit`）也在这时 `discard()`，否则拆掉垫子后女仆会悬在原地。

### 4. `HOME_MEAL` 从「只有枚举值」变成真的有行为

之前 `MaidMealType.HOME_MEAL` 只是个占位：任务不存在，`MaidMealManager` 也没注册实现。
这次补齐三样东西：

1. `MaidFindHomeMealTask`（IDLE，权重 6）—— 通过 `home_meal` 兴趣点找「还有空位的垫子」走过去坐下；
2. `MaidHomeMealTask`（`tlm_ride_idle`，权重 5）—— 坐在垫子上时从中心格取**一份**食物吃掉；
3. `DefaultMaidHomeMeal` —— 家庭餐的好感度换算。

第 3 条是关键：`MaidMealManager#init` 之前只注册了 `WORK_MEAL` 与 `HEAL_MEAL`，
所以 `getMaidMeals(HOME_MEAL)` 恒为空。**表现是女仆会走过去坐下、然后一直不吃**
（第一次实测就是这样：面包数量一动不动）。补上实现后，`startUsingItem` 才会被调用，
原版的进食流程与 Gecko 的 `use_mainhand:eat` 动画才真正开始。

家庭餐与工作餐的差别只在好感度：工作餐掷 100 面骰给 0/1 点，家庭餐把整份营养
按 `round(营养 + 营养 × 饱和度 × 2) / 15` 折算，余数再按概率补 1 点。

### 5. 兴趣点必须只收中心格

`MaidPoiManager` 里新加的 `HOME_MEAL` 过滤了 `PART == CENTER`，与棋盘那边「九格全收」相反。
理由：棋盘边角格查不到方块实体、会被当成「已占用」自动滤掉，而野餐垫边角格**确实有方块实体**，
多收的话女仆会走向同一张垫子的 25 个坐标，「四张座位还空不空」的判据就失效了。

顺带把 `JOY_BLOCK` 兴趣点补全成上游的六块（棋盘 3 块 + 键盘 / 书架 / 电脑）——
之前只收了棋盘。

### 6. 渲染：直接用 1.20.1 的基岩模型

- 几何**直接读 1.20.1 的 `models/bedrock/block/picnic_mat.json` 与 `picnic_basket.json`**
  （与本模组 jar 里那一份逐字节相同），走 `BedrockBlockModels` —— 与祭坛 / 神社 / 三张棋盘
  同一条管线。上一版曾把这两个模型手抄成 Java `ModelPart`（529 行 `addBox`），
  那是在重复造轮子：管线已经能用，手抄只会引入抄错。现在已经删掉。
- `PicnicBasketIconModel` 是唯一的重写：上游用基岩模型库的「单南面」方块（厚度 0 的板），
  本移植版没有那个库，于是用原版 `addBox` 造同样一块 0 厚度的板 —— 六面共用同一段 UV，
  正对摄像机时看到的就是同一张图。
- `PicnicBasketItemRenderer` 按 `TransformType` 分两种：`GUI` 画扁平图标，
  其余画 3D 模型（上游用 Forge 的 `IItemRenderProperties`，Fabric 用 `BuiltinItemRendererRegistry`）。
  图层在**第一次渲染时**才烘焙 —— 注册时 `Minecraft#getEntityModels()` 还没就绪。
- `TileEntityPicnicMatRenderer` 三层：装饰件（`basketHide/breadHide/cakeHide` 在真的放了东西时隐藏）

**为什么不能照抄上游那两句 `translate(0.5, 1.5, 0.5)` + `ZN(180)`：** 那是给
「原版 `ModelPart` 空间」的旧模型用的（那个空间 Y 向下、y=24 是地面）。野餐这套 json 进的是
本移植版的基岩管线，`GeoCube#createFromPojoCube` 在换算时已经处理过方向。实测包围盒：

```
Block model picnic_mat loaded: 49 bones, bbox x[-2.231..2.313] y[-0.013..0.625] z[-2.251..2.251]
```

底面就在 0、水平居中、Y 向上 —— 照抄那两句会被翻个底朝天再抬到 0.875~1.5 格高，
也就是「悬空倒扣」。这与上一轮祭坛 / 神社换基岩模型时踩的是同一个坑，
所以这次先看日志里的实测包围盒，再决定姿态：摆放只有 `translate(0.5, floorOffset, 0.5)` +
`YP(toYRot + 180)`（后者与上游的 `YN(180 - 2D*90)` 等价）。

食物那 6 组坐标也是同理：上游给的是「抬 1.5 格 + 翻转」之后的空间，竖直分量正好被那个 1.5
反了过来，于是本移植版把 z 换成 `1.5 - z`（`1.4125 → 0.0875` 就是「贴着垫面」），x / y 不变。
  → 六格食物按固定坐标摆盘（≥10 个时叠一摞）→ 最后画垫子本身。

### 6.5 「取出食物后模型不消失」：`ItemStackHandler#deserializeNBT` 不清空

实测报告：空手潜行右键把垫子里的食物取走，**物品还画在原地**。

根因在 Forge 的 `ItemStackHandler#deserializeNBT`：

```java
public void deserializeNBT(CompoundTag nbt) {
    setSize(nbt.contains("Size") ? nbt.getInt("Size") : stacks.size());   // 9 → 9，什么都不做
    for (每个 NBT 里出现的 Items 条目) { stacks.set(slot, ItemStack.of(tag)); }
}
```

空槽本来就不会写进 `Items`，而它<b>只覆盖 NBT 里出现的槽位</b> —— 于是客户端
「复用同一个方块实体、再 `load()` 一次」时，被取空的那些格子仍然留着上一次的物品，
渲染器照旧画它。服务端是对的（`extractItem` 自己清了），只有客户端这份拷贝会残留。

修法：`TileEntityPicnicMat#load` 在 `deserializeNBT` 之前把 9 格显式清空；座位表同理，
先复位成 `NIL_UUID` 再读。这条对任何「方块实体里挂 `ItemStackHandler`、靠数据包同步」的场景都成立。

### 7. 验证到什么程度

服务端（`./gradlew runServer` + RCON）跑通了完整链路，全部由命令行断言，没有靠肉眼看：

```
/setblock 0 61 0 touhou_little_maid:picnic_mat[part=center]
/data merge block 0 61 0 {StorageItem:{Size:9,Items:[{Slot:0b,id:"minecraft:bread",Count:3b}]}}
/time set 13000            # 13000 才是 IDLE 时段
/summon touhou_little_maid:maid 2 62 2
```

结果：

- 女仆从 (2.5, 62.5) 走到 (2.0, 60.95) 并**坐在一个 `EntitySit` 上**（`Pos` 与那个实体完全重合，
  活动从 `idle` 变成 `tlm_ride_idle`）；
- 面包从 `Count: 3b` 变成 `Count: 2b` —— 她真的吃了一个；
- `Favorability` 从 0 变成 2（一整个面包的营养折算）；
- 服务端日志无异常，`crash-reports` 为空。

`port/book_pages_held_back/validate-book.ps1` → `entries=54 categories=3 problems=0`：
野餐篮词条从 `port/book_pages_held_back/` 还原进手册，`altar_recipe` 页引用的
`craft_picnic_basket` 存在、材料都是已注册物品。

客户端也起过一次并连上同一个服务端：垫子（中心格 + 边角格）与野餐篮物品在视野里渲染，
`Picnic basket item renderer failed` 与帕秋莉的 `Error compiling book` 都没出现。

**没验证到的**：界面本身（右键空气打开的 9 格）与 tooltip 需要真人操作。
两者的注册方式与已经验证过的隙间界面完全同构，所以风险很低，但仍属于「留给你实测」。

## 模型切换器：红石切模型，以及它为什么不需要 `canConnectRedstone`

上游对应 `block/BlockModelSwitcher` + `tileentity/TileEntityModelSwitcher` + `item/ItemModelSwitcher`
+ 两个 Screen + 两个数据包。玩法见手册 `other/model_switcher`：拿物品右击女仆完成绑定 →
放下方块 → 在界面里配若干「模式」（模型 id + 头顶文字 + 朝向）→ 往它的左右两侧各接一根
红石线，每来一次脉冲就把女仆换成下一个模式。

### 1. 它是「普通 JSON 模型」的方块

`getRenderShape` 返回 `MODEL`，几何在 `models/block/model_switcher.json`；界面与红石逻辑才在方块实体里。
所以它**不需要**方块实体渲染器 —— `port/check-renderers.ps1` 的白名单里那条是神社庭灯
（庭灯有方块实体但靠 blockstate 画），两者不是同一类例外。

### 2. 左右各一根线 = 上一张 / 下一张

```java
Direction facing = state.getValue(FACING);
boolean leftSignal  = getSignal(pos.offset(facing.getCounterClockWise().getNormal()),
                                facing.getCounterClockWise()) > 0;
boolean rightSignal = getSignal(pos.offset(facing.getClockWise().getNormal()),
                                facing.getClockWise()) > 0;
boolean hasSignal = leftSignal || rightSignal;
if (switcher.isPowered() == hasSignal) return;   // 只认「无 → 有」这条边沿
switcher.setPowered(hasSignal);
if (!switcher.isPowered()) return;               // 掉电不换模式
```

正面留给玩家右击开界面、背面通常贴墙，所以「左右各一根」天然就是两个输入口。
`calculateIndex` 与上游一致：左侧 `index + 1`（到末尾回绕）、右侧 `index - 1`。

**上游那处「除以零取模」被去掉了。** 上游直接拿 `size` 参与取模，列表为空时是 `% 0`；
本移植版先判 `size <= 0` 直接返回。

### 3. 上游的 `Block#canConnectRedstone` 是 Forge 补丁，这里不需要

上游重写了它，让两侧的红石线「拐弯指进方块」。这个方法在 1.18.2 原版里**不存在**
（`javap` 扫过整个 `net/minecraft`，`canConnectRedstone` 一次都没出现），
它是 Forge 给 `BlockBehaviour` 打的补丁。而它纯粹是外观：红石线要不要把线拉到这一侧。

功能上并不需要 —— `RedStoneWireBlock#getSignal(state, level, pos, dir)` 的实现是
`return state.getValue(POWER);`，根本不看 `dir`。本类读的是
`level.getSignal(左/右邻居, 那一侧)`，与线的外观无关。

### 4. NBT 搬运比上游少一层 `ForgeData`

| 位置 | 上游（Forge） | 本移植版 |
| --- | --- | --- |
| 方块实体存档 | `getTileData()`，落盘到 `ForgeData` 下 | 直接写 `saveAdditional` 收到的 tag |
| 物品里的快照 | `StorageData/ForgeData/*` | `StorageData/*` |

键名逐字相同（`info_list` / `entity_uuid` / `list_index`，条目是
`model_id` / `text` / `direction`），所以两边的读写共用同一套 `load` / `saveWithoutMetadata`。
`entity_uuid` 是 `NbtUtils.createUUID` 产的 `[I; …]`。

拆方块时 `onRemove` 手动 `popResource`，战利品表里**不再**写一份 —— 否则会掉两个，
且掉出来的那个是空白方块（绑定信息与模式列表会丢）。

### 5. 验证到什么程度

服务端（`./gradlew runServer` + RCON）跑通全链路，全部命令行断言，没有靠肉眼看。
`facing=north` 时左 = 西（`-1`）、右 = 东（`+1`）：

```mcfunction
/fill -5 99 -5 5 99 5 minecraft:stone
/setblock 0 100 0 touhou_little_maid:model_switcher[facing=north]
/data merge block 0 100 0 {info_list:[{model_id:"touhou_little_maid:hakurei_reimu",text:"MODE-A",direction:0},
                                      {model_id:"touhou_little_maid:kirisame_marisa",text:"MODE-B",direction:1}],
                         entity_uuid:[I;-716640274,-413972909,-2081800275,-1312281669],list_index:0}
/setblock 1 100 0 minecraft:redstone_block     # 右侧（东）
```

| 操作 | `list_index` | `ModelId` | `CustomName` | `Rotation` |
| --- | --- | --- | --- | --- |
| 初始 | 0 | `hakurei_reimu` | —— | —— |
| 右侧通电 | 1 | `kirisame_marisa` | `MODE-B` | `[90, 0]` |
| 两路同时通电、再逐路断电 | 1（不变） | 不变 | 不变 | 不变 |
| 左侧通电 | 0 | `hakurei_reimu` | `MODE-A` | `[0, 0]` |

**`direction` 存的是 `Direction#get2DDataValue()`，所以 0 = SOUTH（yaw 0）、1 = WEST（yaw 90）**，
不是「0 = 北」。这与 README 里五子棋那节记的 `toYRot()` 表是同一件事 —— 第一次实测时
按「1 = 南」去对 180°，结果对不上，就是这个原因。

再补三条边界：

- **空文字的条目**：`CustomName` 与 `CustomNameVisible` 都被移除 ——
  `setCustomName(null)` 只清自定义名，可见性标志是独立的，必须显式
  `setCustomNameVisible(false)`，否则上一张模式的名字会一直挂在头顶。
- **空 `info_list` 给脉冲**：什么都不做，不崩。
- **拆方块**：`/setblock … air` 后掉出的物品带
  `tag.StorageData{entity_uuid, info_list, list_index}`，与放进来的那份逐字段相同。

客户端也起过一次（`runClient` 到主菜单）：资源重载没有 `Missing model` / `missing texture`，
日志里没有 switcher 相关报错，`run/crash-reports` 为空。

**没验证到的**：界面本身（右击方块打开的列表编辑）与「手持物品右击女仆绑定」这两步需要真人操作。
数据包已按 1.18.2 的线程模型写好：`ClientPlayNetworking` 的回调里**先把 buffer 读完**
（出回调即失效），再 `client.execute(...)` 切主线程开 Screen；C2S 那侧服务端复核
`ServerLevel#isLoaded`（避免被伪造坐标强制加载区块）与「该坐标确实是模型切换器」。

---

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
---

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

## 进度（成就）系统：54 条全量移植

数据文件直接取自上游 **Forge 1.18.2** 的 jar（`data/touhou_little_maid/advancements/**`），
`assets/touhou_little_maid/textures/advancements/backgrounds/stone.png` 与
`data/touhou_little_maid/loot_tables/advancement/{cake,power_point}.json` 也一并搬来。

### 为什么用 1.18.2 的那一套而不是 1.20.1 的

两个版本的 JSON 只有两处实质差异，而且**都是 1.20.1 才需要改**：

| 字段 | 1.18.2 | 1.20.1 | 本仓库 |
|---|---|---|---|
| 祭坛配方 id | `touhou_little_maid:altar/spawn_box` | `touhou_little_maid:altar_recipe/spawn_box` | 用 1.18.2 的 —— 本仓库配方目录就是 `recipes/altar/`，前缀自然是 `altar/spawn_box` |
| 图标 NBT 写法 | `"nbt": "{RecipeId:\"...\"}"`（字符串） | 仍是字符串 | 同 1.18.2（1.20.2 才变成对象） |
| `sends_telemetry_event` | 无 | 有 | 1.18.2 的 `DisplayInfo` 不认识这个字段，带上只会被忽略 |

其余 52 条逐字相同，所以**不需要**任何改写 —— 直接复制即可。

### 物品侧：九件「只为图标存在」的物品

`display.icon` 里有九个图标没法用原版物品表达（百人斩、百级、全套下界合金、
「生成？？？」……），上游的做法是各注册一件只有贴图的物品：

- `item/ItemAdvancementIcon`（新）—— 八件公用一个类，`stacksTo(1)`、
  **不进任何创造模式物品栏**、`getDescriptionId()` 统一成「进度图标」。
  对应 `all_netherite_equipment` / `change_chair_model` / `change_maid_model` /
  `kill_100` / `kill_dragon` / `kill_slime_300` / `kill_wither` / `maid_100_healthy`。
- `item/ItemEntityPlaceholder`（新）—— 「生成？？？」，配方 id 记在 NBT 的 `RecipeId`，
  客户端渲染器按配方文件名去 `textures/item/<配方文件名>.png` 找贴图，找不到退回
  `entity_placeholder.png`。对着方块顶面右键还能**直接生成那条配方的产物**
  （上游给整合包作者留的口子）。

渲染侧对应 `client/model/EntityPlaceholderIconModel` +
`client/renderer/item/EntityPlaceholderItemRenderer`，注册方式与野餐篮同一套
（`EntityModelLayerRegistry` 烘焙一块 16×16 的零厚度板 + `BuiltinItemRendererRegistry`）。
物品模型 `models/item/entity_placeholder.json` 是 `builtin/entity`，那套 `display` 变换直接复用。

### 触发侧：23 处补点 + 一个新建的管理器

移植前 `TriggerType` 里 48 个常量只有 25 个真有触发点，其余 23 个「写了但没人打」——
表现为**进度永远不亮，日志一片安静**。本轮全部补齐：

| 位置 | 事件 |
|---|---|
| `EntityMaid#tameMaid` | `tamed_maid`、`tamed_maid_from_structure`（结构生成标记写进 NBT 的 `StructureSpawn`） |
| `EntityMaid#thunderHit` | `lightning_bolt`、`maid_100_healthy`（**顺带补上「首次被雷劈 +20 生命上限」**，并把 `StruckByLightning` 写进 NBT） |
| `EntityMaid#setItemSlot`（新覆写） | `any_equipment`、`all_netherite_equipment` |
| `EntityMaid`（牛奶 / 附魔金苹果 / 玻璃瓶 / 鞍 / 背包） | `clear_maid_effects`、`eat_enchanted_golden_apple`、`take_maid_xp`、`pickup_maid`、`maid_backpack` |
| `FavorabilityManager#add` | `favorability_increased`、`favorability_increased_max`、`maid_100_healthy` |
| `MaidFarmPlantTask` / `MaidFeedAnimalTask` / `MaidFeedOwnerTask` | `maid_farm`、`maid_feed_animal`、`maid_feed_player` |
| `MaidFishingHook` | `maid_fishing`、`maid_fishing_enchanted_book` |
| `EntityPowerPoint#playerTouch` | `pickup_power_point` |
| `MaidConfigPacket`（换模型 / 换任务） | `change_maid_model`、`switch_task` |
| **`MaidKillRecordManager`（新）** | `maid_kill_mob`、`kill_100`、`kill_slime_300`、`kill_wither`、`kill_dragon` |

`MaidKillRecordManager` 是这一轮唯一新建的「子系统」：四个计数（总数 / 史莱姆 /
凋灵 / 末影龙）写进 NBT 的 `KillRecord` 子标签。**必须落盘** ——「百人斩」要累计 100 次，
区块一卸载就归零的话这两条进度永远点不亮。

### 与上游的差异（都有原因）

| 上游 | 本移植版 | 原因 |
|---|---|---|
| `LivingDeathEvent` + `source.getEntity()` | `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` | `ServerLivingEntityEvents` 是 fabric-entity-events **1.6.0+** 才有的类；本工程按 1.18.2 的 fabric-api 0.76 解析到 **1.4.x**，里面只有 `AFTER_KILLED_OTHER_ENTITY`，语义完全一致 |
| `EntityMaid#isStruckByLightning()`（Forge 给的） | 自己维护 `struckByLightning` 位 + NBT | 1.18.2 vanilla 的 `Entity` 没有这个方法 |
| `Entity#onEquipItem` / Forge 的 `MaidEquipEvent` | 覆写 `setItemSlot`，用 `firstTick` 过滤读档 | 1.18.2 没有 `onEquipItem`；上游 1.18.2 同样是覆写 `setItemSlot` |
| `getRecipeManager().byType(...).get(id)` | 遍历 `getAllRecipesFor(...)` 比对 id | 1.18.2 没有按 id 取配方的公开方法（配方总数 40 上下，右键频率极低） |
| `initializeClient` + `IItemRenderProperties` | `BuiltinItemRendererRegistry` | Fabric 没有 Forge 的物品渲染属性 |

### 已知缺口

**`base/change_maid_sound`（「换个嗓音」）目前点不亮**：它需要「每只女仆各自一个音效包 id」
这个子系统（上游 `SetMaidSoundIdMessage` + `client/sound/CustomSoundLoader` +
`MaidSoundPackGui` + 自定义 ogg 流式播放）。本仓库只把模型里的 `use_sound_pack_id`
读进了 `MaidModelInfo`，**没有**把它同步到实体、也没有选择界面。
等「女仆音效包」那一轮做完，在写入成功的地方补一行判据即可（`TriggerType.CHANGE_MAID_SOUND` 常量已就位）。

其余 53 条全部可达。

### 验证边界

- `./gradlew.bat build` = BUILD SUCCESSFUL；四道闸全绿（见下）。
- 打包结果里 `data/touhou_little_maid/advancements/**` 共 **54** 个 JSON。
- `runServer` 启动日志：`Loaded 1195 advancements`，**零条** `Parsing error loading custom advancement`
  —— 判据 id 写错 / 字段名写错都会在这一行暴露，所以这一条是有效性证据。
- `/advancement grant` 在无玩家时只回 `No player was found`（选择器先于 id 解析），
  因此**空服时没法**用 RCON 验证进度是否存在；**有玩家在线时就可以** ——
  本轮的修复轮用 `advancement grant Dev only touhou_little_maid:base/craft_gohei`
  验证过，回 `Granted the advancement [advancements.touhou_little_maid.base.craft_gohei.title]`。
- **未验证**：真人游玩下每一条进度是否真的点亮（尤其 `kill_slime_300` 这种要累计 300 次的），
  以及进度页面里九个自绘图标的观感。

### 「进度页里只有三个原版标签」：1.18.2 的渐进式同步（已修复）

**现象**：打开「进度」页只有「农牧业 / 冒险 / Minecraft」三个原版标签，看不到模组的「基础 / 女仆 / 好感度 / 挑战」。

**这不是数据没注册**。服务端日志 `Loaded 1195 advancements`（原版 1141 + 本模组 54），零条 `Parsing error loading custom advancement`；玩家在线时 `/advancement grant Dev only touhou_little_maid:base/craft_gohei` 也能正常授予，回 `Granted the advancement [advancements.touhou_little_maid.base.craft_gohei.title]`。

**根因**：1.18.2 的进度是**渐进式同步**，链路分三段：

1. `PlayerAdvancements` 维护一个 `visible` 集合，只有它的元素才会被 `ClientboundUpdateAdvancementsPacket` 发给客户端；
2. `visible` 的填充入口 `ensureAllVisible()` **只从「已完成的进度」出发**，而 `shouldBeVisible()` 又要求「自己或子树有点亮过」—— 所以一条都没点亮时，整棵树（连 root 一起）都发不出去；
3. 客户端的标签页只由「收到的、`parent == null` 的进度」生成（`AdvancementList#add` → `onAddAdvancementRoot` → `AdvancementTab#create`）。**没同步到的 root 就等于不存在的标签页。**

**用真实存档复现**：拿 2026-11-24 那次会话的玩家进度跑同一套算法的 Python 复刻（`port/sim_adv_visibility.py`，只读，不参与构建），可见集正好 **214** 条，与客户端日志的 `Loaded 214 advancements` 逐字吻合；其中可见的 root 只有 `story / adventure / husbandry` 三个 —— 正是玩家看到的三个原版标签。本模组 4 个 root 当时一个都没点亮，于是全军覆没。

**为什么上游没这问题**：1.20.1 换成了 `AdvancementVisibilityEvaluator` —— 没有 parent 的节点判为 `ALWAYS_SHOW`，再配合 `VISIBILITY_DEPTH = 2`，等价于「root 及其往下两代可见」，且从 root 出发整棵树遍历。1.18.2 既没有 `ALWAYS_SHOW`，入口也不是 root。**数据文件与上游逐字相同**，所以这是版本行为差异，不是移植搬错。

**修法**：新增 `mixin/PlayerAdvancementsMixin`，精确复刻 1.20.1 的规则，并且**只作用于 `touhou_little_maid` 命名空间**：

- 注入 `shouldBeVisible` 的 `HEAD`：沿 parent 链向上最多 2 层，摸到本模组的 root 就返回 `true`（= 补上 `ALWAYS_SHOW`）；
- 注入 `load` 的 `TAIL`：从本模组每个 root 出发，按同样的 2 层深度把节点写进 `visible` / `visibilityChanged`，让首次同步包把它们带上。登录与 `/reload` 都会走到 `load`，所以两条路径都覆盖。

限定命名空间是必须的：原版 `minecraft:recipes/root` 有 display 却永远完不成，若对所有命名空间套用 `ALWAYS_SHOW`，进度页会凭空多出一个「配方」标签。

**验证（本轮实测数字）**

- 修复前：该专用服务器存档的可见集预测 **23** 条、模组 root 全部不可见；修复后预测 **63** 条（23 原版 + 40 条深度 0~2 且带 display 的模组进度）。
- 实跑 `runServer` + `runClient -PjoinServer`，客户端日志 `Loaded 63 advancements` —— **与预测逐字吻合**。
- RCON `reload` 之后客户端再次 `Loaded 63 advancements`，说明 `/reload` 路径也生效。
- 零 mixin 应用失败、零 `Server informed client about progress for unknown advancement`。
- RCON 授予两条模组进度后 `save-all`，玩家存档里正好多出这 4 条（`base/craft_gohei`、`maid_base/switch_task` 加上原本就完成的 `give_smart_slab`、`grant_book_on_first_join`）—— 说明「可见」不等于「凭空造进度」，存档没有膨胀。

**修复后的观感（与上游 1.20.1 一致）**

- 进度页一开始就有 4 个模组标签：基础 / 女仆 / 好感度 / 挑战。
- 每个标签里露出 root 及其往下两代节点（未解锁的显示为灰色问号，这是 vanilla 自身行为），更深的节点要等前序完成才出现 —— 这正是 1.20.1 的 `VISIBILITY_DEPTH = 2` 语义。
- 标签位不紧张：1.18.2 的 `AdvancementTabType` 四个方向各 8 位，共 32 位。

**验证边界**

- 没有做真人点击验收。自动化只能验证到「同步集里包含 4 个 root」，而 `AdvancementsScreen.init` → `setListener` → `onAddAdvancementRoot` 的链路已用字节码反汇编逐行确认（root 带 display 就会建出标签页）。
- 更深层节点（深度 3、4，共 12 条）要等前序解锁后才可见，本轮没有逐条点亮验证。

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

## 配方补全与 REI 联动

### 先做一次盘点：真正缺的只有两条

把 `InitItems` 里注册的 69 件物品与 47 条配方的产出逐一对照，再把两份上游 jar 的
`data/touhou_little_maid/recipes/` 与之对比，结论是：

| 缺口 | 1.18.2 | 1.20.1 | 处理 |
|---|---|---|---|
| `entity_id_copy` | 有 | 有 | **本轮补上**（物品 + 配方 + 右键行为） |
| `altar/snack_cabinet` | **没有** | 有 | **本轮补上**（整块方块系统 + 配方） |

其余 25 件「没有配方」的物品，上游本来也没有：刷怪蛋、8 件进度图标、三份魂符、
三份残局道具、好感度工具、手办、照片、P 点、神社、替身地藏、模型切换器、
「生成？？？」—— 它们分别靠创造物品栏、战利品、祭坛/特殊交互或干脆是内部占位物品获得。
所以「补全配方」这件事的真实工作量就是上面两行。

### `entity_id_copy`：资产早就在，只缺注册

baseline 里已经有 `models/item/entity_id_copy.json`、`textures/item/entity_id_copy.png`，
以及三条语言键（物品名 / tooltip / 复制成功提示）。缺的只有两样：

1. **物品注册** —— 没有物品的配方会被数据包解析成「未知物品」整条丢弃，这也是之前那条
   `recipes/entity_id_copy.json` 一直没补的直接原因；
2. **右键行为** —— 上游是 Forge 的 `PlayerInteractEvent.EntityInteract`，只在该客户端执行；
   Fabric 上换成 `UseEntityCallback`（与 `MaidClientInteractEvents` 同一套路），
   复制 `Registry.ENTITY_TYPE.getKey(entity.getType())` 到剪贴板并在聊天栏回一行提示。

返回 `SUCCESS` 而不是 `PASS` 是必须的：否则对着女仆右键会顺手把女仆界面打开。

### `snack_cabinet`：1.20.1 新增方块，需要整块补

它是 1.20.1 才有的内容，1.18.2 的 jar 里连类都没有。baseline 拷了它的基岩模型 / 贴图 /
物品模型，但方块、方块实体、界面全缺 —— 于是那条祭坛配方无从落地。本轮补齐：

| 文件 | 作用 |
|---|---|
| `block/BlockSnackCabinet` | 方块本体（`FACING` + `TYPE`，`ENTITYBLOCK_ANIMATED`） |
| `tileentity/TileEntitySnackCabinet` | 27 格容器，带开合音效与战利品表支持 |
| `client/renderer/tileentity/TileEntitySnackCabinetRenderer` | 基岩模型渲染（复用 `BedrockBlockModels`） |
| `inventory/chest/SnackCabinetType` | 让隙间（无线 I/O）能把它当目标 |
| `blockstates/` + `models/block/snack_cabinet.json` | 从上游逐字节拷（只有 particle，几何在渲染器里） |
| `loot_tables/blocks/snack_cabinet.json` | 拆掉掉自己 |
| `recipes/altar/snack_cabinet.json` | 从上游逐字节拷 |
| `data/c/tags/items/glass_panes.json` | 配方要 `c:glass_panes`，仓库原来没有这个标签 |

**界面不需要自己写**：27 格正好是原版六行箱子的容量，所以 `createMenu` 直接返回
`ChestMenu.sixRows(...)`，客户端也不用注册 Screen —— 原版 `MenuScreens` 早就把
`MenuType.GENERIC_9x3` 绑到了 `ChestScreen`（重复注册只会多一条无用表项）。

**踩到的一个 1.18.2 差异**：`ContainerOpenersCounter#incrementOpeners` 在 1.18.2 只有 4 个参数，
第 5 个（`viewDistance`）是 1.19 才加的。照抄 1.20.1 的写法会编译不过。

### REI 联动

移植自上游 `compat/rei/` 那 4 个类（1.18.2 与 1.20.1 的 jar 里都有，源码在 `port/upstream`）：

| 类 | 作用 |
|---|---|
| `MaidREIClientPlugin` | 入口：注册分类 / 配方 / 折叠条目 / 排除区 |
| `ReiAltarRecipeCategory` | 「祭坛合成」分类的画布（3×2 戒指形，160×125） |
| `ReiAltarRecipeDisplay` | 一条配方在 REI 里的数据载体 |
| `ReiAltarRecipeMaker` | 把 `altar_crafting` 配方全量搬进 REI |
| `util/JERIUtil` | 配方 → 条目的公共转换（上游抽出来给 JEI/REI 共用） |

**Fabric 没有 `@REIPluginClient` 注解** —— 那是 REI 的 Forge 版扫类路径用的。
Fabric 侧走入口点：把 `MaidREIClientPlugin` 登记在 `fabric.mod.json` 的
`"rei_client"` 入口点里，REI 启动时自己来取。没装 REI 时没有东西会取它，
所以 **REI 是可选依赖** —— 不写进 `depends`。

### 依赖：三个 jar 必须显式放上 classpath

REI 8.4.778 的 `fabric.mod.json` 里**只声明了 minecraft**，但它的核心类直接引用
`dev.architectury.platform.*`（84 个类）与 `me.shedaniel.math.*`（Point / Rectangle）。
这两个包既不在 REI 的 jar 里，也不在它的依赖声明里，所以编译期必须自己补：

- `architectury-4.12.94-fabric.jar`（1.18.2 版；工作区里现成有）
- `basic-math-0.6.1.jar`（`me.shedaniel.math` 的 7 个类）

三个 jar 都按仓库既有约定放进 `libs/` 钉死版本，用 `modCompileOnly` + 
`modLocalRuntime`（后者只影响 `runClient`，不进最终 jar）。

### 验证

- `./gradlew.bat build` = BUILD SUCCESSFUL；四道闸全绿
  （`SCANNED_FILES=1582 BAD_FILES=0`、`block entity types = 14 renderers = 12 / ALL OK`、
  `entries=54 categories=3 problems=0`）。
- jar 内 17 项关键产物全部就位（5 个 REI 类、2 个 entity_id_copy 类、5 个零食柜类、
  4 份数据文件、2 份资源文件 —— 逐个核对过）。
- **REI 真的认到了我们**：客户端日志里
  `[REI] Registered plugin provider MaidREIClientPlugin [touhou_little_maid] for REIClientPlugin`，
  且 `REIClientPlugin starting main-reload` 的插件列表里能看到它（优先级 0.00）。
- **物品与配方真的存在**（RCON，有玩家在线时）：
  `give Dev touhou_little_maid:entity_id_copy 1` → `Gave 1 [item.touhou_little_maid.entity_id_copy]`；
  `give Dev touhou_little_maid:snack_cabinet 1` → `Gave 1 [block.touhou_little_maid.snack_cabinet]`；
  `recipe give Dev touhou_little_maid:entity_id_copy` → `Unlocked 1 recipes`。
- **零食柜能放、方块实体在**：`setblock ... touhou_little_maid:snack_cabinet` → `Changed the block`，
  随后 `data get block` 读到 `Items: []` 与正确的 `id`。
- **模型加载成功**：`[Futo] Block model snack_cabinet loaded: 3 bones, bbox y[0.000..2.000]`，
  客户端零 `Missing texture`、零渲染异常、零 crash-report。

### 验证边界

- **没有做视觉验收**：REI 的祭坛分类画布长什么样、零食柜在游戏里好不好看，都要真人看。
- `recipe give` 对祭坛配方回 `No new recipes were learned` 是**预期的** ——
  那个命令只认原版配方书类型（crafting / smelting …），`altar_crafting` 是自定义类型、
  本来就不进配方书。要确认祭坛配方在不在，看的是「零 `Parsing error`」与 REI 里的分类。
- **上游还有两段 REI 功能没搬**：`registerScreens`（背包界面的「点一下切到配方」热区）与
  `registerTransferHandlers`（把配方一键摆进工作台/熔炉背包）。它们依赖
  `CraftingTableBackpackContainerScreen` / `FurnaceBackpackContainerScreen` ——
  本移植版还没有那两套界面（背包目前是挂在实体上的数据容器，没有对应 GUI 与 Menu）。
  等界面做出来，把上游那 4 行 handler 注册补回来即可，插件结构不用改。
- 零食柜的 `TYPE` 属性（空 / 满 / 半）只做了 state 与默认值，
  **没有实现「蹲下点顶面往上叠一个半高柜」** 那段传染逻辑。它只影响外观分层，
  不影响「27 格能装、能开界面、能被隙间当目标」这些核心功能。

## 装进正式实例：两处「开发环境能跑、真 jar 崩」的坑

用户把 jar 丢进 HMCL 实例后客户端在**入口点阶段**就崩（附带的崩溃报告：
`minecraft-exported-crash-info-2026-11-24T22-24-42.zip`）。这类问题的共同点是
**`gradlew runClient` 永远发现不了** —— 那里 classpath 由 Gradle 拼、资源是
`build/resources/main` 的真实目录。修了两处。

### 1. 纯 Java 库根本没进 jar

```
Caused by: java.lang.NoClassDefFoundError: org/yaml/snakeyaml/Yaml
    at ...ai.manager.setting.AvailableSites.readSites
    at ...config.subconfig.AIConfig.init
```

`snakeyaml` / `concentus` / `vorbis-java-core` 三个库一直是用
`implementation files("libs/...")` 引入的。`implementation` **只把它们放到 Gradle 的
runtimeClasspath 上**，也就是只对开发环境有效，`remapJar` 不会带上它们。

为什么不能改用 Loom 的 `include`：那会把 jar 塞进 `META-INF/jars` 交给 Fabric Loader
当作**独立 mod** 加载，而这三个 jar 里都没有 `fabric.mod.json`，加载器会直接拒绝。

所以现在在 `jar` task 里用 `from(zipTree(...))` 把三个库的类直接合并进主 jar，只排除
「合并后会坏掉」的那几类（`MANIFEST` / 签名 / `module-info`），`META-INF/maven/**`
（内含各自 pom 与 LICENSE）**保留** —— 那是版权声明。

产物因此从 39.7 MB 变成 40.6 MB。

### 2. 遍历 jar 内资源时调了 `Path#toFile()`

```
Caused by: java.lang.UnsupportedOperationException
    at jdk.zipfs/jdk.zipfs.ZipPath.toFile
    at ...ai.manager.setting.SettingReader$1.visitFile(SettingReader.java:161)
```

`SettingReader#readConfigSetting` 用 `Files.walkFileTree` 扫
`ModContainer#findPath("assets")/.../settings/*.yml`，然后对每个文件调 `file.toFile()`。
在开发环境里那是真实目录，能 `toFile()`；**装进实例后这些路径位于模组 jar 内部（zipfs）**，
`ZipPath#toFile()` 直接抛 `UnsupportedOperationException`。

改成 `Files.newInputStream(file)`（`CharacterSetting` 本来就有 `InputStream` 构造器）。
顺手把 `readCustomPack` 里的 `folder.toFile().isDirectory()` 换成 `Files.isDirectory(folder)`。

### 验证方式（这次真的在正式实例里启动过）

不能只用 `runClient` 验证。这次是**直接拿用户实例的启动命令跑**：

1. `port/deploy-jar.ps1` 把新 jar 拷进 `D:\MCHMCL\.minecraft\versions\1.18.2-Fabric-test\mods`
   （旧 jar 自动备份到同级的 `mods_backup_tlm`，sha256 双向校验）；
2. 用工单里那份 `launch.bat` 的完整命令行启动 —— 注意 HMCL 导出的命令里
   access token 是 `<access token>` 占位符，**尖括号会被 cmd 当成重定向**，
   必须先替换成合法 token，否则会得到「系统找不到指定的文件」而不是真正的崩溃；
3. 轮询该实例的 `logs/latest.log`。

结果：日志出现 `Sound engine started`，且

```
[Futo] AI sites in use: 0 chat / 1 tts (enabled only)     <- snakeyaml 读到了 available_sites.yml
[Futo] loaded 6 character settings for AI chat            <- SettingReader 读到了 jar 内 6 份 yml
[Futo] Touhou Little Maid initialized.
```

`NoClassDefFoundError` / `UnsupportedOperationException` / `Could not execute entrypoint`
全部为零。之后用 PID 精确关掉了这次启动的客户端（只匹配
`1.18.2-Fabric-test` + `KnotClient`，不碰其它 java 进程）。

### 仍然需要用户自己装的模组

| 模组 | 原因 |
|---|---|
| Fabric API | `depends` 已声明 |
| ForgeConfigAPIPort | `depends` 已声明（它自带 night-config） |
| Cloth Config | `suggests`；不装则没有配置界面 |
| Patchouli | `suggests`；不装则没有手册（也会有下载提示） |
| REI + Architectury | 都装了才会点亮「祭坛合成」分类 |

**不再需要**额外放 snakeyaml / concentus / vorbis —— 它们现在在 jar 里。

