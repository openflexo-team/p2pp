package org.openflexo.p2pp.testing.scenario;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader of {@code .p2pt} files (see {@link Scenario} for the format)
 */
public final class ScenarioFile {

	private ScenarioFile() {
	}

	public static List<Scenario> read(File file) throws IOException {
		List<String> rows = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		List<Scenario> returned = new ArrayList<>();
		Scenario current = null;
		String block = null;
		int blockLine = 0;
		List<String> blockRows = new ArrayList<>();
		for (int i = 0; i <= rows.size(); i++) {
			String row = i < rows.size() ? rows.get(i) : "=== end";
			boolean newScenario = row.startsWith("=== ");
			boolean newBlock = row.startsWith("--- ");
			if (newScenario || newBlock) {
				if (current != null && block != null) {
					close(current, block, blockRows, blockLine);
				}
				block = null;
				blockRows = new ArrayList<>();
				if (newScenario && i < rows.size()) {
					current = new Scenario(file.getName(), i + 1, row.substring(4).trim());
					returned.add(current);
				}
				else if (newBlock) {
					if (current == null) {
						throw new IllegalArgumentException(file.getName() + ":" + (i + 1) + ": a block before any scenario");
					}
					block = row.substring(4).trim();
					blockLine = i + 1;
				}
			}
			else if (block != null) {
				blockRows.add(row);
			}
			else if (current != null && row.startsWith("known-defect:")) {
				current.knownDefect = row.substring("known-defect:".length()).trim();
			}
			else if (current != null && !row.trim().isEmpty()) {
				throw new IllegalArgumentException(file.getName() + ":" + (i + 1) + ": unexpected row outside a block: " + row);
			}
		}
		for (Scenario s : returned) {
			if (s.source == null || s.expect == null) {
				throw new IllegalArgumentException(file.getName() + ":" + s.getLine() + ": scenario '" + s.getTitle()
						+ "' needs a source block and an expect block");
			}
		}
		return returned;
	}

	private static void close(Scenario scenario, String block, List<String> rows, int blockLine) {
		// blockLine is the row of the block header: the row k of the block is at blockLine + 1 + k
		switch (block) {
			case "edit":
				for (int k = 0; k < rows.size(); k++) {
					String command = rows.get(k).trim();
					if (!command.isEmpty() && !command.startsWith("#")) {
						scenario.edits.add(new Scenario.Edit(command, blockLine + 1 + k));
					}
				}
				break;
			case "source":
				scenario.source = text(rows);
				break;
			case "expect":
				scenario.expect = text(rows);
				break;
			case "expect-normalized":
				scenario.expectNormalized = text(rows);
				break;
			default:
				throw new IllegalArgumentException(scenario.getFile() + ":" + blockLine + ": unknown block '" + block + "'");
		}
	}

	private static String text(List<String> rows) {
		int end = rows.size();
		while (end > 0 && rows.get(end - 1).isEmpty()) {
			end--;
		}
		return String.join("\n", rows.subList(0, end));
	}
}
