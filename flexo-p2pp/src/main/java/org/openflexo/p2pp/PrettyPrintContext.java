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

	public Indentation getIndentation();

	public String getResultingIndentation();

	public String indent(String stringToIndent);

}
