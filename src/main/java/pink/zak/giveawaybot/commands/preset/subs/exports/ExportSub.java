package pink.zak.giveawaybot.commands.preset.subs.exports;

import com.google.gson.Gson;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import org.json.JSONObject;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.lang.enums.Text;
import pink.zak.giveawaybot.models.Preset;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.service.command.command.SubCommand;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ExportSub extends SubCommand {
    private final Gson gson = new Gson();

    public ExportSub(GiveawayBot bot) {
        super(bot, true, true, false);
        this.addFlat("export");
        this.addArgument(Preset.class);
    }

    @Override
    public void onExecute(Member sender, Server server, GuildMessageReceivedEvent event, List<String> args) {
        TextChannel channel = event.getChannel();
        Preset preset = this.parseArgument(args, event.getGuild(), 1);
        if (preset == null) {
            this.langFor(server, Text.COULDNT_FIND_PRESET).to(channel);
            return;
        }
        this.export(server, preset, channel);
    }

    private void export(Server server, Preset preset, TextChannel channel) {
        String fileName = "presets-" + preset.name() + "-" + server.getId() + ".json";
        String json = this.gson.toJson(preset.serialized());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("singular", true);
        jsonObject.put("preset-name", preset.name());
        jsonObject.put("preset-values", json);

        try (InputStream inputStream = new ByteArrayInputStream(jsonObject.toString().getBytes(StandardCharsets.UTF_8))) {
            channel.sendMessage(this.langFor(server, Text.PRESET_EXPORTED_SINGLE, replacer -> replacer
                    .set("preset", preset.name())).get()).addFile(inputStream, fileName).queue(message -> {}, Throwable::printStackTrace);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}