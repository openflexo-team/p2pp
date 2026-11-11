package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.openflexo.p2pp.mini.MiniDocument;
import org.openflexo.p2pp.mini.MiniModel.Concept;
import org.openflexo.p2pp.mini.MiniModel.Field;
import org.openflexo.p2pp.mini.MiniModel.Method;
import org.openflexo.p2pp.mini.MiniParseException;

/**
 * Tests of the fixture language itself: its parser, the model it builds, and the fragments of its nodes. They guard the harness the
 * pretty-print tests rely on.
 */
public class TestMiniParser {

	static final String SAMPLE = "unit U {\n" + "\tabstract concept A extends B {\n" + "\t\tfield x : int;\n"
			+ "\t\tmethod m(a : int, b : string);\n" + "\t}\n" + "\tconcept C {\n" + "\t}\n" + "}";

	@Test
	public void parsesTheModel() throws MiniParseException {
		MiniDocument doc = MiniDocument.parse(SAMPLE);
		assertEquals("unit U\n" + "  concept A abstract extends B\n" + "    field x : int\n" + "    method m\n" + "      param a : int\n"
				+ "      param b : string\n" + "  concept C\n", doc.getUnit().dump());
	}

	@Test
	public void commentsAndLayoutAreIgnored() throws MiniParseException {
		String messy = "// header\n\n/* a block\n   over two rows */ unit   U{abstract concept A{field x:int;/* inline */}\n"
				+ "concept C extends A { method m ( ) ; } } // trailer\n";
		assertEquals("unit U\n  concept A abstract\n    field x : int\n  concept C extends A\n    method m\n",
				MiniDocument.parse(messy).getUnit().dump());
	}

	@Test
	public void nodesKnowTheirFragment() throws MiniParseException {
		MiniDocument doc = MiniDocument.parse(SAMPLE);
		Concept a = doc.getUnit().getConcepts().get(0);
		Field x = (Field) a.getMembers().get(0);
		Method m = (Method) a.getMembers().get(1);
		assertEquals("abstract concept A extends B {\n\t\tfield x : int;\n\t\tmethod m(a : int, b : string);\n\t}",
				doc.getRootNode().getObjectNode(a).getLastParsedFragment().getRawText());
		assertEquals("field x : int;", doc.getRootNode().getObjectNode(x).getLastParsedFragment().getRawText());
		assertEquals("int", doc.getRootNode().getObjectNode(x.getType()).getLastParsedFragment().getRawText());
		assertEquals("method m(a : int, b : string);", doc.getRootNode().getObjectNode(m).getLastParsedFragment().getRawText());
		assertEquals("b : string", doc.getRootNode().getObjectNode(m.getParams().get(1)).getLastParsedFragment().getRawText());
	}

	@Test
	public void theUnitNodeCoversTheWholeSource() throws MiniParseException {
		String text = "// header\nunit U {\n}\n// trailer";
		MiniDocument doc = MiniDocument.parse(text);
		assertEquals(text, doc.getRootNode().getLastParsedFragment().getRawText());
	}

	@Test
	public void aNodeBuiltFromTheModelHasNoParsedFragment() {
		MiniDocument doc = MiniDocument.of(org.openflexo.p2pp.mini.MiniModel.unit("U"));
		assertTrue(doc.getRootNode().getASTNode() == null);
		assertEquals(null, doc.getRootNode().getLastParsedFragment());
		assertNotNull(doc.normalized());
	}

	private static void assertSyntaxError(String source, int line, int col) {
		try {
			MiniDocument.parse(source);
			fail("Expected a syntax error in [" + source + "]");
		} catch (MiniParseException e) {
			assertEquals(e.getMessage(), line, e.getLine());
			assertEquals(e.getMessage(), col, e.getCol());
		}
	}

	@Test
	public void syntaxErrorsAreLocated() {
		assertSyntaxError("unit U {\n\tconcept A {\n\t\tfield x : int\n\t}\n}", 4, 1); // missing ';'
		assertSyntaxError("unit U {\n\tconcept A { field x # int; }\n}", 2, 21); // unknown character
		assertSyntaxError("unit U {\n\tconcept concept {\n\t}\n}", 2, 9); // keyword as a name
		assertSyntaxError("unit U {\n}\nextra", 3, 0); // text after the unit
		assertSyntaxError("unit U {\n\tconcept A {\n\t\tfield x : int;", 3, 0); // end of source inside a concept
		assertSyntaxError("unit U { /* never closed\n}", 2, 0); // unterminated comment
		assertSyntaxError("concept A { }", 1, 0); // a unit is expected
	}
}
