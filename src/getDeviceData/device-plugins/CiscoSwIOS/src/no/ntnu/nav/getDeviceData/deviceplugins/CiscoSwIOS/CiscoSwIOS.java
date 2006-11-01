package no.ntnu.nav.getDeviceData.deviceplugins.CiscoSwIOS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import no.ntnu.nav.ConfigParser.ConfigParser;
import no.ntnu.nav.SimpleSnmp.SimpleSnmp;
import no.ntnu.nav.SimpleSnmp.TimeoutException;
import no.ntnu.nav.getDeviceData.Netbox;
import no.ntnu.nav.getDeviceData.dataplugins.DataContainer;
import no.ntnu.nav.getDeviceData.dataplugins.DataContainers;
import no.ntnu.nav.getDeviceData.dataplugins.Module.Module;
import no.ntnu.nav.getDeviceData.dataplugins.Module.ModuleContainer;
import no.ntnu.nav.getDeviceData.dataplugins.Swport.SwModule;
import no.ntnu.nav.getDeviceData.dataplugins.Swport.Swport;
import no.ntnu.nav.getDeviceData.dataplugins.Swport.SwportContainer;
import no.ntnu.nav.getDeviceData.deviceplugins.DeviceHandler;
import no.ntnu.nav.logger.Log;
import no.ntnu.nav.util.HashMultiMap;
import no.ntnu.nav.util.MultiMap;
import no.ntnu.nav.util.util;

/**
 * <p>
 * DeviceHandler for collecting the standard Cisco IOS switch port OIDs.
 * </p>
 *
 * <p>
 * This plugin handles the following OID keys:
 * </p>
 *
 * <ul>
 *	<li>From Cisco IOS</li>
 *	<ul>
 *	 <li>ifDescr</li>
 *	 <li>ifName</li>
 *	 <li>ifVlan</li>
 *	 <li>ifVlansAllowed</li>
 *	 <li>portPortName</li>
 *	</ul>
 * </ul>
 * </p>
 *
 */

public class CiscoSwIOS implements DeviceHandler
{
	private static String[] canHandleOids = {
			"ifDescr", 
			"ifVlan", 
			"ifVlansAllowed", 
			"ifPortName",
	};
	private static Pattern interfacePattern = Pattern.compile("((.*?)(\\d+))/(\\d+)(/(\\d+))?");
	SimpleSnmp sSnmp;

	public int canHandleDevice(Netbox nb) {
		int v = nb.isSupportedOids(canHandleOids) ? ALWAYS_HANDLE : NEVER_HANDLE;

		Log.d("IOS_CANHANDLE", "CHECK_CAN_HANDLE", "Can handle device: " + v);
		return v;
	}

	public void handleDevice(Netbox nb, SimpleSnmp sSnmp, ConfigParser cp, DataContainers containers) throws TimeoutException
	{
		Log.setDefaultSubsystem("IOS_DEVHANDLER");

		ModuleContainer mc;
		{
			DataContainer dc = containers.getContainer("ModuleContainer");
			if (dc == null) {
				Log.w("NO_CONTAINER", "No ModuleContainer found, plugin may not be loaded");
				return;
			}
			if (!(dc instanceof ModuleContainer)) {
				Log.w("NO_CONTAINER", "Container is not a ModuleContainer! " + dc);
				return;
			}
			mc = (ModuleContainer)dc;
		}
		
		SwportContainer sc;
		{
			DataContainer dc = containers.getContainer("SwportContainer");
			if (dc == null) {
				Log.w("NO_CONTAINER", "No SwportContainer found, plugin may not be loaded");
				return;
			}
			if (!(dc instanceof SwportContainer)) {
				Log.w("NO_CONTAINER", "Container is not an SwportContainer! " + dc);
				return;
			}
			sc = (SwportContainer)dc;
		}

		String netboxid = nb.getNetboxidS();
		String ip = nb.getIp();
		String cs_ro = nb.getCommunityRo();
		String type = nb.getType();
		//String sysName = nb.getSysname();
		//String cat = nb.getCat();
		this.sSnmp = sSnmp;

		processIOS(nb, mc, sc);

		// Commit data
		if (mc.isCommited()) sc.setEqual(mc);
		sc.commit();
	}
	
	void processIOS(Netbox nb, ModuleContainer mc, SwportContainer sc) throws TimeoutException
	{
		List ifDescriptions = sSnmp.getAll(nb.getOid("ifDescr"), true);
		// This nice OID, if supported, returns a list of module/port-numbers assigned to ifIndexes
		Map portIfindexes = sSnmp.getAllMap(nb.getOid("portIfIndex"));
		MultiMap portIfindexMap = null;
		if (portIfindexes != null) {
			portIfindexMap = util.reverse(portIfindexes);
		}
		int moduleCount = getModuleCount(ifDescriptions);
		
		Set matchedIfindexes = new HashSet();
		MultiMap moduleDescriptions = new HashMultiMap();
		MultiMap modulePortMM = new HashMultiMap();		
		// Only add interfaces matching our ifDescr pattern
		if (ifDescriptions != null) {
			Log.d("PROCESS_IOS", "ifDescr reported " + ifDescriptions.size() + " interfaces (guessed "+ moduleCount +" modules)");
			for (Iterator it = ifDescriptions.iterator(); it.hasNext();) {
				String[] s = (String[])it.next();
				String ifindex = s[0];
				String ifdescr = s[1];
				
				Matcher matcher = interfacePattern.matcher(ifdescr);
				if (matcher.matches()) {
					// Proceed with adding this port to this box
					matchedIfindexes.add(ifindex);
					
					int moduleNumber;
					int portNumber;
					// Assign a module number to the interface
					if (portIfindexMap != null && portIfindexMap.containsKey(ifindex)) {
						// use the portIfIndex OID to extract module and port number
						// value pattern is "module.port", e.g. "2.13"
						String moduleDotPort = (String) portIfindexMap.getFirst(ifindex);
						Log.d("PROCESS_IOS", "portIfIndex maps port " + ifdescr + " to " + moduleDotPort);
						
						String[] mdp = moduleDotPort.split("\\.");
						moduleNumber = Integer.parseInt(mdp[0]);
					} else {
						// otherwise, guesstimate it from the ifDescr value
						moduleNumber = Integer.parseInt(matcher.group(3));
					}
					// Do not use port numbers from the portIfIndex OID, as this breaks with the old port numbering scheme
					// Instead, we parse a possible port number from the ifDescr value
					if (util.groupCountNotNull(matcher) >= 6) {
						// submodule numbering is in use
						portNumber = Integer.parseInt(matcher.group(6));
					} else {
						portNumber = Integer.parseInt(matcher.group(4));
					}

					String moduleDescription = matcher.group(1);
					Module module = mc.getModule(moduleNumber);
					if (module == null) {
						/* This is some sort of old heuristic.  If we 
						 * somehow think this interface belongs to the yet
						 * non-existant module 0, and we guessed that there
						 * exists only one module on this box, we create
						 * module 1 and attach the port there instead.
						 */
						if (moduleNumber == 0 && moduleCount == 1) {
							moduleNumber = 1;
							module = mc.moduleFactory(1);
						} else {
							// Normally, we don't allow module creation here, so ignore this port if we couldn't attach it anywhere
							Log.w("PROCESS_IOS", "Module " + moduleNumber + " (parsed from "+ ifdescr +") does not exist, skipping");
							continue;
						}
					}
					SwModule swModule = sc.swModuleFactory(moduleNumber);
					Swport swPort = swModule.swportFactory(ifindex); // Create module <-> ifindex mapping

					// Compose a module description by concatenating distinct interface type names such as "FastEthernet" and "GigabitEthernet"
					if (moduleDescription != null) {
						boolean composed = false;
						if (!moduleDescriptions.put(new Integer(moduleNumber), moduleDescription)) {
							String composedDescr = composeModuleDescription(moduleNumber, moduleDescriptions.get(new Integer(moduleNumber)));
							if (!moduleDescription.equals(composedDescr)) {
								composed = true;
								moduleDescription = composedDescr;
							}
						}
						// Only set if we composed a new description, or no description was previously set
						if (module.getDescr() == null || composed) {
							module.setDescr(moduleDescription);
						}
					}
					
					// Assign the chosen port number
					if (modulePortMM.put(new Integer(moduleNumber), new Integer(portNumber))) {
						swPort.setPort(new Integer(portNumber));
					} else {
						// The port number has already been used on this module, fall back to using ifindex as port number
						// (and hope to your deities that it works)
						swPort.setPort(Integer.valueOf(ifindex));
					}
				}
			}
		}
		// We didn't find any acceptable interfaces, so we bail out
		if (matchedIfindexes.isEmpty()) return;
		
		ifDescriptions = sSnmp.getAll(nb.getOid("ifVlan"));
		if (ifDescriptions != null) {
			for (Iterator it = ifDescriptions.iterator(); it.hasNext();) {
				String[] s = (String[])it.next();
				String ifindex = s[0];
				if (!matchedIfindexes.contains(ifindex)) continue;
				int vlan = 0;
				try{
					vlan = Integer.parseInt(s[1]);
				} catch	 (NumberFormatException e) {
					Log.w("PROCESS_IOS", "netboxid: " + nb.getNetboxid() + " ifindex: " + s[0] + " NumberFormatException on vlan: " + s[1]);
				}
				sc.swportFactory(ifindex).setVlan(vlan);
			}
		}

		ifDescriptions = sSnmp.getAll(nb.getOid("ifTrunk"));
		if (ifDescriptions != null) {
			for (Iterator it = ifDescriptions.iterator(); it.hasNext();) {
				String[] s = (String[])it.next();
				String ifindex = s[0];
				if (!matchedIfindexes.contains(ifindex)) continue;

				boolean trunk = (s[1].equals("1") ? true : false);
				sc.swportFactory(ifindex).setTrunk(trunk);
			}
		}

		ifDescriptions = sSnmp.getAll(nb.getOid("ifVlansAllowed"));
		if (ifDescriptions != null) {
			for (Iterator it = ifDescriptions.iterator(); it.hasNext();) {
				String[] s = (String[])it.next();
				if (!matchedIfindexes.contains(s[0])) continue;
				sc.swportFactory(s[0]).setHexstring(s[1]);
			}
		}
		
		ifDescriptions = sSnmp.getAll(nb.getOid("ifPortName"), true);
		if (ifDescriptions != null) {
			for (Iterator it = ifDescriptions.iterator(); it.hasNext();) {
				String[] s = (String[])it.next();
				if (!matchedIfindexes.contains(s[0])) continue;
				sc.swportFactory(s[0]).setPortname(s[1]);
			}
		}

	}

	private String composeModuleDescription(int module, Set names) {
		Set ls = new HashSet(names);
		String pat = "([a-zA-z]+)Ethernet(\\d+)";
		List nl = new ArrayList();
		boolean eth  = false;
		for (Iterator it = ls.iterator(); it.hasNext();) {
			String s = (String)it.next();
			Matcher m = Pattern.compile(pat).matcher(s);
			if (m.matches()) {
				it.remove();
				nl.add(m.group(1));
				eth = true;
			}
		}
		Collections.sort(nl);
		String n = "";
		for (Iterator it = nl.iterator(); it.hasNext();) {
			n += it.next();
		}

		nl.clear();
		for (Iterator it = ls.iterator(); it.hasNext();) {
			nl.add(it.next());
		}
		Collections.sort(nl);
		for (Iterator it = nl.iterator(); it.hasNext();) {
			n += it.next();
		}

		if (eth) {
			n += "Ethernet" + module;
		}
		return n;
	}
	
	/**
	 * Guesstimates a module count for the current device, based on ifDescr pattern matching.
	 * @param ifDescriptions A List of ifDescription SNMP responses
	 * @return A guesstimated number of modules.
	 */
	private int getModuleCount(List ifDescriptions) {
		HashSet modules = new HashSet();
		if (ifDescriptions != null) {
			for (Iterator it = ifDescriptions.iterator(); it.hasNext();) {
				String[] s = (String[])it.next();
				String ifindex = s[0];
				String ifdescr = s[1];
				
				Matcher matcher = interfacePattern.matcher(ifdescr);
				if (matcher.matches()) {		
					// guesstimate a module number from the ifDescr value
					Integer moduleNumber = Integer.valueOf(matcher.group(3));
					modules.add(moduleNumber);
				}
			}
		}
		return modules.size();
	}
}
