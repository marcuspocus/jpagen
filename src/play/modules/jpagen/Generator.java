package play.modules.jpagen;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.Play;
import play.libs.IO;
import play.templates.GroovyTemplateCompiler;
import play.templates.JavaExtensions;
import play.templates.Template;
import play.vfs.VirtualFile;

public class Generator {
	private static List<String> keyword = new ArrayList<String>();

	public static void main(String[] args) throws Exception {

		// Load all forbidden keywords
		init();
		try {

			// init Play Framework
			File root = new File(System.getProperty("application.path"));
			Play.init(root, System.getProperty("play.id", ""));
			Thread.currentThread().setContextClassLoader(Play.classloader);

			// Load properties
			String driver = Play.configuration.getProperty("db.driver");
			String url = Play.configuration.getProperty("db.url");
			String user = Play.configuration.getProperty("db.user");
			String password = Play.configuration.getProperty("db.pass", "");
			String temp = Play.configuration.getProperty("db.default.schema", "");
			String[] schemas = null;
			if (temp.length() > 0) {
				schemas = temp.split(",");
			}
			
			String mode = Play.configuration.getProperty("jpagen.mode");
			if (mode == null || mode.trim().length() == 0) {
				throw new ExceptionInInitializerError("ERROR: Parameter jpagen.mode is mandatory!");
			}
			if (ModeEnum.valueOf(mode.toUpperCase()) == null) {
				throw new ExceptionInInitializerError("ERROR: Parameter jpagen.mode provided is not supported: MYSQL or ORACLE only!");
			}
			
			String packageName = Play.configuration.getProperty("jpagen.package.name", "models");
			String templatePath = Play.configuration.getProperty("jpagen.template.entity", "jpagen/entity.tmpl");
			String templateIdPath = Play.configuration.getProperty("jpagen.template.idClass", "jpagen/idClass.tmpl");


			Logger.info("driver: %s url: %s user: %s password: %s", driver, url, user, password);

			// Open DB Connection
			SimpleDB db = new SimpleDB(driver, url, user, password);
			Connection con = db.getConnection();

			// Entity template
			VirtualFile templateFile = VirtualFile.search(Play.templatesPath, templatePath);
			Logger.info("templateFile: %s", templateFile.getName());
			GroovyTemplateCompiler compiler1 = new GroovyTemplateCompiler();
			Template entityTemplate = compiler1.compile(templateFile);

			// Composite Key template
			VirtualFile templateIdFile = VirtualFile.search(Play.templatesPath, templateIdPath);
			Logger.info("templateIdFile: %s", templateIdFile.getName());
			GroovyTemplateCompiler compiler2 = new GroovyTemplateCompiler();
			Template idTemplate = compiler2.compile(templateIdFile);

			// Get table list from conf/table_list.conf or from metadata
			DatabaseMetaData meta = con.getMetaData();
			List<String> tableList = new ArrayList<String>();
			if (Play.getVirtualFile("conf/table_list.conf").exists()) {
				Logger.info("Using table_list.conf");
				tableList = IO.readLines(Play.getFile("conf/table_list.conf"));
				Logger.info("TableList contains %s lines (including comments and empty lines)", tableList.size());
			} else {
				Logger.info("Using metadata");
				ResultSet rs = null;
				if (schemas != null) {
					for (String schema : schemas) {
						rs = meta.getTables(null, schema, null, new String[] { "TABLE" });
						while (rs.next()) {
							tableList.add(schema + "." + rs.getString("TABLE_NAME"));
						}
						rs.close();
					}
				} else {
					rs = meta.getTables(null, null, null, new String[] { "TABLE" });
					while (rs.next()) {
						tableList.add(rs.getString("TABLE_NAME"));
					}
					rs.close();
				}
			}

			int count = 0;
			int idCount = 0;
			int skipCount = 0;
			int skipIdCount = 0;

			// Iterate over table list
			for (String tableName : tableList) {
				if (tableName == null || tableName.trim().length() == 0 || tableName.startsWith("#")) {
					Logger.debug("skipping line...");
					continue;
				}

				String schema = "";
				if (tableName.indexOf(".") > 0) {
					Logger.info("tableName: %s", tableName);
					String[] names = tableName.split("\\.");
					Logger.debug("%s", Arrays.asList(names));
					schema = names[0];
					tableName = names[1];
				}else{
					Logger.info("No schema for table %s", tableName);
				}

				Logger.debug("Working on table %s", schema + "." + tableName);

				Table table = new Table();
				table.tableName = tableName;
				table.packageName = packageName;
				table.className = JavaExtensions.camelCase(table.tableName.toLowerCase().replaceAll("_", " "));

				List<String> keys = new ArrayList<String>();
				ResultSet rsKeys = meta.getPrimaryKeys(null, (schema.length() == 0 ? null : schema), tableName);
				while (rsKeys.next()) {
					String key = rsKeys.getString("COLUMN_NAME");
					keys.add(key);
					Logger.info("Table: %s, primaryKey: %s", (schema.length() > 0 ? schema + "." : "") + tableName, key);
				}
				rsKeys.close();
				if (keys.size() == 0) {
					table.isIdLess = true;
					table.extend = "Model";
				}

				PreparedStatement ps = null;
				if (mode.equalsIgnoreCase(ModeEnum.MYSQL.name())) {
					ps = con.prepareStatement("select * from " + (schema.length() > 0 ? schema + "." : "") + tableName + " limit 0");
				}
				if (mode.equalsIgnoreCase(ModeEnum.ORACLE.name())) {
					ps = con.prepareStatement("select * from " + (schema.length() > 0 ? schema + "." : "") + tableName + " where rownum < 2");
				}
				ResultSet rs = ps.executeQuery();
				ResultSetMetaData tm = rs.getMetaData();

				// Setting Composite Keys class
				IdClass idClass = new IdClass();
				idClass.className = table.className + "Id";
				idClass.packageName = table.packageName;

				// Iterating on columns
				int cc = tm.getColumnCount();
				for (int i = 1; i <= cc; i++) {

					Column column = new Column();
					column.columnName = tm.getColumnName(i);
					column.columnType = tm.getColumnClassName(i).substring(tm.getColumnClassName(i).lastIndexOf(".") + 1);
					if (column.columnType.equals("[B")) {
						Logger.error("BLOB??? %s", tm.getColumnClassName(i));
						column.columnType = "Blob";
					}
					if (column.columnType.equals("String")) {
						column.max = tm.getPrecision(i);
					}
					column.columnPropertyName = JavaExtensions.camelCase(column.columnName.toLowerCase().replaceAll("_", " "));
					column.columnPropertyName = normalizeColumnName(column.columnPropertyName.substring(0, 1).toLowerCase() + column.columnPropertyName.substring(1));

					if (tm.isNullable(i) == tm.columnNoNulls) {
						column.nullable = false;
					}
					if (keys.contains(column.columnName)) {
						column.primary = true;
						if (keys.size() > 1) {
							idClass.keys.add(column);
						}
					}
					if (column.columnPropertyName.equals("id")) {
						table.extend = "GenericModel";
					}
					table.columns.add(column);
				}
				rs.close();
				ps.close();

				// Composite Key
				if (keys.size() > 1) {
					table.idClass = table.className + "Id.class";
					Logger.info("Key: %s", idClass.className);
					String relativePath = "app/" + packageName.replace('.', '/') + "/" + table.className + "Id.java";
					if (!VirtualFile.fromRelativePath(relativePath).exists()) {
						File idFile = VirtualFile.fromRelativePath(relativePath).getRealFile();
						Map<String, Object> params = new HashMap<String, Object>();
						params.put("idClass", idClass);
						String output = idTemplate.render(params);
						IO.writeContent(output, idFile);
						idCount++;
					} else {
						skipIdCount++;
						Logger.warn("File %s already exists...NOT OVERWRITING", relativePath);
					}
				}

				// Entity
				String relativePath = "app/" + packageName.replace('.', '/') + "/" + table.className + ".java";
				if (!VirtualFile.fromRelativePath(relativePath).exists()) {
					Map<String, Object> params = new HashMap<String, Object>();
					params.put("table", table);
					String output = entityTemplate.render(params);
					File entityFile = VirtualFile.fromRelativePath(relativePath).getRealFile();
					IO.writeContent(output, entityFile);
					count++;
				} else {
					skipCount++;
					Logger.warn("File %s already exists...NOT OVERWRITING", relativePath);
				}
				Logger.debug("Table done: %s", tableName);
			}

			db.close();

			Logger.info("Process finished: %s Entities and %s Composite Keys generated", count, idCount);
			Logger.warn("%s Entities and %s Composite Keys SKIPPED", skipCount, skipIdCount);
		} catch (Exception e) {
			Logger.error(e, e.getMessage());
		}

		System.exit(0);
	}

	private static String normalizeColumnName(String column) {
		if (keyword.contains(column)) {
			return column + "Col";
		} else {
			return column;
		}
	}

	private static void init() {
		keyword.add("abstract");
		keyword.add("assert");
		keyword.add("boolean");
		keyword.add("break");
		keyword.add("byte");
		keyword.add("case");
		keyword.add("catch");
		keyword.add("char");
		keyword.add("class");
		keyword.add("const");
		keyword.add("continue");
		keyword.add("default");
		keyword.add("do");
		keyword.add("double");
		keyword.add("else");
		keyword.add("entityId");
		keyword.add("enum");
		keyword.add("extends");
		keyword.add("final");
		keyword.add("finally");
		keyword.add("float");
		keyword.add("for");
		keyword.add("goto");
		keyword.add("if");
		keyword.add("implements");
		keyword.add("import");
		keyword.add("instanceof");
		keyword.add("int");
		keyword.add("interface");
		keyword.add("long");
		keyword.add("native");
		keyword.add("new");
		keyword.add("package");
		keyword.add("private");
		keyword.add("protected");
		keyword.add("public");
		keyword.add("return");
		keyword.add("short");
		keyword.add("static");
		keyword.add("strictfp");
		keyword.add("super");
		keyword.add("switch");
		keyword.add("synchronized");
		keyword.add("this");
		keyword.add("throw");
		keyword.add("throws");
		keyword.add("transient");
		keyword.add("try");
		keyword.add("void");
		keyword.add("volatile");
		keyword.add("while");
	}
}
