# Known defects of p2pp-core

Defects of the parser-to-pretty-printer that are known, understood at least in part, and not fixed yet. Each entry says what is verified
and what is not, points to what reproduces it, and is removed once the defect is fixed.

## P2PP-D-1 - Inserting a child in front of an indented node

Status: TODO

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

## P2PP-D-2 - A RawSource built from an empty text throws

Status: TODO

**Symptom.** `new RawSource(new StringReader(""))` throws `IndexOutOfBoundsException: Index -1 out of bounds for length 0`.

**Verified.** The constructor computes the end position from `rows.get(rows.size() - 1)`, and `rows` is empty. Reproduced by
`TestRawSourceEdgeCases.emptySourceIsSupported` (currently `@Ignore`d). Not checked: whether a parser can hand an empty file to p2pp.

## P2PP-D-3 - `RawSourcePosition.getLengthTo()` fails towards an earlier position

Status: TODO

**Symptom.** `getLengthTo(other)` with `other` before the receiver throws `ArrayIndexOutOfBoundsException` (decrement before start).

**Verified.** The loop decrements `current` in both branches, so when `other` is before `this` it moves away from `this` until it leaves
the source. Forward lengths are correct. Reproduced by `TestRawSourceEdgeCases.lengthToAPositionBefore` (`@Ignore`d). No caller in the
workspace uses it (only a commented-out line in `DerivedRawSource`), so the cheapest fix may be to remove it.

## P2PP-D-4 - `DefaultPrettyPrintContext.indent()` loses a trailing line separator

Status: TODO

**Symptom.** With `Indentation.Indent`, `indent("a\n")` returns `"\ta"`, not `"\ta\n"`.

**Verified.** The text is split with `BufferedReader.readLine()`, which gives one row for `"a\n"`, and the separator is only re-emitted
between rows. Reproduced by `TestDefaultPrettyPrintContext.trailingLineSeparatorIsKept` (`@Ignore`d). Not checked: whether a real
caller passes a text ending with a separator to an indenting context (`getNormalizedTextualRepresentation`, `SequentialContents`,
`ConditionalContents`). Related, current behaviour pinned by `blankRowsInTheMiddleAreDropped`: blank rows other than the first vanish.

## P2PP-D-5 - The final newline of a source is lost by the derived pretty-print

Status: TODO

**Symptom.** A source ending with a line separator is reproduced without it: `"unit U {\n}\n"` comes back as `"unit U {\n}"`, with no model
change at all.

**Verified.** `RawSource` reads the text with `readLine()`, so `"a\n"` and `"a"` both give ONE row and the same end position; the final
separator is not part of any fragment, and a node covering the whole source (the compilation unit) cannot print it. Reproduced by
`TestMiniNormalizedPrettyPrint.untouchedSourceKeepsItsFinalNewline` (`@Ignore`d). Not checked: whether FML and B writers add the
separator themselves when saving.

## P2PP-D-6 - A source whose layout is not the canonical one is reformatted without any model change

Status: TODO

**Symptom.** With nothing modified, the derived pretty-print is not the parsed text when a separator differs from the one the node
declares. With nodes declaring `staticContents(" ", "{", LINE_SEPARATOR)` and `staticContents(" ", ":", " ")`, the source
`unit U{ ... field x:int; ... concept C{}}` comes back as `unit U {`, `field x : int;` and `concept C {` followed by a line separator.

**Mechanism - read in the code, not traced.** `StaticContents.updatePrettyPrint()` (and `DynamicContents`) re-inject the canonical prelude
or postlude whenever `getPreludeFragment()` / `getPostludeFragment()` find no EXACT match of it around the parsed token. The postlude has no
"already separated by whitespace" exception (the prelude has one, added for inline blocks). The B nodes work around it by overriding
`staticContents()` / `dynamicContents()` with `whitespaceBefore()` / `whitespaceAfter()`; the Mini fixture nodes do not. Reproduced by
`TestMiniNormalizedPrettyPrint.untouchedSourceWithFreeLayoutIsReproduced` (`@Ignore`d). A source in the canonical layout, or with another
indentation, blank rows and comments, IS reproduced exactly (tests of the same class, green).

**Open question for the owner.** Is "no change, same text" meant to hold for any layout, or only for the layout the nodes declare? If
the former, the fix belongs in p2pp-core (a whitespace-only separator already materialized in the source counts as found), and the B
override can go.

## P2PP-D-7 - Removing a conditional contents leaves a stray separator

Status: TODO

**Symptom.** Removing `extends B` from `concept A extends B {` gives `concept A  {` (two spaces). Same on the other end: `abstract concept C  {`
after the `abstract` condition goes false on a concept whose parent is removed too.

**Verified in the code.** `PrettyPrintableContents.getExtendedFragment()` (and `getExtendedFragmentNoRecomputation()`) builds the fragment
with its prelude, then overwrites it with `getFragment().union(getPostludeFragment())` instead of `returned.union(...)`: when a contents
has both a prelude and a postlude, the prelude is dropped from the fragment `ConditionalContents` removes. Reproduced by the scenarios
`remove the parent` and `flip both conditions at once` of `conditional.p2pt`, and by the group `set-parent/-` of the edit matrix.

## P2PP-D-8 - A child added before the first parsed sibling is inserted after the last one

Status: TODO

**Symptom.** A child added at index 0 of a list that has parsed children is written after the last of them: concepts `Z, A, B` come out
as `A, B, Z`. In a comma-separated list the text is not even valid: adding `z : bool` first to `m(a : int, b : string)` gives
`m(a : int, b : stringz : bool)`.

**Mechanism - read in the code.** `ChildrenContents.updatePrettyPrint()` starts with both insertion points at `getInsertionPoint()`, the end
of the LAST parsed node, and only moves them when it meets a parsed child: a new child met before any parsed one lands there. No
separator is added after an inserted first item either. A child inserted in the middle or at the end is placed correctly. Reproduced by
`add a concept at the start of a unit`, `add a field at the start of the members` and `add a parameter at the start of a method`
(`add.p2pt`), and by the groups `add-concept/first`, `add-member/first` and `add-param/first` of the edit matrix.

## P2PP-D-9 - Only one level of indentation is known inside a nested node

Status: TODO

**Symptom.** In a parsed source, the members of a concept (depth 2) are indented with ONE tab when added, and removing one leaves the
rest of its indentation:

```
	concept A {
		field x : int;
	field y : string;        <- added: one tab instead of two
```

Removing the first member of three leaves `\t\t\tfield y` (three tabs) in front of the next one; removing the last leaves `\t\t}`.
Children at depth 1 (the concepts of a unit) are fine, and so is the normalized pretty-print of a whole tree.

**Mechanism - read in the code.** `DefaultPrettyPrintContext.derive()` builds a context that holds an `Indentation` and nothing else, and
`getResultingIndentation()` is a single tab: the text inserted into a parsed node is indented one level, whatever the depth of the node;
and `handlePreludeAndPoslude()` looks for that single tab before a child, so a removal takes one tab out of two. Related to P2PP-D-1
(same family: the indentation of the line an insertion lands on is not taken from the source). Reproduced by most scenarios of `add.p2pt`
that add a member and by `remove.p2pt` (members, methods), and by the groups `add-member/*` and `remove-member/*` of the edit matrix.

## P2PP-D-10 - Reordering the children of a node is ignored

Status: TODO

**Symptom.** After `move A.z to 0` on the members `x, y, z`, the derived text still reads `x, y, z`: it no longer matches the model, and
does not parse back to it. Same for parameters and concepts (groups `move-*/*` of the edit matrix).

**Mechanism - read in the code.** `ChildrenContents.updatePrettyPrint()` replaces each parsed child by its own text, in place (`Case 5`):
nothing compares the order of the model with the order of the parsed nodes. Reproduced by `move.p2pt`.

## P2PP-D-11 - Removing the first item of a list leaves its separator when `preludeForFirstItem` is empty

Status: TODO

**Symptom.** Removing `a` from `m(a : int, b : string, c : bool)` gives `m(, b : string, c : bool)`.

**Mechanism - read in the code.** The separator `", "` is the prelude of the items after the first; the first item declares `""` as
`preludeForFirstItem`. In the removal, `wasFirst` tests `StringUtils.isNotEmpty(preludeForFirstItem)` to decide to remove the prelude of the new
first node, while the insertion and the normalized pretty-print test `preludeForFirstItem != null`. With `""` the separator is left. The
same test is made on `postludeForLastItem` for the last item. Reproduced by `remove the first parameter` (`remove.p2pt`) and the group `remove-param/first` of the edit matrix.
