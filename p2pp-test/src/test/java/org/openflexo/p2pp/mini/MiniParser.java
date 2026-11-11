package org.openflexo.p2pp.mini;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openflexo.p2pp.RawSource;
import org.openflexo.p2pp.mini.MiniAst.Tok;

/**
 * Hand-written lexer and recursive-descent parser of Mini. Comments ({@code // ...} and {@code /* ... *}{@code /}) and whitespace are
 * skipped: they are found again in the derived pretty-print only because they lie out of every fragment.
 */
public final class MiniParser {

	private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList("unit", "abstract", "concept", "extends", "field", "const", "method"));
	private static final String PUNCTUATION = "{}():;,";

	private final RawSource raw;
	private final List<Tok> tokens;
	private int index = 0;

	private MiniParser(RawSource raw) throws MiniParseException {
		this.raw = raw;
		this.tokens = lex(raw);
	}

	public static MiniAst parse(RawSource raw) throws MiniParseException {
		return new MiniParser(raw).unit();
	}

	// ---- Lexer ----

	private static List<Tok> lex(RawSource raw) throws MiniParseException {
		List<Tok> returned = new ArrayList<>();
		boolean inBlockComment = false;
		for (int r = 0; r < raw.size(); r++) {
			String s = raw.getRow(r);
			int line = r + 1;
			int i = 0;
			while (i < s.length()) {
				if (inBlockComment) {
					int end = s.indexOf("*/", i);
					if (end < 0) {
						i = s.length();
					}
					else {
						i = end + 2;
						inBlockComment = false;
					}
					continue;
				}
				char c = s.charAt(i);
				if (Character.isWhitespace(c)) {
					i++;
				}
				else if (s.startsWith("//", i)) {
					i = s.length();
				}
				else if (s.startsWith("/*", i)) {
					inBlockComment = true;
					i += 2;
				}
				else if (Character.isLetter(c) || c == '_') {
					int j = i;
					while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
						j++;
					}
					returned.add(token(raw, s.substring(i, j), line, i, true));
					i = j;
				}
				else if (PUNCTUATION.indexOf(c) >= 0) {
					returned.add(token(raw, String.valueOf(c), line, i, false));
					i++;
				}
				else {
					throw new MiniParseException("Unexpected character '" + c + "'", line, i);
				}
			}
		}
		if (inBlockComment) {
			throw new MiniParseException("Unterminated comment", raw.size(), 0);
		}
		return returned;
	}

	private static Tok token(RawSource raw, String text, int line, int col, boolean identifier) {
		return new Tok(text, raw.makeFragment(raw.makePositionAfterChar(line, col), raw.makePositionAfterChar(line, col + text.length())), line,
				col, identifier);
	}

	// ---- Parser ----

	private boolean peek(String text) {
		return index < tokens.size() && tokens.get(index).text.equals(text);
	}

	private Tok next() {
		return tokens.get(index++);
	}

	private MiniParseException error(String message) {
		if (index < tokens.size()) {
			Tok t = tokens.get(index);
			return new MiniParseException(message + ", found '" + t.text + "'", t.line, t.col);
		}
		return new MiniParseException(message + ", found end of source", raw.size(), 0);
	}

	private Tok expect(String text) throws MiniParseException {
		if (!peek(text)) {
			throw error("Expected '" + text + "'");
		}
		return next();
	}

	private Tok ident() throws MiniParseException {
		if (index >= tokens.size() || !tokens.get(index).identifier || KEYWORDS.contains(tokens.get(index).text)) {
			throw error("Expected an identifier");
		}
		return next();
	}

	private MiniAst unit() throws MiniParseException {
		MiniAst a = new MiniAst("unit");
		a.put("kw", expect("unit"));
		a.put("name", ident());
		a.put("open", expect("{"));
		while (!peek("}")) {
			a.add(concept());
		}
		a.put("close", expect("}"));
		if (index < tokens.size()) {
			throw error("Unexpected text after the unit");
		}
		return a;
	}

	private MiniAst concept() throws MiniParseException {
		MiniAst a = new MiniAst("concept");
		if (peek("abstract")) {
			a.put("abstract", next());
		}
		a.put("kw", expect("concept"));
		a.put("name", ident());
		if (peek("extends")) {
			a.put("extends", next());
			a.put("parent", ident());
		}
		a.put("open", expect("{"));
		while (!peek("}")) {
			a.add(member());
		}
		a.put("close", expect("}"));
		return a;
	}

	private MiniAst member() throws MiniParseException {
		if (peek("field") || peek("const")) {
			MiniAst a = new MiniAst("field");
			a.put("kw", next());
			a.put("name", ident());
			if (peek(":")) {
				a.put("colon", next());
				MiniAst type = new MiniAst("type");
				type.put("name", ident());
				a.add(type);
			}
			a.put("semi", expect(";"));
			return a;
		}
		if (peek("method")) {
			MiniAst a = new MiniAst("method");
			a.put("kw", next());
			a.put("name", ident());
			a.put("open", expect("("));
			if (!peek(")")) {
				a.add(param());
				while (peek(",")) {
					next();
					a.add(param());
				}
			}
			a.put("close", expect(")"));
			a.put("semi", expect(";"));
			return a;
		}
		throw error("Expected 'field', 'const' or 'method'");
	}

	private MiniAst param() throws MiniParseException {
		MiniAst a = new MiniAst("param");
		a.put("name", ident());
		if (peek(":")) {
			a.put("colon", next());
			a.put("type", ident());
		}
		return a;
	}
}
