package com.botmaker.java.edit.palette;

import com.botmaker.java.grammar.ValueGrammar;
import com.botmaker.java.grammar.ValueTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chooser's type as a signature type, and back — and how a person reads one. */
class SignatureTypesTest {

    private static final ValueGrammar NO_PLUGINS = ValueGrammar.empty();

    @Test
    void aCatalogueTypeStaysDescribed() {
        SignatureType whole = SignatureTypes.of(int.class, NO_PLUGINS);
        assertEquals(new SignatureType.Described(BotType.Choice.of(BotType.WHOLE_NUMBER)), whole);
        assertEquals(new SignatureType.Described(BotType.Choice.listOf(BotType.TEXT)),
                SignatureTypes.of(ValueTypes.listOf(String.class), NO_PLUGINS));
        assertTrue(SignatureTypes.of(void.class, NO_PLUGINS).isVoid());
    }

    @Test
    void anythingElseIsTypedAndReadsAsJava() {
        SignatureType nested = SignatureTypes.of(ValueTypes.mapOf(String.class, ValueTypes.listOf(int.class)),
                NO_PLUGINS);
        assertInstanceOf(SignatureType.Typed.class, nested);
        assertEquals("Map<String, List<Integer>>", nested.sourceName());
        assertEquals("null",
                SignatureTypes.of(new ValueTypes.BotClass("com.bot.Pair", List.of()), NO_PLUGINS).defaultText());
    }

    @Test
    void theChooserOpensOnWhatTheSignatureSays() {
        assertEquals(int.class, SignatureTypes.typeOf(SignatureTypes.of(int.class, NO_PLUGINS), NO_PLUGINS));
        assertEquals(ValueTypes.listOf(String.class), SignatureTypes.typeOf(
                new SignatureType.Described(BotType.Choice.listOf(BotType.TEXT)), NO_PLUGINS));
        assertEquals(new ValueTypes.Unknown("Outcome"),
                SignatureTypes.typeOf(SignatureType.kept("Outcome"), NO_PLUGINS));
    }

    @Test
    void aVariableIsNamedAfterItsTypeNeverAKeyword() {
        assertEquals("number", TypeNames.variableName(int.class));
        assertEquals("pair", TypeNames.variableName(new ValueTypes.BotClass("com.bot.Pair", List.of())));
        assertEquals("strings", TypeNames.variableName(ValueTypes.listOf(String.class)));
    }
}
