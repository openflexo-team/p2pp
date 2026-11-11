package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;

import org.junit.Ignore;
import org.junit.Test;
import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.p2pp.RawSource.RawSourcePosition;

/**
 * Edge cases of {@link RawSource}: source shapes (no trailing newline, blank lines, CRLF), positions, fragments, offsets.
 */
public class TestRawSourceEdgeCases {

	private static RawSource source(String text) throws IOException {
		return new RawSource(new StringReader(text));
	}

	// ---- Shapes of source ----

	@Test
	public void singleLineWithoutNewline() throws IOException {
		RawSource s = source("abc");
		assertEquals(1, s.size());
		assertEquals("(1:0)", s.getStartPosition().toString());
		assertEquals("(1:3)", s.getEndPosition().toString());
		assertEquals("abc", s.makeFragment(s.getStartPosition(), s.getEndPosition()).getRawText());
	}

	@Test
	public void trailingNewlineDoesNotAddARow() throws IOException {
		RawSource s = source("a\nb\n");
		assertEquals(2, s.size());
		assertEquals("(2:1)", s.getEndPosition().toString());
	}

	@Test
	public void blankLinesAreRows() throws IOException {
		RawSource s = source("a\n\n\nb");
		assertEquals(4, s.size());
		assertEquals("", s.getRow(1));
		assertEquals("", s.getRow(2));
	}

	@Test
	public void crlfIsNormalizedToLf() throws IOException {
		RawSource s = source("a\r\nb");
		assertEquals(2, s.size());
		assertEquals("a", s.getRow(0));
		assertEquals("a\nb", s.makeFragment(s.getStartPosition(), s.getEndPosition()).getRawText());
	}

	@Ignore("P2PP-D-2")
	@Test
	public void emptySourceIsSupported() throws IOException {
		// Suspected defect: endPosition is computed from rows.get(rows.size() - 1) on an empty list
		RawSource s = source("");
		assertEquals(s.getStartPosition(), s.getEndPosition());
	}

	// ---- Positions ----

	@Test
	public void cannotMoveOutOfTheSource() throws IOException {
		RawSource s = source("ab");
		try {
			s.getStartPosition().decrement();
			fail("decrement before start must fail");
		} catch (ArrayIndexOutOfBoundsException e) {
			// expected
		}
		try {
			s.getEndPosition().increment();
			fail("increment after end must fail");
		} catch (ArrayIndexOutOfBoundsException e) {
			// expected
		}
	}

	@Test
	public void incrementCrossesBlankLines() throws IOException {
		RawSource s = source("a\n\nb");
		RawSourcePosition p = s.getStartPosition().increment(); // (1:1)
		assertEquals("(1:1)", p.toString());
		p = p.increment();
		assertEquals("(2:0)", p.toString());
		p = p.increment();
		assertEquals("(3:0)", p.toString());
		assertEquals("(2:0)", p.decrement().toString());
		assertEquals("(1:1)", p.decrement().decrement().toString());
	}

	@Test
	public void comparisons() throws IOException {
		RawSource s = source("abc\ndef");
		RawSourcePosition a = s.makePositionAfterChar(1, 2);
		RawSourcePosition b = s.makePositionAfterChar(1, 3);
		RawSourcePosition c = s.makePositionAfterChar(2, 0);
		assertTrue(a.isBefore(b));
		assertTrue(b.isBefore(c));
		assertTrue(c.isAfter(a));
		assertTrue(a.isBeforeOrEquals(a));
		assertTrue(a.isAfterOrEquals(a));
		assertFalse(a.isAfter(a));
		assertEquals(0, a.compareTo(s.makePositionAfterChar(1, 2)));
	}

	@Test
	public void offsets() throws IOException {
		RawSource s = source("abc\ndef\n\nx");
		assertEquals(0, s.getStartPosition().getOffset());
		assertEquals(2, s.makePositionAfterChar(1, 2).getOffset());
		assertEquals(4, s.makePositionAfterChar(2, 0).getOffset());
		assertEquals(8, s.makePositionAfterChar(3, 0).getOffset());
		assertEquals(9, s.makePositionAfterChar(4, 0).getOffset());
		// getIndex gives the same answer as getOffset
		RawSourcePosition p = s.getStartPosition();
		while (true) {
			assertEquals(p.getOffset(), s.getIndex(p));
			if (!p.canIncrement()) {
				break;
			}
			p = p.increment();
		}
	}

	@Test
	public void offsetOfEveryPositionMatchesTheText() throws IOException {
		String text = "ab\n\ncde\nf";
		RawSource s = source(text);
		RawSourcePosition p = s.getStartPosition();
		int expected = 0;
		while (p.canIncrement()) {
			assertEquals(expected, p.getOffset());
			p = p.increment();
			expected++;
		}
		assertEquals(text.length(), p.getOffset());
	}

	@Test
	public void charAfter() throws IOException {
		RawSource s = source("ab\ncd");
		assertEquals(Character.valueOf('a'), s.getStartPosition().getCharAfter());
		assertEquals(Character.valueOf('b'), s.makePositionAfterChar(1, 1).getCharAfter());
		assertNull("end of line", s.makePositionAfterChar(1, 2).getCharAfter());
		assertNull("end of source", s.getEndPosition().getCharAfter());
	}

	@Test
	public void lengthTo() throws IOException {
		RawSource s = source("abc\ndef");
		RawSourcePosition start = s.getStartPosition();
		assertEquals(0, start.getLengthTo(start));
		assertEquals(2, start.getLengthTo(start.increment(2)));
		assertEquals(5, start.getLengthTo(start.increment(5)));
	}

	@Ignore("P2PP-D-3")
	@Test
	public void lengthToAPositionBefore() throws IOException {
		RawSource s = source("abc\ndef");
		// Loops by moving away from the target until it falls out of the source
		assertEquals(2, Math.abs(s.getStartPosition().increment(5).getLengthTo(s.getStartPosition().increment(3))));
	}

	@Test
	public void insideFragment() throws IOException {
		RawSource s = source("abcdef");
		RawSourceFragment f = s.makeFragment(s.makePositionAfterChar(1, 1), s.makePositionAfterChar(1, 4));
		assertTrue(s.makePositionAfterChar(1, 1).isInside(f));
		assertTrue(s.makePositionAfterChar(1, 4).isInside(f));
		assertFalse(s.makePositionAfterChar(1, 0).isInside(f));
		assertFalse(s.makePositionAfterChar(1, 5).isInside(f));
		assertFalse(s.getStartPosition().isInside(null));
	}

	// ---- Fragments ----

	@Test
	public void emptyFragment() throws IOException {
		RawSource s = source("abc\ndef");
		RawSourcePosition p = s.makePositionAfterChar(1, 2);
		RawSourceFragment f = s.makeFragment(p, p);
		assertEquals("", f.getRawText());
		assertEquals(0, f.getLength());
	}

	@Test
	public void fragmentEndingAtStartOfALine() throws IOException {
		RawSource s = source("abc\ndef");
		RawSourceFragment f = s.makeFragment(s.makePositionAfterChar(1, 1), s.makePositionAfterChar(2, 0));
		assertEquals("bc\n", f.getRawText());
	}

	@Test
	public void fragmentOverBlankLines() throws IOException {
		RawSource s = source("a\n\n\nb");
		RawSourceFragment f = s.makeFragment(s.makePositionAfterChar(1, 1), s.makePositionAfterChar(4, 1));
		assertEquals("\n\n\nb", f.getRawText());
	}

	@Test
	public void fragmentTextLengthMatchesOffsets() throws IOException {
		String text = "ab\n\ncde\nf";
		RawSource s = source(text);
		for (int i = 0; i <= text.length(); i++) {
			for (int j = i; j <= text.length(); j++) {
				RawSourcePosition start = s.getStartPosition().increment(i);
				RawSourcePosition end = s.getStartPosition().increment(j);
				assertEquals("fragment " + i + "-" + j, text.substring(i, j), s.makeFragment(start, end).getRawText());
			}
		}
	}

	@Test
	public void intersectionWithEmptyFragment() throws IOException {
		RawSource s = source("abcdef");
		RawSourceFragment big = s.makeFragment(s.makePositionAfterChar(1, 1), s.makePositionAfterChar(1, 5));
		RawSourcePosition inside = s.makePositionAfterChar(1, 3);
		RawSourceFragment empty = s.makeFragment(inside, inside);
		assertFalse("an empty fragment maps nothing", big.intersects(empty) && empty.getLength() > 0);
	}

	@Test
	public void adjacentFragmentsDoNotIntersect() throws IOException {
		RawSource s = source("abcdef");
		RawSourceFragment f1 = s.makeFragment(s.makePositionAfterChar(1, 0), s.makePositionAfterChar(1, 3));
		RawSourceFragment f2 = s.makeFragment(s.makePositionAfterChar(1, 3), s.makePositionAfterChar(1, 6));
		assertFalse(f1.intersects(f2));
		assertFalse(f2.intersects(f1));
	}

	@Test
	public void fragmentsOfDifferentSourcesNeverIntersect() throws IOException {
		RawSource s1 = source("abc");
		RawSource s2 = source("abc");
		RawSourceFragment f1 = s1.makeFragment(s1.getStartPosition(), s1.getEndPosition());
		RawSourceFragment f2 = s2.makeFragment(s2.getStartPosition(), s2.getEndPosition());
		assertFalse(f1.intersects(f2));
		assertNotEquals(f1, f2);
	}

	@Test
	public void union() throws IOException {
		RawSource s = source("abcdef");
		RawSourceFragment f1 = s.makeFragment(s.makePositionAfterChar(1, 1), s.makePositionAfterChar(1, 3));
		RawSourceFragment f2 = s.makeFragment(s.makePositionAfterChar(1, 4), s.makePositionAfterChar(1, 6));
		assertEquals("bcdef", f1.union(f2).getRawText());
		assertEquals("bcdef", f2.union(f1).getRawText());
		assertEquals(f1, f1.union(null));
		assertEquals(f1, f1.union(f1));
	}

	@Test
	public void equalsAndHashCode() throws IOException {
		RawSource s = source("abcdef");
		RawSourceFragment f1 = s.makeFragment(s.makePositionAfterChar(1, 1), s.makePositionAfterChar(1, 3));
		RawSourceFragment f2 = s.makeFragment(1, 1, 1, 3);
		assertEquals(f1, f2);
		assertEquals(f1.hashCode(), f2.hashCode());
		assertEquals(s.makePositionAfterChar(1, 2), s.getStartPosition().increment(2));
		assertEquals(s.makePositionAfterChar(1, 2).hashCode(), s.getStartPosition().increment(2).hashCode());
	}

	@Test
	public void rawTextOfWholeSourceEndsWithANewlineEvenWithoutOne() throws IOException {
		// Documents current behaviour: RawSource.getRawText() appends a line separator to every row
		assertEquals("abc\n", source("abc").getRawText());
	}
}
