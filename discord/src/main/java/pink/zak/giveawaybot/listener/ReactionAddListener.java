package pink.zak.giveawaybot.listener;

import com.google.common.collect.Maps;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.cache.GiveawayCache;
import pink.zak.giveawaybot.cache.ServerCache;
import pink.zak.giveawaybot.enums.EntryType;
import pink.zak.giveawaybot.enums.Setting;
import pink.zak.giveawaybot.models.Preset;
import pink.zak.giveawaybot.service.types.ReactionContainer;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ReactionAddListener extends ListenerAdapter {
    private final ServerCache serverCache;
    private final GiveawayCache giveawayCache;
    private final Preset defaultPreset;
    private final AtomicInteger entryCount;
    private final EnumMap<EntryType, AtomicInteger> baseMap;

    public ReactionAddListener(GiveawayBot bot) {
        this.serverCache = bot.getServerCache();
        this.giveawayCache = bot.getGiveawayCache();
        this.defaultPreset = bot.getDefaults().getDefaultPreset();
        this.entryCount = bot.getMetricsLogger().getGenericBotMetrics().getEntryCount();

        this.baseMap = Maps.newEnumMap(EntryType.class);
        this.baseMap.put(EntryType.REACTION, new AtomicInteger(1));

    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        long messageId = event.getMessageIdLong();
        long userId = event.getUserIdLong();
        this.serverCache.get(event.getGuild().getIdLong()).thenAccept(server -> {
            if (!server.getActiveGiveaways().contains(messageId)) {
                return;
            }
            this.giveawayCache.get(messageId).thenAccept(giveaway -> {
                if (giveaway.getEnteredUsers().contains(userId)) {
                    return;
                }
                Preset preset = giveaway.getPresetName().equals("default") ? this.defaultPreset : server.getPreset(giveaway.getPresetName());
                MessageReaction.ReactionEmote messageReaction = event.getReactionEmote();
                MessageReaction.ReactionEmote setReaction = ((ReactionContainer) preset.getSetting(Setting.REACT_TO_ENTER_EMOJI)).getReactionEmote();
                if (messageReaction.isEmoji() != setReaction.isEmoji()
                        || (messageReaction.isEmoji() && !messageReaction.getAsCodepoints().equals(setReaction.getAsCodepoints()))
                        || (messageReaction.isEmote() && !messageReaction.getAsReactionCode().equals(setReaction.getAsReactionCode()))) {
                    return;
                }
                server.getUserCache().get(userId).thenAccept(user -> {
                    giveaway.getEnteredUsers().add(userId);
                    user.getEntries().put(giveaway.getMessageId(), this.baseMap.clone());
                    this.entryCount.incrementAndGet();
                });
            }).exceptionally(ex -> {
                GiveawayBot.logger().error("messageId:userId:serverId  {}:{}:{}", messageId, userId, server.getId(), ex);
                return null;
            });
        }).exceptionally(ex -> {
            GiveawayBot.logger().error("Error in ReactionAddListener point A", ex);
            return null;
        });
    }
}