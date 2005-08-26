package com.xpn.xwiki.plugin.charts;

import java.util.ArrayList;

import org.radeox.macro.table.Table;
import org.radeox.macro.table.TableBuilder;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.render.PreTagSubstitution;
import com.xpn.xwiki.render.XWikiRenderer;

public class RadeoxHelper {
	private XWikiRenderer renderer;
	private XWikiContext context;
    private XWikiDocument document;
    private static final String TABLE = "{table}";

    public RadeoxHelper(XWikiDocument document, XWikiContext context) {
        this.context = context;
        this.renderer = context.getWiki().getRenderingEngine().getRenderer("wiki");
        this.document = document;
    }

    /**
     * @return The string content of all the the tables in the document  
     */
	public String[] getTableStrings() {
		ArrayList tables = new ArrayList();
		String content = document.getContent();

        // Remove the content that is inside "{pre}"
		content = (new PreTagSubstitution(context.getUtil(), true)).substitute(content);

		int index = 0, lastIndex = 0; boolean opened = false;
		while (index < content.length()) {
			lastIndex = index;
			index = content.indexOf(TABLE, index+TABLE.length());
			if (index == -1) break;
			if (opened) {
				tables.add(content.substring(lastIndex+TABLE.length(), index).trim());
			}
			opened = !opened;
		}
		return (String[])tables.toArray(new String[tables.size()]);
	}

	/**
	 * @return All the radeox tables in the document
	 */
	public Table[] getTables() {
		String[] tableStrings = getTableStrings();
		Table[] tables = new Table[tableStrings.length];
		for (int i = 0; i<tableStrings.length;i++) {
			tables[i] = buildTable(tableStrings[i]);
		}
		return tables;
	}

	/**
	 * @return The string content of the given table, or null, when no such table exists 
	 */
	public String getTableString(int idx) {
		String content = document.getContent();

        // Remove the content that is inside "{pre}"
		content = (new PreTagSubstitution(context.getUtil(), true)).substitute(content);

		int index = Integer.MIN_VALUE;
		int lastIndex = Integer.MIN_VALUE;
		int i = -1;
		boolean opened = false;
		while (index < content.length() && i<idx) {
			lastIndex = index;
			index = content.indexOf(TABLE, index+TABLE.length());
			if (index == -1) break;
			if (opened) {
				i++;
			}
			opened = !opened;
		}
		if (i == idx) {
			return content.substring(lastIndex+TABLE.length(), index).trim();
		} else {
            return null;
		}
	}

	/**
	 * @return The radeox table coresponding to the given index, or null, when no such table exists 
	 */
	public Table getTable(int idx) {
		String tableString = getTableString(idx);
		if (tableString != null) {
			return buildTable(tableString);
		} else {
			return null;
		}
	}
	
	/**
	 * @return The HTML representation of the given table-content
	 */
	public String getRenderedTable(int idx) {
		String tableString = getTableString(idx);
		if (tableString != null) {
			return renderer.render("{table}\n"+tableString+"\n{table}", null, null, context);
		} else {
			return null;
		}
	}

	private Table buildTable(String content) {
	    content = content.trim() + "\n";
	    Table table = TableBuilder.build(content);
	    table.calc();
	    return table;
	}
}
