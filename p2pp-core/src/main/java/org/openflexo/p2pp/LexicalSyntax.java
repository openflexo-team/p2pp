/**
 * 
 * Copyright (c) 2026, Openflexo
 * 
 * This file is part of p2pp, a component of the software infrastructure 
 * developed at Openflexo.
 * 
 * 
 * Openflexo is dual-licensed under the European Union Public License (EUPL, either 
 * version 1.1 of the License, or any later version ), which is available at 
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 * and the GNU General Public License (GPL, either version 3 of the License, or any 
 * later version), which is available at http://www.gnu.org/licenses/gpl.html .
 * 
 * You can redistribute it and/or modify under the terms of either of these licenses
 * 
 * If you choose to redistribute it and/or modify under the terms of the GNU GPL, you
 * must include the following additional permission.
 *
 *          Additional permission under GNU GPL version 3 section 7
 *
 *          If you modify this Program, or any covered work, by linking or 
 *          combining it with software containing parts covered by the terms 
 *          of EPL 1.0, the licensors of this Program grant you additional permission
 *          to convey the resulting work. * 
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. 
 *
 * See http://www.openflexo.org/license.html for details.
 * 
 * 
 * Please contact Openflexo (openflexo-contacts@openflexo.org)
 * or visit www.openflexo.org if you need additional information.
 * 
 */

package org.openflexo.p2pp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the re-indentation of a text ({@link Reindenter}) needs to know of the lexical syntax of a language, beyond its printing
 * specification: how comments are written, which pairs of delimiters a row continuing a statement is aligned in, and how string literals
 * are delimited (a delimiter inside a string literal is not one).
 * <p>
 * A language gives its own by overriding {@link P2PPNode#getLexicalSyntax()} on its root node; {@link #C_STYLE} is the default.
 * Immutable: the {@code with...} methods return a new syntax.
 */
public class LexicalSyntax {

	/**
	 * {@code //} and {@code /* ... *&#47;} comments, alignment in {@code ( )} and {@code [ ]}, string literals between {@code "} or
	 * {@code '} with {@code \} as escape: FML, B and Mini. Braces are not alignment delimiters: a block brace is followed by a line
	 * break, and a brace inside a statement is rare.
	 */
	public static final LexicalSyntax C_STYLE = new LexicalSyntax("//", "/*", "*/", Collections.emptyList(), "\"'", '\\')
			.withAlignmentDelimiters("(", ")").withAlignmentDelimiters("[", "]");

	private final String lineComment;
	private final String blockCommentStart;
	private final String blockCommentEnd;
	private final List<String[]> alignmentDelimiters;
	private final String stringDelimiters;
	private final Character escape;

	/**
	 * @param lineComment
	 *            the prefix of a comment ending with its row, or null
	 * @param blockCommentStart
	 *            the start of a comment that may span several rows, or null
	 * @param blockCommentEnd
	 *            its end, or null
	 * @param alignmentDelimiters
	 *            pairs {open, close}: a row continuing a statement inside a pair left open is aligned on the text after its opening
	 * @param stringDelimiters
	 *            each character delimits a string literal, up to the same character on the same row (empty: none)
	 * @param escape
	 *            the character escaping the next one inside a string literal, or null
	 */
	public LexicalSyntax(String lineComment, String blockCommentStart, String blockCommentEnd, List<String[]> alignmentDelimiters,
			String stringDelimiters, Character escape) {
		this.lineComment = lineComment;
		this.blockCommentStart = blockCommentStart;
		this.blockCommentEnd = blockCommentEnd;
		List<String[]> pairs = new ArrayList<>();
		for (String[] pair : alignmentDelimiters) {
			if (pair.length != 2 || pair[0] == null || pair[0].isEmpty() || pair[1] == null || pair[1].isEmpty()) {
				throw new IllegalArgumentException("A pair of alignment delimiters is {open, close}, both non-empty");
			}
			pairs.add(new String[] { pair[0], pair[1] });
		}
		this.alignmentDelimiters = Collections.unmodifiableList(pairs);
		this.stringDelimiters = stringDelimiters != null ? stringDelimiters : "";
		this.escape = escape;
	}

	/** This syntax, with one more pair of alignment delimiters ({@code withAlignmentDelimiters("{", "}")}) */
	public LexicalSyntax withAlignmentDelimiters(String open, String close) {
		List<String[]> pairs = new ArrayList<>(alignmentDelimiters);
		pairs.add(new String[] { open, close });
		return new LexicalSyntax(lineComment, blockCommentStart, blockCommentEnd, pairs, stringDelimiters, escape);
	}

	/** This syntax, without any alignment: a row continuing a statement is always indented one level more */
	public LexicalSyntax withoutAlignment() {
		return new LexicalSyntax(lineComment, blockCommentStart, blockCommentEnd, Collections.emptyList(), stringDelimiters, escape);
	}

	/** This syntax, with other comments (any may be null) */
	public LexicalSyntax withComments(String lineComment, String blockCommentStart, String blockCommentEnd) {
		return new LexicalSyntax(lineComment, blockCommentStart, blockCommentEnd, alignmentDelimiters, stringDelimiters, escape);
	}

	/** This syntax, with other string literals */
	public LexicalSyntax withStrings(String stringDelimiters, Character escape) {
		return new LexicalSyntax(lineComment, blockCommentStart, blockCommentEnd, alignmentDelimiters, stringDelimiters, escape);
	}

	public String getLineComment() {
		return lineComment;
	}

	public String getBlockCommentStart() {
		return blockCommentStart;
	}

	public String getBlockCommentEnd() {
		return blockCommentEnd;
	}

	/** Pairs {open, close} */
	public List<String[]> getAlignmentDelimiters() {
		return alignmentDelimiters;
	}

	public String getStringDelimiters() {
		return stringDelimiters;
	}

	public Character getEscape() {
		return escape;
	}
}
