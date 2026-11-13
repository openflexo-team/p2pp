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

import java.util.Objects;
import java.util.function.Supplier;

import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.toolbox.StringUtils;

/**
 * A dynamic content (a String computed from underlying pretty-printable object)
 * 
 * @author sylvain
 *
 * @param <N>
 *            Type of AST node
 * @param <T>
 *            General type of pretty-printable object
 */
public class DynamicContents<N, T> extends PrettyPrintableContents<N, T> {

	private final Supplier<String> stringRepresentationSupplier;

	// P2PP-F-2: when set, the parsed text is kept while the value is the one captured once the model was fully built
	private boolean keepsParsedTextWhileUnchanged = false;
	// P2PP-F-2: the text written by the preserving pretty-print when it differs from the normalized one (a value made of parts that keep
	// their own parsed text)
	private Supplier<String> derivedValueSupplier;
	private boolean referenceValueCaptured = false;
	private String referenceValue;

	/**
	 * Build a new {@link DynamicContents}, whose value is intented to replace text determined with supplied fragment
	 * 
	 * @param prelude
	 * @param stringRepresentationSupplier
	 *            gives dynamic value of that contents
	 * @param fragment
	 */
	public DynamicContents(P2PPNode<N, T> node, String prelude, Supplier<String> stringRepresentationSupplier, String postlude,
			RawSourceFragment fragment) {
		super(node, prelude, postlude);
		this.stringRepresentationSupplier = stringRepresentationSupplier;
		setFragment(fragment);
	}

	@Override
	public String getNormalizedPrettyPrint(PrettyPrintContext context) {
		StringBuffer sb = new StringBuffer();
		// Written in its normalized form inside the preserving pretty-print (an object created by a model change): its value as written
		String dynamicValue = context != null && context.isPreservingParsedText() && derivedValueSupplier != null ? derivedValueSupplier.get()
				: stringRepresentationSupplier.get();
		if (StringUtils.isNotEmpty(dynamicValue)) {
			if (StringUtils.isNotEmpty(getPrelude())) {
				sb.append(getPrelude());
			}
			sb.append(dynamicValue);
			if (StringUtils.isNotEmpty(getPostlude())) {
				sb.append(getPostlude());
			}
		}
		return sb.toString();
	}

	/*@Override
	public void updatePrettyPrint(DerivedRawSource derivedRawSource, PrettyPrintContext context) {
	
		super.updatePrettyPrint(derivedRawSource, context);
	
		// System.out.println("> Pour DynamicContents, faudrait passer " + getFragment() + " a " + stringRepresentationSupplier.get());
		derivedRawSource.replace(getFragment(), stringRepresentationSupplier.get());
	}*/

	/**
	 * Keep the parsed text of this contents as long as its value is the one it had when the model was built (see
	 * {@link #captureReferenceValue()}), instead of writing the value again at each pretty-print.<br>
	 * To be used when the value is a normalized form of the text (an expression, a qualified name, a literal): {@code 1+2} keeps being
	 * written {@code 1+2}, not {@code 1 + 2}, until the value changes. Without a captured reference value, the value is written as
	 * before.
	 * 
	 * @return this contents
	 */
	public DynamicContents<N, T> keepingParsedTextWhileUnchanged() {
		keepsParsedTextWhileUnchanged = true;
		return this;
	}

	/**
	 * The text the preserving pretty-print writes when the parsed text cannot be kept, if it is not the normalized value: a value made of
	 * parts that keep their own parsed text (the items of a list, the operands of an expression). It is also written when this contents
	 * is written in its normalized form inside the preserving pretty-print (an object created by a model change, see
	 * {@link PrettyPrintContext#isPreservingParsedText()}). The normalized pretty-print of a whole text, and the comparison with the
	 * reference value, still use the normalized value.
	 * 
	 * @return this contents
	 */
	public DynamicContents<N, T> derivedWith(Supplier<String> derivedValueSupplier) {
		this.derivedValueSupplier = derivedValueSupplier;
		return this;
	}

	public boolean keepsParsedTextWhileUnchanged() {
		return keepsParsedTextWhileUnchanged;
	}

	/**
	 * Record the current value as the one the parsed text stands for. Called once the model built from the source is complete (references
	 * resolved), before any edit: see {@link P2PPNode#captureReferenceValues()}
	 */
	@Override
	public void captureReferenceValue() {
		if (keepsParsedTextWhileUnchanged) {
			referenceValue = stringRepresentationSupplier.get();
			referenceValueCaptured = true;
		}
	}

	/**
	 * Tells whether the parsed text of this contents still stands for its value: it was parsed, and its value is the captured reference
	 * value
	 */
	public boolean isParsedTextUpToDate() {
		return keepsParsedTextWhileUnchanged && referenceValueCaptured && getFragment() != null
				&& Objects.equals(stringRepresentationSupplier.get(), referenceValue);
	}

	@Override
	public void updatePrettyPrint(DerivedRawSource derivedRawSource, PrettyPrintContext context) {

		super.updatePrettyPrint(derivedRawSource, context);

		if (isParsedTextUpToDate()) {
			// The value did not change since it was parsed: the text of the source is kept as it was written
			return;
		}

		String replacedString = derivedValueSupplier != null ? derivedValueSupplier.get() : stringRepresentationSupplier.get();
		if (replacedString == null) {
			// A null value is an empty one (it must not be written as "null")
			replacedString = "";
		}

		RawSourceFragment fragmentToReplace = getFragment();
		boolean inserted = fragmentToReplace == null;

		if (fragmentToReplace == null) {
			fragmentToReplace = makeInsertionFragment();
		}

		if (StringUtils.isNotEmpty(replacedString)) {
			if (StringUtils.isNotEmpty(getPrelude())) {
				if (getPreludeFragment() == null) {
					replacedString = getPrelude() + replacedString;
				}
			}
			if (StringUtils.isNotEmpty(getPostlude())) {
				if (getPostludeFragment() == null) {
					replacedString = replacedString + getPostlude();
				}
			}
			if (inserted) {
				replacedString = separatorMissingBeforeInsertion(replacedString) + replacedString;
			}
		}
		else {
			if (fragmentToReplace.getLength() > 0) {
				// A significant portion of text (length>0) was initially parsed, but the value to pretty-print is empty now
				// We have to remove eventual prelude and postlude
				fragmentToReplace = getExtendedFragment();
			}
		}

		derivedRawSource.replace(fragmentToReplace, replacedString);

	}

	@Override
	public void initializePrettyPrint(P2PPNode<?, ?> rootNode, PrettyPrintContext context) {
		// Nothing to do
	}

}
