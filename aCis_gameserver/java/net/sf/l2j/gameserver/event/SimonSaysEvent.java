package net.sf.l2j.gameserver.event;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.actor.Player;

public class SimonSaysEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(SimonSaysEvent.class.getName());
	
	private static final String[] WORDS =
	{
		"apple", "brave", "crane", "dance", "eagle", "flame", "grape", "heart",
		"ivory", "joker", "knife", "lemon", "magic", "noble", "ocean", "piano",
		"queen", "river", "stone", "tiger", "ultra", "vivid", "whale", "xenon",
		"yacht", "zebra", "blitz", "crisp", "dwarf", "fjord", "glyph", "hymn"
	};
	
	private int _roundTime = 10;
	private String _currentWord = "";
	private boolean _waitingForAnswer;
	private ScheduledFuture<?> _roundTask;
	private final java.util.List<EventPlayer> _eliminated = new java.util.ArrayList<>();
	
	public SimonSaysEvent(EventConfig.EventData data)
	{
		super(data);
	}
	
	@Override
	protected void onStartRegistering()
	{
	}
	
	@Override
	protected void onStartMatch()
	{
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
			{
				ep.getPlayer().setTitle("[Simon] Alive");
				ep.getPlayer().broadcastTitleInfo();
				ep.getPlayer().sendMessage("[Simon] Say exactly what I say as fast as possible!");
			}
		}
		
		startNewRound();
	}
	
	private void startNewRound()
	{
		_waitingForAnswer = true;
		_currentWord = WORDS[Rnd.get(WORDS.length)];
		
		broadcastToPlayers("[Simon] --- NEW ROUND ---");
		broadcastToPlayers("[Simon] Say this word: " + _currentWord);
		broadcastToPlayers("[Simon] Type .simon " + _currentWord + " in chat!");
		
		_roundTask = ThreadPool.schedule(this::endRound, _roundTime * 1000L);
	}
	
	private void endRound()
	{
		if (getState() != State.RUNNING)
			return;
		
		_waitingForAnswer = false;
		
		// Eliminate all who didn't answer
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			
			_eliminated.add(ep);
			p.setIsImmobilized(true);
			p.setIsParalyzed(true);
			p.setTitle("[Simon] Slow!");
			p.broadcastTitleInfo();
			p.sendMessage("[Simon] Too slow! You're eliminated!");
			broadcastToPlayers("[Simon] " + ep.getName() + " was too slow!");
		}
		
		checkRemaining();
	}
	
	// Called when player types .simon <word>
	public boolean onPlayerSay(String word, Player player)
	{
		if (getState() != State.RUNNING || !_waitingForAnswer)
			return false;
		
		final EventPlayer ep = getEventPlayer(player.getObjectId());
		if (ep == null || _eliminated.contains(ep))
			return false;
		
		if (!word.equalsIgnoreCase(_currentWord))
		{
			// Wrong word - eliminate
			_eliminated.add(ep);
			player.setIsImmobilized(true);
			player.setIsParalyzed(true);
			player.setTitle("[Simon] Wrong!");
			player.broadcastTitleInfo();
			player.sendMessage("[Simon] Wrong word! You're eliminated!");
			broadcastToPlayers("[Simon] " + ep.getName() + " said the wrong word!");
			
			checkRemaining();
		}
		else
		{
			// Correct!
			player.sendMessage("[Simon] Correct! You survive this round!");
		}
		
		return true;
	}
	
	private void checkRemaining()
	{
		int alive = 0;
		EventPlayer winner = null;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			alive++;
			winner = ep;
		}
		
		if (alive <= 1 && winner != null)
		{
			cancelTask(_roundTask);
			broadcastToPlayers("[Simon] " + winner.getName() + " wins Simon Says!");
			endMatch();
		}
		else if (alive <= 0)
		{
			cancelTask(_roundTask);
			broadcastToPlayers("[Simon] Everyone is eliminated!");
			endMatch();
		}
		else
		{
			// Start next round
			startNewRound();
		}
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
	}
	
	@Override
	protected void onStop()
	{
		cancelTask(_roundTask);
		_eliminated.clear();
		_waitingForAnswer = false;
	}
	
	@Override
	protected String getScorebar()
	{
		int alive = getAllPlayers().size() - _eliminated.size();
		return "[Simon] Alive: " + alive + " | Round: " + (_eliminated.size() + 1);
	}
	
	public boolean isWaitingForAnswer() { return _waitingForAnswer; }
	public String getCurrentWord() { return _currentWord; }
}
