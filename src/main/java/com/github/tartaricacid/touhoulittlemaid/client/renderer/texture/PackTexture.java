package com.github.tartaricacid.touhoulittlemaid.client.renderer.texture;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * 「游戏目录里的资源包」贴图，对应上游 {@code renderer/texture/{Zip,File}PackTexture}。
 *
 * <h2>为什么必须自己造一张贴图</h2>
 * <p>{@code tlm_custom_pack/*.zip} 是玩家放在游戏目录里的文件，<b>不属于</b>任何
 * 原版资源包 —— {@code ResourceManager} 根本不知道它的存在。而渲染时用的是
 * {@code RenderSystem.setShaderTexture(0, ResourceLocation)}，那个接口只会去
 * {@code TextureManager} 里查；查不到就画成紫黑格。上游的做法是
 * 「读出 PNG 自己上传成一张纹理」，这里照抄。</p>
 *
 * <h2>两个与上游一致的细节</h2>
 * <ul>
 *     <li><b>上传必须在渲染线程。</b>资源重载发生在后台线程，而
 *         {@code TextureUtil.prepareImage} 会调 GL。上游用
 *         {@code RenderSystem.recordRenderCall} 把整段推到下一帧开头执行，
 *         这里保持同样做法 —— 少了它，某些显卡驱动上会直接崩在
 *         「GL called from wrong thread」。</li>
 *     <li><b>字节来源用 {@link Supplier} 而不是提前读好。</b>一个模型包可能有几十张
 *         512×512 的贴图，提前全读进内存是几 MB 的无谓峰值；{@code load} 只在
 *         注册那一次被调用，进去再读刚好。</li>
 * </ul>
 *
 * <p>{@code 1.18.2} 的 {@link AbstractTexture#load(ResourceManager)} 由
 * {@code TextureManager#register} 直接调用（反编译确认），所以只要把逻辑写在
 * {@code load} 里，`register` 一次就完成「读 + 上传」。</p>
 */
public class PackTexture extends AbstractTexture {
    /** ASCII 兜底：万一 PNG 读不出来，至少日志里能看出是哪个包、哪张图。 */
    private final String description;
    private final Supplier<byte @Nullable []> dataSupplier;
    private int width = 16;
    private int height = 16;

    /**
     * @param description   出错时打日志用，形如 {@code 包名/textures/entity/xxx.png}
     * @param dataSupplier  返回 PNG 原始字节；返回 {@code null} 表示文件不存在
     */
    public PackTexture(String description, Supplier<byte @Nullable []> dataSupplier) {
        this.description = description;
        this.dataSupplier = dataSupplier;
    }

    @Override
    public void load(ResourceManager manager) {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            this.doLoad();
        } else {
            RenderSystem.recordRenderCall(this::doLoad);
        }
    }

    private void doLoad() {
        byte[] data;
        try {
            data = this.dataSupplier.get();
        } catch (Exception e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] failed to read pack texture {}", this.description, e);
            return;
        }
        if (data == null || data.length == 0) {
            return;
        }
        // NativeImage#read 会自己申请堆外内存，所以必须关掉它（上游漏了这一步，
        // 每下载一次包就泄漏一张图的显存 + 内存；这里在 finally 里 close）
        NativeImage image = null;
        try {
            image = NativeImage.read(new ByteArrayInputStream(data));
            this.width = image.getWidth();
            this.height = image.getHeight();
            TextureUtil.prepareImage(this.getId(), 0, this.width, this.height);
            image.upload(0, 0, 0, 0, 0, this.width, this.height, false, false, false, true);
        } catch (IOException e) {
            TouhouLittleMaid.LOGGER.warn("[Futo] invalid PNG in pack texture {}", this.description, e);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}
