# Re-indentation of a parsed text (P2PP-F-1)

`Reindenter` (in `p2pp-core`) re-indents a parsed text, or some of its rows, from the structure p2pp already knows. This note describes
the approach and the algorithm. The record of the feature (decisions, acceptance criteria, limits) is P2PP-F-1 in
`p2pp-core/BACKLOG.md`.

## Context

A text edited by hand (an FML file in the FML editor, for instance) ends up with an indentation that no longer follows its structure.
Reformatting it from the model (the normalized pretty-print) would lose comments and layout. Re-indentation rewrites only the leading
whitespace of rows, so that comments, spacing inside rows and every token stay as they are.

p2pp already has everything this needs:

- every leaf contents (`StaticContents`, `DynamicContents`) knows the `RawSourceFragment` of its token;
- identified preludes and postludes are fragments too (`getPreludeFragment()`, `getPostludeFragment()`, and the node-level
  `getPrelude()` / `getPostlude()`);
- the nesting of the contents declared `Indentation.Indent` is the nesting the normalized pretty-print indents with (one unit per nested
  `Indent`), and it is right, since new objects print correctly;
- the preludes and postludes that hold a line separator say where the language breaks rows;
- `DerivedRawSource.replace(fragment, String)` applies position-based edits to the original text.

So the depth of every token can be computed from the tree, and what lies outside tokens is either layout or a comment.

## Classifying the characters

For a parsed tree (its root node and its `RawSource`), a character is:

| Class | Definition | May re-indentation change it? |
|---|---|---|
| token | inside a non-empty leaf fragment (static or dynamic contents) | never |
| separator | non-blank text of an identified prelude or postlude (`,`, `;`); a whitespace prelude is layout | never |
| comment | recognized outside tokens with the `LexicalSyntax` of the language | never, only moved by the leading whitespace of its rows |
| layout | whitespace outside tokens and comments | yes, the leading whitespace of a row only |
| unknown | anything else: text no node describes (code the parser kept no node for) | never |

## The lexical syntax: `LexicalSyntax`

What re-indentation needs to know of the lexical syntax, beyond the printing specification, is a `LexicalSyntax`:

- comments: a line prefix, and the start and end of a block comment;
- pairs of delimiters of alignment, `{open, close}`, possibly several characters long;
- the delimiters of string literals and their escape character (a delimiter inside a string literal is not one).

It is immutable; `with...` methods return a modified copy (`withAlignmentDelimiters("{", "}")`, `withoutAlignment()`, `withComments(...)`,
`withStrings(...)`). The root node of a language gives it through `P2PPNode.getLexicalSyntax()`, or the caller passes one to the
`Reindenter` constructor. The default is `LexicalSyntax.C_STYLE`: `//` and `/* */` comments, alignment in `( )` and `[ ]`, strings
between `"` or `'`, escape `\`. FML and Mini use it. Braces are not delimiters of alignment in C style: a block brace is followed by a
line break, so the rows of a block never continue a statement.

## Algorithm

### 1. The anchor map, built once from the root

The tree is walked in the order of the specification, which is the order of the source. Each step records an *anchor*: a token or a
separator, with its fragment and its depth, or a list (a `ChildContents` / `ChildrenContents`) with no fragment, which marks where its
children go even when there is none.

```
visitNode(node, d, prelude, postlude):           // prelude and postlude: the strings the parent writes around the node
    if node was not parsed: return
    recordSeparator(prelude, node.prelude fragment, d)
    for c in node.ppContents: visitContents(c, d)
    recordSeparator(postlude, node.postlude fragment, d)

visitContents(c, d):
    d' = d + (c.indentation == Indent ? 1 : 0)
    Static / Dynamic: if its fragment is not empty:
        recordSeparator(c.prelude, c.preludeFragment, d'); record(c.fragment, d'); recordSeparator(c.postlude, c.postludeFragment, d')
    Child:       record(list, d'); visitNode(parsed child, d', c.prelude, c.postlude)
    Children:    record(list, d'); for each parsed child registered for c, in order:
                     visitNode(child, d', prelude (or preludeForFirstItem), postlude (or postludeForLastItem))
    Sequential:  for s in c.ppContents: visitContents(s, d')
    Conditional: visitContents(the branch that was parsed, d')
```

While walking, a flag records whether the specification declares a line break since the last anchor: a prelude or postlude holding a line
separator (before or after its non-blank part), or a token spanning several rows, sets it. Each anchor keeps the flag it was recorded with
(`breakBefore`). The start of the text counts as a break.

Anchors with a fragment are sorted by start position; they do not overlap. The comments are then found by scanning the text outside
tokens with the lexical syntax, so that a `//` inside a string literal token is not a comment.

### 2. The indentation of a row

`unit` is the indentation of one level: by default that of the pretty-print context of the root
(`makePrettyPrintContext().derive(Indent).getResultingIndentation()`, a tab), or the one given to the constructor. For a row, with `p` the
position of its first non-blank character:

1. **The row starts inside a token that began on an earlier row** (a string literal, an expression written over several rows): left as it
   is, its leading whitespace may be meaningful.
2. **The row is blank**: emptied.
3. **`p` is in an anchor A** (a token or a separator):
   - if A has `breakBefore`, the row starts a statement: `unit × depth(A)`;
   - otherwise the row continues the statement of the row before. If a pair of delimiters of alignment was opened on an earlier row of the
     statement, is still open, and text follows the opening on its row, the row is aligned under that text: the new indentation of the row
     of the opening, then one space per character up to the text (a tab is kept as a tab, so that the alignment holds whatever the width
     of a tab). Otherwise (no delimiter open, or one ending its row), `unit × (depth(A) + 1)`.
4. **`p` starts a comment**: aligned as in 3 when it lies inside a delimiter left open by a statement; otherwise the deepest depth found
   between the anchor before `p` and the anchor after it, the lists recorded between them included. A comment after `{` or before `}`,
   or alone in an empty block, so belongs to the block.
5. **`p` is inside a block comment that began on an earlier row**: the row moves with the row the comment began on and keeps its
   indentation relative to it (the ` * ` rows of a documentation comment, commented-out code, blank rows included). A row less indented
   than that first row is left as it is.
6. **Anything else** (text no node describes): left as it is.

The search of an open delimiter starts at the row that starts the statement (the nearest earlier row whose first anchor has
`breakBefore`) and skips comments and string literals. A closing delimiter closes the last opening of its own pair, and what was opened
after it.

The indentation of a row depends on the whole tree and on no other row, except the first row of its block comment and the row of an open
delimiter, both earlier. Re-indenting a portion of the text therefore gives its rows what re-indenting the whole text gives them.

### 3. Output

- `computeIndentations(fromLine, toLine)`: the rows whose indentation changes, as `(row, length of the old indentation, new
  indentation)`, so that an editor can apply them to its document without replacing the whole text;
- `reindentAsDerivedRawSource(fromLine, toLine)`: the same edits on a `DerivedRawSource` over the whole source;
- `reindent(fromLine, toLine)` and `reindent()`: the resulting text;
- `explain(line)`: what decides the indentation of a row (the anchor and its depth, a continuation, a comment, unknown text), for
  diagnostics.

Precondition: the tree describes the current text, the last parse with no model change since. A client with pending changes prints the
text first, parses it again, then re-indents.

## Example: a statement over several rows

```text
→→DiagramShape·shape
→→→with·ShapeRole(metamodelElement=DIAGRAM_SHAPE,
→→→···············container=diagram,label=name);
→create::_create·(
→→required·String·aC1="toto",
→→Boolean·aC3=true)·{
```

`with` continues the declaration and no delimiter is open: one level more. `container` follows the open `(` of `ShapeRole(`: aligned under
`metamodelElement`. The parameters of `create::_create (` follow a `(` that ends its row: one level more.

## Use in the FML editor

"Re-indent" in the context menu of the FML editor (openflexo-ui) and Cmd/Ctrl+I: the rows of the selection, or the whole text when nothing
is selected, with tabs (the unit of the normalized FML), in one undoable edit. The text is parsed into a unit of its own, so the model of
the editor is not touched; a text that does not parse is left as it is.

## Verification

The oracles, run on the Mini corpus (`TestMiniReindent`, p2pp-test) and on the FML parsing examples (`TestFMLReindent`,
`TestFMLReindentCases`, fml-parser):

1. only the leading whitespace of rows changes;
2. the result parses to the same model;
3. re-indenting the result again changes nothing;
4. a canonically indented text, comments included, is a fixed point, and so is every normalized pretty-print. On FML this checks that the
   nodes declare every line break they write;
5. a canonical text whose indentation was scrambled (a block comment moved as a whole) comes back to the canonical text;
6. re-indenting a portion changes no row outside it, and gives the rows inside what re-indenting the whole text gives them;
7. targeted cases: comments around blocks, block comments of commented-out code, annotations, `else` on its own row, continuation rows,
   alignment in open delimiters, a free layout, a lexical syntax with no alignment or no comments.

## Limits

- A row the specification does not declare as a line break is a continuation: a `{` written alone on its row (Allman style) is indented
  one level more.
- In FML an expression is one token: a call written over several rows (`b(x,⏎y)`) is a token spanning several rows, whose following
  rows are left as they are. The arguments of a role or a behaviour (`with ShapeRole(a=…,⏎b=…)`) are separate tokens and are aligned.
- Comments and delimiters are found by their syntax, outside the tokens the tree knows: in text no node describes, a `/*` inside a string
  could be taken for a comment.
- No test exercises a row starting inside a multi-row string literal (neither Mini nor the FML corpus has one).
