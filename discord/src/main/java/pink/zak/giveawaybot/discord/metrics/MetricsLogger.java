package pink.zak.giveawaybot.discord.metrics;

import pink.zak.giveawaybot.discord.GiveawayBot;
import pink.zak.giveawaybot.discord.cache.GiveawayCache;
import pink.zak.giveawaybot.discord.cache.ServerCache;
import pink.zak.giveawaybot.discord.metrics.helpers.GenericBotMetrics;
import pink.zak.giveawaybot.discord.metrics.queries.CommandQuery;
import pink.zak.giveawaybot.discord.metrics.queries.GenericQuery;
import pink.zak.giveawaybot.discord.metrics.queries.GiveawayCacheQuery;
import pink.zak.giveawaybot.discord.metrics.queries.ServerCacheQuery;
import pink.zak.giveawaybot.discord.metrics.queries.ServerQuery;
import pink.zak.giveawaybot.discord.models.Server;
import pink.zak.giveawaybot.discord.service.command.discord.DiscordCommandBase;
import pink.zak.metrics.Metrics;
import pink.zak.metrics.queries.stock.SystemQuery;
import pink.zak.metrics.queries.stock.backends.ProcessStats;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricsLogger {
    private final GenericBotMetrics genericBotMetrics;

    public MetricsLogger(GiveawayBot bot) {
        this.genericBotMetrics = new GenericBotMetrics(bot);
    }

    public void checkAndStart(GiveawayBot bot) {
        if (!bot.getConfig("settings").bool("enable-metrics")) {
            GiveawayBot.logger().info("Metrics has not been enabled as it is disabled via configuration.");
            return;
        }
        ScheduledExecutorService scheduler = bot.getThreadManager().getScheduler();
        Metrics metrics = bot.getMetrics();

        ProcessStats processStats = new ProcessStats();
        GiveawayCache giveawayCache = bot.getGiveawayCache();
        DiscordCommandBase commandBase = bot.getDiscordCommandBase();
        ServerCache serverCache = bot.getServerCache();

        scheduler.scheduleAtFixedRate(() -> {
            metrics.<ProcessStats>log(query -> query
                    .primary(processStats)
                    .push(SystemQuery.ALL));
            metrics.<GiveawayCache>log(query -> query
                    .primary(giveawayCache)
                    .push(GiveawayCacheQuery.ALL)
            );
            metrics.<ServerCache>log(query -> query
                    .primary(serverCache)
                    .push(ServerCacheQuery.ALL)
            );
            metrics.<DiscordCommandBase>log(query -> query
                    .primary(commandBase)
                    .push(CommandQuery.COMMAND_EXECUTIONS));
            metrics.<GenericBotMetrics>log(query -> query
                    .primary(this.genericBotMetrics)
                    .push(GenericQuery.ALL));
            for (Server server : serverCache.getMap().values()) {
                metrics.<Server>log(query -> query
                        .primary(server)
                        .push(ServerQuery.ALL)
                );
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public GenericBotMetrics getGenericBotMetrics() {
        return this.genericBotMetrics;
    }

    public int getGuildCount() {
        return this.genericBotMetrics.getGuilds();
    }
}
