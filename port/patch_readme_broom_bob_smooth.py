"""Append the "why the idle bob looked stiff" section to README.md.

README is CRLF + UTF-8 without BOM and the repo is byte-managed (`* -text`),
so read/write bytes and force CRLF.
"""
import pathlib
import sys

README = pathlib.Path(__file__).resolve().parent.parent / 'README.md'
ANCHOR = '## 扫帚浮动为什么看着生硬：两端都在算同一条正弦'

SECTION = """## 扫帚浮动为什么看着生硬：两端都在算同一条正弦

上一轮把浮动加进了 `EntityBroom#tickRidden`，公式与上游 1.20.1 一字不差，
但看起来一顿一顿的。原因不在那行 `Math.sin`，在**它在哪个位置被调用**。

### 上游的结构：只有一侧在算位置

1.20.1 的 `LivingEntity#travelRidden`（这次从 1.21.1 的 NeoForge 源码包里逐行核对过）：

```java
private void travelRidden(Player player, Vec3 vec3) {
    Vec3 input = this.getRiddenInput(player, vec3);
    this.tickRidden(player, input);          // 浮动、朝向：两端都跑
    if (this.isControlledByLocalInstance()) {
        this.setSpeed(this.getRiddenSpeed(player));
        this.travel(input);                  // 只有这一侧真的 move
    } else {
        this.calculateEntityAnimation(false);
        this.setDeltaMovement(Vec3.ZERO);    // 另一侧：速度清零，不动
        this.tryCheckInsideBlocks();
    }
}
```

两件事同时成立：

1. `tickRidden` 在分支**之前**，所以「叠一条正弦速度」两端都会做；
2. 但只有「本地操纵」的那一侧会 `travel()` → `move()`，
   另一侧的速度当场被清零，位置完全来自客户端上行的 `ServerboundMoveVehiclePacket`。

### 本移植版之前错在哪

`EntityBroom#travel` 里无条件 `rideControl(player)` + `move(...)`，
也就是**服务端也在按自己的 `tickCount` 相位算同一条正弦、也在挪扫帚**。

而 1.18.2 的 `Entity#lerpTo(double, double, double, float, float, int, boolean)`
字节码只有两句 —— `setPos` 和 `setRot`，**没有任何插值**
（1.16 那种 `lerpSteps` 平滑在 `Entity` 上已经没了，只剩 `LivingEntity` 自己那一套）。
于是客户端每收到一次位置包，就被硬按到服务端那条相位不同的正弦上：

- 客户端：`y = A·sin(ω·t)`
- 服务端：`y = A·sin(ω·(t+Δ))`，`Δ` 是两个实体的 `tickCount` 差，
  再加上服务端读到的 `zza` / `isShiftKeyDown` 总比客户端晚一个包

结果是每 tick 在两条正弦之间来回跳 —— 幅度不大但方向乱，
肉眼看到的就是「生硬」，而不是平滑的上下漂。

### 改法

删掉 `aiStep()` 覆写，把「`travelRidden` → `tickRidden` → `travel`」压进 `travel` 一个方法，
顺序与上游一致：

```java
@Override
public void travel(Vec3 vec3) {
    Entity controlling = this.getControllingPassenger();
    if (controlling instanceof Player player && this.isAlive()) {
        this.tickRidden(player);                        // 浮动 + 朝向：两端都跑
        if (!this.isControlledByLocalInstance()) {
            this.setDeltaMovement(Vec3.ZERO);           // 非本地操纵：不模拟
            this.tryCheckInsideBlocks();
            return;
        }
        if (this.hasPassenger(e -> e instanceof EntityMaid)) {
            this.rideControl(player);
            this.move(MoverType.SELF, this.getDeltaMovement());
            return;
        }
    }
    // ...原有的「没有玩家 + 女仆就往下掉」
}
```

`tryCheckInsideBlocks()` 在 1.18.2 里是 `tryCheckInsideBlocks`（不是 1.19 的 `checkInsideBlocks` 直接调），
它只是外面套了崩溃报告，语义一致。

顺手修掉的还有浮动的相位：原来 `tickRidden` 放在 `aiStep` 末尾，
等于「先 `move` 再叠速度」，比上游整整晚一个 tick；
现在它与上游一样，在同一个 tick 里「叠速度 → 减速 → 移动」。
公式本身没动，`0.003 * sin(tickCount * PI / 18)`、周期 36 tick，
配合 `rideControl` 无输入时的 `scale(0.75)`，稳态位移大约 ±4 厘米，
和上游是同一条曲线。

### 验证的边界

能证明的：`javap` 出来的 `travel` 字节码顺序就是
`tickRidden` → `isControlledByLocalInstance` →（false）`setDeltaMovement(ZERO)` + `tryCheckInsideBlocks`
→（true）`rideControl` + `move`，与上游 `travelRidden` 的分支逐条对应；
`aiStep` 的覆写已经消失。

冒烟测试跑过一次：服务端召唤一把挂着女仆的扫帚，从 y=70 落到 y=57 稳定落地、
`OnGround=1b`、日志无异常 —— 也就是说「没有玩家操纵」那条老路径没有被这次改动破坏。

**没法自动验证的**：真人骑上去的手感。1.18.2 没有 `/ride`，服务端塞不上玩家，
而浮动只在「有玩家操纵」时才有。这条仍然要你实测：
现在扫帚静止悬停时应该是一条平滑的正弦，而不是之前那种碎抖。
"""


def main():
    text = README.read_bytes().decode('utf-8')
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

