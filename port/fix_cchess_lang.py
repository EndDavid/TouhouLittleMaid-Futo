import io, json

TEXT = ("\u4f60\u53ef\u4ee5\u548c\u5973\u4ec6\u4e0b\u4e00\u5c40\u4e2d\u56fd\u8c61\u68cb\uff0c\u83b7\u80dc\u80fd\u591f\u589e\u52a0\u5973\u4ec6\u7684\u597d\u611f\u5ea6\u3002"
        "$(br2)\u68cb\u76d8\u5360 3\u00d73 \u683c\uff0c\u7a7a\u624b\u53f3\u51fb\u68cb\u76d8\u5373\u53ef\u9009\u5b50\u3001\u843d\u5b50\uff1b"
        "\u70b9\u51fb\u68cb\u76d8\u4fa7\u9762\u6807\u8bb0\u7684\u533a\u57df\u53ef\u4ee5\u91cd\u65b0\u5f00\u5c40\u3002")

path = "src/main/resources/assets/touhou_little_maid/lang/zh_cn.json"
key = "patchouli.touhou_little_maid.book.entries.other.cchess.pages.0.text"

raw = io.open(path, encoding="utf-8", newline="").read()
lines = raw.split("\n")
hit = 0
for i, line in enumerate(lines):
    if '"%s"' % key in line:
        eol = "\r" if line.endswith("\r") else ""
        prefix = line[:line.index('"')]
        lines[i] = prefix + '"%s": %s,%s' % (key, json.dumps(TEXT, ensure_ascii=False), eol)
        hit += 1
assert hit == 1, hit
io.open(path, "w", encoding="utf-8", newline="").write("\n".join(lines))

d = json.loads(io.open(path, encoding="utf-8", newline="").read())
print(repr(d[key]))

