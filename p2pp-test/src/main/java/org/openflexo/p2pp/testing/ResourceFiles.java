package org.openflexo.p2pp.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lists the files of a resource directory of the test classpath ({@code src/test/resources/...}): the sources of a corpus, the scenarios.
 */
public final class ResourceFiles {

	private ResourceFiles() {
	}

	/** The files of a directory of the classpath, sorted by name, whose name ends with the suffix ({@code ".p2pt"}) */
	public static List<File> list(Class<?> anchor, String directory, String suffix) {
		File[] files = new File(anchor.getResource(directory).getFile()).listFiles((dir, name) -> name.endsWith(suffix));
		Arrays.sort(files);
		return new ArrayList<>(Arrays.asList(files));
	}
}
