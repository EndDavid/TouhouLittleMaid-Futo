"""Append the picnic basket / mat section to README.md (CRLF + UTF-8, byte-exact)."""
import pathlib
import sys

README = pathlib.Path(__file__).resolve().parent.parent / 'README.md'
ANCHOR = '## 野餐篮与野餐垫：一个物品，三种身份'

SECTION = """## 野餐篮与野餐垫：一个物品，三种身份

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

### 6. 渲染：两个模型层 + 一个物品渲染器

- `PicnicMatModel`（529 行几何）与 `PicnicBasketModel` 从上游 1.18.2 **逐字节**搬过来，
  见 `port/port_picnic_models.py`（只加文件头注释并转 CRLF）。手抄几百行 `addBox` 只会引入抄错。
- `PicnicBasketIconModel` 是唯一的重写：上游用基岩模型库的「单南面」方块（厚度 0 的板），
  本移植版没有那个库，于是用原版 `addBox` 造同样一块 0 厚度的板 —— 六面共用同一段 UV，
  正对摄像机时看到的就是同一张图。
- `PicnicBasketItemRenderer` 按 `TransformType` 分两种：`GUI` 画扁平图标，
  其余画 3D 模型（上游用 Forge 的 `IItemRenderProperties`，Fabric 用 `BuiltinItemRendererRegistry`）。
  图层在**第一次渲染时**才烘焙 —— 注册时 `Minecraft#getEntityModels()` 还没就绪。
- `TileEntityPicnicMatRenderer` 三层：装饰件（`basketHide/breadHide/cakeHide` 在真的放了东西时隐藏）
  → 六格食物按固定坐标摆盘（≥10 个时叠一摞）→ 最后画垫子本身。

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

`port/book_pages_held_back/validate-book.ps1` → `entries=53 categories=3 problems=0`：
野餐篮词条从 `port/book_pages_held_back/` 还原进手册，`altar_recipe` 页引用的
`craft_picnic_basket` 存在、材料都是已注册物品。

客户端也起过一次并连上同一个服务端：垫子（中心格 + 边角格）与野餐篮物品在视野里渲染，
`Picnic basket item renderer failed` 与帕秋莉的 `Error compiling book` 都没出现。

**没验证到的**：界面本身（右键空气打开的 9 格）与 tooltip 需要真人操作。
两者的注册方式与已经验证过的隙间界面完全同构，所以风险很低，但仍属于「留给你实测」。
"""


def main():
    text = README.read_bytes().decode('utf-8')
    if ANCHOR in text:
        sys.exit('anchor already present')
    if not text.endswith('\r\n'):
        sys.exit('README does not end with CRLF')
    section = SECTION.replace('\r\n', '\n').replace('\n', '\r\n')
    out = text + '\r\n' + section
    README.write_bytes(out.encode('utf-8'))
    print('ok, lines before =', text.count('\n'), 'after =', out.count('\n'))


if __name__ == '__main__':
    main()

