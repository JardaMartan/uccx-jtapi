package com.cisco.jmartan.jtapi.hzs;

import java.io.Serializable;

@SuppressWarnings("serial")
public class CallerInfo implements Serializable {
	public String displayName = "Neznamy";
	public String displayNameUnicode = "Neznámý";
	
	public CallerInfo(String displayName, String displayNameUnicode) {
		this.displayName = displayName;
		this.displayNameUnicode = displayNameUnicode;
	}
}
