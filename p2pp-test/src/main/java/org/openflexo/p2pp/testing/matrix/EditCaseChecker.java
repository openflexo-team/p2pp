package org.openflexo.p2pp.testing.matrix;

import java.util.ArrayList;
import java.util.List;

import org.openflexo.p2pp.testing.PrettyPrintTarget;
import org.openflexo.p2pp.testing.PrettyPrintTarget.Document;

/**
 * The invariants checked on every {@link EditCase}. None needs an expected text: they hold whatever the edit.
 * <ul>
 * <li><b>parse-back</b>: the derived text parses back to the edited model;</li>
 * <li><b>stable</b>: that text, parsed and pretty-printed again with no edit, does not change;</li>
 * <li><b>layout</b>: for a source that is itself laid out canonically ({@link PrettyPrintTarget#layoutViolation}), the derived text still
 * is: a new or a remaining row is indented like its siblings, and no separator is left behind;</li>
 * <li><b>comments</b>: no comment of the source ({@link PrettyPrintTarget#comments}) is lost, except by a removal, which may take the
 * comments of what it removes;</li>
 * <li><b>undo</b>: the edit then its inverse, on a fresh document, give the pretty-print of the untouched source;</li>
 * <li><b>undo-after-print</b>: same on the document that was already pretty-printed after the edit (a pretty-print must not
 * change what the next one does).</li>
 * </ul>
 */
public final class EditCaseChecker {

	private EditCaseChecker() {
	}

	/** The failures of a case, empty when it passes; an edit that cannot be applied is an error of the generator, thrown */
	public static List<String> check(PrettyPrintTarget target, EditCase c) throws Exception {
		List<String> failures = new ArrayList<>();
		String untouched = target.parse(c.source).derived();

		Document doc = target.parse(c.source);
		apply(doc, c.edits, c);
		String edited = doc.dump();
		String derived;
		try {
			derived = doc.derived();
		} catch (RuntimeException e) {
			failures.add("derived pretty-print throws " + e);
			return failures;
		}

		try {
			Document reparsed = target.parse(derived);
			if (!reparsed.dump().equals(edited)) {
				failures.add("parse-back: the text means another model\n" + derived);
			}
			else if (!reparsed.derived().equals(derived)) {
				failures.add("stable: pretty-printed again, the text changes\n" + derived);
			}
		} catch (Exception e) {
			failures.add("parse-back: the text does not parse (" + e.getMessage() + ")\n" + derived);
		}

		if (target.layoutViolation(untouched) == null) {
			String violation = target.layoutViolation(derived);
			if (violation != null) {
				failures.add("layout: " + violation + "\n" + derived);
			}
		}

		if (!c.kind.startsWith("remove")) {
			for (String comment : target.comments(c.source)) {
				if (!derived.contains(comment)) {
					failures.add("comments: '" + comment + "' is lost\n" + derived);
					break;
				}
			}
		}

		if (c.inverse != null) {
			Document fresh = target.parse(c.source);
			apply(fresh, c.edits, c);
			apply(fresh, c.inverse, c);
			String undone = fresh.derived();
			if (!undone.equals(untouched)) {
				failures.add("undo: the edit and its inverse do not give the source back\n" + undone);
			}
			apply(doc, c.inverse, c);
			String undoneAfterPrint = doc.derived();
			if (!undoneAfterPrint.equals(untouched)) {
				failures.add("undo-after-print: pretty-printing in between changes the result\n" + undoneAfterPrint);
			}
		}
		return failures;
	}

	private static void apply(Document doc, List<String> commands, EditCase c) {
		for (String command : commands) {
			try {
				doc.apply(command);
			} catch (RuntimeException e) {
				throw new AssertionError("Generator error in " + c + ": " + e.getMessage() + " [" + command + "]");
			}
		}
	}
}
