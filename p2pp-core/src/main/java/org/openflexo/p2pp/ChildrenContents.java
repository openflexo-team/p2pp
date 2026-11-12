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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.openflexo.connie.type.TypeUtils;
import org.openflexo.p2pp.PrettyPrintContext.Indentation;
import org.openflexo.p2pp.RawSource.RawSourcePosition;
import org.openflexo.toolbox.StringUtils;

/**
 * Specification of the pretty-print of a collection of children objects
 * 
 * @author sylvain
 *
 * @param <PN>
 *            Type of AST node of parent
 * @param <PT>
 *            General type of pretty-printable parent object
 * @param <CN>
 *            Type of AST nodes referenced by this {@link ChildrenContents}
 * @param <CT>
 *            Type of children objects beeing handled in this {@link ChildrenContents}
 */
public class ChildrenContents<PN, PT, CN, CT> extends PrettyPrintableContents<PN, PT> {

	private static final Logger logger = Logger.getLogger(ChildrenContents.class.getPackage().getName());

	private final String preludeForFirstItem;
	private final String postludeForLastItem;

	private Supplier<List<? extends CT>> childrenObjectsSupplier;

	private ArrayList<P2PPNode<CN, CT>> lastParsedNodes;
	private List<P2PPNode<CN, CT>> childrenNodes;

	public ChildrenContents(P2PPNode<PN, PT> parentNode, String prelude, Supplier<List<? extends CT>> childrenObjects, String postlude,
			Indentation indentation, Class<CT> objectType) {
		this(parentNode, null, prelude, childrenObjects, postlude, null, indentation, objectType);
	}

	@SuppressWarnings("unchecked")
	public ChildrenContents(P2PPNode<PN, PT> parentNode, String preludeForFirstItem, String prelude,
			Supplier<List<? extends CT>> childrenObjects, String postlude, String postludeForLastItem, Indentation indentation,
			Class<CT> objectType) {
		super(parentNode, prelude, postlude, indentation);
		this.preludeForFirstItem = preludeForFirstItem;
		this.postludeForLastItem = postludeForLastItem;

		childrenObjectsSupplier = childrenObjects;

		// System.out.println("Tous les children: " + parentNode.getChildren());
		// System.out.println("Type: " + objectType);
		lastParsedNodes = new ArrayList<>();
		for (P2PPNode<?, ?> objectNode : parentNode.getChildren()) {
			// TODO: Y a t'il vraiment besoin de se lier à connie ici ?
			if (TypeUtils.isOfType(objectNode.getModelObject(), objectType)) {
				lastParsedNodes.add((P2PPNode<CN, CT>) objectNode);
			}
		}

	}

	public P2PPNode<PN, PT> getParentNode() {
		return getNode();
	}

	public List<P2PPNode<CN, CT>> getChildrenNodes() {
		return childrenNodes;
	}

	@Override
	public String getNormalizedPrettyPrint(PrettyPrintContext context) {
		StringBuffer sb = new StringBuffer();

		List<? extends CT> allObjects = childrenObjectsSupplier.get();

		if (allObjects == null) {
			return "";
		}

		for (int i = 0; i < allObjects.size(); i++) {
			CT childObject = allObjects.get(i);
			String applicablePrelude = getPrelude();
			if (i == 0 && preludeForFirstItem != null) {
				applicablePrelude = preludeForFirstItem;
			}
			String applicablePostlude = getPostlude();
			if (i == allObjects.size() - 1 && postludeForLastItem != null) {
				applicablePostlude = postludeForLastItem;
			}
			P2PPNode<?, CT> childNode = getParentNode().getObjectNode(childObject);
			if (childNode == null) {
				childNode = getParentNode().makeObjectNode(childObject);
				if (childNode == null) {
					logger.severe("Cannot make node for " + childObject);
					Thread.dumpStack();
				}
				else {
					getParentNode().addToChildren(childNode);
				}
			}
			if (childNode != null) {
				childNode.setRegisteredForContents(this);
				String childPrettyPrint = childNode.getNormalizedTextualRepresentation(context.derive(getIndentation()));
				if (StringUtils.isNotEmpty(childPrettyPrint)) {
					if (StringUtils.isNotEmpty(applicablePrelude)) {
						sb.append(applicablePrelude);
					}
					sb.append(childPrettyPrint);
					if (StringUtils.isNotEmpty(applicablePostlude)) {
						sb.append(applicablePostlude);
					}
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Override {@link #getInsertionPoint()} by looking up last parsed nodes
	 */
	@Override
	public RawSourcePosition getInsertionPoint() {
		// System.out.println("lastParsedNodes=" + lastParsedNodes);
		if (lastParsedNodes.size() > 0) {
			P2PPNode<CN, CT> lastNode = lastParsedNodes.get(lastParsedNodes.size() - 1);
			if (lastNode.getPostlude() != null) {
				return lastNode.getPostlude().getEndPosition();
			}
			else {
				return lastNode.getLastParsedFragment().getEndPosition();
			}
		}
		return super.getInsertionPoint();
	}

	private boolean DEBUG = false;

	@Override
	public void updatePrettyPrint(DerivedRawSource derivedRawSource, PrettyPrintContext context) {

		super.updatePrettyPrint(derivedRawSource, context);

		// System.out.println("Children: " + parentNode.getChildren());
		// System.out.println("Type: " + objectType);
		List<P2PPNode<?, CT>> nodesToBeRemoved = new ArrayList<>();
		for (P2PPNode<?, CT> lastParsedNode : lastParsedNodes) {
			if (lastParsedNode.getRegisteredForContents() == this) {
				nodesToBeRemoved.add(lastParsedNode);
			}
		}

		RawSourcePosition insertionPoint = getInsertionPoint(); // defaultInsertionPoint;
		RawSourcePosition insertionPointAfterPostlude = getInsertionPoint(); // defaultInsertionPoint;

		PrettyPrintContext derivedContext = context.derive(getIndentation());

		List<? extends CT> childrenObjectsList = childrenObjectsSupplier.get();

		if (DEBUG) {
			System.out.println("Handling children: " + childrenObjectsList);
			System.out.println("insertionPoint=" + insertionPoint);
			System.out.println("PARENT insertionPoint=" + getParentNode().getDefaultInsertionPoint());
		}

		// The nodes of the children, in the order of the model (the nodes of new children are made now)
		List<P2PPNode<?, CT>> nodesInModelOrder = new ArrayList<>();
		for (CT childObject : childrenObjectsList) {
			P2PPNode<?, CT> childNode = getParentNode().getObjectNode(childObject);
			if (childNode == null) {
				childNode = getParentNode().makeObjectNode(childObject);
				if (childNode == null) {
					System.err.println("Cannot make P2PPNode for " + childObject + " in " + getParentNode());
				}
				getParentNode().addToChildren(childNode);
			}
			childNode.setRegisteredForContents(this);
			nodesInModelOrder.add(childNode);
		}

		// The parsed nodes that stay: a parsed node is replaced in its place ("slot") in the source by the node that has taken it in the
		// model. Without reordering, the node of each slot is the one that was parsed there
		List<P2PPNode<?, CT>> parsedInModelOrder = new ArrayList<>();
		for (P2PPNode<?, CT> node : nodesInModelOrder) {
			if (lastParsedNodes.contains(node)) {
				parsedInModelOrder.add(node);
			}
		}
		List<P2PPNode<?, CT>> slots = new ArrayList<>();
		for (P2PPNode<CN, CT> node : lastParsedNodes) {
			if (parsedInModelOrder.contains(node)) {
				slots.add(node);
			}
		}
		int parsedIndex = 0;
		String indentString = getIndentation() == Indentation.Indent ? computeIndentString(slots, derivedContext) : null;

		// New children before the first parsed one: they go before it, not after the last parsed one
		int firstParsed = 0;
		while (firstParsed < nodesInModelOrder.size() && !lastParsedNodes.contains(nodesInModelOrder.get(firstParsed))) {
			firstParsed++;
		}
		int start = 0;
		if (!slots.isEmpty() && firstParsed > 0) {
			insertBeforeFirstParsed(derivedRawSource, derivedContext, indentString, nodesInModelOrder.subList(0, firstParsed), slots.get(0));
			start = firstParsed;
		}

		for (int i = start; i < childrenObjectsList.size(); i++) {
			boolean isFirst = (i == 0);
			boolean isLast = (i == childrenObjectsList.size() - 1);
			if (DEBUG) {
				System.out.println("*** Handling " + childrenObjectsList.get(i));
				System.out.println("insertionPoint=" + insertionPoint);
			}
			P2PPNode<?, CT> childNode = nodesInModelOrder.get(i);
			if (!lastParsedNodes.contains(childNode)) {
				// In this case manage insertion
				// KNOWN ISSUE: with no parsed sibling, the insertion point is the start of the parent node, i.e. AFTER its indentation.
				// A child inserted in front of an indented node leaves that node at column 0 - see "Inserting a child in front of an
				// indented node" in KNOWN_DEFECTS.md, at the root of p2pp-core.

				boolean handleSpecificPrelude = false;
				boolean handleSpecificPostlude = false;
				String applicablePrelude = getPrelude();
				if (isFirst && preludeForFirstItem != null) {
					applicablePrelude = preludeForFirstItem;
					handleSpecificPrelude = true;
				}
				String applicablePostlude = getPostlude();
				if (isLast && postludeForLastItem != null) {
					applicablePostlude = postludeForLastItem;
					handleSpecificPostlude = true;
				}

				if (handleSpecificPrelude) {
					if (handleSpecificPostlude) {
						String insertThis = applicablePrelude + newChildText(childNode, derivedContext, indentString) + applicablePostlude;
						if (DEBUG)
							System.out.println("Case 1: Inserting in " + insertionPoint + " value=[" + insertThis + "]");
						derivedRawSource.insert(insertionPoint, insertThis);
					}
					else {
						String insertThis = applicablePrelude + newChildText(childNode, derivedContext, indentString) + applicablePostlude;
						if (DEBUG)
							System.out.println("Case 2: Inserting in " + insertionPoint + " value=[" + insertThis + "]");
						derivedRawSource.insert(insertionPoint, insertThis);
					}
				}
				else {
					if (handleSpecificPostlude) {
						// We have a special postlude for last item and this is the last item
						String insertThis = "";
						RawSourcePosition insertThisAt = insertionPoint;
						if (i > 0) {
							CT previousObject = childrenObjectsList.get(i - 1);
							P2PPNode<?, CT> previousObjectNode = getParentNode().getObjectNode(previousObject);
							if (lastParsedNodes.contains(previousObjectNode)) {
								boolean previousWasLastParsed = lastParsedNodes.get(lastParsedNodes.size() - 1) == previousObjectNode;
								if (!previousWasLastParsed && previousObjectNode.getPostlude() != null) {
									// Previous node was not the last one when parsed: its postlude (the separator) is already there, insert
									// after it. Adding the postlude again would duplicate the separator ("A;" + ";B"), and the original
									// separator would end up after the inserted node. When it was the last one, its postlude is the
									// postlude for last item (e.g. "}"), and the insertion must stay before it
									insertThisAt = insertionPointAfterPostlude;
								}
								else {
									// In this case, previous node hasn't appened postlude, so do it first
									insertThis = (getPostlude() != null ? getPostlude() : "");
								}
							}
						}
						insertThis = insertThis + applicablePrelude + newChildText(childNode, derivedContext, indentString)
								+ (lastParsedNodes.size() > 0 ? "" : applicablePostlude);
						if (DEBUG)
							System.out.println("Case 3: Inserting in " + insertThisAt + " value=[" + insertThis + "]");
						derivedRawSource.insert(insertThisAt, insertThis);
					}
					else {
						String insertThis = applicablePrelude + newChildText(childNode, derivedContext, indentString) + applicablePostlude;
						if (DEBUG)
							System.out.println("Case 4: Inserting in " + insertionPoint + " value=[" + insertThis + "]");
						derivedRawSource.insert(insertionPointAfterPostlude, insertThis);
					}
				}

			}
			else {
				// OK, this is an update: the node replaces the one that was parsed in this place
				P2PPNode<?, CT> slot = slots.get(parsedIndex++);
				if (DEBUG)
					System.out.println("Case 5: Replacing contents value=[" + childNode.getTextualRepresentation(context) + "]");
				derivedRawSource.replace(slot.getLastParsedFragment(), childNode.getTextualRepresentation(context));
				insertionPoint = slot.getLastParsedFragment().getEndPosition();
				insertionPointAfterPostlude = slot.getLastParsedFragment().getEndPosition();
				if (slot.getPostlude() != null) {
					insertionPointAfterPostlude = slot.getPostlude().getEndPosition();
				}
			}

			// Marks the node beeing handled
			nodesToBeRemoved.remove(childNode);
		}
		for (int i = 0; i < start; i++) {
			nodesToBeRemoved.remove(nodesInModelOrder.get(i));
		}

		for (P2PPNode<?, CT> removedNode : nodesToBeRemoved) {
			boolean wasFirst = (lastParsedNodes.get(0) == removedNode);
			boolean wasLast = (lastParsedNodes.get(lastParsedNodes.size() - 1) == removedNode);
			// boolean isFirst = (childrenObjectsList.get(0) == removedNode.getModelObject());
			// boolean isLast = (childrenObjectsList.get(childrenObjectsList.size() - 1) == removedNode.getModelObject());
			RawSourcePosition startPosition = removedNode.getLastParsedFragment().getStartPosition();
			RawSourcePosition endPosition = removedNode.getLastParsedFragment().getEndPosition();
			if (wasFirst && wasLast) {
				if (removedNode.getPrelude() != null) {
					startPosition = removedNode.getPrelude().getStartPosition();
				}
				if (removedNode.getPostlude() != null) {
					endPosition = removedNode.getPostlude().getEndPosition();
				}
				derivedRawSource.remove(startPosition.getOuterType().makeFragment(startPosition, endPosition));
			}
			else if (wasFirst) {
				// The first item has a prelude of its own, other than the one of the others (even an empty one: "" for "(a, b)"): the item
				// that becomes the first one takes it, so the prelude it had as a following item goes
				if (preludeForFirstItem != null && !preludeForFirstItem.equals(getPrelude()) && childrenObjectsList.size() > 0) {
					derivedRawSource.remove(startPosition.getOuterType().makeFragment(startPosition, endPosition));
					P2PPNode<?, CT> newFirstNode = getParentNode().getObjectNode(childrenObjectsList.get(0));
					if (newFirstNode.getPrelude() != null) {
						derivedRawSource.remove(newFirstNode.getPrelude());
					}
				}
				else {
					if (removedNode.getPrelude() != null) {
						startPosition = removedNode.getPrelude().getStartPosition();
					}
					if (removedNode.getPostlude() != null) {
						endPosition = removedNode.getPostlude().getEndPosition();
					}
					derivedRawSource.remove(startPosition.getOuterType().makeFragment(startPosition, endPosition));
				}
			}
			else if (wasLast) {
				// Same for the last item, whose postlude is not the one of the others ("" for "a; b; c")
				if (postludeForLastItem != null && !postludeForLastItem.equals(getPostlude()) && childrenObjectsList.size() > 0) {
					derivedRawSource.remove(startPosition.getOuterType().makeFragment(startPosition, endPosition));
					P2PPNode<?, CT> newLastNode = getParentNode().getObjectNode(childrenObjectsList.get(childrenObjectsList.size() - 1));
					if (newLastNode.getPostlude() != null) {
						derivedRawSource.remove(newLastNode.getPostlude());
					}
				}
				else {
					if (removedNode.getPrelude() != null) {
						startPosition = removedNode.getPrelude().getStartPosition();
					}
					if (removedNode.getPostlude() != null) {
						endPosition = removedNode.getPostlude().getEndPosition();
					}
					derivedRawSource.remove(startPosition.getOuterType().makeFragment(startPosition, endPosition));
				}
			}
			else {
				if (removedNode.getPrelude() != null) {
					startPosition = removedNode.getPrelude().getStartPosition();
				}
				if (removedNode.getPostlude() != null) {
					endPosition = removedNode.getPostlude().getEndPosition();
				}
				derivedRawSource.remove(startPosition.getOuterType().makeFragment(startPosition, endPosition));
			}

		}

		// Update parent default insertion point
		// getParentNode().setInsertionPoint(insertionPointAfterPostlude, this);
	}

	/**
	 * The whitespace that indents the row of a position, when only whitespace precedes the position on that row, null otherwise
	 */
	private static String sourceIndentation(RawSourcePosition position) {
		if (position == null || position.getLine() < 1) {
			return null;
		}
		String row = position.getOuterType().getRow(position.getLine() - 1);
		if (position.getPos() > row.length()) {
			return null;
		}
		String before = row.substring(0, position.getPos());
		return before.trim().isEmpty() ? before : null;
	}

	/**
	 * The indentation that new children must have, taken from the source: that of the first parsed child that stays, else that of the
	 * parent, one level deeper. Null when the source gives none (the context indentation is then applied).
	 */
	private String computeIndentString(List<P2PPNode<?, CT>> slots, PrettyPrintContext derivedContext) {
		if (!slots.isEmpty() && slots.get(0).getLastParsedFragment() != null) {
			String indentation = sourceIndentation(slots.get(0).getLastParsedFragment().getStartPosition());
			if (indentation != null) {
				return indentation;
			}
		}
		if (getParentNode().getLastParsedFragment() != null) {
			String indentation = sourceIndentation(getParentNode().getLastParsedFragment().getStartPosition());
			if (indentation != null) {
				return indentation + derivedContext.getResultingIndentation();
			}
		}
		return null;
	}

	/**
	 * Text of a new child. When the indentation of the source is known, the text is indented with it (every row of the child), instead of
	 * being indented by one level of the context, which is wrong as soon as the parent is not at the root.
	 */
	private String newChildText(P2PPNode<?, CT> childNode, PrettyPrintContext derivedContext, String indentString) {
		if (indentString == null) {
			return childNode.getTextualRepresentation(derivedContext);
		}
		String text = childNode.getTextualRepresentation(derivedContext.derive(Indentation.DoNotIndent));
		StringBuilder sb = new StringBuilder();
		String[] rows = text.split("\n", -1);
		for (int i = 0; i < rows.length; i++) {
			if (i > 0) {
				sb.append('\n');
			}
			if (!rows[i].trim().isEmpty()) {
				sb.append(indentString);
			}
			sb.append(rows[i]);
		}
		return sb.toString();
	}

	/**
	 * Insert new children before the first parsed one, which stays the first of the parsed ones (but is no longer the first child).
	 * <ul>
	 * <li>When the first child has no prelude of its own (the separators follow the items: "a;" "b;"), the new items go in front of the
	 * prelude of the parsed one, each with its prelude and postlude.</li>
	 * <li>When it has one that the others do not have ("(" and ", " in "(a, b)"), the prelude of the parsed one is now the one of the first
	 * new item, and the parsed one needs the prelude of the following items: the new items go in front of it, each followed by the
	 * separator that now precedes the next item.</li>
	 * </ul>
	 */
	private void insertBeforeFirstParsed(DerivedRawSource derivedRawSource, PrettyPrintContext derivedContext,
			String indentString, List<P2PPNode<?, CT>> newNodes, P2PPNode<?, CT> firstParsed) {
		String prelude = getPrelude() != null ? getPrelude() : "";
		String postlude = getPostlude() != null ? getPostlude() : "";
		boolean firstHasOwnPrelude = preludeForFirstItem != null && !preludeForFirstItem.equals(prelude);
		StringBuilder insertThis = new StringBuilder();
		RawSourcePosition insertAt;
		if (firstHasOwnPrelude) {
			insertAt = firstParsed.getLastParsedFragment().getStartPosition();
			for (int k = 0; k < newNodes.size(); k++) {
				if (k > 0) {
					insertThis.append(prelude);
				}
				insertThis.append(newChildText(newNodes.get(k), derivedContext, indentString)).append(postlude);
			}
			insertThis.append(prelude);
		}
		else {
			insertAt = firstParsed.getPrelude() != null ? firstParsed.getPrelude().getStartPosition()
					: firstParsed.getLastParsedFragment().getStartPosition();
			for (P2PPNode<?, CT> newNode : newNodes) {
				insertThis.append(prelude).append(newChildText(newNode, derivedContext, indentString)).append(postlude);
			}
		}
		derivedRawSource.insert(insertAt, insertThis.toString());
	}

	@Override
	public void initializePrettyPrint(P2PPNode<?, ?> rootNode, PrettyPrintContext context) {
		handlePreludeAndPoslude(rootNode, context);
		List<? extends CT> allObjects = childrenObjectsSupplier.get();
		for (int i = 0; i < allObjects.size(); i++) {
			CT childObject = allObjects.get(i);
			P2PPNode<?, CT> childNode = getParentNode().getObjectNode(childObject);
			if (childNode != null) {
				childNode.initializePrettyPrint(rootNode, context.derive(getIndentation()));
				childNode.setRegisteredForContents(this);
			}
			else {
				logger.warning("Cannot find P2PPNode for object " + childObject);
			}
		}
	}

	/**
	 * Return list of all objects sorted by their position in the last parsed fragment
	 * 
	 * @return
	 */
	private List<? extends CT> getAllObjectsSortedByParsedPosition() {
		List<? extends CT> returned = new ArrayList<CT>(childrenObjectsSupplier.get());
		Collections.sort(returned, new Comparator<CT>() {
			@Override
			public int compare(CT o1, CT o2) {
				P2PPNode<?, CT> childNode1 = getParentNode().getObjectNode(o1);
				P2PPNode<?, CT> childNode2 = getParentNode().getObjectNode(o2);
				if (childNode1 == null && childNode2 == null) {
					return 0;
				}
				if (childNode1 == null) {
					return -1;
				}
				if (childNode2 == null) {
					return 1;
				}
				return childNode1.getLastParsedFragment().getStartPosition()
						.compareTo(childNode2.getLastParsedFragment().getStartPosition());
			}
		});
		return returned;
	}

	private void handlePreludeAndPoslude(P2PPNode<?, ?> rootNode, PrettyPrintContext context) {
		// List<? extends CT> allObjects = childrenObjectsSupplier.get();
		List<? extends CT> allObjects = getAllObjectsSortedByParsedPosition();
		for (int i = 0; i < allObjects.size(); i++) {
			CT childObject = allObjects.get(i);
			String applicablePrelude = getPrelude();
			if (i == 0 && preludeForFirstItem != null) {
				applicablePrelude = preludeForFirstItem;
			}
			if (StringUtils.isEmpty(applicablePrelude) && context.getIndentation() != Indentation.DoNotIndent) {
				applicablePrelude = context.getResultingIndentation();
			}

			String applicablePostlude = getPostlude();
			if (i == allObjects.size() - 1 && postludeForLastItem != null) {
				applicablePostlude = postludeForLastItem;
			}

			P2PPNode<?, CT> childNode = getParentNode().getObjectNode(childObject);

			if (childNode != null) {
				// An indented child has as prelude the indentation of its row, whatever its depth (not one level of the context)
				String sourceIndentation = null;
				if (StringUtils.isEmpty(getPrelude()) && (i > 0 || StringUtils.isEmpty(preludeForFirstItem))
						&& context.getIndentation() != Indentation.DoNotIndent && childNode.getLastParsedFragment() != null) {
					sourceIndentation = sourceIndentation(childNode.getLastParsedFragment().getStartPosition());
				}
				if (sourceIndentation != null && sourceIndentation.length() > 0) {
					RawSourcePosition start = childNode.getLastParsedFragment().getStartPosition();
					childNode.prelude = start.getOuterType().makeFragment(start.decrement(sourceIndentation.length()), start);
				}
				else if (StringUtils.isNotEmpty(applicablePrelude)) {
					childNode.tryToIdentifyPrelude(applicablePrelude, rootNode);
				}
				if (StringUtils.isNotEmpty(applicablePostlude)) {
					childNode.tryToIdentifyPostlude(applicablePostlude, rootNode);
				}
			}
			else {
				logger.warning("Cannot find P2PPNode for object " + childObject);
			}
		}

	}

	/*@Override
	protected void debug(StringBuffer sb, int identation) {
		String indent = StringUtils.buildWhiteSpaceIndentation(identation * 2);
		sb.append(indent + "> " + getClass().getSimpleName() + "[" + getIdentifier() + "]" + " fragment=" + getFragment() + "["
				+ (getFragment() != null ? getFragment().getRawText() : "?") + "] prelude=" + getPreludeFragment() + " postlude="
				+ getPostludeFragment() + " insertionPoint=" + getInsertionPoint() + "\n");
	}*/

}
