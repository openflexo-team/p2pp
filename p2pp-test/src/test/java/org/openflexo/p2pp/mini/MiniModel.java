package org.openflexo.p2pp.mini;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The model of the Mini language, the fixture language of the p2pp tests: plain objects, nothing from PAMELA.
 *
 * <pre>
 * unit U {
 *   abstract concept A extends B {
 *     field x : int;
 *     method m(a : int, b : string);
 *   }
 * }
 * </pre>
 *
 * Each construct is there to exercise one kind of pretty-printable contents (see {@link MiniNodes}).
 */
public final class MiniModel {

	private MiniModel() {
	}

	public static Unit unit(String name, Concept... concepts) {
		Unit returned = new Unit(name);
		returned.getConcepts().addAll(Arrays.asList(concepts));
		return returned;
	}

	public static Concept concept(String name, Member... members) {
		Concept returned = new Concept(name);
		returned.getMembers().addAll(Arrays.asList(members));
		return returned;
	}

	public static Field field(String name, String type) {
		return new Field(name, new TypeRef(type));
	}

	public static Method method(String name, Param... params) {
		Method returned = new Method(name);
		returned.getParams().addAll(Arrays.asList(params));
		return returned;
	}

	public static Param param(String name, String type) {
		return new Param(name, type);
	}

	/** Canonical, indented dump of a model, used to compare two models */
	public interface Dumpable {
		void dump(StringBuilder sb, String indent);

		default String dump() {
			StringBuilder sb = new StringBuilder();
			dump(sb, "");
			return sb.toString();
		}
	}

	public static class Unit implements Dumpable {
		private String name;
		private final List<Concept> concepts = new ArrayList<>();

		public Unit(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<Concept> getConcepts() {
			return concepts;
		}

		@Override
		public void dump(StringBuilder sb, String indent) {
			sb.append(indent).append("unit ").append(name).append("\n");
			for (Concept c : concepts) {
				c.dump(sb, indent + "  ");
			}
		}
	}

	public static class Concept implements Dumpable {
		private String name;
		private boolean isAbstract;
		private String parentName;
		private final List<Member> members = new ArrayList<>();

		public Concept(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isAbstract() {
			return isAbstract;
		}

		public Concept setAbstract(boolean isAbstract) {
			this.isAbstract = isAbstract;
			return this;
		}

		public String getParentName() {
			return parentName;
		}

		public Concept setParentName(String parentName) {
			this.parentName = parentName;
			return this;
		}

		public List<Member> getMembers() {
			return members;
		}

		@Override
		public void dump(StringBuilder sb, String indent) {
			sb.append(indent).append("concept ").append(name);
			if (isAbstract) {
				sb.append(" abstract");
			}
			if (parentName != null) {
				sb.append(" extends ").append(parentName);
			}
			sb.append("\n");
			for (Member m : members) {
				m.dump(sb, indent + "  ");
			}
		}
	}

	public abstract static class Member implements Dumpable {
		private String name;

		protected Member(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class Field extends Member {
		private TypeRef type;

		public Field(String name, TypeRef type) {
			super(name);
			this.type = type;
		}

		public TypeRef getType() {
			return type;
		}

		public void setType(TypeRef type) {
			this.type = type;
		}

		@Override
		public void dump(StringBuilder sb, String indent) {
			sb.append(indent).append("field ").append(getName()).append(" : ").append(type != null ? type.getName() : "?").append("\n");
		}
	}

	public static class Method extends Member {
		private final List<Param> params = new ArrayList<>();

		public Method(String name) {
			super(name);
		}

		public List<Param> getParams() {
			return params;
		}

		@Override
		public void dump(StringBuilder sb, String indent) {
			sb.append(indent).append("method ").append(getName()).append("\n");
			for (Param p : params) {
				p.dump(sb, indent + "  ");
			}
		}
	}

	public static class Param implements Dumpable {
		private String name;
		private String type;

		public Param(String name, String type) {
			this.name = name;
			this.type = type;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		@Override
		public void dump(StringBuilder sb, String indent) {
			sb.append(indent).append("param ").append(name).append(" : ").append(type).append("\n");
		}
	}

	/** The type of a field: an object of its own, so that a field pretty-prints it through a single-child contents */
	public static class TypeRef {
		private String name;

		public TypeRef(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}
}
