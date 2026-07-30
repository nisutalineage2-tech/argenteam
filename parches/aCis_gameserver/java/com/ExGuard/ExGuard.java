package com.ExGuard;

import com.ExGuard.packet.SendBanInfo;
import com.ExGuard.structure.BanInfo;
import com.ExGuard.structure.HwId;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import net.sf.l2j.commons.config.ExProperties;

import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.LoginServerThread;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

public class ExGuard
{
	private static final Logger _log = Logger.getLogger(ExGuard.class.getName());
	
	private static Map<HwId, BanInfo> _bannedHwid;
	private static List<String> _list;
	
	public ExGuard()
	{
		_bannedHwid = new ConcurrentHashMap<>();
		_list = new CopyOnWriteArrayList<>();
		load();
		loadList();
	}
	
	public static void loadList()
	{
		final ExProperties blockList = new ExProperties();
		try
		{
			blockList.load(new File("./config/ExGuard.properties"));
			
			String block = blockList.getProperty("ExceptionUrl", "");
			
			String[] split = block.split(";");
			
			for (String s : split)
			{
				if (s.length() > 7)
					_list.add(s);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		_log.info("ExGuard : load " + _list.size() + " default Exception ips");
	}
	
	private static void updateData()
	{
		final File file = new File("./config/ExGuard.properties");
		StringBuilder string = new StringBuilder();
		
		try (BufferedReader reader = new BufferedReader(new FileReader(file.toString())))
		{
			String line = null;
			while ((line = reader.readLine()) != null)
			{
				if (!line.startsWith("ExceptionUrl"))
					string.append(line + "\n");
				else
				{
					String ips = "";
					for (String i : _list)
						ips += (i += ";");
					
					string.append("ExceptionUrl = " + ips + "\n");
				}
			}
			
			try (BufferedWriter out = new BufferedWriter(new FileWriter(file.toString())))
			{
				out.write(string.toString());
				out.close();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public static List<String> getUrls()
	{
		return _list;
	}
	
	public String getInfo(HwId hwid, String ip)
	{
		boolean net = getUrls().contains(ip);
		for (Entry<HwId, BanInfo> find : _bannedHwid.entrySet())
		{
			if (net ? hwid.checkNetHWID(find.getKey()) : hwid.checkCombinedHWID(find.getKey()))
				return "Player : " + find.getValue().getName() + " banned with this Hwid\nReason is : " + find.getValue().getReason() + "";
		}
		return "free";
	}
	
	public void BanHwId(L2PcInstance player, String reason)
	{
		final HwId hwid = player.getHwId();
		final BanInfo baninfo = new BanInfo(player.getName(), player.getClient().getConnection().getInetAddress().getHostAddress(), reason);
		
		_bannedHwid.put(hwid, baninfo);
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection(); PreparedStatement stm_items = con.prepareStatement("INSERT INTO ex_guard_ban_data (name,hdd,mac,guid,userid,lastip,reason) VALUES (?,?,?,?,?,?,?)"))
		{
			stm_items.setString(1, baninfo.getName());
			stm_items.setString(2, hwid.getHdd());
			stm_items.setString(3, hwid.getMac());
			stm_items.setString(4, hwid.getGuId());
			stm_items.setString(5, hwid.getUser());
			stm_items.setString(6, baninfo.getLastIp());
			stm_items.setString(7, reason);
			stm_items.executeUpdate();
			stm_items.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		for (L2PcInstance bot : World.getInstance().getPlayers())
		{
			if (hwid.checkCombinedHWID(bot.getHwId()))
			{
				LoginServerThread.getInstance().sendAccessLevel(bot.getAccountName(), -100);
				bot.setAccessLevel(-100);
				
				bot.sendPacket(new SendBanInfo("Player : " + baninfo.getName() + " banned with this Hwid\nReason is : " + reason + ""));
				bot.getClient().cleanMe(true);
			}
		}
	}
	
	private static void load()
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement st = con.prepareStatement("SELECT * FROM ex_guard_ban_data");
			ResultSet rs = st.executeQuery();
			
			while (rs.next())
			{
				final String name = rs.getString("name");
				final String hdd = rs.getString("hdd");
				final String mac = rs.getString("mac");
				final String guid = rs.getString("guid");
				final String user = rs.getString("userid");
				final String lastip = rs.getString("lastip");
				final String reason = rs.getString("reason");
				
				_bannedHwid.put(new HwId(hdd, mac, guid, user), new BanInfo(name, lastip, reason));
			}
			
			rs.close();
			st.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		_log.info("ExGuard : load " + _bannedHwid.size() + " banned Hwid");
	}
	
	public static void removeUrl(L2PcInstance player, String url)
	{
		String ip = getIp(url);
		
		if (ip.equals("error"))
			player.sendMessage("Exception : This is not correct : " + url + " ");
		else
		{
			if (!_list.contains(ip))
				player.sendMessage("Exception : This Ip : " + ip + " never added");
			else
			{
				_list.remove(ip);
				
				updateData();
				player.sendMessage("Exception : This Ip : " + ip + " removed");
			}
		}
	}
	
	public static void addUrl(L2PcInstance player, String url)
	{
		String ip = getIp(url);
		
		if (!ip.equals("error"))
		{
			if (!_list.contains(ip))
			{
				_list.add(ip);
				
				if (player != null)
				{
					updateData();
					player.sendMessage("Added on Exception " + (url.equals(ip) ? " ip : " + ip + " " : " Url : " + url + " With Ip : " + ip + ""));
				}
			}
			else if (player != null)
				player.sendMessage("Exception : This Ip : " + ip + " can't saved again");
		}
		else if (player != null)
			player.sendMessage("Exception : This is not correct : " + url + " ");
	}
	
	private static String getIp(String url)
	{
		try
		{
			String[] inet = InetAddress.getByName(url).toString().split("/");
			return inet[1].length() > 6 ? inet[1] : inet[0];
		}
		catch (UnknownHostException e)
		{
			return "error";
		}
	}
	
	public static ExGuard getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final ExGuard _instance = new ExGuard();
	}
}