# -*- coding: utf-8 -*-
"""在 README 的扫帚小节里补上「潜行键被原版当成下车」这一节，并重写「验证的边界」。

用精确锚点 + 出现次数断言，避免 insert_edit_into_file 在大文件上截断。
"""
import io
import sys

PATH = r"D:\codename_EndDavid\hello java\touhou_little_maid_futo\README.md"


def crlf(s):
    return s.replace("\r\n", "\n").replace("\n", "\r\n")


OLD = crlf("""### 验证的边界
能自动验证的部分：构建通过、客户端带新 KeyMapping 正常启动（重复注册会在 init 抛异常，
这次没有）、两个包的服务端接收器都注册成功。
**不能自动验证的部分**：实际按键和准星行为都需要真人操作 —— 骑上扫帚、按 C 看掉不掉下来，
对着方块按中键看取到的是方块还是扫帚。这两条我留给你实测。""")

NEW = crlf("""### 3. 潜行键被原版当成了「下车」
这是补上 C 键之后才暴露的第二个问题：**左 Shift 和 C 都能下车**，于是下降彻底没了。
原因不在扫帚，在原版：
```java
// Player#rideTick（1.18.2 字节码：isClientSide → wantsToStopRiding → stopRiding → setShiftKeyDown(false)）
public void rideTick() {
    if (!this.level.isClientSide && this.wantsToStopRiding() && this.isPassenger()) {
        this.stopRiding();
        this.setShiftKeyDown(false);
        return;
    }
    ...
}

// Player#wantsToStopRiding
protected boolean wantsToStopRiding() { return isShiftKeyDown(); }
```
也就是说**原版规则是「按着潜行键就从载具上下来」**（马、船、矿车都一样）。
扫帚把潜行占用成「下降」之后，这条规则直接把下降顶掉了：按一下 Shift 人就掉下去，
`keySneak ? -0.2f` 那行一次都执行不到。

上游 1.20.1 的修法是一条 Mixin 注入（`mixin/PlayerMixin#tlmWantsToStopRiding`）：
```java
@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
private void tlmWantsToStopRiding(CallbackInfoReturnable<Boolean> cir) {
    Player player = (Player) (Object) this;
    if (player.isShiftKeyDown() && player.getVehicle() instanceof EntityBroom) {
        cir.setReturnValue(false);
    }
}
```
本移植版逐字对齐，落在 `mixin/PlayerBroomMixin`，注册在**公共** mixin 列表
（`Player#rideTick` 里那条判断带着 `!isClientSide`，真正生效的是服务端）。

**为什么不能用别的写法：**
- `wantsToStopRiding()` 是 `protected`，扫帚改不了别人身上的判定；
- 在 `EntityBroom#tickRidden` 里每 tick 调 `player.setShiftKeyDown(false)` 不行 ——
  服务端每 tick 都会被 `ServerboundPlayerInputPacket` 写回真实按键
  （`ServerPlayer#setPlayerInput` 的四个写入全在 `if (isPassenger())` 里面），
  而且那样下降自己也就读不到潜行了；
- 改 `EntityBroom#canRide` 没用 —— 那是「上车」判定，不是「下车」判定。

注入点核对过 1.18.2 的字节码：`Player` 里 `wantsToStopRiding` 只被 `rideTick` 调用一次，
返回值就是 `isShiftKeyDown()`，与 1.20.1 完全同形，所以这条上游补丁可以原样搬过来。
### 验证的边界
Mixin 这种改动没法靠「跑一遍程序」证明 —— 它改的是原版方法的返回值。
这次的办法是**打开 Mixin 的导出开关，把改写后的 `Player` 类 dump 出来看字节码**：
```
JAVA_TOOL_OPTIONS=-Dmixin.debug.export=true gradlew runClient -PjoinServer
```
导出到 `run/.mixin.out/class/.../Player.class`，反汇编后是这样：
```
public boolean wantsToStopRiding();
   0: new  CallbackInfoReturnable("wantsToStopRiding", true)
  14: invokespecial handler$zzi000$touhou_little_maid$tlmWantsToStopRiding
  21: ifeq 29
  24: return cir.getReturnValueZ()
  29: invokevirtual isShiftKeyDown()Z
  33: ireturn
```
注入确实落在方法头，取消时走 `getReturnValueZ()`，未取消则照旧返回 `isShiftKeyDown()`。
同时客户端带着这条 Mixin 连上本地服务端跑过一次，`run/crash-reports` 为空、日志里没有 mixin 报错。

**不能自动验证的部分**：真人按键。骑上扫帚按住左 Shift 应该**稳定下降、人还在扫帚上**，
按 C 才是下来；对着前面的方块按中键应该取到方块而不是扫帚。这三条留给你实测。""")


def main():
    with io.open(PATH, "r", encoding="utf-8", newline="") as f:
        text = f.read()
    if "\r\n" not in text:
        print("WARN: file is not CRLF")
    count = text.count(OLD)
    print("anchor count =", count)
    if count != 1:
        print("ABORT: anchor not unique")
        return 1
    text = text.replace(OLD, NEW)
    with io.open(PATH, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("written, lines =", text.count("\n") + 1)
    return 0


if __name__ == "__main__":
    sys.exit(main())

