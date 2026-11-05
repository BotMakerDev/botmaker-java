package com.botmaker.java.grammar;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A value held past its file's parse (2026-11-05): detached from the file's tree, and still read as written. */
class JavaValueTest {

    /** The expression sits mid-text, as an initialiser sits in its file. */
    private static final String FILE = "  Flow.of( Collect::body ,\n  \"x\" ) ";

    @Test
    void aDetachedValuePinsNoFileAndReadsItsPartsAsWritten() {
        SourceNode inFile = SourceNode.parse(FILE).orElseThrow();
        JavaValue value = JavaValue.detached(inFile);

        assertEquals("Flow.of( Collect::body ,\n  \"x\" )", value.source());
        assertNotSame(inFile.node().getRoot(), value.node().getRoot());
        MethodInvocation call = (MethodInvocation) value.written().node();
        assertEquals("Collect::body", value.written().child((Expression) call.arguments().get(0)).source());
        assertEquals("\"x\"", value.written().child((Expression) call.arguments().get(1)).source());
        assertTrue(value.sameJava(JavaValue.kept(inFile)));
    }

    @Test
    void aValueWhoseNodeIndexesOtherTextShowsAsPrinted() {
        assertNull(JavaValue.kept(SourceNode.parse(FILE).orElseThrow()).written().text());
        assertEquals("a.b(1)", JavaValue.parse("a.b(1)").orElseThrow().written().text());
    }
}
