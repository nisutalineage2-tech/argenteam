package ext.mods.gameserver.handler;

import net.sf.l2j.gameserver.model.actor.Player;

/**
 * Interface for voiced command handlers.
 * Similar to IUserCommandHandler but for voiced commands (starting with '.')
 */
public interface IVoicedCommandHandler
{
	/**
	 * This is the worker method that is called when a {@link Player} uses a voiced command.
	 * @param command : The voiced command string (without the dot).
	 * @param player : The Player who is requesting the command.
	 * @param target : The target of the command (can be null).
	 * @return True if the command was handled, false otherwise.
	 */
	public boolean useVoicedCommand(String command, Player player, String target);

	/**
	 * @return all known voiced commands this handler can process.
	 */
	public String[] getVoicedCommandList();
}