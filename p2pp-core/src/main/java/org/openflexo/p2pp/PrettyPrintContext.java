package org.openflexo.p2pp;

public interface PrettyPrintContext {

	public enum Indentation {
		Indent, DoNotIndent
	}

	/**
	 * Derive a PrettyPrintContext from this PrettyPrintContext
	 * 
	 * <ul>
	 * <li>When relativeIndentation is zero, keep current indentation</li>
	 * <li>When relativeIndentation is positive, increment current indentation with that value</li>
	 * <li>When relativeIndentation is negative (-1), discard current indentation</li>
	 * </ul>
	 */
	public PrettyPrintContext derive(Indentation indentation);

	/**
	 * Tells whether this context is the one of the preserving pretty-print ({@link P2PPNode#getTextualRepresentation(PrettyPrintContext)}),
	 * where a contents written in its normalized form (an object created by a model change) still writes its values as they were written
	 * ({@link DynamicContents#derivedWith(java.util.function.Supplier)}), rather than the normalized pretty-print of a whole text
	 */
	public default boolean isPreservingParsedText() {
		return false;
	}

	/**
	 * This context, for the preserving pretty-print (see {@link #isPreservingParsedText()})
	 */
	public default PrettyPrintContext preservingParsedText() {
		return this;
	}

	public Indentation getIndentation();

	public String getResultingIndentation();

	public String indent(String stringToIndent);

}
