package net.sf.l2j.gameserver.handler.itemhandlers;

import net.sf.l2j.gameserver.bingo.BingoCard;
import net.sf.l2j.gameserver.bingo.BingoManager;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.model.actor.Playable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;

public class L2BingoCard implements IItemHandler
{
	@Override
	public void useItem(Playable playable, ItemInstance item, boolean forceUse)
	{
		if (playable instanceof Player player)
		{
			if (BingoManager.getInstance().getGame() == null)
			{
				player.destroyItem(item, false);
				return;
			}
			
			final BingoCard card = BingoManager.getInstance().getCardById(item.getObjectId());
			if (card == null)
			{
				player.destroyItem(item, false);
				return;
			}
			
			if (BingoManager.getInstance().getGame().getManager() == player)
				return;

			// Las cartelas de otros duenos deben usarse/abrirse al menos una vez para transferir la posesion y registrarse.
			if (card.getOwnerId() != player.getObjectId())
			{
				// Intento de usar una cartela comprada por otro player para evadir el limite.
				if (BingoManager.getInstance().getGame().getCardsByPlayer(player.getObjectId()).size() >= BingoManager.MAX_CARTELAS)
					return;
				
				BingoManager.getInstance().updateCardOwner(player, card);
			}
			
			BingoManager.getInstance().showCardHtm(player, item.getObjectId(), 0);
		}
	}
}
