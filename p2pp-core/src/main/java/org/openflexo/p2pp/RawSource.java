/**
 * 
 * Copyright (c) 2019, Openflexo
 * 
 * This file is part of FML-parser, a component of the software infrastructure 
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.openflexo.toolbox.StringUtils;

/**
 * Represent raw source at it has been serialized and parsed.<br>
 * 
 * Suppose the raw source text is this:
 * 
 * <pre>
 * ABCD
 * EFG
 * HI
 * JKL
 * </pre>
 * 
 * The text will be handled as:
 * 
 * <pre>
 *    pos 0 1 2 3 4 < represents RawSourcePosition
 *   char  1 2 3 4  < represent characters indexes
 * Line 1 |A|B|C|D|
 * Line 2 |E|F|G|
 * Line 3 |H|I|
 * Line 4 |J|K|L|
 * </pre>
 * 
 * Thus, first character of that source is (1:1) (first line, first character), while first position is (1:0) (first line, before first
 * char)
 * 
 * @author sylvain
 * 
 */
public class RawSource {

	private static final Logger logger = Logger.getLogger(RawSource.class.getPackage().getName());

	private List<String> rows;
	private final RawSourcePosition startPosition;
	private final RawSourcePosition endPosition;
	private StringBuffer rawText;

	/**
	 * Encodes a position in the RawSource, using line and position in line<br>
	 * Note that line numbering starts at 1 and position numbering also starts at 1<br>
	 * This means that the first position in the RawSource is (1:1)
	 * 
	 * @author sylvain
	 *
	 */
	public class RawSourcePosition implements Comparable<RawSourcePosition> {
		private final int line;
		private final int pos;

		public RawSourcePosition(int line, int pos) {
			super();
			this.line = line;
			this.pos = pos;
		}

		public boolean canDecrement() {
			return line > 1 || (line == 1 && pos > 0);
		}

		public RawSourcePosition decrement() {
			if (!canDecrement()) {
				// Cannot proceed
				throw new ArrayIndexOutOfBoundsException("Cannot decrement from position " + this);
			}
			int newPos = pos - 1;
			int newLine = line;
			if (newPos == -1) {
				newLine = line - 1;
				newPos = rows.get(newLine - 1).length();
			}
			return new RawSourcePosition(newLine, newPos);
		}

		public RawSourcePosition decrement(int n) {
			RawSourcePosition returned = this;
			for (int i = 0; i < n; i++) {
				returned = returned.decrement();
			}
			return returned;
		}

		public boolean canIncrement() {
			// System.out.println("pos=" + pos);
			// System.out.println("rows.get(rows.size() - 1).length()=" + (rows.get(rows.size() - 1).length()));
			return line <= rows.size() - 1 || pos < rows.get(rows.size() - 1).length();
		}

		public RawSourcePosition increment() {
			if (!canIncrement()) {
				// Cannot proceed
				/*System.out.println("Cannot increment");
				System.out.println("line=" + line);
				System.out.println("rows.size()=" + rows.size());
				System.out.println("pos=" + pos);
				System.out.println("rows.get(rows.size() - 1).length()=" + rows.get(rows.size() - 1).length());*/
				throw new ArrayIndexOutOfBoundsException("Cannot increment from position " + this);
			}
			int newPos = pos + 1;
			int newLine = line;
			if (newPos > rows.get(newLine - 1).length()) {
				newLine++;
				newPos = 0;
			}
			return new RawSourcePosition(newLine, newPos);
		}

		public RawSourcePosition increment(int n) {
			RawSourcePosition returned = this;
			for (int i = 0; i < n; i++) {
				returned = returned.increment();
			}
			return returned;
		}

		@Override
		public int compareTo(RawSourcePosition o) {
			if (o.line < line) {
				return 1;
			}
			else if (o.line > line) {
				return -1;
			}
			else {
				if (o.pos < pos) {
					return 1;
				}
				else if (o.pos > pos) {
					return -1;
				}
				else {
					return 0;
				}
			}
		}

		public boolean isBefore(RawSourcePosition other) {
			return compareTo(other) < 0;
		}

		public boolean isAfter(RawSourcePosition other) {
			return compareTo(other) > 0;
		}

		public boolean isBeforeOrEquals(RawSourcePosition other) {
			return compareTo(other) <= 0;
		}

		public boolean isAfterOrEquals(RawSourcePosition other) {
			return compareTo(other) >= 0;
		}

		public int getLengthTo(RawSourcePosition other) {
			// System.out.println("getLengthTo from " + this + " to " + other);
			int returned = 0;
			RawSourcePosition current = other;
			while (compareTo(current) != 0) {
				if (isBefore(current)) {
					current = current.decrement();
					returned++;
				}
				if (isAfter(current)) {
					current = current.decrement();
					returned--;
				}
				// System.out.println("current=" + current);
			}
			// System.out.println("return " + returned);
			return returned;
		}

		@Override
		public String toString() {
			return "(" + line + ":" + pos + ")";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + line;
			result = prime * result + pos;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RawSourcePosition other = (RawSourcePosition) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (line != other.line)
				return false;
			if (pos != other.pos)
				return false;
			return true;
		}

		public RawSource getOuterType() {
			return RawSource.this;
		}

		public int getLine() {
			return line;
		}

		public int getPos() {
			return pos;
		}

		public int getOffset() {
			int returned = 0;
			for (int i = 0; i < getLine() - 1; i++) {
				returned += (rows.get(i).length() + 1);
			}
			return returned + getPos();
		}

		public Character getCharAfter() {
			if (getLine() <= rows.size()) {
				String row = rows.get(getLine() - 1);
				if (getPos() < row.length()) {
					return row.charAt(getPos());
				}
			}
			return null;
		}

		public boolean isInside(RawSourceFragment fragment) {
			if (fragment == null || fragment.getStartPosition() == null || fragment.getEndPosition() == null) {
				return false;
			}
			return isAfterOrEquals(fragment.getStartPosition()) && isBeforeOrEquals(fragment.getEndPosition());
		}
	}

	/**
	 * Encodes a fragment of {@link RawSource}, identified by start position, inclusive and end position, exclusive<br>
	 * 
	 * @author sylvain
	 *
	 */
	public class RawSourceFragment {

		private final RawSourcePosition start;
		private final RawSourcePosition end;

		/**
		 * Build new fragment, identified by start position and end position
		 * 
		 * @param start
		 *            start position (index of first character to take)
		 * @param end
		 *            end position (index of first character to exclude)
		 */
		public RawSourceFragment(RawSourcePosition start, RawSourcePosition end) {
			super();
			if (start == null) {
				logger.warning("Create a fragment with null start position");
			}
			if (end == null) {
				logger.warning("Create a fragment with null end position");
			}
			this.start = start;
			this.end = end;
		}

		@Override
		public String toString() {
			return start.toString() + "-" + end.toString();
		}

		public RawSourcePosition getStartPosition() {
			return start;
		}

		public RawSourcePosition getEndPosition() {
			return end;
		}

		public String getRawText() {

			int startLine = start.line;
			int startPos = start.pos;
			int endLine = end.line;
			int endPos = end.pos;
			if (startLine > -1 && startPos > -1 && endLine > -1 && endPos > -1 && startLine <= endLine) {
				if (startLine == endLine) {
					// All in one line
					// System.out.println("Computing rawText for " + this + " from " + startPos + " to " + endPos);
					// System.out.println("row: " + (startLine - 1) + "[" + rows.get(startLine - 1) + "]");
					if (endPos <= rows.get(startLine - 1).length()) {
						return rows.get(startLine - 1).substring(startPos, endPos);
					}
					else {
						logger.warning("Cannot append substring(" + startPos + "," + endPos + ") of [" + rows.get(startLine - 1) + "]");
					}
				}
				StringBuffer sb = new StringBuffer();
				for (int i = startLine; i <= endLine; i++) {
					if (i == startLine) {
						// First line
						if (startPos >= 0 && startPos <= rows.get(i - 1).length()) {
							sb.append(rows.get(i - 1).substring(startPos) + "\n");
						}
						else {
							logger.warning("Cannot append substring(" + startPos + ") of [" + rows.get(i - 1) + "]");
						}
					}
					else if (i == endLine) {
						// Last line
						// try {
						if (endPos > rows.get(i - 1).length()) {
							sb.append(rows.get(i - 1).substring(0, endPos) + "\n");
						}
						else {
							sb.append(rows.get(i - 1).substring(0, endPos));
						}
						/*} catch (StringIndexOutOfBoundsException e) {
							System.out.println(
									"Bizarre, pour " + this + " from " + startLine + ":" + endPos + " to " + endLine + ":" + endPos);
							System.out.println(getRawSource().debug());
							System.out.println("String = [" + rows.get(i - 1) + "]");
							System.out.println("Je cherche a extraire 0-" + endPos);
							sb.append("ERROR!");
							Thread.dumpStack();
						}*/
					}
					else {
						sb.append(rows.get(i - 1) + "\n");
					}
				}
				return sb.toString();
			}
			return null;
		}

		public boolean intersects(RawSourceFragment otherFragment) {
			if (!getRawSource().equals(otherFragment.getRawSource()))
				return false;
			RawSourcePosition s1 = getStartPosition();
			RawSourcePosition e1 = getEndPosition();
			RawSourcePosition s2 = otherFragment.getStartPosition();
			RawSourcePosition e2 = otherFragment.getEndPosition();
			if (s1.compareTo(s2) < 0) {
				// s1 is BEFORE s2
				if (e1.compareTo(s2) <= 0) {
					// e1 is BEFORE s2
					return false; // This fragment is located BEFORE other fragment
				}
				else {
					return true; // Fragments intersects
				}
			}
			else if (s2.compareTo(s1) < 0) {
				// s2 is BEFORE s1
				if (e2.compareTo(s1) <= 0) {
					// e2 is BEFORE s1
					return false; // Other fragment is located BEFORE this fragment
				}
				else {
					return true; // Fragments intersects
				}
			}
			else { // s1==s2
				return getLength() > 0 && otherFragment.getLength() > 0;
			}
		}

		public RawSourceFragment union(RawSourceFragment f) {
			if (f == null) {
				return this;
			}
			RawSourcePosition s = getStartPosition();
			RawSourcePosition e = getEndPosition();
			if (f.getStartPosition().isBefore(s)) {
				s = f.getStartPosition();
			}
			if (f.getEndPosition().isAfter(e)) {
				e = f.getEndPosition();
			}
			return getRawSource().makeFragment(s, e);
		}

		public int getLength() {
			return getRawText().length();
		}

		public RawSource getRawSource() {
			return RawSource.this;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getRawSource().hashCode();
			result = prime * result + ((end == null) ? 0 : end.hashCode());
			result = prime * result + ((start == null) ? 0 : start.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RawSourceFragment other = (RawSourceFragment) obj;
			if (!getRawSource().equals(other.getRawSource()))
				return false;
			if (end == null) {
				if (other.end != null)
					return false;
			}
			else if (!end.equals(other.end))
				return false;
			if (start == null) {
				if (other.start != null)
					return false;
			}
			else if (!start.equals(other.start))
				return false;
			return true;
		}

	}

	public RawSource(InputStream inputStream) throws IOException {
		this(new InputStreamReader(inputStream));
	}

	public RawSource(Reader reader) throws IOException {
		rawText = new StringBuffer();
		rows = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(reader)) {
			String nextLine = null;
			do {
				nextLine = br.readLine();
				if (nextLine != null) {
					rows.add(nextLine);
					rawText.append(nextLine + StringUtils.LINE_SEPARATOR);
				}
			} while (nextLine != null);
		}
		startPosition = new RawSourcePosition(1, 0);
		endPosition = new RawSourcePosition(rows.size(), rows.get(rows.size() - 1).length());
	}

	/**
	 * Create a position as a cursor BEFORE the targeted character
	 * 
	 * @param line
	 * @param pos
	 * @return
	 */
	public RawSourcePosition makePositionBeforeChar(int line, int character) {
		return new RawSourcePosition(line, character - 1);
	}

	/**
	 * Create a position as a cursor AFTER the targeted character
	 * 
	 * @param line
	 * @param pos
	 * @return
	 */
	public RawSourcePosition makePositionAfterChar(int line, int character) {
		return new RawSourcePosition(line, character);
	}

	public RawSourcePosition getStartPosition() {
		return startPosition;
	}

	public RawSourcePosition getEndPosition() {
		return endPosition;
	}

	/**
	 * Build new fragment, identified by start position and end position
	 * 
	 * @param start
	 *            start position (index of first character to take)
	 * @param end
	 *            end position (index of last character to take)
	 */
	public RawSourceFragment makeFragment(RawSourcePosition start, RawSourcePosition end) {
		return new RawSourceFragment(start, end);
	}

	/**
	 * Build new fragment, identified by start position and end position
	 * 
	 * @param startLine
	 * @param startCharacter
	 * @param endLine
	 * @param endCharacter
	 * @return
	 */
	public RawSourceFragment makeFragment(int startLine, int startCharacter, int endLine, int endCharacter) {
		return makeFragment(makePositionAfterChar(startLine, startCharacter), makePositionAfterChar(endLine, endCharacter));
	}

	/**
	 * @return the size of the source, its number of rows
	 */
	public int size() {
		return rows.size();
	}

	/**
	 * @return row identified by its index
	 */
	public String getRow(int rowIndex) {
		return rows.get(rowIndex);
	}

	public String debug() {
		StringBuffer sb = new StringBuffer();
		int i = 1;
		for (String row : rows) {
			String lineNumber = "" + i;
			lineNumber = StringUtils.buildWhiteSpaceIndentation(4 - lineNumber.length()) + lineNumber;
			sb.append("| " + lineNumber + " : " + row + "\n");
			i++;
		}
		return sb.toString();
	}

	public int getIndex(RawSourcePosition position) {
		// System.out.println("Index de "+position);
		int index = 0;
		for (int i = 1; i < position.getLine(); i++) {
			index += getRow(i - 1).length() + 1;
		}
		index += position.pos;
		return index;
	}

	public String getRawText() {
		return rawText.toString();
	}

}
