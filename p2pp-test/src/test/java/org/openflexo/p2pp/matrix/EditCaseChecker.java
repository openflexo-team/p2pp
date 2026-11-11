package org.openflexo.p2pp.matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openflexo.p2pp.mini.MiniDocument;
import org.openflexo.p2pp.mini.MiniParseException;
import org.openflexo.p2pp.scenario.MiniEdits;

/**
 * The invariants checked on every {@link EditCase}. None needs an expected text: they hold whatever the edit.
 * <ul>
 * <li><b>parse-back</b>: the derived text parses back to the edited model;</li>
 * <li><b>stable</b>: that text, parsed and pretty-printed again with no edit, does not change;</li>
 * <li><b>layout</b>: for a source that is itself laid out canonically (tabs, one per level of braces; single spaces; no trailing space),
 * the derived text still is: a new or a remaining row is indented like its siblings, and no separator is left behind;</li>
 * <li><b>comments</b>: no comment of the source is lost (except by a removal, which may take the comments of what it removes);</li>
 * <li><b>undo</b>: the edit then its inverse, on a fresh document, give the pretty-print of the untouched source;</li>
 * <li><b>undo-after-print</b>: same on the document that was already pretty-printed after the edit (a pretty-print must not
 * change what the next one does).</li>
 * </ul>
 */
public final class EditCaseChecker {

	private static final Pattern COMMENT = Pattern.compile("//[^\n]*|/\\*.*?\\*/", Pattern.DOTALL);

	private EditCaseChecker() {
	}

	/** The failures of a case, empty when it passes; an edit that cannot be applied is an error of the generator, thrown */
	public static List<String> check(EditCase c) throws MiniParseException {
		List<String> failures = new ArrayList<>();
		String untouched = MiniDocument.parse(c.source).derived();

		MiniDocument doc = MiniDocument.parse(c.source);
		apply(doc, c.edits, c);
		String edited = doc.getUnit().dump();
		String derived;
		try {
			derived = doc.derived();
		} catch (RuntimeException e) {
			failures.add("derived pretty-print throws " + e);
			return failures;
		}

		try {
			MiniDocument reparsed = MiniDocument.parse(derived);
			if (!reparsed.getUnit().dump().equals(edited)) {
				failures.add("parse-back: the text means another model\n" + derived);
			}
			else if (!reparsed.derived().equals(derived)) {
				failures.add("stable: pretty-printed again, the text changes\n" + derived);
			}
		} catch (MiniParseException e) {
			failures.add("parse-back: the text does not parse (" + e.getMessage() + ")\n" + derived);
		}

		if (layoutViolation(untouched) == null) {
			String violation = layoutViolation(derived);
			if (violation != null) {
				failures.add("layout: " + violation + "\n" + derived);
			}
		}

		if (!c.kind.startsWith("remove")) {
			Matcher m = COMMENT.matcher(c.source);
			while (m.find()) {
				if (!derived.contains(m.group())) {
					failures.add("comments: '" + m.group() + "' is lost\n" + derived);
					break;
				}
			}
		}

		if (c.inverse != null) {
			MiniDocument fresh = MiniDocument.parse(c.source);
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

	private static final Pattern LINE_COMMENT = Pattern.compile("[ \t]*//[^\n]*");
	private static final Pattern BLOCK_COMMENT = Pattern.compile("[ \t]*/\\*.*?\\*/", Pattern.DOTALL);

	/**
	 * The first departure of a text from the canonical layout of Mini, or null: comments aside, one tab per level of braces in front of
	 * a row (one less for a row starting with a closing brace), single spaces, no trailing space or tab.
	 */
	static String layoutViolation(String text) {
		String code = BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(text).replaceAll("")).replaceAll("");
		int depth = 0;
		int rowNumber = 0;
		for (String row : code.split("\n", -1)) {
			rowNumber++;
			if (row.trim().isEmpty()) {
				continue;
			}
			int expected = row.trim().startsWith("}") ? depth - 1 : depth;
			int tabs = 0;
			while (tabs < row.length() && row.charAt(tabs) == '\t') {
				tabs++;
			}
			String content = row.substring(tabs);
			if (tabs != expected || Character.isWhitespace(content.charAt(0))) {
				return "row " + rowNumber + " has " + (Character.isWhitespace(content.charAt(0)) ? "other indentation" : tabs + " tabs, " + expected
						+ " expected") + ": [" + row.replace("\t", "→") + "]";
			}
			if (content.contains("  ") || content.endsWith(" ") || content.endsWith("\t")) {
				return "row " + rowNumber + " has a double or a trailing space: [" + row.replace("\t", "→") + "]";
			}
			for (char ch : content.toCharArray()) {
				if (ch == '{') {
					depth++;
				}
				else if (ch == '}') {
					depth--;
				}
			}
		}
		return null;
	}

	private static void apply(MiniDocument doc, List<String> commands, EditCase c) {
		for (String command : commands) {
			try {
				MiniEdits.apply(doc.getUnit(), command);
			} catch (RuntimeException e) {
				throw new AssertionError("Generator error in " + c + ": " + e.getMessage() + " [" + command + "]");
			}
		}
	}
}
