package org.openflexo.p2pp.mini;

import java.util.regex.Pattern;

/**
 * The canonical layout of Mini, which the edit matrix checks the derived text against.
 */
final class MiniLayout {

	private static final Pattern LINE_COMMENT = Pattern.compile("[ \t]*//[^\n]*");
	private static final Pattern BLOCK_COMMENT = Pattern.compile("[ \t]*/\\*.*?\\*/", Pattern.DOTALL);

	private MiniLayout() {
	}

	/**
	 * The first departure of a text from the canonical layout of Mini, or null: comments aside, one tab per level of braces in front of
	 * a row (one less for a row starting with a closing brace), single spaces, no trailing space or tab.
	 */
	static String violation(String text) {
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
				return "row " + rowNumber + " has "
						+ (Character.isWhitespace(content.charAt(0)) ? "other indentation" : tabs + " tabs, " + expected + " expected") + ": ["
						+ row.replace("\t", "→") + "]";
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
}
