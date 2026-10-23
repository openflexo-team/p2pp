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

import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.toolbox.StringUtils;

/**
 * A static contents (a keyword for example)
 * 
 * @author sylvain
 *
 * @param <N>
 *            Type of AST node
 * @param <T>
 *            General type of pretty-printable object
 */
public class StaticContents<N, T> extends PrettyPrintableContents<N, T> {

	private final String staticContents;

	public StaticContents(P2PPNode<N, T> node, String staticContents, RawSourceFragment fragment) {
		super(node);
		this.staticContents = staticContents;
	}

	public StaticContents(P2PPNode<N, T> node, String prelude, String staticContents, RawSourceFragment fragment) {
		super(node, prelude, null);
		this.staticContents = staticContents;
	}

	public StaticContents(P2PPNode<N, T> node, String prelude, String staticContents, String postlude, RawSourceFragment fragment) {
		super(node, prelude, postlude);
		this.staticContents = staticContents;
	}

	public String getStaticContents() {
		return staticContents;
	}

	@Override
	public String getNormalizedPrettyPrint(PrettyPrintContext context) {
		StringBuffer sb = new StringBuffer();
		if (StringUtils.isNotEmpty(getPrelude())) {
			sb.append(getPrelude());
		}
		if (StringUtils.isNotEmpty(getStaticContents())) {
			sb.append(getStaticContents());
		}
		if (StringUtils.isNotEmpty(getPostlude())) {
			sb.append(getPostlude());
		}
		return sb.toString();
	}

	@Override
	public void updatePrettyPrint(DerivedRawSource derivedRawSource, PrettyPrintContext context) {

		super.updatePrettyPrint(derivedRawSource, context);

		String replacedString = getStaticContents();

		/*if (staticContents.equals("abstract")) {
			System.out.println("Prelude: [" + getPrelude() + "]");
			System.out.println("Postlude: [" + getPostlude() + "]");
			System.out.println("Found prelude: " + getPreludeFragment());
			System.out.println("Found postlude: " + getPostludeFragment());
			System.out.println("replacedString=[" + replacedString + "]");
		}*/

		if (StringUtils.isNotEmpty(getPrelude())) {
			// Inject the canonical prelude only when it is not already materialized in the source.
			// getPreludeFragment() detects an EXACT match of the canonical separator; but when the
			// token already exists and is directly preceded by whitespace, that whitespace already
			// separates it and must be preserved. Re-injecting the canonical prelude here corrupted
			// inline constructs whose source whitespace differs from the canonical separator, e.g.
			// turning `{ return x; }` into `{ return x; \n}` (the closing brace has a LINE_SEPARATOR
			// prelude that no longer matches the inline space).
			if (getPreludeFragment() == null && !isPrecededByWhitespaceInSource()) {
				replacedString = getPrelude() + replacedString;
			}
		}
		if (StringUtils.isNotEmpty(getPostlude())) {
			if (getPostludeFragment() == null) {
				replacedString = replacedString + getPostlude();
			}
		}

		derivedRawSource.replace(getFragment() == null ? makeInsertionFragment() : getFragment(), replacedString);

	}

	/**
	 * Return true when this token exists in the parsed source and is immediately preceded by a
	 * whitespace character. In that case the source already separates the token, so the canonical
	 * prelude must not be re-injected (which would otherwise duplicate/replace the original whitespace
	 * with the canonical separator and break inline constructs).
	 */
	private boolean isPrecededByWhitespaceInSource() {
		if (getFragment() == null || getFragment().getStartPosition() == null) {
			return false;
		}
		try {
			Character before = getFragment().getStartPosition().decrement().getCharAfter();
			return before != null && Character.isWhitespace(before);
		}
		catch (RuntimeException e) {
			// Start of source (nothing before): not preceded by whitespace
			return false;
		}
	}

	@Override
	public void initializePrettyPrint(P2PPNode<?, ?> rootNode, PrettyPrintContext context) {
		// Nothing to do
	}
}
