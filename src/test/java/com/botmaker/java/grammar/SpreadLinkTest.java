package com.botmaker.java.grammar;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCall;
import com.botmaker.plugin.api.value.Link;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A varargs link, {@code .tags("home", "daily")}, is one list part written with an argument per element (2026-11-06);
 * two links of one name, one per element type, each read by what its arguments read as; and a {@code Supplier} part
 * written {@code () -> …} answers what its body reads as.
 */
class SpreadLinkTest {

    /** A chore: tags and counts are varargs links of one name; {@code after} names another chore lazily. */
    public record Chore(String name, List<String> tags, List<Integer> counts, List<Supplier<Object>> after) {
        public static Chore of(String name) {
            return new Chore(name, List.of(), List.of(), List.of());
        }

        @Link
        public Chore tags(String... more) {
            return new Chore(name, plus(tags, more), counts, after);
        }

        public Chore tags(Integer... more) {
            return new Chore(name, tags, plus(counts, more), after);
        }

        @SafeVarargs
        @Link
        public final Chore after(Supplier<Object>... others) {
            return new Chore(name, tags, counts, plus(after, others));
        }

        private static <T> List<T> plus(List<T> had, T[] more) {
            List<T> all = new ArrayList<>(had);
            all.addAll(List.of(more));
            return all;
        }
    }

    private static final DeclaredCall<Chore> CHORE = ComponentType.part(Chore.class).writtenAs(Chore::of, Chore::name)
            .<Integer>withEach(Chore::tags, Chore::counts);

    private static final ValueGrammar GRAMMAR = ValueGrammar.of(List.of(), List.of(CHORE));

    private static final String OWNER = JavaNames.canonical(Chore.class);

    @Test
    void aVarargsLinkWritesEachElementAndReadsThemBack() {
        Chore chore = Chore.of("dishes").tags("home", "daily");
        String source = GRAMMAR.initializer(Chore.class, chore).map(JavaValue::source).orElseThrow();
        assertEquals(OWNER + ".of(\"dishes\").tags(\"home\", \"daily\")", source);
        assertEquals(Optional.of(chore), GRAMMAR.valueOf(Chore.class, source));
        assertEquals(OWNER + ".of(\"dishes\")",
                GRAMMAR.initializer(Chore.class, Chore.of("dishes")).map(JavaValue::source).orElseThrow(),
                "an empty list is no link");
    }

    /** {@code .tags("a").tags("b")} holds both, as its calls do at run time, and is written back as one link. */
    @Test
    void aVarargsLinkWrittenTwiceAddsToItsList() {
        Chore chore = Chore.of("dishes").tags("home", "daily");
        assertEquals(Optional.of(chore), GRAMMAR.valueOf(Chore.class, "Chore.of(\"dishes\").tags(\"home\").tags(\"daily\")"));
    }

    @Test
    void twoLinksOfOneNameReadByTheirArguments() {
        Chore chore = Chore.of("dishes").tags("home").tags(2, 3);
        assertEquals(Optional.of(chore), GRAMMAR.valueOf(Chore.class, "Chore.of(\"dishes\").tags(2, 3).tags(\"home\")"));
        String source = GRAMMAR.initializer(Chore.class, chore).map(JavaValue::source).orElseThrow();
        assertEquals(Optional.of(chore), GRAMMAR.valueOf(Chore.class, source), source);
    }

    @Test
    void aLambdaPartAnswersWhatItsBodyReadsAs() {
        Optional<Object> read = GRAMMAR.valueOf(Chore.class, "Chore.of(\"dishes\").after(() -> Chore.of(\"sweep\"))");
        assertTrue(read.isPresent());
        Chore chore = (Chore) read.get();
        assertEquals(Chore.of("sweep"), chore.after().getFirst().get());
        String source = GRAMMAR.initializer(Chore.class, chore).map(JavaValue::source).orElseThrow();
        assertEquals(OWNER + ".of(\"dishes\").after(() -> " + OWNER + ".of(\"sweep\"))", source);
    }
}
