package net.sf.l2j.gameserver.phantom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.Config;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public final class PhantomSocial
{
	private static final Map<Integer, Long> NEXT_TALK = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> NEXT_ACTION = new ConcurrentHashMap<>();
	
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
		"Por la Argentina. Vamos a demostrarles poder.",
		"Vamos equipo. Es hora de barrer con esos brasileños.",
		"La victoria es nuestra si luchamos juntos.",
		"Ya era hora de otra guerra. Vamos con todo.",
		"Que tiemblen los Brasil, alla vamos.",
		"Voy para el campo de batalla, nos vemos alla.",
		"Al ataque. Por la gloria de la Argentina.",
		"No dejaremos ni uno en pie. Vamos.",
		"Listo para la batalla. Estoy re manija.",
		"Vamos a mostrarles de que estamos hechos.",
		"Nos vemos en el campo, no falten.",
		"Espero estar a la altura de esta batalla.",
		"Voy para alla, no empiecen la fiesta sin mi.",
		"Mejor que esten preparados porque vamos con todo.",
		"Juntos somos imparables. Vamos Argentina.",
		"Al fin llego la guerra. Los esperamos alla.",
		"Vamos muchachos. A defender el honor.",
		"Esto va a ser epico. Alla nos vemos.",
		"Ya estoy en camino. No me dejen atras.",
		"Es hora de pegarle duro a los enemigos."
	};
	
	private static final String[] WAR_LINES_EVIL =
	{
		"Ja. Otra guerra? Vamos a divertirnos un rato.",
		"Llego la hora de sembrar caos y destruccion.",
		"Los Argentina van a caer uno por uno.",
		"Sangre y oscuridad. Vamos Brasil.",
		"Que tiemblen los Argentina.",
		"Nadie nos va a detener esta vez.",
		"Vamos a demostrarles quien manda en esta tierra.",
		"Por la Brasil. Vamos a destrozarlos a todos.",
		"No vamos a dejar a nadie en pie.",
		"El poder oscuro nos guia hacia la victoria.",
		"Vamos. Alla los espero para la batalla.",
		"Yo voy a ser el que decida el destino de la guerra.",
		"Ya van a ver de lo que somos capaces.",
		"La oscuridad se va a apoderar del campo.",
		"Voy alla a pegarle a todo lo que se mueva.",
		"Ya estamos en guerra. Salgan todos.",
		"Esto se pone bueno. Vamos a pelear.",
		"Al fin algo de emocion. Los espero alla.",
		"Que se escondan los buenos. Alla voy.",
		"La oscuridad los va a devorar."
	};
	
	private static final String[] WAR_LINES_NEUTRAL =
	{
		"Escuche que hay guerra de facciones. Voy para alla.",
		"Que emocion. Guerra otra vez. No me lo pierdo.",
		"Hay que unirse a la batalla y ver que pasa.",
		"Van a estar todos peleando, yo no me quedo afuera.",
		"Voy a ver de que trata esta guerra.",
		"Mejor ir a la guerra que estar farmeando toda la tarde.",
		"Todos al campo de batalla. Vamos a participar.",
		"No me pienso perder esta guerra. Alla voy.",
		"Voy a darle duro a los que se crucen.",
		"Escuche que hay facciones peleando, voy a sumarme.",
		"Esto promete. Todos al campo de batalla.",
		"Ya nos juntamos varios para ir a la guerra.",
		"Vamos alla a ver quien tiene mas poder.",
		"La guerra es lo unico que importa ahora.",
		"Todos estan yendo para alla. Vamos."
	};
	
	/** Frases de accion dichas durante la guerra: describen lo que el phantom esta haciendo en el campo de batalla (movimiento, objetivo, apoyo). */
	private static final String[] WAR_ACTION_LINES =
	{
		"Voy a la bandera, cubranme.",
		"Avanzo por el flanco, no me vean.",
		"Veo un checkpoint desprotegido, voy.",
		"Estoy cortando el paso por el centro.",
		"Necesito apoyo, vienen varios.",
		"Retrocedo un toque a curarme.",
		"Tomen la bandera, yo los cubro.",
		"Me muevo al checkpoint del norte.",
		"Estoy protegiendo a un aliado.",
		"Voy a hostigar a los que estan en la flag.",
		"Cambio de posicion, me flanquean.",
		"Aguantemos en la zona, que llegue el resto.",
		"Voy a intentar capturar el checkpoint.",
		"Estoy de avanzada, aviso si viene alguien.",
		"Me replego a la base un momento.",
		"Vamos todos juntos a la bandera.",
		"Salgo a buscar al enemigo al medio.",
		"Estoy viendo quien se acerca por atras.",
		"Defiendo este punto, no lo suelto.",
		"Me aparto para curar y vuelvo."
	};
	
	private static final String[] BOSS_LINES =
	{
		"Escuche que aparecio un boss. Voy para alla.",
		"Dicen que hay un Grand Boss activo. No me lo pierdo.",
		"Vamos a ver si entre todos tumbamos al boss.",
		"Hay que armar party y ir a matar al boss.",
		"El boss esta online. Todos al lair.",
		"Voy a ver si sale algo bueno del boss.",
		"Con un grupo se puede. Vamos por el boss.",
		"Alguien se apunta a ir contra el Grand Boss?",
		"El boss no se va a matar solo. Vamos.",
		"Dicen que el drop del boss es buenazo. Alla voy.",
		"Hay que aprovechar que el boss esta despierto.",
		"Voy al lair, seguro hay pelea buena.",
		"El boss no dura nada con todos atacandolo.",
		"Me avisaron que el boss esta. Salgo para alla.",
		"Otra vez el boss dando vueltas. Vamos a cobrarlo."
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
		
		// Only selected war participants say war phrases; the rest keep normal social chat.
		final boolean warRunning = PhantomEngine.canJoinWar(phantom);
		
		final Player nearby = findNearbyRealPlayer(phantom, warRunning);
		if (nearby == null)
			return;
		
		if (warRunning)
		{
			final String[] lines;
			if (phantom.getFactionId() == FactionWarConfig.getGoodFactionId())
				lines = WAR_LINES_GOOD;
			else if (phantom.getFactionId() == FactionWarConfig.getEvilFactionId())
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
		if (phantom.getFactionId() == FactionWarConfig.getGoodFactionId())
			lines = WAR_LINES_GOOD;
		else if (phantom.getFactionId() == FactionWarConfig.getEvilFactionId())
			lines = WAR_LINES_EVIL;
		else
			lines = WAR_LINES_NEUTRAL;
		
		say(phantom, lines[Rnd.get(lines.length)]);
	}
	
	/**
	 * Says a war action phrase describing what the phantom is currently doing on the
	 * battlefield (moving to a flag, covering an ally, retreating to heal, etc.).
	 * Uses its own cooldown so it does not collide with the regular social chat timer.
	 * @param phantom : The phantom to speak.
	 */
	public static void sayWarAction(Player phantom)
	{
		if (phantom == null || phantom.isDead() || !PhantomConfig.socialChatEnabled() || phantom.getCast().isCastingNow())
			return;
		
		final long now = System.currentTimeMillis();
		final long next = NEXT_ACTION.getOrDefault(phantom.getObjectId(), 0L);
		if (now < next)
			return;
		
		NEXT_ACTION.put(phantom.getObjectId(), now + Rnd.get(20000, 60000));
		say(phantom, WAR_ACTION_LINES[Rnd.get(WAR_ACTION_LINES.length)]);
	}
	
	/**
	 * Says a random Grand Boss hunting phrase.
	 * @param phantom : The phantom to speak.
	 * @param bossNpcId : The hunted Grand Boss npcId (unused, kept for future per-boss lines).
	 */
	public static void sayBossPhrase(Player phantom, int bossNpcId)
	{
		if (phantom == null || phantom.isDead())
			return;
		
		say(phantom, BOSS_LINES[Rnd.get(BOSS_LINES.length)]);
	}
	
	public static void forget(int objectId)
	{
		NEXT_TALK.remove(objectId);
		NEXT_ACTION.remove(objectId);
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
