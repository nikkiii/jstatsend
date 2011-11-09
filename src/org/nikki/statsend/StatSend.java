package org.nikki.statsend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.hyperic.sigar.Mem;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.json.JSONException;
import org.json.JSONObject;

public class StatSend {
	
	private static Sigar sigar;

	public static void main(String[] args) {
		try {
			sigar = new Sigar();
			
			final Properties props = new Properties();
			props.load(new FileInputStream("jstatsend.conf"));

			final Timer timer = new Timer();
			timer.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					try {
						URLConnection conn = new URL(props.getProperty("url"))
								.openConnection();
						conn.setDoOutput(true);

						conn.setRequestProperty("User-Agent", "JSS/1.0");

						OutputStreamWriter writer = new OutputStreamWriter(conn
								.getOutputStream());
						writer.write(construct(props.getProperty("uid"), props.getProperty("key")));
						writer.flush();

						BufferedReader reader = new BufferedReader(
								new InputStreamReader(conn.getInputStream()));
						String line = reader.readLine();
						if(line != null) {
							//Fail :(
							System.out.println("ERROR! Server returned "+line);
							System.exit(0);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}, 0, 60 * 1000 * Integer.parseInt(props.getProperty("interval")));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String construct(String uid, String key) throws JSONException, SigarException {
		JSONObject construct = new JSONObject();
		construct.put("uid", uid);
		construct.put("key", key);
		construct.put("hostname", sigar.getNetInfo().getHostName());

		JSONObject obj = new JSONObject();
		obj.put("uptime", formatUptime(sigar.getUptime().getUptime()));
		obj.put("load1", ((int) (sigar.getCpuPerc().getCombined() * 100)));
		obj.put("load5", 0);
		obj.put("load15", 0);
		construct.put("uplo", obj);

		Mem mem = sigar.getMem();

		JSONObject memory = new JSONObject();
		memory.put("total", byteToMByte(mem.getTotal()));
		memory.put("used", byteToMByte(mem.getUsed()));
		memory.put("free", byteToMByte(mem.getFree()));
		memory.put("bufcac", 0);

		construct.put("ram", memory);

		LinkedList<HashMap<String, Object>> fsystems = new LinkedList<HashMap<String, Object>>();
		long total = 0;
		long used = 0;
		long free = 0;
		for (File file : File.listRoots()) {
			long ftotal = file.getTotalSpace();
			long ffree = file.getFreeSpace();
			long fused = ftotal - ffree;

			HashMap<String, Object> system = new HashMap<String, Object>();
			system.put("mount", file.getPath());
			system.put("total", byteToKByte(ftotal));
			system.put("used", byteToKByte(fused));
			system.put("avail", byteToKByte(ffree));
			fsystems.add(system);

			total += ftotal;
			free += ffree;
			used += fused;
		}

		HashMap<String, Object> bla = new HashMap<String, Object>();
		bla.put("single", fsystems);
		HashMap<String, Object> tot = new HashMap<String, Object>();
		tot.put("total", byteToKByte(total));
		tot.put("free", byteToKByte(free));
		tot.put("used", byteToKByte(used));
		bla.put("total", tot);

		construct.put("disk", bla);

		return construct.toString();
	}

	public static String formatUptime(double uptime) {
		if (uptime >= 86400) {
			return ((int) (uptime / 86400)) + " days";
		}
		if (uptime >= 3600) {
			return ((int) (uptime / 3600)) + ":" + ((int) (uptime % 3600) / 60);
		}
		if (uptime >= 60) {
			return ((int) (uptime / 60)) + " minutes";
		}  
		return uptime + " seconds";
	}

	public static int byteToMByte(long bytes) {
		return (int) (bytes / 1024 / 1024);
	}

	public static long byteToKByte(long bytes) {
		return (long) (bytes / 1024);
	}
}
