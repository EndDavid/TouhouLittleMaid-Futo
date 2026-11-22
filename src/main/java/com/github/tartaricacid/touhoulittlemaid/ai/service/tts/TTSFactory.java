package com.github.tartaricacid.touhoulittlemaid.ai.service.tts;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.setting.Site;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.TTSFishAudioClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request.Format;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request.OpusBitRate;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.fishaudio.request.TTSFishAudioRequest;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.gptsovits.TTSGptSovitsClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.gptsovits.request.TTSGptSovitsRequest;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpClient;

/**
 * 按站点的 {@code api_type} 造出对应的客户端与请求体，移植自上游 {@code ai/service/tts/TTSFactory}。
 *
 * <p>两个工厂方法都可能返回 {@code null}：站点类型不认识（比如配置里写了
 * {@code api_type: system}，那条路径走本地朗读，不需要 HTTP），调用方必须先判空。</p>
 */
public final class TTSFactory {
    @Nullable
    public static TTSClient<?> getTtsClient(HttpClient client, Site site) {
        if (site.getApiType().equals(TTSApiType.FISH_AUDIO.getName())) {
            String ttsApiKey = site.getApiKey();
            String ttsBaseUrl = site.getUrl();
            return TTSFishAudioClient.create(client)
                    .apiKey(ttsApiKey)
                    .baseUrl(ttsBaseUrl);
        }
        if (site.getApiType().equals(TTSApiType.GPT_SOVITS.getName())) {
            String ttsApiKey = site.getApiKey();
            String ttsBaseUrl = site.getUrl();
            return TTSGptSovitsClient.create(client)
                    .apiKey(ttsApiKey)
                    .baseUrl(ttsBaseUrl);
        }
        return null;
    }

    @Nullable
    public static TTSRequest getTtsRequest(Site site, String ttsText, String ttsLang, String model) {
        if (site.getApiType().equals(TTSApiType.FISH_AUDIO.getName())) {
            return TTSFishAudioRequest.create()
                    .setReferenceId(model)
                    .setFormat(Format.OPUS)
                    // OPUS 极低比特率情况下，音质效果也还不错
                    .setOpusBitrate(OpusBitRate.LOWEST)
                    .setText(ttsText);
        }
        if (site.getApiType().equals(TTSApiType.GPT_SOVITS.getName())) {
            return TTSGptSovitsRequest.create()
                    .setText(ttsText)
                    .setTextLang(ttsLang)
                    .setSiteExtraArgs(site);
        }
        return null;
    }

    private TTSFactory() {
    }
}

