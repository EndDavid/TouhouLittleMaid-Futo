"""Append the "did we actually use ForgeConfigAPIPort" section to README.md.

Run once:  python port/patch_readme_fcap.py

Written as a script rather than a shell heredoc because the terminal drops some
multi-byte characters and blank lines (the earlier wireless-IO section had to be
repaired for exactly that reason).
"""
import io

SECTION = """
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
"""


def main():
    path = 'README.md'
    with io.open(path, encoding='utf-8', newline='') as handle:
        text = handle.read()
    if '## 配置：Forge Config API Port 到底给了什么，我们用了多少' in text:
        print('already present, nothing to do')
        return
    body = SECTION.replace('\n', '\r\n')
    with io.open(path, 'w', encoding='utf-8', newline='') as handle:
        handle.write(text.rstrip('\r\n') + '\r\n' + body.lstrip('\r\n'))
    print('appended')


if __name__ == '__main__':
    main()

