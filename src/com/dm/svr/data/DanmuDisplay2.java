package com.dm.svr.data;

import org.json.JSONArray;
import org.json.JSONObject;

import dmkp.dm.data.DanmuText.Danmu;

public class DanmuDisplay2 {
	
	public DanmuDisplay2() {
		data = new JSONArray();
	}
	
	public JSONObject ToJSON() {
		JSONObject o = new JSONObject();
		o.put("InstrumentID", InstrumentID);
		o.put("data", data);
		return o;
	}

	public void AddDanmu(Danmu d) {
		JSONObject o = new JSONObject();
		o.put("UserName", d.UserName);
		o.put("UserID", d.UserID);
		o.put("InstrumentID", d.InstrumentID);
		o.put("Text", d.Text);
		o.put("DanmuClass", d.DanmuClass);
		o.put("DanmuID", d.DanmuID);
		o.put("_METADATA_", d._METADATA_);
		data.put(o);
	}
	
	public String InstrumentID = "";
	JSONArray data;
}
