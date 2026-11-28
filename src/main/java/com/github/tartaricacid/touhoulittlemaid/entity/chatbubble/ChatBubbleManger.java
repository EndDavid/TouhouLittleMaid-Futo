package com.github.tartaricacid.touhoulittlemaid.entity.chatbubble;
import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDamageEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
/**
 * 聊天气泡的服务端管理器，移植自 1.18.2 上游 {@code entity/chatbubble/ChatBubbleManger}。
 */
public final class ChatBubbleManger {
    private static final Cache<Integer, ChatText> INNER_CHAT_TEXT_CACHE =
            CacheBuilder.newBuilder().expireAfterWrite(25, TimeUnit.SECONDS).build();
    private static final Random RANDOM = new Random();
    private static final String IDLE_CHAT_TEXT_ID = "idle";
    private static final String SLEEP_CHAT_TEXT_ID = "sleep";
    private static final String WORK_CHAT_TEXT_ID = "work";
    private static final String MORNING_CHAT_TEXT_ID = "morning";
    private static final String NIGHT_CHAT_TEXT_ID = "night";
    private static final String RAIN_CHAT_TEXT_ID = "rain";
    private static final String SNOW_CHAT_TEXT_ID = "snow";
    private static final String COLD_CHAT_TEXT_ID = "cold";
    private static final String HOT_CHAT_TEXT_ID = "hot";
    private static final String HURT_CHAT_TEXT_ID = "hurt";
    private static final String BEG_CHAT_TEXT_ID = "beg";
    /** 早上 0-3000 刻（约 6:00-9:00）。 */
    private static final long MORNING_START = 0;
    private static final long MORNING_END = 3000;
    /** 傍晚 12000-15000 刻（约 18:00-21:00）。 */
    private static final long EVENING_START = 12000;
    private static final long EVENING_END = 15000;
    private static final float HIGH_TEMPERATURE = 0.95F;
    /** 显示时长，15 秒。 */
    private static final long DURATION = 15 * 1000;
    /** 随机台本检测间隔，60 秒。 */
    private static final int CHECK_RATE = 60 * 20;
    private static boolean registered = false;
    private ChatBubbleManger() {
    }
    /**
     * 注册受伤事件监听（对应上游的 {@code @EventBusSubscriber}）。
     *
     * <p>必须在模组初始化时调用一次。重复调用会被忽略。</p>
     */
    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new HurtListener());
    }
    /** 女仆受伤时弹一条 {@code hurt} 台词（上游 {@code addHurtChatText}）。 */
    private static final class HurtListener {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onMaidDamaged(MaidDamageEvent event) {
            EntityMaid maid = event.getMaid();
            if (maid == null || maid.level.isClientSide) {
                return;
            }
            addSpecialChatText(maid, HURT_CHAT_TEXT_ID);
        }
    }
    // ---------------------------------------------------------------- 三槽位
    /**
     * 往三个槽位里塞一条气泡。
     *
     * <p>选槽规则与上游逐字一致：{@code minEndTime} 从 -1 开始，谁「到期时间 ≤ 当前最小值」
     * 就被选中 —— 于是空槽（-1）优先，其次是最早到期的那个。</p>
     */
    public static void addChatBubble(long endTime, ChatText text, EntityMaid maid) {
        if (System.currentTimeMillis() > endTime) {
            return;
        }
        if (text == ChatText.EMPTY_CHAT_TEXT) {
            return;
        }
        Pair<Long, ChatText> bubbleItem = Pair.of(endTime, text);
        MaidChatBubbles chatBubble = maid.getChatBubble();
        long minEndTime = -1;
        int index = 1;
        Pair<Long, ChatText> bubble1 = chatBubble.getBubble1();
        Pair<Long, ChatText> bubble2 = chatBubble.getBubble2();
        Pair<Long, ChatText> bubble3 = chatBubble.getBubble3();
        if (bubble1.getLeft() <= minEndTime) {
            minEndTime = bubble1.getLeft();
        }
        if (bubble2.getLeft() <= minEndTime) {
            minEndTime = bubble2.getLeft();
            index = 2;
        }
        if (bubble3.getLeft() <= minEndTime) {
            index = 3;
        }
        MaidChatBubbles newChatBubble;
        switch (index) {
            default:
            case 1:
                newChatBubble = new MaidChatBubbles(bubbleItem, bubble2, bubble3);
                break;
            case 2:
                newChatBubble = new MaidChatBubbles(bubble1, bubbleItem, bubble3);
                break;
            case 3:
                newChatBubble = new MaidChatBubbles(bubble1, bubble2, bubbleItem);
                break;
        }
        maid.setChatBubble(newChatBubble);
    }
    public static int getChatBubbleCount(EntityMaid maid) {
        int count = 0;
        MaidChatBubbles chatBubble = maid.getChatBubble();
        if (chatBubble.getBubble1() != MaidChatBubbles.EMPTY) {
            count = count + 1;
        }
        if (chatBubble.getBubble2() != MaidChatBubbles.EMPTY) {
            count = count + 1;
        }
        if (chatBubble.getBubble3() != MaidChatBubbles.EMPTY) {
            count = count + 1;
        }
        return count;
    }
    // ---------------------------------------------------------------- tick
    /**
     * 服务端每 tick 调用。
     *
     * <p>三路随机各自用车牌号与 UUID 的低/高位算偏移，
     * 于是同一刻只有「该说话」的那一部分女仆会说话（上游同款）。</p>
     */
    public static void tick(EntityMaid maid) {
        checkTimeoutChatBubble(maid);
        long offset1 = maid.getUUID().getLeastSignificantBits() % CHECK_RATE;
        long offset2 = maid.getUUID().getMostSignificantBits() % CHECK_RATE;
        if ((maid.tickCount + offset1) % CHECK_RATE == 0) {
            addMainChatText(maid);
        }
        if ((maid.tickCount + offset2) % CHECK_RATE == 0) {
            addSpecialChatText(maid);
        }
        if ((maid.tickCount + offset1 + offset2) % CHECK_RATE == 0) {
            addOtherChatText(maid);
        }
    }
    private static void checkTimeoutChatBubble(EntityMaid maid) {
        MaidChatBubbles chatBubble = maid.getChatBubble();
        Pair<Long, ChatText> bubble1 = chatBubble.getBubble1();
        Pair<Long, ChatText> bubble2 = chatBubble.getBubble2();
        Pair<Long, ChatText> bubble3 = chatBubble.getBubble3();
        long currentTimeMillis = System.currentTimeMillis();
        boolean changed = false;
        if (bubble1 != MaidChatBubbles.EMPTY && currentTimeMillis > bubble1.getLeft()) {
            bubble1 = MaidChatBubbles.EMPTY;
            changed = true;
        }
        if (bubble2 != MaidChatBubbles.EMPTY && currentTimeMillis > bubble2.getLeft()) {
            bubble2 = MaidChatBubbles.EMPTY;
            changed = true;
        }
        if (bubble3 != MaidChatBubbles.EMPTY && currentTimeMillis > bubble3.getLeft()) {
            bubble3 = MaidChatBubbles.EMPTY;
            changed = true;
        }
        // 上游无条件写回；这里只在真的清掉东西时才写，避免每 tick 触发一次同步包
        if (changed) {
            maid.setChatBubble(new MaidChatBubbles(bubble1, bubble2, bubble3));
        }
    }
    // ---------------------------------------------------------------- 内部提示 / AI
    /**
     * 内部提示（翻译键），例如 {@code ai.touhou_little_maid.chat.api_key.empty}。
     *
     * <p>用 {@code {key}} 花括号包起来，渲染时由 {@code ParseI18n} 转成
     * {@code TranslatableComponent} —— 这样客户端语言决定最终文本，
     * 而不是由服务端的语言决定。</p>
     */
    public static void addInnerChatText(EntityMaid maid, String key) {
        if (INNER_CHAT_TEXT_CACHE.getIfPresent(maid.getId()) == null) {
            ChatText chatText = new ChatText(ChatTextType.TEXT, ChatText.EMPTY_ICON_PATH, String.format("{%s}", key));
            INNER_CHAT_TEXT_CACHE.put(maid.getId(), chatText);
            maid.addChatBubble(getEndTime(), chatText);
        }
    }
    /**
     * 可以直接异步加载（HTTP 回调线程调用）。
     */
    public static void addAiChatTextSync(EntityMaid maid, String message) {
        if (StringUtils.isNotBlank(message) && maid.level instanceof ServerLevel serverLevel) {
            MinecraftServer server = serverLevel.getServer();
            server.submit(() -> addAiChatText(maid, message));
        }
    }
    public static void addAiChatText(EntityMaid maid, String message) {
        ChatText chatText = new ChatText(ChatTextType.TEXT, ChatText.EMPTY_ICON_PATH, message);
        maid.addChatBubble(getEndTime(), chatText);
        // 给主人发送聊天栏信息
        if (maid.getOwner() instanceof ServerPlayer player && player.isAlive()) {
            Component name = maid.getName();
            MutableComponent msg = new TextComponent("<").append(name).append(">")
                    .append(StringUtils.SPACE).append(message);
            player.sendMessage(msg.withStyle(ChatFormatting.GRAY), Util.NIL_UUID);
        }
    }
    // ---------------------------------------------------------------- 随机台词
    private static void addMainChatText(EntityMaid maid) {
        Activity activity = maid.getScheduleDetail();
        if (activity == Activity.IDLE) {
            addMainChatText(maid, IDLE_CHAT_TEXT_ID);
            return;
        }
        if (activity == Activity.REST) {
            addMainChatText(maid, SLEEP_CHAT_TEXT_ID);
            return;
        }
        if (activity == Activity.WORK) {
            addWorkChatText(maid, maid.getTask().getUid().getPath());
        }
    }
    private static void addMainChatText(EntityMaid maid, String chatTextId) {
        List<ChatText> chatTexts = getChatTexts(maid, ChatBubbleScripts.Section.MAIN, chatTextId);
        addRandomChat(maid, chatTexts);
    }
    /**
     * 工作状态：{@code work} 的通用台词与「当前任务 id」的专属台词混在一起挑。
     */
    private static void addWorkChatText(EntityMaid maid, String taskId) {
        List<ChatText> chatTexts = getChatTexts(maid, ChatBubbleScripts.Section.MAIN, WORK_CHAT_TEXT_ID);
        chatTexts.addAll(getChatTexts(maid, ChatBubbleScripts.Section.MAIN, taskId));
        addRandomChat(maid, chatTexts);
    }
    /**
     * 按环境挑 special 台词。
     *
     * <p>判定顺序与上游一致：讨食 → 早上 → 傍晚 → 雨 → 雪 → 冷 → 热。
     * 越靠前的优先级越高（例如下雨的清晨先说「早上好」）。</p>
     */
    private static void addSpecialChatText(EntityMaid maid) {
        Level world = maid.level;
        BlockPos pos = maid.blockPosition();
        long dayTime = world.getDayTime();
        Biome biome = world.getBiome(pos).value();
        if (maid.isBegging()) {
            addSpecialChatText(maid, BEG_CHAT_TEXT_ID);
            return;
        }
        // 差不多早上 6:00 - 9:00
        if (MORNING_START < dayTime && dayTime < MORNING_END) {
            addSpecialChatText(maid, MORNING_CHAT_TEXT_ID);
            return;
        }
        // 差不多傍晚 18:00 - 21:00
        if (EVENING_START < dayTime && dayTime < EVENING_END) {
            addSpecialChatText(maid, NIGHT_CHAT_TEXT_ID);
            return;
        }
        if (world.isRaining() && SoundUtil.isRainBiome(biome, pos)) {
            addSpecialChatText(maid, RAIN_CHAT_TEXT_ID);
            return;
        }
        if (world.isRaining() && SoundUtil.isSnowyBiome(biome, pos)) {
            addSpecialChatText(maid, SNOW_CHAT_TEXT_ID);
            return;
        }
        if (biome.coldEnoughToSnow(pos)) {
            addSpecialChatText(maid, COLD_CHAT_TEXT_ID);
            return;
        }
        // 1.18.2 的 Biome#getTemperature(BlockPos) 是 private（上游靠 AT 才能调），
        // 没有 AT 时只能取「基础温度」—— 对「是不是很热」这个判断足够，
        // 区别仅在于山地这类按高度降温的生物群系会判得略热。
        if (biome.getBaseTemperature() > HIGH_TEMPERATURE) {
            addSpecialChatText(maid, HOT_CHAT_TEXT_ID);
        }
    }
    private static void addSpecialChatText(EntityMaid maid, String chatTextId) {
        addRandomChat(maid, getChatTexts(maid, ChatBubbleScripts.Section.SPECIAL, chatTextId));
    }
    /**
     * 饰品自定义台词：第一个返回非空 {@code getChatBubbleId()} 的饰品决定挑哪一类。
     */
    private static void addOtherChatText(EntityMaid maid) {
        maid.getMaidBauble().fireEvent((bauble, stack) -> {
            String chatBubbleId = bauble.getChatBubbleId();
            if (StringUtils.isBlank(chatBubbleId)) {
                return false;
            }
            List<ChatText> chatTexts = getChatTexts(maid, ChatBubbleScripts.Section.OTHER, chatBubbleId);
            addRandomChat(maid, chatTexts);
            return true;
        });
    }
    /**
     * 调试入口：按台本里的某一类台词直接弹一条（{@code /tlm ai_chat bubble}）。
     *
     * <p>与随机 tick 的区别只有「不看时机」：不查作息、不看天气、不掷骰子，
     * 直接把 main / special / other 三段里这个键的全部候选混在一起随机挑一条。
     * 找不到任何候选时什么也不做（命令那边会报 {@code N without script}）。</p>
     *
     * @return 实际挑中的那条；这一类没有台词时 {@code null}
     */
    public static ChatText addDebugChatText(EntityMaid maid, String chatTextId) {
        List<ChatText> chatTexts = Lists.newArrayList();
        chatTexts.addAll(getChatTexts(maid, ChatBubbleScripts.Section.MAIN, chatTextId));
        chatTexts.addAll(getChatTexts(maid, ChatBubbleScripts.Section.SPECIAL, chatTextId));
        chatTexts.addAll(getChatTexts(maid, ChatBubbleScripts.Section.OTHER, chatTextId));
        ChatText picked = getRandomChatText(chatTexts);
        if (picked == ChatText.EMPTY_CHAT_TEXT) {
            return null;
        }
        maid.addChatBubble(getEndTime(), picked);
        return picked;
    }
    private static void addRandomChat(EntityMaid maid, List<ChatText> chatTexts) {
        ChatText randomChat = getRandomChatText(chatTexts);
        if (randomChat == ChatText.EMPTY_CHAT_TEXT) {
            // 这一类没写台词：不要因此塞一条空气泡（上游同样会在这里返回空文本，
            // 由 addChatBubble 拦掉）
            return;
        }
        maid.addChatBubble(getEndTime(), randomChat);
    }
    private static ChatText getRandomChatText(List<ChatText> chatTexts) {
        int length = chatTexts.size();
        if (length == 0) {
            return ChatText.EMPTY_CHAT_TEXT;
        }
        return chatTexts.get(RANDOM.nextInt(length));
    }
    /**
     * 某一只女仆、某一段、某一个键的全部候选台词 = 模型台本 + 书与笔台本。
     */
    private static List<ChatText> getChatTexts(EntityMaid maid, ChatBubbleScripts.Section section, String chatTextId) {
        List<ChatText> result = Lists.newArrayList(
                ChatBubbleScripts.getTexts(ChatBubbleScripts.get(maid.getModelId()), section, chatTextId));
        // 玩家用书与笔写进去的台本，键名同样是 idle / hurt 这些
        result.addAll(maid.getScriptBookManager().getScripts(chatTextId));
        return result;
    }
    private static long getEndTime() {
        return System.currentTimeMillis() + DURATION;
    }
}
