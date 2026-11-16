// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.java.index;

import com.botmaker.java.grammar.BotNames;
import com.botmaker.java.grammar.JavaValue;
import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.java.grammar.ValueTypes;
import com.botmaker.plugin.api.params.Pool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pools of a {@code @Pools} group read off the bot's bound trees (2026-11-15). */
class PoolReaderTest {

    @TempDir
    Path root;

    private static final String FARM = """
            package com.bot.params.bot;

            import com.bot.Hero;
            import com.botmaker.plugin.api.params.Param;
            import java.util.List;

            @Param
            public final class Farm {
                public String account() { return "main"; }
                public List<Hero> team() { return List.of(Hero.ARIA); }
                public Hero leader() { return Hero.ARIA; }
                public int level() { return 1; }
                public long since() { return 0L; }
            }
            """;

    private static final String HERO = """
            package com.bot;

            public enum Hero { ARIA, BRAM, CYRA }
            """;

    private static final String ACCOUNT = """
            package com.bot;

            public final class Account {
                public static final Account MAIN = new Account();
                public static final Account ALT = new Account();
                public static final Account FIRST = MAIN;
                public static final String NOT_ONE = "x";
            }
            """;

    private static final String NAMES = """
            package com.bot;

            public final class Names {
                public static final String MAIN = "main";
                public static final String FIRST = MAIN;
                public static String random() { return "x"; }
            }
            """;

    private List<PoolReader.Reading> read(String... pools) {
        StringBuilder body = new StringBuilder();
        for (String pool : pools) body.append("    ").append(pool).append('\n');
        String group = """
                package com.bot.params.bot;

                import com.bot.Account;
                import com.bot.Hero;
                import com.bot.Names;
                import com.botmaker.plugin.api.params.Pool;
                import com.botmaker.plugin.api.params.Pools;
                import java.util.List;

                import static com.bot.Hero.CYRA;

                @Pools
                public final class FarmPools {
                """ + body + "}\n";
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(root.resolve("com/bot/Hero.java"), HERO);
        sources.put(root.resolve("com/bot/Account.java"), ACCOUNT);
        sources.put(root.resolve("com/bot/Names.java"), NAMES);
        sources.put(root.resolve("com/bot/params/bot/Farm.java"), FARM);
        Path file = root.resolve("com/bot/params/bot/FarmPools.java");
        sources.put(file, group);
        BotIndex index = BotIndex.uncached(sources, List.of(contract()), root);
        return index.read(units -> PoolReader.read(units.get(file), group, units.values(), ValueGrammar.empty(),
                BotNames.NONE, PoolReader.Elements.NONE));
    }

    private static String contract() {
        try {
            return Path.of(Pool.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> written(PoolReader.Reading reading) {
        return reading.elements().stream().map(JavaValue::source).toList();
    }

    @Test
    void ofReadsItsElementsAndTheParameterItFills() {
        PoolReader.Reading pool = read("public Pool<String> accounts() {"
                + " return Pool.of(\"main\", \"alt\").fills(Farm::account).each(2).rotate(); }").getFirst();
        assertTrue(pool.reads(), pool.problem());
        assertEquals("com.bot.params.bot.FarmPools", pool.group());
        assertEquals("accounts", pool.method());
        assertEquals(List.of("\"main\"", "\"alt\""), written(pool));
        PoolReader.Filled filled = pool.fills().orElseThrow();
        assertEquals("com.bot.params.bot.Farm", filled.owner());
        assertEquals("account", filled.method());
        assertEquals("()Ljava/lang/String;", filled.descriptor());
        assertEquals(0, pool.take());
        assertEquals(2, pool.each());
        assertTrue(pool.rotates());
    }

    @Test
    void constantsReadAndAPrimitiveParameterHasItsDescriptor() {
        List<PoolReader.Reading> pools = read(
                "public Pool<Hero> leaders() { return Pool.of(Hero.ARIA, Hero.BRAM).fills(Farm::leader); }",
                "public Pool<Integer> levels() { return Pool.of(1, 2, 3).fills(Farm::level); }");
        assertEquals(List.of("Hero.ARIA", "Hero.BRAM"), written(pools.get(0)));
        assertEquals(List.of("com.bot.Hero"), pools.get(0).elements().getFirst().imports());
        assertEquals(new ValueTypes.BotClass("com.bot.Hero", List.of()), pools.get(0).fills().orElseThrow().element());
        assertTrue(pools.get(1).reads(), pools.get(1).problem());
        assertEquals("()I", pools.get(1).fills().orElseThrow().descriptor());
    }

    @Test
    void allTakesAnEnumsConstantsIntoAListParameter() {
        PoolReader.Reading pool = read(
                "public Pool<List<Hero>> teams() { return Pool.all(Hero.class).take(2).fills(Farm::team); }").getFirst();
        assertTrue(pool.reads(), pool.problem());
        assertEquals(List.of("Hero.ARIA", "Hero.BRAM", "Hero.CYRA"), written(pool));
        assertEquals("com.bot.Hero", pool.allOf().orElseThrow());
        assertEquals(2, pool.take());
        assertEquals("()Ljava/util/List;", pool.fills().orElseThrow().descriptor());
        assertEquals(ValueTypes.listOf(new ValueTypes.BotClass("com.bot.Hero", List.of())),
                pool.fills().orElseThrow().type());
    }

    @Test
    void allOverAnOpenSetTakesItsOwnConstantsEachValueOnce() {
        List<PoolReader.Reading> pools = read(
                "public Pool<Object> accounts() { return Pool.all(Account.class).fills(Farm::account); }");
        // Account is no String: refused. The open set itself is read where its type fits, below.
        assertFalse(pools.getFirst().reads());

        String withAccount = FARM.replace("public int level() { return 1; }",
                "public com.bot.Account login() { return com.bot.Account.MAIN; }");
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(root.resolve("com/bot/Account.java"), ACCOUNT);
        sources.put(root.resolve("com/bot/Hero.java"), HERO);
        sources.put(root.resolve("com/bot/params/bot/Farm.java"), withAccount);
        String group = """
                package com.bot.params.bot;

                import com.bot.Account;
                import com.botmaker.plugin.api.params.Pool;
                import com.botmaker.plugin.api.params.Pools;

                @Pools
                public final class FarmPools {
                    public Pool<Account> logins() { return Pool.all(Account.class).fills(Farm::login); }
                }
                """;
        Path file = root.resolve("com/bot/params/bot/FarmPools.java");
        sources.put(file, group);
        PoolReader.Reading pool = BotIndex.uncached(sources, List.of(contract()), root).read(units ->
                PoolReader.read(units.get(file), group, units.values(), ValueGrammar.empty(), BotNames.NONE,
                        PoolReader.Elements.NONE)).getFirst();
        assertTrue(pool.reads(), pool.problem());
        assertEquals(List.of("Account.MAIN", "Account.ALT"), written(pool), "FIRST is MAIN; NOT_ONE is no Account");
    }

    @Test
    void aFillsNamingAMissingParameterOrTheWrongTypeSaysWhy() {
        List<PoolReader.Reading> pools = read(
                "public Pool<String> missing() { return Pool.of(\"a\").fills(Farm::nothing); }",
                "public Pool<Hero> wrong() { return Pool.of(Hero.ARIA).fills(Farm::account); }",
                "public Pool<String> none() { return Pool.of(\"a\"); }",
                "public Pool<String> lambda() { return Pool.of(\"a\").fills(farm -> farm.account()); }");
        for (PoolReader.Reading pool : pools) {
            assertFalse(pool.reads(), pool.method());
            assertTrue(pool.elements().isEmpty(), pool.method());
        }
        assertTrue(pools.get(1).problem().contains("Farm::account is a String"), pools.get(1).problem());
        assertTrue(pools.get(2).problem().startsWith("fills no parameter"), pools.get(2).problem());
    }

    @Test
    void takeOnAParameterThatIsNotAListSaysWhy() {
        List<PoolReader.Reading> pools = read(
                "public Pool<List<String>> two() { return Pool.of(\"a\", \"b\").take(2).fills(Farm::account); }",
                "public Pool<List<String>> three() { return Pool.of(\"a\", \"b\").take(3).fills(Farm::account); }");
        assertTrue(pools.get(0).problem().contains("fills a List"), pools.get(0).problem());
        assertTrue(pools.get(1).problem().contains("takes 3 of 2"), pools.get(1).problem());
    }

    @Test
    void anElementTheHostCannotWriteOrOneListedTwiceIsRefused() {
        List<PoolReader.Reading> pools = read(
                "public Pool<String> random() { return Pool.of(Names.random()).fills(Farm::account); }",
                "public Pool<Hero> twice() { return Pool.of(Hero.ARIA, Hero.ARIA).fills(Farm::leader); }");
        assertTrue(pools.get(0).problem().contains("not a value the host writes"), pools.get(0).problem());
        assertTrue(pools.get(1).problem().contains("twice"), pools.get(1).problem());
    }

    /** What the contract's Pool would find equal, or refuse, the reader finds alike: the two never disagree. */
    @Test
    void theReaderAgreesWithTheContractsPool() {
        List<PoolReader.Reading> pools = read(
                "public Pool<String> sameValue() { return Pool.of(Names.MAIN, \"main\").fills(Farm::account); }",
                "public Pool<String> alias() { return Pool.of(Names.MAIN, Names.FIRST).fills(Farm::account); }",
                "public Pool<Integer> widened() { return Pool.of(1, 2).fills(Farm::since); }",
                "public Pool<List<Hero>> tooMany() { return Pool.all(Hero.class).take(4).fills(Farm::team); }",
                "public Pool<Hero> imported() { return Pool.of(CYRA).fills(Farm::leader); }");
        assertTrue(pools.get(0).problem().contains("twice"), pools.get(0).problem());
        assertTrue(pools.get(1).problem().contains("twice"), pools.get(1).problem());
        assertTrue(pools.get(2).problem().contains("Farm::since is a long"), "an int never widens: " + pools.get(2));
        assertTrue(pools.get(3).problem().contains("takes 4 of 3"), pools.get(3).problem());
        assertTrue(pools.get(4).reads(), pools.get(4).problem());
        assertEquals(List.of("Hero.CYRA"), written(pools.get(4)), "a constant is written with its class");
        assertEquals(List.of("com.bot.Hero"), pools.get(4).elements().getFirst().imports());
    }

    @Test
    void withoutTheContractOnTheClasspathItSaysSo() {
        Map<Path, String> sources = new LinkedHashMap<>();
        sources.put(root.resolve("com/bot/params/bot/Farm.java"), FARM.replace("import com.bot.Hero;\n", "")
                .replace("public List<Hero> team() { return List.of(Hero.ARIA); }\n", "")
                .replace("public Hero leader() { return Hero.ARIA; }\n", ""));
        String group = """
                package com.bot.params.bot;

                import com.botmaker.plugin.api.params.Pool;
                import com.botmaker.plugin.api.params.Pools;

                @Pools
                public final class FarmPools {
                    public Pool<String> accounts() { return Pool.of("a").fills(Farm::account); }
                }
                """;
        Path file = root.resolve("com/bot/params/bot/FarmPools.java");
        sources.put(file, group);
        PoolReader.Reading pool = BotIndex.uncached(sources, List.of(), root).read(units ->
                PoolReader.read(units.get(file), group, units.values(), ValueGrammar.empty(), BotNames.NONE,
                        PoolReader.Elements.NONE)).getFirst();
        assertTrue(pool.problem().contains("project's classpath"), pool.problem());
    }
}
