package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.openflexo.p2pp.mini.MiniCorpus;
import org.openflexo.p2pp.mini.MiniDocument;
import org.openflexo.p2pp.mini.MiniLayout;
import org.openflexo.p2pp.mini.MiniParseException;

/**
 * Re-indentation of a parsed text ({@link Reindenter}, P2PP-F-1), on the Mini corpus and on targeted cases. The oracles:
 * <ul>
 * <li>only the leading whitespace of rows changes;</li>
 * <li>the result parses to the same model, and re-indenting it again changes nothing;</li>
 * <li>a canonically indented text, comments included, is a fixed point, and so is every normalized pretty-print;</li>
 * <li>a canonical text whose indentation was scrambled (a block comment moved as a whole) comes back to the canonical text;</li>
 * <li>re-indenting a portion changes no row outside it, and gives the rows inside what re-indenting the whole text gives them.</li>
 * </ul>
 */
public class TestMiniReindent {

	private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
	private static final String[] PREFIXES = { "", " ", "\t", "\t\t\t", "  \t", "        " };

	private static String reindent(String text) throws MiniParseException {
		return new Reindenter(MiniDocument.parse(text).getRootNode()).reindent();
	}

	private static String[] rows(String text) {
		return text.split("\n", -1);
	}

	private static String stripLeading(String text) {
		StringBuilder sb = new StringBuilder();
		for (String row : rows(text)) {
			sb.append(row.replaceFirst("^\\s+", "")).append('\n');
		}
		return sb.toString();
	}

	private static String show(String text) {
		return text.replace("\t", "→");
	}

	@Test
	public void onlyLeadingWhitespaceChanges() throws Exception {
		for (Map.Entry<String, String> e : MiniCorpus.load().entrySet()) {
			assertEquals(e.getKey(), stripLeading(e.getValue()), stripLeading(reindent(e.getValue())));
		}
	}

	@Test
	public void theResultParsesToTheSameModelAndIsAFixedPoint() throws Exception {
		for (Map.Entry<String, String> e : MiniCorpus.load().entrySet()) {
			String once = reindent(e.getValue());
			assertEquals(e.getKey(), MiniDocument.parse(e.getValue()).normalized(), MiniDocument.parse(once).normalized());
			assertEquals(e.getKey(), show(once), show(reindent(once)));
		}
	}

	@Test
	public void aCanonicalTextIsAFixedPoint() throws Exception {
		int checked = 0;
		for (Map.Entry<String, String> e : MiniCorpus.load().entrySet()) {
			if (MiniLayout.violation(e.getValue()) == null) {
				assertEquals(e.getKey(), show(e.getValue()), show(reindent(e.getValue())));
				checked++;
			}
		}
		assertTrue("too few canonical sources: " + checked, checked >= 5);
	}

	@Test
	public void aNormalizedPrettyPrintIsAFixedPoint() throws Exception {
		for (Map.Entry<String, String> e : MiniCorpus.load().entrySet()) {
			String normalized = MiniDocument.parse(e.getValue()).normalized();
			assertEquals(e.getKey(), show(normalized), show(reindent(normalized)));
		}
	}

	@Test
	public void theResultOfAnyTextIsCanonical() throws Exception {
		// the sources that keep several braces on a row cannot be canonical by re-indentation alone
		for (Map.Entry<String, String> e : MiniCorpus.load().entrySet()) {
			String result = reindent(e.getValue());
			String violation = MiniLayout.violation(result);
			if (violation == null || !violation.contains("several braces") && !violation.contains("double or a trailing space")) {
				assertNull(e.getKey() + "\n" + show(result), violation);
			}
		}
	}

	/**
	 * Every row gets a random leading whitespace, except that the rows inside a block comment are moved with the row it begins on (an
	 * editor moving the comment as a whole)
	 */
	private static String scramble(String text, Random random) {
		List<int[]> blocks = new ArrayList<>();
		Matcher m = BLOCK_COMMENT.matcher(text);
		while (m.find()) {
			blocks.add(new int[] { m.start(), m.end() });
		}
		StringBuilder sb = new StringBuilder();
		int offset = 0;
		String headOld = null;
		String headNew = null;
		String[] rows = rows(text);
		for (int i = 0; i < rows.length; i++) {
			String row = rows[i];
			String stripped = row.replaceFirst("^\\s+", "");
			String old = row.substring(0, row.length() - stripped.length());
			boolean insideBlock = false;
			for (int[] b : blocks) {
				if (offset + old.length() > b[0] && offset + old.length() < b[1]) {
					insideBlock = true;
				}
			}
			String scrambled;
			if (insideBlock && headOld != null && old.startsWith(headOld)) {
				scrambled = stripped.isEmpty() ? "" : headNew + old.substring(headOld.length());
			}
			else {
				scrambled = PREFIXES[random.nextInt(PREFIXES.length)];
				if (!insideBlock) {
					// the row a block comment may begin on
					headOld = old;
					headNew = scrambled;
				}
			}
			sb.append(scrambled).append(stripped);
			if (i < rows.length - 1) {
				sb.append('\n');
			}
			offset += row.length() + 1;
		}
		return sb.toString();
	}

	@Test
	public void lineCommentsAreIndentedOneByOneAndBlockCommentsAsAWhole() throws MiniParseException {
		String source = String.join("\n", "unit U {", "// first", "  // deeper", "concept A {", "  /* commented out:", "  concept B {",
				"", "      field x;", "  }", "*/", "}", "}");
		// the end of the block comment is less indented than its first row: left as it is
		String expected = String.join("\n", "unit U {", "\t// first", "\t// deeper", "\tconcept A {", "\t\t/* commented out:",
				"\t\tconcept B {", "", "\t\t    field x;", "\t\t}", "*/", "\t}", "}");
		assertEquals(show(expected), show(reindent(source)));
	}

	@Test
	public void aScrambledCanonicalTextComesBack() throws Exception {
		Random random = new Random(20261112);
		for (Map.Entry<String, String> e : MiniCorpus.load().entrySet()) {
			if (MiniLayout.violation(e.getValue()) == null) {
				for (int n = 0; n < 20; n++) {
					String scrambled = scramble(e.getValue(), random);
					assertEquals(e.getKey() + " scrambled as\n" + show(scrambled), show(e.getValue()), show(reindent(scrambled)));
				}
			}
		}
	}

	@Test
	public void aPortionIsReindentedLikeTheWholeText() throws Exception {
		Random random = new Random(42);
		for (Map.Entry<String, String> e : MiniCorpus.load().entrySet()) {
			for (String source : new String[] { e.getValue(), scramble(e.getValue(), random) }) {
				Reindenter reindenter = new Reindenter(MiniDocument.parse(source).getRootNode());
				String[] original = rows(source);
				String[] whole = rows(reindenter.reindent());
				int size = reindenter.getRawSource().size();
				for (int n = 0; n < 10; n++) {
					int from = 1 + random.nextInt(size);
					int to = from + random.nextInt(size - from + 1);
					String[] portion = rows(reindenter.reindent(from, to));
					assertEquals(e.getKey(), original.length, portion.length);
					for (int line = 1; line <= original.length; line++) {
						String expected = line >= from && line <= to ? whole[line - 1] : original[line - 1];
						assertEquals(e.getKey() + " rows " + from + "-" + to + ", row " + line, show(expected), show(portion[line - 1]));
					}
				}
			}
		}
	}

	@Test
	public void aFreeLayoutIsReindented() throws MiniParseException {
		String source = "unit U{abstract   concept A extends B{ field x:int; // x\n method m( a:int ,b : string ) ; }\n  \n\n  concept C{}}";
		assertEquals(show("unit U{abstract   concept A extends B{ field x:int; // x\n\t\tmethod m( a:int ,b : string ) ; }\n\n\n\tconcept C{}}"),
				show(reindent(source)));
	}

	@Test
	public void commentsFollowTheBlockTheyAreIn() throws MiniParseException {
		String source = String.join("\n", //
				"  // header", //
				"unit U {", //
				"// after an opening brace", //
				"concept A {", //
				"        // alone in a block", //
				"}", //
				"concept B {", //
				"field x;", //
				"    // after the last member", //
				"   }", //
				"/* a block comment", //
				"   over two rows */", //
				"concept C {", //
				"}", //
				"// before the closing brace of the unit", //
				"}", //
				"\t// trailer");
		String expected = String.join("\n", //
				"// header", //
				"unit U {", //
				"\t// after an opening brace", //
				"\tconcept A {", //
				"\t\t// alone in a block", //
				"\t}", //
				"\tconcept B {", //
				"\t\tfield x;", //
				"\t\t// after the last member", //
				"\t}", //
				"\t/* a block comment", //
				"\t   over two rows */", //
				"\tconcept C {", //
				"\t}", //
				"\t// before the closing brace of the unit", //
				"}", //
				"// trailer");
		assertEquals(show(expected), show(reindent(source)));
	}

	@Test
	public void annotationsAndContinuationRowsOfAList() throws MiniParseException {
		String source = String.join("\n", //
				"unit U {", //
				"@first", //
				"      @second", //
				"concept A {", //
				"method m(a : int,", //
				"b : string);", //
				"}", //
				"}");
		// a parameter starting a row continues the row of the method (no line break is declared before it): aligned on the first parameter
		String expected = String.join("\n", //
				"unit U {", //
				"\t@first", //
				"\t@second", //
				"\tconcept A {", //
				"\t\tmethod m(a : int,", //
				"\t\t         b : string);", //
				"\t}", //
				"}");
		assertEquals(show(expected), show(reindent(source)));
	}

	@Test
	public void aContinuationRowIsAlignedOnTheTextAfterAnOpenBracket() throws MiniParseException {
		String source = String.join("\n", //
				"unit U {", //
				"concept A {", //
				"method m(", //
				"a : int,", //
				"b : string);", //
				"method n(  a : int, // first", //
				"/* ( not a bracket */ b : string,", //
				"c : int);", //
				"}", //
				"}");
		// a bracket ending its row: one level more; text after the bracket: aligned on it, comments skipped
		String expected = String.join("\n", //
				"unit U {", //
				"\tconcept A {", //
				"\t\tmethod m(", //
				"\t\t\ta : int,", //
				"\t\t\tb : string);", //
				"\t\tmethod n(  a : int, // first", //
				"\t\t           /* ( not a bracket */ b : string,", //
				"\t\t           c : int);", //
				"\t}", //
				"}");
		assertEquals(show(expected), show(reindent(source)));
	}

	@Test
	public void theLexicalSyntaxIsAParameter() throws MiniParseException {
		String source = String.join("\n", "unit U {", "concept A {", "// note", "method m(a : int,", "b : string);", "}", "}");
		MiniDocument document = MiniDocument.parse(source);
		// no delimiter of alignment: a continuation row is one level more
		String withoutAlignment = new Reindenter(document.getRootNode(), "\t", LexicalSyntax.C_STYLE.withoutAlignment()).reindent();
		assertEquals(show(String.join("\n", "unit U {", "\tconcept A {", "\t\t// note", "\t\tmethod m(a : int,", "\t\t\tb : string);", "\t}",
				"}")), show(withoutAlignment));
		// no comment syntax: "// note" is text that no node describes, left as it is
		String withoutComments = new Reindenter(document.getRootNode(), "\t", LexicalSyntax.C_STYLE.withComments(null, null, null))
				.reindent();
		assertEquals(show(String.join("\n", "unit U {", "\tconcept A {", "// note", "\t\tmethod m(a : int,", "\t\t         b : string);",
				"\t}", "}")), show(withoutComments));
		// the root node gives its syntax: C style by default
		assertEquals(show(new Reindenter(document.getRootNode(), "\t", LexicalSyntax.C_STYLE).reindent()),
				show(new Reindenter(document.getRootNode()).reindent()));
	}

	@Test
	public void anotherUnitIsUsedWhenAsked() throws MiniParseException, IOException {
		String source = MiniCorpus.load().get("canonical.mini");
		String result = new Reindenter(MiniDocument.parse(source).getRootNode(), "  ").reindent();
		assertEquals(source.replace("\t", "  "), result);
	}
}
