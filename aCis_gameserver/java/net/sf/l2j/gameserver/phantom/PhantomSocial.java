package net.sf.l2j.gameserver.phantom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.Config;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public final class PhantomSocial
{
	private static final Map<Integer, Long> NEXT_TALK = new ConcurrentHashMap<>();
	private static final String[] CASUAL_LINES =
	{
		"buen drop por aca",
		"voy a probar otro spot despues",
		"estos mobs rinden bastante",
		"me falta un poco para subir",
		"ando juntando herbs para no parar",
		"si cae algo bueno aviso",
		"esta zona esta tranquila",
		"necesito mejorar el arma pronto"
	};
	
	private PhantomSocial()
	{
	}
	
	public static void think(Player phantom)
	{
		if (phantom == null || !PhantomConfig.socialChatEnabled() || phantom.isDead() || phantom.getCast().isCastingNow())
			return;
		
		final long now = System.currentTimeMillis();
		final long next = NEXT_TALK.getOrDefault(phantom.getObjectId(), 0L);
		if (now < next)
			return;
		
		NEXT_TALK.put(phantom.getObjectId(), now + Rnd.get(PhantomConfig.socialChatMinDelayMs(), PhantomConfig.socialChatMaxDelayMs()));
		if (Rnd.get(100) >= PhantomConfig.socialChatChance())
			return;
		
		final Player nearby = findNearbyRealPlayer(phantom);
		if (nearby == null)
			return;
		
		say(phantom, CASUAL_LINES[Rnd.get(CASUAL_LINES.length)]);
	}
	
	public static void forget(int objectId)
	{
		NEXT_TALK.remove(objectId);
	}
	
	private static Player findNearbyRealPlayer(Player phantom)
	{
		final int myFaction = phantom.getFactionId();
		final Player[] result = new Player[1];
		phantom.forEachKnownTypeInRadius(Player.class, PhantomConfig.socialChatRange(), player ->
		{
			if (result[0] != null || player == null || player == phantom || player.isDead() || !player.isVisible() || PhantomEngine.isPhantom(player.getObjectId()))
				return;
			
			if (Config.ENABLE_FACTION_SYSTEM && myFaction > 0 && player.getFactionId() != myFaction)
				return;
			
			result[0] = player;
		});
		return result[0];
	}
	
	private static void say(Player phantom, String text)
	{
		final CreatureSay packet = new CreatureSay(phantom, SayType.ALL, text);
		phantom.sendPacket(packet);
		phantom.forEachKnownTypeInRadius(Player.class, 1250, player -> player.sendPacket(packet));
		PhantomLog.info("Social chat " + phantom.getName() + ": " + text);
	}
}
