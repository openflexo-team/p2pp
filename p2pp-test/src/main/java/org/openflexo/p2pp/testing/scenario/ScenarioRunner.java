package org.openflexo.p2pp.testing.scenario;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.openflexo.p2pp.testing.KnownDefects;
import org.openflexo.p2pp.testing.PrettyPrintTarget;
import org.openflexo.p2pp.testing.PrettyPrintTarget.Document;
import org.openflexo.p2pp.testing.scenario.Scenario.Edit;
import org.openflexo.p2pp.testing.scenario.Scenario.Step;

/**
 * Runs a {@link Scenario} on a language: parse the source, then for each step apply its edits to the model of the same document,
 * pretty-print, and check
 * <ul>
 * <li>the derived pretty-print is the expected text;</li>
 * <li>the derived text parses back to the edited model (whatever the expected text is, it must still mean what the model says);</li>
 * <li>the derived text is stable: parsed and pretty-printed again, with no edit, it does not change;</li>
 * <li>the normalized pretty-print is the expected one, when the step has one.</li>
 * </ul>
 * Texts are compared without their final line separators (a block of a scenario has none) and as {@link PrettyPrintTarget#sameText} says.
 * <p>
 * A scenario marked with a known defect fails like any other. When the run tolerates the known defects (see {@link KnownDefects}), it is
 * instead expected to break at least one of these checks, and fails when they all hold, so that it asks to remove its mark once the defect is
 * fixed. An edit that cannot be applied is an error of the scenario, never an expected failure.
 */
public final class ScenarioRunner {

	private ScenarioRunner() {
	}

	public static void run(PrettyPrintTarget target, Scenario scenario) {
		Document doc;
		try {
			doc = target.parse(scenario.getSource());
		} catch (Exception e) {
			throw new AssertionError(scenario + ": the source of the scenario does not parse: " + e.getMessage());
		}

		List<String> failures = new ArrayList<>();
		int stepNumber = 0;
		for (Step step : scenario.getSteps()) {
			stepNumber++;
			String where = scenario.getSteps().size() > 1 ? "step " + stepNumber + ": " : "";
			for (Edit edit : step.getEdits()) {
				try {
					doc.apply(edit.command);
				} catch (RuntimeException e) {
					throw new AssertionError(scenario.getFile() + ":" + edit.line + ": " + e.getMessage() + " [" + edit.command + "]");
				}
			}
			check(target, doc, step, where, failures);
		}

		if (scenario.getKnownDefect() == null || !KnownDefects.areTolerated()) {
			if (!failures.isEmpty()) {
				fail(scenario + (scenario.getKnownDefect() != null ? " [" + scenario.getKnownDefect() + "]" : "") + "\n"
						+ String.join("\n", failures));
			}
		}
		else if (failures.isEmpty()) {
			fail(scenario + ": " + scenario.getKnownDefect() + " seems fixed: remove the known-defect row of this scenario");
		}
		else {
			KnownDefects.register(scenario.getKnownDefect(), scenario.toString());
		}
	}

	private static void check(PrettyPrintTarget target, Document doc, Step step, String where, List<String> failures) {
		String editedModel = doc.dump();
		String derived = doc.derived();

		if (!same(target, step.getExpect(), derived)) {
			failures.add(where + "derived pretty-print differs\n" + compare(step.getExpect(), derived));
		}
		try {
			Document reparsed = target.parse(derived);
			if (!reparsed.dump().equals(editedModel)) {
				failures.add(where + "the derived text does not parse back to the edited model\nedited model:\n" + editedModel
						+ "parsed back:\n" + reparsed.dump());
			}
			String again = reparsed.derived();
			if (!same(target, derived, again)) {
				failures.add(where + "the derived text is not stable\n" + compare(derived, again));
			}
		} catch (Exception e) {
			failures.add(where + "the derived text does not parse: " + e.getMessage() + "\n" + show(derived));
		}
		if (step.getExpectNormalized() != null && !same(target, step.getExpectNormalized(), doc.normalized())) {
			failures.add(where + "normalized pretty-print differs\n" + compare(step.getExpectNormalized(), doc.normalized()));
		}
	}

	private static boolean same(PrettyPrintTarget target, String expected, String actual) {
		return target.sameText(withoutFinalSeparators(expected), withoutFinalSeparators(actual));
	}

	private static String withoutFinalSeparators(String text) {
		int end = text.length();
		while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
			end--;
		}
		return text.substring(0, end);
	}

	private static String compare(String expected, String actual) {
		return "--- expected\n" + show(expected) + "\n--- actual\n" + show(actual);
	}

	/** Tabs made visible */
	private static String show(String text) {
		return text.replace("\t", "→");
	}
}
