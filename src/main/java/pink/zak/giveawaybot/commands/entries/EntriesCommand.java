package pink.zak.giveawaybot.commands.entries;

import com.google.common.collect.Sets;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.cache.GiveawayCache;
import pink.zak.giveawaybot.lang.enums.Text;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.models.giveaway.CurrentGiveaway;
import pink.zak.giveawaybot.service.colour.Palette;
import pink.zak.giveawaybot.service.command.command.SimpleCommand;
import pink.zak.giveawaybot.service.command.command.SubCommand;
import pink.zak.giveawaybot.service.types.UserUtils;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

public class EntriesCommand extends SimpleCommand {
    private final GiveawayCache giveawayCache;
    private final Palette palette;

    public EntriesCommand(GiveawayBot bot) {
        super(bot, false, "entries");
        this.giveawayCache = bot.getGiveawayCache();
        this.palette = bot.getDefaults().getPalette();

        this.setSubCommands(
                new UserEntriesSub(bot)
        );
    }

    @Override
    public void onExecute(Member sender, Server server, MessageReceivedEvent event, List<String> args) {
        this.runLogic(sender, server, event.getTextChannel(), true);
    }

    private class UserEntriesSub extends SubCommand {

        public UserEntriesSub(GiveawayBot bot) {
            super(bot, true);

            this.addArgument(Member.class); // target
        }

        @Override
        public void onExecute(Member sender, Server server, MessageReceivedEvent event, List<String> args) {
            Member target = this.parseArgument(args, event.getGuild(), 0);
            if (target == null) {
                this.langFor(server, Text.COULDNT_FIND_MEMBER).to(event.getTextChannel());
                return;
            }
            runLogic(target, server, event.getTextChannel(), false);
        }
    }

    private void runLogic(Member target, Server server, TextChannel channel, boolean self) {
        String targetName = UserUtils.getNameDiscrim(target);
        if (server.getActiveGiveaways().isEmpty()) {
            this.langFor(server, Text.NO_ACTIVE_GIVEAWAYS).to(channel);
            return;
        }
        server.getUserCache().get(target.getIdLong()).thenAccept(user -> {
            if (user.isBanned()) {
                this.langFor(server, self ? Text.SELF_BANNED_FROM_GIVEAWAYS : Text.TARGET_BANNED_FROM_GIVEAWAYS, replacer -> replacer.set("target", target)).to(channel, this.bot, 10);
                return;
            }
            Set<Long> presentGiveaways = Sets.newHashSet();
            for (long giveawayId : server.getActiveGiveaways()) {
                if (user.entries().containsKey(giveawayId) && user.hasEntries(giveawayId)) {
                    presentGiveaways.add(giveawayId);
                }
            }
            if (presentGiveaways.isEmpty()) {
                this.langFor(server, self ? Text.SELF_NOT_ENTERED : Text.TARGET_NOT_ENTERED, replacer -> replacer.set("target", target)).to(channel, this.bot, 10);
                return;
            }
            StringBuilder descriptionBuilder = new StringBuilder();
            for (long giveawayId : presentGiveaways) {
                BigInteger entries = user.entries(giveawayId);
                CurrentGiveaway giveaway = this.giveawayCache.getSync(giveawayId);
                if (giveaway != null) {
                    descriptionBuilder.append(this.langFor(server,
                            entries.compareTo(BigInteger.ONE) < 1 ? Text.ENTRIES_EMBED_GIVEAWAY_LINE : Text.ENTRIES_EMBED_GIVEAWAY_LINE_PLURAL, replacer -> replacer
                                    .set("item", giveaway.giveawayItem())
                                    .set("entries", entries.toString())).get());
                }
            }
            channel.sendMessage(new EmbedBuilder()
                    .setTitle(this.langFor(server, Text.ENTRIES_EMBED_TITLE, replacer -> replacer.set("target", targetName)).get())
                            .setColor(this.palette.primary())
                            .setDescription(descriptionBuilder.toString())
                            .build()).queue();
        }).exceptionally(ex -> {
            GiveawayBot.getLogger().error("", ex);
            return null;
        });
    }
}
