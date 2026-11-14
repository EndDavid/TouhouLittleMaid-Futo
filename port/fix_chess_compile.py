import io

BASE = "src/main/java/com/github/tartaricacid/touhoulittlemaid/"


def patch(path, pairs):
    with io.open(path, "r", encoding="utf-8", newline="") as f:
        text = f.read()
    nl = "\r\n" if "\r\n" in text else "\n"
    for old, new in pairs:
        old_l = old.replace("\n", nl)
        new_l = new.replace("\n", nl)
        cnt = text.count(old_l)
        assert cnt == 1, "%s: expected 1 match, got %d for %r" % (path, cnt, old_l[:60])
        text = text.replace(old_l, new_l, 1)
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("patched", path)


# ------------------------------------------------------------------ EntitySit
patch(BASE + "entity/item/EntitySit.java", [
    ("""    /**
     * \u4e0a\u6e38\u8fd9\u91cc\u7528 Forge \u7684 {@code NetworkHooks.getEntitySpawningPacket}\uff1b
     * Fabric / \u539f\u7248\u7528\u7236\u7c7b\u9ed8\u8ba4\u5b9e\u73b0\u5373\u53ef\uff08{@code ClientboundAddEntityPacket}\uff09\u3002
     */
    @Override
    public Packet<?> getAddEntityPacket() {
        return super.getAddEntityPacket();
    }""",
     """    /**
     * \u751f\u6210\u751f\u6210\u5305\u3002
     *
     * <p>1.18.2 \u7684 {@code Entity#getAddEntityPacket} \u662f <b>abstract</b>\uff08javap \u786e\u8ba4\uff09\uff0c
     * \u6ca1\u6709\u7236\u7c7b\u9ed8\u8ba4\u5b9e\u73b0\u53ef\u4ee5\u8f6c\u53d1\u3002\u4e0a\u6e38\u7528 Forge \u7684
     * {@code NetworkHooks.getEntitySpawningPacket}\uff1b\u539f\u7248\u5bf9\u5e94\u7269\u5c31\u662f
     * {@link ClientboundAddEntityPacket}\uff0c\u5b83\u4f1a\u628a\u5b9e\u4f53\u7c7b\u578b\u3001UUID\u3001\u5750\u6807\u3001\u65cb\u8f6c\u4e0e\u6570\u636e
     * \u4e00\u5e76\u4e0b\u53d1\u3002\u5ea7\u4f4d\u5b9e\u4f53\u6ca1\u6709\u81ea\u5b9a\u4e49\u62d6\u5c3e\u6570\u636e\uff0c\u6240\u4ee5\u76f4\u63a5\u7528\u5b83\u5c31\u591f\u3002</p>
     */
    @Override
    public Packet<?> getAddEntityPacket() {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this);
    }"""),
])

# ------------------------------------------------------------------ MaidBoardGameTask
patch(BASE + "entity/ai/brain/task/MaidBoardGameTask.java", [
    ("InitEntities.TARGET_POS.get(), MemoryStatus.VALUE_ABSENT", "InitEntities.TARGET_POS, MemoryStatus.VALUE_ABSENT"),
    ("maid.getBrain().setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(gamePos));",
     "maid.getBrain().setMemory(InitEntities.TARGET_POS, new BlockPosTracker(gamePos));"),
    ("maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());",
     "maid.getBrain().eraseMemory(InitEntities.TARGET_POS);"),
    ("maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).ifPresent((targetPos) -> {",
     "maid.getBrain().getMemory(InitEntities.TARGET_POS).ifPresent((targetPos) -> {"),
])

# ------------------------------------------------------------------ EntityMaid javadoc
patch(BASE + "entity/passive/EntityMaid.java", [
    ("""    /**
     * \u5f3a\u5236\u5199\u5165\u540c\u6b65\u6570\u636e\u3002
     *
     * <p>\u4e0a\u6e38\u76f4\u63a5\u64cd\u7edc {@code DataItem} \u6765\u7ed5\u5f00\u300c\u503c\u76f8\u540c\u5c31\u4e0d\u53d1\u300d\u7684\u4f18\u5316\uff1b
     * 1.18.2 \u7684 {@code SynchedEntityData#set} \u672c\u8eab\u5c31\u6709 {@code force} \u53c2\u6570\uff0c\u76f4\u63a5\u8f6c\u53d1\u5373\u53ef\u3002</p>
     */""",
     """    /**
     * \u5199\u5165\u540c\u6b65\u6570\u636e\u3002
     *
     * <p><b>\u4e3a\u4ec0\u4e48\u4e0d\u80fd\u539f\u5730\u4fee\u6539\u518d\u5199\u56de\uff1a</b>\u53cd\u7f16\u8bd1\u786e\u8ba4 1.18.2 \u7684
     * {@code SynchedEntityData#set} \u7528 {@code ObjectUtils.notEqual(\u65b0\u503c, \u65e7\u503c)} \u5224\u5b9a\u8981\u4e0d\u8981\u6807\u810f\uff0c
     * \u800c {@code CompoundTag} \u7684 {@code equals} \u6bd4\u7684\u662f<b>\u5185\u5bb9</b>\u3002\u82e5\u53d6\u51fa\u540c\u4e00\u4e2a\u5b9e\u4f8b\u3001
     * \u6539\u5b8c\u518d\u585e\u56de\u53bb\uff0c\u300c\u65b0\u503c\u300d\u548c\u300c\u65e7\u503c\u300d\u662f\u540c\u4e00\u4e2a\u5bf9\u8c61\uff0c\u5185\u5bb9\u4e5f\u5c31\u76f8\u7b49
     * \u2192 \u4e0d\u6807\u810f \u2192 \u5ba2\u6237\u7aef\u6c38\u8fdc\u6536\u4e0d\u5230\u66f4\u65b0\u3002</p>
     *
     * <p>\u4e0a\u6e38\u4e3a\u6b64\u76f4\u63a5\u64cd\u7edc {@code DataItem} \u7ed5\u8fc7\u5224\u5b9a\uff1b1.18.2 \u7684 {@code DataItem} \u4e0e
     * {@code getItem} \u90fd\u662f private\uff0c\u6ca1\u6709\u7b49\u4ef7\u5165\u53e3\uff0c\u4e5f\u6ca1\u6709\u63a5\u53d7 {@code force} \u7684\u91cd\u8f7d\u3002
     * \u8fd9\u91cc\u6539\u4e3a<b>\u8c03\u7528\u65b9\u4f20\u526f\u672c</b>\uff08\u89c1 {@code MaidGameRecordManager#getGameSkill}\uff09\uff0c
     * \u5185\u5bb9\u5dee\u5f02\u5c31\u80fd\u88ab\u6b63\u786e\u8bc6\u522b\u3002\u65b9\u6cd5\u540d\u4fdd\u7559\u4e0e\u4e0a\u6e38\u4e00\u81f4\uff0c\u4fbf\u4e8e\u5bf9\u7167\u3002</p>
     */"""),
])

