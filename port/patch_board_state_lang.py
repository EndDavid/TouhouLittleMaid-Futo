# -*- coding: utf-8 -*-
"""Add every lang key the endgame items and the two new book entries need.

The files have mixed line endings (a few strings contain a literal \\r\\n, the
rest of the file is LF), so new lines are inserted with the same LF the
neighbouring lines use.  Each insertion asserts that its anchor occurs exactly
once, and the multiplication sign is re-checked after writing because it has
been eaten by tooling before in this project.
"""
import io
import json
import sys

LANG = 'src/main/resources/assets/touhou_little_maid/lang/'

COMMON = {
    'en_us.json': [
        ('  "item.touhou_little_maid.kappa_compass"',
         u'  "item.touhou_little_maid.gomoku_board_state": "Gomoku Endgame",\n'
         u'  "item.touhou_little_maid.cchess_board_state": "Chinese Chess Endgame",\n'
         u'  "item.touhou_little_maid.wchess_board_state": "Chess Endgame",\n'),
        ('  "message.touhou_little_maid.entity_id_copy.copy"',
         u'  "message.touhou_little_maid.board_state.bad_data": "This endgame item is broken and was ignored",\n'),
        ('  "tooltips.touhou_little_maid.info.game_skill.gomoku"',
         u'  "tooltips.touhou_little_maid.board_state.empty": "There is no endgame on this item",\n'
         u'  "tooltips.touhou_little_maid.board_state.author": "Author: %s",\n'
         u'  "board_state.touhou_little_maid.show_picture": "< Hold Shift to show the board >",\n'
         u'  "board_state.touhou_little_maid.gomoku.default": "Gomoku endgame",\n'
         u'  "board_state.touhou_little_maid.gomoku.moon": "Moon formation",\n'
         u'  "board_state.touhou_little_maid.gomoku.stealth_cow": "Stealth cow formation",\n'
         u'  "board_state.touhou_little_maid.gomoku.noodles": "Noodles formation",\n'
         u'  "board_state.touhou_little_maid.gomoku.square": "Square formation",\n'
         u'  "board_state.touhou_little_maid.gomoku.bed": "Bed formation",\n'
         u'  "board_state.touhou_little_maid.xiangqi.default": "Chinese chess endgame",\n'
         u'  "board_state.touhou_little_maid.chess.default": "Chess endgame",\n'),
        ('  "patchouli.touhou_little_maid.book.entries.other.gomoku.name"',
         u'  "patchouli.touhou_little_maid.book.entries.other.wchess.name": "Chess",\n'
         u'  "patchouli.touhou_little_maid.book.entries.other.wchess.pages.0.text": "You can play chess with the maid, and winning will increase her favorability.$(br2)The knight moves in an L, the bishop diagonally, and the pawn can advance two squares on its first move. Castling and en passant are supported; the 50-move rule ends the game in a draw.",\n'),
    ],
    'zh_cn.json': [
        ('  "item.touhou_little_maid.kappa_compass"',
         u'  "item.touhou_little_maid.gomoku_board_state": "\u4e94\u5b50\u68cb\u6b8b\u5c40\u9053\u5177",\n'
         u'  "item.touhou_little_maid.cchess_board_state": "\u4e2d\u56fd\u8c61\u68cb\u6b8b\u5c40\u9053\u5177",\n'
         u'  "item.touhou_little_maid.wchess_board_state": "\u56fd\u9645\u8c61\u68cb\u6b8b\u5c40\u9053\u5177",\n'),
        ('  "message.touhou_little_maid.entity_id_copy.copy"',
         u'  "message.touhou_little_maid.board_state.bad_data": "\u8fd9\u4efd\u6b8b\u5c40\u9053\u5177\u7684\u6570\u636e\u5df2\u635f\u574f\uff0c\u5df2\u5ffd\u7565",\n'),
        ('  "tooltips.touhou_little_maid.info.game_skill.gomoku"',
         u'  "tooltips.touhou_little_maid.board_state.empty": "\u8fd9\u4e0a\u9762\u6ca1\u6709\u6b8b\u5c40\u6570\u636e",\n'
         u'  "tooltips.touhou_little_maid.board_state.author": "\u4f5c\u8005\uff1a%s",\n'
         u'  "board_state.touhou_little_maid.show_picture": "< \u6309\u4e0b Shift \u663e\u793a\u7ec6\u8282 >",\n'
         u'  "board_state.touhou_little_maid.gomoku.default": "\u4e94\u5b50\u68cb\u6b8b\u5c40",\n'
         u'  "board_state.touhou_little_maid.gomoku.moon": "\u6708\u4eae\u9635",\n'
         u'  "board_state.touhou_little_maid.gomoku.stealth_cow": "\u9690\u8eab\u725b\u9635",\n'
         u'  "board_state.touhou_little_maid.gomoku.noodles": "\u9762\u6761\u9635",\n'
         u'  "board_state.touhou_little_maid.gomoku.square": "\u53e3\u5b57\u9635",\n'
         u'  "board_state.touhou_little_maid.gomoku.bed": "\u5e8a\u9635",\n'
         u'  "board_state.touhou_little_maid.xiangqi.default": "\u4e2d\u56fd\u8c61\u68cb\u6b8b\u5c40",\n'
         u'  "board_state.touhou_little_maid.chess.default": "\u56fd\u9645\u8c61\u68cb\u6b8b\u5c40",\n'),
        ('  "patchouli.touhou_little_maid.book.entries.other.gomoku.name"',
         u'  "patchouli.touhou_little_maid.book.entries.other.wchess.name": "\u56fd\u9645\u8c61\u68cb",\n'
         u'  "patchouli.touhou_little_maid.book.entries.other.wchess.pages.0.text": "\u4f60\u53ef\u4ee5\u548c\u5973\u4ec6\u4e0b\u4e00\u5c40\u56fd\u9645\u8c61\u68cb\uff0c\u83b7\u80dc\u80fd\u591f\u589e\u52a0\u5973\u4ec6\u7684\u597d\u611f\u5ea6\u3002$(br2)\u9a6c\u8d70\u65e5\u3001\u8c61\u8d70\u659c\u3001\u5175\u9996\u6b65\u53ef\u4ee5\u8d70\u4e24\u683c\uff1b\u652f\u6301\u6613\u4f4d\u4e0e\u5403\u8fc7\u8def\u5175\uff0c\u4e94\u5341\u56de\u5408\u81ea\u7136\u9650\u7740\u5224\u548c\u3002",\n'),
    ],
}

# The two zh_cn chess lines lost their multiplication sign and grew a stray
# space in an earlier import; fix them while we are here.
FIXES = {
    'zh_cn.json': [
        (u'\u68cb\u76d8\u5360 33 \u683c\uff0c\u7a7a\u624b\u53f3\u51fb\u68cb\u76d8\u5373\u53ef\u9009\u5b50\u3001\u843d\u5b50\uff1b\u70b9 \u51fb\u68cb\u76d8\u4fa7\u9762\u6807\u8bb0\u7684\u533a\u57df\u53ef\u4ee5\u91cd\u65b0\u5f00\u5c40\u3002',
         u'\u68cb\u76d8\u5360 3\u00d73 \u683c\uff0c\u7a7a\u624b\u53f3\u51fb\u68cb\u76d8\u5373\u53ef\u9009\u5b50\u3001\u843d\u5b50\uff1b\u70b9\u51fb\u68cb\u76d8\u4fa7\u9762\u6807\u8bb0\u7684\u533a\u57df\u53ef\u4ee5\u91cd\u65b0\u5f00\u5c40\u3002'),
        (u'\u4f60\u53ef\u4ee5\u548c\u5973\u4ec6\u4e0b\u4e00\u5c40\u4e2d\u56fd\u8c61\u68cb\uff0c\u83b7\u80dc\u80fd\u591f\u589e\u52a0\u5973\u4ec6\u7684\u597d\u611f\u5ea6\u3002$(br2)\u68cb\u76d8\u5360 3\u00d73 \u683c',
         u'\u4f60\u53ef\u4ee5\u548c\u5973\u4ec6\u4e0b\u4e00\u5c40\u4e2d\u56fd\u8c61\u68cb\uff0c\u83b7\u80dc\u80fd\u591f\u589e\u52a0\u5973\u4ec6\u7684\u597d\u611f\u5ea6\u3002$(br2)\u68cb\u76d8\u5360 3\u00d73 \u683c'),
    ],
}


def main():
    for name, inserts in COMMON.items():
        path = LANG + name
        raw = io.open(path, encoding='utf-8', newline='').read()
        for anchor, block in inserts:
            count = raw.count(anchor)
            if count != 1:
                sys.exit('%s: anchor %r found %d times' % (name, anchor[:50], count))
            raw = raw.replace(anchor, block + anchor)
        if name in FIXES:
            for old, new in FIXES[name]:
                if raw.count(old) != 1:
                    sys.exit('%s: fix anchor found %d times: %r' % (name, raw.count(old), old[:40]))
                raw = raw.replace(old, new)
        io.open(path, 'w', encoding='utf-8', newline='').write(raw)
        json.load(io.open(path, encoding='utf-8'))
        text = io.open(path, encoding='utf-8').read()
        print('%-12s ok, times-sign=%d, fffd=%d' % (name, text.count(u'\u00d7'), text.count(u'\ufffd')))


if __name__ == '__main__':
    main()

