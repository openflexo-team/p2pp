package org.openflexo.p2pp.mini;

/**
 * Syntax error in a Mini source
 */
@SuppressWarnings("serial")
public class MiniParseException extends Exception {

	private final int line;
	private final int col;

	public MiniParseException(String message, int line, int col) {
		super(message + " at " + line + ":" + col);
		this.line = line;
		this.col = col;
	}

	public int getLine() {
		return line;
	}

	public int getCol() {
		return col;
	}
}
