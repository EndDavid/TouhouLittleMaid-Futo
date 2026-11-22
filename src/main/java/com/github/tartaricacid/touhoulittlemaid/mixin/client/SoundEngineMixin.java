package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.client.sound.data.MaidAISoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

/**
 * 让原版声音引擎愿意播放 {@link MaidAISoundInstance} 自带的音频流。
 *
 * <h2>为什么需要这个 mixin</h2>
 * <p>Forge 1.18.2 给 {@code SoundInstance} 加了一个补丁方法
 * {@code getStream(SoundBufferLibrary, Sound, boolean)}，上游 TLM 正是覆写它来播放内存里的
 * TTS 音频。1.18.2 Fabric 的原版接口没有这个方法，而 {@code SoundEngine#play} 在流式分支里
 * 固定调用 {@code this.soundBuffers.getStream(sound.getPath(), looping)} ——
 * 也就是只会去资源包里找文件，永远读不到我们手里的字节。</p>
 *
 * <p>于是这里把那一处调用改写成「先问实例，问不到再走原路」：
 * 效果等价于 Forge 的补丁，但改动面只有一行调用，不会影响其它任何音效。</p>
 *
 * <p><b>为什么匹配 {@code play} 里的这一处：</b>1.18.2 的 {@code SoundEngine#play}
 * 只有一个 {@code SoundBufferLibrary#getStream(ResourceLocation, boolean)} 调用点
 * （另一处是 {@code getCompleteBuffer}，属于非流式分支）；
 * 注册的 {@code maid.ai_chat} 带 {@code "stream": true}，所以我们的实例一定走流式分支。</p>
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Redirect(
            method = "play",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/ResourceLocation;Z)Ljava/util/concurrent/CompletableFuture;")
    )
    private CompletableFuture<AudioStream> touhouLittleMaid$useAISoundStream(
            SoundBufferLibrary library, ResourceLocation path, boolean looping, SoundInstance instance) {
        if (instance instanceof MaidAISoundInstance maidSound) {
            // 解码失败时 createStream() 以 null 完成，这里再退回占位静音流，
            // 避免把 null 交给 SoundEngine（那会在 loadStream 里 NPE）
            return maidSound.createStream().thenCompose(stream ->
                    stream != null
                            ? CompletableFuture.completedFuture(stream)
                            : library.getStream(path, looping));
        }
        return library.getStream(path, looping);
    }
}
