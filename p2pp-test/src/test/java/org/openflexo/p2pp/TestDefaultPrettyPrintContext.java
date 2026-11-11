package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.openflexo.p2pp.PrettyPrintContext.Indentation;

/**
 * Tests of {@link DefaultPrettyPrintContext}: indentation of a pretty-printed text.
 */
public class TestDefaultPrettyPrintContext {

	private final DefaultPrettyPrintContext indent = new DefaultPrettyPrintContext(Indentation.Indent);
	private final DefaultPrettyPrintContext doNotIndent = new DefaultPrettyPrintContext(Indentation.DoNotIndent);

	@Test
	public void resultingIndentation() {
		assertEquals("\t", indent.getResultingIndentation());
		assertEquals("", doNotIndent.getResultingIndentation());
	}

	@Test
	public void doNotIndentKeepsTheTextAsIs() {
		String text = "a\n\n  b\n";
		assertEquals(text, doNotIndent.indent(text));
	}

	@Test
	public void indentsEveryNonBlankRow() {
		assertEquals("\ta\n\tb", indent.indent("a\nb"));
	}

	@Test
	public void indentKeepsTheRelativeIndentation() {
		assertEquals("\ta\n\t  b", indent.indent("a\n  b"));
	}

	@Test
	public void indentKeepsALeadingLineSeparator() {
		assertEquals("\n\ta", indent.indent("\na"));
	}

	@Test
	public void blankRowsInTheMiddleAreDropped() {
		// Documents current behaviour: blank rows (other than the first) disappear
		assertEquals("\ta\n\tb", indent.indent("a\n\nb"));
	}

	@Test
	public void trailingLineSeparatorIsDropped() {
		// Documents current behaviour, which the nodes of FML and B rely on (they append their own separators): the final line separator
		// of the indented text is not kept. Keeping it doubled the separators of the normalized FML (18 tests of the FML pretty-print).
		assertEquals("\ta", indent.indent("a\n"));
	}

	@Test
	public void indentOfEmptyString() {
		assertEquals("", indent.indent(""));
	}

	@Test
	public void deriveBuildsAContextWithTheRequestedIndentation() {
		assertEquals(Indentation.Indent, doNotIndent.derive(Indentation.Indent).getIndentation());
		assertEquals(Indentation.DoNotIndent, indent.derive(Indentation.DoNotIndent).getIndentation());
	}

	@Test
	public void resultingIndentationIsCached() {
		assertSame(indent.getResultingIndentation(), indent.getResultingIndentation());
	}
}
