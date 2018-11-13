package com.dm.svr;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.Common;
import com.Result;
import com.dm.svr.data.DanmuDisplay2;
import com.logging.JSONSocketHandler;
import com.net.TcpPoint;

import dmkp.dm.data.DanmuText.Danmu;

public class DanmuServer implements Runnable {
	
	class ClientInfo {
		public ClientInfo() {}
		
		public String IP;
		public int Port;
		public TcpPoint TCP;
	}
	
	class SyncList {
		public SyncList() {
			_dm = new LinkedList<Danmu>();
			_lock = new ReentrantReadWriteLock();
		}
		
		public void AddDanmu(Danmu d) {
			_lock.writeLock().lock();
			_dm.add(d);
			_lock.writeLock().unlock();
		}
		
		public List<Danmu> PopDanmu() {
			List<Danmu> tmp = _dm;
			_lock.writeLock().lock();
			_dm = new LinkedList<Danmu>();
			_lock.writeLock().unlock();
			return tmp;
		}
		
		private ReentrantReadWriteLock _lock;
		private List<Danmu> _dm;
	}
	
	/*日志对象*/
	Logger LOG;
	
	/*线程池*/
	ExecutorService _es;
	
	/*保存客户端连接信息*/
	List<ClientInfo> _clients;
	
	/*保存合约和弹幕映射*/
	ReentrantReadWriteLock _lock;
	Map<String, SyncList> _danmuCache;
	
	public DanmuServer() {
		_InitLogger();
		_LoadClientInfo();
		_lock = new ReentrantReadWriteLock();
		_es = Executors.newCachedThreadPool();
		_clients = new LinkedList<ClientInfo>();
		_danmuCache = new HashMap<String, SyncList>();
		_es.execute(new Runnable() {
			@Override
			public void run() {
				while(true) {
					_lock.readLock().lock();
					/*因为_danmuCache不会删除key，所以根据key来获得数据不会引起异常，能提高并发性*/
					Set<String> keys = new HashSet<String>();
					keys.addAll(_danmuCache.keySet());
					_lock.readLock().unlock();
					for (String k : keys) {
						List<Danmu> l = _danmuCache.get(k).PopDanmu();
						if (l.size() < 1) {
							continue;
						}
						_BroadcastDanmu(k, l);
					}			
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						LOG.warning("弹幕服务器线程休眠失败，" + e.getMessage());
					}
				}
			}
		});
	}

	@Override
	public void run() {
		int port = 0;
		DatagramSocket ss = null;
        byte[] receiveData = new byte[1500], sendData = new byte[1500];
		try {
			JSONObject o = Common.LoadJSONObject(this.getClass().getResourceAsStream("dm_listen.json"));
			port = o.getInt("Port");
			ss = new DatagramSocket(port);
			System.out.println("弹幕服务器监听端口：" + port);
		} catch (SocketException e) {
			LOG.severe("弹幕服务器监听端口失败，" + port);
			return;
		}
		while(true) {
            try {
            	DatagramPacket rp = new DatagramPacket(receiveData, receiveData.length);
				ss.receive(rp);
				Danmu d = Danmu.Parse(new JSONObject(
						new String(rp.getData(), Charset.forName("UTF-8"))));
				_lock.readLock().lock();
				SyncList b = _danmuCache.get(d.InstrumentID);
				_lock.readLock().unlock();
				if (b != null) {
					b.AddDanmu(d);
				}
				else {
					SyncList l = new SyncList();
					l.AddDanmu(d);
					_lock.writeLock().lock();
					_danmuCache.put(d.InstrumentID, l);
					_lock.writeLock().unlock();
				}
			} catch (IOException e) {
				LOG.warning("接收来自前端的弹幕失败，" + e.getMessage());
			} 
		}
	}
	
	private void _BroadcastDanmu(String InstrumentID, List<Danmu> danmus) {
		DanmuDisplay2 dis = new DanmuDisplay2();
		dis.InstrumentID = InstrumentID;
		for (Danmu d :danmus) {
			dis.AddDanmu(d);
		}
		byte[] bytes = dis.ToJSON().toString(-1).getBytes(Charset.forName("UTF-8"));
		for (ClientInfo c : _clients) {
			if (!c.TCP.IsConnected() && c.TCP.Connect(c.IP,  c.Port).equals(Result.Success)) {
				LOG.info("重连弹幕客户成功，" + c.IP + ":" + c.Port);
			}
			if (c.TCP.IsConnected()) {
				Result r = c.TCP.Send(bytes);
				if (r.equals(Result.Error)) {
					LOG.warning("向客户发送弹幕失败，" + c.IP + ":" + c.Port);
				}
			}
		}
	}
	
	private void _LoadClientInfo() {
		JSONArray arr = Common.LoadJSONArray(this.getClass().getResourceAsStream("dm_client.json"));
		if (arr.length() < 1) {
			return;
		}
		for (int i=0; i<arr.length(); ++i) {
			try {
				JSONObject o = arr.getJSONObject(i);
				ClientInfo info = new ClientInfo();
				info.IP = o.getString("IP");
				info.Port = o.getInt("Port");
				info.TCP = new TcpPoint();
				Result r = info.TCP.Connect(info.IP,  info.Port);
				if (r.equals(Result.Error)) {
					LOG.warning("连接弹幕客户失败，" + info.IP +":" + info.Port + "，" + r.Message);
				}
				_clients.add(info);
			} catch (JSONException e) {
				Common.PrintException("加载弹幕客户信息失败，" + e.getMessage());
			}	
		}
	}
	
	private void _InitLogger() {
		LOG = Logger.getLogger(this.getClass().getCanonicalName());
		try {
			String ip = null;
			int port = 0;
			JSONObject obj = Common.LoadJSONObject(this.getClass().getResource("dmlog_addr.json").getFile());
			if (obj != null && obj.has("IP") && obj.has("Port")) {
				ip = obj.getString("IP");
				port = obj.getInt("Port");
				LOG.addHandler(new JSONSocketHandler(ip, port));
			}
		} catch (SecurityException e) {
			/*提示JVM回收内存*/
			LOG = null;
			Common.PrintException(new Exception("网络日志对象初始化异常，" + e.getMessage()));
		}
	}

	public static void main(String[] args) {
		Thread th = new Thread(new DanmuServer());
		th.start();
		try {
			th.join();
		} catch (InterruptedException e) {
			Common.PrintException(e);
		}
	}
}
