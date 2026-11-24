# -*- coding: utf-8 -*-
"""Simulate vanilla 1.18.2 PlayerAdvancements visibility to explain
"Loaded N advancements" and whether mod roots get synced to the client.

Model (from 1.18.2 bytecode):
  shouldBeVisible(adv):
     for depth in 0..2:
        if adv is None: return False
        if depth == 0 and hasCompletedChildrenOrSelf(adv): return True
        if display(adv) is None: return False
        if done(adv): return True
        if display(adv).hidden: return False
        adv = parent(adv)
     return False

  ensureVisibility(adv):
     vis = shouldBeVisible(adv); was = adv in visible
     if vis and not was: visible.add(adv)
     elif not vis and was: visible.discard(adv)
     if vis != was and parent(adv): ensureVisibility(parent(adv))
     for child in children(adv): ensureVisibility(child)

  ensureAllVisible(): for each done advancement -> ensureVisibility
  auto-awarded: advancements whose criteria map is empty
"""
import json
import os
import sys
import zipfile

MC_JAR = 'C:/Users/Exschwasion/.gradle/caches/fabric-loom/1.18.2/minecraft-merged.jar'
MOD = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'src', 'main', 'resources', 'data', 'touhou_little_maid', 'advancements')
PLAYER_FILE = (sys.argv[1] if len(sys.argv) > 1 else
               os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                            'run', 'saves', 'New World', 'advancements',
                            '8a5928f7-beb7-37f5-80fc-dd4e68365b90.json'))

advs = {}


def add_adv(rid, data):
    display = data.get('display')
    advs[rid] = {
        'parent': data.get('parent'),
        'has_display': display is not None,
        'hidden': bool(display.get('hidden', False)) if display else False,
        'criteria': list(data.get('criteria', {}).keys()),
    }


with zipfile.ZipFile(MC_JAR) as z:
    for n in z.namelist():
        if n.startswith('data/minecraft/advancements/') and n.endswith('.json'):
            path = n[len('data/minecraft/advancements/'):-len('.json')]
            add_adv('minecraft:' + path, json.loads(z.read(n)))

for root, _dirs, files in os.walk(MOD):
    for f in files:
        if not f.endswith('.json'):
            continue
        full = os.path.join(root, f)
        path = os.path.relpath(full, MOD)[:-len('.json')].replace('\\', '/')
        with open(full, encoding='utf-8') as fh:
            add_adv('touhou_little_maid:' + path, json.load(fh))

print('total advancements:', len(advs))

children = {}
for rid, a in advs.items():
    if a['parent']:
        children.setdefault(a['parent'], []).append(rid)

missing_parent = sorted({a['parent'] for a in advs.values()
                         if a['parent'] and a['parent'] not in advs})
print('advancements with unknown parent:', missing_parent)

done = set()
for rid, a in advs.items():
    if not a['criteria']:
        done.add(rid)
print('auto-awarded (empty criteria):', len(done), sorted(done)[:10])

with open(PLAYER_FILE, encoding='utf-8') as fh:
    player = json.load(fh)
for rid, prog in player.items():
    if rid == 'DataVersion':
        continue
    if isinstance(prog, dict) and prog.get('done'):
        if rid in advs:
            done.add(rid)
        else:
            print('WARN player-progress id not found in data:', rid)
print('done advancements:', len(done), sorted(done))


def has_completed_children_or_self(rid):
    # iterative DFS, vanilla walks the whole subtree
    stack = [rid]
    seen = set()
    while stack:
        cur = stack.pop()
        if cur in seen:
            continue
        seen.add(cur)
        if cur in done:
            return True
        stack.extend(children.get(cur, ()))
    return False


def should_be_visible(rid):
    # --- 1.20.1 的 ALWAYS_SHOW 复刻（仅本模组命名空间）---
    cur = rid
    for depth in range(3):
        if cur is None:
            break
        if not advs[cur]['has_display']:
            break
        if advs[cur]['parent'] is None:
            if cur.startswith('touhou_little_maid:'):
                return True
            break
        cur = advs[cur]['parent']

    cur = rid
    for depth in range(3):
        if cur is None:
            return False
        if depth == 0 and has_completed_children_or_self(cur):
            return True
        a = advs[cur]
        if not a['has_display']:
            return False
        if cur in done:
            return True
        if a['hidden']:
            return False
        cur = a['parent']
    return False


visible = set()


def ensure_visibility(rid):
    vis = should_be_visible(rid)
    was = rid in visible
    if vis and not was:
        visible.add(rid)
    elif not vis and was:
        visible.discard(rid)
    parent = advs[rid]['parent']
    if vis != was and parent in advs:
        ensure_visibility(parent)
    for c in children.get(rid, ()):
        ensure_visibility(c)


sys.setrecursionlimit(100000)
for rid in sorted(done):
    ensure_visibility(rid)

# mixin 在 load 末尾追加的入口：从本模组的 root 出发传播（1.20.1 的做法）
for rid, a in sorted(advs.items()):
    if a['parent'] is None and a['has_display'] and rid.startswith('touhou_little_maid:'):
        ensure_visibility(rid)

print()
print('visible (=sent in first ClientboundUpdateAdvancementsPacket):', len(visible))
print()
print('roots in visible set:')
for rid, a in sorted(advs.items()):
    if a['parent'] is None and a['has_display']:
        print('   ', rid, '->', rid in visible, '(hidden=%s, done=%s)' % (a['hidden'], rid in done))
