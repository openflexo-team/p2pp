package org.openflexo.p2pp.mini;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openflexo.p2pp.RawSource.RawSourceFragment;

/**
 * The abstract syntax tree of Mini, standing for what a generated parser (SableCC for FML or B) provides: nodes with their tokens, each
 * located by a fragment of the raw source.
 */
public final class MiniAst {

	/** A token of the source, with its location */
	public static final class Tok {
		public final String text;
		public final RawSourceFragment fragment;
		public final int line;
		public final int col;
		public final boolean identifier;

		Tok(String text, RawSourceFragment fragment, int line, int col, boolean identifier) {
			this.text = text;
			this.fragment = fragment;
			this.line = line;
			this.col = col;
			this.identifier = identifier;
		}

		@Override
		public String toString() {
			return text + "@" + line + ":" + col;
		}
	}

	private final String kind;
	private final Map<String, Tok> tokens = new HashMap<>();
	private final List<MiniAst> children = new ArrayList<>();
	private RawSourceFragment fragment;

	MiniAst(String kind) {
		this.kind = kind;
	}

	void put(String role, Tok token) {
		tokens.put(role, token);
		extend(token.fragment);
	}

	void add(MiniAst child) {
		children.add(child);
		extend(child.fragment);
	}

	private void extend(RawSourceFragment f) {
		fragment = fragment == null ? f : fragment.union(f);
	}

	public String getKind() {
		return kind;
	}

	public List<MiniAst> getChildren() {
		return children;
	}

	/** Fragment from the first to the last token of this node */
	public RawSourceFragment getFragment() {
		return fragment;
	}

	public boolean has(String role) {
		return tokens.containsKey(role);
	}

	public String text(String role) {
		Tok t = tokens.get(role);
		return t != null ? t.text : null;
	}

	public RawSourceFragment fragmentOf(String role) {
		Tok t = tokens.get(role);
		return t != null ? t.fragment : null;
	}
}
