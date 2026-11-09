package com.botmaker.java.edit.helpers;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotEntryTest {

    @Test
    void theClassHoldingMainAndItsPackageAreRead() {
        assertEquals(Optional.of(new BotEntry("com.acme.bot", "Bot")), BotEntry.of("""
                package com.acme.bot;

                public class Bot {
                    public static void main(String[] args) {}
                }
                """));
        assertEquals("com.acme.bot.Bot", new BotEntry("com.acme.bot", "Bot").qualifiedName());
    }

    @Test
    void varargsOnAnotherLineAndTheDefaultPackageCount() {
        Optional<BotEntry> entry = BotEntry.of("""
                public class Bot {
                    public static
                    void main(String... args) {}
                }
                """);
        assertEquals(Optional.of(new BotEntry("", "Bot")), entry);
        assertEquals("Bot", entry.orElseThrow().qualifiedName());
    }

    @Test
    void aCommentOrAStringNamingMainIsNoEntry() {
        assertEquals(Optional.empty(), BotEntry.of("""
                package com.acme;

                // public static void main(String[] args) {}
                class Helper {
                    String text = "static void main(";
                    void main(int times) {}
                }
                """));
    }

    @Test
    void theTopLevelTypeIsTheEntryWhenMainSitsInAMemberClass() {
        assertEquals(Optional.of(new BotEntry("com.acme", "Outer")), BotEntry.of("""
                package com.acme;

                class Other {}

                class Outer {
                    static class Inner {
                        public static void main(String[] args) {}
                    }
                }
                """));
    }

    @Test
    void aFileThatDoesNotParseIsReadByItsTokens() {
        assertEquals(Optional.of(new BotEntry("com.acme", "Bot")), BotEntry.of("""
                package com.acme;

                public class Bot {
                    public static void main(String[] args) {
                        int x = ;
                """));
        Optional<BotEntry> nameless = BotEntry.of("package com.acme;\npublic clas F { public static void main(String[] a) {} }\n");
        assertEquals("com.acme", nameless.orElseThrow().packageName());
        assertEquals(false, nameless.orElseThrow().named());
    }
}
