package org.openflexo.p2pp.testing;

import java.util.ArrayList;
import java.util.List;

import org.openflexo.p2pp.testing.PrettyPrintTarget.Document;

/**
 * The invariants of a source that nobody edits, which a language can check without any language of edits: the first level of the harness.
 * <ul>
 * <li><b>parse</b>: the source parses;</li>
 * <li><b>derived</b>: the derived pretty-print is the source, to the character;</li>
 * <li><b>derived-parse-back</b>: the derived text parses back to the same model;</li>
 * <li><b>normalized-parse-back</b>: the normalized pretty-print parses back to the same model;</li>
 * <li><b>normalized-stable</b>: that second model pretty-prints (normalized) to the same text.</li>
 * </ul>
 */
public final class UntouchedChecks {

	private UntouchedChecks() {
	}

	/** The failures of a source, each starting with the name of the invariant; empty when the source passes */
	public static List<String> check(PrettyPrintTarget target, String source) {
		List<String> failures = new ArrayList<>();
		Document doc;
		try {
			doc = target.parse(source);
		} catch (Exception e) {
			failures.add("parse: the source does not parse (" + e + ")");
			return failures;
		}
		String model = doc.dump();

		String derived = doc.derived();
		if (!derived.equals(source)) {
			failures.add("derived: the pretty-print of the untouched source is not the source\n" + firstDifference(source, derived));
		}
		checkParseBack("derived-parse-back", target, derived, model, failures);

		String normalized = doc.normalized();
		checkParseBack("normalized-parse-back", target, normalized, model, failures);
		try {
			String again = target.parse(normalized).normalized();
			if (!again.equals(normalized)) {
				failures.add("normalized-stable: the normalized text, parsed and normalized again, changes\n"
						+ firstDifference(normalized, again));
			}
		} catch (Exception e) {
			// already reported as a parse-back failure
		}
		return failures;
	}

	private static void checkParseBack(String name, PrettyPrintTarget target, String text, String model, List<String> failures) {
		try {
			String parsedBack = target.parse(text).dump();
			if (!parsedBack.equals(model)) {
				failures.add(name + ": the text parses back to another model\n" + firstDifference(model, parsedBack));
			}
		} catch (Exception e) {
			failures.add(name + ": the text does not parse (" + e + ")");
		}
	}

	/** Where two texts start to differ, with some context, tabs made visible */
	static String firstDifference(String expected, String actual) {
		int i = 0;
		while (i < expected.length() && i < actual.length() && expected.charAt(i) == actual.charAt(i)) {
			i++;
		}
		int from = Math.max(0, i - 30);
		return "  first difference at offset " + i + "\n  expected: ..." + around(expected, from, i) + "\n  actual:   ..." + around(actual, from, i);
	}

	private static String around(String text, int from, int at) {
		int to = Math.min(text.length(), at + 30);
		return text.substring(Math.min(from, text.length()), to).replace("\t", "→").replace("\n", "⏎");
	}
}
