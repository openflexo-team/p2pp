p2pp
====

Parser / pretty-printer framework (P2PP): the `p2pp-core` module, used by the FML parser and the FML editor.

It was part of `openflexo-utils` until 2.0. It depends on `openflexo-utils` and on `connie`, hence its own repository
(`openflexo-utils` is the bottom layer and must not depend on `connie`).

The tests live in the `p2pp-test` module (`./gradlew :p2pp-test:test`); `p2pp-core` itself ships no test code. That module also holds a test
harness other pretty-printers can reuse, see `p2pp-test/MINI.md`.

## Approach: a layout-preserving parser/pretty-printer

### Context

P2PP ("Parser to Pretty-Printer") is a small Java framework (15 classes, ~4,500 lines) from the OpenFlexo platform, a model-federation
platform. It is used by two textual languages:

- **FML** (Flexo Modeling Language), the platform's user-facing modeling language. It is parsed with SableCC.
- A parser for the **B method** language.

The objects being printed are *models*, not syntax trees. In OpenFlexo, model objects are PAMELA annotated Java interfaces, a reflective
modeling framework. The text is a *serialization* of the model, and users edit it in a text editor.

### The problem it solves

A user writes a text file, which is parsed into a model. The model is then modified programmatically (for example by a GUI action that adds
a concept or renames an element). The text must be regenerated such that:

1. **Untouched parts are reproduced byte for byte.** This covers comments, blank lines, custom indentation and spacing.
2. **Only the changes appear.** A rename changes one token, an added child is inserted at a sensible place, and a removed child takes its
   separator with it.
3. **Objects created from scratch (never parsed) still get a canonical rendering.**

A normal pretty-printer fails (1), because it regenerates everything from the model and loses comments and layout. A plain AST-to-text
rewriter on a concrete syntax tree needs the tree to be kept in sync with the model. P2PP instead keeps the *source text*, the *model* and a
small *printing specification* side by side, and merges them on demand.

### Core design

#### Source text with positions

- `RawSource` holds the parsed text as rows and addresses it with `(line:pos)` positions. A position is a cursor between characters, so
  `(1:0)` is before the first character.
- `RawSourceFragment` is a pair of positions. It supports `getRawText()`, `intersects`, `union`, and comparison of positions.
- The text is never modified. It is the reference for the last parse.

#### One node per model object: `P2PPNode<N, T>`

`N` is the parser's AST node type, `T` the model object type. A node:

- records the fragment of source its model object occupied when parsed (its start and end positions are retrieved from the AST node through
  a `FragmentRetriever`);
- is linked to a parent and to child nodes, which mirror the containment of the model;
- owns an ordered list of `PrettyPrintableContents`, the declarative printing specification of the object (next section);
- is abstract: each language subclasses it, one subclass per model concept (about 19 in FML, 11 in B).

The printing specification is declared once per node, in `preparePrettyPrint(boolean hasParsedVersion)`. The same declaration drives both the
normalized rendering and the update of an existing text.

#### Printing specification: `PrettyPrintableContents`

Six building blocks are appended to a node, in order:

| Contents | Meaning |
|---|---|
| `StaticContents` | a keyword or literal (`"concept"`, `"{"`), with optional prelude/postlude strings |
| `DynamicContents` | a string computed from the model (a name, a value), via a `Supplier<String>` |
| `ChildContents` | one child object, rendered by its own node |
| `ChildrenContents` | a list of children, with a prelude and postlude per item, plus a special prelude for the first item and a special postlude for the last one (this models comma-separated lists, `;`-terminated statements, enclosing braces) |
| `SequentialContents` | a sequence of contents |
| `ConditionalContents` | a condition on the model with then/else contents (optional syntax parts) |

Each content can carry an `Indentation` (`DoNotIndent` or `Indent`).

The specification is *structural*, not a grammar. It is a small tree of combinators over suppliers that read the current model. It is fixed
for the life of a node. It is re-evaluated against the model every time a text is requested.

Prelude and postlude are canonical separator strings ("the string that normally goes before/after this item"). They matter because
separators belong to neither the parent nor the child, yet must move with an item when it is added or removed.

#### Two renderings

1. **Normalized rendering.** `getNormalizedTextualRepresentation(context)` walks the contents and builds the text from the model only. It
   ignores the original source. It is used for new objects and as the canonical form.
2. **Derived (layout-preserving) rendering.** `getTextualRepresentation(context)` returns the original fragment with modifications applied.
   - `computeTextualRepresentation` creates a `DerivedRawSource` on the node's last parsed fragment.
   - It asks each contents to `updatePrettyPrint(...)`, and each one records edits on that `DerivedRawSource`.
   - `DerivedRawSource` keeps a list of `StringReplacement`, `StringInsertion`, `StringDeletion` and nested `DerivedRawSourceReplacement`,
     all expressed against *original* positions.
   - `getStringRepresentation()` sorts them by position and splices them into the original text.
   - A node without an AST node (never parsed) falls back to the normalized rendering.

#### Recovering where things are: prelude/postlude identification

After parsing, a one-time `initializePrettyPrint` pass tries to locate the separators in the original text. For each child,
`tryToIdentifyPrelude`/`tryToIdentifyPostlude` scan backward or forward from the child's fragment for an *exact textual match* of the expected
separator. The scan stops when it would intersect a fragment already mapped to another node (`isFragmentMapped`). Identified fragments are
remembered, and a parent's extent is widened when its child's separator lies outside it.

This is how a deletion can remove "the item and its comma" and how an insertion knows whether the previous item's separator is already
present. It is a heuristic text match, not something derived from the grammar.

#### Edit semantics, in `ChildrenContents.updatePrettyPrint`

For each child in the current model list, the code distinguishes:

- **Parsed, still present**: its fragment is replaced by its own (recursively derived) text.
- **New (not parsed)**: its normalized/derived text is *inserted* after the last parsed sibling, or at the node's default insertion point if
  there is none. The prelude/postlude logic has several cases (first item, last item, both, neither, previous sibling was or was not the
  last parsed one).
- **Parsed, now absent**: its fragment is deleted, together with its prelude/postlude. There are cases for first, last, only and middle item,
  and for items carrying special first/last separators.

`ConditionalContents` remembers the condition value at parse time and handles four transitions: still true, true→false (remove the extended
fragment), false→true (insert and remove any stale fragment), still false.

#### Re-indentation: `Reindenter`

`new Reindenter(rootNode).reindent(fromLine, toLine)` re-indents rows of a parsed text (P2PP-F-1). The depth of every token is the number of
contents declared `Indent` around it, the nesting the normalized rendering indents with. A character is a *token* (in the fragment of a
static or dynamic contents), a *separator* (non-blank text of an identified prelude or postlude), a *comment* or *layout* (whitespace);
anything else is text no node describes. The lexical part comes from a `LexicalSyntax` — comments, pairs of delimiters of alignment,
string literals — that the root node gives (`P2PPNode.getLexicalSyntax()`, `LexicalSyntax.C_STYLE` by default: `//`, `/* */`, `( )`,
`[ ]`, `"`, `'`) or that is passed to the `Reindenter`. Only the leading
whitespace of rows is rewritten:

- a row starting with a token or a separator gets the depth of that token. When no line break is declared before it in the specification
  (preludes and postludes holding a line separator), it continues a statement written over several rows: it is aligned on the text after
  a pair of delimiters of alignment still open from an earlier row of the statement (tabs, then spaces), else indented one level more;
- a row starting a comment gets the deepest depth between the tokens around it, the lists declared between them included, so that a
  comment after `{`, before `}` or alone in an empty block belongs to the block;
- a row inside a block comment moves with the row the comment began on, keeping its relative indentation (commented-out code);
- a row starting inside a token spanning several rows, or with text no node describes, is left as it is; a blank row is emptied.

The indentation of a row depends on the whole tree, never on the rows around it (but the first row of its block comment), so a portion gets
the rows the whole text would get. The tree must describe the current text (no model change since the parse). The result is a list of row
edits, a `DerivedRawSource`, or the text; `explain(line)` tells what decides the indentation of a row.

### Characteristics relevant for comparison

- **No concrete syntax tree is retained.** The AST is used once to compute fragments. Afterwards the source of truth is *(source text,
  model, printing spec)*.
- **Edits are located by position, in the original text.** There is no incremental reparse and no tree diff. The model is the edited
  artifact, and the text is the output.
- **Trivia are never modelled.** Comments and whitespace are preserved only because they lie in unmodified parts of the source text. A
  comment between two items survives if neither item's fragment includes it. A comment inside a replaced fragment is lost.
- **The printing spec is hand-written per node** and separate from the grammar. Nothing guarantees that it is consistent with the grammar.
  Consistency is checked only by round-trip tests.
- **Indentation is a coarse feature:** one tab per level for normalized output, with explicit context objects.
- **Model-centric rather than syntax-centric.** One model change maps to local text edits, which is closer to a *bidirectional
  transformation* where the text is the view and the model the source. No lens laws are stated or enforced.
- **Heuristics and known weaknesses** (see below).

### Status of verification

- p2pp-core has had few direct tests so far. It was exercised only through the FML and B test suites, which need the whole platform.
- The test module `p2pp-test` was just started. It currently covers positions, fragments, `DerivedRawSource` and the indentation context
  (53 tests). Planned next: a small fixture language with a hand-written parser, plus a scenario DSL (source, model edits, expected text)
  with automatic oracles. The oracles are: no edit gives identical text; the derived text reparses to the edited model; the normalized text
  reparses; a second print is idempotent. Edits will then be generated over a corpus.
- Defects recorded in `p2pp-core/KNOWN_DEFECTS.md`:
  - inserting a child in front of an indented node leaves the node's indentation in the wrong place (layout damage only, text still valid);
  - an empty source throws;
  - `getLengthTo` to an earlier position throws (unused);
  - indentation drops a trailing line separator.
- Suspected but not yet tested:
  - overlap detection between modifications is an empty method;
  - a nested replacement is compared with `equals` between a `String` and a `DerivedRawSource`;
  - the textual search for separators can in principle mis-identify a separator that also appears elsewhere.
- Not claimed: correctness in general, or preservation of layout for arbitrary edit sequences. The design has no formal guarantee. Whether
  repeated edits remain consistent is exactly what the planned generated-edit tests are meant to probe. The code even carries a
  `TODO: implement a cache`.

### Questions that a literature comparison could address

- How does this compare to **layout- or format-preserving pretty-printing** and **"lossless" syntax trees**: trivia attached to tokens
  (Roslyn-style), concrete syntax trees, or the Wadler/Leijen document-algebra line, which cannot preserve existing layout by itself?
- How does it relate to **bidirectional transformations / lenses** (`get`/`put` between text and model) and to **model-text synchronisation
  in language workbenches** (Xtext's node model and formatter, Spoofax, MPS projectional editing, Rascal, Eclipse EMFText/TCS)?
- Is a position-addressed, edit-list approach (as in `DerivedRawSource`) a known technique under another name (text patching,
  source-to-source refactoring engines, `recast`-style tools)?
- Which formal properties from that literature (round-trip laws, idempotence, minimality of the edit) would be the right oracles for testing
  P2PP?
- What does the literature say about locating separators and delimiters after the fact versus recording them at parse time?
