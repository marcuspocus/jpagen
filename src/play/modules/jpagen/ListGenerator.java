package play.modules.jpagen;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
			String schema = Play.configuration.getProperty("db.default.schema", "");
			String templateListPath = Play.configuration.getProperty("jpagen.template.list", "db/list.tmpl");

			Logger.info("driver: %s url: %s user: %s password: %s", driver, url, user, password);
			SimpleDB db = new SimpleDB(driver, url, user, password);
			Connection con = db.getConnection();

			// List template
			VirtualFile templateListFile = VirtualFile.search(Play.templatesPath, templateListPath);
			Logger.info("templateFile: %s", templateListFile.getName());
			GroovyTemplateCompiler compiler = new GroovyTemplateCompiler();
			Template listTemplate = compiler.compile(templateListFile);
			
			// Get table list from conf/table_list.conf or from metadata
			DatabaseMetaData meta = con.getMetaData();
			List<String> tableList = new ArrayList<String>();
			ResultSet rs = meta.getTables(null, schema, null, new String[] { "TABLE" });
			while(rs.next()){
				tableList.add(rs.getString("TABLE_NAME").trim());
			}
			Collections.sort(tableList);
			rs.close();
			db.close();

			// Table List 
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("schema", schema);
			params.put("url", url);
			params.put("tables", tableList);
			String output = listTemplate.render(params);
			String relativePath = "conf/table_list.conf";
			if(!VirtualFile.fromRelativePath(relativePath).exists()){
				File entityFile = VirtualFile.fromRelativePath(relativePath).getRealFile();
				IO.writeContent(output, entityFile);
				Logger.info("Process finished: Generated conf/table_list.conf with %s tables", tableList.size());
			}else{
				Logger.warn("File %s already exists...NOT OVERWRITING", relativePath);
			}

		} catch (Exception e) {
			Logger.error(e, e.getMessage());
		}

		System.exit(0);
	}

}