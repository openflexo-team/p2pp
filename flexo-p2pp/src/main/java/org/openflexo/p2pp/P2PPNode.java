/**
 * 
 * Copyright (c) 2019, Openflexo
 * 
 * This file is part of flexo-p2pp, a component of the software infrastructure 
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
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.openflexo.p2pp.PrettyPrintContext.Indentation;
import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.p2pp.RawSource.RawSourcePosition;
import org.openflexo.toolbox.StringUtils;

/**
 * Parser/PrettyPrinter node<br>
 * 
 * Maintains consistency between the model (a pretty-printable object) and source code represented in a textual language
 * 
 * Manage normalized textual representation
 * 
 * Manage a String representing textual representation as the merge of:
 * <ul>
 * <li>Last parsed version (as it was in the original source file)</li>
 * <li>Eventual model modifications</li>
 * </ul>
 * 
 * @author sylvain
 * 
 * @param <N>
 *            Type of AST node
 * @param <T>
 *            General type of pretty-printable object
 */
public abstract class P2PPNode<N, T> {

	private static final Logger logger = Logger.getLogger(P2PPNode.class.getPackage().getName());

	private N astNode;
	protected T modelObject;

	private P2PPNode<?, ?> parent;
	private List<P2PPNode<?, ?>> children = new ArrayList<>();

	protected RawSourcePosition startPosition;
	protected RawSourcePosition endPosition;
	protected RawSourceFragment parsedFragment;
	protected RawSourceFragment prelude;
	protected RawSourceFragment postlude;

	private List<PrettyPrintableContents<N, T>> ppContents = new ArrayList<>();

	// Indicates that this P2PPNode was registered in parent P2PPNode relatively to that contents
	private PrettyPrintableContents<?, ?> registeredForContents;

	public static final String SPACE = " ";
	public static final String DOUBLE_SPACE = SPACE + SPACE;
	public static final String LINE_SEPARATOR = "\n";

	public P2PPNode(T aModelObject, N astNode, FragmentRetriever<?> fragmentRetriever) {
		this.astNode = astNode;
		this.modelObject = aModelObject;

		if (astNode != null && fragmentRetriever != null) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			RawSourceFragment fragment = ((FragmentRetriever) fragmentRetriever).retrieveFragment(astNode);
			setStartPosition(fragment.getStartPosition());
			setEndPosition(fragment.getEndPosition());
		}
	}

	/**
	 * Return underlying model object: object being represented by referenced AST node through this node
	 * 
	 * @return
	 */
	public T getModelObject() {
		return modelObject;
	}

	public void setModelObject(T modelObject) {
		this.modelObject = modelObject;
	}

	protected void addToChildren(P2PPNode<?, ?> child, int index) {
		child.parent = this;
		children.add(index, child);
	}

	public List<PrettyPrintableContents<N, T>> getPPContents() {
		return ppContents;
	}

	protected void addToChildren(P2PPNode<?, ?> child) {
		child.parent = this;
		if (!children.contains(child)) {
			children.add(child);
		}
	}

	public PrettyPrintableContents<?, ?> getRegisteredForContents() {
		return registeredForContents;
	}

	protected void setRegisteredForContents(PrettyPrintableContents<?, ?> registeredForContents) {
		this.registeredForContents = registeredForContents;
	}

	public N getASTNode() {
		return astNode;
	}

	public P2PPNode<?, ?> getParent() {
		return parent;
	}

	public List<P2PPNode<?, ?>> getChildren() {
		return children;
	}

	public int getIndex() {
		return parent.children.indexOf(this);
	}

	public abstract T buildModelObjectFromAST(N astNode);

	public abstract P2PPNode<N, T> deserialize();

	/**
	 * Called at the end (after all types resolution)
	 */
	public void finalizeDeserialization() {
		// Override when required
	}

	/**
	 * Return original version of last serialized raw source, FOR THE ENTIRE compilation unit
	 * 
	 * @return
	 */
	public abstract RawSource getRawSource();

	/**
	 * Return starting position of RawSource, where underlying model object is textually serialized, inclusive
	 * 
	 * @return
	 */
	public RawSourcePosition getStartPosition() {
		return startPosition;
	}

	public void setStartPosition(RawSourcePosition startPosition) {
		this.startPosition = startPosition;
		parsedFragment = null;
	}

	/**
	 * Return end position of RawSource, where underlying model object is textually serialized, inclusive
	 * 
	 * @return
	 */
	public RawSourcePosition getEndPosition() {
		return endPosition;
	}

	public void setEndPosition(RawSourcePosition endPosition) {
		this.endPosition = endPosition;
		parsedFragment = null;
	}

	/**
	 * Return fragment representing underlying model object as a String in textual language, as it was last parsed
	 * 
	 * @return
	 */
	public RawSourceFragment getLastParsedFragment() {
		if (parsedFragment == null && getStartPosition() != null && getEndPosition() != null) {
			parsedFragment = getRawSource().makeFragment(getStartPosition(), getEndPosition());
		}
		return parsedFragment;
	}

	public RawSourceFragment getPrelude() {
		return prelude;
	}

	public RawSourceFragment getPostlude() {
		return postlude;
	}

	/**
	 * Build and return a new pretty-print context
	 * 
	 * @return
	 */
	public PrettyPrintContext makePrettyPrintContext() {
		return new DefaultPrettyPrintContext(Indentation.DoNotIndent);
	}

	/**
	 * Initialize pretty print features while recursively calling {@link #preparePrettyPrint(boolean)} for the whole {@link P2PPNode}
	 * hierarchy
	 * 
	 * @param rootNode
	 * @param context
	 */
	public final void initializePrettyPrint(P2PPNode<?, ?> rootNode, PrettyPrintContext context) {
		preparePrettyPrint(getASTNode() != null);

		for (PrettyPrintableContents<N, T> prettyPrintableContents : new ArrayList<>(ppContents)) {
			prettyPrintableContents.initializePrettyPrint(rootNode, context.derive(prettyPrintableContents.getIndentation()));
		}

	}

	/**
	 * This method is called to set the specifications of the pretty-print to apply for this node<br>
	 * This is here a trivial initialization and this method should be overriden
	 * 
	 * @param hasParsedVersion
	 */
	protected void preparePrettyPrint(boolean hasParsedVersion) {
		defaultInsertionPoint = getStartPosition();
	}

	/**
	 * Return normalized textual representation for this node
	 * 
	 * @param context
	 * @return
	 */
	public final String getNormalizedTextualRepresentation(PrettyPrintContext context) {
		StringBuffer sb = new StringBuffer();
		for (PrettyPrintableContents<N, T> child : new ArrayList<>(ppContents)) {
			String normalizedPP = child.getNormalizedPrettyPrint(context);
			if (normalizedPP != null) {
				sb.append(normalizedPP);
			}
		}
		return context.indent(sb.toString());
	}

	private RawSourcePosition defaultInsertionPoint;

	public RawSourcePosition getDefaultInsertionPoint() {
		if (defaultInsertionPoint == null) {
			if (getParent() != null) {
				return getParent().getDefaultInsertionPoint();
			}
		}
		return defaultInsertionPoint;
	}

	/**
	 * Make new {@link StaticContents}, whose value is intended to reference a static text (a keyword or a known litteral)
	 * 
	 * @param staticContents
	 * @return
	 */
	public StaticContents<N, T> staticContents(String staticContents) {
		return new StaticContents<N, T>(this, null, staticContents, null);
	}

	/**
	 * Make new {@link StaticContents}, whose value is intended to reference a static text (a keyword or a known litteral)
	 * 
	 * @param prelude
	 *            A String to append before the static contents
	 * @param staticContents
	 *            The String to serialize (the keyword or known litteral)
	 * @param postlude
	 *            A String to append after the static contents
	 * @return
	 */
	public StaticContents<N, T> staticContents(String prelude, String staticContents, String postlude) {
		return new StaticContents<N, T>(this, prelude, staticContents, postlude, null);
	}

	/**
	 * Make new {@link DynamicContents}, whose value is intended to serialize a dynamic (variable or computed) text
	 * 
	 * @param stringRepresentationSupplier
	 *            gives dynamic value of that contents
	 * @return
	 */
	public DynamicContents<N, T> dynamicContents(Supplier<String> stringRepresentationSupplier) {
		return new DynamicContents<N, T>(this, null, stringRepresentationSupplier, null, null);
	}

	/**
	 * Make new {@link DynamicContents}, whose value is intended to serialize a dynamic (variable or computed) text
	 * 
	 * @param prelude
	 *            A String to append before the dynamic contents
	 * @param stringRepresentationSupplier
	 * @return
	 */
	public DynamicContents<N, T> dynamicContents(String prelude, Supplier<String> stringRepresentationSupplier) {
		return new DynamicContents<N, T>(this, prelude, stringRepresentationSupplier, null, null);
	}

	/**
	 * Make new {@link DynamicContents}, whose value is intended to serialize a dynamic (variable or computed) text
	 * 
	 * @param stringRepresentationSupplier
	 * @param postlude
	 *            A String to append after the dynamic contents
	 * @return
	 */
	public DynamicContents<N, T> dynamicContents(Supplier<String> stringRepresentationSupplier, String postlude) {
		return new DynamicContents<N, T>(this, null, stringRepresentationSupplier, postlude, null);
	}

	/**
	 * Make new {@link DynamicContents}, whose value is intended to serialize a dynamic (variable or computed) text
	 * 
	 * @param prelude
	 *            A String to append before the dynamic contents
	 * @param stringRepresentationSupplier
	 * @param postlude
	 *            A String to append after the dynamic contents
	 * @return
	 */
	public DynamicContents<N, T> dynamicContents(String prelude, Supplier<String> stringRepresentationSupplier, String postlude) {
		return new DynamicContents<N, T>(this, prelude, stringRepresentationSupplier, postlude, null);
	}

	/**
	 * Make new {@link ChildContents}, indicating that a child referenced by the supplier must be serialized at this pretty-print level
	 * 
	 * @param <CN>
	 * @param <CT>
	 * @param prelude
	 *            A String to append before serialized object
	 * @param childObjectSupplier
	 *            Supply object to be serialized here
	 * @param postude
	 *            A String to append after serialized object
	 * @param indentation
	 *            Indentation for the serialization of children
	 * @param identifier
	 *            Identifier used in debug
	 * @return
	 */
	public <CN, CT> ChildContents<N, T, CN, CT> childContents(String prelude, Supplier<CT> childObjectSupplier, String postude,
			Indentation indentation, String identifier) {
		ChildContents<N, T, CN, CT> returned = new ChildContents<N, T, CN, CT>(this, prelude, childObjectSupplier, postude, indentation);
		returned.setIdentifier(identifier);
		return returned;
	}

	/**
	 * Make new {@link ChildContents}, indicating that a child referenced by the supplier must be serialized at this pretty-print level
	 * 
	 * @param prelude
	 *            A String to append before serialized object
	 * @param childObjectSupplier
	 *            Supply object to be serialized here
	 * @param postude
	 *            A String to append after serialized object
	 * @param indentation
	 *            Indentation for the serialization of children
	 * @return
	 */
	public <CN, CT> ChildContents<N, T, CN, CT> childContents(String prelude, Supplier<CT> childObjectSupplier, String postude,
			Indentation indentation) {
		return new ChildContents<N, T, CN, CT>(this, prelude, childObjectSupplier, postude, indentation);
	}

	/**
	 * Make new {@link ChildrenContents}, indicating that some children referenced by the supplier must be serialized at this pretty-print
	 * level
	 * 
	 * @param <C>
	 * @param preludeForFirstItem
	 *            A String to append before the first object of the list
	 * @param prelude
	 *            A String to append for each object of the list except the first one
	 * @param childrenObjects
	 *            Supply all objects to be serialized here
	 * @param postude
	 *            A String to append after each object of the list except the last one
	 * @param postludeForLastItem
	 *            A String to append after last object of the list
	 * @param indentation
	 *            Indentation for the serialization of children
	 * @param childrenType
	 *            Type (class) of addressed children
	 * @return
	 */
	public <CN, CT> ChildrenContents<N, T, CN, CT> childrenContents(String preludeForFirstItem, String prelude,
			Supplier<List<? extends CT>> childrenObjects, String postude, String postludeForLastItem, Indentation indentation,
			Class<CT> childrenType) {

		return new ChildrenContents<N, T, CN, CT>(this, preludeForFirstItem, prelude, childrenObjects, postude, postludeForLastItem,
				indentation, childrenType);
	}

	/**
	 * Make new {@link ChildrenContents}, indicating that some children referenced by the supplier must be serialized at this pretty-print
	 * level
	 * 
	 * @param <C>
	 * @param prelude
	 *            A String to append for each object of the list
	 * @param childrenObjects
	 *            Supply all objects to be serialized here
	 * @param postude
	 *            A String to append after each object of the list
	 * @param indentation
	 *            Indentation for the serialization of children
	 * @param childrenType
	 *            Type (class) of addressed children
	 * @return
	 */
	public <CN, CT> ChildrenContents<N, T, CN, CT> childrenContents(String prelude, Supplier<List<? extends CT>> childrenObjects,
			String postude, Indentation indentation, Class<CT> childrenType) {

		return new ChildrenContents<N, T, CN, CT>(this, prelude, childrenObjects, postude, indentation, childrenType);
	}

	/**
	 * Make new {@link ChildrenContents}, indicating that some children referenced by the supplier must be serialized at this pretty-print
	 * level
	 * 
	 * @param <C>
	 * @param preludeForFirstItem
	 *            A String to append before the first object of the list
	 * @param prelude
	 *            A String to append for each object of the list except the first one
	 * @param childrenObjects
	 *            Supply all objects to be serialized here
	 * @param postude
	 *            A String to append after each object of the list except the last one
	 * @param postludeForLastItem
	 *            A String to append after last object of the list
	 * @param indentation
	 *            Indentation for the serialization of children
	 * @param childrenType
	 *            Type (class) of addressed children
	 * @param identifier
	 *            Identifier used in debug
	 * @return
	 */
	public <CN, CT> ChildrenContents<N, T, CN, CT> childrenContents(String preludeForFirstItem, String prelude,
			Supplier<List<? extends CT>> childrenObjects, String postude, String postludeForLastItem, Indentation indentation,
			Class<CT> childrenType, String identifier) {

		ChildrenContents<N, T, CN, CT> returned = new ChildrenContents<N, T, CN, CT>(this, preludeForFirstItem, prelude, childrenObjects,
				postude, postludeForLastItem, indentation, childrenType);
		returned.setIdentifier(identifier);
		return returned;
	}

	/**
	 * Make new {@link ChildrenContents}, indicating that some children referenced by the supplier must be serialized at this pretty-print
	 * level
	 * 
	 * @param <C>
	 * @param prelude
	 *            A String to append for each object of the list
	 * @param childrenObjects
	 *            Supply all objects to be serialized here
	 * @param postude
	 *            A String to append after each object of the list
	 * @param indentation
	 *            Indentation for the serialization of children
	 * @param childrenType
	 *            Type (class) of addressed children
	 * @param identifier
	 *            Identifier used in debug
	 * @return
	 */
	public <CN, CT> ChildrenContents<N, T, CN, CT> childrenContents(String prelude, Supplier<List<? extends CT>> childrenObjects,
			String postude, Indentation indentation, Class<CT> childrenType, String identifier) {

		ChildrenContents<N, T, CN, CT> returned = new ChildrenContents<N, T, CN, CT>(this, prelude, childrenObjects, postude, indentation,
				childrenType);
		returned.setIdentifier(identifier);
		return returned;
	}

	/**
	 * Sequentially append supplied {@link PrettyPrintableContents}, declaring a contents to serialize at this point
	 * 
	 * @param contents
	 *            contents to serialize
	 * @param fragment
	 *            current serialized fragment in original textual version, not null if contents was parsed
	 * @return supplied contents, for cascading calls
	 */
	public <PPC extends PrettyPrintableContents<N, T>> PPC append(PPC contents, RawSourceFragment fragment) {
		/*if (fragment == null) {
			fragment = defaultInsertionPoint != null
					? defaultInsertionPoint.getOuterType().makeFragment(defaultInsertionPoint, defaultInsertionPoint)
					: null;
		}*/
		contents.setFragment(fragment);
		ppContents.add(contents);
		if (fragment != null) {
			if (contents.getPostludeFragment() != null) {
				// In this case, insertion point is after the postlude
				defaultInsertionPoint = contents.getPostludeFragment().getEndPosition();
			}
			else {
				defaultInsertionPoint = fragment.getEndPosition();
			}
		}
		return contents;
	}

	/**
	 * Sequentially append supplied {@link PrettyPrintableContents}, declaring a contents to serialize at this point
	 * 
	 * @param contents
	 *            contents to serialize
	 * @param fragment
	 *            current serialized fragment in original textual version, not null if contents was parsed
	 * @param identifier
	 *            Identifier used in debug
	 * @return supplied contents, for cascading calls
	 */
	public <PPC extends PrettyPrintableContents<N, T>> PPC append(PPC contents, RawSourceFragment fragment, String identifier) {
		PPC returned = append(contents, fragment);
		returned.setIdentifier(identifier);
		return returned;
	}

	/**
	 * Convenient method to append a {@link ChildContents} (fragment is not required in this case)
	 * 
	 * @param contents
	 * @return supplied contents, for cascading calls
	 */
	public <CN, CT> ChildContents<N, T, CN, CT> append(ChildContents<N, T, CN, CT> contents) {
		return append(contents, (RawSourceFragment) null);
	}

	/**
	 * Convenient method to append a {@link ChildContents} (fragment is not required in this case)
	 * 
	 * @param contents
	 * @param identifier
	 *            Identifier used in debug
	 * @return supplied contents, for cascading calls
	 */
	public <CN, CT> ChildContents<N, T, CN, CT> append(ChildContents<N, T, CN, CT> contents, String identifier) {
		return append(contents, null, identifier);
	}

	/**
	 * Convenient method to append a {@link ChildrenContents} (fragment is not required in this case)
	 * 
	 * @param contents
	 * @return supplied contents, for cascading calls
	 */
	public <CN, CT> ChildrenContents<N, T, CN, CT> append(ChildrenContents<N, T, CN, CT> contents) {
		return append(contents, (RawSourceFragment) null);
	}

	/**
	 * Convenient method to append a {@link ChildrenContents} (fragment is not required in this case)
	 * 
	 * @param contents
	 * @param identifier
	 *            Identifier used in debug
	 * @return supplied contents, for cascading calls
	 */
	public <CN, CT> ChildrenContents<N, T, CN, CT> append(ChildrenContents<N, T, CN, CT> contents, String identifier) {
		return append(contents, null, identifier);
	}

	/**
	 * Declare and append a new sequence block
	 * 
	 * @return
	 */
	public SequentialContents<N, T> appendBlock() {
		SequentialContents<N, T> sequence = new SequentialContents<>(this);
		ppContents.add(sequence);
		return sequence;
	}

	/**
	 * Declare and append a new conditional contents
	 * 
	 * @param conditionSupplier
	 *            determines the condition to compute at run-time
	 * @param identifier
	 *            Identifier used in debug
	 * @return
	 */
	public ConditionalContents<N, T> when(Supplier<Boolean> conditionSupplier, String identifier) {
		ConditionalContents<N, T> returned = when(conditionSupplier);
		returned.setIdentifier(identifier);
		return returned;
	}

	/**
	 * Declare and append a new conditional contents
	 * 
	 * @param conditionSupplier
	 *            determines the condition to compute at run-time
	 * @return
	 */
	public ConditionalContents<N, T> when(Supplier<Boolean> conditionSupplier) {
		ConditionalContents<N, T> conditionalContents = new ConditionalContents<N, T>(this, conditionSupplier);
		ppContents.add(conditionalContents);
		return conditionalContents;
	}

	/**
	 * Append {@link StaticContents}, whose value is intended to replace text determined with supplied fragment
	 * 
	 * @param staticContents
	 *            value to append
	 * @param fragment
	 */
	@Deprecated
	public void appendStaticContents(String staticContents, RawSourceFragment fragment) {
		// boolean suppliedFragment = (fragment != null);
		if (fragment == null) {
			fragment = defaultInsertionPoint != null
					? defaultInsertionPoint.getOuterType().makeFragment(defaultInsertionPoint, defaultInsertionPoint)
					: null;
		}
		StaticContents<N, T> newContents = new StaticContents<N, T>(this, null, staticContents, null, fragment);
		ppContents.add(newContents);
		if (fragment != null) {
			defaultInsertionPoint = fragment.getEndPosition();
		}
	}

	/**
	 * Append {@link StaticContents}, whose value is intended to replace text determined with supplied fragment
	 * 
	 * @param prelude
	 *            prelude to add if normalized pretty-print is to be applied
	 * @param staticContents
	 *            value to append
	 * @param fragment
	 */
	@Deprecated
	public void appendStaticContents(String prelude, String staticContents, RawSourceFragment fragment) {
		if (fragment == null) {
			fragment = defaultInsertionPoint != null
					? defaultInsertionPoint.getOuterType().makeFragment(defaultInsertionPoint, defaultInsertionPoint)
					: null;
		}
		StaticContents<N, T> newContents = new StaticContents<N, T>(this, prelude, staticContents, null, fragment);
		ppContents.add(newContents);
		if (fragment != null) {
			defaultInsertionPoint = fragment.getEndPosition();
		}
	}

	/**
	 * Append {@link StaticContents}, whose value is intended to replace text determined with supplied fragment
	 * 
	 * @param prelude
	 *            prelude to add if normalized pretty-print is to be applied
	 * @param staticContents
	 *            value to append
	 * @param postlude
	 *            postlude to add if normalized pretty-print is to be applied
	 * @param fragment
	 */
	@Deprecated
	public void appendStaticContents(String prelude, String staticContents, String postlude, RawSourceFragment fragment) {
		if (fragment == null) {
			fragment = defaultInsertionPoint != null
					? defaultInsertionPoint.getOuterType().makeFragment(defaultInsertionPoint, defaultInsertionPoint)
					: null;
		}
		StaticContents<N, T> newContents = new StaticContents<N, T>(this, prelude, staticContents, postlude, fragment);
		ppContents.add(newContents);
		if (fragment != null) {
			defaultInsertionPoint = fragment.getEndPosition();
		}
	}

	/**
	 * Append {@link DynamicContents}, whose value is intended to replace text determined with supplied fragment
	 * 
	 * @param stringRepresentationSupplier
	 *            gives dynamic value of that contents
	 * @param fragment
	 */
	@Deprecated
	public void appendDynamicContents(Supplier<String> stringRepresentationSupplier, RawSourceFragment fragment) {
		if (fragment == null) {
			fragment = defaultInsertionPoint != null
					? defaultInsertionPoint.getOuterType().makeFragment(defaultInsertionPoint, defaultInsertionPoint)
					: null;
		}
		DynamicContents<N, T> newContents = new DynamicContents<N, T>(this, null, stringRepresentationSupplier, null, fragment);
		ppContents.add(newContents);
		if (fragment != null) {
			defaultInsertionPoint = fragment.getEndPosition();
		}
	}

	/**
	 * Append {@link DynamicContents}, whose value is intended to replace text determined with supplied fragment
	 * 
	 * @param prelude
	 * @param stringRepresentationSupplier
	 *            gives dynamic value of that contents
	 * @param fragment
	 */
	@Deprecated
	public void appendDynamicContents(String prelude, Supplier<String> stringRepresentationSupplier, RawSourceFragment fragment) {
		if (fragment == null) {
			fragment = defaultInsertionPoint != null
					? defaultInsertionPoint.getOuterType().makeFragment(defaultInsertionPoint, defaultInsertionPoint)
					: null;
		}
		DynamicContents<N, T> newContents = new DynamicContents<N, T>(this, prelude, stringRepresentationSupplier, null, fragment);
		ppContents.add(newContents);
		if (fragment != null) {
			defaultInsertionPoint = fragment.getEndPosition();
		}
	}

	/**
	 * Append {@link DynamicContents}, whose value is intended to replace text determined with supplied fragment
	 * 
	 * @param stringRepresentationSupplier
	 *            gives dynamic value of that contents
	 * @param postlude
	 * @param fragment
	 */
	@Deprecated
	public void appendDynamicContents(Supplier<String> stringRepresentationSupplier, String postlude, RawSourceFragment fragment) {
		if (fragment == null) {
			fragment = defaultInsertionPoint != null
					? defaultInsertionPoint.getOuterType().makeFragment(defaultInsertionPoint, defaultInsertionPoint)
					: null;
		}
		DynamicContents<N, T> newContents = new DynamicContents<N, T>(this, null, stringRepresentationSupplier, postlude, fragment);
		ppContents.add(newContents);
		if (fragment != null) {
			defaultInsertionPoint = fragment.getEndPosition();
		}
	}

	/**
	 * Append {@link ChildContents} managing pretty-print for supplied childObject<br>
	 * Either this object is already serialized, or should be created
	 * 
	 * @param prelude
	 * @param childObject
	 * @param postude
	 * @param relativeIndentation
	 *            <ul>
	 *            <li>When relativeIndentation is zero, keep current indentation</li>
	 *            <li>When relativeIndentation is positive, increment current indentation with that value</li>
	 *            <li>When relativeIndentation is negative (-1), discard current indentation</li>
	 *            </ul>
	 */
	@Deprecated
	public <CN, CT> ChildContents<N, T, CN, CT> appendToChildPrettyPrintContents(String prelude, Supplier<CT> childObjectSupplier,
			String postude, Indentation indentation) {

		ChildContents<N, T, CN, CT> newChildContents = new ChildContents<N, T, CN, CT>(this, prelude, childObjectSupplier, postude,
				indentation);
		ppContents.add(newChildContents);
		return newChildContents;
	}

	/**
	 * Called to indicate that supplied childObject must be serialized at this pretty-print level<br>
	 * Either this object is already serialized, or should be created
	 * 
	 * This method is used for example to render a vertical layout in pretty-printed text
	 * 
	 * @param prelude
	 * @param childrenObjects
	 * @param postude
	 * @param relativeIndentation
	 *            <ul>
	 *            <li>When relativeIndentation is zero, keep current indentation</li>
	 *            <li>When relativeIndentation is positive, increment current indentation with that value</li>
	 *            <li>When relativeIndentation is negative (-1), discard current indentation</li>
	 *            </ul>
	 * @param childrenType
	 * @return
	 */
	@Deprecated
	public <CN, CT> ChildrenContents<N, T, CN, CT> appendToChildrenPrettyPrintContents(String prelude,
			Supplier<List<? extends CT>> childrenObjects, String postude, Indentation indentation, Class<CT> childrenType) {

		ChildrenContents<N, T, CN, CT> newChildrenContents = new ChildrenContents<N, T, CN, CT>(this, prelude, childrenObjects, postude,
				indentation, childrenType);
		ppContents.add(newChildrenContents);
		return newChildrenContents;
	}

	/**
	 * Called to indicate that supplied childObject must be serialized at this pretty-print level<br>
	 * Either this object is already serialized, or should be created
	 * 
	 * This method is used for example to render an horizontal layout in pretty-printed text (a comma-separated list for example)
	 * 
	 * @param preludeForFirstItem
	 * @param prelude
	 * @param childrenObjects
	 * @param postude
	 * @param postludeForLastItem
	 * @param relativeIndentation
	 *            <ul>
	 *            <li>When relativeIndentation is zero, keep current indentation</li>
	 *            <li>When relativeIndentation is positive, increment current indentation with that value</li>
	 *            <li>When relativeIndentation is negative (-1), discard current indentation</li>
	 *            </ul>
	 * @param childrenType
	 * @return
	 */
	@Deprecated
	public <CN, CT> ChildrenContents<N, T, CN, CT> appendToChildrenPrettyPrintContents(String preludeForFirstItem, String prelude,
			Supplier<List<? extends CT>> childrenObjects, String postude, String postludeForLastItem, Indentation indentation,
			Class<CT> childrenType) {

		ChildrenContents<N, T, CN, CT> newChildrenContents = new ChildrenContents<N, T, CN, CT>(this, preludeForFirstItem, prelude,
				childrenObjects, postude, postludeForLastItem, indentation, childrenType);
		ppContents.add(newChildrenContents);
		return newChildrenContents;
	}

	/**
	 * Called to indicate that supplied childObject must be serialized at this pretty-print level<br>
	 * Either this object is already serialized, or should be created
	 * 
	 * This method is used for example to render an horizontal layout in pretty-printed text (a comma-separated list for example)
	 * 
	 * @param preludeForFirstItem
	 * @param prelude
	 * @param childrenObjects
	 * @param postude
	 * @param postludeForLastItem
	 * @param childrenType
	 * @return
	 */
	@Deprecated
	public <CN, CT> ChildrenContents<N, T, CN, CT> appendToChildrenPrettyPrintContents(String preludeForFirstItem, String prelude,
			Supplier<List<? extends CT>> childrenObjects, String postude, String postludeForLastItem, Class<CT> childrenType) {

		return appendToChildrenPrettyPrintContents(preludeForFirstItem, prelude, childrenObjects, postude, postludeForLastItem,
				Indentation.DoNotIndent, childrenType);
	}

	/**
	 * Return textual representation for underlying object
	 * 
	 * @param context
	 * @return
	 */
	public String getTextualRepresentation(PrettyPrintContext context) {
		// TODO: implement a cache !!!!

		if (getASTNode() == null) {
			return getNormalizedTextualRepresentation(context);
		}

		DerivedRawSource derivedRawSource = computeTextualRepresentation(context);
		return derivedRawSource.getStringRepresentation();
	}

	/**
	 * Computes and return a {@link DerivedRawSource} representing textual representation as the merge of:
	 * <ul>
	 * <li>Last parsed version (as it was in the original source file)</li>
	 * <li>Eventual model modifications</li>
	 * </ul>
	 * 
	 * @return
	 */
	protected DerivedRawSource computeTextualRepresentation(PrettyPrintContext context) {

		DerivedRawSource derivedRawSource = new DerivedRawSource(getLastParsedFragment());

		if (getModelObject() == null) {
			logger.warning("Unexpected null model object in " + this);
			return derivedRawSource;
		}

		for (PrettyPrintableContents<N, T> prettyPrintableContents : new ArrayList<>(ppContents)) {
			prettyPrintableContents.updatePrettyPrint(derivedRawSource, context);
		}

		return derivedRawSource;

	}

	/**
	 * Build new {@link P2PPNode} representing supplied pretty-printable object
	 * 
	 * @param object
	 *            Pretty-printable object
	 * @return
	 */
	public abstract <C> P2PPNode<?, C> makeObjectNode(C object);

	/**
	 * Return {@link P2PPNode} representing supplied pretty-printable object, when it exists
	 * 
	 * @param object
	 *            Pretty-printable object
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <C> P2PPNode<?, C> getObjectNode(C object) {
		if (getModelObject() == object) {
			return (P2PPNode<?, C>) this;
		}
		for (P2PPNode<?, ?> objectNode : getChildren()) {
			P2PPNode<?, C> returned = objectNode.getObjectNode(object);
			if (returned != null) {
				return returned;
			}
		}
		return null;
	}

	protected RawSourceFragment tryToIdentifyPrelude(String expectedPrelude, P2PPNode<?, ?> rootNode) {
		if (getLastParsedFragment() == null) {
			return null;
		}
		if (StringUtils.isEmpty(expectedPrelude)) {
			return null;
		}
		// System.out.println("On " + getClass().getSimpleName() + " trying to identify prelude [" + expectedPrelude + "]");
		prelude = findUnmappedSegmentBackwardFrom(expectedPrelude, getLastParsedFragment().getStartPosition(), rootNode);
		if (prelude == null) {
			// Try to find after trimming
			// System.out.println("cannot find, looking for [" + expectedPrelude.trim() + "]");
			prelude = findUnmappedSegmentBackwardFrom(expectedPrelude.trim(), getLastParsedFragment().getStartPosition(), rootNode);
		}
		if (prelude != null && prelude.getStartPosition().isBefore(getParent().getStartPosition())) {
			getParent().setStartPosition(prelude.getStartPosition());
		}
		// System.out.println("Finally found " + prelude);
		// if (prelude != null) {
		// System.out.println("RawText: [" + prelude.getRawText() + "]");
		// }
		return prelude;
	}

	protected RawSourceFragment tryToIdentifyPostlude(String expectedPostlude, P2PPNode<?, ?> rootNode) {
		if (getLastParsedFragment() == null) {
			return null;
		}
		if (StringUtils.isEmpty(expectedPostlude)) {
			return null;
		}
		// System.out.println("On " + getClass().getSimpleName() + " trying to identify postlude [" + expectedPostlude + "]");
		postlude = findUnmappedSegmentForwardFrom(expectedPostlude, getLastParsedFragment().getEndPosition(), rootNode);
		if (postlude == null) {
			// Try to find after trimming
			// System.out.println("cannot find, looking for [" + expectedPrelude.trim() + "]");
			postlude = findUnmappedSegmentForwardFrom(expectedPostlude.trim(), getLastParsedFragment().getEndPosition(), rootNode);
		}
		if (postlude != null && postlude.getEndPosition().isAfter(getParent().getEndPosition())) {
			getParent().setEndPosition(postlude.getEndPosition());
		}
		// System.out.println("Finally found " + postlude);
		// if (postlude != null) {
		// System.out.println("RawText: [" + postlude.getRawText() + "]");
		// }
		return postlude;
	}

	private static RawSourceFragment findUnmappedSegmentBackwardFrom(String expected, RawSourcePosition position, P2PPNode<?, ?> rootNode) {
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
				if (rootNode.isFragmentMapped(f)) {
					// This fragment intersects another mapped fragment, abort
					return null;
				}
				if (f.getRawText().equals(expected)) {
					return f;
				}
				i++;
			} catch (ArrayIndexOutOfBoundsException e) {
				positionStillValid = false;
			}
		}
		return null;
	}

	private static RawSourceFragment findUnmappedSegmentForwardFrom(String expected, RawSourcePosition position, P2PPNode<?, ?> rootNode) {
		int length = expected.length();
		int i = 0;
		// System.out.println("Forward looking for [" + expected + "] from " + position);
		boolean positionStillValid = true;
		while (positionStillValid) {
			try {
				RawSourcePosition start = position.increment(i);
				RawSourcePosition end = position.increment(length + i);
				RawSourceFragment f = position.getOuterType().makeFragment(start, end);
				// System.out.println("Test forward fragment " + f + " [" + f.getRawText() + "]");
				if (rootNode.isFragmentMapped(f)) {
					// This fragment intersects another mapped fragment, abort
					return null;
				}
				if (f.getRawText().equals(expected)) {
					return f;
				}
				i++;
			} catch (ArrayIndexOutOfBoundsException e) {
				positionStillValid = false;
			}
		}
		return null;
	}

	/**
	 * Search if supplied fragment is entirely or partially mapped in any of this node's subtree, either by the lastParsedFragment, or the
	 * prelude, or the postlude
	 * 
	 * @param fragment
	 * @return
	 */
	public boolean isFragmentMapped(RawSourceFragment fragment) {
		if (getChildren().size() == 0) {
			// This is a leaf node
			if (fragment.intersects(getLastParsedFragment())) {
				return true;
			}
			if (getPrelude() != null && fragment.intersects(getPrelude())) {
				return true;
			}
			if (getPostlude() != null && fragment.intersects(getPostlude())) {
				return true;
			}
		}
		else {
			// Otherwise, look in children
			for (P2PPNode<?, ?> child : getChildren()) {
				if (child.isFragmentMapped(fragment)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Search if supplied fragment is mapped by {@link PrettyPrintableContents} in the context of this node (this method is really distinct
	 * from {@link #isFragmentMapped(RawSourceFragment)} where the scope is the whole {@link P2PPNode} hierarchy
	 * 
	 * @param fragment
	 * @return
	 */
	public boolean isFragmentMappedInPPContents(RawSourceFragment fragment) {
		for (PrettyPrintableContents<N, T> prettyPrintableContents : new ArrayList<>(ppContents)) {
			// System.out.println(" > PPContents " + prettyPrintableContents + " " + prettyPrintableContents.getFragment());
			// Do not consider control graph structures, but terminals only (because such structures may contains some non-significant
			// characters)
			if ((prettyPrintableContents != null) && (!(prettyPrintableContents instanceof SequentialContents))
					&& (!(prettyPrintableContents instanceof ConditionalContents))
					&& prettyPrintableContents.getExtendedFragmentNoRecomputation() != null
					&& prettyPrintableContents.getExtendedFragmentNoRecomputation().intersects(fragment)) {
				return true;
			}
		}
		return false;
	}

	public String debug() {
		StringBuffer sb = new StringBuffer();
		sb.append("DEBUG PrettyPrint for " + this + "\n");
		sb.append("fragment=" + getLastParsedFragment() + "\n");
		sb.append(getLastParsedFragment().getRawText() + "\n");

		for (PrettyPrintableContents<N, T> prettyPrintableContents : new ArrayList<>(ppContents)) {
			prettyPrintableContents.debug(sb, 2);
		}
		return sb.toString();
	}

	// TODO
	// Provide better implementation by researching in backward direction first occurence of prelude
	// while text is not associated by any semantics
	/*private void handlePreludeExtension(P2PPNode<?, T> node) {
		if (node.getLastParsedFragment() == null) {
			return;
		}
		if (getPrelude() == null || getPrelude().equals("")) {
			// Nothing to do
		}
		else if (getPrelude().equals(P2PPNode.LINE_SEPARATOR)) {
			// We go to previous line, when possible
			if (node.getStartPosition().canDecrement()) {
				node.setStartPosition(node.getStartPosition().decrement());
			}
		}
		else if (getPrelude().equals(",")) {
			System.out.println("Tiens c'est bon, j'ai ma virgule");
			if (node.getStartPosition().canDecrement()) {
				node.setStartPosition(node.getStartPosition().decrement());
			}
		}
	
		// Workaround to handle indentation: please do better here !!!
		if (getRelativeIndentation() == 1) {
			if (node.getStartPosition().canDecrement()) {
				node.setStartPosition(node.getStartPosition().decrement());
			}
		}
	}*/

}
