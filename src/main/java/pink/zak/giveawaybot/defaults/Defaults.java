package pink.zak.giveawaybot.defaults;

import net.dv8tion.jda.api.JDA;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.enums.Setting;
import pink.zak.giveawaybot.models.Preset;
import pink.zak.giveawaybot.service.colour.Palette;
import pink.zak.giveawaybot.service.colour.PaletteBuilder;
import pink.zak.giveawaybot.service.types.ReactionContainer;

import java.awt.*;

public class Defaults {
    private final JDA jda;
    public static Preset defaultPreset;
    private Palette palette;

    public Defaults(GiveawayBot bot) {
        this.jda = bot.getShardManager().retrieveApplicationInfo().getJDA();
        this.setupDefaultPreset();
        this.setupPalette();
    }

    private void setupDefaultPreset() {
        defaultPreset = new Preset("default");
        defaultPreset.setSetting(Setting.ENABLE_REACT_TO_ENTER, true);
        defaultPreset.setSetting(Setting.REACT_TO_ENTER_EMOJI, new ReactionContainer("\uD83C\uDF89", this.jda));
        defaultPreset.setSetting(Setting.ENABLE_MESSAGE_ENTRIES, true);
        defaultPreset.setSetting(Setting.ENTRIES_PER_MESSAGE, 1);
        defaultPreset.setSetting(Setting.ENABLE_INVITE_ENTRIES, true);
        defaultPreset.setSetting(Setting.ENTRIES_PER_INVITE, 250);
        defaultPreset.setSetting(Setting.MAX_ENTRIES, 1000);
        defaultPreset.setSetting(Setting.PING_WINNERS, true);
    }

    private void setupPalette() {
        this.palette = new PaletteBuilder()
                .setPrimary("D6008D")
                .setSecondary("424066")
                .setSuccess(Color.GREEN)
                .setFailure(Color.RED)
                .build();
    }

    public Preset getDefaultPreset() {
        return defaultPreset;
    }

    public Palette getPalette() {
        return this.palette;
    }
}
