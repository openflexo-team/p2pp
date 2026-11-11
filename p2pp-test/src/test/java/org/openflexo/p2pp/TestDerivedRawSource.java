package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Test;
import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.p2pp.RawSource.RawSourcePosition;

/**
 * Tests of {@link DerivedRawSource}: the initial text with replacements, insertions and deletions applied.
 */
public class TestDerivedRawSource {

	private static final String TEXT = "class A {\n\tint x;\n\tint y;\n}";

	private RawSource raw;

	private DerivedRawSource derived(String text) throws IOException {
		raw = new RawSource(new StringReader(text));
		return new DerivedRawSource(raw.makeFragment(raw.getStartPosition(), raw.getEndPosition()));
	}

	/** Fragment from offset i to offset j of the source */
	private RawSourceFragment frag(int i, int j) {
		return raw.makeFragment(raw.getStartPosition().increment(i), raw.getStartPosition().increment(j));
	}

	private RawSourcePosition pos(int i) {
		return raw.getStartPosition().increment(i);
	}

	@Test
	public void noModification() throws IOException {
		assertEquals(TEXT, derived(TEXT).getStringRepresentation());
	}

	@Test
	public void replace() throws IOException {
		DerivedRawSource d = derived(TEXT);
		d.replace(frag(6, 7), "Bee");
		assertEquals("class Bee {\n\tint x;\n\tint y;\n}", d.getStringRepresentation());
	}

	@Test
	public void replaceByIdenticalTextIsANoOp() throws IOException {
		DerivedRawSource d = derived(TEXT);
		d.replace(frag(6, 7), "A");
		assertEquals(TEXT, d.getStringRepresentation());
	}

	@Test
	public void insert() throws IOException {
		DerivedRawSource d = derived(TEXT);
		d.insert(pos(7), " extends B");
		assertEquals("class A extends B {\n\tint x;\n\tint y;\n}", d.getStringRepresentation());
	}

	@Test
	public void insertAtStartAndAtEnd() throws IOException {
		DerivedRawSource d = derived("abc");
		d.insert(pos(0), "<");
		d.insert(pos(3), ">");
		assertEquals("<abc>", d.getStringRepresentation());
	}

	@Test
	public void remove() throws IOException {
		DerivedRawSource d = derived("abcdef");
		d.remove(frag(2, 4));
		assertEquals("abef", d.getStringRepresentation());
	}

	@Test
	public void removeWholeLinesAcrossRows() throws IOException {
		DerivedRawSource d = derived(TEXT);
		// "\tint x;\n"
		d.remove(frag(10, 18));
		assertEquals("class A {\n\tint y;\n}", d.getStringRepresentation());
	}

	@Test
	public void severalModificationsAreOrderedByPosition() throws IOException {
		DerivedRawSource d = derived("abcdef");
		// Declared out of order on purpose
		d.replace(frag(4, 5), "E");
		d.insert(pos(0), "[");
		d.remove(frag(1, 2));
		d.replace(frag(2, 3), "C");
		assertEquals("[aCdEf", d.getStringRepresentation());
	}

	@Test
	public void replaceThenInsertAtTheSamePoint() throws IOException {
		DerivedRawSource d = derived("abcdef");
		d.replace(frag(1, 3), "XY");
		d.insert(pos(3), "!");
		assertEquals("aXY!def", d.getStringRepresentation());
	}

	@Test
	public void insertBeforeAReplacementStartingAtTheSamePoint() throws IOException {
		DerivedRawSource d = derived("abcdef");
		d.replace(frag(1, 3), "XY");
		d.insert(pos(1), "!");
		assertEquals("a!XYdef", d.getStringRepresentation());
	}

	@Test
	public void nestedDerivedRawSource() throws IOException {
		DerivedRawSource d = derived("ab(cd)ef");
		RawSourceFragment inner = frag(3, 5);
		DerivedRawSource innerDerived = new DerivedRawSource(inner);
		innerDerived.replace(raw.makeFragment(pos(3), pos(4)), "C");
		d.replace(inner, innerDerived);
		assertEquals("ab(Cd)ef", d.getStringRepresentation());
	}

	@Test
	public void multiLineReplacement() throws IOException {
		DerivedRawSource d = derived("a\nb\nc");
		d.replace(frag(1, 4), "-");
		assertEquals("a-c", d.getStringRepresentation());
	}

	@Test
	public void derivedFromAFragmentOnly() throws IOException {
		raw = new RawSource(new StringReader("0123456789"));
		DerivedRawSource d = new DerivedRawSource(frag(2, 8));
		d.replace(frag(3, 4), "X");
		assertEquals("2X4567", d.getStringRepresentation());
	}

	@Test
	public void representationIsStableWhenAskedTwice() throws IOException {
		DerivedRawSource d = derived("abcdef");
		d.replace(frag(1, 3), "XY");
		d.insert(pos(5), "!");
		String first = d.getStringRepresentation();
		assertEquals(first, d.getStringRepresentation());
	}
}
