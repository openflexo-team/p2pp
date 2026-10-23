# Known defects of flexo-p2pp

Defects of the parser-to-pretty-printer that are known, understood at least in part, and not fixed yet. Each entry says what is verified
and what is not, points to what reproduces it, and is removed once the defect is fixed.

## Inserting a child in front of an indented node

**Symptom.** When a child that was not parsed is added to the FIRST contents of a node - typically an annotation added programmatically
to an FML declaration that already sits at some indentation - the indentation of the node ends up in front of the inserted child, and the
node itself restarts at column 0:

```
	/** Neither convention nor annotation nor parent: no component at all. */
	@UI(compact="WithoutAnyComponentCompact.fib")
public concept WithoutAnyComponent {
```

Only the layout is damaged: the result is still valid FML, parses back, and declares what was added. Observed on a `FlexoConcept` inside a
`VirtualModel` (2026-09); reproduced by `TestCreateFIBComponent.test6DeclaringAVariantChangesOnlyTheAnnotation` in
`openflexo-ui/fml-gina-extension`, which tolerates this one difference until it is fixed.

**Mechanism - verified in the code.**

1. `FlexoConceptNode.preparePrettyPrint()` (`openflexo-core/fml-parser`) declares the metadata as its FIRST contents:
   `childrenContents("", () -> getModelObject().getMetaData(), LINE_SEPARATOR, Indentation.DoNotIndent, FMLMetaData.class, "MetaData")` -
   an empty prelude, a line separator as postlude, no indentation.
2. `ChildrenContents.updatePrettyPrint()` inserts a child that was not parsed at `getInsertionPoint()`, as
   `prelude + child + postlude`. With no parsed sibling, `ChildrenContents.getInsertionPoint()` falls back to
   `PrettyPrintableContents.getInsertionPoint()`, which - there being no previous contents - returns `P2PPNode.getDefaultInsertionPoint()`,
   initialised to `getStartPosition()` (`P2PPNode`, around line 259).
3. That start position is where the first fragment of the node begins, which does not include the whitespace indenting it - as the output
   above shows. The child, followed by its line separator, is therefore inserted between the indentation and the first token of the node,
   and nothing emits the indentation again in front of the node.
4. `ChildrenContents.handlePreludeAndPoslude()` computes an indentation prelude only when the context indentation is not
   `Indentation.DoNotIndent`, which the metadata contents is.

A child parsed from the source is not affected: its fragments, whitespace included, come from the original text.

**Scope - not checked node by node.** The FML nodes that print metadata before their header are the candidates (`getMetaData()` in
`openflexo-core/fml-parser/.../fmlnodes`): `FlexoConceptNode`, `VirtualModelNode`, `FlexoBehaviourNode`, `ActionSchemeNode`,
`DeletionSchemeNode`, `FlexoEnumNode`, `FlexoEventNode`. Only `FlexoConceptNode` has been observed. The defect can only show when the node
is indented: a top-level `VirtualModel` is not.

**Direction for a fix - not attempted.** When a child is inserted at the start position of a node, the inserted text should be followed by
the indentation of the line it is inserted on - or the insertion point should be the start of that line rather than the start of the node's
first fragment. Either belongs here, in `ChildrenContents` or `P2PPNode.getDefaultInsertionPoint()`, rather than in each FML node.
