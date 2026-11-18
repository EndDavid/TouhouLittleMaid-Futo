"""Remove the config-value try/catch guards that were written on a false premise.

Evidence: ForgeConfigAPIPort 3.2.4's ForgeConfigSpec$ConfigValue#get() starts with

    if (spec.childConfig == null) return defaultSupplier.get();

so an unloaded config yields the DEFAULT value and never throws (Forge 1.18.2's
own copy of that method has the same shape).  The intOr/boolOr/stringOr/listOr/
doubleOr wrappers in MaidConfig, plus two one-off helpers in MiscConfig and one
in ServerConfig, were therefore dead code -- and worse than dead: they swallowed
genuine errors (bad value type, invalid default) with no log line, which reads to
a player as "my config change does nothing".

This script is the *record* of that cleanup, not something to re-run: the four
passes it originally took (grep for the simple wrappers, then a regex for the
`X == null ? d : X.get()` one-liners, then two manual anchor patches for the
multi-line ternaries and the javadoc blocks) have all been applied.
`ConfigValue#get()` no longer appears inside a `catch` anywhere in `config/`.
"""
import io

NOTE = ('\u65e0\u9700 try/catch \u2014\u2014 \u914d\u7f6e\u672a\u52a0\u8f7d\u65f6 ConfigValue#get() '
        '\u8fd4\u56de\u9ed8\u8ba4\u503c\uff08\u4f9d\u636e\u89c1 MaidConfig \u6587\u4ef6\u672b\u5c3e\u7684\u8bf4\u660e\uff09\u3002')

OLD_DOC = ('     * <p><b>\u53d6\u503c\u8bf7\u7528\u6587\u4ef6\u672b\u5c3e\u7684\u9759\u6001\u8f85\u52a9\u65b9\u6cd5</b>'
           '\uff08\u5982 {@link #workRange()}\uff09\uff0c\u4e0d\u8981\u76f4\u63a5\n'
           ' * {@code MAID_WORK_RANGE.get()}\u3002\u539f\u56e0\uff1a{@code ForgeConfigSpec.ConfigValue#get()} \u5728\u914d\u7f6e\n'
           ' * \u5c1a\u672a\u52a0\u8f7d\u65f6\u4f1a\u629b {@code IllegalStateException}\uff0c\u800c\u5973\u4ec6\u5b9e\u4f53\u7684\u9759\u6001\u521d\u59cb\u5316\u3001\u5ba2\u6237\u7aef\u6e32\u67d3\u7b49\n'
           ' * \u90fd\u53ef\u80fd\u65e9\u4e8e\u914d\u7f6e\u52a0\u8f7d\u3002\u8f85\u52a9\u65b9\u6cd5\u5728\u672a\u52a0\u8f7d\u65f6\u56de\u9000\u5230\u4e0a\u6e38\u9ed8\u8ba4\u503c\uff0c\u884c\u4e3a\u53ef\u9884\u671f\u3002</p>')

NEW_DOC = ('     * <p><b>\u53d6\u503c\u8bf7\u7528\u6587\u4ef6\u672b\u5c3e\u7684\u9759\u6001\u8f85\u52a9\u65b9\u6cd5</b>'
           '\uff08\u5982 {@link #workRange()}\uff09\uff0c\u4e0d\u8981\u76f4\u63a5\n'
           ' * {@code MAID_WORK_RANGE.get()}\u3002\u539f\u56e0\u4e0d\u662f\u300c{@code get()} \u4f1a\u629b\u5f02\u5e38\u300d'
           '\uff08\u5b83\u4e0d\u4f1a\uff1a\u672a\u52a0\u8f7d\u65f6\u8fd4\u56de\u9ed8\u8ba4\u503c\uff0c\n'
           ' * \u4f9d\u636e\u89c1\u6587\u4ef6\u672b\u5c3e\uff09\uff0c\u800c\u662f\u4e0a\u6e38\u8bfb\u7684\u5c31\u662f\u8fd9\u4e9b\u9ed8\u8ba4\u503c\uff1a\n'
           ' * \u8f85\u52a9\u65b9\u6cd5\u628a\u300c\u9ed8\u8ba4\u503c\u300d\u5199\u5728\u8c03\u7528\u70b9\u4e0a\uff0c\u4e0e\u4e0a\u6e38\u884c\u4e3a\u9010\u5904\u5bf9\u5f97\u4e0a\u3002</p>')


def patch(path, pairs):
    with io.open(path, encoding='utf-8', newline='') as handle:
        text = handle.read()
    nl = '\r\n' if '\r\n' in text else '\n'
    for old, new in pairs:
        old = old.replace('\n', nl)
        new = new.replace('\n', nl)
        if text.count(old) != 1:
            raise SystemExit('%s: anchor matched %d times: %r' % (path, text.count(old), old[:60]))
        text = text.replace(old, new)
    with io.open(path, 'w', encoding='utf-8', newline='') as handle:
        handle.write(text)
    print('patched', path)


base = 'src/main/java/com/github/tartaricacid/touhoulittlemaid/config/'

patch(base + 'subconfig/MiscConfig.java', [
    ('''    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (Exception e) {
            return fallback;
        }
    }''',
     '''    // ''' + NOTE + '''
    private static boolean boolOr(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return value == null ? fallback : value.get();
    }'''),

    ('''        try {
            if (MAID_FAIRY_BLACKLIST_DIMENSION != null) {
                return new ArrayList<>(MAID_FAIRY_BLACKLIST_DIMENSION.get());
            }
        } catch (Exception ignored) {
            // \u914d\u7f6e\u672a\u52a0\u8f7d\uff0c\u56de\u9000\u9ed8\u8ba4\u503c
        }
''',
     '''        if (MAID_FAIRY_BLACKLIST_DIMENSION != null) {
            return new ArrayList<>(MAID_FAIRY_BLACKLIST_DIMENSION.get());
        }
'''),

    ('''    public static int maidFairySpawnProbability() {
        try {
            return MAID_FAIRY_SPAWN_PROBABILITY == null ? 70 : MAID_FAIRY_SPAWN_PROBABILITY.get();
        } catch (Exception e) {
            return 70;
        }
    }''',
     '''    public static int maidFairySpawnProbability() {
        return MAID_FAIRY_SPAWN_PROBABILITY == null ? 70 : MAID_FAIRY_SPAWN_PROBABILITY.get();
    }'''),
])

patch(base + 'subconfig/MaidConfig.java', [(OLD_DOC, NEW_DOC)])

