package org.openflexo.p2pp.mini;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openflexo.p2pp.testing.ResourceFiles;


/**
 * The sources of {@code src/test/resources/mini}, as the edit matrix uses them: those that p2pp reproduces when untouched. A source marked
 * with a known defect is left out: the corpus test already covers it, and its layout would put its own defect in every case.
 */
public final class MiniCorpus {

	private MiniCorpus() {
	}

	public static Map<String, String> load() throws IOException {
		List<File> files = ResourceFiles.list(MiniCorpus.class, "/mini", ".mini");
		Map<String, String> returned = new LinkedHashMap<>();
		for (File f : files) {
			String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			String source = text;
			if (!source.startsWith("// p2pp-known-defect:")) {
				returned.put(f.getName(), source);
			}
		}
		return returned;
	}

	public static List<String> names() throws IOException {
		return new ArrayList<>(load().keySet());
	}
}
