package pink.zak.giveawaybot.pipelines.entries;

import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.cache.GiveawayCache;
import pink.zak.giveawaybot.pipelines.entries.steps.EligibilityCheckStep;
import pink.zak.giveawaybot.enums.EntryType;
import pink.zak.giveawaybot.models.Preset;
import pink.zak.giveawaybot.models.Server;

public class EntryPipeline {
    private final EligibilityCheckStep checkStep;
    private final GiveawayCache giveawayCache;
    private final Preset defaultPreset;

    public EntryPipeline(GiveawayBot bot) {
        this.checkStep = new EligibilityCheckStep(bot);
        this.giveawayCache = bot.getGiveawayCache();
        this.defaultPreset = bot.getDefaults().getDefaultPreset();
    }

    public void process(EntryType entryType, Server server, long userId) {
        if (server.getActiveGiveaways().isEmpty()) {
            return;
        }
        server.getUserCache().get(userId).thenAccept(user -> {
            if (user.isBanned()) {
                return;
            }
            for (long giveawayId : server.getActiveGiveaways()) {
                this.giveawayCache.get(giveawayId).thenAccept(giveaway -> {
                    if (giveaway == null) {
                        return;
                    }
                    this.checkStep.process(entryType, user, giveaway, giveaway.getPresetName().equals("default") ? this.defaultPreset : server.getPreset(giveaway.getPresetName()));
                }).exceptionally(ex -> {
                    GiveawayBot.logger().error("Server " + server.getId() + " user " + userId + " giveaway id " + giveawayId, ex);
                    return null;
                });
            }
        });
    }
}