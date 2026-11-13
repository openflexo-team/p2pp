package org.openflexo.p2pp.testing.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * One scenario of a {@code .p2pt} file: a source, then one or several steps, each made of edits applied to the model of the SAME document
 * and of the pretty-print expected afterwards.
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
 * --- edit                        (a next step: edits applied to the document as it is, then its expectations)
 * ...
 * --- expect
 * ...
 * </pre>
 *
 * An {@code edit} block that comes after an {@code expect} one starts a new step; the first step may have no edit (a source that is only
 * pretty-printed). A block ends at the next {@code ---} or {@code ===} row; its trailing blank rows and its final line separator are not part of it.
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

	/** Edits applied to the document, then what its pretty-prints must be */
	public static final class Step {
		final List<Edit> edits = new ArrayList<>();
		String expect;
		String expectNormalized;

		public List<Edit> getEdits() {
			return edits;
		}

		public String getExpect() {
			return expect;
		}

		public String getExpectNormalized() {
			return expectNormalized;
		}

		boolean isClosed() {
			return expect != null || expectNormalized != null;
		}
	}

	private final String file;
	private final int line;
	private final String title;
	String knownDefect;
	String source;
	final List<Step> steps = new ArrayList<>();

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

	public List<Step> getSteps() {
		return steps;
	}

	@Override
	public String toString() {
		return file + ": " + title;
	}
}
