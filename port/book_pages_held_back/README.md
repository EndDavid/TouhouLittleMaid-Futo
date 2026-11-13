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

被挪出来的 14 页，缺的是**尚未移植的物品 / 方块**：

| 词条 | 缺失的物品 ID | 所属子系统 |
| --- | --- | --- |
| `maid/gap` | `wireless_io` | 女仆饰品（无线 IO） |
| `maid/kappa_compass` | `kappa_compass` | 搜索类工具 |
| `maid/servant_bell` | `servant_bell` | 搜索类工具 |
| `other/broom` | `broom` | 扫帚 |
| `other/chisel_and_statues` | `chisel`、`statue` | 手办与雕像 |
| `other/favorability_tool` | `favorability_tool_add/full/reduce` | 好感度工具 |
| `other/fox_scroll` | `red_fox_scroll`、`white_fox_scroll` | 搜索类工具 |
| `other/garage_kit` | `garage_kit` | 手办与雕像 |
| `other/gomoku` | `gomoku` | 棋类与娱乐方块 |
| `other/maid_joy` | `keyboard`、`bookshelf`、`computer`、`gomoku` | 棋类与娱乐方块 |
| `other/model_switcher` | `model_switcher` | 模型切换器 |
| `other/picnic_basket` | `picnic_basket` | 野餐与娱乐 |
| `other/trumpet` | `trumpet` | 搜索类工具 |

对应 5 个子系统：**手办与雕像**、**棋类与娱乐方块**、**扫帚**、
**野餐与娱乐**、**搜索类工具**。

## 已经还回去的

- **女仆饰品**：`maid/maid_bauble.json`、`maid/mute_bauble.json`、`maid/item_magnet.json`
  —— 饰品子系统移植完成后，它们引用的 `ultramarine_orb_elixir` /
  `*_protect_bauble` / `nimble_fabric` / `item_magnet_bauble` / `mute_bauble` 全部完成注册。
- **神社复兴**：`maid/film.json`、`maid/substitute_jizo.json`、`other/maid_bed.json`、
  `other/shrine.json`、`other/granite_inari_fox.json`（稻草人）。
- **坐垫**：`other/chair.json`、`other/chair_show.json`（本轮）。

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

## 目前手册里保留了什么（39 词条 / 3 分类）

`validate-book.ps1` 的当前输出：`entries=39 categories=3 problems=0`。

- `maid/`：backpack、chat_bubbles、film、item_magnet、last_slot、maid_bauble、maid_base、
  maid_config、maid_home、maid_meal、maid_skin、maid_sound、maid_task、mute_bauble、
  naming_eggs、other_backpack、snowball_fight、spawn_maid、substitute_jizo
- `other/`：camera_and_photo、chair、chair_show、command、extinguisher、
  granite_inari_fox、maid_beacon、maid_bed、power_point、shrine
- `overview/`：download_custom_pack、favorability、gohei_and_danmaku、
  make_custom_chatbubble_text、make_custom_model、multiblocks_altar、power_point、
  vanilla_change、what_is_touhou、world_spawn

其中 `overview/favorability` 的图标从 `favorability_tool_add`（未移植）改成了
`minecraft:cake`，否则帕秋莉会画一个空槽。`categories/other.json` 的图标
已经换回上游的 `chair{ModelId:'touhou_little_maid:beach_chair'}` —— 坐垫移植完成
之后这个图标可以正常显示了。

还有一点：手册里 43 个 `altar_recipe` 页目前是空框架，因为祭坛的**配方渲染组件**
（`compat/patchouli/AltarRecipeComponent`）仍是占位实现。祭坛本体与 26 个配方都已
落地，缺的是把配方画进手册的那部分，属于手册联动而非核心玩法。
