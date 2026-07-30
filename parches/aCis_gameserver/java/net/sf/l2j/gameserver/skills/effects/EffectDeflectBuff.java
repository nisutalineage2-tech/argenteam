package net.sf.l2j.gameserver.skills.effects;

import net.sf.l2j.gameserver.model.L2Effect;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.skills.Env;
import net.sf.l2j.gameserver.templates.skills.L2EffectType;
import net.sf.l2j.gameserver.templates.skills.L2SkillType;


public final class EffectDeflectBuff extends L2Effect
{
       public EffectDeflectBuff(Env env, EffectTemplate template)
       {
               super(env, template);
       }

       /*
        * (non-Javadoc)
        *
        * @see com.l2jfrozen.gameserver.model.L2Effect#getEffectType()
        */
       @Override
       public L2EffectType getEffectType()
       {
               return L2EffectType.PREVENT_BUFF;
       }
      
       /*
        * (non-Javadoc)
        *
        * @see com.l2jfrozen.gameserver.model.L2Effect#onActionTime()
        */
       @Override
       public boolean onActionTime()
       {
               if(getSkill().getSkillType() != L2SkillType.CONT)
                       return false;

               double manaDam = calc();

               if(manaDam > getEffected().getCurrentMp())
               {
       getEffected().sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SKILL_REMOVED_DUE_LACK_MP));
                       return false;
               }

               getEffected().reduceCurrentMp(manaDam);
               return true;
       }
      
       /*
        * (non-Javadoc)
        *
        * @see com.l2jfrozen.gameserver.model.L2Effect#onStart()
        */
       @Override
       public boolean onStart()
       {
               getEffected().setIsBuffProtected(true);
               return true;
       }

       /*
        * (non-Javadoc)
        *
        * @see com.l2jfrozen.gameserver.model.L2Effect#onExit()
        */
       @Override
       public void onExit()
       {
               getEffected().setIsBuffProtected(false);
       }
}