// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.java.grammar;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.botmaker.java.parse.JavaLevel.LATEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A value of this PC found by its mark, and written into its method, as a run does (2026-11-15). */
class MarkedReturnsTest {

    private static final String RUNS = """
            package com_github_botmakerdev.botmaker_sdk;

            import com.botmaker.sdk.internal.plan.SdkRunValue;

            public class Runs {
                @SdkRunValue(SdkRunValue.Id.DONE)
                public static String done() { return "first"; }

                @SdkRunValue(SdkRunValue.Id.OTHER)
                public static String other() { return "kept"; }

                @Deprecated
                public static String unmarked() { return "no"; }
            }
            """;

    @Test
    void theMarkedMethodsReturnIsRead() {
        assertEquals(Optional.of("\"first\""), MarkedReturns.returned(LATEST, RUNS, "Runs", "SdkRunValue", "DONE"));
        assertEquals(Optional.of("\"kept\""), MarkedReturns.returned(LATEST, RUNS, "Runs", "SdkRunValue", "OTHER"));
        assertTrue(MarkedReturns.returned(LATEST, RUNS, "Runs", "SdkRunValue", "GONE").isEmpty());
        assertTrue(MarkedReturns.returned(LATEST, RUNS, "Runs", "OtherMark", "DONE").isEmpty(), "another mark");
        assertTrue(MarkedReturns.returned(LATEST, RUNS, "Elsewhere", "SdkRunValue", "DONE").isEmpty());
        assertTrue(MarkedReturns.returned(LATEST, "class {", "Runs", "SdkRunValue", "DONE").isEmpty(), "broken");
    }

    @Test
    void aWriteReplacesOnlyThatMethodsReturn() {
        JavaValue second = JavaValue.parse("\"second\"").orElseThrow();

        String out = MarkedReturns.withReturned(LATEST, RUNS, "Runs", "SdkRunValue", "DONE", second).orElseThrow();

        assertTrue(out.contains("return \"second\";"), out);
        assertTrue(out.contains("return \"kept\";"), out);
        assertEquals(Optional.of("\"second\""), MarkedReturns.returned(LATEST, out, "Runs", "SdkRunValue", "DONE"));
        assertTrue(MarkedReturns.withReturned(LATEST, RUNS, "Runs", "SdkRunValue", "GONE", second).isEmpty());
    }
}
