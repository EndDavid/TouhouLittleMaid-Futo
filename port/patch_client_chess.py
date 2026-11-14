import io

PATH = "src/main/java/com/github/tartaricacid/touhoulittlemaid/client/TouhouLittleMaidFutoClient.java"

with io.open(PATH, "r", encoding="utf-8", newline="") as f:
    text = f.read()

nl = "\r\n"


def replace_once(old, new):
    global text
    cnt = text.count(old)
    assert cnt == 1, "expected exactly one match, got %d for: %s" % (cnt, old[:70])
    text = text.replace(old, new, 1)


RENDERER_ANCHOR = (
    "        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register("
    + nl
    + "                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.SHRINE_TE,"
    + nl
    + "                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityShrineRenderer::new);"
)

RENDERER_ADD = nl.join([
    "        // \u4e2d\u56fd\u8c61\u68cb\u68cb\u76d8\uff1a\u4e5f\u662f ENTITYBLOCK_ANIMATED\uff0c\u68cb\u5b50\u4e0e\u68cb\u76d8",
    "        // \u5168\u9760\u57fa\u5ca9\u6a21\u578b\u753b\uff08\u89c1 TileEntityCChessRenderer\uff09",
    "        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(",
    "                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.CCHESS_TE,",
    "                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityCChessRenderer::new);",
])
replace_once(RENDERER_ANCHOR, RENDERER_ANCHOR + nl + RENDERER_ADD)


RECV_ANCHOR = "        // \u300c\u8fd9\u6b21\u5f00\u4e0d\u4e86\u5c45\u5bb6\u6a21\u5f0f\u300d\u7684\u8bf4\u660e\uff08S2C\uff0c\u4e0a\u6e38 CheckSchedulePosMessage\uff09\u3002"

RECV_ADD = nl.join([
    "        // \u8c61\u68cb\uff1a\u670d\u52a1\u7aef\u628a\u5c40\u9762\u4ea4\u8fc7\u6765\uff0c\u7531\u672c\u673a\u5728\u540e\u53f0\u7ebf\u7a0b\u7b97\u5973\u4ec6\u7684\u4e0b\u4e00\u6b65",
    "        // \uff08S2C\uff0c\u4e0a\u6e38 CChessToClientMessage\uff09\u3002\u5fc5\u987b\u5148\u628a\u5b57\u6bb5\u8bfb\u5b8c\u518d execute\u3002",
    "        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(",
    "                com.github.tartaricacid.touhoulittlemaid.network.CChessToClientPacket.CHANNEL,",
    "                (client, handler, buf, responseSender) -> {",
    "                    com.github.tartaricacid.touhoulittlemaid.network.CChessToClientPacket.Payload payload =",
    "                            com.github.tartaricacid.touhoulittlemaid.network.CChessToClientPacket.read(buf);",
    "                    client.execute(() -> com.github.tartaricacid.touhoulittlemaid.client.event.CChessClientHandler",
    "                            .handle(client, payload.getPos(), payload.getFen()));",
    "                });",
    "",
])
replace_once(RECV_ANCHOR, RECV_ADD + RECV_ANCHOR)

with io.open(PATH, "w", encoding="utf-8", newline="") as f:
    f.write(text)

print("ok", text.count("\ufffd"))

