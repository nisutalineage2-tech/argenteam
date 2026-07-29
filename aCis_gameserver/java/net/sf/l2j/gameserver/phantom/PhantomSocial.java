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
		"Alguien para party en este spot?",
		"Buen drop por aca, estoy joya.",
		"Saquenme de Cruma, me estoy quedando sin pots jaja",
		"Voy a probar otro spot despues.",
		"Estos mobs rinden bastante la verdad.",
		"Me falta un toque para subir de nivel.",
		"Ando juntando herbs para no parar.",
		"Si cae algo bueno aviso.",
		"Esta zona esta tranquila, se deja estar.",
		"Necesito mejorar el arma pronto.",
		"Buscando party para hacer unas quests.",
		"Que alguien me preste un buff porfa jaja.",
		"Tengo que farmear un poco mas para el siguiente nivel.",
		"Alguien sabe si este item sirve para algo?",
		"Casi saco el skill nuevo, me falta poco.",
		"Estoy modeando aca hasta que me canse.",
		"Voy a dejar el char afk un rato.",
		"Hay alguien para hacer unas ruins?",
		"Que pesados estos mobs, no sueltan nada bueno.",
		"Me voy a comprar unas cositas al town.",
		"Que tal el drop por alla?",
		"Voy a ver si saco algo para vender en la tienda.",
		"Paren de matar mis mobs jaja",
		"Hace rato que no veo caer nada raro.",
		"Estoy viendo si cambio de arma o mejoro esta.",
		"Noche tranquila para levear.",
		"Un rato mas y me duermo.",
		"No tengo suerte con el drop hoy.",
		"Voy a ir a la mision de clase cuando termine aca.",
		"Che, alguien tiene spare de Soulshots?"
	};
	
	private static final String[] WAR_LINES_GOOD =
	{
		"Por la Orden! Vamos a demostrarles poder!",
		"Vamos equipo! Es hora de barrer con esos sombrios!",
		"La victoria es nuestra si luchamos juntos!",
		"Ya era hora de otra guerra! Vamos con todo!",
		"Que tiemblen los Shadow Legion, alla vamos!",
		"Voy para el campo de batalla, nos vemos alla!",
		"Al ataque! Por la gloria de la Orden!",
		"No dejaremos ni uno en pie! Vamos!",
		"Listo para la batalla! Estoy re manija!",
		"Vamos a mostrarles de que estamos hechos!",
		"Nos vemos en el campo, no falten!",
		"Espero estar a la altura de esta batalla!",
		"Voy para alla, no empiecen la fiesta sin mi!",
		"Mejor que esten preparados porque vamos con todo!",
		"Juntos somos imparables! Vamos Order!",
		"Al fin llego la guerra! Los esperamos alla!",
		"Vamos muchachos! A defender el honor!",
		"Esto va a ser epico! Alla nos vemos!",
		"Ya estoy en camino! No me dejen atras!",
		"Es hora de pegarle duro a los enemigos!"
	};
	
	private static final String[] WAR_LINES_EVIL =
	{
		"Ja! Otra guerra? Vamos a divertirnos un rato!",
		"Llego la hora de sembrar caos y destruccion!",
		"Los Elite van a caer uno por uno!",
		"Sangre y oscuridad! Vamos Legion!",
		"Que tiemblen los Order of the Elites!",
		"Nadie nos va a detener esta vez!",
		"Vamos a demostrarles quien manda en esta tierra!",
		"Por la Legion! Vamos a destrozarlos a todos!",
		"No vamos a dejar a nadie en pie!",
		"El poder oscuro nos guia hacia la victoria!",
		"Vamos! Alla los espero para la batalla!",
		"Yo voy a ser el que decida el destino de la guerra!",
		"Ya van a ver de lo que somos capaces!",
		"La oscuridad se va a apoderar del campo!",
		"Voy alla a pegarle a todo lo que se mueva!",
		"Ya estamos en guerra! Salgan todos!",
		"Esto se pone bueno! Vamos a pelear!",
		"Al fin algo de emocion! Los espero alla!",
		"Que se escondan los buenos! Alla voy!",
		"La oscuridad los va a devorar!"
	};
	
	private static final String[] WAR_LINES_NEUTRAL =
	{
		"Escuche que hay guerra de facciones! Voy para alla!",
		"Que emocion! Guerra otra vez! No me lo pierdo!",
		"Hay que unirse a la batalla y ver que pasa!",
		"Van a estar todos peleando, yo no me quedo afuera!",
		"Voy a ver de que trata esta guerra!",
		"Mejor ir a la guerra que estar farmeando toda la tarde.",
		"Todos al campo de batalla! Vamos a participar!",
		"No me pienso perder esta guerra! Alla voy!",
		"Voy a darle duro a los que se crucen!",
		"Escuche que hay facciones peleando, voy a sumarme!",
		"Esto promete! Todos al campo de batalla!",
		"Ya nos juntamos varios para ir a la guerra!",
		"Vamos alla a ver quien tiene mas poder!",
		"La guerra es lo unico que importa ahora!",
		"Todos estan yendo para alla! Vamos!"
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
