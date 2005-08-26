package com.xpn.xwiki.plugin.charts.params;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import com.xpn.xwiki.plugin.charts.exceptions.ParamException;

public class ColorChartParam extends ChoiceChartParam {
	private Map choices = new HashMap();

	public ColorChartParam(String name) {
		super(name);
	}

	public Class getType() {
		return Color.class;
	}
	
	public void init() {
		addChoice("black", new Color(0x000000));
		addChoice("silver", new Color(0xC0C0C0));
		addChoice("gray", new Color(0x808080));
		addChoice("white", new Color(0xFFFFFF));
		addChoice("maroon", new Color(0x800000));
		addChoice("red", new Color(0xFF0000));
		addChoice("purple", new Color(0x800080));
		addChoice("fuchsia", new Color(0xFF00FF));
		addChoice("green", new Color(0x008000));
		addChoice("lime", new Color(0x00FF00));
		addChoice("olive", new Color(0x808000));
		addChoice("yellow", new Color(0xFFFF00));
		addChoice("navy", new Color(0x000080));
		addChoice("blue", new Color(0x0000FF));
		addChoice("teal", new Color(0x008080));
		addChoice("aqua", new Color(0x00FFFF));
	}

	public Object convert(String value) throws ParamException {
		try {
			return super.convert(value);
		} catch (ParamException e) {
			if (value.length() == 0 || value.charAt(0) != '#') {
	        	throw new ParamException("Color parameter "+getName()+" must start with #");
	        }
			
	        value = value.substring(1);
	        int intValue;
	        try {
	        	intValue = Integer.parseInt(value, 16);
	        } catch (NumberFormatException nfe) {
				throw new ParamException("Color parameter "+getName()+" is not a valid hexadecimal number");
	        }
	        return new Color(intValue);			
		}		
	}
}
