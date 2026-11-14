import io

p = "src/main/java/com/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid.java"
t = io.open(p, encoding="utf-8", newline="").read()
nl = "\r\n"

old = nl.join([
    "    @Override",
    "    public boolean startRiding(Entity vehicle, boolean force) {",
    "        if (!this.level.isClientSide && !force && !this.isRideable()) {",
    "            return false;",
    "        }",
    "        return super.startRiding(vehicle, force);",
    "    }",
])
assert t.count(old) == 1, t.count(old)

new = nl.join([
    "    @Override",
    "    public boolean startRiding(Entity vehicle, boolean force) {",
    "        if (!this.level.isClientSide && !force) {",
    "            // \u5bf9\u9f50\u4e0a\u6e38 {@code MaidMountEvent}\uff0c\u4e24\u6761\u89c4\u5219\u90fd\u4e0d\u80fd\u6f0f\uff1a",
    "            //",
    "            // 1. \u5ea7\u4f4d\uff08{@code EntitySit}\uff09\u4e0d\u53d7\u300c\u5973\u4ec6\u9a91\u4e58\u300d\u5f00\u5173\u5f71\u54cd\u3002\u4e0a\u6e38\u5199\u7684\u5c31\u662f",
    "            //    {@code !(entityBeingMounted instanceof EntitySit)}\u3002\u8fd9\u4e2a\u5f00\u5173\u7ba1\u7684\u662f\u300c\u62b1\u7740\u5979\u8d70\u300d\uff0c",
    "            //    \u800c\u68cb\u76d8 / \u5750\u57ab\u7684\u5ea7\u4f4d\u662f\u5979\u81ea\u5df1\u8d70\u8fc7\u53bb\u5750\u4e0b\u7684\u2014\u2014\u5173\u6389\u9a91\u4e58\u4e0d\u8be5\u8ba9\u5979",
    "            //    \u8fde\u4e0b\u68cb\u90fd\u505a\u4e0d\u5230\u3002\u6f0f\u4e86\u8fd9\u4e00\u6761\uff0c\u68cb\u76d8\u4f1a\u300c\u770b\u8d77\u6765\u80fd\u5750\u3001\u5b9e\u9645\u5750\u4e0d\u4e0b\u300d\u3002",
    "            // 2. \u8239\u4e0a\u5df2\u7ecf\u653e\u4e86\u5750\u57ab\u3001\u800c\u5750\u57ab\u8fd8\u7a7a\u7740\u65f6\uff0c\u4f18\u5148\u53bb\u5750\u5750\u57ab\u3002\u5426\u5219\u5979\u4f1a\u76f4\u63a5",
    "            //    \u5750\u8fdb\u8239\u91cc\uff0c\u5750\u57ab\u6c38\u8fdc\u7a7a\u7740\u3002",
    "            if (!(vehicle instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit)) {",
    "                if (!this.isRideable()) {",
    "                    return false;",
    "                }",
    "                if (vehicle instanceof net.minecraft.world.entity.vehicle.Boat boat",
    "                        && boat.getControllingPassenger()",
    "                        instanceof com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair chair",
    "                        && chair.getPassengers().isEmpty()) {",
    "                    return false;",
    "                }",
    "            }",
    "        }",
    "        return super.startRiding(vehicle, force);",
    "    }",
])
t = t.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="").write(t)
print("ok", t.count("\ufffd"))

