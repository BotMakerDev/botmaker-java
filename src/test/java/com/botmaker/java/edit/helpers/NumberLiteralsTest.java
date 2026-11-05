package com.botmaker.java.edit.helpers;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every legal spelling of a Java number reads as its value and its own type — the 60000L regression. */
class NumberLiteralsTest {

    @Test
    void aLongLiteralIsALong() {
        assertEquals(Optional.of(60000L), NumberLiterals.value("60000L"));
        assertEquals(Optional.of(255L), NumberLiterals.value("0xFFL"));
    }

    @Test
    void aSuffixDecidesOnlyForTheFormItBelongsTo() {
        for (String token : List.of("1", "1_000", "010", "0xE", "0xFF", "0xD", "0X1f", "0b101")) {
            assertEquals("int", NumberLiterals.typeName(token), token);
        }
        for (String token : List.of("1L", "1l", "0xFFL", "0b1L", "017L")) {
            assertEquals("long", NumberLiterals.typeName(token), token);
        }
        for (String token : List.of("1f", "1.5F", "1e3f", "0x1p3f", "0x1.8p1F")) {
            assertEquals("float", NumberLiterals.typeName(token), token);
        }
        for (String token : List.of("1d", "1.5", ".5", "1e3", "0x1p3", "0x1ep3", "0x1.8p1d")) {
            assertEquals("double", NumberLiterals.typeName(token), token);
        }
    }

    @Test
    void prefixesAndSeparatorsAreTheGrammars() {
        assertEquals(Optional.of(1000), NumberLiterals.value("1_000"));
        assertEquals(Optional.of(255), NumberLiterals.value("0xFF"));
        assertEquals(Optional.of(5), NumberLiterals.value("0b101"));
        assertEquals(Optional.of(8), NumberLiterals.value("010"));
        assertEquals(Optional.of(0), NumberLiterals.value("0"));
        // A hex literal spells the bits: 0xFFFFFFFF is a legal int, and it is -1.
        assertEquals(Optional.of(-1), NumberLiterals.value("0xFFFFFFFF"));
    }

    @Test
    void floatingLiteralsKeepTheirType() {
        assertEquals(Optional.of(1.5f), NumberLiterals.value("1.5f"));
        assertEquals(Optional.of(3.0), NumberLiterals.value("3.0"));
        assertEquals(Optional.of(1000.0), NumberLiterals.value("1e3"));
        assertEquals(Optional.of(2.0), NumberLiterals.value("2d"));
    }

    @Test
    void whatIsNotALiteralIsEmptyRatherThanAThrow() {
        assertEquals(Optional.empty(), NumberLiterals.value("2147483648"));   // legal only under a minus
        assertEquals(Optional.empty(), NumberLiterals.value(""));
        assertEquals(Optional.empty(), NumberLiterals.value("0x"));
    }

    @Test
    void onlyWhatANumberFieldWritesBackIsPlain() {
        assertTrue(NumberLiterals.isPlainDecimal("60000L"));
        assertTrue(NumberLiterals.isPlainDecimal("3.0"));
        assertTrue(NumberLiterals.isPlainDecimal("1.5f"));
        assertFalse(NumberLiterals.isPlainDecimal("0xFF"));
        assertFalse(NumberLiterals.isPlainDecimal("1_000"));
        assertFalse(NumberLiterals.isPlainDecimal("1e3"));
        assertFalse(NumberLiterals.isPlainDecimal("010"));
    }
}
