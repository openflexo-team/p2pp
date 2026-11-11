package org.openflexo.p2pp.testing.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * One scenario of a {@code .p2pt} file: a source, edits applied to its model, and the pretty-print expected afterwards.
 * <p>
 * File format (rows outside a block, before the first {@code ===}, are comments):
 *
 * <pre>
 * === title of the scenario
 * known-defect: P2PP-D-n          (optional)
 * --- source
 * text of the source
 * --- edit
 * one edit per row, in the language of edits of the target
 * --- expect
 * text of the derived pretty-print
 * --- expect-normalized           (optional)
 * text of the normalized pretty-print
 * </pre>
 *
 * A block ends at the next {@code ---} or {@code ===} row; its trailing blank rows and its final line separator are not part of it.
 */
public final class Scenario {

	/** An edit row, with the line it was read at (for error messages) */
	public static final class Edit {
		public final String command;
		public final int line;

		Edit(String command, int line) {
			this.command = command;
			this.line = line;
		}
	}

	private final String file;
	private final int line;
	private final String title;
	String knownDefect;
	String source;
	String expect;
	String expectNormalized;
	final List<Edit> edits = new ArrayList<>();

	Scenario(String file, int line, String title) {
		this.file = file;
		this.line = line;
		this.title = title;
	}

	public String getFile() {
		return file;
	}

	public int getLine() {
		return line;
	}

	public String getTitle() {
		return title;
	}

	/** The id of the p2pp defect this scenario shows, or null: the scenario is then expected to FAIL until the defect is fixed */
	public String getKnownDefect() {
		return knownDefect;
	}

	public String getSource() {
		return source;
	}

	public List<Edit> getEdits() {
		return edits;
	}

	public String getExpect() {
		return expect;
	}

	public String getExpectNormalized() {
		return expectNormalized;
	}

	@Override
	public String toString() {
		return file + ": " + title;
	}
}
