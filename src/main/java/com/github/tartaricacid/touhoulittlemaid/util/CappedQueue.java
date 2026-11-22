package com.github.tartaricacid.touhoulittlemaid.util;
import org.jetbrains.annotations.Nullable;
import java.util.concurrent.LinkedBlockingDeque;
/**
 * 有上限的双端队列，移植自上游 {@code util/CappedQueue}。
 *
 * <p>AI 聊天的历史记录用它：{@code add} 永远从头部插入，满了就丢掉尾部最旧的一条。
 * 之所以用 {@link LinkedBlockingDeque} 而不是 {@code ArrayDeque}，是因为上游就是如此
 * —— 长度上限由构造器直接交给 JDK，满了 {@code offerFirst} 返回 false，我们自己再腾位置。
 * 注意它<b>不加锁</b>地用于「异步 HTTP 回调 + 服务端主线程」两侧，所以只做最小操作。</p>
 */
public class CappedQueue<E> {
    private final LinkedBlockingDeque<E> deque;
    public CappedQueue(int maxSize) {
        this.deque = new LinkedBlockingDeque<>(maxSize);
    }
    public void add(E element) {
        if (!deque.offerFirst(element)) {
            deque.pollLast();
            deque.offerFirst(element);
        }
    }
    @Nullable
    public E remove() {
        return deque.pollFirst();
    }
    @Nullable
    public E peek() {
        return deque.peekFirst();
    }
    public boolean isEmpty() {
        return deque.isEmpty();
    }
    public int size() {
        return deque.size();
    }
    public LinkedBlockingDeque<E> getDeque() {
        return deque;
    }
}
