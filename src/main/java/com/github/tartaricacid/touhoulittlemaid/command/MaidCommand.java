package com.github.tartaricacid.touhoulittlemaid.command;

import com.github.tartaricacid.touhoulittlemaid.api.backpack.IMaidBackpack;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.capability.IPower;
import com.github.tartaricacid.touhoulittlemaid.capability.PowerCapability;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.PickType;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 女仆管理命令：{@code /tlm ...}。
 *
 * <p><b>为什么需要它：</b>女仆的「工作模式 / 日程 / 居家模式 / 拾取开关 / 模型」都是
 * <b>同步数据</b>（{@code SynchedEntityData}），只能通过对应 setter 修改。
 * 用 {@code /data merge entity ... {MaidTask:"..."}} 是<b>无效</b>的 —— 它只写 NBT，
 * 而 NBT 里的这些字段只在 {@code readAdditionalSaveData}（读存档）时才会被解析。</p>
 *
 * <p>上游是用 GUI 切换这些设置的。GUI（容器 + 网络包）属于后续里程碑，
 * 在那之前本命令是唯一的等价入口；GUI 完成后它仍然保留，用于批量修改与调试。</p>
 *
 * <p>权限等级 2（与 {@code /gamemode} 同级），因为可以批量改写他人女仆的行为。</p>
 */
public final class MaidCommand {
    private static final SuggestionProvider<CommandSourceStack> TASK_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    TaskManager.getTaskIndex().stream().map(task -> task.getUid().getPath()), builder);

    private static final SuggestionProvider<CommandSourceStack> SCHEDULE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(Stream.of(MaidSchedule.values()).map(MaidSchedule::name), builder);
    /** 背包 id 的补全：注册表里的 path，外加表示「空背包」的 {@code empty}。 */
    private static final SuggestionProvider<CommandSourceStack> BACKPACK_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    BackpackManager.getBackpackMap().keySet().stream().map(ResourceLocation::getPath), builder);

    private MaidCommand() {
    }

    public static void init() {
        net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback.EVENT.register(
                (dispatcher, dedicated) -> dispatcher.register(
                        Commands.literal("tlm")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("ai_chat")
                                        .then(Commands.literal("reload")
                                                .executes(MaidCommand::reloadAiChat))
                                        // 调试用：让选中的女仆头顶直接冒一条气泡，
                                        // 不经过任何 HTTP。/tlm ai_chat say @e[type=...] "很长的句子"
                                        .then(Commands.literal("say")
                                                .then(Commands.argument("targets", EntityArgument.entities())
                                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                                .executes(MaidCommand::debugSay)))))
                                .then(Commands.literal("task")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(MaidCommand::queryTask)
                                                .then(Commands.argument("task", StringArgumentType.word())
                                                        .suggests(TASK_SUGGESTIONS)
                                                        .executes(MaidCommand::setTask))))
                                .then(Commands.literal("schedule")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(MaidCommand::querySchedule)
                                                .then(Commands.argument("mode", StringArgumentType.word())
                                                        .suggests(SCHEDULE_SUGGESTIONS)
                                                        .executes(MaidCommand::setSchedule))))
                                .then(Commands.literal("home")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .then(Commands.argument("enable", BoolArgumentType.bool())
                                                        .executes(MaidCommand::setHome))))
                                .then(Commands.literal("pickup")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .then(Commands.argument("enable", BoolArgumentType.bool())
                                                        .executes(MaidCommand::setPickup))
                                                .then(Commands.literal("type")
                                                        .then(Commands.argument("type", StringArgumentType.word())
                                                                .suggests(PICK_TYPE_SUGGESTIONS)
                                                                .executes(MaidCommand::setPickupType)))))
                                .then(Commands.literal("model")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(MaidCommand::queryModel)
                                                .then(Commands.argument("modelId", StringArgumentType.string())
                                                        .executes(MaidCommand::setModel))))
                                .then(Commands.literal("inv")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(MaidCommand::queryInventory)
                                                .then(Commands.literal("clear")
                                                        .executes(MaidCommand::dropInventory)
                                                        .then(Commands.literal("void")
                                                                .executes(MaidCommand::voidInventory)))))
                                .then(Commands.literal("claim")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(MaidCommand::claim)))
                                .then(Commands.literal("info")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(MaidCommand::queryInfo)))
                                .then(Commands.literal("backpack")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(MaidCommand::queryBackpack)
                                                .then(Commands.argument("backpack", StringArgumentType.word())
                                                        .suggests(BACKPACK_SUGGESTIONS)
                                                        .executes(MaidCommand::setBackpack))))
                                .then(Commands.literal("power")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(MaidCommand::queryPower)
                                                .then(Commands.literal("set")
                                                        .then(Commands.argument("count", FloatArgumentType.floatArg(0, 5))
                                                                .executes(MaidCommand::setPower)))
                                                .then(Commands.literal("add")
                                                        .then(Commands.argument("count", FloatArgumentType.floatArg(0, 5))
                                                                .executes(MaidCommand::addPower)))
                                                .then(Commands.literal("min")
                                                        .then(Commands.argument("count", FloatArgumentType.floatArg(0, 5))
                                                                .executes(MaidCommand::minPower)))))
                ));
    }

    // ------------------------------------------------------------------ task

    /**
     * /tlm ai_chat reload：重读站点表与角色设定，并把结果广播给全部在线玩家。
     *
     * <p>AI 的两个数据源都在服务运行中可改：</p>
     * <ul>
     *     <li>config/touhou_little_maid/available_sites.yml（填 api_key）；</li>
     *     <li>config/touhou_little_maid/settings/*.yml（角色设定）。</li>
     * </ul>
     * <p>没有这条命令就只能重启服务器。上游同样提供。</p>
     *
     * <p>权限等级 2：它会重新读取磁盘上的配置，属于管理操作。</p>
     */
    private static int reloadAiChat(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        net.minecraft.server.MinecraftServer server = context.getSource().getServer();
        // 顺序有讲究：站点表先（它决定有哪些站点可选），设定后（它决定哪些模型能聊天）
        com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.AvailableSites.readSites();
        com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.SettingReader.reloadSettings();
        // 必须广播：否则客户端那份 CAN_CHAT_MAID_IDS 与站点表还是旧的
        com.github.tartaricacid.touhoulittlemaid.network.SyncAiSettingPacket.broadcast(server);
        context.getSource().sendSuccess(
                new net.minecraft.network.chat.TranslatableComponent(
                        "message.touhou_little_maid.ai_chat.reload_success"), true);
        return 1;
    }

    /**
     * /tlm ai_chat say &lt;targets&gt; &lt;text&gt;：让女仆头顶直接冒一条气泡。
     *
     * <p>纯调试入口：不校验站点、不发 HTTP，用来检查<b>气泡渲染</b>本身 ——
     * 换行宽度、多条堆叠、坐下时的下移、贴图九宫格。真人验收 AI 回复时
     * 不必为每一句都等一次网络往返。</p>
     */
    private static int debugSay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String text = StringArgumentType.getString(ctx, "text");
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManger
                    .addAiChatText(maid, text);
            count++;
        }
        reply(ctx, "chat bubble sent to " + count + " maid(s)");
        return count;
    }
    private static int queryTask(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (EntityMaid maid : maids(ctx)) {
            reply(ctx, maid.getDisplayName().getString() + " task = " + maid.getTask().getUid());
        }
        return 1;
    }

    private static int setTask(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "task");
        IMaidTask task = resolveTask(raw);
        if (task == null) {
            reply(ctx, "Unknown task: " + raw + " (available: " + availableTasks() + ")");
            return 0;
        }
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setTask(task);
            reply(ctx, maid.getDisplayName().getString() + " task -> " + task.getUid());
            count++;
        }
        return count;
    }

    /**
     * 解析任务 id，容忍三种写法：完整 id（{@code touhou_little_maid:attack}）、
 * 裸 path（{@code attack}）、以及带别命名空间的 id。
     */
    private static IMaidTask resolveTask(String raw) {
        for (IMaidTask task : TaskManager.getTaskIndex()) {
            if (task.getUid().toString().equals(raw) || task.getUid().getPath().equals(raw)) {
                return task;
            }
        }
        return null;
    }

    private static String availableTasks() {
        StringBuilder sb = new StringBuilder();
        for (IMaidTask task : TaskManager.getTaskIndex()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(task.getUid().getPath());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ schedule

    private static int querySchedule(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (EntityMaid maid : maids(ctx)) {
            reply(ctx, maid.getDisplayName().getString() + " schedule = " + maid.getSchedule()
                    + " (now: " + activityName(maid) + ")");
        }
        return 1;
    }

    /**
     * 当前活动的名字.
     *
     * <p>1.18.2 的 {@code Activity#getId()} 不是 public（上游靠 Forge AT 打开），
 * 因此这里通过注册表反查 id，效果相同且不需要 access widener。</p>
     */
    private static String activityName(EntityMaid maid) {
        Activity activity = maid.getScheduleDetail();
        ResourceLocation id = Registry.ACTIVITY.getKey(activity);
        return id == null ? activity.toString() : id.toString();
    }

    private static int setSchedule(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "mode");
        MaidSchedule schedule;
        try {
            schedule = MaidSchedule.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            reply(ctx, "Unknown schedule: " + raw + " (day / night / all)");
            return 0;
        }
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setSchedule(schedule);
            reply(ctx, maid.getDisplayName().getString() + " schedule -> " + schedule);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ 开关

    private static int setHome(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean enable = BoolArgumentType.getBool(ctx, "enable");
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setHomeModeEnable(enable);
            reply(ctx, maid.getDisplayName().getString() + " home mode -> " + enable);
            count++;
        }
        return count;
    }

    private static int setPickup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean enable = BoolArgumentType.getBool(ctx, "enable");
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setPickup(enable);
            reply(ctx, maid.getDisplayName().getString() + " pickup -> " + enable);
            count++;
        }
        return count;
    }

    /**
     * Suggestion provider for the pickup type argument.
     */
    private static final SuggestionProvider<CommandSourceStack> PICK_TYPE_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    Stream.of(PickType.values()).map(PickType::getId), builder);

    /**
     * Set the pickup type of the targeted maids (upstream: the GUI pickup button cycles it).
     *
     * <p>item = items and arrows only; xp = experience orbs and power points only;
     * all = both. It only takes effect while the pickup switch itself is on.</p>
     */
    private static int setPickupType(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "type");
        PickType type = PickType.byId(raw.toLowerCase(Locale.ROOT));
        if (type == null) {
            reply(ctx, "Unknown pickup type: " + raw + " (item / xp / all)");
            return 0;
        }
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setPickupType(type);
            reply(ctx, maid.getDisplayName().getString() + " pickup type -> " + type.getId());
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ model

    private static int queryModel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (EntityMaid maid : maids(ctx)) {
            reply(ctx, maid.getDisplayName().getString() + " model = " + maid.getModelId());
        }
        return 1;
    }

    private static int setModel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "modelId");
        // 模型 id 允许省略命名空间：先试 touhou_little_maid:，再试 geckolib:
        String modelId = raw.contains(":") ? raw : "touhou_little_maid:" + raw;
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setModelId(modelId);
            reply(ctx, maid.getDisplayName().getString() + " model -> " + modelId);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------ 物品栏

    /**
     * 打印女仆物品栏的<b>真实</b>容量与占用情况。
     *
     * <p>纯只读，用于核对「到底有几格」这类问题 —— GUI 尚未移植时没有别的直观入口。</p>
     *
     * <p>注意容量由两部分组成（与上游 {@code EntityMaid#getAvailableInv} 一致）：
     * <b>可用背包格数</b>（空背包为 6）+ <b>双手</b>（主手 / 副手各 1）。</p>
     */
    private static int queryInventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (EntityMaid maid : maids(ctx)) {
            IMaidBackpack backpack = maid.getMaidBackpackType();
            int capacity = backpack.getAvailableMaxContainerIndex();
            int handSlots = maid.getHandsInvWrapper().getSlots();
            ItemStackHandler inv = maid.getMaidInv();
            reply(ctx, maid.getDisplayName().getString()
                    + " backpack=" + backpack.getId()
                    + " capacity=" + capacity
                    + " hands=" + handSlots
                    + " total=" + (capacity + handSlots));
            int used = 0;
            for (int i = 0; i < capacity; i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                used++;
                ResourceLocation itemId = Registry.ITEM.getKey(stack.getItem());
                reply(ctx, "  slot[" + i + "] " + stack.getCount() + "x "
                        + stack.getHoverName().getString() + " (" + itemId + ")");
            }
            reply(ctx, "  used " + used + "/" + capacity
                    + " | main=" + describe(maid.getMainHandItem())
                    + " off=" + describe(maid.getOffhandItem()));
        }
        return 1;
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return stack.getCount() + "x " + Registry.ITEM.getKey(stack.getItem());
    }

    /**
     * 清空女仆物品栏，物品<b>掉在她脚下</b>（可挽回）。
     *
     * <p>只动物品栏 36 格，<b>不动主手 / 副手 / 护甲</b> —— 很多工作模式依赖手持物品
     * （剪羊毛要剪刀、农耕要锄头、采蜜要瓶子），清掉反而更难测。</p>
     *
     * <p>若拾取开关是开着的，掉落物会立刻被她自己捡回去，命令里会提示这一点。</p>
     */
    private static int dropInventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            ItemStackHandler inv = maid.getMaidInv();
            int stacks = countStacks(inv);
            ItemsUtil.dropEntityItems(maid, inv);
            reply(ctx, maid.getDisplayName().getString() + " inventory cleared, " + stacks + " stacks dropped on ground"
                    + (maid.isPickup() ? " (拾取已开启，她可能马上捡回来；可先 /tlm pickup <targets> false)" : ""));
            count++;
        }
        return count;
    }

    /**
     * 清空女仆物品栏，物品<b>直接销毁</b>（不可挽回）。
     *
     * <p>测试时更彻底：不会掉一地东西，也不会被拾取逻辑立刻捡回来。</p>
     */
    private static int voidInventory(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            ItemStackHandler inv = maid.getMaidInv();
            int stacks = countStacks(inv);
            for (int i = 0; i < inv.getSlots(); i++) {
                inv.setStackInSlot(i, ItemStack.EMPTY);
            }
            reply(ctx, maid.getDisplayName().getString() + " inventory voided, " + stacks + " stacks deleted");
            count++;
        }
        return count;
    }

    private static int countStacks(ItemStackHandler inv) {
        int stacks = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                stacks++;
            }
        }
        return stacks;
    }

    // ------------------------------------------------------------------ 背包

    private static int queryBackpack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (EntityMaid maid : maids(ctx)) {
            IMaidBackpack backpack = maid.getMaidBackpackType();
            reply(ctx, maid.getDisplayName().getString() + " backpack = " + backpack.getId()
                    + " (slots " + backpack.getAvailableMaxContainerIndex() + ")");
        }
        return 1;
    }

    /**
     * 设置背包类型。
     *
     * <p>只改类型、不动物品栏内容：容量变小后，超出部分只是被隐藏（仍存在存档里），
     * 想按上游规则「把装不下的吐出来」请用背包物品 / 剪刀在游戏内操作。</p>
     */
    private static int setBackpack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "backpack");
        IMaidBackpack backpack = resolveBackpack(raw);
        if (backpack == null) {
            reply(ctx, "Unknown backpack: " + raw + " (available: " + availableBackpacks() + ")");
            return 0;
        }
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setMaidBackpackType(backpack);
            reply(ctx, maid.getDisplayName().getString() + " backpack -> " + backpack.getId()
                    + " (slots " + backpack.getAvailableMaxContainerIndex() + ")");
            count++;
        }
        return count;
    }

    /**
     * 解析背包 id，容忍三种写法：完整 id、裸 path（如 {@code small_backpack}）、
 * 以及 {@code none} / {@code empty}（清空背包）。
     */
    private static IMaidBackpack resolveBackpack(String raw) {
        if ("none".equalsIgnoreCase(raw) || "empty".equalsIgnoreCase(raw)) {
            return BackpackManager.getEmptyBackpack();
        }
        for (IMaidBackpack backpack : BackpackManager.getBackpackMap().values()) {
            if (backpack.getId().toString().equals(raw) || backpack.getId().getPath().equals(raw)) {
                return backpack;
            }
        }
        return null;
    }

    private static String availableBackpacks() {
        StringBuilder sb = new StringBuilder();
        for (IMaidBackpack backpack : BackpackManager.getBackpackMap().values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(backpack.getId().getPath());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 工具

    private static Collection<? extends EntityMaid> maids(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        return EntityArgument.getEntities(ctx, "targets").stream()
                .filter(EntityMaid.class::isInstance)
                .map(EntityMaid.class::cast)
                .toList();
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String message) {
        Component component = new TextComponent("[TLM] " + message);
        ctx.getSource().sendSuccess(component, false);
    }


    // ------------------------------------------------------------------ 归属修复

    /**
     * 把目标女仆的所有权改判给执行者。
     *
     * <p>用途：开发环境（{@code gradlew runClient}）里 Loom 的玩家名默认每次随机，
     * 离线 UUID 随之改变，于是上一次启动驯服的女仆在本轮「有主但不是你」，
     * 表现为完全无法交互。正式环境一般用不到这个命令。</p>
     */
    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        net.minecraft.world.entity.Entity source = ctx.getSource().getEntity();
        if (!(source instanceof net.minecraft.world.entity.player.Player player)) {
            reply(ctx, "This command must be run by a player.");
            return 0;
        }
        int count = 0;
        for (EntityMaid maid : maids(ctx)) {
            maid.setOwnerUUID(player.getUUID());
            maid.setTame(true);
            reply(ctx, maid.getDisplayName().getString() + " owner -> " + player.getGameProfile().getName());
            count++;
        }
        return count;
    }
    // ------------------------------------------------------------------ 诊断

    /**
     * 打印女仆的关键状态，用于排查「旧存档里的女仆无法交互」这类问题。
     *
     * <p>重点看 {@code tame} 与 {@code owner} 两行：交互要求
     * {@code owner} 与玩家 UUID 一致；若 {@code tame=false} 但 {@code owner} 有值，
     * 读档时的自愈逻辑会把它补回来。</p>
     */
    private static int queryInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (EntityMaid maid : maids(ctx)) {
            String owner = maid.getOwnerUUID() == null ? "null" : maid.getOwnerUUID().toString();
            reply(ctx, maid.getDisplayName().getString()
                    + " tame=" + maid.isTame()
                    + " owner=" + owner);
            reply(ctx, "  task=" + maid.getTask().getUid()
                    + " schedule=" + maid.getSchedule()
                    + " now=" + activityName(maid));
            reply(ctx, "  sitting=" + maid.isMaidInSittingPose()
                    + " orderedToSit=" + maid.isOrderedToSit()
                    + " home=" + maid.isHomeModeEnable()
                    + " pickup=" + maid.isPickup());
            reply(ctx, "  pos=" + maid.blockPosition().toShortString()
                    + " restrict=" + maid.getRestrictCenter().toShortString()
                    + " r=" + maid.getRestrictRadius()
                    + " within=" + maid.isWithinRestriction());
            reply(ctx, "  exp=" + maid.getExperience()
                    + " favorability=" + maid.getFavorability()
                    + " level=" + maid.getFavorabilityManager().getLevel()
                    + " expBottles=" + (maid.getExperience() / 12));

        }
        return 1;
    }

    // ------------------------------------------------------------------ 灵力

    /**
     * 查询灵力（上游 {@code /power get <targets>}）。
     *
     * <p>没有 GUI 数值面板时，这是唯一能精确读到当前灵力的入口 ——
     * 手持御币的 HUD 也能看，但只对自己有效，且只有两位小数。</p>
     */
    private static int queryPower(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            reply(ctx, player.getGameProfile().getName() + " power = "
                    + String.format("%.2f", IPower.getPower(player).get()) + " / "
                    + PowerCapability.MAX_POWER);
        }
        return 1;
    }

    private static int setPower(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        float count = FloatArgumentType.getFloat(ctx, "count");
        int result = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            IPower.getPower(player).set(count);
            reply(ctx, player.getGameProfile().getName() + " power -> " + String.format("%.2f", count));
            result++;
        }
        return result;
    }

    private static int addPower(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        float count = FloatArgumentType.getFloat(ctx, "count");
        int result = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            IPower.getPower(player).add(count);
            reply(ctx, player.getGameProfile().getName() + " power = "
                    + String.format("%.2f", IPower.getPower(player).get()));
            result++;
        }
        return result;
    }

    private static int minPower(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        float count = FloatArgumentType.getFloat(ctx, "count");
        int result = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            IPower.getPower(player).min(count);
            reply(ctx, player.getGameProfile().getName() + " power = "
                    + String.format("%.2f", IPower.getPower(player).get()));
            result++;
        }
        return result;
    }
}