package net.sf.l2j.gameserver.phantom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.Config;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
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
	
	private static final String[] WAR_LINES_GOOD =
	{
		"Por la Orden! A la guerra!",
		"Vamos equipo! A barrer con esos sombrios!",
		"La victoria es nuestra!",
		"Ya era hora de otra guerra!",
		"Que tiemblen los Shadow Legion!",
		"Voy para el campo de batalla!",
		"Al ataque! Por la gloria!",
		"No dejaremos ni uno vivo!",
		"Listo para la batalla!",
		"A mostrarles nuestro poder!",
		"Nos vemos en el campo!",
		"Espero estar a la altura de la batalla!",
		"Voy para alla, no empiecen sin mi!",
		"Mejor que esten preparados!",
		"Juntos somos imparables!"
	};
	
	private static final String[] WAR_LINES_EVIL =
	{
		"Ja! Otra guerra? Vamos a divertirnos!",
		"Es hora de sembrar caos!",
		"Los Elite van a caer!",
		"Sangre y oscuridad!",
		"Que tiemblen los Order of the Elites!",
		"Nadie nos detendra!",
		"Vamos a demostrarles quienes mandan!",
		"Por la Legion! A destrozarlos!",
		"No dejaremos a nadie en pie!",
		"El poder oscuro nos guia!",
		"Vamos! Nos espera la batalla!",
		"Sere yo quien decida su destino!",
		"Ya van a ver de que estamos hechos!",
		"La oscuridad cubrira el campo!",
		"Voy alla a pegar un poco!"
	};
	
	private static final String[] WAR_LINES_NEUTRAL =
	{
		"Escuche que hay guerra de facciones!",
		"Que emocion! Guerra otra vez!",
		"Hay que unirse a la batalla!",
		"Van a estar todos peleando alla!",
		"Voy a ver de que trata la guerra!",
		"Mejor ir a la guerra antes que farmear!",
		"Todos al campo de batalla!",
		"Vamos alla a participar!",
		"No me pienso perder esta guerra!",
		"A darle duro a los contrarios!"
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
		
		final boolean warRunning = Config.ENABLE_FACTION_SYSTEM && phantom.getFactionId() > 0 && FactionWarManager.getInstance().isRunning();
		
		final Player nearby = findNearbyRealPlayer(phantom, warRunning);
		if (nearby == null)
			return;
		
		if (warRunning)
		{
			final String[] lines;
			if (phantom.getFactionId() == 1)
				lines = WAR_LINES_GOOD;
			else if (phantom.getFactionId() == 2)
				lines = WAR_LINES_EVIL;
			else
				lines = WAR_LINES_NEUTRAL;
			
			say(phantom, lines[Rnd.get(lines.length)]);
		}
		else
			say(phantom, CASUAL_LINES[Rnd.get(CASUAL_LINES.length)]);
	}
	
	public static void sayWarPhrase(Player phantom)
	{
		if (phantom == null || phantom.isDead())
			return;
		
		final String[] lines;
		if (phantom.getFactionId() == 1)
			lines = WAR_LINES_GOOD;
		else if (phantom.getFactionId() == 2)
			lines = WAR_LINES_EVIL;
		else
			lines = WAR_LINES_NEUTRAL;
		
		say(phantom, lines[Rnd.get(lines.length)]);
	}
	
	public static void forget(int objectId)
	{
		NEXT_TALK.remove(objectId);
	}
	
	private static Player findNearbyRealPlayer(Player phantom, boolean warRunning)
	{
		final int myFaction = phantom.getFactionId();
		final Player[] result = new Player[1];
		phantom.forEachKnownTypeInRadius(Player.class, PhantomConfig.socialChatRange(), player ->
		{
			if (result[0] != null || player == null || player == phantom || player.isDead() || !player.isVisible())
				return;
			
			// During war: can talk to anyone (faction members, neutrals, even enemies)
			// Outside war: only talk to same faction or non-faction players
			if (!warRunning && PhantomEngine.isPhantom(player.getObjectId()))
				return;
			
			if (!warRunning && Config.ENABLE_FACTION_SYSTEM && myFaction > 0 && player.getFactionId() != myFaction)
				return;
			
			result[0] = player;
		});
		return result[0];
	}
	
	public static void say(Player phantom, String text)
	{
		final CreatureSay packet = new CreatureSay(phantom, SayType.ALL, text);
		phantom.sendPacket(packet);
		phantom.forEachKnownTypeInRadius(Player.class, 1250, player -> player.sendPacket(packet));
		PhantomLog.info("Social chat " + phantom.getName() + ": " + text);
	}
}
