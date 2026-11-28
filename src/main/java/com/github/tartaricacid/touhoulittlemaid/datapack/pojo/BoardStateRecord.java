package com.github.tartaricacid.touhoulittlemaid.datapack.pojo;
import com.google.gson.annotations.SerializedName;
import java.util.List;
/**
 * 一条残局数据，对应 {@code data/<namespace>/board_states/*.json} 里的一项。
 */
public class BoardStateRecord {
    @SerializedName("tags")
    private List<String> tags;
    @SerializedName("display")
    private Display display;
    @SerializedName("data")
    private String data;
    @SerializedName("weight")
    private int weight;
    public List<String> tags() {
        return this.tags;
    }
    public Display display() {
        return this.display;
    }
    public String data() {
        return this.data;
    }
    public int weight() {
        return this.weight;
    }
    /**
     * 这条数据是否带某个标签。
     *
     * <p>{@code tags} 缺失时返回 {@code false} 而不是抛 NPE —— JSON 是数据包作者写的，
     * 少一个字段不该让整个战利品表加载失败。</p>
     */
    public boolean hasTag(String tag) {
        return this.tags != null && this.tags.contains(tag);
    }
    /**
     * 显示信息。{@code description} 是语言键，{@code author} 是原样显示的署名。
     *
     * <p>同样是普通类而不是 record，理由见外层类的注释。</p>
     */
    public static class Display {
        @SerializedName("description")
        private String description;
        @SerializedName("author")
        private String author;
        public String description() {
            return this.description;
        }
        public String author() {
            return this.author;
        }
    }
}
