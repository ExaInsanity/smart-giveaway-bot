package pink.zak.giveawaybot.controllers;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.apache.commons.lang3.tuple.ImmutablePair;
import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.cache.FinishedGiveawayCache;
import pink.zak.giveawaybot.cache.GiveawayCache;
import pink.zak.giveawaybot.cache.ServerCache;
import pink.zak.giveawaybot.cache.UserCache;
import pink.zak.giveawaybot.enums.EntryType;
import pink.zak.giveawaybot.enums.ReturnCode;
import pink.zak.giveawaybot.enums.Setting;
import pink.zak.giveawaybot.models.Preset;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.models.User;
import pink.zak.giveawaybot.models.giveaway.CurrentGiveaway;
import pink.zak.giveawaybot.models.giveaway.FinishedGiveaway;
import pink.zak.giveawaybot.models.giveaway.Giveaway;
import pink.zak.giveawaybot.service.colour.Palette;
import pink.zak.giveawaybot.service.time.Time;
import pink.zak.giveawaybot.service.types.NumberUtils;
import pink.zak.giveawaybot.service.types.ReactionContainer;
import pink.zak.giveawaybot.storage.GiveawayStorage;
import pink.zak.giveawaybot.threads.ThreadFunction;
import pink.zak.giveawaybot.threads.ThreadManager;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GiveawayController {
    private final ThreadManager threadManager;
    private final GiveawayCache giveawayCache;
    private final GiveawayStorage giveawayStorage;
    private final FinishedGiveawayCache finishedGiveawayCache;
    private final ServerCache serverCache;
    private final Preset defaultPreset;
    private final Palette palette;
    private final GiveawayBot bot;

    public GiveawayController(GiveawayBot bot) {
        this.threadManager = bot.getThreadManager();
        this.giveawayCache = bot.getGiveawayCache();
        this.giveawayStorage = bot.getGiveawayStorage();
        this.finishedGiveawayCache = bot.getFinishedGiveawayCache();
        this.serverCache = bot.getServerCache();
        this.defaultPreset = bot.getDefaults().getDefaultPreset();
        this.palette = bot.getDefaults().getPalette();
        this.bot = bot;
        this.loadAllGiveaways();
        this.startGiveawayUpdater();
    }

    public ImmutablePair<CurrentGiveaway, ReturnCode> createGiveaway(Server server, long length, int winnerAmount, TextChannel giveawayChannel, String presetName, String giveawayItem) {
        long endTime = System.currentTimeMillis() + length;
        if (server.getActiveGiveaways().size() >= 50) {
            return ImmutablePair.of(null, ReturnCode.GIVEAWAY_LIMIT_FAILURE);
        }
        Preset preset = presetName.equalsIgnoreCase("default") ? this.defaultPreset : server.getPreset(presetName);
        if (preset == null) {
            return ImmutablePair.of(null, ReturnCode.NO_PRESET);
        }
        try {
            Message message = giveawayChannel.sendMessage(new EmbedBuilder()
                    .setTitle("Giveaway: ".concat(giveawayItem))
                    .setColor(this.palette.primary())
                    .setFooter("Ends in " + Time.format(length) + " with " + winnerAmount + " winner" + (winnerAmount > 1 ? "s" : ""))
                    .build()).complete(true);

            CurrentGiveaway giveaway = new CurrentGiveaway(message.getIdLong(), giveawayChannel.getIdLong(), giveawayChannel.getGuild().getIdLong(), endTime, winnerAmount, presetName, giveawayItem);

            // Add reaction
            if ((boolean) preset.getSetting(Setting.ENABLE_REACT_TO_ENTER)) {
                MessageReaction.ReactionEmote reaction = ((ReactionContainer) preset.getSetting(Setting.REACT_TO_ENTER_EMOJI)).getReactionEmote();
                if (reaction == null) {
                    return ImmutablePair.of(giveaway, ReturnCode.UNKNOWN_EMOJI);
                }
                if (reaction.isEmoji()) {
                    message.addReaction(reaction.getEmoji()).queue();
                } else {
                    try {
                        message.addReaction(reaction.getEmote()).queue();
                    } catch (ErrorResponseException ex) {
                        if (ex.getErrorResponse() == ErrorResponse.UNKNOWN_EMOJI) {
                            return ImmutablePair.of(giveaway, ReturnCode.UNKNOWN_EMOJI);
                        }
                    }
                }
            }
            this.giveawayCache.addGiveaway(giveaway);
            server.addActiveGiveaway(giveaway);
            this.startGiveawayTimer(giveaway);
            return ImmutablePair.of(giveaway, ReturnCode.SUCCESS);
        } catch (RateLimitedException ex) {
            GiveawayBot.getLogger().error("", ex);
            return ImmutablePair.of((CurrentGiveaway) null, ReturnCode.RATE_LIMIT_FAILURE);
        }
    }

    public void loadAllGiveaways() {
        long loadStartTime = System.currentTimeMillis();
        this.bot.runAsync(ThreadFunction.STORAGE, () -> {
            for (CurrentGiveaway giveaway : this.giveawayStorage.loadAll()) {
                if (!giveaway.isActive()) {
                    this.endGiveaway(giveaway);
                    continue;
                }
                if (this.getGiveawayMessage(giveaway) == null) {
                    this.deleteGiveaway(giveaway);
                    continue;
                }
                this.giveawayCache.addGiveaway(giveaway);
                this.startGiveawayTimer(giveaway);
            }
            return null;
        }).whenComplete((o, ex) -> {
            if (ex != null) {
                GiveawayBot.getLogger().error("", ex);
            }
            GiveawayBot.getLogger().info("Loaded {} giveaways in {} milliseconds", this.giveawayCache.size(), System.currentTimeMillis() - loadStartTime);
        });
    }

    private void endGiveaway(CurrentGiveaway giveaway) {
        Message giveawayMessage = this.getGiveawayMessage(giveaway);
        if (giveawayMessage != null) {
            this.serverCache.get(giveaway.serverId()).thenAccept(server -> {
                BigInteger totalEntries = BigInteger.ZERO;
                Map<Long, BigInteger> userEntriesMap = Maps.newHashMap();
                for (long enteredUserId : giveaway.enteredUsers()) {
                    User user = server.getUserCache().getSync(enteredUserId);
                    if (user == null || user.isBanned() || user.isShadowBanned()) {
                        continue;
                    }
                    Map<EntryType, AtomicInteger> entries = user.entries().get(giveaway.messageId());
                    if (entries != null) {
                        BigInteger totalUserEntries = BigInteger.ZERO;
                        for (AtomicInteger entryTypeAmount : entries.values()) {
                            totalEntries = totalEntries.add(BigInteger.valueOf(entryTypeAmount.get()));
                            totalUserEntries = totalUserEntries.add(BigInteger.valueOf(entryTypeAmount.get()));
                        }
                        userEntriesMap.put(user.id(), totalUserEntries);
                    }
                }
                server.getActiveGiveaways().remove(giveaway.channelId());
                if (totalEntries.equals(BigInteger.ZERO)) {
                    giveawayMessage.editMessage(new EmbedBuilder()
                            .setColor(this.palette.success())
                            .setTitle("Giveaway: " + giveaway.giveawayItem())
                            .setDescription("There were not enough entries to determine winners.")
                            .setFooter("Ended with no winners.").build()).queue();
                    return;
                }
                Set<Long> winners = this.generateWinners(giveaway, totalEntries, userEntriesMap);
                GiveawayBot.getLogger().info("Giveaway {} generated winners {}", giveaway.messageId(), winners);
                this.handleGiveawayEndMessages(giveaway, winners, totalEntries, giveawayMessage, server);
                // Convert to a FinishedGiveaway
                this.finishedGiveawayCache.set(giveaway.messageId(), new FinishedGiveaway(giveaway, totalEntries, userEntriesMap, winners));
            }).exceptionally(ex -> {
                GiveawayBot.getLogger().error("", ex);
                return null;
            });
        }
        this.deleteGiveaway(giveaway);
    }

    public void handleGiveawayEndMessages(Giveaway giveaway, Set<Long> winners, BigInteger totalEntries, Message giveawayMessage, Server server) {
        StringBuilder descriptionBuilder = new StringBuilder();
        for (long winnerId : winners) {
            descriptionBuilder.append("<@").append(winnerId).append(">\n");
        }
        giveawayMessage.editMessage(new EmbedBuilder()
                .setColor(this.palette.success())
                .setTitle("Giveaway: " + giveaway.giveawayItem())
                .setDescription((winners.size() > 1 ? "**Winners:**\n" : "**Winner:**\n") + descriptionBuilder.toString())
                .setFooter("Ended with " + winners.size() + (winners.size() > 1 ? " winners" : " winner") + " and " + totalEntries.toString() + " entries.").build()).queue();
        // Handle the pinging of winners
        Preset preset = giveaway.presetName().equals("default") ? this.defaultPreset : server.getPreset(giveaway.presetName());
        if ((boolean) preset.getSetting(Setting.PING_WINNERS)) {
            giveawayMessage.getTextChannel().sendMessage(descriptionBuilder.toString()).queue(message -> message.delete().queue());
        }
    }

    public Set<Long> generateWinners(CurrentGiveaway giveaway, BigInteger totalEntries, Map<Long, BigInteger> userEntries) {
        return this.generateWinners(giveaway.winnerAmount(), totalEntries, userEntries);
    }

    public Set<Long> generateWinners(int winnerAmount, BigInteger totalEntries, Map<Long, BigInteger> userEntries) {
        Set<Long> winners = Sets.newHashSet();
        if (userEntries.size() <= winnerAmount) {
            return userEntries.keySet();
        }
        BigInteger currentTotalEntries = totalEntries;
        for (int i = 1; i <= winnerAmount; i++) {
            BigInteger decreasingRandom = currentTotalEntries.divide(NumberUtils.getRandomBigInteger(currentTotalEntries));
            for (Map.Entry<Long, BigInteger> entry : userEntries.entrySet()) {
                decreasingRandom = decreasingRandom.subtract(entry.getValue());
                if (decreasingRandom.compareTo(BigInteger.ONE) < 0) {
                    winners.add(entry.getKey());
                    if (i + 1 <= winnerAmount) {
                        userEntries.remove(entry.getKey());// Prevent duplicates if it will be looped again
                        currentTotalEntries = currentTotalEntries.subtract(entry.getValue());
                    }
                    break;
                }
            }

        }
        return winners;
    }

    public void deleteGiveaway(CurrentGiveaway giveaway) {
        this.giveawayCache.invalidateAsync(giveaway.messageId(), false);
        this.serverCache.get(giveaway.serverId()).thenAccept(server -> {
            server.getActiveGiveaways().remove(giveaway.messageId());
            GiveawayBot.getLogger().info("Removing giveaway from server {}  :  {}", giveaway.serverId(), giveaway.messageId());
            UserCache userCache = server.getUserCache();
            for (long enteredId : giveaway.enteredUsers()) {
                userCache.get(enteredId).thenAccept(user -> user.entries().remove(giveaway.messageId()));
            }
        });
        this.giveawayStorage.delete(String.valueOf(giveaway.messageId()));
    }

    private void startGiveawayUpdater() {
        this.threadManager.getUpdaterExecutor().scheduleAtFixedRate(() -> {
            for (CurrentGiveaway giveaway : this.giveawayCache.getMap().values()) {
                if (!giveaway.isActive()) {
                    return;
                }
                Message message = this.getGiveawayMessage(giveaway);
                if (message == null) {
                    this.deleteGiveaway(giveaway);
                    return;
                }
                message.editMessage(new EmbedBuilder()
                        .setTitle("Giveaway: ".concat(giveaway.giveawayItem()))
                        .setColor(this.palette.primary())
                        .setFooter("Ends in " + Time.format(giveaway.timeToExpiry()) + " with " + giveaway.winnerAmount() + " winner" + (giveaway.winnerAmount() > 1 ? "s" : ""))
                        .build()).queue();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void startGiveawayTimer(CurrentGiveaway giveaway) {
        this.threadManager.getUpdaterExecutor().schedule(() -> {
            GiveawayBot.getLogger().info("Giveaway {} expired", giveaway.messageId());
            this.endGiveaway(giveaway);
        }, giveaway.timeToExpiry(), TimeUnit.MILLISECONDS);
    }

    @SneakyThrows
    public Message getGiveawayMessage(Giveaway giveaway) {
        Guild guild = this.bot.getShardManager().getGuildById(giveaway.serverId());
        if (guild == null) {
            return null;
        }
        TextChannel channel = guild.getTextChannelById(giveaway.channelId());
        if (channel == null) {
            return null;
        }
        Message cachedMessage = channel.getHistory().getMessageById(giveaway.messageId());
        if (cachedMessage != null) {
            return cachedMessage;
        }
        try {
            return channel.retrieveMessageById(giveaway.messageId()).complete(true);
        } catch (CompletionException | ErrorResponseException ignored) {
            return null;
        }
    }
}