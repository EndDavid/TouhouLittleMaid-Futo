import io

p = "src/main/java/com/github/tartaricacid/touhoulittlemaid/client/TouhouLittleMaidFutoClient.java"
t = io.open(p, encoding="utf-8", newline="").read()
nl = "\r\n"

anchor = ("        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(" + nl +
          "                com.github.tartaricacid.touhoulittlemaid.init.InitBlocks.CCHESS_TE," + nl +
          "                com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityCChessRenderer::new);")
assert t.count(anchor) == 1

add = nl.join([
    "",
    "        // \u5ea7\u4f4d\u5b9e\u4f53\u4e0d\u8be5\u88ab\u770b\u89c1\uff0c\u4f46\u5fc5\u987b\u6709\u6e32\u67d3\u5668\uff1a\u672a\u6ce8\u518c\u7c7b\u578b\u7684",
    "        // getRenderer \u8fd4\u56de null\uff0c\u6e32\u67d3\u5faa\u73af\u4e0d\u5904\u5904\u5224\u7a7a\uff08\u540c P \u70b9\u5b9e\u4f53\u90a3\u4e2a\u5751\uff09\u3002",
    "        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(",
    "                com.github.tartaricacid.touhoulittlemaid.init.InitEntities.SIT,",
    "                com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EmptyEntityRenderer::new);",
])
t = t.replace(anchor, anchor + nl + add, 1)
io.open(p, "w", encoding="utf-8", newline="").write(t)
print("ok", t.count("EmptyEntityRenderer"))

