package org.openflexo.p2pp;

import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openflexo.p2pp.matrix.EditCase;
import org.openflexo.p2pp.matrix.EditCaseChecker;
import org.openflexo.p2pp.matrix.EditGenerator;
import org.openflexo.p2pp.matrix.MiniCorpus;
import org.openflexo.p2pp.mini.MiniParseException;

/**
 * The edit matrix: every atomic edit of the model of every source of the corpus (see {@link EditGenerator}), checked against the invariants
 * of {@link EditCaseChecker}. One test per kind of edit and position ({@code add-member/first}, {@code remove-param/only}...), which runs
 * all the cases of that group and reports how many fail, with the first ones.
 * <p>
 * The groups that show a known defect are listed in {@code matrix-known-defects.txt}: they fail like the others, unless the run tolerates the
 * known defects (see {@link KnownDefects}): they then must fail, and the test asks to remove the line when they do not.
 */
@RunWith(Parameterized.class)
public class TestMiniEditMatrix {

	private static final int REPORTED = 4;

	/** The groups listed in matrix-known-defects.txt, with their defects */
	private static Map<String, String> knownDefects() throws IOException {
		Map<String, String> returned = new LinkedHashMap<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(TestMiniEditMatrix.class.getResourceAsStream("/matrix-known-defects.txt"),
				StandardCharsets.UTF_8))) {
			String row;
			while ((row = r.readLine()) != null) {
				row = row.trim();
				if (!row.isEmpty() && !row.startsWith("#")) {
					String[] parts = row.split("\\s+", 2);
					returned.put(parts[0], parts[1]);
				}
			}
		}
		return returned;
	}

	@Parameters(name = "{0}")
	public static Collection<Object[]> groups() throws IOException, MiniParseException {
		Map<String, List<EditCase>> bySignature = new LinkedHashMap<>();
		for (Map.Entry<String, String> source : MiniCorpus.load().entrySet()) {
			for (EditCase c : EditGenerator.generate(source.getKey(), source.getValue())) {
				bySignature.computeIfAbsent(c.signature(), k -> new ArrayList<>()).add(c);
			}
		}
		List<Object[]> returned = new ArrayList<>();
		for (Map.Entry<String, List<EditCase>> e : bySignature.entrySet()) {
			returned.add(new Object[] { e.getKey(), e.getValue() });
		}
		return returned;
	}

	private final String signature;
	private final List<EditCase> cases;

	public TestMiniEditMatrix(String signature, List<EditCase> cases) {
		this.signature = signature;
		this.cases = cases;
	}

	@Test
	public void invariantsHold() throws MiniParseException, IOException {
		List<String> failed = new ArrayList<>();
		for (EditCase c : cases) {
			List<String> failures = EditCaseChecker.check(c);
			if (!failures.isEmpty()) {
				failed.add(c + "\n  " + failures.get(0).replace("\n", "\n  "));
			}
		}
		String known = knownDefects().get(signature);
		if (known != null && KnownDefects.areTolerated()) {
			if (failed.isEmpty()) {
				fail(signature + ": " + known + " seems fixed: remove the line of matrix-known-defects.txt");
			}
			KnownDefects.register(known, "TestMiniEditMatrix." + signature);
			return;
		}
		if (!failed.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			sb.append(failed.size()).append(" of ").append(cases.size()).append(" cases of ").append(signature).append(" fail")
					.append(known != null ? " (" + known + ")" : "").append("\n");
			for (int i = 0; i < Math.min(REPORTED, failed.size()); i++) {
				sb.append("* ").append(failed.get(i)).append("\n");
			}
			fail(sb.toString());
		}
	}
}
