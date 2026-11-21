"""Append the "broom bobbing / physical box / onboard hint" section to README.md.

README is CRLF + UTF-8 without BOM and the repo is byte-managed (`* -text`),
so everything here is read and written as bytes with explicit CRLF endings.
"""
import pathlib
import sys

README = pathlib.Path(__file__).resolve().parent.parent / 'README.md'
ANCHOR = '## 扫帚：浮动、碰撞箱，还有那句提示'

SECTION = """## 扫帚：浮动、碰撞箱，还有那句提示

上一轮解决的是「按什么键下车」，这一轮补的三件事看着不相干，
其实都来自同一件事：**拿上游 1.20.1 的代码去比 1.18.2 的字节码**。
三件事是：静止时不浮动、靠近扫帚被反复推开抖动、下车提示里的键名还是「左 Shift」。

### 1. 静止浮动：上游有一行正弦，本移植版漏了

上游 1.20.1 的 `EntityBroom#tickRidden` 里有这几行：

```java
// 施加上下晃动
if (!this.onGround()) {
    this.addDeltaMovement(new Vec3(0, 0.003 * Math.sin(this.tickCount * Math.PI / 18), 0));
}
```

周期 `2*PI / (PI/18)` = **36 tick（1.8 秒）**，峰值只有 0.003 格/tick。
扫帚 `setNoGravity(true)`，悬空飞行时 `onGround` 为 false，于是这条速度每 tick
都往 `getDeltaMovement()` 里叠一点；而 `rideControl` 在无输入时是 `scale(0.75)`（快速减速），
两者叠加成一条稳定的小幅振荡，位移幅度只有几厘米 —— 正好是「看得出来、但不会晃到头晕」的量级。

**1.18.2 没有 `Entity#addDeltaMovement`**（1.19+ 才有），等价写法是
`setDeltaMovement(getDeltaMovement().add(0, 0.003 * Math.sin(...), 0))`。

更要紧的一点：**1.18.2 的 `LivingEntity` 里根本没有 `tickRidden` 这个方法**。
这次用 `javap -p` 把 `LivingEntity` 的方法名全列了一遍，确实没有 `tickRidden`，
`travel` 是在 `aiStep` 内部调的（字节码偏移 721）。所以上游那个
`@Override protected void tickRidden(Player, Vec3)` 在 1.18.2 无处可挂。
本移植版本来就是自己覆写 `aiStep()`、再在里头调 `tickRidden(player)`，这行直接加进那个方法即可。

一个刻意的细节：它在 `super.aiStep()` **之后**执行，语义是「先移动、再把浮动速度留给下一 tick」。
上游的 `tickRidden` 在 `travel` 之前，等价于「先叠加、再移动」，两者只差 1 tick 的相位，
肉眼分辨不出；不为此去调整 `aiStep` 里的调用顺序，以免把上一轮验证过的朝向 / 俯仰逻辑搅乱。

### 2. 「卡碰撞箱」抖动：真实碰撞箱不能跟着乘客数变

症状是「靠近扫帚时玩家被反复推开、抖动」，像是服务端与客户端在互相纠正位置。

**根因在本移植版自己身上。** 上游 1.20.1 的写法是：

```java
private AABB physicalBoundingBox = new AABB(Vec3.ZERO, Vec3.ZERO);

@Override
protected AABB makeBoundingBox() {
    AABB aabb = super.makeBoundingBox();
    if (this.getPassengers().size() > 1) {
        this.physicalBoundingBox = new AABB(... aabb.maxY + 1 ...);   // 只存进字段
    } else {
        this.physicalBoundingBox = aabb;
    }
    return aabb;                                                      // 真实碰撞箱永远不变
}
```

也就是说那口「长高一格」的箱子是**另一个箱子**，只在一处被消费：
上游的 `EntityMixin` 在 `Entity#move` 调 `collide` 的前后开关 `inPhysicalCheck`，
再在 `getBoundingBox()` 的 RETURN 上按标志位换箱子 —— **只有解算方块碰撞时**才看得到它。

本移植版最初图省事，直接在 `makeBoundingBox()` 里 `return` 了那个大箱子，
于是「真实碰撞箱」的高度变成了**乘客数的函数**。而乘客数是同步状态：

- 服务端刚把女仆放上扫帚、客户端还在等 `ClientboundSetPassengersPacket` 的那几 tick；
- 两端箱子高度差一整格：同一个位置在一端「撞住了」、在另一端「是空的」；
- 位置包来回纠正 -> 就是那个「卡碰撞箱」的回弹抖动。

更长期的麻烦是：扫帚 `canBeCollidedWith()` 返回 `isAlive()`（上游如此），所以它对玩家是**实心**的；
女仆上车那一瞬间凭空长出一格高的实心墙，恰好站在那个高度里的玩家会被挤出去 ——
「靠近扫帚就抖」的另一半原因在这里。

修法：`makeBoundingBox()` 不再覆写，新增 `getPhysicalBoundingBox()`；
新 mixin `EntityBroomPhysicalBoxMixin` 把 `Entity#collide` 里**取箱子那一次调用**重定向过去。

**为什么不照抄上游的「标志位 + RETURN 注入」**：

- `getBoundingBox()` 是热路径，每个实体每次移动 / 碰撞 / 渲染都调，
  在 RETURN 上做全局判断等于给全服每个实体每 tick 白加一次 `instanceof`；
- 单点重定向没有跨方法状态，也就没有「其它模组中途抛异常导致标志位永久为 true」的风险
  （上游的注释里也提到了这个担忧，只是它靠 `shift = AFTER` 的第二个注入点绕开）。

覆盖范围也核对过 1.18.2 的字节码：`collide` 里 `getBoundingBox()` 只出现一次（偏移 1），
`Entity#move` 里另一个调用在偏移 990、已经在 `collide`（偏移 132）返回之后，
所以两种写法的作用范围等价。

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

### 验证的边界

这三件事里，只有两件能靠字节码证明：

```
# 扫帚自己的类：makeBoundingBox 的覆写已经消失，浮动那几行确实编译进去了
javap -p build/classes/java/main/.../EntityBroom.class
  -> 只有 getPhysicalBoundingBox / tickRidden，没有 makeBoundingBox

# Mixin 导出（JAVA_TOOL_OPTIONS=-Dmixin.debug.export=true gradlew runClient -PjoinServer）
javap -p -c run/.mixin.out/class/net/minecraft/world/entity/Entity.class
  1999: invokespecial redirect$...$futo$physicalBoundingBoxForCollide   # collide 里那一次取箱子

javap -p -c run/.mixin.out/class/net/minecraft/client/multiplayer/ClientPacketListener.class
  -> handler$...$futo$handleSetEntityPassengersPacket 在方法头
  -> 里面 invokestatic DismountBroomKey.getDismountKey + ldc "mount.onboard"
```

运行时也跑过一遍：development server 上 summoned 一把带两个女仆乘客的扫帚，
它带着 `getPhysicalBoundingBox()` 那条「长高一格」的路径从空中落到地面并稳定停住，
服务端日志无异常、`crash-reports` 为空。

**不能自动验证的部分**：那句提示和浮动都要「真人骑上去」才看得见 ——
它由 `ClientboundSetPassengersPacket` 触发，而 1.18.2 没有 `/ride` 命令
（`net/minecraft/server/commands/RideCommand` 在 1.18.2 的 jar 里不存在），
服务端无法把玩家塞上扫帚。这两条留给你实测：骑上扫帚应该看到「按下 C 来脱离」，
静止悬停时扫帚应该以约 1.8 秒的周期轻轻上下浮动。
"""


def main():
    raw = README.read_bytes()
    text = raw.decode('utf-8')
    if ANCHOR in text:
        sys.exit('anchor already present, refusing to duplicate')
    if not text.endswith('\r\n'):
        sys.exit('README does not end with CRLF, refusing to guess')
    section = SECTION.replace('\r\n', '\n').replace('\n', '\r\n')
    out = text + '\r\n' + section
    README.write_bytes(out.encode('utf-8'))
    print('ok, lines before =', text.count('\n'), 'after =', out.count('\n'))


if __name__ == '__main__':
    main()

