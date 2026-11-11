# 暂存的手册页面（Patchouli book pages held back）

这里放的是**刚从 `src/main/resources` 里挪出来**的《记忆中的幻想乡》词条，
原样保留、未做任何修改 —— 它们和上游 jar 里的同名文件仍然逐字节一致。

## 为什么要挪出来

帕秋莉（Patchouli）在构建整本书时的行为是「**一页出错，整本作废**」：

1. `BookContentsBuilder#load` 逐条读 `entries/*.json`；
2. `BookContentsBuilder#build` 对每条词条调用 `BookEntry#build`；
3. 每个 `spotlight` 页会把 `item` / `items` 字段解析成 `Ingredient`
   （`ItemStackUtil.loadIngredientFromString`）；
4. 只要里面出现一个**未注册的物品 ID**，`ItemStackUtil.loadStackFromString`
   就抛 `JsonParseException: Unknown item ID: xxx`；
5. `BookEntry#build` 把它包成 `RuntimeException`，`Book#reloadContents` 捕获后
   直接 `BookContents.empty(book, e)`；
6. 结果就是**整本书变成空的** —— 首页只剩一个「索引」按钮，并显示
   `Loading error! (Hover for info)`。

日志里对应的行是：

```
[patchouli] Error compiling book touhou_little_maid:memorizable_gensokyo, using empty contents
   at BookContentsBuilder.lambda$build$2(BookContentsBuilder.java:98)
   at ItemStackUtil.loadStackFromString(ItemStackUtil.java:114)
   ... Unknown item ID: touhou_little_maid:chair
```

> 注意：**词条图标**（`icon` 字段）不认识物品时只 warn、不抛异常
> （`BookIcon#from` 会退回 `StackIcon(ItemStack.EMPTY)`，画出来是空槽），
> 所以图标缺失不会导致整本书崩溃；真正致命的是 `spotlight` 页的 `item`。

## 现在还剩什么缺口

被挪出来的 20 页，缺的是**尚未移植的物品 / 方块**：

| 词条 | 缺失的物品 ID | 所属子系统 |
| --- | --- | --- |
| `maid/film` | `film` | 神社复兴 |
| `maid/gap` | `wireless_io` | 女仆饰品（无线 IO） |
| `maid/kappa_compass` | `kappa_compass` | 搜索类工具 |
| `maid/servant_bell` | `servant_bell` | 搜索类工具 |
| `maid/substitute_jizo` | `substitute_jizo` | 神社复兴 |
| `other/broom` | `broom` | 扫帚 |
| `other/chair` | `chair`、`cat_cart`、`beach_chair` | 坐垫 / 椅子 |
| `other/chair_show` | `chair_show` | 坐垫 / 椅子 |
| `other/chisel_and_statues` | `chisel`、`statue` | 手办与雕像 |
| `other/favorability_tool` | `favorability_tool_add/full/reduce` | 好感度工具 |
| `other/fox_scroll` | `red_fox_scroll`、`white_fox_scroll` | 搜索类工具 |
| `other/garage_kit` | `garage_kit` | 手办与雕像 |
| `other/gomoku` | `gomoku` | 棋类与娱乐方块 |
| `other/granite_inari_fox` | `scarecrow` | 神社复兴 |
| `other/maid_bed` | `maid_bed` | 神社复兴 |
| `other/maid_joy` | `keyboard`、`bookshelf`、`computer`、`gomoku` | 棋类与娱乐方块 |
| `other/model_switcher` | `model_switcher` | 模型切换器 |
| `other/picnic_basket` | `picnic_basket` | 野餐与娱乐 |
| `other/shrine` | `shrine` | 神社复兴（含祭坛） |
| `other/trumpet` | `trumpet` | 搜索类工具 |

对应 6 个子系统：**坐垫 / 椅子**、**手办与雕像**、**棋类与娱乐方块**、
**扫帚**、**神社复兴**（含祭坛）、**搜索类工具**。

## 已经还回去的（女仆饰品）

`maid/maid_bauble.json`、`maid/mute_bauble.json`、`maid/item_magnet.json`
三页已经还原进资源 —— 饰品子系统移植完成后，它们引用的
`ultramarine_orb_elixir` / `*_protect_bauble` / `nimble_fabric` /
`item_magnet_bauble` / `mute_bauble` 全部完成注册。

`maid/gap.json` 仍留在本目录：它讲的是 `wireless_io`（无线 IO 饰品），
这件物品要等 IO 配置界面一起移植，所以先不还原。

## 怎么还原

某个子系统移植完成后，把对应文件原样搬回：

```
port/book_pages_held_back/other/gomoku.json
  -> src/main/resources/assets/touhou_little_maid/patchouli_books/memorizable_gensokyo/en_us/entries/other/gomoku.json
```

不需要改任何其它东西：分类页是自动收录词条的，词条回来自动出现。

**还原前务必确认该页引用的物品 / 方块都已注册**，否则整本书会再次变成
`Loading error!`（这正是帕秋莉的失败模式）。`validate-book.ps1` 就是为此而写的：

```
powershell -NoProfile -ExecutionPolicy Bypass -File port/book_pages_held_back/validate-book.ps1
```

它按帕秋莉的规则静态检查 `spotlight` 物品 / 词条与分类图标 / 分类引用 / entity 页。
**每还原一页就要跑一次，并同步在 `$registered` 里补上新增的物品 id。**

## 目前手册里保留了什么（32 词条 / 3 分类）

- `maid/`：backpack、chat_bubbles、item_magnet、last_slot、maid_bauble、maid_base、
  maid_config、maid_home、maid_meal、maid_skin、maid_sound、maid_task、mute_bauble、
  naming_eggs、other_backpack、snowball_fight、spawn_maid
- `other/`：camera_and_photo、command、extinguisher、maid_beacon、power_point
- `overview/`：download_custom_pack、favorability、gohei_and_danmaku、
  make_custom_chatbubble_text、make_custom_model、multiblocks_altar、power_point、
  vanilla_change、what_is_touhou、world_spawn

其中 `overview/favorability` 的图标从 `favorability_tool_add`（未移植）改成了
`minecraft:cake`，`categories/other.json` 的图标从 `chair{...}`（未移植）改成了
`minecraft:chest`，否则帕秋莉会画一个空槽。

还有一点：手册里 43 个 `altar_recipe` 页目前是空框架，因为**祭坛合成系统整体
没移植**（见 `compat/patchouli/AltarRecipeComponent`）。那是核心玩法而不是手册
联动，等祭坛落地后这些页面自然会填满。

