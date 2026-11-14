import io

PATH = "src/main/java/com/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid.java"

with io.open(PATH, "r", encoding="utf-8", newline="") as f:
    text = f.read()

nl = "\r\n"
assert text.count(nl) > 3000


def replace_once(old, new):
    global text
    cnt = text.count(old)
    assert cnt == 1, "expected exactly one match, got %d for: %s" % (cnt, old[:70])
    text = text.replace(old, new, 1)


GETTER = (
    "    public com.github.tartaricacid.touhoulittlemaid.entity.favorability.FavorabilityManager getFavorabilityManager() {"
    + nl
    + "        return this.favorabilityManager;"
    + nl
    + "    }"
)

MANAGER = nl.join([
    "",
    "    /**",
    "     * \u5bf9\u5c40\u8bb0\u5f55\uff08\u4e94\u5b50\u68cb\u80dc\u573a + \u8fd9\u4e00\u5c40\u7684\u80dc\u8d1f\u8868\u60c5\uff09\uff0c",
    "     * \u5bf9\u5e94\u4e0a\u6e38 {@code entity/passive/MaidGameRecordManager}\u3002",
    "     *",
    "     * <p>\u4e24\u4e2a {@code EntityDataAccessor} \u58f0\u660e\u5728\u90a3\u4e2a\u7c7b\u91cc\u800c\u4e0d\u662f\u672c\u6587\u4ef6\uff0c",
    "     * \u53ea\u662f\u4e3a\u4e86\u5c11\u6539\u4e00\u6b21\u8fd9\u4e2a\u5f88\u957f\u7684\u6587\u4ef6\uff1b\u540c\u6b65\u6570\u636e\u7684 {@code define}",
    "     * \u4ecd\u7136\u5728 {@code defineSynchedData()} \u91cc\u5b8c\u6210\u3002</p>",
    "     */",
    "    private final MaidGameRecordManager gameRecordManager = new MaidGameRecordManager(this);",
    "",
    "    public MaidGameRecordManager getGameRecordManager() {",
    "        return this.gameRecordManager;",
    "    }",
    "",
    "    /**",
    "     * \u5f3a\u5236\u5199\u5165\u540c\u6b65\u6570\u636e\u3002",
    "     *",
    "     * <p>\u4e0a\u6e38\u76f4\u63a5\u64cd\u7edc {@code DataItem} \u6765\u7ed5\u5f00\u300c\u503c\u76f8\u540c\u5c31\u4e0d\u53d1\u300d\u7684\u4f18\u5316\uff1b",
    "     * 1.18.2 \u7684 {@code SynchedEntityData#set} \u672c\u8eab\u5c31\u6709 {@code force} \u53c2\u6570\uff0c\u76f4\u63a5\u8f6c\u53d1\u5373\u53ef\u3002</p>",
    "     */",
    "    public <T> void forceSyncData(net.minecraft.network.syncher.EntityDataAccessor<T> key, T value, boolean force) {",
    "        this.entityData.set(key, value, force);",
    "    }",
])
replace_once(GETTER, GETTER + nl + MANAGER)


DEFINE = "        MaidSubConfig.define(this.entityData);"
replace_once(
    DEFINE,
    DEFINE + nl
    + "        // \u5bf9\u5c40\u8bb0\u5f55\u7684\u4e24\u4e2a\u540c\u6b65\u4f4d\uff1a\u4e94\u5b50\u68cb\u80dc\u573a\u6570\u636e\u4e0e\u8fd9\u4e00\u5c40\u7684\u80dc\u8d1f"
    + nl
    + "        this.gameRecordManager.defineSynchedData(this.entityData);")


SAVE = "        this.favorabilityManager.save(tag);"
replace_once(SAVE, SAVE + nl + "        this.gameRecordManager.addAdditionalSaveData(tag);")

LOAD = "        this.favorabilityManager.load(tag);"
replace_once(LOAD, LOAD + nl + "        this.gameRecordManager.readAdditionalSaveData(tag);")


TICK_ANCHOR = "        if (this.backpackDelay > 0) {"
replace_once(
    TICK_ANCHOR,
    "        // \u79bb\u5f00\u5ea7\u4f4d\uff08\u68cb\u76d8 / \u5750\u57ab\uff09\u540e\u6e05\u6389\u4e0a\u4e00\u5c40\u7684\u80dc\u8d1f\u8868\u60c5" + nl
    + "        this.gameRecordManager.tick();" + nl
    + TICK_ANCHOR)


with io.open(PATH, "w", encoding="utf-8", newline="") as f:
    f.write(text)

print("lines", text.count(nl))
print("replacement-chars", text.count("\ufffd"))

