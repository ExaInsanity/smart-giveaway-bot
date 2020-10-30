package pink.zak.giveawaybot.commands.preset.subs;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.models.Preset;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.service.colour.Palette;
import pink.zak.giveawaybot.service.command.command.SubCommand;

import java.util.List;

public class ListSub extends SubCommand {
    private final Palette palette;

    public ListSub(GiveawayBot bot) {
        super(bot);
        this.palette = bot.getDefaults().getPalette();

        this.addFlat("list");
    }

    @Override
    public void onExecute(Member sender, Server server, MessageReceivedEvent event, List<String> args) {
        StringBuilder listBuilder = new StringBuilder("default - This cannot be removed");
        for (Preset preset : server.getPresets().values()) {
            listBuilder.append("\n")
                    .append(preset.name());
        }
        event.getChannel().sendMessage(new EmbedBuilder()
                .setColor(this.palette.primary())
                .setTitle("Current Presets (**" + (server.getPresets().size() + 1) + "**)")
                .setDescription(listBuilder.toString())
                .build()).queue();
    }
}
