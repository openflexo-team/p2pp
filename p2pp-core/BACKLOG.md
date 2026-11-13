# Backlog of p2pp-core

Features and evolutions of the parser-to-pretty-printer. Identifiers `P2PP-F-<n>` are never reused; defects are in `KNOWN_DEFECTS.md`.

## P2PP-F-1 — Re-indent a portion of a parsed text — DONE

**Problem.** A text edited by hand (an FML file in the FML editor) ends up with an indentation that no longer follows its structure, and
nothing in p2pp could restore it without reformatting the whole text and losing comments and layout.

**Approach.** `Reindenter` (see the README, "Re-indentation"). The depth of each token is the nesting of `Indent` contents around it;
characters are classified as token, separator, comment or layout, the rest being text no node describes. What re-indentation needs of the
lexical syntax is a `LexicalSyntax` (comments, pairs of delimiters of alignment, string literals and their escape), given by the root
node (`P2PPNode.getLexicalSyntax()`, `LexicalSyntax.C_STYLE` by default: `//`, `/* */`, `( )`, `[ ]`, `"`, `'`, `\`) or passed to the
`Reindenter`; FML uses the default. Braces are not delimiters of alignment in C style (a block brace is followed by a line break);
a language wanting them adds them (`withAlignmentDelimiters("{", "}")`). Only the leading whitespace of rows is rewritten:
- a row starting with a token gets its depth; when the specification declares no line break before that token (a statement written over
  several rows), it is aligned on the text after a `(` or `[` opened on an earlier row of the statement and still open, or else
  indented one level more (a bracket ending its row aligns nothing). The alignment is the indentation of the row of the bracket, then
  spaces, so that it holds whatever the width of a tab;
- a row starting a comment takes the deepest depth between the tokens around it (empty lists included), or is aligned as above inside an
  open bracket;
- a row inside a block comment moves with the row the comment began on (relative indentation kept, blank rows included);
- a row starting inside a multi-row token, or with text no node describes, is left as it is; a blank row is emptied.

Decided with the user: multi-row tokens untouched, blank rows emptied, continuation rows one level more (option 1 of three: also
considered, keeping the offset of the source, or the depth of the statement); then, asked while trying it in the application, the
arguments of `with ShapeRole(…)` / `with DropScheme(…)` aligned on the first one.

**FML editor (openflexo-ui).** "Re-indent" in the context menu of the FML editor and Cmd/Ctrl+I: the rows of the selection, or the whole
text when nothing is selected, with tabs (the unit of the normalized FML, not the 3 spaces the editor emulates), in one undoable edit. The
text is parsed into a unit of its own, the model is not touched; a text that does not parse is left as it is (error feedback).

**Acceptance criteria.**
- `TestMiniReindent` (p2pp-test, Mini corpus): only leading whitespace changes; the result parses to the same model and is a fixed
  point; canonical texts and normalized pretty-prints are fixed points; scrambled canonical texts come back; a portion changes no row
  outside it and gives the rows inside what the whole text gives them; comments, block comments of commented-out code, annotations,
  continuation rows, free layout.
- `TestFMLReindent` (fml-parser, the 32 `FMLParsingExamples`): the same oracles; normalized pretty-prints are fixed points, which checks
  that the FML nodes declare every line break they write (all 32 since CORE-D-57 and CORE-D-58 were fixed: their normalized text parses again).
  `TestFMLReindentCases`: `else` on its own row, a wrapped statement, an annotation before `model`.
- `TestRowsToReindent` (fml-technologyadapter-ui): the rows of a selection.

**Limits.**
- A row the specification does not declare as a line break is a continuation: a `{` written alone on its row (Allman style) would be
  indented one level more. Not met in the FML corpus.
- A comment is recognized by its syntax only outside the tokens the tree knows: in text no node describes, a `/*` inside a string could
  be taken for a comment.
- In FML, an expression is one token: a call written over several rows (`b(x,⏎y)`) is a token spanning several rows, whose following
  rows are left as they are (pinned by `TestFMLReindentCases`). The arguments of a role or a behaviour (`with ShapeRole(a=…,⏎b=…)`) are
  separate tokens and are aligned.
- The editor action was compiled (javac and ECJ) but not run in the application.

## P2PP-F-2 — Keep the parsed text of a value while it does not change — DONE

**Problem.** A `DynamicContents` replaces its fragment with the value of its supplier at each pretty-print: it cannot know whether the value
changed. When the value is a normalized form of the text (an expression, a qualified name, a literal), an untouched source is rewritten
(`1+2` becomes `1 + 2` in FML, CORE-D-33 of openflexo-core).

**Approach.** `dynamicContents(...).keepingParsedTextWhileUnchanged()`: the contents keeps its parsed fragment as long as its value equals
a reference value, captured by `P2PPNode.captureReferenceValues()` (recursively over the contents and the children), which the language
calls once the model built from the source is complete and before any edit. A value that changes is written; changed back, the parsed text
is kept again. Without a captured value the contents behaves as before. Decided with the user: the reference is captured after the model is
built (the FML parser prepares the pretty-print before resolving references, too early). Captured once, not again when a referenced unit
completes later: a later capture could take an edit for the parsed text.

`derivedWith(supplier)`: the text the preserving pretty-print writes when it is not the normalized value (a value made of parts that
keep their own text, the binding of FML written through its nodes). It is also written by a contents written in its normalized form
inside the preserving pretty-print (an object created by a model change): `P2PPNode.getTextualRepresentation` marks its context
(`PrettyPrintContext.isPreservingParsedText()`), and `derive()` passes the mark on. The normalized pretty-print of a whole text, and the
comparison with the reference value, use the normalized value.

**Acceptance.** `TestKeepParsedTextWhileUnchanged` (p2pp-test); the FML invariants of openflexo-core (CORE-D-33).

