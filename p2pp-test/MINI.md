# Mini, the fixture language of the p2pp tests

Mini is a tiny language written for the tests of p2pp: each of its constructs exercises one kind of pretty-printable contents.
Its parser, model and nodes are in `src/test/java/org/openflexo/p2pp/mini/`; the sample sources are in `src/test/resources/mini/`.

```
unit U {
	abstract concept A extends B {
		field x : int;
		method m(a : int, b : string);
	}
	concept C {
	}
}
```

## Concrete syntax (EBNF)

```
unit      ::= "unit" ID "{" concept* "}"                 -- end of source right after
concept   ::= [ "abstract" ] "concept" ID [ "extends" ID ] "{" member* "}"
member    ::= field | method
field     ::= "field" ID ":" ID ";"
method    ::= "method" ID "(" [ param { "," param } ] ")" ";"
param     ::= ID ":" ID

ID          ::= (letter | "_") (letter | digit | "_")*   -- not a keyword
keywords    ::= unit abstract concept extends field method
punctuation ::= { } ( ) : ; ,
ignored     ::= whitespace, "// ...", "/* ... */" (a block comment may span several rows)
```

A keyword cannot be a name: `fieldish` and `concepts` are valid, `concept concept {` is refused. An unterminated comment, an unknown
character or text after the unit is a syntax error, located by line and column.

## Abstract syntax (the model, `MiniModel`)

```
Unit     (name, concepts : Concept*)
Concept  (name, isAbstract : bool, parentName : String?, members : Member*)
Member   = Field | Method
Field    (name, type : TypeRef)
Method   (name, params : Param*)
Param    (name : String, type : String)
TypeRef  (name)
```

Two asymmetries are deliberate: the type of a field is an object (`TypeRef`) while the type of a parameter is a plain string, and the
parent of a concept is a name, not a reference. They exercise the single-child contents on one side and the dynamic contents on the other.

## Contents of the pretty-print (`MiniNodes`)

| Construct | Contents | Declared separators |
|---|---|---|
| `unit`, `concept`, `field`, `method`, `extends`, `abstract`, `{ } ( ) : ;` | static | `unit␣`, `␣{⏎`, `␣:␣`, `⏎}`... |
| names (unit, concept, member, parameter, parent, parameter type) | dynamic | none |
| `abstract` | conditional (`isAbstract`) | `␣` after |
| `extends` + parent name | conditional holding a sequence of two contents (`parentName != null`) | `␣extends␣` |
| type of a field | single child (`TypeRef`) | none |
| concepts of a unit, members of a concept | children, indented | `⏎` after each |
| parameters of a method | children, not indented | `, ` between items |

These separators are the ones a node declares for the normalized pretty-print. The derived pretty-print looks for them as they are in
the parsed text, which is why a source in another layout is reformatted (P2PP-D-6 in `p2pp-core/KNOWN_DEFECTS.md`).

## Conventions of the sample sources

A file whose first row is `// p2pp-known-defect: P2PP-D-n` is a source that p2pp does not reproduce yet: `TestMiniCorpus` then fails like
any other test (see below for the tolerant run). The final line separator of a file is dropped before
parsing (P2PP-D-5).

## Scenarios (`src/test/resources/scenarios/*.p2pt`)

A scenario gives a source, edits applied to its model, and the text the derived pretty-print must then be. Run by `TestMiniScenarios`
(one test per scenario), through `ScenarioRunner`.

```
# a comment, outside a block
=== add a field at the end of the members
known-defect: P2PP-D-9           (optional)
--- source
unit U {
	concept A {
		field x : int;
	}
}
--- edit
add field A y : string
--- expect
unit U {
	concept A {
		field x : int;
		field y : string;
	}
}
--- expect-normalized                (optional)
```

A block ends at the next `---` or `===` row; its trailing blank rows and final line separator are not part of it. Edits are applied in
order, to the model only, then the document is pretty-printed once. They are written with names as they are at that moment (after
`rename A to Z`, the concept is `Z`).

| Edit | Effect |
|---|---|
| `rename PATH to NAME` | rename the unit, a concept, a member or a parameter |
| `set abstract CONCEPT true\|false`, `set parent CONCEPT NAME\|none` | the two conditions of a concept |
| `set type PATH TYPE` | the type of a field or of a parameter |
| `add concept NAME [abstract] [extends P] [at N]` | add a concept to the unit |
| `add field CONCEPT NAME : TYPE [at N]`, `add method CONCEPT NAME ( [p : T, ...] ) [at N]` | add a member |
| `add param PATH NAME : TYPE [at N]` | add a parameter to a method |
| `remove PATH`, `move PATH to N` | remove, or reorder inside its list |

A PATH is `unit`, `A` (a concept), `A.x` (a member) or `A.m.p` (a parameter). Indexes start at 0; without `at`, the element goes last.

Besides the expected text, each scenario checks that the derived text parses back to the edited model, and that it is stable (parsed and
pretty-printed again, it does not change). `known-defect: P2PP-D-n[, P2PP-D-m]` marks a scenario that p2pp does not pass yet, with the
ideal result as `expect`: the scenario fails like any other until the defect is fixed. An edit that cannot be
applied (unknown path...) is an error of the scenario, never an expected failure.

## The edit matrix (`TestMiniEditMatrix`)

Where scenarios say what a result must be, the matrix needs no expected text. For every source of the corpus (except those marked with a
known defect) `EditGenerator` produces every atomic edit of its model, as the commands of the scenarios:

- rename each object, to a shorter and a longer name; flip each condition; change each type;
- add an object at every index of every list (concepts of the unit, members of a concept, parameters of a method);
- remove each object; move each object to every other index.

Each case is checked by `EditCaseChecker` against invariants that hold whatever the edit: the derived text parses back to the edited model;
it is stable (parsed and pretty-printed again it does not change); no comment is lost (except by a removal); for a source laid out
canonically, the derived text still is (one tab per level of braces, single spaces, no trailing space); and the edit followed by its inverse
gives the pretty-print of the untouched source back, also on a document that was pretty-printed in between.

Cases are run by group, one test per kind of edit and position (`add-member/first`, `remove-param/only`, `move-concept/middle`...): the
message says how many cases of the group fail, and the first ones with the commands to reproduce them (paste them in a `.p2pt` scenario
to keep one). The groups that show a known defect are listed in `src/test/resources/matrix-known-defects.txt`.

## Known defects in the results

A test that shows a known defect of p2pp-core (`../p2pp-core/KNOWN_DEFECTS.md`) FAILS, like any other: the goal is to fix the defects.
The `known-defect` marks of scenarios and corpus files only say which defect a test is about.

To get a green build while the defects are open, tolerate them:

```bash
./gradlew :p2pp-test:test -Dp2pp.tolerateKnownDefects=true
```

Those tests then wait for the fix: they are skipped, or, for a marked scenario or corpus file, they expect the failure (and fail when it
is gone, asking to remove the mark). Each of them is recorded, and the end of the `test` task prints which defects the run waits for:

```
Known p2pp defects waited for by this run (p2pp-core/KNOWN_DEFECTS.md): 10 defects, 31 defect/test pairs
  P2PP-D-2   1 test(s)
  ...
```

A test that cannot run at all until a fix (it would crash, or has no sensible result to expect) starts with
`KnownDefects.skipIfTolerated("P2PP-D-n")`. The details are in `KnownDefects` (test sources) and the `test` configuration of `build.gradle`.
