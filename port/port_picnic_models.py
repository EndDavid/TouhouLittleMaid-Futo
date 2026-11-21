"""把上游 1.18.2 的野餐篮 / 野餐垫模型逐字节搬进本移植版。

这两个文件是纯几何数据（基岩模型转成原版 ModelPart 的产物，几百行 addBox），
手抄一遍只会引入抄错的风险，所以按字节复制，只在文件头加上一段说明。

上游 1.18.2 与本移植版用的是同一套 mojmap 名字、同一个包名，
因此除了换行（LF → CRLF）与文件头之外不需要任何改写。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT.parent / '_ref' / 'tlm-1182' / 'src' / 'main' / 'java' / 'com' / 'github' / 'tartaricacid' / 'touhoulittlemaid' / 'client' / 'model'
DST = ROOT / 'src' / 'main' / 'java' / 'com' / 'github' / 'tartaricacid' / 'touhoulittlemaid' / 'client' / 'model'

HEADERS = {
    'PicnicBasketModel': """/**
 * 野餐篮的模型（物品形态 + 手持/掉落的 3D 图标），逐字节来自上游 1.18.2
 * {@code client/model/PicnicBasketModel}。
 *
 * <p>它是纯几何数据：一个 {@code upperCover}（盖子）、一个 {@code body}（箱体）
 * 与两只 {@code handle}（提手，左右镜像）。方块实体那一侧用的是另一个文件
 * {@link PicnicMatModel} —— 那里的篮子只是整张贴图的一部分。</p>
 *
 * <p>图形界面里的<b>扁平图标</b>不走这个模型，见 {@code PicnicBasketIconModel}。</p>
 */
""",
    'PicnicMatModel': """/**
 * 野餐垫的模型，逐字节来自上游 1.18.2 {@code client/model/PicnicMatModel}。
 *
 * <p>它把「整张贴图 + 篮子 + 面包 + 蛋糕 + 饮料 + 盘子」全部装在一个图层里，
 * 另外单独留了一个 {@code hide} 根节点：{@code basketHide} / {@code breadHide} / {@code cakeHide}
 * 这三组是<b>「看起来有东西」的装饰件</b>，当对应格子真的放了食物时由
 * {@code TileEntityPicnicMatRenderer} 把它们隐藏掉，否则会和物品模型叠在一起。</p>
 *
 * <p>四个 {@code sit} 小方块与 {@code BlockPicnicMat#sitPosition} 的四个偏移一一对应，
 * 改一边就必须改另一边。</p>
 */
""",
}


def convert(name: str) -> None:
    src = SRC / f'{name}.java'
    raw = src.read_bytes().decode('utf-8')
    if not raw.startswith('package '):
        sys.exit(f'{name}: unexpected file start')
    # 去掉上游的 package 行，稍后重新拼（保留原包名，只是为了让头部注释在最前面）
    body = raw
    lines = body.replace('\r\n', '\n').split('\n')
    header = HEADERS[name]
    # 在 import 之前插入头部注释
    out_lines = []
    for line in lines:
        out_lines.append(line)
        if line.startswith('package '):
            out_lines.append('')
            out_lines.extend(header.rstrip('\n').split('\n'))
    text = '\n'.join(out_lines)
    if not text.endswith('\n'):
        text += '\n'
    data = text.replace('\n', '\r\n')
    dst = DST / f'{name}.java'
    dst.write_bytes(data.encode('utf-8'))
    print(f'{name}: {len(lines)} lines -> {dst.relative_to(ROOT)}')


def main() -> None:
    for name in HEADERS:
        convert(name)


if __name__ == '__main__':
    main()

