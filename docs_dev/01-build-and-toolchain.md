# 构建与工程

> 移植过程笔记。摘自根目录 `README.md`（2026-11-25 拆分），返回索引：[`docs_dev/README.md`](README.md)。

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

### 哪些东西入库、哪些不入库

`.gitignore` 的分组就是规则本身（构建产物 / 运行目录 / 临时文件 / 日志 / Python / IDE / 系统 /
脚本临时输出），末尾还留着一份「必须入库」的备忘清单。要点：

- **入库**：`libs/*.jar`（`build.gradle` 硬引用）、`gradle/wrapper/`、`src/main/resources/`（约 48 MB 资源）、
  `port/`（上游参考源码 + 守卫脚本）、`docs_dev/`。
- **不入库**：`build/` `.gradle/`、`run/`（开发运行目录，含存档与日志）、`loom-cache/` `.fabric/`
  `crash-reports/`、`__pycache__/`、`*.log` / `*.hprof` / `hs_err_pid*`，以及 `port/_*.txt`
  （helper 脚本的中间输出）。
- 想加「`*.jar` 一律忽略」这类大范围规则时，务必同时加回 `!libs/*.jar` 与
  `!gradle/wrapper/gradle-wrapper.jar`，否则 clone 下来直接编译失败。

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

### 图标与美术资源：`pack.png` 是唯一来源

模组自带的图标美术都来自同一张 32×32 的立绘，源文件就是 `src/main/resources/pack.png`：
jar 根目录有 `pack.mcmeta`，模组本身就是一个内置资源包，这张图正是它在「资源包」界面里的图标
（上游同样如此）。三处用途、三种尺寸：

| 文件 | 用途 | 为什么是这个尺寸 |
|---|---|---|
| `src/main/resources/pack.png` | 内置资源包的图标 | 上游就是这个名字、这个位置 |
| `src/main/resources/icon.png` | `fabric.mod.json` 的 `icon`，ModMenu 读 | ModMenu 画的是 32×32 的方块，32×32 正好 1:1 |
| `assets/touhou_little_maid/textures/gui/tab_icon.png` | 主创造模式页签的图标 | 32×32，绘制时由 GPU 采样到 16×16（见下） |

`icon.png` **必须**是正方形：ModMenu 的 `FabricIconHandler.createIcon` 里有
`Validate.validState(w == h, "Must be square icon")`，非正方形一律被拒、退回默认图标，
日志是 `Invalid mod icon for icon source touhou_little_maid:logo.png`。上游 1.20.1 Fabric 版把
`icon` 指向 1200×240 的横幅 `logo.png`，所以这条日志上游也有；Forge 那边没事，是因为
`mods.toml` 本来就把 `logoFile` 当横幅画。`logo.png` 仍然保留 —— `META-INF/mods.toml` 还在引用它。

页签图标换不了配置项：Fabric 的 `FabricItemGroupBuilder.icon(Supplier<ItemStack>)` 只收物品栈，
原版也只在 `CreativeModeInventoryScreen#renderTabButton` 里拿 `getIconItem()` 去
`renderAndDecorateItem`。所以这里用客户端 mixin
（`mixin/client/CreativeModeInventoryScreenMixin`）把主页签的那一次调用重定向成贴图，
别的页签原样转交。**不新增注册物品** —— 凭空多一件「只有贴图」的物品会被 `/give` 和 REI 看到。

页签里那个图标**不必**按 16×16 出图：贴图保持 32×32，绘制时压进 16×16，由 GPU 点采样
（`futo$tabIconSize` 控制目标边长，默认 16 = 原版尺寸）。

**这里有个坑，值得记住。** `GuiComponent.blit` 有两个重载，长得很像但语义不同：

```java
// 9 参：UV 跨度 == 目标尺寸，写 (16, 16, 32, 32) 等于"取原图左上角 16×16 那四分之一"
blit(pose, x, y, blitOffset, u, v, w, h, texW, texH)
// 11 参：目标尺寸与 UV 范围分开，这才是「32×32 缩到 16×16」
blit(pose, x, y, width, height, u, v, uWidth, vHeight, texW, texH)
```

一开始误用了 9 参版本，结果页签上只出现立绘的左上角 —— 看上去像「被裁掉」。用 11 参版本、
目标 16×16、UV 取满 0..32 才是正确缩放；这个重载 z 固定为 0，所以用 `poseStack.translate(0, 0, 100)`
补回原版 `itemRenderer.blitOffset = 100` 的层级。

嫌图标小可以把 `futo$tabIconSize` 改成 24：页签底图 28×32，原版图标位置留了 6（左右）/ 9（上）
的边距，24 撑满后横向还剩 2 像素、纵向 3 像素，不会压到圆角。贴图不用动。

三张图都由 `port/make-icons.py` 同步（纯标准库，不需要 Pillow）：两个产物都是 `pack.png` 的
**逐字节副本**，脚本里没有任何缩放 —— 缩放已经由 GPU 在绘制时做那一次了，离线再缩一次只会
把信息丢两遍，而且会把尺寸焊死（页签那张要同时服务 16 / 20 / 24 几种画法）。
一旦换美术，只要替换 `pack.png` 再跑一次：

```bash
python port/make-icons.py
```

> 踩过的坑：早先这脚本会在离线把 32×32 降到 16×16，出来的页签在游戏里糊得不能看。
> 一半责任在脚本自带的 PNG 解码器 —— filter type 3（Average）那一条忘了加**上一行**的像素，
> 而这张图正好用了 Average 滤波，于是每次降采样都是在错乱的像素上算的。修好后 `pack.png`
> 解出 570 个不透明像素、0 个半透明像素；脚本每次运行都会打印这两个数（换美术后数字会变，
> 但「半透明像素 = 0」应当一直成立），异常就说明解码器又坏了。

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
只验证「右键打开界面」这类**服务端交互**时不需要客户端：
`gradlew runServer -PsnackSelftest` 会放一个零食柜、用假玩家调一遍
`BlockState#use`、断言开出 27 格箱子界面，然后自己停服（见 `docs_dev/03`）。
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
   它**还会把 mods/ 里本模组其它版本**（`touhoulittlemaid-fabric-*.jar`）一起移进备份目录 ——
   产物名带 `mod_version`，每换一次版本就会多留一个旧 jar，而两个同 `modid` 的 jar 会让
   Fabric Loader 直接拒绝启动（或悄悄加载旧的那个）。脚本也不再写死版本号，改为在
   `build/libs` 里取最新的非 `-sources` 产物，`port/check-jar.ps1` 同理；
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

## 部署时游戏必须已退出（否则下次加载类必定崩）

`port/deploy-jar.ps1` 会用新 jar 覆盖测试实例 `mods/` 里的那一份。这件事在 Windows 上
<b>不会</b>被系统拒绝：JVM 打开 jar 时是带 `FILE_SHARE_WRITE` 的，别人照样能改写文件内容。

后果是延迟发作的：JVM 手里的 ZipFile 仍按旧偏移量读新字节，下一次要加载某个类时抛

```
java.lang.RuntimeException: Failed to load class file for '<某个类>'!
Caused by: java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)
```

报的是「恰好接下来要加载的那个类」，与真正的原因（部署动作）毫无关系 —— 2026-11-28 就
因此把「在零食柜上放蛋糕会崩」当成了方块逻辑的 bug，实际只是那次部署盖在了一个正在运行的游戏上。

所以脚本现在先查 `Get-CimInstance Win32_Process` 里有没有命令行含 `1.18.2-Fabric-test` 的
`java.exe` / `javaw.exe`，有就 `throw`。`--gameDir` 在 HMCL 与 loom 的 `runClient` / `runServer`
命令行里都有，与用哪个 java、带哪些参数无关。

`Get-CimInstance` 不可用的环境（老 PowerShell / 被策略限制）会降级成 `Get-Process java` 的
粗查：那种情况下无法区分实例，所以只写警告、不阻断 —— 否则开发时开着的 `runClient`
会让每一次部署都失败。

配套习惯：改完 jar 先关游戏，再部署，再启动。这条和 `port/kill-dev-run.ps1`（只杀
`loom-cache/launch.cfg` 匹配到的进程）一起用，不要手动 `taskkill /IM java.exe`。
