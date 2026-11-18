"""Append the gap (wireless I/O) section to README.md.

Run once:  python port/patch_readme_wireless.py

Written as a script (not a shell heredoc) because the terminal mangles some
multi-byte characters -- U+00D7 and blank lines were both silently dropped the
first time this section was appended.
"""
import io

SECTION = """
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
"""


def main():
    path = 'README.md'
    with io.open(path, encoding='utf-8', newline='') as handle:
        text = handle.read()
    if '## 隙间（无线 I/O 饰品）' in text:
        print('already present, nothing to do')
        return
    body = SECTION.replace('\n', '\r\n')
    with io.open(path, 'w', encoding='utf-8', newline='') as handle:
        handle.write(text.rstrip('\r\n') + '\r\n' + body.lstrip('\r\n'))
    print('appended')


if __name__ == '__main__':
    main()

