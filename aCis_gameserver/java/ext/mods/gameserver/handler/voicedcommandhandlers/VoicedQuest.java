package ext.mods.gameserver.handler.voicedcommandhandlers;

import ext.mods.gameserver.handler.IVoicedCommandHandler;
import ext.mods.gameserver.model.actor.Player;
import ext.mods.quests.QuestManager;

public class VoicedQuest implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"quest"
	};

	@Override
	public boolean useVoicedCommand(String command, Player player, String target)
	{
		if (command.startsWith("quest"))
		{
			QuestManager.getInstance().showMenuQuest(player, 1);
		}
		return true;
	}

	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}