package org.openflexo.p2pp.mini;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.openflexo.p2pp.mini.MiniModel.Concept;
import org.openflexo.p2pp.testing.matrix.EditCase;
import org.openflexo.p2pp.mini.MiniModel.Field;
import org.openflexo.p2pp.mini.MiniModel.Member;
import org.openflexo.p2pp.mini.MiniModel.Method;
import org.openflexo.p2pp.mini.MiniModel.Param;
import org.openflexo.p2pp.mini.MiniModel.Unit;

/**
 * Generates every atomic edit of the model of a Mini source, with its position class: rename each object (to a shorter and a longer
 * name), flip each condition, change each type, add an object at every index of every list, remove each object, move each object to every
 * other index.
 * <p>
 * The names that are created ({@code gen}, {@code Gen}) must not be in the sources.
 */
public final class EditGenerator {

	private final String sourceName;
	private final String source;
	private final List<EditCase> cases = new ArrayList<>();

	private EditGenerator(String sourceName, String source) {
		this.sourceName = sourceName;
		this.source = source;
	}

	public static List<EditCase> generate(String sourceName, String source) throws MiniParseException {
		EditGenerator g = new EditGenerator(sourceName, source);
		g.generate(MiniDocument.parse(source).getUnit());
		return g.cases;
	}

	private void add(String kind, String position, String edit, String inverse) {
		cases.add(new EditCase(sourceName, source, kind, position, Collections.singletonList(edit),
				inverse == null ? null : Collections.singletonList(inverse)));
	}

	private void generate(Unit unit) {
		renames("unit", "unit", unit.getName());
		for (Concept c : unit.getConcepts()) {
			renames(c.getName(), parentPath(c.getName()), c.getName());
			add("set-abstract", "-", "set abstract " + c.getName() + " " + !c.isAbstract(), "set abstract " + c.getName() + " " + c.isAbstract());
			String oldParent = c.getParentName() == null ? "none" : c.getParentName();
			for (String newParent : c.getParentName() == null ? Arrays.asList("Gen") : Arrays.asList("none", "Gen")) {
				add("set-parent", "-", "set parent " + c.getName() + " " + newParent, "set parent " + c.getName() + " " + oldParent);
			}
			for (Member m : c.getMembers()) {
				String path = c.getName() + "." + m.getName();
				renames(path, c.getName(), m.getName());
				if (m instanceof Field) {
					types(path, ((Field) m).getType().getName());
				}
				else {
					for (Param p : ((Method) m).getParams()) {
						String ppath = path + "." + p.getName();
						renames(ppath, path, p.getName());
						types(ppath, p.getType());
					}
				}
			}
		}

		// Concepts of the unit
		int n = unit.getConcepts().size();
		for (int i = 0; i <= n; i++) {
			add("add-concept", addPosition(n, i), "add concept Gen at " + i, "remove Gen");
			add("add-concept", addPosition(n, i), "add concept Gen abstract extends Z at " + i, "remove Gen");
		}
		for (int i = 0; i < n; i++) {
			String path = unit.getConcepts().get(i).getName();
			add("remove-concept", removePosition(n, i), "remove " + path, null);
			moves("move-concept", path, n, i);
		}

		// Members of the concepts
		for (Concept c : unit.getConcepts()) {
			int m = c.getMembers().size();
			for (int i = 0; i <= m; i++) {
				String pos = addPosition(m, i);
				add("add-member", pos, "add field " + c.getName() + " gen : int at " + i, "remove " + c.getName() + ".gen");
				add("add-member", pos, "add method " + c.getName() + " gen() at " + i, "remove " + c.getName() + ".gen");
				add("add-member", pos, "add method " + c.getName() + " gen(a : int, b : string) at " + i, "remove " + c.getName() + ".gen");
			}
			for (int i = 0; i < m; i++) {
				String path = c.getName() + "." + c.getMembers().get(i).getName();
				add("remove-member", removePosition(m, i), "remove " + path, null);
				moves("move-member", path, m, i);
			}

			// Parameters of the methods
			for (Member member : c.getMembers()) {
				if (member instanceof Method) {
					Method method = (Method) member;
					String mpath = c.getName() + "." + method.getName();
					int p = method.getParams().size();
					for (int i = 0; i <= p; i++) {
						add("add-param", addPosition(p, i), "add param " + mpath + " gen : int at " + i, "remove " + mpath + ".gen");
					}
					for (int i = 0; i < p; i++) {
						String path = mpath + "." + method.getParams().get(i).getName();
						add("remove-param", removePosition(p, i), "remove " + path, null);
						moves("move-param", path, p, i);
					}
				}
			}
		}
	}

	private static String parentPath(String path) {
		int dot = path.lastIndexOf('.');
		return dot < 0 ? "unit" : path.substring(0, dot);
	}

	/** Rename of the object at path, whose name is oldName; parentPath is the path of its container (or "unit") */
	private void renames(String path, String parentPath, String oldName) {
		for (String newName : Arrays.asList("q", "RenamedWithALongName")) {
			String renamedPath = path.equals("unit") ? "unit" : (path.lastIndexOf('.') < 0 ? "" : path.substring(0, path.lastIndexOf('.') + 1)) + newName;
			add("rename", "-", "rename " + path + " to " + newName, "rename " + renamedPath + " to " + oldName);
		}
	}

	private void types(String path, String oldType) {
		for (String newType : Arrays.asList("q", "ALongTypeName")) {
			add("set-type", "-", "set type " + path + " " + newType, "set type " + path + " " + oldType);
		}
	}

	private void moves(String kind, String path, int size, int index) {
		String name = path.substring(path.lastIndexOf('.') + 1);
		String listPath = path.contains(".") ? path.substring(0, path.lastIndexOf('.') + 1) : "";
		for (int target = 0; target < size; target++) {
			if (target != index) {
				String pos = target == 0 ? "first" : target == size - 1 ? "last" : "middle";
				add(kind, pos, "move " + path + " to " + target, "move " + listPath + name + " to " + index);
			}
		}
	}

	private static String addPosition(int size, int index) {
		return size == 0 ? "empty" : index == 0 ? "first" : index == size ? "last" : "middle";
	}

	private static String removePosition(int size, int index) {
		return size == 1 ? "only" : index == 0 ? "first" : index == size - 1 ? "last" : "middle";
	}
}
