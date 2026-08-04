package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.data.xml.RestartPointData;
import net.sf.l2j.gameserver.enums.RestartType;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.enums.actors.OperateType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.phantom.PhantomAI;
import net.sf.l2j.gameserver.phantom.PhantomConfig;
import net.sf.l2j.gameserver.phantom.PhantomEngine;
import net.sf.l2j.gameserver.phantom.PhantomFactory;
import net.sf.l2j.gameserver.phantom.PhantomState;

public class AdminPhantom implements IAdminCommandHandler
{
	private static final int PAGE_SIZE = 4;
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_phantom",
		"admin_phantom_reload",
		"admin_phantom_start",
		"admin_phantom_load",
		"admin_phantom_create",
		"admin_phantom_ai",
		"admin_phantom_stop",
		"admin_phantom_kill",
		"admin_phantom_delete",
		"admin_phantom_bring",
		"admin_phantom_bringfaction",
		"admin_phantom_resurrect",
		"admin_phantom_resurrectfaction",
		"admin_phantom_heal",
		"admin_phantom_healfaction",
		"admin_phantom_deleteall",
		"admin_phantom_online",
		"admin_phantom_status",
		"admin_phantom_radar",
		"admin_phantom_faction",
		"admin_phantom_factions",
		"admin_phantom_party",
		"admin_phantom_store",
		"admin_phantom_factionall",
		"admin_phantom_warinfo",
		"admin_phantom_warstat",
		"admin_phantom_warstart",
		"admin_phantom_warstop",
		"admin_phantom_warforcejoin",
		"admin_phantom_warforceleave",
		"admin_phantom_warteleportin",
		"admin_phantom_warteleportout",
		"admin_phantom_emergencyrecall",
		"admin_phantom_emergencyheal",
		"admin_phantom_emergencyrespawn"
	};

	@Override
	public void useAdminCommand(String command, Player player)
	{
		final StringTokenizer st = new StringTokenizer(command);
		String cmd = st.nextToken();

		if (cmd.equals("admin_phantom") && st.hasMoreTokens())
			cmd = "admin_phantom_" + st.nextToken().toLowerCase();

		if (cmd.equals("admin_phantom") || cmd.equals("admin_phantom_status"))
		{
			showPanel(player, null);
			return;
		}

		if (cmd.equals("admin_phantom_online"))
		{
			showOnline(player, parsePage(st), 0, null);
			return;
		}

		if (cmd.equals("admin_phantom_factions"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final int factionId = parseCount(st, 0);
			final int page = parsePage(st);
			showOnline(player, page, factionId, null);
			return;
		}

		if (cmd.equals("admin_phantom_reload"))
		{
			PhantomConfig.load();
			showPanel(player, "Config reloaded. IDs: " + PhantomConfig.getPhantomIds().size());
			return;
		}

		if (cmd.equals("admin_phantom_start"))
		{
			PhantomEngine.startConfigured(player);
			showPanel(player, "Loading phantoms in background...");
			return;
		}

		if (cmd.equals("admin_phantom_create"))
		{
			final int count = parseCount(st, 1);
			int loaded = 0;
			for (Player phantom : PhantomFactory.create(count))
			{
				if (PhantomEngine.load(phantom.getObjectId(), player, true) != null)
					loaded++;
			}
			showPanel(player, "Created NEW phantoms: " + loaded + ".");
			return;
		}

		if (cmd.equals("admin_phantom_bring"))
		{
			// Per-phantom bring from the list: admin_phantom bring <page> <filterFaction> <objectId>.
			if (st.countTokens() >= 3)
			{
				final int page = parsePage(st);
				final int filterFaction = parseCount(st, 0);
				final int objectId = parseObjectId(st, player, page, filterFaction);
				if (objectId > 0)
					showOnline(player, page, filterFaction, PhantomEngine.bring(player, objectId) ? "Brought phantom " + objectId + "." : "Phantom " + objectId + " isn't active.");
				return;
			}
			showPanel(player, "Brought phantoms: " + PhantomEngine.bringAll(player) + ".");
			return;
		}

		if (cmd.equals("admin_phantom_bringfaction"))
		{
			final int factionId = parseCount(st, 0);
			final String label = (factionId > 0) ? ("Faction " + factionId) : "all";
			showPanel(player, "Brought " + label + " phantoms: " + PhantomEngine.bringFaction(player, factionId) + ".");
			return;
		}

		if (cmd.equals("admin_phantom_resurrect"))
		{
			// Per-phantom revive from the list: admin_phantom resurrect <page> <filterFaction> <objectId>.
			if (st.countTokens() >= 3)
			{
				final int page = parsePage(st);
				final int filterFaction = parseCount(st, 0);
				final int objectId = parseObjectId(st, player, page, filterFaction);
				if (objectId > 0)
					showOnline(player, page, filterFaction, PhantomEngine.resurrect(objectId) ? "Resurrected phantom " + objectId + "." : "Phantom " + objectId + " isn't dead or isn't active.");
				return;
			}

			// No args: revive the targeted phantom, otherwise everyone.
			final WorldObject target = player.getTarget();
			if (target instanceof Player tp && PhantomEngine.isPhantom(tp.getObjectId()))
			{
				showPanel(player, PhantomEngine.resurrect(tp.getObjectId()) ? "Resurrected " + tp.getName() + "." : tp.getName() + " is not dead.");
				return;
			}
			showPanel(player, "Resurrected phantoms: " + PhantomEngine.resurrectAll() + ".");
			return;
		}

		if (cmd.equals("admin_phantom_resurrectfaction"))
		{
			final int factionId = parseCount(st, 0);
			final String label = (factionId > 0) ? ("Faction " + factionId) : "all";
			showPanel(player, "Resurrected " + label + " phantoms: " + PhantomEngine.resurrectFaction(factionId) + ".");
			return;
		}

		if (cmd.equals("admin_phantom_heal"))
		{
			// Per-phantom heal from the list: admin_phantom heal <page> <filterFaction> <objectId>.
			if (st.countTokens() >= 3)
			{
				final int page = parsePage(st);
				final int filterFaction = parseCount(st, 0);
				final int objectId = parseObjectId(st, player, page, filterFaction);
				if (objectId > 0)
					showOnline(player, page, filterFaction, PhantomEngine.heal(objectId) ? "Healed phantom " + objectId + "." : "Phantom " + objectId + " isn't active.");
				return;
			}

			// No args: heal the targeted phantom, otherwise everyone.
			final WorldObject target = player.getTarget();
			if (target instanceof Player tp && PhantomEngine.isPhantom(tp.getObjectId()))
			{
				showPanel(player, PhantomEngine.heal(tp.getObjectId()) ? "Healed " + tp.getName() + "." : tp.getName() + " is not healable.");
				return;
			}
			showPanel(player, "Healed phantoms: " + PhantomEngine.healFaction(0) + ".");
			return;
		}

		if (cmd.equals("admin_phantom_healfaction"))
		{
			final int factionId = parseCount(st, 0);
			final String label = (factionId > 0) ? ("Faction " + factionId) : "all";
			showPanel(player, "Healed " + label + " phantoms: " + PhantomEngine.healFaction(factionId) + ".");
			return;
		}

		if (cmd.equals("admin_phantom_deleteall"))
		{
			final int deleted = PhantomEngine.deleteAll();
			showPanel(player, "Deleted ALL phantoms: " + deleted + ".");
			return;
		}

		if (cmd.equals("admin_phantom_ai"))
		{
			if (!st.hasMoreTokens())
			{
				showPanel(player, "Usage: AI on/home");
				return;
			}

			final String mode = st.nextToken().toLowerCase();				switch (mode)
			{
				case "on" -> showPanel(player, "AI started for: " + PhantomEngine.startAi());
				case "home" -> showPanel(player, "AI home updated for: " + PhantomEngine.setHomes());
				default -> showPanel(player, "Usage: AI on/home");
			}
			return;
		}

		if (cmd.equals("admin_phantom_radar"))
		{
			markRadar(player, st.hasMoreTokens() ? st.nextToken().toLowerCase() : "phantoms");
			return;
		}

		if (cmd.equals("admin_phantom_load"))
		{
			if (!st.hasMoreTokens())
			{
				showPanel(player, "Usage: load objectId [here|db]");
				return;
			}

			final int objectId = parseObjectId(st, player, 0, 0);
			if (objectId <= 0)
				return;

			boolean spawnHere = true;
			if (st.hasMoreTokens())
				spawnHere = !st.nextToken().equalsIgnoreCase("db");

			final Player phantom = PhantomEngine.load(objectId, player, spawnHere);
			showPanel(player, (phantom == null) ? "Couldn't load phantom " + objectId + "." : "Loaded phantom " + phantom.getName() + ".");
			return;
		}

		if (cmd.equals("admin_phantom_kill"))
		{
			final int page = parsePage(st);
			final int filterFaction = parseCount(st, 0);
			final int objectId = parseObjectId(st, player, page, filterFaction);
			if (objectId > 0)
				showOnline(player, page, filterFaction, PhantomEngine.kill(objectId) ? "Killed phantom " + objectId + "." : "Phantom " + objectId + " isn't active.");
			return;
		}

		if (cmd.equals("admin_phantom_delete"))
		{
			final int page = parsePage(st);
			final int filterFaction = parseCount(st, 0);
			final int objectId = parseObjectId(st, player, page, filterFaction);
			if (objectId > 0)
				showOnline(player, page, filterFaction, PhantomEngine.deleteConfigured(objectId) ? "Deleted phantom " + objectId + "." : "Phantom " + objectId + " not found.");
			return;
		}

		if (cmd.equals("admin_phantom_stop"))
		{
			if (st.hasMoreTokens())
			{
				final int page = parsePage(st);
				final int filterFaction = parseCount(st, 0);
				final int objectId = parseObjectId(st, player, page, filterFaction);
				if (objectId > 0)
					showOnline(player, page, filterFaction, PhantomEngine.stop(objectId) ? "Stopped phantom " + objectId + "." : "Phantom " + objectId + " isn't active.");
				return;
			}

			showPanel(player, "Stopped phantoms: " + PhantomEngine.stopAll() + ".");
			return;
		}

		if (cmd.equals("admin_phantom_faction"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final int page = parsePage(st);
			final int filterFaction = parseCount(st, 0);
			final int objectId = parseObjectId(st, player, page, filterFaction);
			if (objectId <= 0)
				return;

			if (!st.hasMoreTokens())
			{
				showOnline(player, page, filterFaction, "Usage: faction " + page + " " + filterFaction + " " + objectId + " <factionId|0>");
				return;
			}

			final int factionId;
			try
			{
				factionId = Integer.parseInt(st.nextToken());
			}
			catch (NumberFormatException e)
			{
				showOnline(player, page, filterFaction, "Invalid factionId.");
				return;
			}

			final Player phantom = PhantomEngine.getActivePhantom(objectId);
			if (phantom == null)
			{
				showOnline(player, page, filterFaction, "Phantom " + objectId + " is not active.");
				return;
			}

			if (factionId == 0)
			{
				phantom.setFactionId(0);
				FactionData.getInstance().removeData(phantom);
				showOnline(player, page, filterFaction, phantom.getName() + " removed from faction.");
			}
			else
			{
				final Faction faction = FactionData.getInstance().getFaction(factionId);
				if (faction == null)
				{
					showOnline(player, page, filterFaction, "Faction " + factionId + " not found in faction.xml.");
					return;
				}
				phantom.setFactionId(factionId);
				FactionData.getInstance().storeData(phantom);
				showOnline(player, page, filterFaction, phantom.getName() + " -> " + faction.getName() + ".");
			}
			return;
		}

		if (cmd.equals("admin_phantom_party"))
		{
			PhantomAI.ensureFactionParties();
			final java.util.Map<Integer, Integer> partyCount = new java.util.HashMap<>();
			int partied = 0;
			for (Player p : PhantomEngine.getActivePhantoms())
			{
				if (p != null && p.getParty() != null)
				{
					final int leaderId = p.getParty().getLeaderObjectId();
					partyCount.merge(leaderId, 1, Integer::sum);
					partied++;
				}
			}
			showPanel(player, "Parties formed. " + partied + " phantoms in " + partyCount.size() + " parties.");
			return;
		}

		if (cmd.equals("admin_phantom_store"))
		{
			int opened = 0;
			int closed = 0;
			for (Player p : PhantomEngine.getActivePhantoms())
			{
				if (p == null || p.isDead())
					continue;

				if (p.getOperateType() == OperateType.NONE)
				{
					p.sitDown();
					p.setOperateType(OperateType.SELL);
					p.broadcastUserInfo();
					opened++;
				}
				else
				{
					p.setOperateType(OperateType.NONE);
					p.standUp();
					p.broadcastUserInfo();
					closed++;
				}
			}
			showPanel(player, "Store toggle: " + opened + " opened, " + closed + " closed.");
			return;
		}

		if (cmd.equals("admin_phantom_factionall"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final int factionId;
			try
			{
				factionId = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				showPanel(player, "Usage: factionall <factionId> - assigns ALL phantoms without faction to the given faction.");
				return;
			}

			final Faction faction = FactionData.getInstance().getFaction(factionId);
			if (faction == null)
			{
				showPanel(player, "Faction " + factionId + " not found.");
				return;
			}

			int assigned = 0;
			for (Player p : PhantomEngine.getActivePhantoms())
			{
				if (p != null && p.getFactionId() <= 0)
				{
					p.setFactionId(factionId);
					FactionData.getInstance().storeData(p);
					assigned++;
				}
			}
			showPanel(player, "Assigned " + assigned + " phantoms to " + faction.getName() + ".");
			return;
		}

		// Wartime Commands
		if (cmd.equals("admin_phantom_warinfo"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final FactionWarManager fwm = FactionWarManager.getInstance();
			final boolean running = fwm.isRunning();
			final String status = running ? "EN CURSO" : "DETENIDA";
			final String voteStatus = fwm.isVotingPhaseActive() ? "EN VOTACION" : "SIN VOTACION";

			StringBuilder sb = new StringBuilder();
			sb.append("<html><body>");
			sb.append("<center><table width=200><tr><td><font color=LEVEL>Estado Faction War:</font></td></tr>");
			sb.append("<tr><td>Estado: <font color=").append(running ? "00FF00" : "FF0000").append(">").append(status).append("</font></td></tr>");
			sb.append("<tr><td>Votacion: <font color=").append(fwm.isVotingPhaseActive() ? "00FF00" : "FF0000").append(">").append(voteStatus).append("</font></td></tr>");
			sb.append("<tr><td>Mapa: ").append(fwm.getCurrentMapIndex() + 1).append("/").append(FactionWarConfig.getMaps().size()).append("</td></tr>");
			sb.append("<tr><td>Duracion: ").append(FactionWarConfig.getWarDurationMinutes()).append(" min</td></tr>");
			sb.append("<tr><td>Puntos - Furious: ").append(fwm.getScore(FactionWarConfig.getGoodFactionId())).append("</td></tr>");
			sb.append("<tr><td>Puntos - Fast: ").append(fwm.getScore(FactionWarConfig.getEvilFactionId())).append("</td></tr>");
			sb.append("<tr><td>Ganadora: Faccion ").append(fwm.getWinningFaction()).append("</td></tr>");
			sb.append("</table></center>");
			sb.append("<br><center><button value=\"Actualizar\" action=\"bypass -h admin_phantom_warinfo\" width=80 height=20></center>");
			sb.append("</body></html>");

			sendHtml(player, sb);
			return;
		}

		if (cmd.equals("admin_phantom_warstat"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final FactionWarManager fwm = FactionWarManager.getInstance();
			final List<FactionWarManager.FactionWarStats> topPlayers = fwm.getTopPlayers(5);
			final Map<Integer, FactionWarManager.FactionWarStats> allStats = fwm.getAllPlayerStats();

			StringBuilder sb = new StringBuilder();
			sb.append("<html><body>");
			sb.append("<center><table width=250><tr><td><font color=LEVEL>Top 5 Jugadores Faction War:</font></td></tr></table></center>");

			if (topPlayers.isEmpty())
			{
				sb.append("<center>No hay estadisticas disponibles</center>");
			}
			else
			{
				sb.append("<center><table width=250>");
				for (int i = 0; i < topPlayers.size(); i++)
				{
					final FactionWarManager.FactionWarStats stats = topPlayers.get(i);
					sb.append("<tr><td>").append(i + 1).append(". ").append(htmlSafe(stats.playerName));
					sb.append(" (Fac ").append(stats.factionId).append("): ");
					sb.append(stats.points).append(" pts (").append(stats.kills).append(" kills, ").append(stats.deaths).append(" deaths)</td></tr>");
				}
				sb.append("</table></center>");
			}

			sb.append("<br><center><button value=\"Actualizar\" action=\"bypass -h admin_phantom_warstat\" width=80 height=20></center>");
			sb.append("</body></html>");

			sendHtml(player, sb);
			return;
		}

		if (cmd.equals("admin_phantom_warstart"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final FactionWarManager fwm = FactionWarManager.getInstance();
			if (!fwm.isVotingPhaseActive())
			{
				fwm.startVotePhase();
				showPanel(player, "Faction War votacion iniciada.");
			}
			else
			{
				showPanel(player, "La votacion ya esta en curso.");
			}
			return;
		}

		if (cmd.equals("admin_phantom_warstop"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final FactionWarManager fwm = FactionWarManager.getInstance();
			if (fwm.isRunning())
			{
				fwm.stop();
				showPanel(player, "Faction War detenida.");
			}
			else
			{
				showPanel(player, "No hay guerra en curso.");
			}
			return;
		}

		if (cmd.equals("admin_phantom_warforcejoin"))
		{
			if (!st.hasMoreTokens())
			{
				showPanel(player, "Usage: warforcejoin <factionId|1|2>");
				return;
			}

			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final int factionId;
			try
			{
				factionId = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				showPanel(player, "Usage: warforcejoin <factionId|1|2>");
				return;
			}

			if (factionId != FactionWarConfig.getGoodFactionId() && factionId != FactionWarConfig.getEvilFactionId())
			{
				showPanel(player, "Faction ID invalido. Use 1 (Furious) o 2 (Fast).");
				return;
			}

			final FactionWarManager fwm = FactionWarManager.getInstance();
			final boolean warRunning = fwm.isRunning();
			int joined = 0;
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom != null && phantom.getFactionId() == 0) // Only unaffiliated phantoms
				{
					phantom.setFactionId(factionId);
					FactionData.getInstance().storeData(phantom);
					
					// If a war is currently running, register the phantom as a participant right
					// away: selectWarParticipants() already ran when the war started, so without
					// this the newly-factioned phantom would never join (no flags, no combat,
					// respawn back to town on death).
					if (warRunning)
						PhantomEngine.addWarParticipant(phantom.getObjectId());
					
					joined++;
				}
			}
			showPanel(player, "Forzado " + joined + " phantoms sin faccion a unirse a la faccion " + factionId + (warRunning ? " (participantes de guerra)" : ".") + ".");
			return;
		}

		if (cmd.equals("admin_phantom_warforceleave"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			int removed = 0;
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom != null && phantom.getFactionId() > 0)
				{
					phantom.setFactionId(0);
					FactionData.getInstance().removeData(phantom);
					
					// Mirror warforcejoin: drop the phantom from the war roster so its AI stops
					// treating it as a war participant.
					PhantomEngine.removeWarParticipant(phantom.getObjectId());
					removed++;
				}
			}
			showPanel(player, "Removido " + removed + " phantoms de sus facciones.");
			return;
		}

		if (cmd.equals("admin_phantom_warteleportin"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			final FactionWarManager fwm = FactionWarManager.getInstance();
			if (!fwm.isRunning())
			{
				showPanel(player, "No hay guerra en curso para teletransportar.");
				return;
			}

			int teleported = 0;
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom != null && phantom.getFactionId() > 0 && !phantom.isDead())
				{
					final Location spawn = fwm.getFactionSpawn(phantom.getFactionId());
					if (spawn != null)
					{
						// Register the phantom as a war participant so its AI actually treats it
						// as being in the war (search flags, attack enemies, respawn back on the
						// war map). Otherwise it would stand on the war map without any objective.
						PhantomEngine.addWarParticipant(phantom.getObjectId());
						
						final int rx = spawn.getX() + Rnd.get(-100, 100);
						final int ry = spawn.getY() + Rnd.get(-100, 100);
						phantom.teleportTo(rx, ry, spawn.getZ(), 0);
						teleported++;
					}
				}
			}
			showPanel(player, "Teletransportado " + teleported + " phantoms de faccion a sus spawns de guerra.");
			return;
		}

		if (cmd.equals("admin_phantom_warteleportout"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			int teleported = 0;
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom != null && phantom.getFactionId() > 0 && !phantom.isDead())
				{
					final Location neutral = FactionWarConfig.getNeutralSpawnLoc();
					final int rx = neutral.getX() + Rnd.get(-100, 100);
					final int ry = neutral.getY() + Rnd.get(-100, 100);
					phantom.teleportTo(rx, ry, neutral.getZ(), 0);
					teleported++;
				}
			}
			showPanel(player, "Teletransportado " + teleported + " phantoms de faccion a zona neutral.");
			return;
		}

		if (cmd.equals("admin_phantom_emergencyrecall"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}

			int recalled = 0;
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom != null && !phantom.isDead())
				{
					final Location nearestTown = RestartPointData.getInstance().getLocationToTeleport(phantom, RestartType.TOWN);
					if (nearestTown != null)
					{
						final int rx = nearestTown.getX() + Rnd.get(-100, 100);
						final int ry = nearestTown.getY() + Rnd.get(-100, 100);
						phantom.teleportTo(rx, ry, nearestTown.getZ(), 0);
						recalled++;
					}
				}
			}
			showPanel(player, "Recall de emergencia: " + recalled + " phantoms teletransportados a la ciudad mas cercana.");
			return;
		}

		if (cmd.equals("admin_phantom_emergencyheal"))
		{
			int healed = 0;
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom != null && phantom.isDead())
				{
					if (PhantomEngine.resurrect(phantom.getObjectId()))
					{
						healed++;
					}
				}
				else if (phantom != null && phantom.getStatus().getHp() < phantom.getStatus().getMaxHp())
				{
					phantom.getStatus().setHp(phantom.getStatus().getMaxHp());
					phantom.getStatus().setMp(phantom.getStatus().getMaxMp());
					phantom.broadcastUserInfo();
					healed++;
				}
			}
			showPanel(player, "Curacion de emergencia: " + healed + " phantoms curados o resucitados.");
			return;
		}

		if (cmd.equals("admin_phantom_emergencyrespawn"))
		{
			int respawned = 0;
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom != null && phantom.isDead())
				{
					if (PhantomEngine.resurrect(phantom.getObjectId()))
					{
						respawned++;
					}
				}
			}
			showPanel(player, "Respawn de emergencia: " + respawned + " phantoms resucitados.");
			return;
		}
	}

	private static List<Player> filterByFaction(List<Player> phantoms, int factionId)
	{
		if (factionId <= 0)
			return phantoms;

		final List<Player> filtered = new ArrayList<>();
		for (Player p : phantoms)
		{
			if (p != null && p.getFactionId() == factionId)
				filtered.add(p);
		}
		return filtered;
	}

	private static int parsePage(StringTokenizer st)
	{
		if (!st.hasMoreTokens())
			return 0;

		try
		{
			return Math.max(0, Integer.parseInt(st.nextToken()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static int parseObjectId(StringTokenizer st, Player player, int page, int filterFaction)
	{
		if (!st.hasMoreTokens())
		{
			showOnline(player, page, filterFaction, "Missing objectId.");
			return 0;
		}

		try
		{
			return Integer.parseInt(st.nextToken());
		}
		catch (NumberFormatException e)
		{
			showOnline(player, page, filterFaction, "Invalid objectId.");
			return 0;
		}
	}

	private static int parseCount(StringTokenizer st, int defaultValue)
	{
		if (!st.hasMoreTokens())
			return defaultValue;

		try
		{
			return Math.max(0, Math.min(50, Integer.parseInt(st.nextToken())));
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}

	private static void markRadar(Player player, String mode)
	{
		if (mode.equals("clear"))
		{
			player.getRadarList().removeAllMarkers();
			showPanel(player, "Radar cleared.");
			return;
		}

		player.getRadarList().removeAllMarkers();
		int markers = 0;
		for (Player phantom : PhantomEngine.getActivePhantoms())
		{
			if (phantom == null)
				continue;

			final Location loc = mode.equals("targets") ? PhantomAI.getLastTarget(phantom) : phantom.getPosition();
			if (loc != null)
			{
				player.getRadarList().addMarker(loc);
				markers++;
					}
		}
		showPanel(player, "Radar markers: " + markers + " (" + mode + ").");
	}

	private static void showPanel(Player player, String message)
	{
		final List<Player> allPhantoms = PhantomEngine.getActivePhantomsSorted();
		final boolean warEnabled = FactionWarConfig.isEnabled();
		final boolean warRunning = warEnabled && FactionWarManager.getInstance().isRunning();
		final FactionWarManager fwm = FactionWarManager.getInstance();
		final StringBuilder sb = new StringBuilder(4096);

		// IMPORTANT: flat HTML only. Nested tables (table inside td inside table) crash the
		// L2 client with "Insufficient Memory" (NCHtmlTable::CreateFrame recursion).
		sb.append("<html><body><center><font color=FFD700 size=2><b>PHANTOM MANAGER</b></font></center>");
		if (message != null && !message.isEmpty())
			sb.append("<center><font color=00FF00>").append(htmlSafe(message)).append("</font></center>");
		sb.append("<br1>");

		// --- Faction War Status ---
		if (warEnabled)
		{
			sb.append("<table width=350><tr><td bgcolor=000080 align=center><font color=FFFFFF><b>Faction War</b></font></td></tr></table>");
			sb.append("<table width=350>");
			sb.append("<tr><td width=110>Estado:</td><td width=240><font color=").append(warRunning ? "00FF00" : "FF0000").append("><b>").append(warRunning ? "EN CURSO" : "DETENIDA").append("</b></font></td></tr>");
			sb.append("<tr><td>Mapa:</td><td>").append(fwm.getCurrentMapIndex() + 1).append("/").append(FactionWarConfig.getMaps().size()).append("</td></tr>");
			sb.append("<tr><td>Duracion:</td><td>").append(FactionWarConfig.getWarDurationMinutes()).append(" min</td></tr>");
			if (warRunning)
			{
				sb.append("<tr><td>Tiempo:</td><td><font color=FFA500><b>").append(fwm.getRemainingTimeStr()).append("</b></font></td></tr>");
				sb.append("<tr><td>Puntos " + FactionWarConfig.getGoodFactionName() + ":</td><td><font color=00BFFF><b>").append(fwm.getScore(FactionWarConfig.getGoodFactionId())).append("</b></font></td></tr>");
				sb.append("<tr><td>Puntos " + FactionWarConfig.getEvilFactionName() + ":</td><td><font color=FF4444><b>").append(fwm.getScore(FactionWarConfig.getEvilFactionId())).append("</b></font></td></tr>");
				sb.append("<tr><td>Ganadora:</td><td><font color=FFD700><b>Fac ").append(fwm.getWinningFaction()).append("</b></font></td></tr>");
			}
			sb.append("<tr>");
			button(sb, "Iniciar Votacion", "admin_factionwar start", 100);
			button(sb, "Detener", "admin_factionwar stop", 80);
			button(sb, "Panel Guerra", "admin_factionwar", 80);
			sb.append("</tr>");
			sb.append("</table><br1>");
		}

		// --- General Stats ---
		sb.append("<table width=350><tr><td bgcolor=000080 align=center><font color=FFFFFF><b>Estadisticas</b></font></td></tr></table>");
		sb.append("<table width=350>");
		infoRow(sb, "Phantoms activos", allPhantoms.size());			sb.append("<tr><td width=140>IA del Sistema:</td><td width=210><font color=00FF00><b>ON (siempre activa)</b></font></td></tr>");
		if (warEnabled)
			infoRow(sb, "Participacion Guerra", PhantomConfig.warParticipationChance() + "% max " + PhantomConfig.warMaxPerFaction() + "/fac");
		sb.append("</table><br1>");

		// --- Phantom Creation ---
		sb.append("<table width=350><tr><td bgcolor=000080 align=center><font color=FFFFFF><b>Creacion</b></font></td></tr></table>");
		sb.append("<table width=350><tr><td>Cant: <edit var=\"createCount\" width=50 height=16 type=number></td>");
		button(sb, "Crear", "admin_phantom create $createCount", 60);
		sb.append("</tr>");
		sb.append("<tr>");
		button(sb, "x1", "admin_phantom create 1", 45);
		button(sb, "x5", "admin_phantom create 5", 45);
		button(sb, "x10", "admin_phantom create 10", 45);
		button(sb, "x20", "admin_phantom create 20", 45);
		button(sb, "x50", "admin_phantom create 50", 45);
		button(sb, "x100", "admin_phantom create 100", 45);
		sb.append("</tr></table><br1>");

		// --- Phantom Control ---
		sb.append("<table width=350><tr><td bgcolor=000080 align=center><font color=FFFFFF><b>Control</b></font></td></tr></table>");
		sb.append("<table width=350>");
		sb.append("<tr>");
		button(sb, "Traer Todos", "admin_phantom bring", 85);
		button(sb, "Curar Todos", "admin_phantom heal", 85);
		button(sb, "Revivir Todos", "admin_phantom resurrect", 85);
		button(sb, "Detener Todos", "admin_phantom stop", 85);
		sb.append("</tr>");
		sb.append("<tr>");
		button(sb, "Formar Party", "admin_phantom party", 85);
		button(sb, "Lista Online", "admin_phantom online 0", 85);
		button(sb, "Borrar Todos", "admin_phantom deleteall", 85);
		sb.append("</tr>");
		sb.append("</table><br1>");

		// --- Per-Faction Controls ---
		if (warEnabled && FactionData.getInstance().getFactionCount() > 0)
		{
			sb.append("<table width=350><tr><td bgcolor=000080 align=center><font color=FFFFFF><b>Control por Faccion</b></font></td></tr></table>");
			sb.append("<table width=350>");
			for (int fid : FactionData.getInstance().getFactionIds())
			{
				final Faction f = FactionData.getInstance().getFaction(fid);
				final String label = (f != null) ? f.getName() : ("F" + fid);
				final String safeLabel = htmlSafe(shortText(label, 8));
				sb.append("<tr><td width=70><font color=FFD700><b>").append(safeLabel).append(":</b></font></td>");
				button(sb, "Traer", "admin_phantom bringfaction " + fid, 50);
				button(sb, "Revivir", "admin_phantom resurrectfaction " + fid, 55);
				button(sb, "Curar", "admin_phantom healfaction " + fid, 50);
				button(sb, "Ver", "admin_phantom factions " + fid + " 0", 45);
				sb.append("</tr>");
			}
			sb.append("</table><br1>");
		}

		// --- Help ---
		sb.append("<table width=350><tr><td bgcolor=000080 align=center><font color=FFFFFF><b>Ayuda</b></font></td></tr></table>");
		sb.append("<table width=350><tr><td>");
		sb.append("<font color=808080>Los Phantoms no ejecutan comandos de admin.<br1>");
		sb.append("Con un phantom como target, Revivir y Curar actuan sobre el.<br1>");
		sb.append("Usa Lista Online o Ver por Faccion para editar individuales.</font>");
		sb.append("</td></tr></table>");

		sb.append("</body></html>");
		sendHtml(player, sb);
	}

	private static void showOnline(Player player, int page, int filterFaction, String message)
	{
		List<Player> phantoms = PhantomEngine.getActivePhantomsSorted();
		final String title;
		if (filterFaction > 0)
		{
			phantoms = filterByFaction(phantoms, filterFaction);
			final Faction f = FactionData.getInstance().getFaction(filterFaction);
			title = "Phantoms - Faccion " + filterFaction + (f != null ? " (" + htmlSafe(f.getName()) + ")" : "");
		}
		else
		{
			title = "Phantoms en linea";
		}

		final int maxPage = phantoms.isEmpty() ? 0 : (phantoms.size() - 1) / PAGE_SIZE;
		page = Math.max(0, Math.min(page, maxPage));

		final StringBuilder sb = new StringBuilder(8192);
		sb.append("<html><body><center><font color=LEVEL>").append(title).append("</font></center><br>");
		if (message != null && !message.isEmpty())
			sb.append("<font color=99FF99>").append(htmlSafe(message)).append("</font><br1>");

		sb.append("Total: <font color=LEVEL>").append(phantoms.size()).append("</font> | Pagina: ").append(page + 1).append("/").append(maxPage + 1).append("<br1>");
		sb.append("<table width=310>");

		final int start = page * PAGE_SIZE;
		final int end = Math.min(start + PAGE_SIZE, phantoms.size());
		for (int i = start; i < end; i++)
		{
			final Player phantom = phantoms.get(i);
			final int objectId = phantom.getObjectId();
			final int fId = phantom.getFactionId();
			final String factionTag = (FactionWarConfig.isEnabled() && fId > 0) ? String.valueOf(fId) : "-";
			sb.append("<tr><td><font color=FFD700>").append(htmlSafe(shortText(phantom.getName(), 14))).append("</font> | Nv").append(phantom.getStatus().getLevel()).append(" | Fac ").append(factionTag).append(" | ").append(htmlSafe(shortText(PhantomState.label(objectId), 6))).append(" | ").append(htmlSafe(shortText(PhantomAI.getLastAction(phantom), 10))).append("</td></tr>");
			sb.append("<tr><td>");
			miniButton(sb, "Traer", "admin_phantom bring " + page + " " + filterFaction + " " + objectId, 44);
			miniButton(sb, "Rev", "admin_phantom resurrect " + page + " " + filterFaction + " " + objectId, 36);
			miniButton(sb, "Cur", "admin_phantom heal " + page + " " + filterFaction + " " + objectId, 36);
			miniButton(sb, "K", "admin_phantom kill " + page + " " + filterFaction + " " + objectId, 22);
			miniButton(sb, "S", "admin_phantom stop " + page + " " + filterFaction + " " + objectId, 22);
			miniButton(sb, "X", "admin_phantom delete " + page + " " + filterFaction + " " + objectId, 22);
			if (FactionWarConfig.isEnabled())
			{
				final int nextFaction = getNextFactionId(fId);
				miniButton(sb, "Fac" + nextFaction, "admin_phantom faction " + page + " " + filterFaction + " " + objectId + " " + nextFaction, 36);
			}
			sb.append("</td></tr>");
		}
		sb.append("</table>");
		sb.append("<center><font color=808080>Traer=teleport | Rev=revivir | Cur=curar | K=matar | S=detener | X=borrar | Fac=asignar faccion</font></center><br>");

		sb.append("<table width=300><tr>");
		if (filterFaction > 0)
		{
			button(sb, "Anterior", "admin_phantom factions " + filterFaction + " " + Math.max(0, page - 1));
			button(sb, "Todos", "admin_phantom online 0");
			button(sb, "Siguiente", "admin_phantom factions " + filterFaction + " " + Math.min(maxPage, page + 1));
		}
		else
		{
			button(sb, "Anterior", "admin_phantom online " + Math.max(0, page - 1));
			button(sb, "Principal", "admin_phantom");
			button(sb, "Siguiente", "admin_phantom online " + Math.min(maxPage, page + 1));
		}
		sb.append("</tr></table>");
		sb.append("</body></html>");
		sendHtml(player, sb);
	}

	/**
	 * Cycles to the next real faction id (from faction.xml), wrapping back to 0 after
	 * the last one. Unlike the old fId+1 logic, this works with non-contiguous ids.
	 */
	private static int getNextFactionId(int current)
	{
		if (!FactionWarConfig.isEnabled())
			return 0;

		final int[] factionIds = FactionData.getInstance().getFactionIds();
		if (factionIds.length == 0)
			return 0;

		if (current == 0)
			return factionIds[0];

		for (int i = 0; i < factionIds.length; i++)
		{
			if (factionIds[i] == current)
				return (i + 1 < factionIds.length) ? factionIds[i + 1] : 0;
		}

		return factionIds[0];
	}

	private static String shortText(String text, int max)
	{
		if (text == null)
			return "-";
		return text.length() <= max ? text : text.substring(0, max);
	}

	private static String htmlSafe(String text)
	{
		if (text == null)
			return "";
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static void sendHtml(Player player, StringBuilder sb)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}

	private static void sectionTitle(StringBuilder sb, String title)
	{
		sb.append("<table width=330><tr><td bgcolor=3A3A3A><font color=FFD700>").append(title).append("</font></td></tr></table><br1>");
	}

	private static void infoRow(StringBuilder sb, String label, Object value)
	{
		sb.append("<tr><td width=140>").append(label).append("</td><td width=190><font color=LEVEL>").append(value).append("</font></td></tr>");
	}

	private static void buttonRow2(StringBuilder sb, String label1, String bypass1, String label2, String bypass2)
	{			sb.append("<table width=300><tr>");
			button(sb, label1, bypass1, 150);
			button(sb, label2, bypass2, 150);
			sb.append("</tr></table>");
	}

	private static void buttonRowSkipEmpty(StringBuilder sb, String label1, String bypass1, String label2, String bypass2, String label3, String bypass3)
	{
		sb.append("<table width=300><tr>");
		if (!label1.isEmpty())
			button(sb, label1, bypass1, 150);
		if (!label2.isEmpty())
			button(sb, label2, bypass2, 150);
		if (!label3.isEmpty())
			button(sb, label3, bypass3, 150);
		sb.append("</tr></table>");
	}

	private static void button(StringBuilder sb, String label, String bypass)
	{
		sb.append("<td><button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=90 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
	}

	private static void button(StringBuilder sb, String label, String bypass, int width)
	{
		sb.append("<td><button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=").append(width).append(" height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
	}

	private static void miniButton(StringBuilder sb, String label, String bypass, int width)
	{
		sb.append("<button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=").append(width).append(" height=17 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\">");
	}

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}