package com.github.tartaricacid.touhoulittlemaid.datapack.pojo;
import com.google.gson.annotations.SerializedName;
import java.util.List;
/**
 * 一条残局数据，对应 {@code data/<namespace>/board_states/*.json} 里的一项。
 *
 * <pre>
 * {
 *   "tags": ["library"],                       // 取用方式：目前战利品只认 library
 *   "display": {
 *     "description": "board_state....moon",    // 语言键，不是明文
 *     "author": "欢乐小游戏"
 *   },
 *   "data": "eNpjYGAmYMTPZ8SreAgAdP8w0twTAAhFAAk, 8, 8_9",   // 棋局本身
 *   "weight": 1                                // 同一件道具内部抽中的相对权重
 * }
 * </pre>
 *
 * <h2>为什么这里<b>不是</b> record</h2>
 * <p>上游 1.20.1 用的是 {@code record}，本移植版一开始也照抄了 —— 然后在真实服务端上直接崩了：</p>
 *
 * <pre>
 * AssertionError (GSON 2.8.9): java.lang.IllegalAccessException:
 *     Can not set final java.util.List field BoardStateRecord.tags to java.util.ArrayList
 * </pre>
 *
 * <p>1.18.2 自带的是 <b>Gson 2.8.9</b>，它靠「反射设字段」填充对象，而 record 的字段是
 * {@code final}，设不进去 —— Gson 支持 record 是 2.10 才有的。所以这里退回普通类：
 * 字段可变，Gson 能写；方法名保持与上游一致（{@code tags()} / {@code data()} …），
 * 调用方不用改。</p>
 *
 * <p>另外那个异常是 {@link AssertionError}（{@code Error}，不是 {@code Exception}），
 * 所以 {@code BoardStateDataReload} 里必须 {@code catch (Throwable)} ——
 * 否则一个写错的数据包能把整个服务端启动带走。</p>
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
