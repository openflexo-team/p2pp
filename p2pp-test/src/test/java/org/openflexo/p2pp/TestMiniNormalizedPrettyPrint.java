package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;
import static org.openflexo.p2pp.mini.MiniModel.concept;
import static org.openflexo.p2pp.mini.MiniModel.field;
import static org.openflexo.p2pp.mini.MiniModel.method;
import static org.openflexo.p2pp.mini.MiniModel.param;
import static org.openflexo.p2pp.mini.MiniModel.unit;

import org.junit.Test;
import org.openflexo.p2pp.mini.MiniDocument;
import org.openflexo.p2pp.mini.MiniModel.Concept;
import org.openflexo.p2pp.mini.MiniModel.Field;
import org.openflexo.p2pp.mini.MiniModel.Unit;
import org.openflexo.p2pp.mini.MiniParseException;

/**
 * Normalized pretty-print (from scratch, from the model only) of the Mini language, one test group per kind of contents, then the round
 * trip text -> model -> text. A node built from a model object has no parsed text: this is the pretty-print a new object gets.
 */
public class TestMiniNormalizedPrettyPrint {

	private static String normalized(Unit unit) {
		return MiniDocument.of(unit).normalized();
	}

	// ---- Static and dynamic contents ----

	@Test
	public void emptyUnit() {
		assertEquals("unit U {\n}", normalized(unit("U")));
	}

	@Test
	public void dynamicContentsFollowTheModel() {
		Unit u = unit("U", concept("A"));
		assertEquals("unit U {\n\tconcept A {\n\t}\n}", normalized(u));
		u.setName("V");
		u.getConcepts().get(0).setName("B");
		assertEquals("unit V {\n\tconcept B {\n\t}\n}", MiniDocument.of(u).normalized());
	}

	@Test
	public void aDocumentFollowsTheModelBetweenTwoPrettyPrints() {
		Unit u = unit("U", concept("A"));
		MiniDocument doc = MiniDocument.of(u);
		assertEquals("unit U {\n\tconcept A {\n\t}\n}", doc.normalized());
		u.getConcepts().get(0).setName("Renamed");
		assertEquals("unit U {\n\tconcept Renamed {\n\t}\n}", doc.normalized());
	}

	// ---- Conditional and sequential contents ----

	@Test
	public void conditionalKeyword() {
		Concept a = concept("A").setAbstract(true);
		assertEquals("unit U {\n\tabstract concept A {\n\t}\n}", normalized(unit("U", a)));
	}

	@Test
	public void conditionalSequence() {
		// 'extends' and the parent name are two contents under one condition
		Concept a = concept("A").setParentName("B");
		assertEquals("unit U {\n\tconcept A extends B {\n\t}\n}", normalized(unit("U", a)));
	}

	@Test
	public void bothConditionsAtOnce() {
		Concept a = concept("A").setAbstract(true).setParentName("B");
		assertEquals("unit U {\n\tabstract concept A extends B {\n\t}\n}", normalized(unit("U", a)));
	}

	@Test
	public void conditionsAreEvaluatedAtEachPrettyPrint() {
		Concept a = concept("A").setAbstract(true).setParentName("B");
		MiniDocument doc = MiniDocument.of(unit("U", a));
		assertEquals("unit U {\n\tabstract concept A extends B {\n\t}\n}", doc.normalized());
		a.setAbstract(false);
		assertEquals("unit U {\n\tconcept A extends B {\n\t}\n}", doc.normalized());
		a.setParentName(null);
		assertEquals("unit U {\n\tconcept A {\n\t}\n}", doc.normalized());
		a.setAbstract(true);
		assertEquals("unit U {\n\tabstract concept A {\n\t}\n}", doc.normalized());
	}

	// ---- Child contents ----

	@Test
	public void childContents() {
		assertEquals("unit U {\n\tconcept A {\n\t\tfield x : int;\n\t}\n}", normalized(unit("U", concept("A", field("x", "int")))));
	}

	@Test
	public void childContentsFollowsAChangeOfTheChild() {
		Concept a = concept("A", field("x", "int"));
		MiniDocument doc = MiniDocument.of(unit("U", a));
		assertEquals("unit U {\n\tconcept A {\n\t\tfield x : int;\n\t}\n}", doc.normalized());
		((Field) a.getMembers().get(0)).getType().setName("long");
		assertEquals("unit U {\n\tconcept A {\n\t\tfield x : long;\n\t}\n}", doc.normalized());
	}

	// ---- Children contents: separators ----

	@Test
	public void childrenWithoutSeparatorsAndWithAnEndOfLineAfterEach() {
		Unit u = unit("U", concept("A"), concept("B"), concept("C"));
		assertEquals("unit U {\n\tconcept A {\n\t}\n\tconcept B {\n\t}\n\tconcept C {\n\t}\n}", normalized(u));
	}

	@Test
	public void childrenSeparatedByACommaNoneOneOrMany() {
		assertEquals("unit U {\n\tconcept A {\n\t\tmethod m();\n\t}\n}", normalized(unit("U", concept("A", method("m")))));
		assertEquals("unit U {\n\tconcept A {\n\t\tmethod m(a : int);\n\t}\n}",
				normalized(unit("U", concept("A", method("m", param("a", "int"))))));
		assertEquals("unit U {\n\tconcept A {\n\t\tmethod m(a : int, b : string, c : bool);\n\t}\n}",
				normalized(unit("U", concept("A", method("m", param("a", "int"), param("b", "string"), param("c", "bool"))))));
	}

	@Test
	public void childrenOfSeveralKindsKeepTheirOrder() {
		Concept a = concept("A", method("m"), field("x", "int"), method("n"), field("y", "int"));
		assertEquals("unit U {\n\tconcept A {\n\t\tmethod m();\n\t\tfield x : int;\n\t\tmethod n();\n\t\tfield y : int;\n\t}\n}",
				normalized(unit("U", a)));
	}

	@Test
	public void childrenFollowAdditionsAndRemovals() {
		Concept a = concept("A", field("x", "int"));
		MiniDocument doc = MiniDocument.of(unit("U", a));
		a.getMembers().add(field("y", "int"));
		assertEquals("unit U {\n\tconcept A {\n\t\tfield x : int;\n\t\tfield y : int;\n\t}\n}", doc.normalized());
		a.getMembers().add(0, field("w", "int"));
		assertEquals("unit U {\n\tconcept A {\n\t\tfield w : int;\n\t\tfield x : int;\n\t\tfield y : int;\n\t}\n}", doc.normalized());
		a.getMembers().remove(1);
		assertEquals("unit U {\n\tconcept A {\n\t\tfield w : int;\n\t\tfield y : int;\n\t}\n}", doc.normalized());
		a.getMembers().clear();
		assertEquals("unit U {\n\tconcept A {\n\t}\n}", doc.normalized());
	}

	// ---- Indentation ----

	@Test
	public void indentationIsRelativeToTheDepth() {
		String[] rows = normalized(unit("U", concept("A", field("x", "int")))).split("\n");
		assertEquals("unit U {", rows[0]);
		assertEquals("\tconcept A {", rows[1]);
		assertEquals("\t\tfield x : int;", rows[2]);
		assertEquals("\t}", rows[3]);
		assertEquals("}", rows[4]);
	}

	// ---- Normalized form of a parsed source ----

	@Test
	public void normalizedFormIgnoresTheLayoutOfTheSource() throws MiniParseException {
		String canonical = "unit U {\n\tabstract concept A extends B {\n\t\tfield x : int;\n\t\tmethod m(a : int, b : string);\n\t}\n"
				+ "\tconcept C {\n\t}\n}";
		String messy = "// header\nunit U{abstract   concept A extends B{ field x:int; // x\n method m( a:int ,b : string ) ; }\n\n\n"
				+ "  concept C{}}\n// trailer\n";
		assertEquals(canonical, MiniDocument.parse(messy).normalized());
		assertEquals(canonical, MiniDocument.parse(TestMiniParser.SAMPLE).normalized());
	}

	// ---- Round trip ----

	private static void assertRoundTrip(Unit unit) throws MiniParseException {
		String text = normalized(unit);
		MiniDocument reparsed = MiniDocument.parse(text);
		assertEquals(unit.dump(), reparsed.getUnit().dump());
		assertEquals("normalized pretty-print is stable", text, reparsed.normalized());
		assertEquals("derived pretty-print of an untouched source is that source", text, reparsed.derived());
	}

	@Test
	public void normalizedTextParsesBackToTheSameModel() throws MiniParseException {
		assertRoundTrip(unit("U"));
		assertRoundTrip(unit("U", concept("A")));
		assertRoundTrip(unit("U", concept("A").setAbstract(true).setParentName("B"), concept("B", field("x", "int"))));
		assertRoundTrip(unit("U", concept("A", method("m"), method("n", param("a", "int")),
				method("o", param("a", "int"), param("b", "string")), field("x", "int"))));
	}

	// ---- Derived pretty-print of an untouched source ----

	private static void assertUntouched(String source) throws MiniParseException {
		assertEquals(source, MiniDocument.parse(source).derived());
	}

	@Test
	public void untouchedCanonicalSourceIsReproduced() throws MiniParseException {
		assertUntouched(TestMiniParser.SAMPLE);
		assertUntouched("unit U {\n}");
	}

	@Test
	public void untouchedSourceWithAnotherIndentationBlankRowsAndCommentsIsReproduced() throws MiniParseException {
		assertUntouched("// header\nunit U {\n  concept A {\n    field x : int;\n\n    /* the method */\n    method m(a : int);\n  }\n}");
	}

	@Test
	public void untouchedSourceWithFreeLayoutIsReproduced() throws MiniParseException {
		KnownDefects.skipIfTolerated("P2PP-D-6");
		assertUntouched("unit U{abstract   concept A extends B{ field x:int; // x\n method m( a:int ,b : string ) ; }\n\n\n  concept C{}}");
	}

	@Test
	public void untouchedSourceKeepsItsFinalNewline() throws MiniParseException {
		KnownDefects.skipIfTolerated("P2PP-D-5");
		assertUntouched("unit U {\n}\n");
	}
}
