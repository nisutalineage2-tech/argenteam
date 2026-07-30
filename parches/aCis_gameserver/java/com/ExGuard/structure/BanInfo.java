package com.ExGuard.structure;

public class BanInfo
{
	private String _name;
	private String _lastip;
	private String _reason;
	
	public BanInfo(String name, String lastip, String reason)
	{
		_name = name;
		_lastip = lastip;
		_reason = reason;
	}

	public String getName()
	{
		return _name;
	}
	
	public String getLastIp()
	{
		return _lastip;
	}
	
	public String getReason()
	{
		return _reason;
	}
}