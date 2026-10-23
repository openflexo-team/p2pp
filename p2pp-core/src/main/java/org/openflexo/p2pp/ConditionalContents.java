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

import java.util.function.Supplier;
import java.util.logging.Logger;

import org.openflexo.p2pp.PrettyPrintContext.Indentation;
import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.toolbox.StringUtils;

/**
 * A conditional {@link PrettyPrintableContents}
 * 
 * This is a control-structure defining alternatives for pretty-printing for a {@link P2PPNode}
 * 
 * @author sylvain
 *
 * @param <N>
 *            Type of AST node
 * @param <T>
 *            General type of pretty-printable object
 */
public class ConditionalContents<N, T> extends PrettyPrintableContents<N, T> {

	private static final Logger logger = Logger.getLogger(ConditionalContents.class.getPackage().getName());

	private final Supplier<Boolean> conditionSupplier;

	private PrettyPrintableContents<N, T> thenContents;
	private PrettyPrintableContents<N, T> elseContents;

	/**
	 * Build a new {@link ConditionalContents}, whose value is intented to replace text determined with supplied fragment
	 * 
	 * @param prelude
	 * @param stringRepresentationSupplier
	 *            gives dynamic value of that contents
	 * @param fragment
	 */
	public ConditionalContents(P2PPNode<N, T> node, Supplier<Boolean> conditionSupplier) {
		super(node);
		this.conditionSupplier = conditionSupplier;
	}

	public PrettyPrintableContents<N, T> getThenContents() {
		return thenContents;
	}

	public void setThenContents(PrettyPrintableContents<N, T> thenContents) {
		this.thenContents = thenContents;
	}

	public PrettyPrintableContents<N, T> getElseContents() {
		return elseContents;
	}

	public void setElseContents(PrettyPrintableContents<N, T> elseContents) {
		this.elseContents = elseContents;
	}

	public ConditionalContents<N, T> thenAppend(PrettyPrintableContents<N, T> contents, RawSourceFragment fragment) {

		// System.out.println("***** Appended " + contents + " in " + fragment + " insertionPoint=" + getNode().getDefaultInsertionPoint());

		if (thenContents == null) {
			contents.setParentContents(this);
			contents.setFragment(fragment);
			thenContents = contents;
		}
		else if (thenContents instanceof SequentialContents) {
			((SequentialContents<N, T>) thenContents).append(contents, fragment);
		}
		else {
			SequentialContents<N, T> sequentialContents = new SequentialContents<>(getNode());
			sequentialContents.setParentContents(this);
			sequentialContents.append(thenContents, thenContents.getFragment());
			sequentialContents.append(contents, fragment);
			thenContents = sequentialContents;
		}
		return this;
	}

	public ConditionalContents<N, T> thenAppend(ChildContents<N, T, ?, ?> contents) {
		return thenAppend(contents, null);
	}

	public ConditionalContents<N, T> thenAppend(ChildrenContents<N, T, ?, ?> contents) {
		return thenAppend(contents, null);
	}

	public ConditionalContents<N, T> elseAppend(PrettyPrintableContents<N, T> contents, RawSourceFragment fragment) {

		if (elseContents == null) {
			contents.setParentContents(this);
			contents.setFragment(fragment);
			elseContents = contents;
		}
		else if (elseContents instanceof SequentialContents) {
			((SequentialContents<N, T>) elseContents).append(contents, fragment);
		}
		else {
			SequentialContents<N, T> sequentialContents = new SequentialContents<N, T>(getNode());
			sequentialContents.setParentContents(this);
			sequentialContents.append(elseContents, elseContents.getFragment());
			sequentialContents.append(contents, fragment);
			elseContents = sequentialContents;
		}
		return this;
	}

	public ConditionalContents<N, T> elseAppend(ChildContents<N, T, ?, ?> contents) {
		return elseAppend(contents, null);
	}

	public ConditionalContents<N, T> elseAppend(ChildrenContents<N, T, ?, ?> contents) {
		return elseAppend(contents, null);
	}

	@Override
	public String getNormalizedPrettyPrint(PrettyPrintContext context) {

		String returned;

		if (conditionSupplier.get()) {
			if (getThenContents() != null) {
				returned = getThenContents().getNormalizedPrettyPrint(context);
			}
			else {
				returned = "<???>";
			}
		}
		else {
			if (getElseContents() != null) {
				returned = getElseContents().getNormalizedPrettyPrint(context);
			}
			else {
				returned = "";
			}
		}

		if (getIndentation() == Indentation.Indent) {
			PrettyPrintContext derivedContext = context.derive(getIndentation());
			// System.out.println("WAS: [" + returned + "]");
			// System.out.println("NOW: [" + derivedContext.indent(returned) + "]");
			return derivedContext.indent(returned);
		}

		return returned;

	}

	@Override
	public void updatePrettyPrint(DerivedRawSource derivedRawSource, PrettyPrintContext context) {

		super.updatePrettyPrint(derivedRawSource, context);

		boolean currentCondition = conditionSupplier.get();

		if (initialCondition) {
			// This match a fragment that was existing when initially parsed
			// Original fragment : initialExtendedFragment
			if (currentCondition) { // Condition still valid
				if (getThenContents() != null) {
					getThenContents().updatePrettyPrint(derivedRawSource, context);
				}
				else {
					logger.warning("Unexpected null THEN contents");
				}
			}
			else { // Condition changed : we have now to remove this fragment
				if (getElseContents() != null) {
					getElseContents().updatePrettyPrint(derivedRawSource, context);
				}
				if (initialExtendedFragment != null) {
					derivedRawSource.replace(initialExtendedFragment, "");
				}
			}
		}

		else {
			if (currentCondition) { // This conditional was initially false and is now true
				if (getThenContents() != null) {
					getThenContents().updatePrettyPrint(derivedRawSource, context);
				}
				if (initialExtendedFragment != null) {
					derivedRawSource.replace(initialExtendedFragment, "");
				}
			}
			else { // Was false and still false > update Else
				if (getElseContents() != null) {
					getElseContents().updatePrettyPrint(derivedRawSource, context);
				}
			}
		}

	}

	@Override
	public void initializePrettyPrint(P2PPNode<?, ?> rootNode, PrettyPrintContext context) {
		initialCondition = conditionSupplier.get();
		initialExtendedFragment = getExtendedFragment();
		if (getThenContents() != null && initialCondition) {
			getThenContents().initializePrettyPrint(rootNode, context);
		}
		if (getElseContents() != null && (!initialCondition)) {
			getElseContents().initializePrettyPrint(rootNode, context);
		}
	}

	private boolean initialCondition;
	private RawSourceFragment initialExtendedFragment;

	@Override
	public RawSourceFragment getFragment() {
		if (conditionSupplier.get() && getThenContents() != null) {
			return getThenContents().getFragment();
		}
		else if (getElseContents() != null) {
			return getElseContents().getFragment();
		}
		return null;
	}

	@Override
	public RawSourceFragment getExtendedFragment() {
		if (conditionSupplier.get() && getThenContents() != null) {
			return getThenContents().getExtendedFragment();
		}
		else if (getElseContents() != null) {
			return getElseContents().getExtendedFragment();
		}
		return null;
	}

	@Override
	protected void debug(StringBuffer sb, int identation) {
		String indent = StringUtils.buildWhiteSpaceIndentation(identation * 2);
		sb.append(indent + "> " + getClass().getSimpleName() + "[" + getIdentifier() + "]" + " fragment=" + getFragment() + "["
				+ (getFragment() != null ? getFragment().getRawText() : "?") + "] prelude=" + getPreludeFragment() + " postlude="
				+ getPostludeFragment() + " insertionPoint=" + getInsertionPoint() + "\n");
		if (getThenContents() != null) {
			sb.append(indent + "  THEN\n");
			getThenContents().debug(sb, identation + 2);
		}
		if (getElseContents() != null) {
			sb.append(indent + "  ELSE\n");
			getElseContents().debug(sb, identation + 2);
		}
	}

}
