package pink.zak.giveawaybot.commands.ban;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.lang.enums.Text;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.service.command.command.SimpleCommand;
import pink.zak.giveawaybot.service.command.command.SubCommand;
import pink.zak.giveawaybot.service.types.UserUtils;

import java.util.List;

public class ShadowBanCommand extends SimpleCommand {

    public ShadowBanCommand(GiveawayBot bot) {
        super(bot, true, "gshadowban");
        this.setAliases("gsban");

        this.setSubCommands(new BanSub(bot));
    }

    @Override
    public void onExecute(Member sender, Server server, MessageReceivedEvent event, List<String> args) {
        event.getChannel().sendMessage(">sban <user> - Bans a user from giveaways but they have no way of finding out (entries count but they cannot win).").queue();

    }

    private class BanSub extends SubCommand {

        public BanSub(GiveawayBot bot) {
            super(bot, true);

            this.addArgument(Member.class);
        }

        @Override
        public void onExecute(Member sender, Server server, MessageReceivedEvent event, List<String> args) {
            Member target = this.parseArgument(args, event.getGuild(), 0);
            TextChannel textChannel = event.getTextChannel();
            if (target == null) {
                this.langFor(server, Text.CANT_FIND_MEMBER).to(textChannel);
                return;
            }
            if (target.getIdLong() == sender.getIdLong()) {
                this.langFor(server, Text.CANNOT_BAN_SELF).to(textChannel);
                return;
            }
            if (server.canMemberManage(target)) {
                this.langFor(server, Text.NOT_ENOUGH_PERMISSIONS_BAN, replacer -> replacer.set("target", target.getAsMention())).to(textChannel);
                return;
            }
            server.getUserCache().get(target.getIdLong()).thenAccept(user -> {
                String userPlaceholder = UserUtils.getNameDiscrim(target);
                if (user.isShadowBanned()) {
                    this.langFor(server, Text.TARGET_ALREADY_SHADOW_BANNED, replacer -> replacer.set("target", userPlaceholder)).to(textChannel);
                    return;
                }
                if (user.isBanned()) {
                    this.langFor(server, Text.CANNOT_BAN_IS_BANNED, replacer -> replacer.set("target", userPlaceholder)).to(textChannel);
                    return;
                }
                user.shadowBan();
                this.langFor(server, Text.BANNED_SUCCESSFULLY, replacer -> replacer.set("target", userPlaceholder)).to(textChannel);
            });
        }
    }
}
