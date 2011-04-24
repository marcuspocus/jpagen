package play.modules.jpagen;

import java.util.ArrayList;
import java.util.List;

public class Table {

	public String tableName;
	public String className;
	public String packageName;
	public String idClass;
	public String extend = "GenericModel";
	public Boolean isIdLess = false;
	public List<Column> columns = new ArrayList<Column>();
}
