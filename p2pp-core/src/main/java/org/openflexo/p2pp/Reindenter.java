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

import org.openflexo.p2pp.PrettyPrintContext.Indentation;
import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.p2pp.RawSource.RawSourcePosition;

/**
 * Re-indents rows of a parsed text, from the structure that p2pp knows (P2PP-F-1).
 * <p>
 * The depth of every token is given by the nesting of the contents declared {@link Indentation#Indent} in the printing specification of the
 * nodes, the very nesting the normalized pretty-print indents with. The characters of the text are then of four kinds:
 * <ul>
 * <li>a <b>token</b>: inside the fragment of a static or dynamic contents;</li>
 * <li>a <b>separator</b>: the non-blank text of an identified prelude or postlude ({@code ,} {@code ;});</li>
 * <li>a <b>comment</b>, recognized outside tokens with the {@link LexicalSyntax} of the language;</li>
 * <li><b>layout</b>: whitespace outside tokens and comments.</li>
 * </ul>
 * What is left is text no node describes (code the parser kept no node for).
 * <p>
 * Only the leading whitespace of a row is ever rewritten, so that no token, comment or spacing inside a row changes and the text keeps its
 * meaning:
 * <ul>
 * <li>a row whose first character is a token or a separator gets the depth of that token. When it continues the row before (the
 * specification declares no line break before that token: a statement written over several rows), it is aligned on the text that follows
 * a {@code (} or a {@code [} opened on an earlier row of the statement and still open, or else indented one level more;</li>
 * <li>a row starting a comment gets the deepest depth found between the token before it and the token after it, the empty lists in between
 * included: a comment after an opening brace, before a closing one, or alone in an empty block belongs to the block; inside a bracket left
 * open by a statement, it is aligned as a continuation row;</li>
 * <li>a row inside a block comment that began on an earlier row moves with the row the comment began on, keeping its indentation relative to
 * it (the {@code  * } rows of a documentation comment, commented-out code); if it is less indented than that row, it is left as it is;</li>
 * <li>a row starting inside a token spanning several rows (a string literal), or with text no node describes, is left as it is;</li>
 * <li>a blank row is emptied.</li>
 * </ul>
 * The indentation of a row depends on the whole tree and on no other row but the one its block comment began on, so that re-indenting a
 * portion of the text gives the rows the whole text would have.
 * <p>
 * The tree must describe the current text: the last parse, with no model change since. A client with pending changes prints the text first,
 * parses it again, then re-indents.
 */
public class Reindenter {

	/** A comment of the text: from its start to its end (excluded) */
	private static class Comment {
		private final RawSourcePosition start;
		private final RawSourcePosition end;

		Comment(RawSourcePosition start, RawSourcePosition end) {
			this.start = start;
			this.end = end;
		}

		@Override
		public String toString() {
			return "comment " + start + "-" + end;
		}
	}

	/**
	 * The indentation a row receives
	 */
	public static class RowIndentation {
		private final int line;
		private final int oldLength;
		private final String newIndentation;

		RowIndentation(int line, int oldLength, String newIndentation) {
			this.line = line;
			this.oldLength = oldLength;
			this.newIndentation = newIndentation;
		}

		/** The row, numbered from 1 as {@link RawSourcePosition#getLine()} */
		public int getLine() {
			return line;
		}

		/** Number of characters of the leading whitespace that is replaced */
		public int getOldLength() {
			return oldLength;
		}

		public String getNewIndentation() {
			return newIndentation;
		}

		@Override
		public String toString() {
			return "row " + line + ": " + oldLength + " -> [" + newIndentation.replace("\t", "\\t") + "]";
		}
	}

	/**
	 * A point of the structure, in the order the specification declares it: a token or a separator (with a fragment), or a list (without
	 * fragment: where its children go, at the depth they have)
	 */
	private static class Anchor {
		private final RawSourceFragment fragment;
		private final int depth;
		private final int order;
		private final Object owner;
		// true when the specification declares a line break between the previous anchor and this one: otherwise a row starting with this
		// anchor continues the row before
		private final boolean breakBefore;

		Anchor(RawSourceFragment fragment, int depth, int order, Object owner, boolean breakBefore) {
			this.fragment = fragment;
			this.depth = depth;
			this.order = order;
			this.owner = owner;
			this.breakBefore = breakBefore;
		}

		@Override
		public String toString() {
			return (fragment != null ? fragment + " [" + fragment.getRawText().replace("\n", "\\n") + "]" : "list") + " depth=" + depth
					+ (breakBefore ? "" : " (continuation)") + " of " + owner;
		}

		RawSourcePosition start() {
			return fragment.getStartPosition();
		}

		RawSourcePosition end() {
			return fragment.getEndPosition();
		}
	}

	private final RawSource rawSource;
	private final String unit;
	private final List<Anchor> inOrder = new ArrayList<>();
	private final List<Anchor> positioned = new ArrayList<>();
	private final List<Comment> comments = new ArrayList<>();
	private final LexicalSyntax syntax;

	/**
	 * A re-indenter with the lexical syntax of the root node, indenting with the unit of its pretty-print context
	 */
	public Reindenter(P2PPNode<?, ?> rootNode) {
		this(rootNode, rootNode.makePrettyPrintContext().derive(Indentation.Indent).getResultingIndentation());
	}

	/**
	 * A re-indenter with the lexical syntax of the root node ({@link P2PPNode#getLexicalSyntax()})
	 */
	public Reindenter(P2PPNode<?, ?> rootNode, String unit) {
		this(rootNode, unit, rootNode.getLexicalSyntax());
	}

	public Reindenter(P2PPNode<?, ?> rootNode, String unit, LexicalSyntax syntax) {
		this.rawSource = rootNode.getRawSource();
		this.unit = unit;
		this.syntax = syntax != null ? syntax : LexicalSyntax.C_STYLE;
		visitNode(rootNode, 0);
		for (Anchor a : inOrder) {
			if (a.fragment != null) {
				positioned.add(a);
			}
		}
		Collections.sort(positioned, (a1, a2) -> a1.start().compareTo(a2.start()));
		findComments(this.syntax);
	}

	public RawSource getRawSource() {
		return rawSource;
	}

	public String getUnit() {
		return unit;
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// The anchor map
	// ---------------------------------------------------------------------------------------------------------------------------------

	// a line break is declared since the last anchor recorded (the start of the text is one)
	private boolean pendingBreak = true;

	private void record(RawSourceFragment fragment, int depth, Object owner) {
		if (fragment == null) {
			// a list: where its children go, it breaks nothing
			inOrder.add(new Anchor(null, depth, inOrder.size(), owner, false));
			return;
		}
		inOrder.add(new Anchor(fragment, depth, inOrder.size(), owner, pendingBreak));
		// a token spanning several rows ends on a row of its own
		pendingBreak = fragment.getRawText().contains("\n");
	}

	private void recordSeparator(RawSourceFragment fragment, int depth, Object owner) {
		if (fragment != null && fragment.getLength() > 0 && !fragment.getRawText().trim().isEmpty()) {
			record(fragment, depth, owner);
		}
	}

	/**
	 * A prelude or a postlude of the specification, with the fragment the source has for it: its line breaks before and after its non-blank
	 * part are declared breaks
	 */
	private void recordSeparator(String separator, RawSourceFragment fragment, int depth, Object owner) {
		if (separator == null || separator.isEmpty()) {
			recordSeparator(fragment, depth, owner);
			return;
		}
		String trimmed = separator.trim();
		if (trimmed.isEmpty()) {
			if (separator.indexOf('\n') >= 0) {
				pendingBreak = true;
			}
			return;
		}
		int first = separator.indexOf(trimmed);
		if (separator.substring(0, first).indexOf('\n') >= 0) {
			pendingBreak = true;
		}
		recordSeparator(fragment, depth, owner);
		if (separator.substring(first + trimmed.length()).indexOf('\n') >= 0) {
			pendingBreak = true;
		}
	}

	private void visitNode(P2PPNode<?, ?> node, int depth) {
		visitNode(node, depth, null, null);
	}

	/** A node, with the prelude and postlude its parent writes around it */
	private void visitNode(P2PPNode<?, ?> node, int depth, String prelude, String postlude) {
		if (node == null || node.getASTNode() == null) {
			// not parsed: it has no text yet
			return;
		}
		recordSeparator(prelude, node.getPrelude(), depth, node);
		for (PrettyPrintableContents<?, ?> contents : new ArrayList<>(node.getPPContents())) {
			visitContents(contents, depth);
		}
		recordSeparator(postlude, node.getPostlude(), depth, node);
	}

	private void visitContents(PrettyPrintableContents<?, ?> contents, int depth) {
		if (contents == null) {
			return;
		}
		int d = contents.getIndentation() == Indentation.Indent ? depth + 1 : depth;
		if (contents instanceof StaticContents || contents instanceof DynamicContents) {
			RawSourceFragment fragment = contents.getFragment();
			if (fragment != null && fragment.getLength() > 0) {
				recordSeparator(contents.getPrelude(), contents.getPreludeFragment(), d, contents);
				record(fragment, d, contents);
				recordSeparator(contents.getPostlude(), contents.getPostludeFragment(), d, contents);
			}
		}
		else if (contents instanceof ChildContents) {
			// where the child goes, even when there is none
			record(null, d, contents);
			visitNode(((ChildContents<?, ?, ?, ?>) contents).getParsedChildNode(), d, contents.getPrelude(), contents.getPostlude());
		}
		else if (contents instanceof ChildrenContents) {
			// where the children go, even when there is none (a comment alone in an empty block)
			record(null, d, contents);
			// the parsed children, in the order of the source, are those registered for this contents at initialization
			ChildrenContents<?, ?, ?, ?> list = (ChildrenContents<?, ?, ?, ?>) contents;
			List<P2PPNode<?, ?>> items = new ArrayList<>();
			for (P2PPNode<?, ?> child : new ArrayList<>(contents.getNode().getChildren())) {
				if (child.getRegisteredForContents() == contents) {
					items.add(child);
				}
			}
			for (int i = 0; i < items.size(); i++) {
				String prelude = i == 0 && list.getPreludeForFirstItem() != null ? list.getPreludeForFirstItem() : list.getPrelude();
				String postlude = i == items.size() - 1 && list.getPostludeForLastItem() != null ? list.getPostludeForLastItem()
						: list.getPostlude();
				visitNode(items.get(i), d, prelude, postlude);
			}
		}
		else if (contents instanceof SequentialContents) {
			for (PrettyPrintableContents<?, ?> c : new ArrayList<>(((SequentialContents<?, ?>) contents).getPPContents())) {
				visitContents(c, d);
			}
		}
		else if (contents instanceof ConditionalContents) {
			// only the branch that was parsed is in the text
			ConditionalContents<?, ?> conditional = (ConditionalContents<?, ?>) contents;
			visitContents(conditional.isInitiallyTrue() ? conditional.getThenContents() : conditional.getElseContents(), d);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// The comments
	// ---------------------------------------------------------------------------------------------------------------------------------

	private RawSourcePosition at(int line, int pos) {
		return rawSource.makePositionAfterChar(line, pos);
	}

	private static boolean startsWith(String row, String prefix, int at) {
		return prefix != null && !prefix.isEmpty() && row.startsWith(prefix, at);
	}

	/** Find the comments of the text, outside the tokens (a {@code //} in a string literal is not a comment) */
	private void findComments(LexicalSyntax syntax) {
		RawSourcePosition blockStart = null;
		for (int line = 1; line <= rawSource.size(); line++) {
			String row = rawSource.getRow(line - 1);
			int c = 0;
			while (c < row.length()) {
				if (blockStart != null) {
					int close = row.indexOf(syntax.getBlockCommentEnd(), c);
					if (close < 0) {
						break;
					}
					c = close + syntax.getBlockCommentEnd().length();
					comments.add(new Comment(blockStart, at(line, c)));
					blockStart = null;
				}
				else if (startsWith(row, syntax.getLineComment(), c) && anchorAt(at(line, c)) == null) {
					comments.add(new Comment(at(line, c), at(line, row.length())));
					break;
				}
				else if (startsWith(row, syntax.getBlockCommentStart(), c) && syntax.getBlockCommentEnd() != null && anchorAt(at(line, c)) == null) {
					blockStart = at(line, c);
					c += syntax.getBlockCommentStart().length();
				}
				else {
					c++;
				}
			}
		}
		if (blockStart != null) {
			// not closed: up to the end of the text
			comments.add(new Comment(blockStart, rawSource.getEndPosition()));
		}
	}

	/** The comment containing the position (start included, end excluded), null if none */
	private Comment commentAt(RawSourcePosition p) {
		int low = 0;
		int high = comments.size() - 1;
		int found = -1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (comments.get(mid).start.compareTo(p) <= 0) {
				found = mid;
				low = mid + 1;
			}
			else {
				high = mid - 1;
			}
		}
		return found >= 0 && comments.get(found).end.compareTo(p) > 0 ? comments.get(found) : null;
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// Looking up a position
	// ---------------------------------------------------------------------------------------------------------------------------------

	/** Index in {@link #positioned} of the last anchor starting at or before the position, -1 if none */
	private int lastStartingAtOrBefore(RawSourcePosition p) {
		int low = 0;
		int high = positioned.size() - 1;
		int found = -1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			if (positioned.get(mid).start().compareTo(p) <= 0) {
				found = mid;
				low = mid + 1;
			}
			else {
				high = mid - 1;
			}
		}
		return found;
	}

	/** The anchor whose fragment contains the position (start included, end excluded), null if none */
	private Anchor anchorAt(RawSourcePosition p) {
		int i = lastStartingAtOrBefore(p);
		if (i >= 0 && positioned.get(i).end().compareTo(p) > 0) {
			return positioned.get(i);
		}
		return null;
	}

	/** True when the start of the row lies strictly inside a token that began on an earlier row */
	private boolean startsInsideToken(int line) {
		RawSourcePosition rowStart = rawSource.makePositionAfterChar(line, 0);
		int i = lastStartingAtOrBefore(rowStart);
		// an anchor starting at the row start does not contain it strictly
		while (i >= 0 && positioned.get(i).start().compareTo(rowStart) == 0) {
			i--;
		}
		// tokens do not overlap: the last one starting before is the only candidate
		return i >= 0 && positioned.get(i).end().compareTo(rowStart) > 0;
	}

	/**
	 * Depth of a comment starting at a position that no anchor covers: the deepest of the anchors around it, the lists declared between them included
	 */
	private int triviaDepth(RawSourcePosition p) {
		int i = lastStartingAtOrBefore(p);
		Anchor previous = i >= 0 ? positioned.get(i) : null;
		Anchor next = i + 1 < positioned.size() ? positioned.get(i + 1) : null;
		int depth = -1;
		if (previous != null) {
			depth = Math.max(depth, previous.depth);
		}
		if (next != null) {
			depth = Math.max(depth, next.depth);
		}
		int from = previous != null ? previous.order + 1 : 0;
		int to = next != null ? next.order : inOrder.size();
		for (int k = from; k < to; k++) {
			if (inOrder.get(k).fragment == null) {
				depth = Math.max(depth, inOrder.get(k).depth);
			}
		}
		return Math.max(depth, 0);
	}

	private String indentation(int depth) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append(unit);
		}
		return sb.toString();
	}

	private static int leadingWhitespace(String row) {
		int k = 0;
		while (k < row.length() && Character.isWhitespace(row.charAt(k))) {
			k++;
		}
		return k;
	}

	// ---------------------------------------------------------------------------------------------------------------------------------
	// Re-indenting
	// ---------------------------------------------------------------------------------------------------------------------------------

	/**
	 * What decides the indentation of a row, for diagnostics: the anchor its first character is in, the comment, or nothing
	 */
	public String explain(int line) {
		String row = rawSource.getRow(line - 1);
		int k = leadingWhitespace(row);
		String prefix = "row " + line + " [" + newIndentation(line).replace("\t", "\\t") + "]: ";
		if (startsInsideToken(line)) {
			return prefix + "inside a token spanning several rows, left as it is";
		}
		if (k == row.length()) {
			return prefix + "blank";
		}
		RawSourcePosition p = at(line, k);
		Anchor anchor = anchorAt(p);
		if (anchor != null) {
			return prefix + anchor;
		}
		Comment comment = commentAt(p);
		if (comment != null) {
			if (!comment.start.equals(p)) {
				return prefix + "inside the " + comment + ", moved with row " + comment.start.getLine();
			}
			int i = lastStartingAtOrBefore(p);
			return prefix + comment + ", depth " + triviaDepth(p) + " between " + (i >= 0 ? positioned.get(i) : "start") + " and "
					+ (i + 1 < positioned.size() ? positioned.get(i + 1) : "end");
		}
		return prefix + "text that no node describes, left as it is";
	}

	/** True when the first character of the row is in a token or a separator that does not continue the row before */
	private boolean startsAStatement(int line) {
		String row = rawSource.getRow(line - 1);
		int k = leadingWhitespace(row);
		if (k == row.length() || startsInsideToken(line)) {
			return false;
		}
		Anchor anchor = anchorAt(at(line, k));
		return anchor != null && anchor.breakBefore;
	}

	/**
	 * For a row continuing a statement: when a delimiter of alignment ({@link LexicalSyntax#getAlignmentDelimiters()}, {@code (} and
	 * {@code [} in C style) opened on an earlier row of the statement is still open, and text follows it on its row, the indentation that puts the row under that text (the indentation of the row of the bracket, then a space for
	 * each character up to the text, a tab kept as a tab, so that the alignment holds whatever the width of a tab). Null otherwise: no
	 * bracket open, or one ending its row (the row is then indented one level more). Comments and string literals are skipped.
	 */
	private String alignedOnOpenBracket(int line) {
		if (line <= 1) {
			return null;
		}
		int start = line - 1;
		while (start > 1 && !startsAStatement(start)) {
			start--;
		}
		List<String[]> pairs = syntax.getAlignmentDelimiters();
		if (pairs.isEmpty()) {
			return null;
		}
		// the delimiters left open: {row, column of the text after the opening, index of the pair}
		List<int[]> open = new ArrayList<>();
		for (int l = start; l < line; l++) {
			String row = rawSource.getRow(l - 1);
			char quote = 0;
			for (int c = 0; c < row.length(); c++) {
				char ch = row.charAt(c);
				if (quote != 0) {
					if (syntax.getEscape() != null && ch == syntax.getEscape()) {
						c++;
					}
					else if (ch == quote) {
						quote = 0;
					}
					continue;
				}
				Comment comment = commentAt(at(l, c));
				if (comment != null) {
					if (comment.end.getLine() > l) {
						// a block comment going on: resume after it
						if (comment.end.getLine() >= line) {
							break;
						}
						l = comment.end.getLine() - 1;
						break;
					}
					c = comment.end.getPos() - 1;
					continue;
				}
				if (syntax.getStringDelimiters().indexOf(ch) >= 0) {
					quote = ch;
					continue;
				}
				for (int k = 0; k < pairs.size(); k++) {
					String[] pair = pairs.get(k);
					if (row.startsWith(pair[0], c)) {
						open.add(new int[] { l, c + pair[0].length(), k });
						c += pair[0].length() - 1;
						break;
					}
					if (row.startsWith(pair[1], c)) {
						// closes the last opening of its pair, and what was opened after it
						for (int o = open.size() - 1; o >= 0; o--) {
							if (open.get(o)[2] == k) {
								open.subList(o, open.size()).clear();
								break;
							}
						}
						c += pair[1].length() - 1;
						break;
					}
				}
			}
		}
		if (open.isEmpty()) {
			return null;
		}
		int[] bracket = open.get(open.size() - 1);
		String row = rawSource.getRow(bracket[0] - 1);
		int text = bracket[1];
		while (text < row.length() && Character.isWhitespace(row.charAt(text))) {
			text++;
		}
		if (text >= row.length() || commentAt(at(bracket[0], text)) != null) {
			// the bracket ends its row
			return null;
		}
		StringBuilder sb = new StringBuilder(newIndentation(bracket[0]));
		for (int c = leadingWhitespace(row); c < text; c++) {
			sb.append(row.charAt(c) == '\t' ? '\t' : ' ');
		}
		return sb.toString();
	}

	/** The indentation a row must have; for a row left as it is, its own */
	private String newIndentation(int line) {
		String row = rawSource.getRow(line - 1);
		int k = leadingWhitespace(row);
		String oldIndentation = row.substring(0, k);
		if (startsInsideToken(line)) {
			return oldIndentation;
		}
		if (k == row.length()) {
			return "";
		}
		RawSourcePosition p = at(line, k);
		Anchor anchor = anchorAt(p);
		if (anchor != null) {
			if (anchor.breakBefore) {
				return indentation(anchor.depth);
			}
			// a row that continues the one before (a statement written over several rows): aligned on the text after a bracket left open,
			// else one level more
			String aligned = alignedOnOpenBracket(line);
			return aligned != null ? aligned : indentation(anchor.depth + 1);
		}
		Comment comment = commentAt(p);
		if (comment == null) {
			// text that no node describes: its depth is unknown
			return oldIndentation;
		}
		if (comment.start.equals(p)) {
			// a comment inside the brackets of a statement is aligned like the arguments around it
			String aligned = alignedOnOpenBracket(line);
			return aligned != null ? aligned : indentation(triviaDepth(p));
		}
		// inside a block comment begun on an earlier row: it moves with that row
		int headLine = comment.start.getLine();
		String headRow = rawSource.getRow(headLine - 1);
		String headOld = headRow.substring(0, leadingWhitespace(headRow));
		if (!oldIndentation.startsWith(headOld)) {
			return oldIndentation;
		}
		return newIndentation(headLine) + oldIndentation.substring(headOld.length());
	}

	/**
	 * The rows of {@code [fromLine, toLine]} (numbered from 1, both included) whose indentation changes, with their new indentation
	 */
	public List<RowIndentation> computeIndentations(int fromLine, int toLine) {
		List<RowIndentation> returned = new ArrayList<>();
		for (int line = Math.max(1, fromLine); line <= Math.min(rawSource.size(), toLine); line++) {
			String row = rawSource.getRow(line - 1);
			int k = leadingWhitespace(row);
			String newIndentation = newIndentation(line);
			if (!newIndentation.equals(row.substring(0, k))) {
				returned.add(new RowIndentation(line, k, newIndentation));
			}
		}
		return returned;
	}

	/**
	 * The edits of {@link #computeIndentations(int, int)} on the whole source, as a {@link DerivedRawSource}
	 */
	public DerivedRawSource reindentAsDerivedRawSource(int fromLine, int toLine) {
		DerivedRawSource returned = new DerivedRawSource(rawSource.makeFragment(rawSource.getStartPosition(), rawSource.getEndPosition()));
		for (RowIndentation r : computeIndentations(fromLine, toLine)) {
			RawSourcePosition start = rawSource.makePositionAfterChar(r.getLine(), 0);
			if (r.getOldLength() == 0) {
				returned.insert(start, r.getNewIndentation());
			}
			else {
				returned.replace(rawSource.makeFragment(start, rawSource.makePositionAfterChar(r.getLine(), r.getOldLength())),
						r.getNewIndentation());
			}
		}
		return returned;
	}

	/**
	 * The whole text, with the rows of {@code [fromLine, toLine]} (numbered from 1, both included) re-indented
	 */
	public String reindent(int fromLine, int toLine) {
		return reindentAsDerivedRawSource(fromLine, toLine).getStringRepresentation();
	}

	/**
	 * The whole text re-indented
	 */
	public String reindent() {
		return reindent(1, rawSource.size());
	}
}
