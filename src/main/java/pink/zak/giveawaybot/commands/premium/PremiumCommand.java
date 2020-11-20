package pink.zak.giveawaybot.commands.premium;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.lang.enums.Text;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.service.colour.Palette;
import pink.zak.giveawaybot.service.command.command.SimpleCommand;
import pink.zak.giveawaybot.service.time.Time;

import java.util.List;

public class PremiumCommand extends SimpleCommand {
    private final Palette palette;

    public PremiumCommand(GiveawayBot bot) {
        super(bot, false, "premium");

        this.palette = bot.getDefaults().getPalette();
    }

    @Override
    public void onExecute(Member sender, Server server, MessageReceivedEvent event, List<String> args) {
        event.getTextChannel().sendMessage(new EmbedBuilder()
                .setTitle(this.langFor(server, Text.PREMIUM_EMBED_TITLE).get())
                .setFooter(this.langFor(server, Text.PREMIUM_EMBED_FOOTER).get())
                .setColor(this.palette.primary())
                .setDescription(this.langFor(server, server.isPremium() ? Text.PREMIUM_EMBED_DESCRIPTION_PURCHASED : Text.PREMIUM_EMBED_DESCRIPTION_NOT_PURCHASED,
                        replacer -> server.isPremium() ? replacer.set("expiry", Time.format(server.getPremiumExpiry())) : replacer).get())
                .build()).queue();
    }
}