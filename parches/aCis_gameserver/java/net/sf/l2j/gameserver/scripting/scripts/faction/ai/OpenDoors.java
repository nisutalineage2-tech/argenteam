/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.scripting.scripts.faction.ai;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.gameserver.datatables.DoorTable;
import net.sf.l2j.gameserver.scripting.Quest;

public class OpenDoors extends Quest
{
	public OpenDoors()
	{
		super(-1, "OpenDoors");
		
		ThreadPool.schedule(() -> OpenAllDoors(), 2000);
	}
	
	public static void CloseAllDoors()
	{
		DoorTable.getInstance().getDoor(20160001).closeMe();
		DoorTable.getInstance().getDoor(20160002).closeMe();
		DoorTable.getInstance().getDoor(20160003).closeMe();
		DoorTable.getInstance().getDoor(20160004).closeMe();
		DoorTable.getInstance().getDoor(20160005).closeMe();
		DoorTable.getInstance().getDoor(20160006).closeMe();
		DoorTable.getInstance().getDoor(25150021).closeMe();
		DoorTable.getInstance().getDoor(25150022).closeMe();
		DoorTable.getInstance().getDoor(25150023).closeMe();
		DoorTable.getInstance().getDoor(25150024).closeMe();
		DoorTable.getInstance().getDoor(25150025).closeMe();
		DoorTable.getInstance().getDoor(25150026).closeMe();
		DoorTable.getInstance().getDoor(25150031).closeMe();
		DoorTable.getInstance().getDoor(25150032).closeMe();
		DoorTable.getInstance().getDoor(25150033).closeMe();
		DoorTable.getInstance().getDoor(25150034).closeMe();
		DoorTable.getInstance().getDoor(25150035).closeMe();
		DoorTable.getInstance().getDoor(25150036).closeMe();
		DoorTable.getInstance().getDoor(25150011).closeMe();
		DoorTable.getInstance().getDoor(25150012).closeMe();
		DoorTable.getInstance().getDoor(25150013).closeMe();
		DoorTable.getInstance().getDoor(25150014).closeMe();
		DoorTable.getInstance().getDoor(25150015).closeMe();
		DoorTable.getInstance().getDoor(25150016).closeMe();
		DoorTable.getInstance().getDoor(25150001).closeMe();
		DoorTable.getInstance().getDoor(25150002).closeMe();
		DoorTable.getInstance().getDoor(25150003).closeMe();
		DoorTable.getInstance().getDoor(25150004).closeMe();
		DoorTable.getInstance().getDoor(25150005).closeMe();
		DoorTable.getInstance().getDoor(25150006).closeMe();
		// ThreadPoolManager.getInstance().scheduleGeneral(new Runnable(){public void run(){OpenAllDoors();}}, 2000);
	}
	
	public static void OpenAllDoors()
	{
		DoorTable.getInstance().getDoor(22150001).openMe();
		DoorTable.getInstance().getDoor(22150002).openMe();
		DoorTable.getInstance().getDoor(22150004).openMe();
		DoorTable.getInstance().getDoor(22150007).openMe();
		DoorTable.getInstance().getDoor(22150008).openMe();
		DoorTable.getInstance().getDoor(23220008).openMe();
		DoorTable.getInstance().getDoor(23220007).openMe();
		DoorTable.getInstance().getDoor(23220002).openMe();
		DoorTable.getInstance().getDoor(23220006).openMe();
		DoorTable.getInstance().getDoor(23220005).openMe();
		DoorTable.getInstance().getDoor(23220004).doDie(null);
		DoorTable.getInstance().getDoor(23220003).doDie(null);
		DoorTable.getInstance().getDoor(20160001).openMe();
		DoorTable.getInstance().getDoor(20160002).openMe();
		DoorTable.getInstance().getDoor(20160003).openMe();
		DoorTable.getInstance().getDoor(20160004).openMe();
		DoorTable.getInstance().getDoor(20160005).openMe();
		DoorTable.getInstance().getDoor(20160006).openMe();
		DoorTable.getInstance().getDoor(25150021).openMe();
		DoorTable.getInstance().getDoor(25150022).openMe();
		DoorTable.getInstance().getDoor(25150023).openMe();
		DoorTable.getInstance().getDoor(25150024).openMe();
		DoorTable.getInstance().getDoor(25150025).openMe();
		DoorTable.getInstance().getDoor(25150026).openMe();
		DoorTable.getInstance().getDoor(25150031).openMe();
		DoorTable.getInstance().getDoor(25150032).openMe();
		DoorTable.getInstance().getDoor(25150033).openMe();
		DoorTable.getInstance().getDoor(25150034).openMe();
		DoorTable.getInstance().getDoor(25150035).openMe();
		DoorTable.getInstance().getDoor(25150036).openMe();
		DoorTable.getInstance().getDoor(25150011).openMe();
		DoorTable.getInstance().getDoor(25150012).openMe();
		DoorTable.getInstance().getDoor(25150013).openMe();
		DoorTable.getInstance().getDoor(25150014).openMe();
		DoorTable.getInstance().getDoor(25150015).openMe();
		DoorTable.getInstance().getDoor(25150016).openMe();
		DoorTable.getInstance().getDoor(25150001).openMe();
		DoorTable.getInstance().getDoor(25150002).openMe();
		DoorTable.getInstance().getDoor(25150003).openMe();
		DoorTable.getInstance().getDoor(25150004).openMe();
		DoorTable.getInstance().getDoor(25150005).openMe();
		DoorTable.getInstance().getDoor(25150006).openMe();
		DoorTable.getInstance().getDoor(19210001).openMe();		
		DoorTable.getInstance().getDoor(19210003).doDie(null);
		DoorTable.getInstance().getDoor(19210004).doDie(null);
		DoorTable.getInstance().getDoor(19210006).openMe();			
		DoorTable.getInstance().getDoor(19210007).openMe();				
		DoorTable.getInstance().getDoor(19210009).openMe();				
		// ThreadPoolManager.getInstance().scheduleGeneral(new Runnable(){public void run(){CloseAllDoors();}}, 2000);
	}
}