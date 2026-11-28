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

