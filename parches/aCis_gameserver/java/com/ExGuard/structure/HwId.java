package com.ExGuard.structure;

public class HwId
{
	private String _hwidHdd, _hwidMac, _hwidGuId, _hwidUser;
	
	public HwId(String hwidHdd, String hwidMac, String hwidGuId, String hwidUser)
	{
		_hwidHdd = hwidHdd;
		_hwidMac = hwidMac;
		_hwidGuId = hwidGuId;
		_hwidUser = hwidUser;
	}
	
	public String getHdd()
	{
		return _hwidHdd;
	}
	
	public String getMac()
	{
		return _hwidMac;
	}
	
	public String getGuId()
	{
		return _hwidGuId;
	}
	
	public String getUser()
	{
		return _hwidUser;
	}
	
	public boolean checkNetHWID(HwId hwid)
	{
		return (hwid.getGuId().equals(getGuId()) && hwid.getUser().equals(getUser()));
	}
	
	public boolean checkCombinedHWID(HwId hwid)
	{
		return (hwid.getHdd().equals(getHdd()) && hwid.getMac().equals(getMac()));
	}
}