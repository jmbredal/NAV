package no.ntnu.nav.getDeviceData.dataplugins.Module;

import java.util.*;
import java.sql.*;

import no.ntnu.nav.logger.*;
import no.ntnu.nav.Database.*;
import no.ntnu.nav.getDeviceData.Netbox;
import no.ntnu.nav.getDeviceData.dataplugins.*;
import no.ntnu.nav.getDeviceData.dataplugins.Device.DeviceHandler;


/**
 * DataHandler plugin for getDeviceData; provides an interface for storing
 * module data.
 *
 * @see ModuleContainer
 */

public class ModuleHandler implements DataHandler {

	/*
	private static Map deviceMap;
	private static Map moduleMap;
	private static Map modDevidMap;
	private static Set deletedDevSet = Collections.synchronizedSet(new HashSet());
	*/

	/**
	 * Fetch initial data from device and module tables.
	 */
	public synchronized void init(Map persistentStorage, Map changedDeviceids) {
		// Remove any devices no longer present
		/*
		if (!changedDeviceids.isEmpty()) {
			for (Iterator it = changedDeviceids.entrySet().iterator(); it.hasNext();) {
				Map.Entry me = (Map.Entry)it.next();
				if (((Integer)me.getValue()).intValue() == DataHandler.DEVICE_DELETED) {
					modDevidMap.remove(me.getKey());
					deletedDevSet.add(me.getKey());
				}
			}
		}
		*/
		if (persistentStorage.containsKey("initDone")) return;
		persistentStorage.put("initDone", null);

		/*
		Map m;
		ResultSet rs;
		long dumpBeginTime,dumpUsedTime;

		Log.setDefaultSubsystem("ModuleHandler");

		try {
		
			// device
			dumpBeginTime = System.currentTimeMillis();
			m  = Collections.synchronizedMap(new HashMap());
			rs = Database.query("SELECT deviceid,serial FROM device");
			while (rs.next()) {
				m.put(rs.getString("serial"), rs.getString("deviceid"));
			}
			deviceMap = m;
			dumpUsedTime = System.currentTimeMillis() - dumpBeginTime;
			Log.d("INIT", "Dumped device in " + dumpUsedTime + " ms");

			// module
			dumpBeginTime = System.currentTimeMillis();
			m = Collections.synchronizedMap(new HashMap());
			modDevidMap = Collections.synchronizedMap(new HashMap());
			rs = Database.query("SELECT deviceid,serial,hw_ver,fw_ver,sw_ver,moduleid,module,netboxid,model,descr FROM device JOIN module USING (deviceid) ORDER BY moduleid");
			while (rs.next()) {
				Module md = new Module(rs.getString("serial"), rs.getString("hw_ver"), rs.getString("fw_ver"), rs.getString("sw_ver"), rs.getInt("module"));
				md.setDeviceid(rs.getInt("deviceid"));
				md.setModuleid(rs.getInt("moduleid"));
				md.setModel(rs.getString("model"));
				md.setDescr(rs.getString("descr"));

				String key = rs.getString("netboxid")+":"+md.getKey();
				m.put(key, md);
				modDevidMap.put(rs.getString("deviceid"), rs.getString("moduleid"));
			}
			moduleMap = m;
			dumpUsedTime = System.currentTimeMillis() - dumpBeginTime;
			Log.d("INIT", "Dumped module in " + dumpUsedTime + " ms");

		} catch (SQLException e) {
			Log.e("INIT", "SQLException: " + e.getMessage());
		}

		for (Iterator it = moduleMap.values().iterator(); it.hasNext();) {
			Module mod = (Module)it.next();
			if ("0".equals(mod.getModuleidS())) System.err.println("OH OH!");
		}
		*/

	}

	/**
	 * Return a DataContainer object used to return data to this
	 * DataHandler.
	 */
	public DataContainer dataContainerFactory() {
		return new ModuleContainer(this);
	}

	private Module getModule(String netboxid, String module) {
		try {
			ResultSet rs = Database.query("SELECT deviceid,serial,hw_ver,fw_ver,sw_ver,moduleid,module,netboxid,model,descr FROM device JOIN module USING (deviceid) WHERE netboxid='"+netboxid+"' AND module='"+module+"'");
			if (rs.next()) {
				Module md = new Module(rs.getString("serial"), rs.getString("hw_ver"), rs.getString("fw_ver"), rs.getString("sw_ver"), rs.getInt("module"));
				md.setDeviceid(rs.getInt("deviceid"));
				md.setModuleid(rs.getInt("moduleid"));
				md.setModel(rs.getString("model"));
				md.setDescr(rs.getString("descr"));

				return md;
			}
		} catch (SQLException e) {
			Log.e("HANDLE", "Cannot fetch old module: " + netboxid + ", " + module);
			e.printStackTrace(System.err);
		}
		return null;
	}
	
	/**
	 * Store the data in the DataContainer in the database.
	 */
	public void handleData(Netbox nb, DataContainer dc, Map changedDeviceids) {
		if (!(dc instanceof ModuleContainer)) return;
		ModuleContainer mc = (ModuleContainer)dc;
		if (!mc.isCommited()) return;

		// Because it's possible for serial to be empty, but we can still
		// identify the device by deviceid in module, we need to loop over
		// all modules and check the deviceid if serial is empty.
		for (Iterator modules = mc.getModules(); modules.hasNext();) {
			Module md = (Module)modules.next();
			try {
				ResultSet rs = Database.query("SELECT deviceid FROM module WHERE netboxid='"+nb.getNetboxid()+"' AND module='"+md.getModule()+"'");
				if (rs.next()) {
					md.setDeviceid(rs.getInt("deviceid"));
				}
			} catch (SQLException e) {
				Log.e("HANDLE", "Cannot fetch old module deviceid: " + nb.getNetboxid() + ", " + md.getModule());
				e.printStackTrace(System.err);
			}
		}

		// Let DeviceHandler update the device table first
		DeviceHandler dh = new DeviceHandler();
		dh.handleData(nb, dc, changedDeviceids);

		Log.setDefaultSubsystem("ModuleHandler");
		for (Iterator modules = mc.getModules(); modules.hasNext();) {
			Module md = (Module)modules.next();

			String moduleid = "-1";
			try {
				// Check if the module is new
				Module oldmd = null;
				ResultSet rs = Database.query("SELECT deviceid,serial,hw_ver,fw_ver,sw_ver,moduleid,module,netboxid,model,descr FROM device JOIN module USING (deviceid) WHERE netboxid="+nb.getNetboxid()+" AND module='"+md.getModule()+"'");
				if (rs.next()) {
					oldmd = new Module(rs.getString("serial"), rs.getString("hw_ver"), rs.getString("fw_ver"), rs.getString("sw_ver"), rs.getInt("module"));
					oldmd.setDeviceid(rs.getInt("deviceid"));
					oldmd.setModuleid(rs.getInt("moduleid"));
					oldmd.setModel(rs.getString("model"));
					oldmd.setDescr(rs.getString("descr"));
				}
				//System.err.println("    module: " + md);
				//System.err.println("Old module: " + oldmd);
				
				
				/*
				  Cases:
				 - Module with same deviceid, but different module found
				   Existing module moved here, delete old and new
				 - Module with same module, but different deviceid found
                   Module moved, delete
				*/
				rs = Database.query("SELECT module, moduleid, netboxid FROM module WHERE deviceid='"+md.getDeviceid()+"'");
				if (rs.next() && (md.getModule() != rs.getInt("module") || nb.getNetboxid() != rs.getInt("netboxid"))) {
					Log.d("DEL_MODULE", "Deleting old module("+rs.getString("moduleid")+"), module: " + rs.getString("module"));
					//System.err.println("Deleting old module("+rs.getString("moduleid")+"), module: " + rs.getString("module"));
					Database.update("DELETE FROM module WHERE moduleid='"+rs.getString("moduleid")+"'");
				}
				if (oldmd != null && md.getDeviceid() != oldmd.getDeviceid()) {
					Database.update("DELETE FROM module WHERE deviceid='"+oldmd.getDeviceid()+"'");
					//System.err.println("Deleting old module("+oldmd.getDeviceid()+")");
					oldmd = null;
				}
				/*
				if (rs.next() && (oldmd == null || oldmd.getModuleid() != rs.getInt("moduleid")) || (oldmd != null && oldmd.getDeviceid() != md.getDeviceid())) {
					Log.d("DEL_MODULE", "Deleting old module("+rs.getString("moduleid")+"), md: " + md);
					Database.update("DELETE FROM module WHERE moduleid='"+rs.getString("moduleid")+"'");
					if (oldmd != null) {
						Log.d("DEL_MODULE", "Deleting old module("+oldmd.getModuleid()+"), md: " + oldmd);
						Database.update("DELETE FROM module WHERE moduleid='"+oldmd.getModuleid()+"'");
						oldmd = null;
					}
				}
				*/

				if (oldmd == null) {
					// Sett inn i module
					Log.i("NEW_MODULE", "deviceid="+md.getDeviceidS()+" netboxid="+nb.getNetboxid()+" module="+md.getModule()+" model="+md.getModel()+" descr="+md.getDescr());
					/*
					  int cnt = Database.update("UPDATE module SET deviceid = NULL WHERE deviceid = '" + md.getDeviceidS() + "'");
					  if (cnt > 0) {
					  Log.w("NEW_MODULE", "Old device with same deviceid("+md.getDeviceidS()+") already exists");
					  }
					*/

					String[] ins = {
						"moduleid", "",
						"deviceid", md.getDeviceidS(),
						"netboxid", nb.getNetboxidS(),
						"module", ""+md.getModule(),
						"model", md.getModel(),
						"descr", md.getDescr(),
					};
					moduleid = Database.insert("module", ins, null);
					//md.setModuleid(moduleid);
					/*
					  changedDeviceids.put(md.getDeviceidS(), new Integer(DataHandler.DEVICE_ADDED));
					  modDevidMap.put(md.getDeviceidS(), moduleid);
					  if ("0".equals(moduleid)) {
					  Log.e("HANDLE_DATA", "Database returned 0 ID, should not happen!");
					  System.err.println("Database returned 0 ID for new module ("+nb.getNetboxid()+"), should not happen!");
					  }
					*/

				} else {
					moduleid = oldmd.getModuleidS();
					if ("0".equals(moduleid)) {
						Log.e("HANDLE_DATA", "Old module data object has 0 moduleid, should not happen!");
						System.err.println("Old module data object has 0 moduleid ("+nb.getNetboxid()+"), should not happen! " + oldmd);
					}

					if (!md.equalsModule(oldmd)) {
						// Vi m� oppdatere module
						Log.i("UPDATE_MODULE", "moduleid="+moduleid+" deviceid="+md.getDeviceidS()+" module="+md.getModule()+" model="+md.getModel()+" descr="+md.getDescr());
						Log.i("UPDATE_MODULE", "moduleid="+moduleid+" deviceid="+oldmd.getDeviceidS()+" module="+oldmd.getModule()+" model="+oldmd.getModel()+" descr="+oldmd.getDescr());

						String[] set = {
							"module", ""+md.getModule(),
							"model", md.getModel(),
							"descr", md.getDescr(),
						};
						String[] where = {
							"moduleid", moduleid
						};
						Database.update("module", set, where);
						//changedDeviceids.put(md.getDeviceidS(), new Integer(DataHandler.DEVICE_UPDATED));
					}
				}

			} catch (SQLException e) {
				Log.e("HANDLE", "SQLException: " + e.getMessage());
				e.printStackTrace(System.err);
			} finally {
				md.setModuleid(moduleid);
			}
			/*
			if (moduleid != null) {
				md.setModuleid(moduleid);
				moduleMap.put(moduleKey, md);
				modDevidMap.put(md.getDeviceidS(), md.getModuleidS());
			}
			*/
		}
	}

}
