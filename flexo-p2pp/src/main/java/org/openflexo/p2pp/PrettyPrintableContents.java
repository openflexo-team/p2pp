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

import org.openflexo.p2pp.DerivedRawSource.Modification;
import org.openflexo.p2pp.PrettyPrintContext.Indentation;
import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.p2pp.RawSource.RawSourcePosition;
import org.openflexo.toolbox.StringUtils;

/**
 * Specification of a part of pretty-print for a pretty-printable object
 * 
 * Note that the structure of {@link PrettyPrintableContents} is fixed for a given {@link P2PPNode} during its whole life cycle. Once it is
 * defined, this structure won't change, and the same {@link PrettyPrintableContents} structure may be used to compute multiple
 * pretty-prints according to underlying model changes. Remember that all PrettyPrintableContents keep reference to the originally parsed
 * context.
 * 
 * When a new parsing is required, all {@link P2PPNode} and underlying {@link PrettyPrintableContents} are newly instantiated and thus
 * reference new {@link RawSourceFragment}
 * 
 * @author sylvain
 *
 * @param <N>
 *            Type of AST node
 * @param <T>
 *            General type of pretty-printable object
 */
public abstract class PrettyPrintableContents<N, T> {

	private final P2PPNode<N, T> node;

	private String identifier;

	private final String prelude;
	private final String postlude;
	private Indentation indentation;

	private RawSourceFragment fragment = null;
	private RawSourceFragment preludeFragment;
	private RawSourceFragment postludeFragment;

	private PrettyPrintableContents<N, T> parentContents;

	public PrettyPrintableContents(P2PPNode<N, T> node, String prelude, String postlude, Indentation indentation) {
		super();
		this.node = node;
		this.prelude = prelude;
		this.postlude = postlude;
		this.indentation = indentation;
	}

	public PrettyPrintableContents(P2PPNode<N, T> node, String prelude, String postlude) {
		super();
		this.node = node;
		this.prelude = prelude;
		this.postlude = postlude;
	}

	public PrettyPrintableContents(P2PPNode<N, T> node, Indentation indentation) {
		super();
		this.node = node;
		this.prelude = null;
		this.postlude = null;
		this.indentation = indentation;
	}

	public PrettyPrintableContents(P2PPNode<N, T> node) {
		super();
		this.node = node;
		this.prelude = null;
		this.postlude = null;
	}

	public String getIdentifier() {
		if (identifier == null) {
			return getClass().getSimpleName() + getIndex();
		}
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public PrettyPrintableContents<N, T> getParentContents() {
		return parentContents;
	}

	protected void setParentContents(PrettyPrintableContents<N, T> parentContents) {
		this.parentContents = parentContents;
	}

	/**
	 * Return {@link P2PPNode} where this {@link PrettyPrintableContents} was defined as contents of {@link P2PPNode}
	 * 
	 * @return
	 */
	public P2PPNode<N, T> getNode() {
		return node;
	}

	public String getPrelude() {
		return prelude;
	}

	public String getPostlude() {
		return postlude;
	}

	public Indentation getIndentation() {
		return indentation;
	}

	public PrettyPrintableContents<N, T> indent() {
		indentation = Indentation.Indent;
		return this;
	}

	/**
	 * Return the originally parsed fragment
	 * 
	 * @return
	 */
	public RawSourceFragment getFragment() {
		return fragment;
	}

	/**
	 * Called when a {@link DynamicContents} is registered to the place determined with supplied fragment
	 * 
	 * @param fragment
	 */
	public void setFragment(RawSourceFragment fragment) {
		this.fragment = fragment;
	}

	/**
	 * Return index of this contents in container
	 * 
	 * @return
	 */
	public int getIndex() {
		if (getParentContents() == null) {
			return getNode().getPPContents().indexOf(this);
		}
		if (getParentContents() instanceof SequentialContents) {
			return ((SequentialContents<N, T>) getParentContents()).getPPContents().indexOf(this);
		}
		if (getParentContents() instanceof ConditionalContents) {
			ConditionalContents<N, T> cc = (ConditionalContents<N, T>) getParentContents();
			if (cc.getThenContents() == this) {
				return 0;
			}
			if (cc.getElseContents() == this) {
				return 1;
			}
		}
		System.err.println("Unexpected parent " + getParentContents());
		return -1;
	}

	/**
	 * Return the previous contents found in related {@link P2PPNode}, relatively to the pretty-print semantics
	 * 
	 * This is the contents used to retrieve an insertion point
	 * 
	 * @return
	 */
	public PrettyPrintableContents<?, ?> getPreviousContents() {
		if (getParentContents() == null) {
			if (getIndex() > 0) {
				return getNode().getPPContents().get(getIndex() - 1);
			}
			return null;
		}
		if (getParentContents() instanceof SequentialContents) {
			if (getIndex() > 0) {
				return ((SequentialContents<N, T>) getParentContents()).getPPContents().get(getIndex() - 1);
			}
		}
		return getParentContents().getPreviousContents();
	}

	/**
	 * Return the position where to insert a new contents when this {@link PrettyPrintableContents} is applied
	 * 
	 * This scheme is applied when no existing fragment was found (the last parsing could not locate any existing fragment), in this case,
	 * we must compute the location where new contents has to be inserted
	 * 
	 * @return
	 */
	public RawSourcePosition getInsertionPoint() {
		// If we are a THEN or ELSE, delegate computation of insertion point to the ConditionalContents
		if (getParentContents() instanceof ConditionalContents) {
			return getParentContents().getInsertionPoint();
		}
		// Otherwise, identify previous contents
		if (getPreviousContents() != null) {
			// If previous contents relies on an existing fragment, return end position (including postlude when existing)
			if (getPreviousContents().getEndPosition() != null) {
				return getPreviousContents().getEndPosition();
			}
			else {
				// Otherwise delegate computation of insertion point to the previous contents (until an existing content is found)
				return getPreviousContents().getInsertionPoint();
			}
		}
		if (getNode().getLastParsedFragment() != null) {
			return getNode().getLastParsedFragment().getStartPosition();
		}
		return getNode().getDefaultInsertionPoint();
	}

	/**
	 * Return end position of this contents, asserting that it relies on an existing fragment
	 * 
	 * @return
	 */
	public RawSourcePosition getEndPosition() {
		if (getFragment() != null) {
			if (getPostludeFragment() != null) {
				return getPostludeFragment().getEndPosition();
			}
			return getFragment().getEndPosition();
		}
		return null;
	}

	/**
	 * Build and return an empty fragment located on insertion point
	 * 
	 * @return
	 */
	public RawSourceFragment makeInsertionFragment() {
		return getInsertionPoint().getOuterType().makeFragment(getInsertionPoint(), getInsertionPoint());
	}

	/**
	 * Build an return String encoding normalized pretty-print, given a {@link PrettyPrintContext}
	 * 
	 * @param context
	 * @return
	 */
	public abstract String getNormalizedPrettyPrint(PrettyPrintContext context);

	/**
	 * Update pretty-print for this contents, given a {@link PrettyPrintContext} and a {@link DerivedRawSource}
	 * 
	 * This method append required {@link Modification} to the supplied {@link DerivedRawSource} (result is then stored in the
	 * {@link DerivedRawSource})
	 * 
	 * @param derivedRawSource
	 * @param context
	 */
	public void updatePrettyPrint(DerivedRawSource derivedRawSource, PrettyPrintContext context) {
		// No need to recompute, since it will not change during the whole life-cycle of this PrettyPrintableContents
		// preludeFragment = null;
		// postludeFragment = null;
	}

	/**
	 * Called during initialization of pretty-print scheme
	 * 
	 * @param rootNode
	 * @param context
	 */
	public abstract void initializePrettyPrint(P2PPNode<?, ?> rootNode, PrettyPrintContext context);

	/**
	 * Return the originally parsed prelude as a {@link RawSourceFragment}
	 * 
	 * @return
	 */
	public RawSourceFragment getPreludeFragment() {
		if (preludeFragment == null && getFragment() != null && getFragment().getLength() > 0 && StringUtils.isNotEmpty(getPrelude())) {
			preludeFragment = findUnmappedSegmentBackwardFrom(getPrelude(), getFragment().getStartPosition());
		}
		return preludeFragment;
	}

	/**
	 * Return the originally parsed postlude as a {@link RawSourceFragment}
	 * 
	 * @return
	 */
	public RawSourceFragment getPostludeFragment() {
		if (postludeFragment == null && getFragment() != null && getFragment().getLength() > 0 && StringUtils.isNotEmpty(getPostlude())) {
			postludeFragment = findUnmappedSegmentForwardFrom(getPostlude(), getFragment().getEndPosition());
		}
		return postludeFragment;
	}

	/**
	 * Return initially parsed fragment, augmented with the union or originally parsed prelude and postludes
	 * 
	 * @return
	 */
	public RawSourceFragment getExtendedFragment() {
		RawSourceFragment returned = getFragment();
		if (StringUtils.isNotEmpty(getPrelude()) && getPreludeFragment() != null) {
			// Include prelude when required
			returned = getFragment().union(getPreludeFragment());
		}
		if (StringUtils.isNotEmpty(getPostlude()) && getPostludeFragment() != null) {
			// Include postlude when required
			returned = getFragment().union(getPostludeFragment());
		}
		return returned;
	}

	/**
	 * Internally used to retrieve actual extended fragment (We try to avoid here collision between fragments)
	 * 
	 * @return
	 */
	protected RawSourceFragment getExtendedFragmentNoRecomputation() {
		RawSourceFragment returned = getFragment();
		if (StringUtils.isNotEmpty(getPrelude()) && preludeFragment != null) {
			// Include prelude when required
			returned = getFragment().union(preludeFragment);
		}
		if (StringUtils.isNotEmpty(getPostlude()) && postludeFragment != null) {
			// Include postlude when required
			returned = getFragment().union(postludeFragment);
		}
		return returned;
	}

	private RawSourceFragment findUnmappedSegmentBackwardFrom(String expected, RawSourcePosition position) {
		int length = expected.length();
		int i = 0;
		// System.out.println("Backward looking for [" + expected + "] from " + position);
		boolean positionStillValid = true;
		while (positionStillValid) {
			try {
				RawSourcePosition start = position.decrement(length + i);
				RawSourcePosition end = position.decrement(i);
				RawSourceFragment f = position.getOuterType().makeFragment(start, end);
				// System.out.println("Test backward fragment " + f + " [" + f.getRawText() + "]");
				if (node.isFragmentMappedInPPContents(f)) {
					// This fragment intersects another mapped fragment, abort
					return null;
				}
				if (f.getRawText().equals(expected)) {
					return f;
				}
				i++;
				if (start.decrement().isBefore(node.getStartPosition())) {
					positionStillValid = false;
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				positionStillValid = false;
			}
		}
		return null;
	}

	private RawSourceFragment findUnmappedSegmentForwardFrom(String expected, RawSourcePosition position) {
		int length = expected.length();
		int i = 0;
		// System.out.println("Forward looking for [" + expected + "] from " + position + " in fragment " + node.getLastParsedFragment());
		boolean positionStillValid = true;
		while (positionStillValid) {
			try {
				RawSourcePosition start = position.increment(i);
				RawSourcePosition end = position.increment(length + i);
				RawSourceFragment f = position.getOuterType().makeFragment(start, end);
				if (node.isFragmentMappedInPPContents(f)) {
					// This fragment intersects another mapped fragment, abort
					return null;
				}
				if (f.getRawText().equals(expected)) {
					return f;
				}
				i++;
				if (end.increment().isAfter(node.getEndPosition())) {
					positionStillValid = false;
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				positionStillValid = false;
			}
		}
		return null;
	}

	protected void debug(StringBuffer sb, int identation) {
		String indent = StringUtils.buildWhiteSpaceIndentation(identation * 2);
		sb.append(indent + "> " + getClass().getSimpleName() + "[" + getIdentifier() + "]" + " fragment=" + getFragment() + "["
				+ (getFragment() != null ? getFragment().getRawText() : "?") + "] prelude=" + getPreludeFragment() + " postlude="
				+ getPostludeFragment() + " insertionPoint=" + getInsertionPoint() + "\n");
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		debug(sb, 0);
		return sb.toString();
	}

}
