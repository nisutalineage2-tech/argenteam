package net.sf.l2j.gameserver.data.xml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.commons.data.xml.IXmlReader;

import net.sf.l2j.gameserver.guildmission.GuildMission;
import net.sf.l2j.gameserver.guildmission.GuildMissionReward;
import net.sf.l2j.gameserver.guildmission.MissionCategory;
import net.sf.l2j.gameserver.guildmission.MissionCondition;
import net.sf.l2j.gameserver.guildmission.MissionObjective;
import net.sf.l2j.gameserver.guildmission.MissionType;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GuildMissionData implements IXmlReader
{
	private final Map<Integer, GuildMission> _missionsById = new HashMap<>();
	private final List<GuildMission> _missions = new ArrayList<>();
	
	protected GuildMissionData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_missionsById.clear();
		_missions.clear();
		parseFile("./data/xml/guildMissions.xml");
		LOGGER.info("Loaded {} guild missions.", _missions.size());
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "mission", missionNode ->
		{
			final Element missionElement = (Element) missionNode;
			
			final GuildMission mission = new GuildMission();
			mission.setId(getIntValue(missionElement, "id"));
			mission.setName(getValue(missionElement, "name"));
			mission.setDescription(getValue(missionElement, "description"));
			mission.setCategory(MissionCategory.valueOf(getValue(missionElement, "category")));
			mission.setType(MissionType.valueOf(getValue(missionElement, "type")));
			mission.setRepeatable(getBoolValue(missionElement, "repeatable"));
			mission.setResetHours(getIntValue(missionElement, "resetHours"));
			mission.setSchedule(getValue(missionElement, "schedule"));
			mission.setActive(getBoolValue(missionElement, "active"));
			
			// Condition (optional).
			final Element conditionElement = getChildElement(missionElement, "condition");
			if (conditionElement != null)
			{
				final MissionCondition condition = new MissionCondition();
				condition.setMinClanLevel(getIntValue(conditionElement, "minClanLevel"));
				condition.setMinMembers(getIntValue(conditionElement, "minMembers"));
				condition.setRequiresCastle(getBoolValue(conditionElement, "requiresCastle"));
				condition.setRequiresAlliance(getBoolValue(conditionElement, "requiresAlliance"));
				condition.setRequiredQuest(getValue(conditionElement, "requiredQuest"));
				condition.setRequiredClass(getValue(conditionElement, "requiredClass"));
				condition.setRequiredLevel(getIntValue(conditionElement, "requiredLevel"));
				condition.setRequiredSubClass(getIntValue(conditionElement, "requiredSubClass"));
				mission.setCondition(condition);
			}
			
			// Objectives.
			final Element objectivesElement = getChildElement(missionElement, "objectives");
			if (objectivesElement != null)
			{
				for (Node objectiveNode : getChildElements(objectivesElement, "objective"))
				{
					final Element objectiveElement = (Element) objectiveNode;
					final MissionObjective objective = new MissionObjective();
					objective.setId(getIntValue(objectiveElement, "id"));
					objective.setType(MissionType.valueOf(getValue(objectiveElement, "type")));
					objective.setTargetId(getIntValue(objectiveElement, "targetId"));
					objective.setQuantity(getLongValue(objectiveElement, "quantity"));
					objective.setMetadata(getValue(objectiveElement, "metadata"));
					mission.getObjectives().add(objective);
				}
			}
			
			// Rewards.
			final Element rewardsElement = getChildElement(missionElement, "rewards");
			if (rewardsElement != null)
			{
				for (Node rewardNode : getChildElements(rewardsElement, "reward"))
				{
					final Element rewardElement = (Element) rewardNode;
					final GuildMissionReward reward = new GuildMissionReward();
					reward.setItemId(getIntValue(rewardElement, "itemId"));
					reward.setItemCount(getLongValue(rewardElement, "itemCount"));
					reward.setAdena(getLongValue(rewardElement, "adena"));
					reward.setClanReputation(getIntValue(rewardElement, "clanReputation"));
					reward.setSkillId(getIntValue(rewardElement, "skillId"));
					reward.setBuffId(getIntValue(rewardElement, "buffId"));
					reward.setCoins(getLongValue(rewardElement, "coins"));
					reward.setCustomReward(getValue(rewardElement, "customReward"));
					mission.getRewards().add(reward);
				}
			}
			
			_missionsById.put(mission.getId(), mission);
			_missions.add(mission);
		}));
	}
	
	public List<GuildMission> getMissions()
	{
		return Collections.unmodifiableList(_missions);
	}
	
	public GuildMission getMission(int missionId)
	{
		return _missionsById.get(missionId);
	}
	
	public int getMissionCount()
	{
		return _missions.size();
	}
	
	private static Element getChildElement(Element parent, String tagName)
	{
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++)
		{
			final Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName()))
				return (Element) node;
		}
		return null;
	}
	
	private static List<Node> getChildElements(Element parent, String tagName)
	{
		final List<Node> result = new ArrayList<>();
		final NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++)
		{
			final Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && tagName.equals(node.getNodeName()))
				result.add(node);
		}
		return result;
	}
	
	private static String getValue(Element parent, String tagName)
	{
		final Element child = getChildElement(parent, tagName);
		return child == null ? "" : child.getTextContent().trim();
	}
	
	private static int getIntValue(Element parent, String tagName)
	{
		final String value = getValue(parent, tagName);
		return value.isEmpty() ? 0 : Integer.parseInt(value);
	}
	
	private static long getLongValue(Element parent, String tagName)
	{
		final String value = getValue(parent, tagName);
		return value.isEmpty() ? 0L : Long.parseLong(value);
	}
	
	private static boolean getBoolValue(Element parent, String tagName)
	{
		final String value = getValue(parent, tagName);
		return !value.isEmpty() && Boolean.parseBoolean(value);
	}
	
	public static GuildMissionData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final GuildMissionData INSTANCE = new GuildMissionData();
	}
}
