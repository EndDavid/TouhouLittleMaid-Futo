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

