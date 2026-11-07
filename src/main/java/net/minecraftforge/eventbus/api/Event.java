package net.minecraftforge.eventbus.api;

/**
 * Futo 兼容层：Forge 事件基类。
 * 只保留上游模组真正用到的语义（cancel / result / phase）。
 */
public class Event {
    private boolean canceled = false;
    private Result result = Result.DEFAULT;
    private EventPriority phase = EventPriority.NORMAL;

    public boolean isCancelable() {
        return this.getClass().isAnnotationPresent(Cancelable.class);
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean hasResult() {
        return false;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public EventPriority getPhase() {
        return phase;
    }

    public void setPhase(EventPriority phase) {
        this.phase = phase;
    }

    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }
}

