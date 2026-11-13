package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;
import org.openflexo.p2pp.RawSource.RawSourceFragment;

/**
 * A {@link DynamicContents} that keeps its parsed text while its value is unchanged (P2PP-F-2): the value is a normalized form of the text
 * (here the decimal form of an integer written in hexadecimal), so writing it again would change an untouched source.
 */
public class TestKeepParsedTextWhileUnchanged {

	/** The model: one integer */
	static class Holder {
		int value;
	}

	/** A node whose AST is the fragment it was parsed from, with the integer as its only dynamic contents */
	static class IntNode extends P2PPNode<RawSourceFragment, Holder> {
		private final RawSource rawSource;
		private final RawSourceFragment valueFragment;
		private final boolean keep;
		/** When set, the text written by the preserving pretty-print (DynamicContents.derivedWith) */
		String derivedValue;

		IntNode(Holder holder, RawSourceFragment whole, RawSourceFragment valueFragment, boolean keep) {
			super(holder, whole, (FragmentRetriever<RawSourceFragment>) f -> f);
			this.rawSource = whole.getRawSource();
			this.valueFragment = valueFragment;
			this.keep = keep;
		}

		@Override
		public Holder buildModelObjectFromAST(RawSourceFragment astNode) {
			return getModelObject();
		}

		@Override
		public P2PPNode<RawSourceFragment, Holder> deserialize() {
			return this;
		}

		@Override
		public RawSource getRawSource() {
			return rawSource;
		}

		@Override
		public <C> P2PPNode<?, C> makeObjectNode(C object) {
			return null;
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			DynamicContents<RawSourceFragment, Holder> value = dynamicContents(() -> String.valueOf(getModelObject().value));
			if (keep) {
				value.keepingParsedTextWhileUnchanged();
			}
			value.derivedWith(() -> derivedValue != null ? derivedValue : String.valueOf(getModelObject().value));
			append(value, valueFragment);
		}
	}

	private static final String SOURCE = "x = 0x1F;";

	private IntNode parse(Holder holder, boolean keep, boolean capture) throws IOException {
		RawSource rawSource = new RawSource(new StringReader(SOURCE));
		RawSourceFragment whole = rawSource.makeFragment(rawSource.getStartPosition(), rawSource.getEndPosition());
		RawSourceFragment value = rawSource.makeFragment(rawSource.makePositionAfterChar(1, 4), rawSource.makePositionAfterChar(1, 8));
		assertEquals("0x1F", value.getRawText());
		holder.value = 31;
		IntNode node = new IntNode(holder, whole, value, keep);
		node.initializePrettyPrint(node, node.makePrettyPrintContext());
		if (capture) {
			node.captureReferenceValues();
		}
		return node;
	}

	private static String derived(IntNode node) {
		return node.getTextualRepresentation(node.makePrettyPrintContext());
	}

	@Test
	public void byDefaultTheValueIsWrittenAgain() throws IOException {
		assertEquals("x = 31;", derived(parse(new Holder(), false, true)));
	}

	@Test
	public void anUnchangedValueKeepsTheParsedText() throws IOException {
		assertEquals(SOURCE, derived(parse(new Holder(), true, true)));
	}

	@Test
	public void aChangedValueIsWritten() throws IOException {
		Holder holder = new Holder();
		IntNode node = parse(holder, true, true);
		holder.value = 32;
		assertEquals("x = 32;", derived(node));
	}

	@Test
	public void aValueChangedBackKeepsTheParsedTextAgain() throws IOException {
		Holder holder = new Holder();
		IntNode node = parse(holder, true, true);
		holder.value = 32;
		assertEquals("x = 32;", derived(node));
		holder.value = 31;
		assertEquals(SOURCE, derived(node));
	}

	@Test
	public void withoutACapturedReferenceTheValueIsWritten() throws IOException {
		assertEquals("x = 31;", derived(parse(new Holder(), true, false)));
	}

	/** The preserving pretty-print writes the derived value; the normalized pretty-print, the value itself */
	@Test
	public void theDerivedValueIsWhatThePreservingPrettyPrintWrites() throws IOException {
		Holder holder = new Holder();
		IntNode node = parse(holder, true, true);
		holder.value = 32;
		node.derivedValue = "0x20";
		assertEquals("x = 0x20;", derived(node));
		assertEquals("32", node.getNormalizedTextualRepresentation(node.makePrettyPrintContext()));
		// A contents written in its normalized form inside the preserving pretty-print (an object created by a model change)
		assertEquals("0x20", node.getNormalizedTextualRepresentation(node.makePrettyPrintContext().preservingParsedText()));
		// The comparison with the reference value is made on the value itself
		holder.value = 31;
		assertEquals(SOURCE, derived(node));
	}
}
