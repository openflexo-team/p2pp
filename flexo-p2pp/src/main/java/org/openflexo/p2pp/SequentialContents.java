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

import java.util.ArrayList;
import java.util.List;

import org.openflexo.p2pp.PrettyPrintContext.Indentation;
import org.openflexo.p2pp.RawSource.RawSourceFragment;

/**
 * A sequential content
 * 
 * This is a control-structure whose semantics is to sequentially execute and append contained {@link PrettyPrintableContents} stored as a
 * {@link List}
 * 
 * @author sylvain
 * 
 * @param <N>
 *            Type of AST node
 * @param <T>
 *            General type of pretty-printable object
 */
public class SequentialContents<N, T> extends PrettyPrintableContents<N, T> {

	private final List<PrettyPrintableContents<N, T>> ppContents;

	/**
	 * Build a new {@link SequentialContents}
	 * 
	 * @param node
	 */
	public SequentialContents(P2PPNode<N, T> node) {
		super(node);
		ppContents = new ArrayList<>();
	}

	public SequentialContents<N, T> append(PrettyPrintableContents<N, T> contents) {
		return append(contents, null);
	}

	public SequentialContents<N, T> append(PrettyPrintableContents<N, T> contents, RawSourceFragment fragment) {
		contents.setParentContents(this);
		contents.setFragment(fragment);
		ppContents.add(contents);
		sequenceFragment = null;
		sequenceExtendedFragment = null;

		return this;
	}

	private RawSourceFragment sequenceFragment;
	private RawSourceFragment sequenceExtendedFragment;

	@Override
	public RawSourceFragment getFragment() {
		if (sequenceFragment == null) {
			buildSequenceFragment();
		}
		return sequenceFragment;
	}

	@Override
	public RawSourceFragment getExtendedFragment() {
		if (sequenceExtendedFragment == null) {
			buildSequenceFragment();
		}
		return sequenceExtendedFragment;
	}

	private void buildSequenceFragment() {
		for (PrettyPrintableContents<N, T> ppContent : ppContents) {
			if (sequenceFragment == null) {
				sequenceFragment = ppContent.getFragment();
			}
			else {
				sequenceFragment = sequenceFragment.union(ppContent.getFragment());
			}
			if (sequenceExtendedFragment == null) {
				sequenceExtendedFragment = ppContent.getExtendedFragment();
			}
			else {
				sequenceExtendedFragment = sequenceExtendedFragment.union(ppContent.getExtendedFragment());
			}
		}
	}

	public List<PrettyPrintableContents<N, T>> getPPContents() {
		return ppContents;
	}

	@Override
	public String getNormalizedPrettyPrint(PrettyPrintContext context) {
		StringBuffer sb = new StringBuffer();
		for (PrettyPrintableContents<N, T> prettyPrintableContents : ppContents) {
			sb.append(prettyPrintableContents.getNormalizedPrettyPrint(context));
		}
		// If sequence block is indented, do it now
		if (getIndentation() == Indentation.Indent) {
			PrettyPrintContext derivedContext = context.derive(getIndentation());
			return derivedContext.indent(sb.toString());
		}
		return sb.toString();
	}

	@Override
	public void updatePrettyPrint(DerivedRawSource derivedRawSource, PrettyPrintContext context) {

		super.updatePrettyPrint(derivedRawSource, context);

		for (PrettyPrintableContents<N, T> prettyPrintableContents : ppContents) {
			prettyPrintableContents.updatePrettyPrint(derivedRawSource, context);
		}
	}

	@Override
	public void initializePrettyPrint(P2PPNode<?, ?> rootNode, PrettyPrintContext context) {
		for (PrettyPrintableContents<N, T> prettyPrintableContents : ppContents) {
			prettyPrintableContents.initializePrettyPrint(rootNode, context);
		}
	}

	@Override
	protected void debug(StringBuffer sb, int identation) {
		super.debug(sb, identation);
		for (PrettyPrintableContents<N, T> prettyPrintableContents : ppContents) {
			prettyPrintableContents.debug(sb, identation + 2);
		}
	}

}
