package net.sf.l2j.gameserver.phantom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PhantomState
{
	private static final Map<Integer, Entry> STATES = new ConcurrentHashMap<>();
	
	private PhantomState()
	{
	}
	
	public static void register(int objectId)
	{
		STATES.putIfAbsent(objectId, new Entry(false, false, System.currentTimeMillis(), "Registered"));
	}
	
	public static void unregister(int objectId)
	{
		STATES.remove(objectId);
	}
	
	public static void setAggressive(int objectId, boolean aggressive)
	{
		update(objectId, aggressive, isPk(objectId), aggressive ? "Aggressive" : "Normal");
	}
	
	public static boolean isAggressive(int objectId)
	{
		final Entry entry = STATES.get(objectId);
		return entry != null && entry.aggressive();
	}
	
	public static void setPk(int objectId, boolean pk)
	{
		update(objectId, isAggressive(objectId), pk, pk ? "PK rage" : "PK cleared");
	}
	
	public static boolean isPk(int objectId)
	{
		final Entry entry = STATES.get(objectId);
		return entry != null && entry.pk();
	}
	
	public static String label(int objectId)
	{
		final Entry entry = STATES.get(objectId);
		if (entry == null)
			return "Normal";
		if (entry.pk())
			return "PK";
		if (entry.aggressive())
			return "Aggr";
		return "Normal";
	}
	
	public static String note(int objectId)
	{
		final Entry entry = STATES.get(objectId);
		return entry == null ? "-" : entry.note();
	}
	
	private static void update(int objectId, boolean aggressive, boolean pk, String note)
	{
		STATES.put(objectId, new Entry(aggressive, pk, System.currentTimeMillis(), note));
	}
	
	private record Entry(boolean aggressive, boolean pk, long since, String note)
	{
	}
}
