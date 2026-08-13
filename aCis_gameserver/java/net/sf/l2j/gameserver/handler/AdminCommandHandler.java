package net.sf.l2j.gameserver.handler;

import net.sf.l2j.commons.logging.CLogger;

public class AdminCommandHandler extends AbstractHandler<Integer, IAdminCommandHandler>
{
	private static final CLogger LOGGER = new CLogger(AdminCommandHandler.class.getName());
	
	protected AdminCommandHandler()
	{
		super(IAdminCommandHandler.class, "admincommandhandlers");
		
		LOGGER.info("Total: {} admin command(s) registered.", _entries.size());
	}
	
	@Override
	protected void registerHandler(IAdminCommandHandler handler)
	{
		final String[] commands = handler.getAdminCommandList();
		
		final StringBuilder sb = new StringBuilder();
		for (String id : commands)
		{
			if (sb.length() > 0)
				sb.append(", ");
			sb.append(id);
			_entries.put(id.hashCode(), handler);
		}
		
		LOGGER.info("Loaded admin command handler {} ({} command(s)): {}", handler.getClass().getSimpleName(), commands.length, sb.toString());
	}
	
	@Override
	public IAdminCommandHandler getHandler(Object key)
	{
		if (!(key instanceof String adminCommand))
			return null;
		
		final int index = adminCommand.indexOf(" ");
		final String command = (index == -1) ? adminCommand : adminCommand.substring(0, index);
		
		return super.getHandler(command.hashCode());
	}
	
	public static AdminCommandHandler getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final AdminCommandHandler INSTANCE = new AdminCommandHandler();
	}
}