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

A file whose first row is `// p2pp-known-defect: P2PP-D-n` is a source that p2pp does not reproduce yet: `TestMiniCorpus` then expects
the difference and fails, asking to remove the row, when the defect is fixed. The final line separator of a file is dropped before
parsing (P2PP-D-5).
