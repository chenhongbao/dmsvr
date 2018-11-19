package dmkp.dm.svr;

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
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import dmkp.common.net.Duplex;
import dmkp.common.util.Common;
import dmkp.common.util.Result;
import dmkp.dm.svr.data.DanmuDisplay2;
import dmkp.dm.svr.data.DanmuDisplay2.Danmu2;
import dmkp.logging.logger.SocketLoggerFactory;

public class DanmuServer {

	class ClientInfo {
		public ClientInfo() {
		}

		public String IP;
		public int Port;
		public Duplex TCP;
	}
	
	class DanmuDuplex extends Duplex {
		public DanmuDuplex() {}
		
		@Override
		public void OnConnect() {
		}

		@Override
		public void OnStream(byte[] Data) {
		}

		@Override
		public void OnDisconnect() {
			LOG.warning("��Ļ�ͻ��Ͽ����ӣ�" + GetInetAddress().getCanonicalHostName());
		}

		@Override
		public void OnHearbeatError(Result Reason) {
		}
	}

	class SyncList {
		public SyncList() {
			_dm = new LinkedList<Danmu2>();
			_lock = new ReentrantReadWriteLock();
		}

		public void AddDanmu(Danmu2 d) {
			_lock.writeLock().lock();
			_dm.add(d);
			_lock.writeLock().unlock();
		}

		public List<Danmu2> PopDanmu() {
			List<Danmu2> tmp = _dm;
			_lock.writeLock().lock();
			_dm = new LinkedList<Danmu2>();
			_lock.writeLock().unlock();
			return tmp;
		}

		private ReentrantReadWriteLock _lock;
		private List<Danmu2> _dm;
	}

	/* ��־���� */
	static Logger LOG;

	/* ����ͻ���������Ϣ */
	List<ClientInfo> _clients;

	/* �����Լ�͵�Ļӳ�� */
	ReentrantReadWriteLock _lock;
	Map<String, SyncList> _danmuCache;

	public DanmuServer() {
		_lock = new ReentrantReadWriteLock();
		_clients = new LinkedList<ClientInfo>();
		_danmuCache = new HashMap<String, SyncList>();
		_InitLogger();
		_LoadClientInfo();
		Common.GetSingletonExecSvc().execute(new Runnable() {
			@Override
			public void run() {
				while (true) {
					_lock.readLock().lock();
					/* ��Ϊ_danmuCache����ɾ��key�����Ը���key��������ݲ��������쳣������߲����� */
					Set<String> keys = new HashSet<String>();
					keys.addAll(_danmuCache.keySet());
					_lock.readLock().unlock();
					for (String k : keys) {
						List<Danmu2> l = _danmuCache.get(k).PopDanmu();
						if (l.size() < 1) {
							continue;
						}
						_BroadcastDanmu(k, l);
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						LOG.warning("��Ļ�������߳�����ʧ�ܣ�" + e.getMessage());
					}
				}
			}
		});
	}

	private void _BroadcastDanmu(String InstrumentID, List<Danmu2> danmus) {
		DanmuDisplay2 dis = new DanmuDisplay2();
		dis.InstrumentID = InstrumentID;
		for (Danmu2 d : danmus) {
			dis.AddDanmu(d);
		}
		byte[] bytes = dis.ToJSON().toString(-1).getBytes(Charset.forName("UTF-8"));
		for (ClientInfo c : _clients) {
			if (!c.TCP.IsConnected() && c.TCP.Connect(c.IP, c.Port).equals(Result.Success)) {
				LOG.info("������Ļ�ͻ��ɹ���" + c.IP + ":" + c.Port);
			}
			if (c.TCP.IsConnected()) {
				Result r = c.TCP.SendStream(bytes);
				if (r.equals(Result.Error)) {
					LOG.warning("��ͻ����͵�Ļʧ�ܣ�" + c.IP + ":" + c.Port);
				}
			}
		}
	}

	private void _LoadClientInfo() {
		JSONArray arr = Common.LoadJSONArray(this.getClass().getResourceAsStream("dm_client.json"));
		if (arr.length() < 1) {
			return;
		}
		for (int i = 0; i < arr.length(); ++i) {
			try {
				JSONObject o = arr.getJSONObject(i);
				ClientInfo info = new ClientInfo();
				info.IP = o.getString("IP");
				info.Port = o.getInt("Port");
				info.TCP = new DanmuDuplex();
				Result r = info.TCP.Connect(info.IP, info.Port);
				if (r.equals(Result.Error)) {
					LOG.warning("���ӵ�Ļ�ͻ�ʧ�ܣ�" + info.IP + ":" + info.Port + "��" + r.Message);
				}
				_clients.add(info);
			} catch (JSONException e) {
				Common.PrintException("���ص�Ļ�ͻ���Ϣʧ�ܣ�" + e.getMessage());
			}
		}
	}
	
	private void _InitLogger() {
		JSONObject obj = Common.LoadJSONObject(this.getClass().getResource("dmlog_addr.json").getFile());
		if (obj != null && obj.has("IP") && obj.has("Port")) {
			int port = 0;
			String ip = null;
			ip = obj.getString("IP");
			port = obj.getInt("Port");
			LOG = SocketLoggerFactory.GetInstance(this.getClass().getCanonicalName(), ip, port);
		}
		else {
			Common.PrintException("ȥ����־��������ʧ��");
		}
	}

	public void Accept() {
		int port = 0;
		DatagramSocket ss = null;
		byte[] receiveData = new byte[1500];
		try {
			JSONObject o = Common.LoadJSONObject(this.getClass().getResourceAsStream("dm_listen.json"));
			port = o.getInt("Port");
			ss = new DatagramSocket(port);
			System.out.println("��Ļ�����������˿ڣ�" + port);
		} catch (SocketException e) {
			LOG.severe("��Ļ�����������˿�ʧ�ܣ�" + port);
			return;
		}
		while (true) {
			try {
				DatagramPacket rp = new DatagramPacket(receiveData, receiveData.length);
				ss.receive(rp);
				Danmu2 d = Danmu2.Parse(new JSONObject(new String(rp.getData(), Charset.forName("UTF-8"))));
				_lock.readLock().lock();
				SyncList b = _danmuCache.get(d.InstrumentID);
				_lock.readLock().unlock();
				if (b != null) {
					b.AddDanmu(d);
				} else {
					_lock.writeLock().lock();
					if (_danmuCache.get(d.InstrumentID) == null) {
						SyncList l = new SyncList();
						l.AddDanmu(d);
						_danmuCache.put(d.InstrumentID, l);
					}
					_lock.writeLock().unlock();
				}
			} catch (IOException e) {
				LOG.warning("��������ǰ�˵ĵ�Ļʧ�ܣ�" + e.getMessage());
			}
		}
	}

	public static void main(String[] args) {
		new DanmuServer().Accept();
	}
}
