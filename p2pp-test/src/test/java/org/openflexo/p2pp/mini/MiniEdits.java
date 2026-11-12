package org.openflexo.p2pp.mini;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openflexo.p2pp.mini.MiniModel.Annotation;
import org.openflexo.p2pp.mini.MiniModel.Concept;
import org.openflexo.p2pp.mini.MiniModel.Field;
import org.openflexo.p2pp.mini.MiniModel.Member;
import org.openflexo.p2pp.mini.MiniModel.Method;
import org.openflexo.p2pp.mini.MiniModel.Param;
import org.openflexo.p2pp.mini.MiniModel.TypeRef;
import org.openflexo.p2pp.mini.MiniModel.Unit;

/**
 * The edit commands of the scenarios: changes of a Mini model, applied to the model of a parsed document (never to its nodes: p2pp must
 * find what changed by itself).
 *
 * <pre>
 * rename PATH to NAME
 * set abstract CONCEPT true|false
 * set parent CONCEPT NAME|none
 * set type PATH TYPE|none              (the type of a field or of a parameter; a field without type gets a new one)
 * replace type PATH TYPE                (a field gets a NEW type object, instead of renaming its type)
 * set const PATH true|false             (a field is written with 'const' instead of 'field')
 * add concept NAME [abstract] [extends PARENT] [at N]
 * add annotation CONCEPT NAME [at N]  (written before the concept, one per row)
 * remove annotation CONCEPT NAME
 * add field CONCEPT NAME : TYPE [at N]
 * add method CONCEPT NAME ( [p : T, ...] ) [at N]
 * add param PATH NAME : TYPE [at N]
 * remove PATH
 * move PATH to N                        (N: index in the list once the element is taken out)
 * </pre>
 *
 * A PATH is {@code unit}, {@code A} (a concept), {@code A.x} (a member of A) or {@code A.m.p} (a parameter of the method m of A). Indexes
 * start at 0; without {@code at}, an element is added at the end.
 */
public final class MiniEdits {

	private static final String ID = "([A-Za-z_][A-Za-z0-9_]*)";
	private static final String PATH = "([A-Za-z_][A-Za-z0-9_.]*)";
	private static final String AT = "(?: at (\\d+))?";

	private static final Pattern RENAME = Pattern.compile("rename " + PATH + " to " + ID);
	private static final Pattern SET_ABSTRACT = Pattern.compile("set abstract " + ID + " (true|false)");
	private static final Pattern SET_PARENT = Pattern.compile("set parent " + ID + " " + ID);
	private static final Pattern SET_TYPE = Pattern.compile("set type " + PATH + " " + ID);
	private static final Pattern ADD_ANNOTATION = Pattern.compile("add annotation " + ID + " " + ID + AT);
	private static final Pattern REMOVE_ANNOTATION = Pattern.compile("remove annotation " + ID + " " + ID);
	private static final Pattern SET_CONST = Pattern.compile("set const " + PATH + " (true|false)");
	private static final Pattern REPLACE_TYPE = Pattern.compile("replace type " + PATH + " " + ID);
	private static final Pattern ADD_CONCEPT = Pattern.compile("add concept " + ID + "( abstract)?(?: extends " + ID + ")?" + AT);
	private static final Pattern ADD_FIELD = Pattern.compile("add field " + ID + " " + ID + " : " + ID + AT);
	private static final Pattern ADD_METHOD = Pattern.compile("add method " + ID + " " + ID + " ?\\(([^)]*)\\)" + AT);
	private static final Pattern ADD_PARAM = Pattern.compile("add param " + PATH + " " + ID + " : " + ID + AT);
	private static final Pattern REMOVE = Pattern.compile("remove " + PATH);
	private static final Pattern MOVE = Pattern.compile("move " + PATH + " to (\\d+)");

	private MiniEdits() {
	}

	public static void apply(Unit unit, String command) {
		Matcher m;
		if ((m = RENAME.matcher(command)).matches()) {
			Object target = resolve(unit, m.group(1));
			String name = m.group(2);
			if (target instanceof Unit) {
				((Unit) target).setName(name);
			}
			else if (target instanceof Concept) {
				((Concept) target).setName(name);
			}
			else if (target instanceof Member) {
				((Member) target).setName(name);
			}
			else {
				((Param) target).setName(name);
			}
		}
		else if ((m = SET_ABSTRACT.matcher(command)).matches()) {
			concept(unit, m.group(1)).setAbstract(Boolean.parseBoolean(m.group(2)));
		}
		else if ((m = SET_PARENT.matcher(command)).matches()) {
			concept(unit, m.group(1)).setParentName(m.group(2).equals("none") ? null : m.group(2));
		}
		else if ((m = SET_TYPE.matcher(command)).matches()) {
			Object target = resolve(unit, m.group(1));
			String newType = m.group(2);
			if (target instanceof Field) {
				Field f = (Field) target;
				if (newType.equals("none")) {
					f.setType(null);
				}
				else if (f.getType() == null) {
					f.setType(new TypeRef(newType));
				}
				else {
					f.getType().setName(newType);
				}
			}
			else if (target instanceof Param) {
				((Param) target).setType(newType.equals("none") ? null : newType);
			}
			else {
				throw new IllegalArgumentException("'" + m.group(1) + "' has no type");
			}
		}
		else if ((m = REPLACE_TYPE.matcher(command)).matches()) {
			Object target = resolve(unit, m.group(1));
			if (!(target instanceof Field)) {
				throw new IllegalArgumentException("'" + m.group(1) + "' is not a field");
			}
			((Field) target).setType(new TypeRef(m.group(2)));
		}
		else if ((m = ADD_ANNOTATION.matcher(command)).matches()) {
			insert(concept(unit, m.group(1)).getAnnotations(), new Annotation(m.group(2)), m.group(3));
		}
		else if ((m = REMOVE_ANNOTATION.matcher(command)).matches()) {
			List<Annotation> annotations = concept(unit, m.group(1)).getAnnotations();
			Annotation found = null;
			for (Annotation a : annotations) {
				if (a.getName().equals(m.group(2))) {
					found = a;
				}
			}
			if (found == null || !annotations.remove(found)) {
				throw new IllegalArgumentException("No annotation '" + m.group(2) + "' on " + m.group(1));
			}
		}
		else if ((m = SET_CONST.matcher(command)).matches()) {
			Object target = resolve(unit, m.group(1));
			if (!(target instanceof Field)) {
				throw new IllegalArgumentException("'" + m.group(1) + "' is not a field");
			}
			((Field) target).setConst(Boolean.parseBoolean(m.group(2)));
		}
		else if ((m = ADD_CONCEPT.matcher(command)).matches()) {
			Concept c = new Concept(m.group(1)).setAbstract(m.group(2) != null).setParentName(m.group(3));
			insert(unit.getConcepts(), c, m.group(4));
		}
		else if ((m = ADD_FIELD.matcher(command)).matches()) {
			insert(concept(unit, m.group(1)).getMembers(), new Field(m.group(2), new org.openflexo.p2pp.mini.MiniModel.TypeRef(m.group(3))),
					m.group(4));
		}
		else if ((m = ADD_METHOD.matcher(command)).matches()) {
			Method method = new Method(m.group(2));
			for (String p : m.group(3).split(",")) {
				if (!p.trim().isEmpty()) {
					String[] nt = p.split(":");
					method.getParams().add(new Param(nt[0].trim(), nt[1].trim()));
				}
			}
			insert(concept(unit, m.group(1)).getMembers(), method, m.group(4));
		}
		else if ((m = ADD_PARAM.matcher(command)).matches()) {
			Object target = resolve(unit, m.group(1));
			if (!(target instanceof Method)) {
				throw new IllegalArgumentException("'" + m.group(1) + "' is not a method");
			}
			insert(((Method) target).getParams(), new Param(m.group(2), m.group(3)), m.group(4));
		}
		else if ((m = REMOVE.matcher(command)).matches()) {
			Object target = resolve(unit, m.group(1));
			if (!list(unit, m.group(1)).remove(target)) {
				throw new IllegalArgumentException("Cannot remove " + m.group(1));
			}
		}
		else if ((m = MOVE.matcher(command)).matches()) {
			Object target = resolve(unit, m.group(1));
			List<Object> list = list(unit, m.group(1));
			list.remove(target);
			list.add(Integer.parseInt(m.group(2)), target);
		}
		else {
			throw new IllegalArgumentException("Unknown edit: " + command);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void insert(List list, Object element, String index) {
		if (index == null) {
			list.add(element);
		}
		else {
			list.add(Integer.parseInt(index), element);
		}
	}

	private static Concept concept(Unit unit, String name) {
		Object o = resolve(unit, name);
		if (!(o instanceof Concept)) {
			throw new IllegalArgumentException("'" + name + "' is not a concept");
		}
		return (Concept) o;
	}

	/** The object a path designates */
	static Object resolve(Unit unit, String path) {
		if (path.equals("unit")) {
			return unit;
		}
		String[] segments = path.split("\\.");
		Object current = unit;
		for (String segment : segments) {
			Object found = null;
			for (Object child : children(current)) {
				if (nameOf(child).equals(segment)) {
					if (found != null) {
						throw new IllegalArgumentException("Ambiguous path '" + path + "' at '" + segment + "'");
					}
					found = child;
				}
			}
			if (found == null) {
				throw new IllegalArgumentException("No '" + segment + "' in path '" + path + "'");
			}
			current = found;
		}
		return current;
	}

	/** The list holding the object a path designates */
	@SuppressWarnings("unchecked")
	private static List<Object> list(Unit unit, String path) {
		int dot = path.lastIndexOf('.');
		Object parent = dot < 0 ? unit : resolve(unit, path.substring(0, dot));
		if (parent instanceof Unit) {
			return (List<Object>) (List<?>) ((Unit) parent).getConcepts();
		}
		if (parent instanceof Concept) {
			return (List<Object>) (List<?>) ((Concept) parent).getMembers();
		}
		if (parent instanceof Method) {
			return (List<Object>) (List<?>) ((Method) parent).getParams();
		}
		throw new IllegalArgumentException("'" + path + "' is not in a list");
	}

	private static List<?> children(Object o) {
		if (o instanceof Unit) {
			return ((Unit) o).getConcepts();
		}
		if (o instanceof Concept) {
			return ((Concept) o).getMembers();
		}
		if (o instanceof Method) {
			return ((Method) o).getParams();
		}
		return new ArrayList<>();
	}

	private static String nameOf(Object o) {
		if (o instanceof Concept) {
			return ((Concept) o).getName();
		}
		if (o instanceof Member) {
			return ((Member) o).getName();
		}
		return ((Param) o).getName();
	}
}
