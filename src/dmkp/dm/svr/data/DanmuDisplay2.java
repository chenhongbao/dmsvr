package dmkp.dm.svr.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DanmuDisplay2 {
	
public static class Danmu2 {
		
		public final static String DataType = "DanmuText";
		
		public Danmu2() {}
		
		public static Danmu2 Parse(JSONObject Json) throws JSONException {
			if (Json == null)
				return new Danmu2();
			Danmu2 d = new Danmu2();
			d.DanmuClass = Json.getString("DanmuClass");
			d.DanmuID = Json.getString("DanmuID");
			d.UserName = Json.getString("UserName");
			d.UserID = Json.getString("UserID");
			d.InstrumentID = Json.getString("InstrumentID");
			d.Text = Json.getString("Text");
			d.UserToken = Json.getString("UserToken");
			d.ClientTime = Json.getString("ClientTime");
			d._METADATA_ = Json.getString("_METADATA_");
			return d;
		}
		
		public JSONObject ToJSON() {
			JSONObject o = new JSONObject();
			o.put("DanmuClass", DanmuClass);
			o.put("DanmuID", DanmuID);
			o.put("UserName", UserName);
			o.put("UserID", UserID);
			o.put("InstrumentID", InstrumentID);
			o.put("Text", Text);
			o.put("UserToken", UserToken);
			o.put("ClientTime", ClientTime);
			o.put("_METADATA_", _METADATA_);
			return o;
		}
		
		/*��Ļ�ļ���չʾ��ͬ�ķ�񣬶�Ӧ��ĻCSS�������*/
		public String DanmuClass;
		/*��ʶ������Ļ��Ψһ����*/
		public String DanmuID;
		/*��Ļ�����ǳ�*/
		public String UserName;
		/*�û�Ψһ��ʶ��*/
		public String UserID;
		/*�õ�Ļ��Ӧ�ĺ�Լ*/
		public String InstrumentID;
		/*��Ļ�ı�*/
		public String Text;
		/*�û���½����*/
		public String UserToken;
		/*�ͻ���ʱ��*/
		public String ClientTime;
		/*Ԫ���ݣ���ʾ�����ݵ�����*/
		public String _METADATA_;
	}
	
	public DanmuDisplay2() {
		data = new JSONArray();
	}
	
	public JSONObject ToJSON() {
		JSONObject o = new JSONObject();
		o.put("InstrumentID", InstrumentID);
		o.put("data", data);
		return o;
	}

	public void AddDanmu(Danmu2 d) {
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
