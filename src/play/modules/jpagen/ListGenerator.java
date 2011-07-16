package play.modules.jpagen;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.Logger;
import play.Play;
import play.libs.IO;
import play.templates.GroovyTemplateCompiler;
import play.templates.JavaExtensions;
import play.templates.Template;
import play.vfs.VirtualFile;

public class ListGenerator {

	public static void main(String[] args) throws Exception {

		try {

			// init Play Framework
			File root = new File(System.getProperty("application.path"));
			Play.init(root, System.getProperty("play.id", ""));
			Thread.currentThread().setContextClassLoader(Play.classloader);

			String driver = Play.configuration.getProperty("db.driver");
			String url = Play.configuration.getProperty("db.url");
			String user = Play.configuration.getProperty("db.user");
			String password = Play.configuration.getProperty("db.pass", "");
			String[] excludePattern = Play.configuration.getProperty("jpagen.excludes", "").split(",");
			String[] includePattern = Play.configuration.getProperty("jpagen.includes", "").split(",");
			
			Logger.info("Exclude pattern: %s", Arrays.asList(excludePattern));
			Logger.info("Include pattern: %s", Arrays.asList(includePattern));
			String temp = Play.configuration.getProperty("db.default.schema", "");
			String[] schemas = null;
			if (temp.length() > 0) {
				schemas = temp.split(",");
			}
			String templateListPath = Play.configuration.getProperty("jpagen.template.list", "jpagen/list.tmpl");

			Logger.info("driver: %s url: %s user: %s password: %s", driver, url, user, password);
			SimpleDB db = new SimpleDB(driver, url, user, password);
			Connection con = db.getConnection();

			// List template
			VirtualFile templateListFile = VirtualFile.search(Play.templatesPath, templateListPath);
			Logger.info("templateFile: %s", templateListFile.getName());
			GroovyTemplateCompiler compiler = new GroovyTemplateCompiler();
			Template listTemplate = compiler.compile(templateListFile);

			// Generate table_list.conf from db metadata
			DatabaseMetaData meta = con.getMetaData();
			List<String> tableList = new ArrayList<String>();
			for (String schema : schemas) {
				ResultSet rs = meta.getTables(null, schema, null, new String[] { "TABLE" });
				while (rs.next()) {
					boolean bExclude = false;
					boolean bInclude = true;
					String table = schema + "." + rs.getString("TABLE_NAME").trim();
					
					if (excludePattern != null && excludePattern.length > 0) {
						for (String exclude : excludePattern) {
							if (exclude.length() > 0 && Pattern.compile(Pattern.quote(exclude)).matcher(table).find()) {
								bExclude = true;
								Logger.info("Table %s excluded", table);
								continue;
							}
						}
					}
					if (includePattern != null && includePattern.length > 0) {
						if(bExclude){
							bInclude = false;
						}else{
							for (String include : includePattern) {
								if (include.length() > 0 && !Pattern.compile(Pattern.quote(include)).matcher(table).find()) {
									Logger.info("Table %s excluded", table);
									bInclude = false;
									continue;
								}
							}
						}
					}

					if (!bExclude && bInclude) {
						tableList.add(table);
						Logger.info("Added table %s", table);
					}
				}
				Collections.sort(tableList);
				rs.close();
			}
			db.close();

			// Table List
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("url", url);
			params.put("tables", tableList);
			String output = listTemplate.render(params);
			String relativePath = "conf/table_list.conf";
			if (!VirtualFile.fromRelativePath(relativePath).exists()) {
				File entityFile = VirtualFile.fromRelativePath(relativePath).getRealFile();
				IO.writeContent(output, entityFile);
				Logger.info("Process finished: Generated conf/table_list.conf with %s tables", tableList.size());
			} else {
				Logger.warn("File %s already exists...NOT OVERWRITING", relativePath);
			}

		} catch (Exception e) {
			Logger.error(e, e.getMessage());
		}

		System.exit(0);
	}

}