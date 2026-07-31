package net.sf.l2j.commons.util;

/**
 * A class holding system oriented methods.
 */
public class SysUtil
{
	private SysUtil()
	{
		throw new IllegalStateException("Utility class");
	}
	
	private static final int MEBIOCTET = 1024 * 1024;
	
	/**
	 * Escapes a string for safe embedding in L2 client HTML.
	 * The client's ListParser crashes on literal '&lt;'/'&gt;'/'&amp;' inside text or attributes,
	 * so they must be replaced by their HTML entities.
	 * @param value the raw string (may be null).
	 * @return the escaped string, or empty string if null.
	 */
	public static String escapeHtml(String value)
	{
		if (value == null || value.isEmpty())
			return "";
		
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	/**
	 * @return the used amount of memory the JVM is using.
	 */
	public static long getUsedMemory()
	{
		return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MEBIOCTET;
	}
	
	/**
	 * @return the maximum amount of memory the JVM can use.
	 */
	public static long getMaxMemory()
	{
		return Runtime.getRuntime().maxMemory() / MEBIOCTET;
	}
}