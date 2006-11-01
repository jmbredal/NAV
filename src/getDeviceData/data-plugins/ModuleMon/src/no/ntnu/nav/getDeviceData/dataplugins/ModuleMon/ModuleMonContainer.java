package no.ntnu.nav.getDeviceData.dataplugins.ModuleMon;

import java.util.*;
import java.sql.*;

import no.ntnu.nav.logger.*;
import no.ntnu.nav.Database.*;
import no.ntnu.nav.util.*;
import no.ntnu.nav.getDeviceData.Netbox;
import no.ntnu.nav.getDeviceData.dataplugins.*;

/**
 * <p>
 * The interface to device plugins for storing collected data.
 * </p>
 *
 * @see ModuleMonHandler
 */

public class ModuleMonContainer implements DataContainer {

	public static final int PRIORITY_MODULE_MON = PRIORITY_NORMAL;

	private ModuleMonHandler mmh;
	private boolean commit = false;
	/**
	 * Should modules with unknown status be considered down?
	 */
	private boolean unknownDown = false;

	//private MultiMap queryIfindices;
	//private Map moduleToIfindex;
	private Set moduleUpSet = new HashSet();
	private Set moduleDownSet = new HashSet();

	protected ModuleMonContainer(ModuleMonHandler mmh) {
		this.mmh = mmh;
		//this.queryIfindices = queryIfindices;
		//this.moduleToIfindex = moduleToIfindex;
	}

	/**
	 * Get the name of the container; returns the string ModuleMonContainer
	 */
	public String getName() {
		return "ModuleMonContainer";
	}

	// Doc in interface
	public int getPriority() {
		return PRIORITY_MODULE_MON;
	}

	/**
	 * Get a data-handler for this container; this is a reference to the
	 * ModuleMonHandler object which created the container.
	 */
	public DataHandler getDataHandler() {
		return mmh;
	}

	public Set getModuleSet(String netboxid) {
		Set s = new HashSet();
		try {
			ResultSet rs = Database.query("SELECT module FROM module WHERE netboxid='"+netboxid+"'");
			while (rs.next()) {
				s.add(rs.getString("module"));
			}
		} catch (SQLException e) {
			Log.e("INIT", "SQLException: " + e.getMessage());
			e.printStackTrace(System.err);
		}
		return s;
	}

	/**
	 * <p> Returns a list of ifindices to query for a box.  </p>
	 */
	public Iterator getQueryIfindices(String netboxid) {
		Map m = new HashMap();
		try {
			// Get ifindices of all swports and gwports on this netbox
			ResultSet rs = Database.query("SELECT * " +
					"FROM " +
					"((SELECT ifindex, module FROM module JOIN swport USING(moduleid) WHERE netboxid='"+netboxid+"') " +
					"UNION " +
					"(SELECT ifindex, module FROM module JOIN gwport USING(moduleid) WHERE netboxid='"+netboxid+"')) AS ports " +
					"ORDER BY module DESC, RANDOM()");
			while (rs.next()) {
				List l;
				String module = rs.getString("module");
				if ( (l=(List)m.get(module)) == null) m.put(module, l = new ArrayList());
				l.add(rs.getString("ifindex"));
			}
		} catch (SQLException e) {
			Log.e("INIT", "SQLException: " + e.getMessage());
			e.printStackTrace(System.err);
		}
		return m.entrySet().iterator();
	}

	/**
	 * Reschedule the given netbox for the given module and OID. OID =
	 * null means all OIDs.
	 */
	public void rescheduleNetbox(Netbox nb, String module, String oid) {
		rescheduleNetbox(nb, module, Arrays.asList(new String[] { oid }));
	}

	/**
	 * Reschedule the given netbox for the given module and OID. OID =
	 * null means all OIDs.
	 */
	public void rescheduleNetbox(Netbox nb, String module, List oid) {
		int cnt = nb.get(module);
		if (cnt < 3) {
			if (cnt < 0) cnt = 0;
			nb.set(module, ++cnt);
			long delay;
			switch (cnt) {
			case 1: delay = 30; break;
			case 2: delay = 60; break;
			case 3: delay = 120; break;
			default:
				System.err.println("Error in rescheduleNetbox, cnt="+cnt+", should not happen");
				return;
			}
			for (Iterator it=oid.iterator(); it.hasNext();) nb.scheduleOid((String)it.next(), delay);
		}
	}

	/**
	 * <p> Returns the ifindices to ask for the given module.  </p>
	 */
	public Iterator ifindexForModule(String netboxid, String module) {
		List l = new ArrayList();
		try {
			ResultSet rs = Database.query("SELECT ifindex FROM module JOIN swport USING(moduleid) WHERE netboxid='"+netboxid+"' AND module='"+module+"' ORDER BY port IS NOT NULL DESC, RANDOM()");
			while (rs.next()) l.add(rs.getString("ifindex"));
		} catch (SQLException e) {
			Log.e("INIT", "SQLException: " + e.getMessage());
			e.printStackTrace(System.err);
		}
		return l.iterator();
	}

	/**
	 * <p> Register that the given module is up on the netbox.
	 * </p>
	 *
	 * @param module The up module
	 */
	public void moduleUp(Netbox nb, String module) {
		moduleUpSet.add(module);
		nb.set(module, 0);
	}

	/**
	 * <p> Register that the given module is down on the netbox.
	 * </p>
	 *
	 * @param module The up module
	 */
	public void moduleDown(Netbox nb, String module) {
		moduleDownSet.add(module);
	}

	public void commit() {
		commit = true;		
	}

	boolean isCommited() {
		return commit;
	}

	public Iterator getModulesUp() {
		return moduleUpSet.iterator();
	}

	Set getModulesUpSet() {
		return moduleUpSet;
	}

	public int modulesUpCount() {
		return moduleUpSet.size();
	}

	public Iterator getModulesDown() {
		return moduleDownSet.iterator();
	}

	Set getModulesDownSet() {
		return moduleDownSet;
	}

	public int modulesDownCount() {
		return moduleDownSet.size();
	}

	/**
	 * @return True if modules with unknown status will be considered as down.
	 */
	public boolean isUnknownDown() {
		return unknownDown;
	}

	/**
	 * <p>Decide whether modules with unknown status are considered as down or not</p>
	 * 
	 * @param unknownDown Set to true if modules with unknown status should be considered down, set to false if not.
	 */
	public void setUnknownDown(boolean unknownDown) {
		this.unknownDown = unknownDown;
	}


}
