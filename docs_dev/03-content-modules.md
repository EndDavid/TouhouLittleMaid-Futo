# 内容模块

> 移植过程笔记。摘自根目录 `README.md`（2026-11-25 拆分），返回索引：[`docs_dev/README.md`](README.md)。

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
  `leftPos+251 / topPos+37`，按钮 26x24、槽位间隔 25（上游 `MaidSideTabs.SPACING`），
  图标 UV `(193, 107 + 25*槽位 + 4)`。框架在 `maid_gui_side.png` 里是
  **两个 21x24 的圆角格**（V 107–130 与 132–155，中间第 131 行是空的），
  整块按 `21x50` 一次画完（上游 `drawSideTabGui` 同样整块画；只画上半截会在下面留一个空槽）。
  - 槽位 0 = 任务手册：点击触发 `OpenPatchouliBookEvent`，未装 Patchouli 时聊天栏给下载提示
    （上游这里是弹警告界面，本移植版按需求改成聊天栏）。
  - 槽位 1 = 全局配置：`ClothConfigCompat.isAvailable()` 为真则开 Cloth 配置界面，
    否则开 `client/gui/mod/ClothConfigScreen`（一页说明 + 下载按钮 + 返回）。
    **这条路径不是聊天栏提示**，与手册按钮不同 —— 上游此处就是整页说明。
    判断必须用 `isModLoaded("cloth-config")`：没装时 `ClothConfigCompat#openConfigScreen`
    里引用的 `me.shedaniel.*` 会抛 `NoClassDefFoundError`（`Error`，不是 `Exception`）。
  - 两个按钮都始终显示（缺前置时点了才提示），与上游一致；`cloth-config` 在
    `fabric.mod.json` 里是 `suggests` 而非 `depends`，所以「没装 Cloth」是正常状态。

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

## 在线模型包下载界面

上游的「资源包下载」是纯客户端的：女仆界面左下角一个 41x20 的按钮，点开是一个
420x240 的自绘界面，里面有一个 `info.json` 清单、按页签分类、按关键词搜索、
下载/更新/删除、以及 CRC32 驱动的「已安装」判定。整套照搬过来了。

### 协议：一份静态 json，不是 REST

整条链路只有两个 GET：

| 请求 | 内容 |
|---|---|
| `https://tlmdl.cfpa.team/info.json` | JSON **数组**，每项一个资源包（169 条） |
| `https://tlmdl.cfpa.team/<url>` | 包体本身（普通 zip，`url` 是相对路径） |

主站不通时自动降级到 `http://tlm.cfpa.team:29434/`，并**记住这次降级**，
之后的包体也从备用源下载 —— 只换清单不换包体会出现「一半能装一半装不上」。

`info.json` 里一条记录的关键字段（`DownloadInfo` 上的 `@SerializedName` 逐字对应）：
`file_name` / `file_size` / `checksum`（zip 的 **CRC32**）/ `upload_time`（epoch ms）/
`name`（语言键）/ `version` / `url` / `desc` / `author` / `keyword` / `old_version` /
`type`（`maid` / `chair` / `sound`）/ `license` / `website`。

实体数据核对过（`run/config/touhou_little_maid/info.json`，145928 字节）：

```
type: list  entries: 169
by type: sound 67 / maid 144 / chair 22       （同一条可以属于多种）
missing fields: none
```

其中 `license` 只有 3 条带这个键，其余 166 条缺键 —— Gson 不会覆盖字段默认值，
所以它们显示成默认的 "All Right Reserved"，不会出现 "null"。

### 本地状态不靠「记住」，靠 CRC32

`checksum` 是包体 zip 的 CRC32。界面每次打开都扫一遍 `tlm_custom_pack/*.zip` 的真实
CRC32，与清单比对：

- 命中 `checksum` → **已下载**（卡片压暗一层）；
- 命中 `old_version` 里的某个值 → **需更新**（右上角画一个角标，页签里会归到「更新」）；
- 都没中 → 未下载。

所以手删文件、手动放进一个旧版本 zip 都能被正确识别，不需要任何额外状态文件。

### 下载 → 安装的四步

```
1. 下载到 <游戏目录>/config/touhou_little_maid/file/<file_name>   （缓存）
2. ZipFileCheck.isZipFile(缓存)                                   （验 EOCD）
3. 复制到 <游戏目录>/tlm_custom_pack/<file_name>                   （安装）
4. CustomPackLoader.reloadPacks()                                 （重载模型）
```

缓存目录与安装目录分开是上游的结构：删掉已安装的包之后还能从缓存一键装回来。
第 2 步不能省 —— 断线下载出来的半个文件没有 EOCD（末尾 22 字节的 `0x06054b50`），
直接复制进 `tlm_custom_pack` 会让下次启动扫包时炸一次、并留下一个永远装不上的坏包。

### 线程：`reloadPacks()` 自己保证在主线程

`HttpUtil.downloadTo` 的完成回调跑在 **IO 线程**，而重载里有
`TextureManager#register`（普通 `HashMap`）与 `RenderSystem` 调用。所以
`CustomPackLoader#reloadPacks()` 里先看 `Minecraft#renderOnThread()`（1.18.2 对应
1.19+ 的 `isSameThread()`），不在主线程就 `minecraft.execute(...)` 排到下一 tick，
调用方不必自己判断。

### 外部模型包：三个注入点，而不是重写加载链

本移植版的内置模型包是**构建期展开成普通资源**的，一直走 `ResourceManager`。
但玩家下载的包是磁盘上的 zip，`ResourceManager` 看不见 —— 不做这一层的话，
「下载成功」之后模型列表里一个都不会多。接法只有三处：

1. `GeckoModelLoader#reload()` 末尾调 `CustomPackLoader.loadExternalMaidPacks()`，
   `ChairModelLoader#reload()` 末尾调 `loadExternalChairPacks()`；
2. `GeckoModelLoader#open(ResourceLocation)` 加一层兜底：`ResourceManager` 找不到时
   问 `CustomPackLoader.openExternal(...)`。几何、动画、图标全走那一个方法，
   所以只改一处就让整条管线都能读 zip；
3. 贴图单独注册成动态纹理（`client/renderer/texture/PackTexture`）——
   贴图不走 `ResourceManager`，而是 `TextureManager`。

优先级是 **内置资源 > 外部包**：`open()` 先问 `ResourceManager`。
后果是外部包用了与内置包相同的域名时会被内置那份盖住（上游同样是「默认包强行覆盖」），
日志里留一条 warn 并跳过重名模型，免得同一个 id 在列表里出现两次。

`scan()` 每轮重载只做一次（`ModelReloader#reloadAll` 开头），女仆 / 坐垫两边复用结果。
包来源抽象成 `PackSource`，zip 与文件夹两种实现 —— 文件夹形态是开发时最常用的
（改一个 json 重启就生效）。

### 界面版式（与上游逐像素一致）

```
整体         420x240，居中
顶部页签     52x20，间距 2，y+3       全部 / 女仆 / 坐垫 / 声音 / 更新
关闭按钮     20x20 @ (x+400, y+2)
列表        每页 4 行，268x46，首行 y+26，行距 48
右侧上框    (x+270..x+420, y+26..y+72)   选中的包名
右侧下框    (x+270..x+420, y+74..y+216)  搜索框 + 热门搜索
翻页        < @ (x, y+218) 40x20 ；> @ (x+228, y+218) 40x20
页码        居中于 x+134, y+227
打开文件夹  (x+270, y+218) 150x20
```

三处「看起来像状态、其实是重算」：

- 已下载 / 需更新：CRC32 比对，见上；
- 搜索 / 页签 / 翻页：全部**重建控件**（`clearWidgets()` 之后重加一遍）。
  比维护一堆可见性开关更不容易漏 —— 上游同款做法；
- 下载进度：`DownloadInfo` 自己实现 `ProgressListener`，下载线程往里写百分比，
  `PackInfoButton` 每帧读它画橙色进度条，界面这一侧没有任何轮询。

### 按钮角标：九宫格气泡，不是一行字

「点击此处下载更多模型包」有 20 个汉字，直接画会同时压住 home / pick / ride 三个按钮，
而且没有任何「这个按钮在说话」的视觉提示。上游 `util/TipsHelper` 用
`maid_gui_button.png` 里三套九宫格（1/2/3 行高，`v = 128/149/180`）拼一个带尾巴的气泡，
尾巴对准按钮右下角，每 400 ms 上下跳 1 像素。已逐像素核对过这三块都是
「左上角 1 像素圆角」的实心矩形。

移植时修掉上游两个疏漏：

- **`PoseStack` 泄漏**：上游在 `pushPose()` 之后才判「文字为空就 return」，那条路径没有
  `popPose()`，每帧给全局矩阵栈留一层；
- **`disableDepthTest` 不恢复**：气泡画完没有 `enableDepthTest()`，之后同帧的绘制全部
  失去深度关系。本移植版补上。

### 与上游的四处差异（都有原因）

| 上游 | 本移植版 | 原因 |
|---|---|---|
| `commons-codec` / `commons-io` 算 MD5 / CRC32 | `MessageDigest` / `java.util.zip.CRC32` | Fabric 侧不保证这两个库一定在；自己算十行代码 |
| `X-Forge-Version` 请求头 | `X-Fabric-Version` | 取 Fabric Loader 版本，语义一致 |
| `type.size()` 当类型数量 | `new HashSet<>(type).size()` | 实测 169 条里 7 条的 `type` 有重复值（如 `["sound","sound","maid"]`），直接取 size 会把两种类型画成「三种合集」的图标 |
| 关闭按钮直接回世界（1.18.2） | 发 `sendTab(MAIN, entityId)` 让服务端重开女仆界面（1.20.1 的行为） | 从容器界面点进来的玩家不该被踢到世界里。**ESC 仍是回世界**，与两个上游版本都一致 |

### 验证到什么程度

`./gradlew.bat runClient -PdownloadSelftest` 端到端跑通，全部由日志断言：

```
[Downloader 0/INFO] [Futo] resource pack list downloaded (169 entries)
[Render thread/INFO] [Futo] download selftest: opening ModelDownloadGui
[Render thread/INFO] [Futo] download selftest: ModelDownloadGui opened with 169 entries
```

- 清单拉取成功，169 条全部解析（`run/config/touhou_little_maid/info.json` 145928 字节）；
- `tlm_custom_pack/` 被自动创建（空目录，无 zip，所以模型数仍是内置的 194 个女仆 / 58 个坐垫，
  与引入本功能之前一致 —— 外部包加载路径没有误伤内置包）；
- 界面构造 + `init()` + `render()` 跑完若干帧，零异常、零 crash-report；
- 守卫脚本全绿（encoding 1617 files / 0 bad、renderers ALL OK、jar checked、dangling checked）。

`-PdownloadSelftest` 是本轮新加的 Gradle 开关（与既有的 `-PclothSelftest` 同风格）：
它会带上 `-Dfuto.download.selftest=true`，客户端在标题画面稳定 60 tick 后自动
`setScreen(new ModelDownloadGui())`。**为什么需要它**：这个界面不属于任何 `MenuType`，
没有「进世界再点」之外的入口，而它自己画的东西很多（气泡、进度条、`EditBox`、
类型图标），只靠 `build` 绿是验不到运行时异常的。不带该属性时行为完全不变。

**没验证到的**：

- **视觉观感**（列表排版、图标是否与内容相符、搜索框位置、气泡尺寸）需要真人看；
- **实际下载一个包**没做 —— 那会真的往 `tlm_custom_pack` 写 20+ MB 文件。
  下载链路的每一步都单独核对过（协议字段、CRC32 比对、zip 校验、复制、重载），
  但没有端到端跑过一次真实下载；
- 「需更新」状态没有真实样本可测：本地没有旧版本的 zip，
  只有 `old_version` 字段的解析被验证过。

## 外部模型包的译文：两张表 + 一层 mixin
界面上那一行「名字」不是文案，是**键**：`maid_model.json` 里写
`"name": "model.<域>.<名字>.name"`，在线清单 `info.json` 里写
`"name": "tlm.yingbai_arknights_pack.name"`。真正的译文在另外两个地方，
而**原版一个都读不到**：
| 来源 | 位置 | 原版为什么读不到 |
|---|---|---|
| 在线清单 | `info.json` 每条记录的 `language` 字段（`zh_cn` / `en_us` → 键值表） | 它不是资源包，只是一份下载目录 |
| 模型包内 | `assets/<域>/lang/<语言>.lang`（旧式 `键=值` 文本） | `ResourceManager` 只认资源包里的 `.json`；`tlm_custom_pack/*.zip` 连资源包都不是 |
不补这一层，界面上就是截图里那种 `tlm.yingbai_arknights_pack.name`。
### 接法：补一张表，不动语言系统
- `client/resource/LanguageLoader`：`语言代码 → (键 → 译文)` 的缓存，三个写入口
  （清单的 `language` 字段、zip 内的 `.lang`、文件夹包内的 `.lang`）；
- `mixin/client/LanguageMixin`：在 `ClientLanguage#getOrDefault(String)` 与 `#has(String)`
  的 `HEAD` 处各注入一次，**只在命中缓存时** `setReturnValue`。
选 `ClientLanguage` 是因为 `LanguageManager` 加载语言时会把 `ClientLanguage`
`Language.inject(...)` 进 `Language.getInstance()`，于是 `Component.translatable(...)`
的解析全部经过它 —— 界面代码（`PackInfoButton` / `ModelDownloadGui`）一行都不用改。
1.18.2 的 `Language` 只有 `getOrDefault(String)` 与 `has(String)` 两个抽象方法，
`ClientLanguage` 两个都实现了（`javap` 核对过），所以注入点只需要这两个。
「查不到就不拦」是关键：没命中就让原方法继续跑，模组自己的
`assets/touhou_little_maid/lang/*.json`、其它模组、资源包的行为完全不变。
当前语言没有该键时回退查一次 `en_us`，与上游一致 —— 清单只提供中英两版时，
德语 / 日语客户端上至少是完整的英文名，而不是原始键。
上游只注了 `getOrDefault`，这里把 `has` 也注上：有些路径会先问「这个键存在吗」，
两条路不一致的话会出现「文字对了但 `has` 说没有」。
### 生命周期与顺序
```
ModelReloader#reloadAll
  CustomPackLoader.scan()                      ← 先扫一遍 tlm_custom_pack
  LanguageLoader.clear()                       ← 与模型缓存同寿命，删包后名字才不会残留
  GeckoModelLoader.reload() / ChairModelLoader.reload()
  LanguageLoader.loadDownloadInfoLanguages()   ← ① 在线清单
  CustomPackLoader.loadExternalLanguages()     ← ② 包内 .lang，可覆盖 ①
```
顺序是有意的：**包内 `.lang` 后登记**，同一个键由「包自带的翻译」胜出。
清单是异步拉的，`InfoGetManager` 解析完 `info.json` 会**立刻**再登记一次 ①——
否则「下载界面要等下一次模型重载才有译名」，而那次重载早就跑完了。
清单下载与模型重载是两个互不相干的时间点，这正是截图那个 bug 的成因：
开发环境里那次重载恰好还排在后面，看着像「能用」，实际部署到实例里就是原始键。
### 线程
三个写方：资源重载线程（`ModelReloader`）、`HttpUtil` 的 IO 线程（清单落地）、
渲染线程（下载完成后的重载）。读方是渲染线程与文字排版，频率极高。
所以缓存是 `ConcurrentHashMap`，且**只**在 `reloadAll` 入口 `clear()` 一次 ——
遍历中清空会读到半个表。上游用普通 `HashMap`，这里是刻意改的。
### 与上游的差异
| 上游 | 本移植版 | 原因 |
|---|---|---|
| `commons-io` 的 `IOUtils.readLines` / `FilenameUtils` | `BufferedReader` + 正则 | Fabric 侧不保证有 commons-io |
| `LANG_CACHE` 是 `HashMap` | `ConcurrentHashMap` | 写读跨线程（见上） |
| `getLanguageMap` 只收 `HashMap<String, HashMap<String, String>>` | 放宽成 `Map<String, ? extends Map<String, String>>` | 清单、包内 `.lang`、未来的内置表三条路共用一个入口 |
| 只注 `getOrDefault` | `getOrDefault` + `has` | 见上 |
| — | `.lang` 首行 BOM 会剥掉 | 带 BOM 的文件会让第一条键多一个不可见字符，那条译文永远查不到 |
| — | 空行直接跳过 | 上游只跳 `#` 注释，空行靠 `split` 长度兜住 —— 行尾带空格时会多出一条空键 |
### 三个容易踩的点
1. **zip 里必须有目录条目**。`ZipSource#domains()` 用的是上游同款正则
   `^assets/[\w.]+/$`，匹配的是文件夹条目；打包工具若没写 `assets/x/` 这种条目，
   域名列表是空的，`.lang` 与 `maid_model.json` 都扫不到。
   （这是既有行为：内置包与在线下载的包都带目录条目。）
2. **只认两段式语言代码**：`^assets/<域>/lang/(\w{2}_\w{2})\.lang$`。
   `zh_CN.lang` 能匹配（最后统一转小写），`zh_cn.json` 不会 —— 上游同款正则，
   模型包里放的是 `.lang` 而不是 `.json`。
3. **外部 `.lang` 可以盖住模组自己的键**（例如某个包写了 `gui.touhou_little_maid.xxx`）。
   上游就是这个行为，因为 mixin 的优先级高于 JSON。清单与 `.lang` 都是第三方内容，
   装了什么包就得承担什么。
### 验证到什么程度
`./gradlew.bat runClient -PdownloadSelftest` 现在打两次探测：清单落地时一次、
打开界面前一次（那时重载已跑完）。第二次的 `cache` 减去第一次，就是「外部包 `.lang`
读进来几条」：

```
[Futo] download selftest[after download]:     tlm.yingbai_arknights_pack.name -> 映白模型包        (lang=zh_cn, cache=338)
[Futo] found 2 custom model pack(s) in ...\run\tlm_custom_pack
[Futo] download selftest[after model reload]: tlm.yingbai_arknights_pack.name -> 来自外部包语言文件 (lang=zh_cn, cache=340)
[Futo] download selftest: ModelDownloadGui opened with 169 entries
```

那两个包是临时合成的（放在 `run/tlm_custom_pack/`，跑完即删，`run/` 不入库）：

| 包 | 形态 | `zh_cn.lang` 里写的东西 |
|---|---|---|
| `futo_langtest.zip` | zip，含 `assets/futo_langtest_zip/` 目录条目 | `model.futo.langtest.zip.name=压缩包里的译文` |
| `futo_langtest_folder/` | 展开目录 | `tlm.yingbai_arknights_pack.name=来自外部包语言文件`、`model.futo.langtest.folder.name=文件夹中的译文`、`subtitle.touhou_little_maid.other.credit=…` |

`338 → 340` 说明两条路各读进 1 条；`subtitle.touhou_little_maid.other.credit`
没有计入 340，`REMOVE_KEY` 的过滤生效。第三条日志里的译文从「映白模型包」变成
「来自外部包语言文件」：文件夹包的文件名排在 zip 之后（`futo_langtest.zip` 的 `.`
小于 `futo_langtest_folder` 的 `_`），登记得更晚，于是盖住了清单里的同名键 ——
证明的是**值真的走到了界面用的那条解析路径**，而不是「表里有这个键」。
- 构建绿；`check-encoding` 1620 files / 0 bad；`check-jar` checked；
- 产物里 `mixins.json` 含 `client.LanguageMixin`；`LanguageMixin.class` 的 `@Mixin` 目标
  已静态重映射成 `net/minecraft/class_1078`、方法名成 `method_4679` / `method_4678`
  （`getOrDefault` / `has`），所以生产环境不靠 refmap 也能命中 —— 这一点必须查，
  否则「开发环境好用、装进实例就失效」；
- 部署到 `1.18.2-Fabric-test`，sha256 两侧一致。
**没验证到的**：
- **视觉观感**：界面里那一行名字会不会被截断、与版本号是否重叠，需要真人看；
- **真实的第三方包**：合成包只覆盖「读得到、值对、能覆盖」，没覆盖
  「20 MB 的包 + 几百个模型 + 多语言文件」这种规模；
- 多注的那条 `has` 在真实场景下没有回归样本，只是两条路语义一致，风险低。

## Jade 联动（女仆 / 神社灯提示行）

Jade 是 Fabric 1.18.2 上的 TOP 等价物：同一套 API，只是包名从
`mcp.mobius.waila.api.*` 换成了 `snownee.jade.api.*`。上游的 `compat/jade` 很小，
三个文件加起来 95 行：

| 类 | 作用 |
|---|---|
| `compat/jade/JadePlugin` | 入口：把两个 provider 注册到 `EntityMaid` / `BlockMaidBeacon` |
| `compat/jade/provider/MaidProvider` | 女仆：任务 / 日程 / 好感度等级 / 距下一级还差多少点 / 永久无敌 |
| `compat/jade/provider/ShrineLampProvider` | 神社灯：已存电力 + 当前效果每秒消耗 |

文案键沿用上游的 `top.touhou_little_maid.entity_maid.*` 与
`tooltips.touhou_little_maid.maid_beacon.desc` / `gui.touhou_little_maid.maid_beacon.cost_power`
（各 lang json 里本来就有，是当年 TOP 联动留下的），**不需要新增译文**。

### 注册机制：Fabric 走入口点，不是注解扫描

Forge 版靠 `@WailaPlugin` 扫类路径。Fabric 版**完全不读这个注解** ——
Jade 5.100.0 的 `snownee.jade.Jade#loadComplete()` 里是：

```java
FabricLoader.getInstance().getEntrypointContainers("jade", IWailaPlugin.class)
```

所以 `JadePlugin` 登记在 `fabric.mod.json` 的 **`"jade"` 入口点**里。没装 Jade 时
没有东西会来取它，于是 **Jade 是可选依赖**：只写 `suggests`，不写 `depends`。

顺带两个坑（都是从 jar 字节码里核出来的，不是猜的）：

- `snownee.jade.api.WailaPlugin` 在这个版本的签名是 `String value()` 且**没有默认值** ——
  照抄上游的 `@WailaPlugin`（无参）会直接编译失败；而且 Fabric 侧根本不用它，所以不写。
- 上游写的是 `registerComponentProvider(provider, TooltipPosition.TAIL, Class)`，
  这个 API 在 5.x 已经不存在了。现在只有
  `registerBlockComponent(IBlockComponentProvider, Class)` /
  `registerEntityComponent(IEntityComponentProvider, Class)`；`TooltipPosition` 退化成一个
  纯常量类，行的顺序由 provider 的 `getDefaultPriority()` 与 `ITooltip#add` 的调用顺序决定。
  写法与 TLMO 1.20.1 Fabric 版（`_ref/orihime-1201`）逐行一致。

### 依赖怎么写

`libs/Jade-1.18.2-fabric-5.100.0.jar` 是 **intermediary 命名**的（类引用是
`net.minecraft.class_1297` 这种），所以必须用 `modCompileOnly` / `modLocalRuntime`：
Loom 会先把它重映射到本仓库的 official mappings 再上 classpath。
写成普通 `compileOnly files(...)` 会因为类名对不上，在 javac 解析 API 签名时就崩。

### 与上游的三处差异（都有原因）

1. **`AddJadeInfoEvent` 没有移植**：上游在「任务」行之后 post 这个 Forge 事件，
   让附属模组往提示里插自己的行。本移植版没有附属模组扩展体系，`MaidProvider`
   里留了注释与空位，将来要加就是一行回调。
2. **「永久无敌」那一行不会显示**：上游读的是自己同步的 `DATA_INVULNERABLE`
   （`Entity#setInvulnerable` 只改服务端字段，不下发客户端；替身地藏就是靠这个同步字段
   让客户端看得见的）。本移植版没有这个同步字段，这里直接读 `Entity#isInvulnerable()`，
   客户端恒为 `false`。要补的话是单独一件事（给 `EntityMaid` 加一个 entity data 字段 +
   覆写 `setInvulnerable`），不在本轮范围。
3. **`@WailaPlugin` 注解不写**（见上）。

### 验证

- `./gradlew.bat build` = BUILD SUCCESSFUL；产物 jar 内含 `compat/jade/*` 三个类，
  `fabric.mod.json` 的 `entrypoints.jade` 指向 `JadePlugin`。
- **Jade 真的认到了我们**（`gradlew runClient`，`run/logs/latest.log`）：

```
[Jade] Start loading plugin from Touhou Little Maid: Futo
[Jade] Registered maid / shrine lamp tooltip providers      <- 我们写的锚点
```

  第二行是写在 `JadePlugin#registerClient` 里的日志：只有「装了 Jade + `jade` 入口点被读到 +
  本类成功加载 + 两次 `register*Component` 都执行完没抛异常」四件事同时成立才会出现。
- 客户端一直加载到 `Sound engine started`（窗口已创建），无 crash-report，
  日志里没有 Jade 相关异常（剩下的 `No data fixer registered for …` 是老噪音）。
- 四道守卫脚本全绿：`check-encoding`（BAD_FILES=0 / FFFD=0）、`check-jar` checked、
  `check-renderers`（entity 11/11、block entity 14/12 ALL OK）、`check-dangling` checked。
- `port/deploy-jar.ps1` 部署到 `1.18.2-Fabric-test`，sha256 两侧一致（该实例里本来就有 Jade）。

### 验证边界

- **没有做视觉验收**：提示行在 HUD 里长什么样、行序对不对、中文会不会挤，需要真人
  对着已驯服的女仆与神社灯各看一会儿。
- 只验到「注册成功」，没有在真实世界里逐条断言数值。任务名 / 日程 / 好感度都是
  entity data 同步字段（`DATA_TASK` / `SCHEDULE_MODE` / `DATA_FAVORABILITY`），
  客户端读得到正确值 —— 这一点与上游相同；但「无敌」那一行例外（见上）。
- `libs/JadeAddons-1.18.2-fabric-2.2.1.jar` 仍在 `libs/` 里但**没有被 `build.gradle` 引用**：
  上游 `compat/jade` 只有上面两个 provider，没有用 JadeAddons 的任何内容
  （1.20.1 版那三个「物品栏」provider 是 Jade 本体的 `registerItemStorage*`）。
  留着是为了将来补「祭坛 / 墓碑 / 野餐垫的容器预览」时能直接接上。

## 服务端没装本模组时，客户端不该露出创造页签

**现象**
只在客户端装本模组、连一个没有它的服务器，创造模式里仍然看得到
「Touhou Little Maid: Main / Chair / Garage Kit」三个页签，还能把物品拖进背包。
拖出来的物品在服务端反序列化时注册表里没有对应 id，于是拿到手就等于空气
（或直接回弹）—— 玩家看到的是「拿到了但完全用不了」。

**为什么会这样**
Fabric 的两端各自加载模组，原版 Minecraft 没有「服务端注册表里没有就藏起来」这层逻辑
（那是 Forge 的 registry sync 在做，Fabric 没有等价物）。所以客户端的物品、方块、
创造页签全都照常注册，只是服务端不认。

**怎么判断对面装没装**
不读服务端 mod 列表（Fabric Loader 不提供），而是复用 Fabric Networking 的握手：
服务端只要 `registerGlobalReceiver` 过一个 channel，Fabric 就会在玩家登录完成时
（`ServerPlayNetworkAddon#onClientReady`）把这个 channel 用 `minecraft:register` 发给客户端，
客户端的 `ClientPlayNetworking#canSend(channel)` 随之为 `true`。

于是新增一个**空载荷 channel** `touhou_little_maid:presence`（`network/ServerPresence`），
它唯一的使命就是「声明本模组在场」；接收器里什么都不做。

**为什么不是一进服就判定**
服务端的通告与客户端的 `ClientPlayConnectionEvents.JOIN` 在同一时刻触发，先后没有保证。
`client/event/ServerPresenceClient` 因此从 JOIN 起观察最多 60 tick（3 秒）：
期间任一刻看到 `canSend == true` 就认定「装了」，超时仍是 `false` 才认定「没装」。
判定一旦落地就停止，连接断开时复位。

误判只会朝一个方向：服务端其实装了、但通告迟到超过 3 秒 —— 此时页签被藏起来，
重进一次世界即可恢复。反方向（没装却显示）才是要修的 bug，所以选择宁可晚确认、不提前放行。

**藏起来的三层**
1. `init/InitItems` 三个页签的 `appendItems` 在判定为「没装」时直接返回 —— 内容为空；
2. `mixin/client/CreativeModeInventoryScreenMixin` 在 `renderTabButton` 的 `HEAD` 处
   `cancel`，按钮不画；
3. 同一个 mixin 在 `checkTabClicked` / `checkTabHovering` 的 `HEAD` 处返回 `false`，
   否则「看不见的按钮」仍然点得到、也仍然弹提示。
另外 `init` 的 `HEAD` 处检查「当前选中的是不是这三个之一」，是的话退回「建筑方块」，
免得高亮框停在一个没有按钮的位置。

**验证**
真起了一个**没有本模组**的 1.18.2 服务端（`server1182`，临时把 mods 里只留 fabric-api、
`online-mode=false`、换新世界名），然后在客户端执行 `gradlew runClient -PjoinServer`：
```
[Futo] remote server does not run Touhou Little Maid (no touhou_little_maid:presence channel):
       hiding its creative tabs, otherwise the items would be unusable
[Futo] creative tab 'main' now exposes 0 item(s)
[Futo] creative tab 'chair' now exposes 0 item(s)
[Futo] creative tab 'garage_kit' now exposes 0 item(s)
```
反例（同一个服务端换回带本模组的 mods、以及 `gradlew runServer` 起的开发服务端）：
```
[Futo] remote server runs Touhou Little Maid, creative tabs kept
```
两个方向都验过，且都没有 crash-report。测完把服务端的 `mods/`、`server.properties`、
两个临时世界全部还原/删除（`server1182` 不属于本仓库）。

**验证边界**
- 判定与「内容为空」是日志断言；**按钮真的消失、点不到**这一步只验到注入点正确，
  视觉上需要真人连一次没装本模组的服务器看一眼。
- 单机 / 服务器侧 `ServerPresence.isAbsent()` 恒为 `false`，行为与改动前完全一致。

## IPN / Iris 联动

上游 compat 里这两门都很小（IPN 23 行 / Oculus 17 行），但**落地方式完全不同**，所以分开写。

### IPN（Inventory Profiles Next）

上游 `compat/ipn` 只有两个动作，本移植版照搬：

| 上游 | 本移植版 | 说明 |
|---|---|---|
| 三个配置页签上的 `@IPNGuiHint` ×5 + `@IPNPlayerSideOnly` | 同样写在 `MaidConfigScreen` / `DefaultTaskConfigScreen` / `AttackTaskConfigScreen` 上 | 让 IPN 把「排序 / 按列排 / 按行排 / 编辑器 / 设置」五个按钮摆出来 |
| `SortButtonScreen#renderBackground` | `compat/ipn/SortButtonScreen`，调用点在 `AbstractMaidScreen#renderBg` | 17×48 底板，位置 `leftPos+249, topPos+166`（与上游逐字相同） |
| `ModList.get().isLoaded("inventoryprofilesnext")` | `IpnCompat#isLoaded`（`FabricLoader#isModLoaded`） | 缓存成 `Boolean`，允许在 `init()` 之前被渲染线程问到 |

**为什么注解就够了，不用调 IPN 的 API**
IPN 是用反射读界面类上的 `@IPNGuiHint`（`RUNTIME` + `@Inherited` + `@Repeatable`，
用 `javap -v` 在 `libs/` 的 jar 上确认过）来定位按钮的。我们只提供数据，
不参与绘制，所以**一个 IPN 类型都不用在运行期加载** —— 没装 IPN 时那些注解只是一坨没人读的
class 文件数据，不会 `NoClassDefFoundError`。

`@IPNPlayerSideOnly` 不能省：这三个界面的容器槽全是女仆自己的（配置页干脆没有物品槽），
能排序的只有底部那份玩家背包。少了它，IPN 会以为整个菜单都该被排序。

底板的位置 `leftPos+249` 已经超出 256 宽的界面本体，是挂在**界面右外侧**的 ——
上游就是这么放的（IPN 的按钮落点在界面之外）。

**为什么不能端到端跑起来**
IPN 的 `fabric.mod.json` 硬依赖 `fabric-language-kotlin` 与 `libipn`，这两份前置都不在
`libs/` 里。所以 `build.gradle` 里只写了 `modCompileOnly`，**没有** `modLocalRuntime` ——
强行带进开发环境只会在加载阶段报缺依赖。验证只能到「注解进得了 class 文件」这一层。

### Iris（Oculus 在 Fabric 侧的名字）

上游 `compat/oculus/OculusCompat` 是一个 17 行的「装没装」判定，唯一的消费者是
`MaidFishingHookRenderer`：

```java
if (OculusCompat.isOculusInstalled()) {
    lineConsumer.vertex(0.0, 0.0, 0.0).color(0, 0, 0, 255).normal(0.0F, 0.0F, 0.0F).endVertex();
}
```

光影管线会把 `lineStrip` 的最后一节吞掉，于是鱼线看起来是从半空中开始的；
在末尾补一个退化顶点就能把最后一节冲出去。

本移植版：

- 类叫 `compat/iris/IrisCompat`（上游 TLMO 1.20.1 的 Fabric 分支里也是同一个类、
  注释写着 `// Fabric: Oculus->Iris`，只把 mod id 从 `oculus` 换成 `iris`）；
- 判定方式与上游 1.20.1 Fabric 版逐字相同：`FabricLoader#isModLoaded("iris")`，
  **不**去调 `IrisApi#isShaderPackInUse()` —— 多补一个退化顶点在任何情况下都无害，
  而模组 API 会引入编译期依赖、且光影尚未初始化时未必有值；
- 调用点在 `MaidFishingHookRenderer#renderFishingLine` 的顶点循环之后，与上游一致。

**dev 环境的坑**：想验「装了 Iris」那条分支得同时装 Sodium，而 Sodium 1.18.2 把 JOML
作为 JiJ 内嵌在 `META-INF/jars/joml-1.10.2.jar` 里 —— Loom 不展开 `files()` 依赖的嵌套 jar
（与 fiber / night-config 同一个坑）。第一次跑直接崩在：

```
Mixin transformation of net.minecraft.client.renderer.culling.Frustum failed
Caused by: ClassNotFoundException: org.joml.Matrix4f
         at sodium.mixins.json:core.MixinFrustum
```

所以 `libs/joml-1.10.2.jar` 是从 Sodium 的 JiJ 里原样解出来的，`-PirisRuntime` 时按
`implementation`（它没有 `fabric.mod.json`）加载。

### 验证

默认（`libs/` 里没有 IPN / Iris 参与运行）：
```
[Futo] Inventory Profiles Next present: false
[Futo] Iris present: false
```
`port/smoketest.ps1` 一路到 `Sound engine started`，无 crash-report，
日志里没有任何 `NoClassDefFoundError` / `ClassNotFoundException` —— 说明缺这两个模组时
新增的代码是死的。

`gradlew runClient -PirisRuntime`（Iris 1.6.5 + Sodium 0.4.1 + JOML 1.10.2）：
```
[Futo] Iris present: true
```
同样到 `Sound engine started`，Sodium / Iris 自己的 mixin 全部应用成功，无 crash-report。

产物审计：`javap -v` 在打包好的 jar 上确认三个界面类都带着
`@IPNPlayerSideOnly` 与 5 个 `@IPNGuiHint`（以 `@IPNGuiHints` 容器的形式），
且 `org.anti_ad.mc.ipn.api.*` 没有被 remap 破坏。

### 验证边界

- **IPN 的按钮没在游戏里见过**：缺 `fabric-language-kotlin` / `libipn`，跑不起来。
  能断言的是「注解在 class 文件里、位置与上游一致、缺 IPN 时不出错」。
- **Iris 的鱼线没做视觉验收**：那需要一条真实的钓鱼线 + 开着光影包看。
  验到的是「判定为 true、游戏能正常启动、渲染路径不抛异常」。
- 上游另外 8 个带 `@IPNGuiHint` 的界面是**背包容器界面**（大/中/小/末影箱/熔炉/储罐/
  工作台/空背包），本移植版没有那套界面，因此那 8 处注解无处可放 —— 与 REI 的
  `registerScreens` / `registerTransferHandlers` 是同一个前置阻塞。

## 农夫乐事联动

上游 1.20.1 有 `compat/farmersdelight`，1.18.2 上游没有；而且它依赖的那套「方块类食物」
体系在 1.18.2 上游同样不存在（`grep -r edible` 零命中，那是 1.4.7 才加的）。所以本轮不只是
搬一个 compat 类，而是把那套**前置基础设施**一起补上。

### 先把「方块类食物」体系补出来

按 TLMO Fabric 1.20.1 的形状补齐了下面这些：

| 新增 | 作用 |
|---|---|
| `api/block/IMaidEdibleBlock` | 「什么算能吃一口的方块」的接口 + 零食架标签 `MAID_SNACK_STAND_BLOCK` |
| `entity/ai/edible/MaidEdibleBlockManager` | 登记表（内置蛋糕 + 各 compat） |
| `entity/ai/edible/MaidEdibleBlockAction` | 记忆值：偷吃 / 摆放 |
| `entity/ai/edible/CakeEdible` | 原版蛋糕实现（这套机制的样板，也是没装任何食物模组时唯一的可观察行为） |
| `entity/ai/brain/task/MaidStealEdibleMoveBlockTask` | 找到目标并走过去（搜索半径 5、冷却 45 秒、可达性放宽到 3×3×3） |
| `entity/ai/brain/task/MaidStealEdibleUseTask` | 到了之后真正动手（吃完加 `Type.STEAL_EDIBLE_BLOCK` 好感度） |
| `data/touhou_little_maid/tags/blocks/maid_snack_stand_block.json` | 零食架标签，内容是 `touhou_little_maid:snack_cabinet` |

顺带补了四处上游有、本移植版缺的东西：

- `EntityMaid#spawnItemParticles(ItemStack, int)`（嘴里掉食物渣的粒子，1.20.1 的写法）；
- `Type.STEAL_EDIBLE_BLOCK`（`+1` 好感度 / 3 分钟冷却，数值与上游逐字相同）；
- `InitEntities.MAID_EDIBLE_BLOCK_ACTION` 记忆模块；
- `MaidMoveToBlockTask#getHorizontalSearchRange`（上游有这个钩子，本移植版当初写死了
  `getRestrictRadius()`；偷吃只搜 5 格，所以必须加回来）。

**为什么要求「必须放在零食架上」**：否则女仆会把玩家精心摆在地上的蛋糕啃掉。
判断走 `belowIsSnackStand`，标签只含零食柜 —— 与原版一致。

**农夫乐事自己的那份**（`compat/farmersdelight`）覆盖两类方块：
`PieBlock`（派，`BITES` 递增）与 `FeastBlock`（盛宴，`servings` 递减）。
Fabric 版的包名是 `com.nhoryzon.mc.farmersdelight.block.*`（**不是** Forge 的
`vectorwing.farmersdelight.common.block.*`），而且 `PieBlock` 没有 `getMaxBites()`，
最大口数是常量 `PieBlock.MAX_BITES` —— 这两处都是从 `libs/` 的 jar 里 `javap` 出来的。

### 饰品（Curios）：本轮不做

先澄清事实（两个 1.20.1 jar 都解包查过）：

- Forge 1.20.1 的 `compat/curios` 做的是：`CuriosContainer extends MaidMainContainer`
  → **把女仆主界面换成一个带饰品格的变体**（每页 5 格 + 翻页箭头），`MaidCurioSlot`
  还带「显示 / 装饰槽」开关；`CuriosEvent#onSlotUpdate` 在饰品变动时重算属性；
  `CuriosEvent#onMaidTombstone` 在死亡时把饰品倒进墓碑；另有时装 `MaidConfig.ENABLE_MAID_CURIOS`
  开关与 `CuriosButton` 客户端按钮。
- Fabric 1.20.1 的同一功能在 `compat/accessories`，类名逐个对应（`AccessoriesCompat`、
  `AccessoriesEvent`、`CuriosContainer` …），**对接的是 Accessories**。
- 两个 jar 里 `unzip -l | grep -ci trinket` 都是 **0** —— 上游从未对接过 Trinkets。

因此「女仆饰品栏」不是一条能照抄的移植项：要么对接 Accessories（Fabric 1.18.2 上
Accessories 的可用性与 API 尚未核实），要么自己发明一套。上一轮抓了 Trinkets 顶上，
结果是界面上多出一块说不清用途的入口、而饰品本身既没槽位也没有任何可见效果，
**已整块回退**（`compat/trinkets/**`、`data/trinkets/**`、两张贴图、`TabIndex.TRINKETS`、
`InitMenu.TRINKET_MAIN`、主界面的入口按钮、死亡倒饰品，以及 `libs/` 下的
`trinkets-3.3.1.jar` 与两份 Cardinal Components）。

若以后要做，正确的起点是**先决定对接哪个 API**，再照 1.20.1 的形状把它做成
「主界面的一个变体」而不是「第四个顶层页签」。

### 验证

默认（农夫乐事不在 dev runtime）：
```
[Futo] Farmer's Delight present: false
[Futo] edible block registered: CakeEdible
```
`gradlew runClient -PfarmersDelightRuntime`：
```
[Futo] Farmer's Delight present: true
[Futo] edible block registered: CakeEdible
[Futo] edible block registered: FarmersDelightEdible
```
两边都到 `Sound engine started`，无 crash-report，日志里没有
`NoClassDefFoundError` / `NoSuchMethodError` —— 说明缺模组时那份代码不会被链接。

产物审计：jar 内 `compat/farmersdelight/**` 两个类 + `entity/ai/edible/**` 三个类 +
`data/touhou_little_maid/tags/blocks/maid_snack_stand_block.json` 全部就位，且**不再有**
任何 `trinkets` 相关条目。`build` 绿；四道守卫绿。

### 验证边界

- **没做过「女仆真的去偷吃」的实机验证**：那需要把蛋糕摆在零食柜上、等到上班时段、
  观察她走过去吃。能自动断言的是「登记表里有两份实现」「缺模组时不报错」。

## 零食柜的三处毛病（2026-11-28）

用户在零食柜上放蛋糕后游戏崩溃，并且报了两处渲染问题。查下来是三件互不相干的事，
外加一个顺带撞见的致命 bug。

### 1. 「放蛋糕崩溃」其实是部署在游戏运行时覆盖了 jar

崩溃报告里的异常与蛋糕毫无关系：

```
java.lang.RuntimeException: Failed to load class file for
        'com.github.tartaricacid.touhoulittlemaid.client.renderer.ai.ChatBubbleRenderer$Side'!
Caused by: java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)
```

JVM 打开 jar 时持有 ZipFile 句柄，而 Windows 允许别人<b>覆盖</b>一个被打开的 jar 文件。
于是下一次要加载某个类时，读到的字节已经是新 jar 的内容、偏移量却是旧的值 —— 报的
永远是「恰好接下来要加载的那个类」。上一次部署（11:14）盖在了一个 11:04 就启动的游戏上，
11:16 才崩，中间两次操作（放蛋糕 / 看向女仆）只是碰巧轮到那个类。

`port/deploy-jar.ps1` 现在会先查 `Win32_Process` 里有没有命令行含 `1.18.2-Fabric-test` 的
java 进程，有就直接 `throw`，不覆盖。查不到命令行信息的环境下降级为「只警告不阻断」
（否则开发时的 `runClient` 会挡住所有部署）。

### 2. 空柜子也竖着两格高的玻璃罩

`models/bedrock/block/snack_cabinet.json` 里有三个互斥的骨骼：`root`（柜体，1 格高）、
`full`（高玻璃罩，顶到 2.11 格）、`half`（矮玻璃罩，顶到 1.94 格）。反编译上游 1.20.1 的
`TileEntitySnackCabinetRenderer#render` 可见它会按 `TYPE` 改 `BedrockPart.visible`：
`1` 只显示 `full`、`2` 只显示 `half`、其余两个都不显示。

本移植版的渲染器起初漏了这一步，三个骨骼全画 —— 于是空柜子顶上平白多出一格玻璃罩，
看上去就像「用了旧模型、两格高」。

补上的东西：

| 新增 | 说明 |
|---|---|
| `BlockSnackCabinet.SNACK_CABINET_FULL` / `SNACK_CABINET_HALF` | 两个方块标签，判定「柜顶摆的是什么」 |
| `data/touhou_little_maid/tags/blocks/snack_cabinet_{full,half}.json` | 与上游逐字相同的两份标签（full 9 项 / half 44 项，后者大半是 kaleidoscope_cookery，全部 `required: false`） |
| `BlockSnackCabinet#updateShape` | 上方邻居变化时重算 `TYPE` |
| `BlockSnackCabinet#getStateForPlacement` | 放下时就算一次，不等方块更新 |
| `TileEntitySnackCabinetRenderer#setBoneVisible` | 每帧按 `TYPE` 设置 `full` / `half` 的 `hidden` |

**与上游的一处差异**：上游的 `updateShape` 只在「邻居命中标签」时赋值，邻居变成空气时
直接走 `super`，于是把食物拿走后玻璃罩不会消失（要等下一次方块更新）。这里改成三分支都赋值，
上方既不在 full 也不在 half 里就写回 `0`。

### 3. 占位物品图标显示成条纹

`reborn_maid.png` 是 16×240（15 帧）、`spawn_box.png` 是 16×656（41 帧），
两者的 `.png.mcmeta` 都只写了 `frametime: 2`。旧实现在两处同时出错：

1. 图标是 `LayerDefinition` + `addBox(..., 16, 16, 0)` 做的<b>零厚度板</b>。原版拆 UV 是按
   盒子六面平铺，厚度为 0 时背面的 u 从 `u + 2d + w` 起算 —— 16 → 32，直接跑出这张 16 宽的
   贴图，采样被夹在最右一列；四块侧面又退化成 0 面积、与正反面共面，同一个 z 上堆了五六层。
2. 就算只有一块面，UV 取满格（0..1）也会把 15 / 41 帧压成一条 —— 仍然是条纹。

现在的做法：

| 变更 | 原因 |
|---|---|
| 删掉 `client/model/EntityPlaceholderIconModel` | 零厚度 `ModelPart` 这条路本身就走不通 |
| `EntityPlaceholderItemRenderer` 手写一块 +Z 单位 quad | 等价于上游的 `singleSouthFace()` |
| 自己读 PNG 尺寸与 `mcmeta` 的 `frametime`，每帧只挪 UV 的 v 区间 | 见下 |

**为什么不能走方块图集**：图集只收「被某个模型引用」的贴图，而
`models/item/reborn_maid.json` / `spawn_box.json` 对应的<b>不是物品</b>（两者都是祭坛配方的
产物），`ModelBakery` 只按物品注册名加载 `models/item/*`，这两张 sprite 永远不会被 stitch，
查出来永远是 `missingno`。所以改成直接绑原贴图 + 自己算帧（帧数 = 高 / 宽，节拍 1 tick = 50 ms）。
代价是与原版动画的相位不保证同步，观感一致。

### 4. 顺带查出：女仆一 tick 就崩服（NPE）

用 RCON 起开发服务端做上面的验证时，服务端启动一秒后直接崩了：

```
java.lang.NullPointerException: Cannot invoke "java.util.Optional.map(...)"
        because the return value of "java.util.Map.get(Object)" is null
    at net.minecraft.world.entity.ai.Brain.getMemory(Brain.java:244)
    at ...MaidStealEdibleMoveBlockTask.start(MaidStealEdibleMoveBlockTask.java:84)
```

`Brain` 的 `memories` 是一个<b>预先按 `getMemoryTypes()` 建好的 map</b>，读一个没登记的键
不会返回 `empty`，而是 NPE。上一轮加了 `InitEntities.MAID_EDIBLE_BLOCK_ACTION` 这个记忆类型，
却忘了把它加进 `MaidBrain#getMemoryTypes()` —— 而 `MaidStealEdibleMoveBlockTask` 在 WORK 时段
每 tick 都会读它。

修复：在 `getMemoryTypes()` 里登记 `InitEntities.MAID_EDIBLE_BLOCK_ACTION`。

### 验证

RCON 端到端（`gradlew runServer` + `port/rcon.py`）：

```
setblock 2 -55 0 touhou_little_maid:snack_cabinet
execute if block 2 -55 0 touhou_little_maid:snack_cabinet[type=0]   -> Test passed
setblock 2 -54 0 minecraft:cake
execute if block 2 -55 0 touhou_little_maid:snack_cabinet[type=1]   -> Test passed
setblock 2 -54 0 minecraft:air
execute if block 2 -55 0 touhou_little_maid:snack_cabinet[type=0]   -> Test passed
```

也就是「空柜 → 0、顶上放蛋糕 → 1、拿掉 → 0」三步都对。服务端连续跑了两分钟、世界里
有女仆在 tick，`Stopping server` 一次都没出现，`crash-reports/` 为空 —— NPE 那条已闭。

客户端 `port/smoketest.ps1` 到 `Sound engine started`，日志里只有离线鉴权的 401，
无 `NoClassDefFoundError` / `NoSuchMethodError`。`build` 绿；四道守卫绿
（`check-encoding` 1649 files / 0 bad、`check-jar` / `check-renderers` 11/11 + 14/12 / `check-dangling`）；
已部署到测试实例且 sha256 一致。

### 验证边界

- **视觉没验收**：玻璃罩是否只在该出现时出现、图标动画的观感，都要真人在游戏里看一眼。
  能自动断言的是「`TYPE` 状态机正确」「服务端不崩」「产物里没有旧模型类」。
- **「女仆真的走过去偷吃」仍未验证**：需要主人在线 + 上班时段，且这一条本身也不属于本轮修的三个问题。
