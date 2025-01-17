package pink.zak.giveawaybot.discord.controllers;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import pink.zak.giveawaybot.discord.GiveawayBot;
import pink.zak.giveawaybot.discord.cache.GiveawayCache;
import pink.zak.giveawaybot.discord.cache.ScheduledGiveawayCache;
import pink.zak.giveawaybot.discord.cache.ServerCache;
import pink.zak.giveawaybot.discord.defaults.Defaults;
import pink.zak.giveawaybot.discord.enums.ReturnCode;
import pink.zak.giveawaybot.discord.enums.Setting;
import pink.zak.giveawaybot.discord.lang.LanguageRegistry;
import pink.zak.giveawaybot.discord.lang.enums.Text;
import pink.zak.giveawaybot.discord.metrics.helpers.LatencyMonitor;
import pink.zak.giveawaybot.discord.models.Preset;
import pink.zak.giveawaybot.discord.models.Server;
import pink.zak.giveawaybot.discord.models.giveaway.CurrentGiveaway;
import pink.zak.giveawaybot.discord.models.giveaway.Giveaway;
import pink.zak.giveawaybot.discord.models.giveaway.RichGiveaway;
import pink.zak.giveawaybot.discord.pipelines.giveaway.GiveawayPipeline;
import pink.zak.giveawaybot.discord.pipelines.giveaway.steps.DeletionStep;
import pink.zak.giveawaybot.discord.service.colour.Palette;
import pink.zak.giveawaybot.discord.service.time.Time;
import pink.zak.giveawaybot.discord.service.time.TimeIdentifier;
import pink.zak.giveawaybot.discord.service.tuple.ImmutablePair;
import pink.zak.giveawaybot.discord.service.types.ReactionContainer;
import pink.zak.giveawaybot.discord.storage.GiveawayStorage;
import pink.zak.giveawaybot.discord.threads.ThreadFunction;
import pink.zak.giveawaybot.discord.threads.ThreadManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GiveawayController {
    private final Map<CurrentGiveaway, ScheduledFuture<?>> scheduledFutures = Maps.newConcurrentMap();
    private final ThreadManager threadManager;
    private final LanguageRegistry languageRegistry;
    private final GiveawayCache giveawayCache;
    private final GiveawayStorage giveawayStorage;
    private final ScheduledGiveawayCache scheduledGiveawayCache;
    private final ServerCache serverCache;
    private final Preset defaultPreset;
    private final Palette palette;
    private final Defaults defaults;
    private final GiveawayBot bot;

    private final GiveawayPipeline giveawayPipeline;
    private final DeletionStep deletionStep;

    public GiveawayController(GiveawayBot bot) {
        this.threadManager = bot.getThreadManager();
        this.languageRegistry = bot.getLanguageRegistry();
        this.giveawayCache = bot.getGiveawayCache();
        this.giveawayStorage = bot.getGiveawayStorage();
        this.scheduledGiveawayCache = bot.getScheduledGiveawayCache();
        this.serverCache = bot.getServerCache();
        this.defaultPreset = Defaults.defaultPreset;
        this.palette = bot.getDefaults().getPalette();
        this.defaults = bot.getDefaults();
        this.bot = bot;

        this.giveawayPipeline = new GiveawayPipeline(bot, this);
        this.deletionStep = new DeletionStep(bot, this);

        this.loadAllGiveaways();
        this.startGiveawayUpdater();
    }

    public ImmutablePair<CurrentGiveaway, ReturnCode> createGiveaway(Server server, long length, long endTime, int winnerAmount, TextChannel giveawayChannel, String presetName, String giveawayItem) {
        if (server.getActiveGiveaways().size() >= (server.isPremium() ? 10 : 5)) {
            return ImmutablePair.of(null, ReturnCode.GIVEAWAY_LIMIT_FAILURE);
        }
        if (!giveawayChannel.getGuild().getSelfMember().hasPermission(giveawayChannel, this.defaults.getRequiredPermissions())) {
            return ImmutablePair.of(null, ReturnCode.PERMISSIONS_FAILURE);
        }
        Preset preset = presetName.equalsIgnoreCase("default") ? this.defaultPreset : server.getPreset(presetName);
        if (preset == null) {
            return ImmutablePair.of(null, ReturnCode.NO_PRESET);
        }
        boolean reactToEnter = preset.getSetting(Setting.ENABLE_REACT_TO_ENTER);
        try {
            Message message = giveawayChannel.sendMessage(new EmbedBuilder()
                    .setTitle(this.languageRegistry.get(server, Text.GIVEAWAY_EMBED_TITLE, replacer -> replacer.set("item", giveawayItem)).get())
                    .setDescription(this.languageRegistry.get(server, reactToEnter ? Text.GIVEAWAY_EMBED_DESCRIPTION_REACTION : Text.GIVEAWAY_EMBED_DESCRIPTION_ALL).get())
                    .setColor(this.palette.primary())
                    .setFooter(this.getFooter(server, length, winnerAmount))
                    .build()).complete(true);

            CurrentGiveaway giveaway = new CurrentGiveaway(message.getIdLong(), giveawayChannel.getIdLong(), giveawayChannel.getGuild().getIdLong(), endTime, winnerAmount, presetName, giveawayItem);

            // Add reaction
            if (reactToEnter) {
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
            this.giveawayStorage.save(giveaway);
            server.addActiveGiveaway(giveaway);
            this.startGiveawayTimer(giveaway);
            return ImmutablePair.of(giveaway, ReturnCode.SUCCESS);
        } catch (RateLimitedException ex) {
            GiveawayBot.logger().error("", ex);
            return ImmutablePair.of(null, ReturnCode.RATE_LIMIT_FAILURE);
        } catch (InsufficientPermissionException ex) {
            return ImmutablePair.of(null, ReturnCode.PERMISSIONS_FAILURE);
        }
    }

    public Set<Giveaway> getCurrentAndScheduledGiveaways(Server server) {
        Set<Giveaway> giveaways = Sets.newHashSet();
        for (long id : server.getActiveGiveaways()) {
            giveaways.add(this.giveawayCache.get(id));
        }
        for (UUID id : server.getScheduledGiveaways()) {
            giveaways.add(this.scheduledGiveawayCache.get(id));
        }
        return giveaways;
    }

    /**
     * Gets the most giveaways concurrently active in a certain period
     * Surprisingly performance efficient (sub 1ms when giveaways are cached)
     */
    public int getGiveawayCountAt(Server server, long startTime, long endTime) {
        Set<Long> checkpoints = Sets.newHashSet(startTime, endTime);
        Set<Giveaway> giveaways = this.getCurrentAndScheduledGiveaways(server);
        for (Giveaway giveaway : giveaways) {
            if (giveaway.getStartTime() >= startTime && giveaway.getStartTime() <= endTime) {
                checkpoints.add(giveaway.getStartTime());
            }
            if (giveaway.getEndTime() <= endTime && giveaway.getEndTime() >= startTime) {
                checkpoints.add(giveaway.getEndTime());
            }
        }
        int maxCount = 0;
        for (long checkpoint : checkpoints) {
            int checkpointCount = 0;
            for (Giveaway giveaway : giveaways) {
                if (giveaway.getStartTime() <= checkpoint && giveaway.getEndTime() >= checkpoint) {
                    checkpointCount++;
                }
            }
            if (checkpointCount > maxCount) {
                maxCount = checkpointCount;
            }
        }
        return maxCount;
    }

    public void loadAllGiveaways() {
        this.threadManager.runAsync(ThreadFunction.GENERAL, () -> {
            long loadStartTime = System.currentTimeMillis();
            // TODO could we batch this into per server so rate limits are limited in effect and don't stack between servers?
            for (CurrentGiveaway giveaway : this.giveawayStorage.loadAll().join()) {
                if (this.getGiveawayMessage(giveaway) == null) {
                    this.deletionStep.delete(giveaway);
                    continue;
                }
                if (!giveaway.isActive()) {
                    this.giveawayPipeline.endGiveaway(giveaway);
                    continue;
                }
                this.giveawayCache.addGiveaway(giveaway);
                this.startGiveawayTimer(giveaway);
            }
            GiveawayBot.logger().info("Loaded {} giveaways in {} milliseconds", this.giveawayCache.size(), System.currentTimeMillis() - loadStartTime);
        });
    }

    private void startGiveawayUpdater() {
        AtomicInteger counter = new AtomicInteger();
        LatencyMonitor latencyMonitor = this.bot.getLatencyMonitor();
        this.threadManager.getScheduler().scheduleAtFixedRate(() -> {
            if (!latencyMonitor.isLatencyUsable()) {
                GiveawayBot.logger().warn("Latency was not usable so did not update giveaways ({}ms)", latencyMonitor.getLastTiming());
                return;
            }
            counter.getAndIncrement();
            for (CurrentGiveaway giveaway : this.giveawayCache.getMap().values()) {
                if (!giveaway.isActive()
                        || GiveawayBot.isLocked()
                        || !latencyMonitor.isLatencyUsable()
                        || !this.shouldUpdateMessage(counter, giveaway)
                ) return;
                this.serverCache.getAsync(giveaway.getServerId(), ThreadFunction.GENERAL).thenAccept(server -> {
                    Message message = this.getGiveawayMessage(giveaway);
                    if (message == null) {
                        GiveawayBot.logger().warn("Giveaway did not delete correctly or the discord api is dying ({} in server {}).", giveaway.getMessageId(), giveaway.getServerId());
                        return;
                    }
                    Preset preset = giveaway.getPresetName().equals("default") ? this.defaultPreset : server.getPreset(giveaway.getPresetName());
                    boolean reactToEnter = preset.getSetting(Setting.ENABLE_REACT_TO_ENTER);
                    if (!GiveawayBot.isLocked()) {
                        message.editMessage(new EmbedBuilder()
                                .setTitle(this.languageRegistry.get(server, Text.GIVEAWAY_EMBED_TITLE, replacer -> replacer.set("item", giveaway.getGiveawayItem())).get())
                                .setDescription(this.languageRegistry.get(server, reactToEnter ? Text.GIVEAWAY_EMBED_DESCRIPTION_REACTION : Text.GIVEAWAY_EMBED_DESCRIPTION_ALL).get())
                                .setColor(this.palette.primary())
                                .setFooter(this.getFooter(server, giveaway.getTimeToExpiry(), giveaway.getWinnerAmount()))
                                .build()).queue();
                    }
                }).exceptionally(ex -> {
                    GiveawayBot.logger().error("Error in giveaway updater: ", ex);
                    return null;
                });

            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private boolean shouldUpdateMessage(AtomicInteger counter, CurrentGiveaway giveaway) {
        int count = counter.get();
        long timeToExpiry = giveaway.getTimeToExpiry();
        if (timeToExpiry >= TimeIdentifier.WEEK.getMilliseconds()) {
            return count % 2880 == 0;
        }
        if (timeToExpiry >= TimeIdentifier.DAY.getMilliseconds()) {
            return count % 480 == 0;
        }
        if (timeToExpiry >= TimeIdentifier.HOUR.getMilliseconds()) {
            return count % 30 == 0;
        }
        if (timeToExpiry >= TimeIdentifier.MINUTE.getMilliseconds() * 30) {
            return count % 10 == 0;
        }
        if (timeToExpiry >= TimeIdentifier.MINUTE.getMilliseconds() * 5) {
            return count % 4 == 0;
        }
        return timeToExpiry >= 60000; // 1 minute or more to update
    }

    private void startGiveawayTimer(CurrentGiveaway giveaway) {
        if (!GiveawayBot.isLocked()) {
            this.scheduledFutures.put(giveaway, this.threadManager.getScheduler().schedule(() -> {
                GiveawayBot.logger().debug("Giveaway {} expired", giveaway.getMessageId());
                this.giveawayPipeline.endGiveaway(giveaway);
            }, giveaway.getTimeToExpiry(), TimeUnit.MILLISECONDS));
        }
    }

    @SneakyThrows
    public Message getGiveawayMessage(RichGiveaway giveaway) {
        Guild guild = this.bot.getShardManager().getGuildById(giveaway.getServerId());
        if (guild == null) {
            return null;
        }
        TextChannel channel = guild.getTextChannelById(giveaway.getChannelId());
        if (channel == null) {
            return null;
        }
        Message cachedMessage = channel.getHistory().getMessageById(giveaway.getMessageId());
        if (cachedMessage != null) {
            return cachedMessage;
        }
        try {
            return channel.retrieveMessageById(giveaway.getMessageId()).complete(true);
        } catch (CompletionException | ErrorResponseException ignored) {
            return null;
        }
    }

    private String getFooter(Server server, long length, int winnerAmount) {
        return this.languageRegistry.get(server, winnerAmount > 1 ? Text.GIVEAWAY_EMBED_FOOTER_PLURAL : Text.GIVEAWAY_EMBED_FOOTER_SINGULAR,
                replacer -> replacer.set("time", Time.format(length)).set("winner-count", winnerAmount)).get();
    }

    public Map<CurrentGiveaway, ScheduledFuture<?>> getScheduledFutures() {
        return this.scheduledFutures;
    }
}
