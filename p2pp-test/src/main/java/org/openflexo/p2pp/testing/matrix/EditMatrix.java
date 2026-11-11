package org.openflexo.p2pp.testing.matrix;

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

import org.openflexo.p2pp.testing.KnownDefects;
import org.openflexo.p2pp.testing.PrettyPrintTarget;

/**
 * The edit matrix of a language: the {@link EditCase}s that its generator produces, run by group (kind of edit / position) against the
 * invariants of {@link EditCaseChecker}. A test class of a language is a parameterized test over {@link #group(Collection)} that calls
 * {@link #run}.
 * <p>
 * The groups that show a known defect are listed in a resource of the language, one row per group, {@code kind/position  P2PP-D-n[, ...]}
 * (rows starting with {@code #} are comments). They fail like the others, unless the run tolerates the known defects (see
 * {@link KnownDefects}): they then must fail, and the test asks to remove the row when they do not.
 */
public final class EditMatrix {

	private static final int REPORTED = 4;

	private EditMatrix() {
	}

	/** The cases grouped by signature, as the parameters of a parameterized test: {signature, cases} */
	public static List<Object[]> group(Collection<EditCase> cases) {
		Map<String, List<EditCase>> bySignature = new LinkedHashMap<>();
		for (EditCase c : cases) {
			bySignature.computeIfAbsent(c.signature(), k -> new ArrayList<>()).add(c);
		}
		List<Object[]> returned = new ArrayList<>();
		for (Map.Entry<String, List<EditCase>> e : bySignature.entrySet()) {
			returned.add(new Object[] { e.getKey(), e.getValue() });
		}
		return returned;
	}

	/** The groups that show a known defect, with their defects, as listed in a resource of the classpath; empty if there is none */
	public static Map<String, String> knownDefects(Class<?> anchor, String resource) throws IOException {
		Map<String, String> returned = new LinkedHashMap<>();
		if (anchor.getResourceAsStream(resource) == null) {
			return returned;
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(anchor.getResourceAsStream(resource), StandardCharsets.UTF_8))) {
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

	/** Run the cases of a group; the group is {@code known} (the defects of its row of the list) or null */
	public static void run(PrettyPrintTarget target, String signature, List<EditCase> cases, String known) throws Exception {
		List<String> failed = new ArrayList<>();
		for (EditCase c : cases) {
			List<String> failures = EditCaseChecker.check(target, c);
			if (!failures.isEmpty()) {
				failed.add(c + "\n  " + failures.get(0).replace("\n", "\n  "));
			}
		}
		if (known != null && KnownDefects.areTolerated()) {
			if (failed.isEmpty()) {
				fail(signature + ": " + known + " seems fixed: remove the row of this group in the list of known defects");
			}
			KnownDefects.register(known, "EditMatrix." + signature);
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
