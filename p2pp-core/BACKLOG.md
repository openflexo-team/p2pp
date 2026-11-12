# Backlog of p2pp-core

Features and evolutions of the parser-to-pretty-printer. Identifiers `P2PP-F-<n>` are never reused; defects are in `KNOWN_DEFECTS.md`.

## P2PP-F-1 — Re-indent a portion of a parsed text — DONE

**Problem.** A text edited by hand (an FML file in the FML editor) ends up with an indentation that no longer follows its structure, and
nothing in p2pp could restore it without reformatting the whole text and losing comments and layout.

**Approach.** `Reindenter` (see the README, "Re-indentation"). The depth of each token is the nesting of `Indent` contents around it;
characters are classified as token, separator, trivia (comments) or layout; only the leading whitespace of rows is rewritten. A comment row
takes the deepest depth between the tokens around it (empty lists included); the comment rows that follow it up to a blank row keep their
relative indentation; a row starting inside a multi-row token is untouched; blank rows are emptied. Decided with the user: multi-row tokens
untouched, blank rows emptied.

**Acceptance criteria** (`TestMiniReindent`, on the Mini corpus): only leading whitespace changes; the result parses to the same model and is
a fixed point; canonical texts and normalized pretty-prints are fixed points; scrambled canonical texts come back; a portion changes no row
outside it and gives the rows inside what the whole text gives them; targeted comment, annotation and free-layout cases.

**Limits.**
- Without a lexer, two consecutive line comments cannot be told from a block comment: the second keeps its offset relative to the first.
- A block comment that starts after a token on a row: its following rows are re-indented as comments of their own.
- The case of a row starting inside a multi-row token is not exercised by Mini (it has no such token).

**Follow-up (not done).** A "Re-indent selection" action in the FML editor (openflexo-ui), and an FML oracle: well-indented files of the FML
corpus must be fixed points.
