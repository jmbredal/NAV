import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.net.*;
import java.text.*;

import java.sql.*;

import no.ntnu.nav.logger.*;
import no.ntnu.nav.ConfigParser.*;
import no.ntnu.nav.Database.*;
import no.ntnu.nav.SimpleSnmp.*;
import no.ntnu.nav.getDeviceData.Netbox;
import no.ntnu.nav.getDeviceData.dataplugins.*;
import no.ntnu.nav.getDeviceData.deviceplugins.*;

/**
 * This class schedules the netboxes, assigns them to threads and runs
 * the plugins.
 */

public class QueryNetbox extends Thread
{
	private static ConfigParser navCp;
	private static Map dataClassMap, deviceClassMap;

	private static Timer timer;
	private static CheckRunQTask checkRunQTask;

	private static Map typeidMap;
	private static SortedMap nbRunQ;
	private static Stack idleThreads;
	private static Map nbMap;

	private static int maxThreadCnt;
	private static int threadCnt;
	private static Integer idleThreadLock = new Integer(0);
	private static int netboxCnt;

	private static String qNetbox;

	// Plugins

	// Caches which device handlers can handle a given Netbox
	static Map deviceNetboxCache = Collections.synchronizedMap(new HashMap());

	// Stores the persistent storage for the dataplugins
	static Map persistentStorage = Collections.synchronizedMap(new HashMap());

	// Object data
	String tid;
	NetboxImpl nb;
	SimpleSnmp sSnmp = new SimpleSnmp();

	// Static init
	public static void init(int numThreads, int updateDataInterval, ConfigParser cp, Map dataCM, Map deviceCM, String qnb) {
		maxThreadCnt = numThreads;
		navCp = cp;
		dataClassMap = dataCM;
		deviceClassMap = deviceCM;
		qNetbox = qnb;

		// Create the netbox map and the run queue
		nbMap = new HashMap();
		nbRunQ = new TreeMap();
		
		// Fetch from DB
		updateTypes(false);
		updateNetboxes();

		// Schedule fetch updates
		Timer updateDataTimer = new Timer();
		Log.d("INIT", "Starting timer for data updating");
		updateDataTimer.schedule(new UpdateDataTask(), updateDataInterval, updateDataInterval);

		Log.d("INIT", "Starting timer for netbox query scheduling");
		timer = new Timer();
		timer.schedule( checkRunQTask = new CheckRunQTask(), 0);

	}

	private static void scheduleCheckRunQ(long l)
	{
		synchronized (timer) {
			checkRunQTask.cancel();
			checkRunQTask = new CheckRunQTask();
			Log.w("QUERY_NETBOX", "SCHEDULE_CHECK_RUN_Q", "Schedule in " + l + " ms");
			timer.schedule(checkRunQTask, l);
		}
	}

	private static void checkRunQ()
	{
		Log.setDefaultSubsystem("QUERY_NETBOX");
		
		// Try to get a free netbox
		Object o;
		while ((o = removeRunQHead()) instanceof NetboxImpl) {
			NetboxImpl nb = (NetboxImpl)o;

			Log.d("CHECK_RUN_Q", "Got netbox: " + nb);

			// Try to get a free thread
			String tid = requestThread();
			if (tid == null) {
				Log.d("CHECK_RUN_Q", "Netbox is available, but no threads are idle");
				// Re-insert into queue
				addToRunQFront(nb);
				return;
			}

			// OK, start a new QueryNetbox
			Log.d("CHECK_RUN_Q", "Starting new thread with id: " + tid);
			new QueryNetbox(tid, nb).start();

		} 

		// No more free netboxes, schedule re-run when the next is ready
		Long nextRun = (Long)o;
		Log.d("CHECK_RUN_Q", "No available netbox, scheduling next check in " + nextRun + " ms");			
		scheduleCheckRunQ(nextRun.longValue());

	}

	public static void updateTypes(boolean updateNetboxes) {
		Map typeidM = new HashMap();

		// First fetch new types from the database
		try {
			ResultSet rs = Database.query("SELECT typeid, type.frequency AS typefreq, typesnmpoid.frequency AS oidfreq, oidkey, snmpoid FROM type JOIN typesnmpoid USING(typeid) JOIN snmpoid USING(snmpoidid) ORDER BY typeid");
			String prevtypeid = null;
			Map keyFreqMap = new HashMap(), keyMap = new HashMap();
			while (rs.next()) {
				String typeid = rs.getString("typeid");
				if (rs.isFirst()) {
					prevtypeid = typeid;
				}
				if (!typeid.equals(prevtypeid)) {
					Type t = new Type(prevtypeid, keyFreqMap, keyMap);
					typeidM.put(prevtypeid, t);
					keyFreqMap = new HashMap();
					keyMap = new HashMap();
				}

				boolean oidfreq = (rs.getString("oidfreq") != null && rs.getString("oidfreq").length() > 0);
				keyFreqMap.put(rs.getString("oidkey"), new Integer(oidfreq ? rs.getInt("oidfreq") : rs.getInt("typefreq")));
				keyMap.put(rs.getString("oidkey"), rs.getString("snmpoid"));
				prevtypeid = typeid;
			}
			typeidM.put(prevtypeid, new Type(prevtypeid, keyFreqMap, keyMap));

			// Make new types global
			typeidMap = typeidM;

			Log.d("UPDATE_TYPES", "Updated typeidMap, size: " + typeidMap.size());

			// Then update all netboxes with the new types
			if (updateNetboxes) updateNetboxesWithNewTypes();

		} catch (SQLException e) {
			Log.e("UPDATE_TYPES", "SQLException: " + e);			
		}
	}

	private static void updateNetboxesWithNewTypes() {
		for (Iterator it = nbMap.values().iterator(); it.hasNext();) {
			NetboxImpl nb = (NetboxImpl)it.next();
			Type t = (Type)typeidMap.get(nb.getTypeT().getTypeid());
			nb.setType(t);
		} 
	}

	public static void updateNetboxes() {
		try {
			String sql = "SELECT ip,ro,netboxid,typeid,typename,catid,sysname FROM netbox JOIN type USING(typeid) WHERE up='y'";
			if (qNetbox != null) sql += " AND sysname LIKE '"+qNetbox+"'";
			ResultSet rs = Database.query(sql);

			int nbCnt=0;
			while (rs.next()) {
				String netboxid = rs.getString("netboxid");
				String typeid = rs.getString("typeid");
				Type t = (Type)typeidMap.get(typeid);
				if (t == null) {
					Log.d("UPDATE_NETBOXES", "Skipping netbox " + rs.getString("sysname") +
								" because type is null (probably the type doesn't have any OIDs)");
					continue;
				}
				NetboxImpl nb;

				synchronized (nbMap) {
					if ( (nb=(NetboxImpl)nbMap.get(netboxid)) != null) {
						nb.remove();
					}
					nbMap.put(netboxid, nb = new NetboxImpl(++nbCnt, t));
				}
				
				nb.setNetboxid(netboxid);
				nb.setIp(rs.getString("ip"));
				nb.setCommunityRo(rs.getString("ro"));
				nb.setType(rs.getString("typename"));
				nb.setSysname(rs.getString("sysname"));
				nb.setCat(rs.getString("catid"));
				//bd.setSnmpMajor(rs.getInt("snmp_major"));
				//bd.setSnmpagent(rs.getString("snmpagent"));

				addToRunQ(nb);
			}

			netboxCnt = nbCnt;

		} catch (SQLException e) {
			Log.e("UPDATE_NETBOXES", "SQLException: " + e);			
		}

		Log.d("UPDATE_NETBOXES", "Updated netboxes, size: " + netboxCnt);

	}

	private static void addToRunQ(NetboxImpl nb) {
		addToRunQ(nb, false);
	}

	private static void addToRunQFront(NetboxImpl nb) {
		addToRunQ(nb, true);
	}

	private static void addToRunQ(NetboxImpl nb, boolean front) {
		Long nextRun = new Long(nb.getNextRun());
		synchronized (nbRunQ) {
			LinkedList l;
			if ( (l = (LinkedList)nbRunQ.get(nextRun)) == null) nbRunQ.put(nextRun, l = new LinkedList());
			if (front) {
				l.addFirst(nb);
			} else {
				l.add(nb);
			}
		}
	}

	private static Object removeRunQHead() {
		Object o;
		while ((o = removeRunQHeadNoCheck()) instanceof NetboxImpl) {
			NetboxImpl nb = (NetboxImpl)o;
			if (nb.isRemoved()) continue;
			return nb;
		}
		return o;
	}

	private static Object removeRunQHeadNoCheck() {
		synchronized (nbRunQ) {
			if (nbRunQ.isEmpty()) return new Long(Integer.MAX_VALUE);

			Long nextRun = (Long)nbRunQ.firstKey();
			if (nextRun.longValue() > System.currentTimeMillis()) return new Long(nextRun.longValue() - System.currentTimeMillis());

			LinkedList l = (LinkedList)nbRunQ.get(nextRun);
			NetboxImpl nb  = (NetboxImpl)l.removeFirst();
			if (l.isEmpty()) nbRunQ.remove(nextRun);
			return nb;
		}
	}

	private static String requestThread() {
		synchronized (idleThreadLock) {
			if (threadCnt < maxThreadCnt) {
				return format(threadCnt++, String.valueOf(maxThreadCnt-1).length());
			}
			return null;
		}
	}

	private static void threadIdle() {
		synchronized (idleThreadLock) {
			threadCnt--;
		}
		scheduleCheckRunQ(0);
	}

	// Constructor
	public QueryNetbox(String tid, NetboxImpl initialNb)
	{
		this.tid = tid;
		this.nb = initialNb;
	}

	public void run()
	{
		Log.setDefaultSubsystem("QUERY_NETBOX_T"+tid);

		long beginTime = System.currentTimeMillis();

		while (true) {

			String netboxid = nb.getNetboxidS();
			String ip = nb.getIp();
			String cs_ro = nb.getCommunityRo();
			String type = nb.getType();
			String sysName = nb.getSysname();
			String cat = nb.getCat();
			int snmpMajor = nb.getSnmpMajor();

			Log.d("RUN", "Now working with("+netboxid+"): " + sysName + ", type="+type+", ip="+ip+" (device "+ nb.getNum() +" of "+ netboxCnt+")");
			long boksBeginTime = System.currentTimeMillis();

			try {

				// Get DataContainer objects from each data-plugin.
				DataContainersImpl containers = getDataContainers();

				// Find handlers for this boks
				DeviceHandler[] deviceHandler = findDeviceHandlers(nb);
				if (deviceHandler == null) {
					throw new NoDeviceHandlerException("  No device handlers found for netbox: " + netboxid + " (cat: " + cat + " type: " + type);
				}

				Log.setDefaultSubsystem("QUERY_NETBOX_T"+tid);
				Log.d("RUN", "  Found " + deviceHandler.length + " deviceHandlers for boksid: " + netboxid + " (cat: " + cat + " type: " + type);

				for (int dhNum=0; dhNum < deviceHandler.length; dhNum++) {

					try {
						deviceHandler[dhNum].handleDevice(nb, sSnmp, navCp, containers);

					} catch (TimeoutException te) {
						Log.setDefaultSubsystem("QUERY_NETBOX_T"+tid);				
						Log.d("RUN", "TimeoutException: " + te.getMessage());
						Log.w("RUN", "GIVING UP ON: " + sysName + ", typeid: " + type );
						continue;
					}

				}

				// Call the data handlers for all data plugins
				containers.callDataHandlers(nb);

			} catch (NoDeviceHandlerException exp) {
				Log.d("RUN", exp.getMessage());
			} catch (Exception exp) {
				Log.w("RUN", "Fatal error, aborting. Exception: " + exp.getMessage());
				exp.printStackTrace(System.err);
			} catch (Throwable e) {
				Log.w("RUN", "Fatal error, plugin is probably old and needs to be updated to new API: " + e.getMessage());
			}

			Log.setDefaultSubsystem("QUERY_NETBOX_T"+tid);				
			Log.d("RUN", "Done processing netbox " + nb);

			// If netbox is removed, don't add it to the RunQ
			if (!nb.isRemoved()) {
				nb.reschedule();
				
				// Insert into queue
				addToRunQ(nb);
			}

			// Try to get a new netbox to process
			Object o = removeRunQHead();
			if (o instanceof NetboxImpl) {
				nb = (NetboxImpl)o;
				Log.d("RUN", "Got new netbox: " + nb);
			} else {				
				// We didn't get a netbox; exit the thread
				break;
			}

		}

		Log.d("RUN", "Thread idle, exiting...");
		threadIdle();

	}

	private DataContainersImpl getDataContainers() {
		DataContainersImpl dcs = new DataContainersImpl();

		try {
			// Iterate over all data plugins
			synchronized (dataClassMap) {
				for (Iterator it=dataClassMap.entrySet().iterator(); it.hasNext();) {
					Map.Entry me = (Map.Entry)it.next();
					String fn = (String)me.getKey();
					Class c = (Class)me.getValue();;
					Object o = c.newInstance();
					
					DataHandler dh = (DataHandler)o;
					
					Map m;
					if ( (m = (Map)persistentStorage.get(fn)) == null) persistentStorage.put(fn,  m = Collections.synchronizedMap(new HashMap()));
					dh.init(m);

					dcs.addContainer(dh.dataContainerFactory());				
				}
			}
		} catch (InstantiationException e) {
			Log.w("GET_DATA_CONTAINERS", "GET_DATA_CONTAINERS", "Unable to instantiate handler for " + nb.getNetboxid() + ", msg: " + e.getMessage());
		} catch (IllegalAccessException e) {
			Log.w("GET_DATA_CONTAINERS", "GET_DATA_CONTAINERS", "IllegalAccessException for " + nb.getNetboxid() + ", msg: " + e.getMessage());
		}

		return dcs;		
	}

	private DeviceHandler[] findDeviceHandlers(Netbox nb) {
		try {
			synchronized (deviceNetboxCache) {
				Class[] c;
				if ( (c=(Class[])deviceNetboxCache.get(nb.getNetboxidS() )) != null) {
					DeviceHandler[] dh = new DeviceHandler[c.length];
					for (int i=0; i < c.length; i++) dh[i] = (DeviceHandler)c[i].newInstance();
					return dh;
				}
			}

			// Iterate over all known plugins to find the set of handlers to process this boks
			// Look at DeviceHandler for docs on the algorithm for selecting handlers
			TreeMap dbMap = new TreeMap();
			List alwaysHandleList = new ArrayList();
			synchronized (deviceClassMap) {

				int high = 0;
				for (Iterator it=deviceClassMap.values().iterator(); it.hasNext();) {
					Class c = (Class)it.next();
					Object o = c.newInstance();

					DeviceHandler dh = (DeviceHandler)o;
					int v;
					try {
						v = dh.canHandleDevice(nb);
					} catch (Exception e) {
						Log.w("FIND_DEVICE_HANDLERS", "FIND_DEVICE_HANDLERS", "Error from DeviceHandler " + c + ", skipping: " + e.getMessage());
						continue;
					} catch (Throwable e) {
						Log.w("FIND_DEVICE_HANDLERS", "FIND_DEVICE_HANDLERS",
									"Fatal error from DeviceHandler " + c + ", plugin is probably old and needs to be updated to new API: " + e.getMessage());
						continue;
					}
					
					if (v == DeviceHandler.ALWAYS_HANDLE) {
						alwaysHandleList.add(c);
					} else {
						if (Math.abs(v) > high) {
							if (v > high) high = v;
							dbMap.put(new Integer(Math.abs(v)), c);
						}
					}
				}

				if (!dbMap.isEmpty() || !alwaysHandleList.isEmpty()) {
					SortedMap dbSMap = dbMap.tailMap(new Integer(high));
					Class[] c = new Class[dbSMap.size() + alwaysHandleList.size()];
					
					int j=dbSMap.size()-1;
					for (Iterator i=dbSMap.values().iterator(); i.hasNext(); j--) c[j] = (Class)i.next();
					
					j = c.length - 1;
					for (Iterator i=alwaysHandleList.iterator(); i.hasNext(); j--) c[j] = (Class)i.next();
					
					synchronized (deviceNetboxCache) { deviceNetboxCache.put(nb.getNetboxidS(), c); }

					// Call ourselves; this avoids duplicating the code for instatiating objects from the classes
					return findDeviceHandlers(nb);
				}
			}
		} catch (InstantiationException e) {
			Log.w("FIND_DEVICE_HANDLERS", "FIND_DEVICE_HANDLERS", "Unable to instantiate handler for " + nb.getNetboxid() + ", msg: " + e.getMessage());
		} catch (IllegalAccessException e) {
			Log.w("FIND_DEVICE_HANDLERS", "FIND_DEVICE_HANDLERS", "IllegalAccessException for " + nb.getNetboxid() + ", msg: " + e.getMessage());
		}

		return null;
	}

	static class UpdateDataTask extends TimerTask {
		public void run() {
			updateTypes(false);
			updateNetboxes();
		}
	}

	static class CheckRunQTask extends TimerTask {
		public void run() {
			checkRunQ();
		}
	}

	private static String format(long i, int n)
	{
		DecimalFormat nf = new DecimalFormat("#");
		nf.setMinimumIntegerDigits(n);
		return nf.format(i);
	}

}
