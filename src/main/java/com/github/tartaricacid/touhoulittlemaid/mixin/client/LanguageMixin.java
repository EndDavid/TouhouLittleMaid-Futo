package com.github.tartaricacid.touhoulittlemaid.mixin.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.LanguageLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.LanguageManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 让 {@link LanguageLoader} 里登记的译文参与原版的文字解析，移植自上游
 * {@code mixin/client/LanguageMixin}。
 *
 * <h2>为什么要改 {@code ClientLanguage}</h2>
 * <p>模型包名 / 简介 / 在线下载清单里的条目都是「键」，译文在包内
 * {@code .lang} 或 {@code info.json} 的 {@code language} 字段里，原版的
 * {@code ResourceManager} 两条都读不到。而 {@code Component.translatable(...)} 的解析
 * 最终都落到 {@code Language.getInstance().getOrDefault(key)} ——
 * 这个实例就是 {@link ClientLanguage}（{@code LanguageManager} 加载语言时
 * {@code Language.inject(clientLanguage)}），所以只在这里加一层查询就够，
 * 界面代码一行都不用改（{@code PackInfoButton} / {@code ModelDownloadGui}
 * 本来就用的是 {@code TranslatableComponent}）。</p>
 *
 * <h2>为什么是「查不到就不拦」</h2>
 * <p>两个注入点都是 {@code HEAD} + {@code cancellable}，但只在命中缓存时才
 * {@code setReturnValue}。没命中就让原方法继续跑，于是模组自己的
 * {@code assets/touhou_little_maid/lang/*.json} 与其它模组、资源包的行为完全不变 ——
 * 这层只是「补一张表」，不是替换语言系统。</p>
 *
 * <h2>缺省回退</h2>
 * <p>与上游一致：当前语言没有这个键时，再查一次 {@code en_us}。于是
 * 清单只提供了英中两版时，德语 / 日语客户端的界面上仍是完整的英文名，
 * 而不是 {@code tlm.yingbai_arknights_pack.name} 这种原始键。</p>
 *
 * <h2>线程</h2>
 * <p>{@code getOrDefault} 可能在任何线程被调用（模组初始化时就会触发），
 * 所以取当前语言这一步要能容忍 {@code Minecraft} 尚未建好 —— 那种时候直接放弃，
 * 让原方法返回键本身，反正那时还没有界面要画。</p>
 */
@Mixin(value = ClientLanguage.class, priority = 943)
public class LanguageMixin {
    @Inject(method = "getOrDefault(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void futo$getCustomLanguage(String key, CallbackInfoReturnable<String> cir) {
        String value = futo$lookup(key);
        if (value != null) {
            cir.setReturnValue(value);
        }
    }

    @Inject(method = "has(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private void futo$hasCustomLanguage(String key, CallbackInfoReturnable<Boolean> cir) {
        if (futo$lookup(key) != null) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 先查当前语言，再回退到 {@code en_us}；都没有返回 {@code null}。
     *
     * @param key 形如 {@code tlm.xxx_pack.name}
     */
    @Unique
    @Nullable
    private static String futo$lookup(@Nullable String key) {
        if (key == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        LanguageManager languageManager = minecraft.getLanguageManager();
        if (languageManager == null || languageManager.getSelected() == null) {
            return null;
        }
        Map<String, String> selected = LanguageLoader.getLanguages(languageManager.getSelected().getCode());
        if (selected != null && selected.containsKey(key)) {
            return selected.get(key);
        }
        Map<String, String> fallback = LanguageLoader.getLanguages("en_us");
        if (fallback != null && fallback.containsKey(key)) {
            return fallback.get(key);
        }
        return null;
    }
}

