# 暂存的手册页面（Patchouli book pages held back）

这里放的是**刚从 `src/main/resources` 里挪出来**的《记忆中的幻想乡》词条，
原样保留、未做任何修改。它们和上游 jar 里的同名文件仍然逐字节一致。

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
6. 结果就是**整本书变成空的**——首页只剩一个「索引」按钮，并显示
   `Loading error! (Hover for info)`。

日志里对应的行是：

```
[patchouli] Error compiling book touhou_little_maid:memorizable_gensokyo, using empty contents
   at BookContentsBuilder.lambda$build$2(BookContentsBuilder.java:98)
   at ItemStackUtil.loadStackFromString(ItemStackUtil.java:114)
   ... Unknown item ID: touhou_little_maid:chair
```

> 注意：**词条图标**（`icon` 字段）不认识物品时只 warn、不抛异常
> （`BookIcon#from` 会退回 `StackIcon(ItemStack.EMPTY)`，画出来是空的），
> 所以图标缺失不会导致整本书崩掉；真正致命的是 `spotlight` 页的 `item`。

## 缺口是什么

被挪出来的 23 页，缺的是 **29 个还没移植的物品/方块**：

| 词条 | 缺失的物品 ID |
| --- | --- |
| `maid/film` | `film` |
| `maid/gap` | `wireless_io` |
| `maid/item_magnet` | `item_magnet_bauble` |
| `maid/kappa_compass` | `kappa_compass` |
| `maid/maid_bauble` | `ultramarine_orb_elixir`、`*_protect_bauble`、`nimble_fabric` |
| `maid/mute_bauble` | `mute_bauble` |
| `maid/servant_bell` | `servant_bell` |
| `maid/substitute_jizo` | `substitute_jizo` |
| `other/broom` | `broom` |
| `other/chair` | `chair`、`cat_cart`、`beach_chair` |
| `other/chair_show` | `chair_show` |
| `other/chisel_and_statues` | `chisel`、`statue` |
| `other/favorability_tool` | `favorability_tool_add/full/reduce` |
| `other/fox_scroll` | `red_fox_scroll`、`white_fox_scroll` |
| `other/garage_kit` | `garage_kit` |
| `other/gomoku` | `gomoku` |
| `other/granite_inari_fox` | `scarecrow` |
| `other/maid_bed` | `maid_bed` |
| `other/maid_joy` | `keyboard`、`bookshelf`、`computer`、`gomoku` |
| `other/model_switcher` | `model_switcher` |
| `other/picnic_basket` | `picnic_basket` |
| `other/shrine` | `shrine` |
| `other/trumpet` | `trumpet` |

对应的 7 个子系统：**坐垫/椅子**（含 `chair_show`）、**手办与雕像**（`chisel` /
`statue` / `garage_kit`）、**棋类与娱乐方块**（`gomoku` / `cchess` / `wchess` /
`keyboard` / `bookshelf` / `computer` / `picnic_mat`）、**扫帚**、
**神社复兴**（`shrine` / `film` / `maid_bed` / `scarecrow` / `altar`）、
**女仆饰品**（各类 bauble / `wireless_io` / `ultramarine_orb_elixir`）、
**搜索类工具**（`kappa_compass` / `servant_bell` / `trumpet` / `fox_scroll`）。

另外 `other/chair` 与 `maid/maid_bauble` 这两页虽然不会致命（它们的 `spotlight`
不存在，只有图标缺），但整页讲的都是尚未移植的物品，留着等于手册在描述不存在的
内容，所以一起挪出来了。

## 怎么还原

某个子系统移植完成后，把对应文件原样搬回：

```
port/book_pages_held_back/other/gomoku.json
  -> src/main/resources/assets/touhou_little_maid/patchouli_books/memorizable_gensokyo/en_us/entries/other/gomoku.json
```

不需要改任何其它东西：分类页是自动收录词条的，词条回来自动出现。

**还原前务必确认该页引用的物品/方块都已注册**，否则整本书会再次变成
`Loading error!`（这正是帕秋莉的失败模式）。

## 目前手册里保留了什么（29 页）

- `maid/`：backpack、chat_bubbles、last_slot、maid_base、maid_config、maid_home、
  maid_meal、maid_skin、maid_sound、maid_task、naming_eggs、other_backpack、
  snowball_fight、spawn_maid
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

