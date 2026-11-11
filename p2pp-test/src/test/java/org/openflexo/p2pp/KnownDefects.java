package org.openflexo.p2pp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assume;

/**
 * The known defects of p2pp-core ({@code p2pp-core/KNOWN_DEFECTS.md}) as the tests see them.
 * <p>
 * By default a test that shows a known defect FAILS: the goal is to fix the defects, and a red test is the work to do. The
 * {@code known-defect} marks of the scenarios and of the corpus files only say which defect a test is about.
 * <p>
 * With {@code -Dp2pp.tolerateKnownDefects=true} those tests wait for the fix instead of failing (they are skipped, or, for the marked
 * scenarios and corpus files, they expect the failure and fail when it is gone). Each of them is recorded here, and the build prints which
 * defects the run waits for.
 * <pre>
 * ./gradlew :p2pp-test:test -Dp2pp.tolerateKnownDefects=true
 * </pre>
 */
public final class KnownDefects {

	private static final boolean TOLERATED = Boolean.getBoolean("p2pp.tolerateKnownDefects");

	/** File where the test JVM records the tests waiting for a fix, one row per test and defect: {@code id TAB test}; set by the build */
	private static final String REPORT = System.getProperty("p2pp.knownDefectsFile");

	private KnownDefects() {
	}

	/** True when the run was asked to tolerate the known defects: the tests that show one then wait for the fix instead of failing */
	public static boolean areTolerated() {
		return TOLERATED;
	}

	/** The defect ids of a mark such as {@code P2PP-D-8, P2PP-D-9} */
	public static List<String> ids(String mark) {
		List<String> returned = new ArrayList<>();
		for (String id : mark.split("[,\\s]+")) {
			if (!id.isEmpty()) {
				returned.add(id);
			}
		}
		return returned;
	}

	/** Record that a test waits for the fix of the defects of a mark */
	public static synchronized void register(String mark, String test) {
		if (REPORT == null) {
			return;
		}
		try {
			Path file = Paths.get(REPORT);
			StringBuilder rows = new StringBuilder();
			for (String id : ids(mark)) {
				rows.append(id).append('\t').append(test).append('\n');
			}
			Files.write(file, rows.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot record known defects in " + REPORT, e);
		}
	}

	/**
	 * First statement of a test that cannot pass until a defect is fixed: the test fails like any other, unless the run tolerates the known
	 * defects, where it is skipped.
	 */
	public static void skipIfTolerated(String mark) {
		if (TOLERATED) {
			StackWalker.StackFrame caller = StackWalker.getInstance().walk(s -> s.skip(1).findFirst()).get();
			register(mark, caller.getClassName().substring(caller.getClassName().lastIndexOf('.') + 1) + "." + caller.getMethodName());
			Assume.assumeTrue(mark + " is a known defect, tolerated by this run", false);
		}
	}
}
