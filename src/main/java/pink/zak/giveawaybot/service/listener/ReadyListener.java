package pink.zak.giveawaybot.service.listener;

import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.service.bot.SimpleBot;

import java.util.concurrent.atomic.AtomicInteger;

public class ReadyListener extends ListenerAdapter {
    private final AtomicInteger countedShards = new AtomicInteger();
    private final SimpleBot bot;
    private int requiredShards;

    public ReadyListener(SimpleBot bot) {
        this.bot = bot;
    }

    public void setRequiredShards(int requiredShards) {
        GiveawayBot.getLogger().info("Set required shards to {}", requiredShards);
        this.requiredShards = requiredShards;
        if (this.countedShards.get() > this.requiredShards) {
            this.bot.onConnect();
        }
    }

    @Override
    public void onReady(@NotNull ReadyEvent readyEvent) {
        int shard = this.countedShards.incrementAndGet();
        if (shard < this.requiredShards) {
            GiveawayBot.getLogger().info("ReadyEvent called. Shard {}/{}", shard, this.requiredShards);
            return;
        }
        GiveawayBot.getLogger().info("All shards are up! Calling onConnect");
        this.bot.setConnected(true);
        this.bot.onConnect();
    }
}
