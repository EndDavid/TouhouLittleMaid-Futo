package com.github.tartaricacid.touhoulittlemaid.entity.chatbubble;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「书与笔台本」管理器，移植自上游 {@code entity/chatbubble/MaidScriptBookManager}。
 *
 * <h2>玩法</h2>
 * <ul>
 *     <li>拿<b>成书 / 书与笔</b>右击女仆 → 把书页当作台词装进她身上；</li>
 *     <li>潜行（{@code isDiscrete}）右击 → 清空她的台本；</li>
 *     <li>拿<b>空的书与笔</b>右击已有台本的女仆 → 把台本复制进书页里带走。</li>
 * </ul>
 *
 * <h2>书页格式</h2>
 * <p>每一页由「台词类型」与若干条台词组成，中间用<b>两个换行</b>分隔：</p>
 * <pre>
 * idle
 *
 * 今天也很闲呢
 *
 * 主人，要一起晒太阳吗？
 * </pre>
 * <p>类型名就是 {@link ChatBubbleScripts.Section} 里那些键：{@code idle} / {@code sleep} /
 * {@code work} / 任务 id / {@code morning} / {@code hurt} …（小写，首尾空白会被裁掉）。
 * 页数不足两段（只有类型没有台词）的页会被忽略。</p>
 *
 * <p>成书（{@code WRITTEN_BOOK}）的每一页在 NBT 里是 JSON（{@code {"text":"…"}}），
 * 所以安装时要先解出 {@code text} 字段；书与笔（{@code WRITABLE_BOOK}）的页面就是纯字符串。
 * 这个差异与上游逐行一致。</p>
 */
public class MaidScriptBookManager {
    private static final Gson GSON = new Gson();
    private static final String STORE_TAG = "MaidScriptBook";
    private static final String PAGES_TAG = "pages";
    /** 台词类型与台词之间、台词与台词之间的分隔符（两个换行）。 */
    private static final String SEPARATOR = "\n\n";

    private final Map<String, List<ChatText>> scripts = Maps.newHashMap();
    private ListTag scriptsTags = new ListTag();

    /**
     * 从书里装台本。
     *
     * @return 是否装入成功（书里没有 pages / pages 为空时为 {@code false}）
     */
    public boolean installScript(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(PAGES_TAG, Tag.TAG_LIST)) {
            return false;
        }
        ListTag pagesTag = tag.getList(PAGES_TAG, Tag.TAG_STRING);
        if (pagesTag.isEmpty()) {
            return false;
        }
        if (stack.getItem() == Items.WRITTEN_BOOK) {
            // 成书每页是 JSON：{"text":"…","extra":[…]}
            ListTag list = pagesTag.copy();
            this.scriptsTags = new ListTag();
            for (int i = 0; i < list.size(); i++) {
                BookText bookText = GSON.fromJson(list.getString(i), BookText.class);
                if (bookText != null && bookText.text != null) {
                    this.scriptsTags.add(StringTag.valueOf(bookText.text));
                }
            }
        } else {
            this.scriptsTags = pagesTag.copy();
        }
        this.loadFromTag(this.scriptsTags);
        return true;
    }

    /**
     * 清空台本。
     */
    public void removeScript() {
        this.scripts.clear();
        this.scriptsTags = new ListTag();
    }

    /**
     * 把台本写进（空白的）书与笔。
     *
     * @return 是否写入成功；书里已经有内容时 {@code false}（不覆盖玩家的字）
     */
    public boolean copyScript(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(PAGES_TAG, Tag.TAG_LIST)
                || tag.getList(PAGES_TAG, Tag.TAG_STRING).isEmpty()) {
            CompoundTag stackTag = stack.getOrCreateTag();
            stackTag.put(PAGES_TAG, this.scriptsTags.copy());
            return true;
        }
        return false;
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        compound.put(STORE_TAG, this.scriptsTags);
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains(STORE_TAG, Tag.TAG_LIST)) {
            this.scriptsTags = compound.getList(STORE_TAG, Tag.TAG_STRING);
            this.loadFromTag(this.scriptsTags);
        }
    }

    /**
     * 把书页解析成「类型 → 台词列表」。
     */
    private void loadFromTag(ListTag pages) {
        this.scripts.clear();
        for (int i = 0; i < pages.size(); i++) {
            String pageText = pages.getString(i);
            String[] split = StringUtils.split(pageText, SEPARATOR);
            if (split == null || split.length < 2) {
                continue;
            }
            String type = StringUtils.strip(split[0]).toLowerCase(Locale.US);
            if (StringUtils.isBlank(type)) {
                continue;
            }
            List<ChatText> scriptList = this.scripts.computeIfAbsent(type, k -> Lists.newArrayList());
            for (int j = 1; j < split.length; j++) {
                String script = StringUtils.strip(split[j]);
                if (StringUtils.isNotBlank(script)) {
                    scriptList.add(new ChatText(ChatTextType.TEXT, ChatText.EMPTY_ICON_PATH, script));
                }
            }
        }
    }

    /**
     * 取某一类台词（{@code idle} / {@code hurt} …）。没有时返回空列表。
     */
    public List<ChatText> getScripts(String type) {
        return this.scripts.getOrDefault(type, Lists.newArrayList());
    }

    /**
     * 台本是否为空（书里什么都没装）。
     */
    public boolean isEmpty() {
        return this.scripts.isEmpty();
    }

    /**
     * 成书页面里那一层 JSON 包装。
     */
    private static class BookText {
        @SerializedName("text")
        @Nullable
        private String text;
    }
}

