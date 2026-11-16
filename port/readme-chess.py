"""Replace the chess placement section of README.md, preserving CRLF exactly.

insert_edit_into_file has destroyed this file twice (truncation), and the README is
stored CRLF-only, so a Python newline='' round trip is the safe route: read bytes,
decode, substitute by exact anchors with occurrence assertions, encode, write bytes.
"""
import io
import sys

README = 'README.md'
NEW = 'port/_readme_chess_new.md'

with io.open(README, 'r', encoding='utf-8', newline='') as f:
    text = f.read()
assert '\r\n' in text and text.count('\r\n') > 1000, 'README lost its CRLF endings'

start_anchor = '### 摆放：一切数字必须量出来，不能照抄上游'
end_anchor = '高度算错 + 没有抬升。'

i = text.find(start_anchor)
assert i >= 0, 'start anchor not found'
assert text.count(start_anchor) == 1, 'start anchor is not unique'
j = text.find(end_anchor, i)
assert j >= 0, 'end anchor not found'
assert text.count(end_anchor) == 1, 'end anchor is not unique'
j += len(end_anchor)

with io.open(NEW, 'r', encoding='utf-8', newline='') as f:
    new = f.read()
new = new.replace('\r\n', '\n')
new = new.replace('\n', '\r\n').rstrip('\r\n')

old = text[i:j]
text = text[:i] + new + text[j:]
print('replaced %d chars with %d' % (len(old), len(new)))

# the ZN(180) bullet claimed the glyphs were drawn upside down; the real reason the
# glyphs were invisible is above
bad = '- 棋子的**文字朝下**，看不见；'
good = '- 棋子看到的也是底面，整片偏暗；'
assert text.count(bad) == 1, 'ZN(180) bullet not found exactly once'
text = text.replace(bad, good)

with io.open(README, 'w', encoding='utf-8', newline='') as f:
    f.write(text)
print('ok, total lines = %d' % text.count('\r\n'))
