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
 * <li><b>trivia</b>: anything else that is not whitespace, i.e. comments;</li>
 * <li><b>layout</b>: whitespace outside the tokens.</li>
 * </ul>
 * Only the leading whitespace of a row is ever rewritten, so that no token, comment or spacing inside a row changes and the text keeps its
 * meaning. A row whose first character is a token or a separator gets the depth of that token. A row starting with trivia (a comment) gets
 * the deepest depth found between the token before it and the token after it, the empty lists in between included: a comment after an
 * opening brace or before a closing one belongs to the block. The rows that follow it while no token starts a row keep their indentation
 * relative to it (the {@code  * } rows of a block comment), until a blank row. A row starting inside a token that spans several rows (a string literal, a
 * wrapped expression) is left untouched, its leading whitespace may be meaningful. A blank row is emptied.
 * <p>
 * The depths come from the whole tree, so that re-indenting a portion of the text gives the rows the whole text would have.
 * <p>
 * The tree must describe the current text: the last parse, with no model change since. A client with pending changes prints the text first,
 * parses it again, then re-indents.
 */
public class Reindenter {

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

		Anchor(RawSourceFragment fragment, int depth, int order) {
			this.fragment = fragment;
			this.depth = depth;
			this.order = order;
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

	/**
	 * A re-indenter indenting with the unit of the pretty-print context of the root node
	 */
	public Reindenter(P2PPNode<?, ?> rootNode) {
		this(rootNode, rootNode.makePrettyPrintContext().derive(Indentation.Indent).getResultingIndentation());
	}

	public Reindenter(P2PPNode<?, ?> rootNode, String unit) {
		this.rawSource = rootNode.getRawSource();
		this.unit = unit;
		visitNode(rootNode, 0);
		for (Anchor a : inOrder) {
			if (a.fragment != null) {
				positioned.add(a);
			}
		}
		Collections.sort(positioned, (a1, a2) -> a1.start().compareTo(a2.start()));
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

	private void record(RawSourceFragment fragment, int depth) {
		inOrder.add(new Anchor(fragment, depth, inOrder.size()));
	}

	private void recordSeparator(RawSourceFragment fragment, int depth) {
		if (fragment != null && fragment.getLength() > 0 && !fragment.getRawText().trim().isEmpty()) {
			record(fragment, depth);
		}
	}

	private void visitNode(P2PPNode<?, ?> node, int depth) {
		if (node == null || node.getASTNode() == null) {
			// not parsed: it has no text yet
			return;
		}
		recordSeparator(node.getPrelude(), depth);
		for (PrettyPrintableContents<?, ?> contents : new ArrayList<>(node.getPPContents())) {
			visitContents(contents, depth);
		}
		recordSeparator(node.getPostlude(), depth);
	}

	private void visitContents(PrettyPrintableContents<?, ?> contents, int depth) {
		if (contents == null) {
			return;
		}
		int d = contents.getIndentation() == Indentation.Indent ? depth + 1 : depth;
		if (contents instanceof StaticContents || contents instanceof DynamicContents) {
			RawSourceFragment fragment = contents.getFragment();
			if (fragment != null && fragment.getLength() > 0) {
				recordSeparator(contents.getPreludeFragment(), d);
				record(fragment, d);
				recordSeparator(contents.getPostludeFragment(), d);
			}
		}
		else if (contents instanceof ChildContents) {
			// where the child goes, even when there is none
			record(null, d);
			visitNode(((ChildContents<?, ?, ?, ?>) contents).getParsedChildNode(), d);
		}
		else if (contents instanceof ChildrenContents) {
			// where the children go, even when there is none (a comment alone in an empty block)
			record(null, d);
			// the parsed children, in the order of the source, are those registered for this contents at initialization
			for (P2PPNode<?, ?> child : new ArrayList<>(contents.getNode().getChildren())) {
				if (child.getRegisteredForContents() == contents) {
					visitNode(child, d);
				}
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
	 * Depth of trivia at a position that no anchor covers: the deepest of the anchors around it, the lists declared between them included
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

	/** True when the row starts with trivia: not blank, not inside a token, its first character in no anchor */
	private boolean isTriviaRow(int line) {
		String row = rawSource.getRow(line - 1);
		int k = leadingWhitespace(row);
		return k < row.length() && !startsInsideToken(line) && anchorAt(rawSource.makePositionAfterChar(line, k)) == null;
	}

	/**
	 * The rows of {@code [fromLine, toLine]} (numbered from 1, both included) whose indentation changes, with their new indentation
	 */
	public List<RowIndentation> computeIndentations(int fromLine, int toLine) {
		List<RowIndentation> returned = new ArrayList<>();
		int first = Math.max(1, fromLine);
		int last = Math.min(rawSource.size(), toLine);
		// a portion starting inside a run of comment rows starts at the head of that run, so that its rows get what the whole text gives them
		int start = first;
		while (start > 1 && isTriviaRow(start - 1)) {
			start--;
		}
		// the comment that the rows starting with trivia follow, as long as no token starts a row: its old and new indentation
		String runOldIndentation = null;
		String runNewIndentation = null;
		for (int line = start; line <= last; line++) {
			String row = rawSource.getRow(line - 1);
			int k = leadingWhitespace(row);
			String oldIndentation = row.substring(0, k);
			if (startsInsideToken(line)) {
				continue;
			}
			String newIndentation;
			if (k == row.length()) {
				// blank row: it ends a run of comment rows (two comments apart are not moved together)
				newIndentation = "";
				runOldIndentation = null;
				runNewIndentation = null;
			}
			else {
				Anchor anchor = anchorAt(rawSource.makePositionAfterChar(line, k));
				if (anchor != null) {
					newIndentation = indentation(anchor.depth);
					runOldIndentation = null;
					runNewIndentation = null;
				}
				else if (runOldIndentation != null && oldIndentation.startsWith(runOldIndentation)) {
					newIndentation = runNewIndentation + oldIndentation.substring(runOldIndentation.length());
				}
				else {
					newIndentation = indentation(triviaDepth(rawSource.makePositionAfterChar(line, k)));
					runOldIndentation = oldIndentation;
					runNewIndentation = newIndentation;
				}
			}
			if (line >= first && !newIndentation.equals(oldIndentation)) {
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
