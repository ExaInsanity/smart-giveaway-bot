package pink.zak.giveawaybot.discord.metrics.helpers;

import lombok.SneakyThrows;
import net.dv8tion.jda.api.entities.TextChannel;
import pink.zak.giveawaybot.discord.GiveawayBot;
import pink.zak.giveawaybot.discord.metrics.queries.LatencyQuery;
import pink.zak.giveawaybot.discord.service.config.Config;
import pink.zak.metrics.Metrics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LatencyMonitor {
    private final List<String> messageLines;
    private final TextChannel testChannel;
    private final Metrics metrics;
    private long lastTiming;

    @SneakyThrows
    public LatencyMonitor(GiveawayBot bot) {
        Config settings = bot.getConfig("settings");
        BufferedReader reader = new BufferedReader(new FileReader(bot.getBasePath().toAbsolutePath().resolve(settings.getConfiguration().getString("latency-tester.file-for-lines"))
                .toFile()));
        this.messageLines = reader.lines().collect(Collectors.toList());
        this.testChannel = bot.getShardManager().getTextChannelById(settings.getConfiguration().getLong("latency-tester.channel-id"));
        this.metrics = bot.getMetrics();
        this.lastTiming = Long.MAX_VALUE;
        bot.getThreadManager().getScheduler().scheduleAtFixedRate(this::testLatency, 0, 30, TimeUnit.SECONDS);

        reader.close();
    }

    private void testLatency() {
        long startTime = System.currentTimeMillis();
        this.testChannel.sendMessage(this.messageLines.get(ThreadLocalRandom.current().nextInt(0, this.messageLines.size() - 1))).queue(message -> {
            this.lastTiming = System.currentTimeMillis() - startTime;
            if (this.metrics != null) {
                this.metrics.<LatencyMonitor>log(query -> query
                        .primary(this)
                        .push(LatencyQuery.LATENCY));
            }
        });
    }

    public boolean isLatencyDesirable() {
        if (this.lastTiming == Long.MAX_VALUE) {
            GiveawayBot.logger().warn("isLatencyDesirable called when no latency is set yet.");
        }
        return this.lastTiming < 2000;
    }

    public boolean isLatencyUsable() {
        if (this.lastTiming == Long.MAX_VALUE) {
            GiveawayBot.logger().warn("isLatencyUsable called when no latency is set yet.");
        }
        return this.lastTiming < 5000;
    }

    public long getLastTiming() {
        return this.lastTiming;
    }
}
