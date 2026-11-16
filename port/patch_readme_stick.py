# -*- coding: utf-8 -*-
"""README: document the creative stick tool, and drop a duplicated limitation bullet."""
import io
import sys

PATH = 'README.md'
raw = io.open(PATH, encoding='utf-8', newline='').read()
if '\r\n' not in raw:
    sys.exit('expected CRLF')

# 1) new subsection, inserted right before the limitations list
anchor = u'### \u5df2\u77e5\u9650\u5236\r\n\r\n- **\u300c\u6728\u68cd + \u521b\u9020\u6a21\u5f0f\u300d'
if raw.count(anchor) != 1:
    sys.exit('section anchor found %d times' % raw.count(anchor))

section = (
    u'#### \u521b\u9020\u6a21\u5f0f\u7684\u6728\u68cd\uff1a\u5bfc\u51fa\u4e0e\u6392\u6b8b\u5c40\r\n'
    u'\r\n'
    u'\u4e0a\u6e38\u628a\u5b83\u53ea\u653e\u5728\u4e94\u5b50\u68cb\u4e0a\uff08{@code BlockGomoku#onCreativePlayerClick}\uff09\uff0c\r\n'
    u'\u672c\u79fb\u690d\u7248\u540c\u6837\u53ea\u7ed9\u4e94\u5b50\u68cb —— \u4e24\u5f20\u8c61\u68cb\u68cb\u76d8\u6ca1\u6709\u8fd9\u4e2a\u5206\u652f\u3002\r\n'
    u'\u7406\u7531\u4e5f\u5f88\u5b9e\u5728\uff1a\u8c61\u68cb\u7684\u5c40\u9762\u662f\u4e00\u4e2a FEN\uff0c\u624b\u5199\u5c31\u884c\uff1b\r\n'
    u'\u4e94\u5b50\u68cb\u7684\u683c\u5f0f\u662f deflate + base64\uff0c\u4eba\u4e0d\u53ef\u80fd\u624b\u63c9\uff0c\u624d\u9700\u8981\u4e00\u4e2a\u5bfc\u51fa\u5668\u3002\r\n'
    u'\r\n'
    u'\u4e24\u4e2a\u7528\u6cd5\uff08\u90fd\u9700\u8981\u521b\u9020\u6a21\u5f0f\uff0c\u4e14\u6728\u68cd\u62ff\u5728\u4e3b\u624b\uff09\uff1a\r\n'
    u'\r\n'
    u'| \u70b9\u54ea\u91cc | \u53d1\u751f\u4ec0\u4e48 |\r\n'
    u'|---|---|\r\n'
    u'| \u68cb\u76d2\uff08\u5bf9\u89d2\u7ebf\u4e0a\u90a3\u4e24\u5806\u68cb\u5b50\uff09 | \u628a\u5f53\u524d\u68cb\u5c40\u7f16\u7801\u6210\u4e00\u884c\u5b57\u7b26\u4e32\u53d1\u5230\u804a\u5929\u680f\uff0c**\u70b9\u4e00\u4e0b\u5c31\u590d\u5236** |\r\n'
    u'| \u4ea4\u53c9\u70b9 | \u843d\u4e00\u9897\u300c\u8f6e\u5230\u8c01\u7684\u989c\u8272\u300d\uff0c\u7136\u540e\u628a\u56de\u5408\u7ffb\u7ed9\u5bf9\u9762 |\r\n'
    u'\r\n'
    u'\u5bfc\u51fa\u7684\u90a3\u884c\u5b57\u7b26\u4e32\u5c31\u662f {@code board_states/*.json} \u91cc {@code data} \u5b57\u6bb5\u7684\u5185\u5bb9\uff0c\r\n'
    u'\u76f4\u63a5\u8d34\u8fdb\u6570\u636e\u5305\u5c31\u80fd\u53d8\u6210\u4e00\u6761\u6b8b\u5c40\u3002\r\n'
    u'\r\n'
    u'**\u6392\u6b8b\u5c40\u662f\u300c\u76f4\u63a5\u7f16\u8f91\u300d\uff0c\u4e0d\u68c0\u67e5\u843d\u5b50\u70b9\u662f\u5426\u4e3a\u7a7a** —— \u843d\u5728\u5df2\u6709\u68cb\u5b50\u4e0a\u662f\u8986\u76d6\u3002\r\n'
    u'\u8fd9\u662f\u4e0a\u6e38\u884c\u4e3a\uff1a\u6392\u7684\u65f6\u5019\u96be\u514d\u8981\u6539\u4e00\u9897\u5b50\u7684\u989c\u8272\uff0c\u53cd\u590d\u70b9\u540c\u4e00\u4e2a\u70b9\r\n'
    u'\u5c31\u80fd\u5728\u9ed1 / \u767d\u4e4b\u95f4\u6765\u56de\u5207\u3002\u4ee3\u4ef7\u662f {@code ChessCounter} \u4f1a\u88ab\u8d85\u7d2f\u52a0\uff08\u5b83\u53ea\u662f\u300c\u56de\u5408\u6570\u300d\u7684\u5c55\u793a\uff09\u3002\r\n'
    u'\r\n'
    u'1.18.2 \u5dee\u5f02\uff08\u4e24\u5904\uff0c\u90fd\u5728\u6ce8\u91ca\u91cc\u8bb0\u4e86\uff09\uff1a\r\n'
    u'{@code ComponentUtils#copyOnClickText} \u662f 1.19+ \u7684\u5de5\u5382\u65b9\u6cd5\uff0c\u8fd9\u91cc\u7528\r\n'
    u'{@code Style#withClickEvent} + {@code withHoverEvent} + {@code withInsertion} \u62fc\u51fa\u6765\uff1b\r\n'
    u'{@code Player#sendSystemMessage} \u540c\u6837\u662f 1.20+\uff0c1.18.2 \u7684\u7b49\u4ef7\u5199\u6cd5\u662f\r\n'
    u'{@code displayClientMessage(x, false)}\uff08actionBar = false \u5c31\u662f SYSTEM \u804a\u5929\u680f\uff09\u3002\r\n'
    u'{@code COPY_TO_CLIPBOARD} \u5728 1.18.2 \u5141\u8bb8\u670d\u52a1\u7aef\u4e0b\u53d1\uff08{@code ClickEvent$Action} \u679a\u4e3e\u521d\u59cb\u5316\u91cc\u662f\r\n'
    u'{@code iconst_1}\uff09\uff0c\u5426\u5219\u5ba2\u6237\u7aef\u4f1a\u628a\u70b9\u51fb\u4e8b\u4ef6\u5254\u6389\u3002\r\n'
    u'\r\n')

raw = raw.replace(anchor, section + anchor)

# 2) the stick bullet is done; replace it, and drop the duplicated render-box bullet
old_bullet = (
    u'### \u5df2\u77e5\u9650\u5236\r\n'
    u'\r\n'
    u'- **\u300c\u6728\u68cd + \u521b\u9020\u6a21\u5f0f\u300d\u90a3\u5957\u6b8b\u5c40\u5de5\u5177\u6ca1\u642c**\uff1a\u4e0a\u6e38\u7528\u6728\u68cd\u5bfc\u51fa / \u9884\u8bbe\u68cb\u5c40\r\n'
    u'  \uff08`GomokuCodec.encode` + \u804a\u5929\u680f\u70b9\u51fb\u590d\u5236\uff09\uff0c\u4ee5\u53ca\u300c\u6b8b\u5c40\u5bfc\u51fa\u6210\u7269\u54c1\u300d\u3002\r\n'
    u'  \u8fd9\u662f\u7ed9\u505a\u6b8b\u5c40\u7684\u4eba\u7528\u7684\u5f00\u53d1\u5de5\u5177\uff0c\u73a9\u5bb6\u4fa7\u4e0d\u9700\u8981\u5b83\uff0c\u7559\u5728\u5f85\u529e\u91cc\u3002\r\n')
new_bullet = (
    u'### \u5df2\u77e5\u9650\u5236\r\n'
    u'\r\n'
    u'- **\u6728\u68cd\u5de5\u5177\u53ea\u6709\u4e94\u5b50\u68cb\u6709**\uff08\u4e0a\u6e38\u540c\u6b3e\uff09\uff0c\u4e14\u5b83\u53ea\u4ea7\u51fa\r\n'
    u'  `board_states/*.json` \u91cc\u7684 `data` \u5b57\u7b26\u4e32 —— \u4e0d\u4f1a\u76f4\u63a5\u53d8\u6210\u4e00\u4ef6\u6b8b\u5c40\u9053\u5177\u3002\r\n'
    u'  \u60f3\u8981\u5f97\u5230\u7269\u54c1\uff0c\u8981\u628a\u5b83\u5199\u8fdb\u6570\u636e\u5305\uff08\u7136\u540e\u9760\u6218\u5229\u54c1\u6216 `/loot` \u62ff\uff09\u3002\r\n')
if raw.count(old_bullet) != 1:
    sys.exit('bullet anchor found %d times' % raw.count(old_bullet))
raw = raw.replace(old_bullet, new_bullet)

dup = (
    u'- **\u4e2d\u5fc3\u683c\u79bb\u5f00\u89c6\u9525\u65f6\u6574\u5f20\u68cb\u76d8\u4f1a\u6d88\u5931**\uff1a1.18.2 \u7684\u65b9\u5757\u5b9e\u4f53\u6ca1\u6709\r\n'
    u'  `getRenderBoundingBox`\uff0c\u4e0e\u96d5\u50cf\u6e32\u67d3\u76d2\u662f\u540c\u4e00\u4e2a\u5df2\u77e5\u9650\u5236\uff08\u8981\u4fee\u5f97 Mixin `LevelRenderer`\uff09\u3002\r\n')
if raw.count(dup) == 2:
    # keep the first, drop the second
    first = raw.find(dup)
    raw = raw[:first + len(dup)] + raw[first + len(dup):].replace(dup, u'', 1)
    print('removed duplicated bullet')
else:
    print('duplicate bullet count:', raw.count(dup))

io.open(PATH, 'w', encoding='utf-8', newline='').write(raw)
text = io.open(PATH, encoding='utf-8').read()
print('README: crlf=%d fffd=%d lines=%d'
      % (io.open(PATH, 'rb').read().count(b'\r\n'), text.count(u'\ufffd'), text.count('\n') + 1))

