package com.github.tartaricacid.touhoulittlemaid.ai.service;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
/**
 * 从配置里读 HTTP 代理，移植自上游 {@code ai/service/ConfigProxySelector}。
 *
 * <p>格式是 {@code host:port}，空串表示不走代理；SOCKS 不支持（与上游一致）。
 * 每次 {@code select} 都现读配置，所以改完配置不用重启游戏。</p>
 *
 * <p>这里依赖的 {@link ForgeConfigSpec.ConfigValue} 来自 Forge Config API Port ——
 * 上游直接拿的就是 Forge 的那套类，因此本类在 Fabric 上可以原样保留。</p>
 */
public class ConfigProxySelector extends ProxySelector {
    private static final List<Proxy> NO_PROXY_LIST = List.of(Proxy.NO_PROXY);
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private final ForgeConfigSpec.ConfigValue<String> config;
    ConfigProxySelector(ForgeConfigSpec.ConfigValue<String> config) {
        this.config = config;
    }
    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException e) {
    }
    @Override
    public synchronized List<Proxy> select(URI uri) {
        String scheme = uri.getScheme().toLowerCase();
        if (HTTP.equals(scheme) || HTTPS.equals(scheme)) {
            String value = StringUtils.defaultString(this.config.get());
            return getProxyFromConfig(value.trim());
        } else {
            return NO_PROXY_LIST;
        }
    }
    private synchronized List<Proxy> getProxyFromConfig(String proxyAddress) {
        if (StringUtils.isBlank(proxyAddress)) {
            return NO_PROXY_LIST;
        }
        String[] split = proxyAddress.split(":", 2);
        if (split.length != 2) {
            return NO_PROXY_LIST;
        }
        String hostname = split[0];
        String portString = split[1];
        if (!StringUtils.isNumeric(portString) || !NumberUtils.isParsable(portString)) {
            return NO_PROXY_LIST;
        }
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(hostname, Integer.parseInt(portString)));
        return List.of(proxy);
    }
}
