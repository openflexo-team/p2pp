package org.openflexo.p2pp.mini;

import java.io.IOException;
import java.io.StringReader;

import org.openflexo.p2pp.PrettyPrintContext;
import org.openflexo.p2pp.RawSource;
import org.openflexo.p2pp.mini.MiniModel.Unit;
import org.openflexo.p2pp.mini.MiniNodes.MiniNode;
import org.openflexo.p2pp.mini.MiniNodes.MiniUnitNode;

/**
 * A Mini unit with its pretty-print: either parsed from a source (the original text is then preserved) or built from a model (only the
 * normalized pretty-print is available).
 */
public final class MiniDocument {

	private final RawSource rawSource;
	private final MiniUnitNode rootNode;

	private MiniDocument(RawSource rawSource, MiniUnitNode rootNode) {
		this.rawSource = rawSource;
		this.rootNode = rootNode;
	}

	/** Parse a source, and initialize its pretty-print as the semantics analyzers of FML and B do once their node tree is built */
	public static MiniDocument parse(String text) throws MiniParseException {
		RawSource rawSource;
		try {
			rawSource = new RawSource(new StringReader(text));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		MiniAst ast = MiniParser.parse(rawSource);
		MiniUnitNode root = (MiniUnitNode) MiniNode.build(ast, rawSource, null);
		root.initializePrettyPrint(root, root.makePrettyPrintContext());
		return new MiniDocument(rawSource, root);
	}

	/** A document that was never parsed */
	public static MiniDocument of(Unit unit) {
		return new MiniDocument(null, new MiniUnitNode(unit, null));
	}

	public RawSource getRawSource() {
		return rawSource;
	}

	public MiniUnitNode getRootNode() {
		return rootNode;
	}

	public Unit getUnit() {
		return rootNode.getModelObject();
	}

	private PrettyPrintContext context() {
		return rootNode.makePrettyPrintContext();
	}

	/** Pretty-print from scratch */
	public String normalized() {
		return rootNode.getNormalizedTextualRepresentation(context());
	}

	/** Pretty-print preserving the parsed text where the model did not change */
	public String derived() {
		return rootNode.getTextualRepresentation(context());
	}
}
