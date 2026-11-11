package org.openflexo.p2pp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openflexo.p2pp.mini.MiniDocument;
import org.openflexo.p2pp.mini.MiniParseException;

/**
 * The corpus of Mini sources ({@code src/test/resources/mini/*.mini}): every file is parsed and checked against the invariants of the
 * pretty-print, with no model change.
 * <ul>
 * <li>the normalized pretty-print is stable: parsed again, it gives the same model and the same text;</li>
 * <li>the derived pretty-print of an untouched source is that source.</li>
 * </ul>
 * A file whose first row is {@code // p2pp-known-defect: P2PP-D-n} is a source that p2pp does not reproduce yet (see
 * {@code KNOWN_DEFECTS.md}): the test then expects the difference, so that it fails, asking to remove the row, when the defect is fixed.
 * <p>
 * The final line separator of a file is dropped before parsing: p2pp cannot reproduce it (P2PP-D-5, which has its own test).
 */
@RunWith(Parameterized.class)
public class TestMiniCorpus {

	private static final Pattern KNOWN_DEFECT = Pattern.compile("^// p2pp-known-defect: (P2PP-D-\\d+)");

	@Parameters(name = "{0}")
	public static Collection<Object[]> corpus() {
		File[] files = new File(TestMiniCorpus.class.getResource("/mini").getFile()).listFiles((dir, name) -> name.endsWith(".mini"));
		Arrays.sort(files);
		List<Object[]> returned = new ArrayList<>();
		for (File f : files) {
			returned.add(new Object[] { f.getName(), f });
		}
		return returned;
	}

	private final String source;
	private final String knownDefect;
	private final String name;

	public TestMiniCorpus(String name, File file) throws IOException {
		this.name = name;
		String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		this.source = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
		Matcher m = KNOWN_DEFECT.matcher(source);
		this.knownDefect = m.find() ? m.group(1) : null;
	}

	@Test
	public void theCorpusIsNotEmpty() {
		assertTrue(source.length() > 0);
	}

	@Test
	public void parses() throws MiniParseException {
		MiniDocument.parse(source);
	}

	@Test
	public void normalizedPrettyPrintIsStable() throws MiniParseException {
		MiniDocument doc = MiniDocument.parse(source);
		String normalized = doc.normalized();
		MiniDocument reparsed = MiniDocument.parse(normalized);
		assertEquals(doc.getUnit().dump(), reparsed.getUnit().dump());
		assertEquals(normalized, reparsed.normalized());
		assertEquals("the normalized text is in the canonical layout", normalized, reparsed.derived());
	}

	@Test
	public void derivedPrettyPrintOfAnUntouchedSourceIsTheSource() throws MiniParseException {
		String derived = MiniDocument.parse(source).derived();
		if (knownDefect == null || !KnownDefects.areTolerated()) {
			assertEquals(source, derived);
		}
		else {
			assertNotEquals(knownDefect + " seems fixed: remove the known-defect row of this file", source, derived);
			KnownDefects.register(knownDefect, "TestMiniCorpus." + name);
		}
	}

	@Test
	public void derivedPrettyPrintStillParsesToTheSameModel() throws MiniParseException {
		MiniDocument doc = MiniDocument.parse(source);
		assertEquals(doc.getUnit().dump(), MiniDocument.parse(doc.derived()).getUnit().dump());
	}
}
