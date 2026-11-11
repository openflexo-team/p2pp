package org.openflexo.p2pp.testing.matrix;

import java.util.List;

/**
 * One generated case of the edit matrix: a source of the corpus, an edit (as the commands of {@code MiniEdits}) and the commands that
 * undo it, when there are some.
 */
public final class EditCase {

	public final String sourceName;
	public final String source;
	/** Kind of edit: {@code rename}, {@code add-member}, {@code remove-param}... */
	public final String kind;
	/** Position class: {@code first}, {@code middle}, {@code last}, {@code only}, {@code empty} (add to an empty list) or {@code -} */
	public final String position;
	public final List<String> edits;
	/** Commands that bring the model back to its state before the edits, or null */
	public final List<String> inverse;

	public EditCase(String sourceName, String source, String kind, String position, List<String> edits, List<String> inverse) {
		this.sourceName = sourceName;
		this.source = source;
		this.kind = kind;
		this.position = position;
		this.edits = edits;
		this.inverse = inverse;
	}

	/** Group of cases that a test runs together */
	public String signature() {
		return kind + "/" + position;
	}

	@Override
	public String toString() {
		return sourceName + ": " + String.join("; ", edits);
	}
}
