package net.sf.l2j.gameserver.model.actor.instance;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class CassinoNpc extends Folk
{
	private static final int CHANCE_VITORIA = 90;
	private static final int GIROS = 10;
	private static final int[] PREMIOS = {57, 6379, 6380, 6381, 6382};
	private static final int TOTAL_PREMIOS = 1;
	private static final int MOEDA = 57;
	private static final int VALOR = 10000;
	private static final boolean ANUNCIAR = true;
	
	private static final String HTML_PATH = "data/html/mods/cassino/";
	
	private static final String[] LETTERS = {"a","b","c","d","e","f","g","h","i","k","l","n","o","p","r","s","t","u","w","y"};
	private static final String[] COLORS = {"0000ff","00FFFF","ffff00","ff00ff"};
	
	private static final Map<Integer, ScheduledFuture<?>> _tasks = new ConcurrentHashMap<>();
	
	public CassinoNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	private enum IconAlignment
	{
		DIAGONAL(new int[][]
		{
			{1, 5, 9},
			{3, 5, 7}
		}),

		HORIZONTAL(new int[][]
		{
			{1, 2, 3},
			{4, 5, 6},
			{7, 8, 9}
		}),

		VERTICAL(new int[][]
		{
			{1, 4, 7},
			{2, 5, 8},
			{3, 6, 9}
		});

		private final int[][] _slots;

		private IconAlignment(int[][] slots)
		{
			_slots = slots;
		}

		public int[][] getSlots()
		{
			return _slots;
		}
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		if (!st.hasMoreTokens())
		{
			super.onBypassFeedback(player, command);
			return;
		}
		
		final String currentCommand = st.nextToken();
		
		if (currentCommand.equals("start"))
		{
			if (checkTask(player.getObjectId(), true))
				return;
			
			if (player.destroyItemByItemId(MOEDA, VALOR, true))
				_tasks.put(player.getObjectId(), ThreadPool.schedule(() -> startGame1(player), 0));
			else
				showNoCoinWindow(player);
		}
		else if (currentCommand.equals("cancel"))
			checkTask(player.getObjectId(), true);
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		if (_tasks.containsKey(player.getObjectId()))
			return;
		
		if (val == 0)
			showIndexWindow(player);
		else
			super.showChatWindow(player, val);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String filename = "";
		if (val == 0)
			filename = "" + npcId;
		else
			filename = npcId + "-" + val;
		
		return "data/html/mods/cassino/" + filename + ".htm";
	}
	
	private void startGame1(Player player)
	{
		try
		{
			final Map<Integer, String> selectedIcons = new HashMap<>();
			boolean won = false;
			int spins = 0;
			int awardItemId = 0;
			String awardIcon = null;
			
			while (spins < GIROS)
			{
				spins++;
				
				if (!checkPlayer(player))
				{
					checkTask(player.getObjectId(), false);
					return;
				}
				
				// Se necesita un HTML nuevo en cada giro.
				final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile(HTML_PATH + "game1.htm");
				
				// En el ultimo giro se calcula la chance de ganar.
				if (spins == GIROS && Rnd.get(1, 100) <= CHANCE_VITORIA)
				{
					won = true;
					awardItemId = PREMIOS[Rnd.get(PREMIOS.length)];
					awardIcon = getAwardIcon(awardItemId);
					
					final IconAlignment alignment = IconAlignment.values()[Rnd.get(0, 2)];
					int[] slots = alignment.getSlots()[Rnd.get(alignment.getSlots().length)];
					
					for (int i : slots)
					{
						selectedIcons.put(i, awardIcon);
						html.replace("%color_" + i + "%", "00ff00");
						html.replace("%icon_" + i + "%", awardIcon);
					}
				}
				
				// Completar los slots restantes.
				for (int i = 1; i <= 9; i++)
				{
					if (selectedIcons.containsKey(i))
						continue;
					
					String icon = getRandomIcon();
					if (spins == GIROS)
					{
						while (checkIfAligned(selectedIcons, icon, i, awardIcon))
						{
							icon = getRandomIcon();
						}
						
						selectedIcons.put(i, icon);
						html.replace("%color_" + i + "%", won ? "ffffff" : "ff0000");
					}
					else
						html.replace("%color_" + i + "%", COLORS[Rnd.get(COLORS.length)]);
					
					html.replace("%icon_" + i + "%", icon);
				}
				
				if (spins == GIROS)
				{
					if (won)
					{
						final String rewardName = ItemData.getInstance().getTemplate(awardItemId).getName();
						if (ANUNCIAR)
							broadcastNpcShout(player.getName() + " creyo en la suerte y gano " + TOTAL_PREMIOS + " " + rewardName + "!");
						
						html.replace("%result%", "Felicidades!<br1>Ganaste " + TOTAL_PREMIOS + " " + rewardName + "!");
						rewardPlayer(player, awardItemId);
					}
					else
						html.replace("%result%", "Esta vez no ganaste.");
					
					html.replace("%cancel%", "");
					html.replace("%spins%", "");
					html.replace("%play_again%", "<button value=\"Jugar de nuevo\" action=\"bypass npc_%objectId%_start\" width=134 height=21 back=L2UI_ch3.BigButton3_over fore=L2UI_ch3.BigButton3>");
					
					sendHtmlMessage(player, html);
					checkTask(player.getObjectId(), false);
				}
				else
				{
					html.replace("%cancel%", "<br><a action=\"bypass -h npc_%objectId%_cancel\">Cancelar</a>");
					html.replace("%result%", "");
					html.replace("%spins%", "<br>" + spins + "/" + GIROS);
					html.replace("%play_again%", "");
					sendHtmlMessage(player, html);
					
					// Esperar hasta la proxima ejecucion.
					Thread.sleep(500);
				}
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}
	
	private void showNoCoinWindow(Player player)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "nocoin.htm");
		html.replace("%coin_name%", ItemData.getInstance().getTemplate(MOEDA).getName());
		html.replace("%coin_qnt%", VALOR);
		sendHtmlMessage(player, html);
	}
	
	private void showIndexWindow(Player player)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "index.htm");
		html.replace("%coin_name%", ItemData.getInstance().getTemplate(MOEDA).getName());
		html.replace("%coin_qnt%", VALOR);
		sendHtmlMessage(player, html);
	}
	
	private void sendHtmlMessage(Player player, NpcHtmlMessage html)
	{
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
	
	private static boolean checkTask(int playerId, boolean cancel)
	{
		final ScheduledFuture<?> task = _tasks.getOrDefault(playerId, null);
		if (task == null)
			return false;
		
		if (cancel)
			task.cancel(true);
		
		_tasks.remove(playerId);
		return true;
	}
	
	private boolean checkPlayer(Player player)
	{
		return player.isOnline() && player.getCurrentFolk() == this && player.isIn3DRadius(this, Npc.INTERACTION_DISTANCE);
	}
	
	private void rewardPlayer(Player player, int itemId)
	{
		if (!checkPlayer(player))
			return;
		
		player.addItem(itemId, TOTAL_PREMIOS, true);
	}
	
	/*
	 * Icono del premio. Solo se usan iconos conocidos del cliente para evitar imagenes rotas.
	 * El pack no tiene un mapeo completo de iconos por item, por eso el premio de adena usa su
	 * icono clasico y el resto usa una letra del alfabeto (el mismo recurso del giro aleatorio).
	 */
	private static String getAwardIcon(int itemId)
	{
		if (itemId == 57)
			return "icon.etc_adena_i00";
		
		return "etc_i.etc_alphabet_" + Rnd.get(LETTERS) + "_i00";
	}
	
	private static String getRandomIcon()
	{
		return "etc_i.etc_alphabet_" + Rnd.get(LETTERS) + "_i00";
	}
	
	/*
	 * Existe una posibilidad muy baja de generar iconos aleatorios que formen una secuencia igual a una victoria.
	 * Este metodo chequea el nuevo icono generado para evitar que ese problema ocurra.
	 */
	private static boolean checkIfAligned(Map<Integer, String> selectedIcons, String newIcon, int slot, String awardIcon)
	{
		// Agregamos el nuevo icono al mapa para poder probarlo. Lo eliminamos despues si no es compatible.
		selectedIcons.put(slot, newIcon);

		for (int[] slots : IconAlignment.HORIZONTAL.getSlots())
		{
			if (selectedIcons.containsKey(slots[0]) && selectedIcons.containsKey(slots[1]) && selectedIcons.containsKey(slots[2]))
			{
				final String icon1 = selectedIcons.get(slots[0]);
				final String icon2 = selectedIcons.get(slots[1]);
				final String icon3 = selectedIcons.get(slots[2]);
				// Verifica si los iconos son iguales.
				if (!icon1.equals(awardIcon) && icon1.equals(icon2) && icon2.equals(icon3))
				{
					selectedIcons.remove(slot);
					return true; // Los iconos estan alineados, no esta permitido.
				}
			}
		}

		for (int[] slots : IconAlignment.VERTICAL.getSlots())
		{
			if (selectedIcons.containsKey(slots[0]) && selectedIcons.containsKey(slots[1]) && selectedIcons.containsKey(slots[2]))
			{
				final String icon1 = selectedIcons.get(slots[0]);
				final String icon2 = selectedIcons.get(slots[1]);
				final String icon3 = selectedIcons.get(slots[2]);
				if (!icon1.equals(awardIcon) && icon1.equals(icon2) && icon2.equals(icon3))
				{
					selectedIcons.remove(slot);
					return true;
				}
			}
		}

		for (int[] slots : IconAlignment.DIAGONAL.getSlots())
		{
			if (selectedIcons.containsKey(slots[0]) && selectedIcons.containsKey(slots[1]) && selectedIcons.containsKey(slots[2]))
			{
				final String icon1 = selectedIcons.get(slots[0]);
				final String icon2 = selectedIcons.get(slots[1]);
				final String icon3 = selectedIcons.get(slots[2]);
				if (!icon1.equals(awardIcon) && icon1.equals(icon2) && icon2.equals(icon3))
				{
					selectedIcons.remove(slot);
					return true;
				}
			}
		}

		selectedIcons.remove(slot);
		return false;
	}
}
