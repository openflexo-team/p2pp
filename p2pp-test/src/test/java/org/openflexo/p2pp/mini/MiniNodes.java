package org.openflexo.p2pp.mini;

import org.openflexo.p2pp.FragmentRetriever;
import org.openflexo.p2pp.P2PPNode;
import org.openflexo.p2pp.PrettyPrintContext.Indentation;
import org.openflexo.p2pp.RawSource;
import org.openflexo.p2pp.RawSource.RawSourceFragment;
import org.openflexo.p2pp.RawSource.RawSourcePosition;
import org.openflexo.p2pp.SequentialContents;
import org.openflexo.p2pp.mini.MiniModel.Annotation;
import org.openflexo.p2pp.mini.MiniModel.Concept;
import org.openflexo.p2pp.mini.MiniModel.Field;
import org.openflexo.p2pp.mini.MiniModel.Member;
import org.openflexo.p2pp.mini.MiniModel.Method;
import org.openflexo.p2pp.mini.MiniModel.Param;
import org.openflexo.p2pp.mini.MiniModel.TypeRef;
import org.openflexo.p2pp.mini.MiniModel.Unit;

/**
 * The {@link P2PPNode}s of Mini, written the way the FML and B ones are. One node class per model class; each construct of the language is
 * there to exercise one kind of contents:
 * <ul>
 * <li>static: keywords and punctuation</li>
 * <li>dynamic: names</li>
 * <li>conditional (and sequential, as soon as it has two contents): {@code abstract}, {@code extends B}</li>
 * <li>child: the type of a field</li>
 * <li>children: the concepts of a unit, the members of a concept (indented), the parameters of a method (comma-separated)</li>
 * </ul>
 */
public final class MiniNodes {

	private MiniNodes() {
	}

	/**
	 * Common part of all Mini nodes. A node is built either from an AST node (it was parsed: it knows its fragment of the source) or from a
	 * model object (it was created by a model change: it only knows its normalized pretty-print).
	 */
	public abstract static class MiniNode<T> extends P2PPNode<MiniAst, T> {

		private final RawSource rawSource;

		protected MiniNode(MiniAst ast, RawSource rawSource) {
			super(null, ast, (FragmentRetriever<MiniAst>) MiniAst::getFragment);
			this.rawSource = rawSource;
			this.modelObject = buildModelObjectFromAST(ast);
		}

		protected MiniNode(T modelObject, RawSource rawSource) {
			super(modelObject, null, null);
			this.rawSource = rawSource;
		}

		@Override
		public RawSource getRawSource() {
			return rawSource;
		}

		/** Fragment of the token playing that role in the AST, null when this node was not parsed or has no such token */
		protected RawSourceFragment frag(String role) {
			return getASTNode() != null ? getASTNode().fragmentOf(role) : null;
		}

		@Override
		public void addToChildren(P2PPNode<?, ?> child) {
			super.addToChildren(child);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <C> P2PPNode<?, C> makeObjectNode(C object) {
			MiniNode<?> returned;
			if (object instanceof Concept) {
				returned = new MiniConceptNode((Concept) object, rawSource);
			}
			else if (object instanceof Annotation) {
				returned = new MiniAnnotationNode((Annotation) object, rawSource);
			}
			else if (object instanceof Field) {
				returned = new MiniFieldNode((Field) object, rawSource);
			}
			else if (object instanceof Method) {
				returned = new MiniMethodNode((Method) object, rawSource);
			}
			else if (object instanceof Param) {
				returned = new MiniParamNode((Param) object, rawSource);
			}
			else if (object instanceof TypeRef) {
				returned = new MiniTypeRefNode((TypeRef) object, rawSource);
			}
			else {
				throw new IllegalArgumentException("No node for " + object);
			}
			return (P2PPNode<?, C>) returned;
		}

		/** Build the node of an AST node, and recursively those of its children */
		static MiniNode<?> build(MiniAst ast, RawSource rawSource, MiniNode<?> parent) {
			MiniNode<?> node;
			switch (ast.getKind()) {
				case "unit":
					node = new MiniUnitNode(ast, rawSource);
					break;
				case "concept":
					node = new MiniConceptNode(ast, rawSource);
					break;
				case "annotation":
					node = new MiniAnnotationNode(ast, rawSource);
					break;
				case "field":
					node = new MiniFieldNode(ast, rawSource);
					break;
				case "method":
					node = new MiniMethodNode(ast, rawSource);
					break;
				case "param":
					node = new MiniParamNode(ast, rawSource);
					break;
				case "type":
					node = new MiniTypeRefNode(ast, rawSource);
					break;
				default:
					throw new IllegalArgumentException("Unexpected AST node " + ast.getKind());
			}
			if (parent != null) {
				parent.addToChildren(node);
			}
			for (MiniAst child : ast.getChildren()) {
				build(child, rawSource, node);
			}
			node.deserialize();
			return node;
		}
	}

	public static class MiniUnitNode extends MiniNode<Unit> {

		public MiniUnitNode(MiniAst ast, RawSource rawSource) {
			super(ast, rawSource);
		}

		public MiniUnitNode(Unit unit, RawSource rawSource) {
			super(unit, rawSource);
			preparePrettyPrint(false);
		}

		@Override
		public Unit buildModelObjectFromAST(MiniAst ast) {
			return new Unit(ast.text("name"));
		}

		@Override
		public MiniUnitNode deserialize() {
			return this;
		}

		// The unit stands for the whole source: what precedes or follows it (comments) is kept by the derived pretty-print
		@Override
		public RawSourcePosition getStartPosition() {
			return getASTNode() != null && getRawSource() != null ? getRawSource().getStartPosition() : super.getStartPosition();
		}

		@Override
		public RawSourcePosition getEndPosition() {
			return getASTNode() != null && getRawSource() != null ? getRawSource().getEndPosition() : super.getEndPosition();
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			append(staticContents("", "unit", " "), frag("kw"));
			append(dynamicContents(() -> getModelObject().getName()), frag("name"));
			append(staticContents(" ", "{", LINE_SEPARATOR), frag("open"));
			append(childrenContents("", () -> getModelObject().getConcepts(), LINE_SEPARATOR, Indentation.Indent, Concept.class));
			append(staticContents("", "}", ""), frag("close"));
		}
	}

	public static class MiniConceptNode extends MiniNode<Concept> {

		public MiniConceptNode(MiniAst ast, RawSource rawSource) {
			super(ast, rawSource);
		}

		public MiniConceptNode(Concept concept, RawSource rawSource) {
			super(concept, rawSource);
			preparePrettyPrint(false);
		}

		@Override
		public Concept buildModelObjectFromAST(MiniAst ast) {
			Concept returned = new Concept(ast.text("name"));
			returned.setAbstract(ast.has("abstract"));
			returned.setParentName(ast.text("parent"));
			return returned;
		}

		@Override
		public MiniConceptNode deserialize() {
			((MiniUnitNode) getParent()).getModelObject().getConcepts().add(getModelObject());
			return this;
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			// The annotations come first, each on a row of its own, before the header of the concept, and they are not indented by the list
			// (the concept itself is): the first contents of a node, whose first token is where an insertion lands
			append(childrenContents("", () -> getModelObject().getAnnotations(), LINE_SEPARATOR, Indentation.DoNotIndent, Annotation.class));
			when(() -> getModelObject().isAbstract(), "abstract").thenAppend(staticContents("", "abstract", " "), frag("abstract"));
			append(staticContents("", "concept", " "), frag("kw"));
			append(dynamicContents(() -> getModelObject().getName()), frag("name"));
			when(() -> getModelObject().getParentName() != null, "extends").thenAppend(staticContents(" ", "extends", " "), frag("extends"))
					.thenAppend(dynamicContents(() -> getModelObject().getParentName()), frag("parent"));
			append(staticContents(" ", "{", LINE_SEPARATOR), frag("open"));
			append(childrenContents("", () -> getModelObject().getMembers(), LINE_SEPARATOR, Indentation.Indent, Member.class));
			append(staticContents("", "}", ""), frag("close"));
		}
	}

	public static class MiniAnnotationNode extends MiniNode<Annotation> {

		public MiniAnnotationNode(MiniAst ast, RawSource rawSource) {
			super(ast, rawSource);
		}

		public MiniAnnotationNode(Annotation annotation, RawSource rawSource) {
			super(annotation, rawSource);
			preparePrettyPrint(false);
		}

		@Override
		public Annotation buildModelObjectFromAST(MiniAst ast) {
			return new Annotation(ast.text("name"));
		}

		@Override
		public MiniAnnotationNode deserialize() {
			((MiniConceptNode) getParent()).getModelObject().getAnnotations().add(getModelObject());
			return this;
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			append(staticContents("", "@", ""), frag("at"));
			append(dynamicContents(() -> getModelObject().getName()), frag("name"));
		}
	}

	public static class MiniFieldNode extends MiniNode<Field> {

		public MiniFieldNode(MiniAst ast, RawSource rawSource) {
			super(ast, rawSource);
		}

		public MiniFieldNode(Field field, RawSource rawSource) {
			super(field, rawSource);
			preparePrettyPrint(false);
		}

		@Override
		public Field buildModelObjectFromAST(MiniAst ast) {
			return new Field(ast.text("name"), null).setConst("const".equals(ast.text("kw")));
		}

		@Override
		public MiniFieldNode deserialize() {
			((MiniConceptNode) getParent()).getModelObject().getMembers().add(getModelObject());
			return this;
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			// 'const' or 'field': a condition with its ELSE. Only the branch that was parsed knows its token.
			boolean parsedConst = getASTNode() != null && "const".equals(getASTNode().text("kw"));
			when(() -> getModelObject().isConst(), "const").thenAppend(staticContents("", "const", " "), parsedConst ? frag("kw") : null)
					.elseAppend(staticContents("", "field", " "), parsedConst ? null : frag("kw"));
			append(dynamicContents(() -> getModelObject().getName()), frag("name"));
			// the type is optional: ':' and the type, a sequence containing a single child, under a condition
			when(() -> getModelObject().getType() != null, "type").thenAppend(staticContents(" ", ":", " "), frag("colon"))
					.thenAppend(childContents("", () -> getModelObject().getType(), "", Indentation.DoNotIndent));
			append(staticContents("", ";", ""), frag("semi"));
		}
	}

	public static class MiniTypeRefNode extends MiniNode<TypeRef> {

		public MiniTypeRefNode(MiniAst ast, RawSource rawSource) {
			super(ast, rawSource);
		}

		public MiniTypeRefNode(TypeRef type, RawSource rawSource) {
			super(type, rawSource);
			preparePrettyPrint(false);
		}

		@Override
		public TypeRef buildModelObjectFromAST(MiniAst ast) {
			return new TypeRef(ast.text("name"));
		}

		@Override
		public MiniTypeRefNode deserialize() {
			((MiniFieldNode) getParent()).getModelObject().setType(getModelObject());
			return this;
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			append(dynamicContents(() -> getModelObject().getName()), frag("name"));
		}
	}

	public static class MiniMethodNode extends MiniNode<Method> {

		public MiniMethodNode(MiniAst ast, RawSource rawSource) {
			super(ast, rawSource);
		}

		public MiniMethodNode(Method method, RawSource rawSource) {
			super(method, rawSource);
			preparePrettyPrint(false);
		}

		@Override
		public Method buildModelObjectFromAST(MiniAst ast) {
			return new Method(ast.text("name"));
		}

		@Override
		public MiniMethodNode deserialize() {
			((MiniConceptNode) getParent()).getModelObject().getMembers().add(getModelObject());
			return this;
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			// the header is a block of its own, built directly as a sequence
			SequentialContents<MiniAst, Method> header = appendBlock();
			header.append(staticContents("", "method", " "), frag("kw"));
			header.append(dynamicContents(() -> getModelObject().getName()), frag("name"));
			append(staticContents("", "(", ""), frag("open"));
			append(childrenContents("", ", ", () -> getModelObject().getParams(), "", "", Indentation.DoNotIndent, Param.class));
			append(staticContents("", ")", ""), frag("close"));
			append(staticContents("", ";", ""), frag("semi"));
		}
	}

	public static class MiniParamNode extends MiniNode<Param> {

		public MiniParamNode(MiniAst ast, RawSource rawSource) {
			super(ast, rawSource);
		}

		public MiniParamNode(Param param, RawSource rawSource) {
			super(param, rawSource);
			preparePrettyPrint(false);
		}

		@Override
		public Param buildModelObjectFromAST(MiniAst ast) {
			return new Param(ast.text("name"), ast.text("type"));
		}

		@Override
		public MiniParamNode deserialize() {
			((MiniMethodNode) getParent()).getModelObject().getParams().add(getModelObject());
			return this;
		}

		@Override
		protected void preparePrettyPrint(boolean hasParsedVersion) {
			super.preparePrettyPrint(hasParsedVersion);
			append(dynamicContents(() -> getModelObject().getName()), frag("name"));
			// the type is optional: its prelude (the colon) goes with it when its value is empty
			append(dynamicContents(" : ", () -> getModelObject().getType()), frag("type"));
		}
	}
}
