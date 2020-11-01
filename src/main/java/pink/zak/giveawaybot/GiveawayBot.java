package pink.zak.giveawaybot;

import com.google.common.collect.Sets;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import pink.zak.giveawaybot.cache.FinishedGiveawayCache;
import pink.zak.giveawaybot.cache.GiveawayCache;
import pink.zak.giveawaybot.cache.ServerCache;
import pink.zak.giveawaybot.commands.ban.BanCommand;
import pink.zak.giveawaybot.commands.ban.ShadowBanCommand;
import pink.zak.giveawaybot.commands.ban.UnbanCommand;
import pink.zak.giveawaybot.commands.entries.EntriesCommand;
import pink.zak.giveawaybot.commands.giveaway.GiveawayCommand;
import pink.zak.giveawaybot.commands.preset.PresetCommand;
import pink.zak.giveawaybot.controllers.GiveawayController;
import pink.zak.giveawaybot.defaults.Defaults;
import pink.zak.giveawaybot.entries.pipeline.EntryPipeline;
import pink.zak.giveawaybot.lang.LanguageRegistry;
import pink.zak.giveawaybot.listener.MessageSendListener;
import pink.zak.giveawaybot.listener.ReactionAddListener;
import pink.zak.giveawaybot.metrics.MetricsStarter;
import pink.zak.giveawaybot.service.bot.JdaBot;
import pink.zak.giveawaybot.service.config.Config;
import pink.zak.giveawaybot.storage.FinishedGiveawayStorage;
import pink.zak.giveawaybot.storage.GiveawayStorage;
import pink.zak.giveawaybot.storage.ServerStorage;
import pink.zak.giveawaybot.storage.redis.RedisManager;
import pink.zak.giveawaybot.threads.ThreadFunction;
import pink.zak.giveawaybot.threads.ThreadManager;
import pink.zak.metrics.Metrics;
import redis.clients.jedis.Jedis;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GiveawayBot extends JdaBot {
    private Defaults defaults;
    private Metrics metricsLogger;
    private Consumer<Throwable> deleteFailureThrowable;
    private ThreadManager threadManager;
    private RedisManager redisManager;
    private FinishedGiveawayStorage finishedGiveawayStorage;
    private FinishedGiveawayCache finishedGiveawayCache;
    private GiveawayStorage giveawayStorage;
    private GiveawayCache giveawayCache;
    private ServerStorage serverStorage;
    private ServerCache serverCache;
    private LanguageRegistry languageRegistry;
    private GiveawayController giveawayController;
    private EntryPipeline entryPipeline;

    public GiveawayBot() {
        super(basePath -> basePath);
    }

    public static Logger getLogger() {
        return logger;
    }

    public void load() {
        this.configRelations();
        this.setupThrowable();
        this.setupStorage();
        Config settings = this.getConfigStore().getConfig("settings");

        this.metricsLogger = new Metrics(new Metrics.Config(settings.string("influx-url"),
                settings.string("influx-token").toCharArray(),
                settings.string("influx-org"),
                settings.string("influx-bucket"), 5));

        this.threadManager = new ThreadManager();
        this.redisManager = new RedisManager(this);
        this.finishedGiveawayStorage = new FinishedGiveawayStorage(this);
        this.finishedGiveawayCache = new FinishedGiveawayCache(this);
        this.giveawayStorage = new GiveawayStorage(this);
        this.giveawayCache = new GiveawayCache(this);
        this.serverStorage = new ServerStorage(this);
        this.serverCache = new ServerCache(this);
        this.languageRegistry = new LanguageRegistry();

        this.languageRegistry.loadLanguages(this);

        this.initialize(this, this.getConfigStore().commons().get("token"), ">", this.getGatewayIntents(), shard -> shard
                .disableCache(CacheFlag.VOICE_STATE)); // This should basically be called as late as physically possible

        this.defaults = new Defaults(this);
        this.entryPipeline = new EntryPipeline(this);

        Runtime.getRuntime().addShutdownHook(new ShutdownHook(this));
    }

    @Override
    public void onConnect() {
        this.getJda().getPresence().setPresence(OnlineStatus.DO_NOT_DISTURB, Activity.playing("Loading...."));
        this.giveawayController = new GiveawayController(this); // Makes use of JDA, retrieving messages
        this.registerCommands(
                new BanCommand(this),
                new ShadowBanCommand(this),
                new UnbanCommand(this),
                new EntriesCommand(this),
                new GiveawayCommand(this),
                new PresetCommand(this)
        );

        this.registerListeners(
                new ReactionAddListener(this),
                new MessageSendListener(this)
        );
        new MetricsStarter().start(this);
        this.getJda().getPresence().setPresence(OnlineStatus.ONLINE, Activity.playing("smartgiveaways.xyz"));
    }

    @Override
    public void unload() {
        logger.info("Shutting down....");
        this.giveawayCache.shutdown();
        this.finishedGiveawayCache.shutdown();
        this.serverCache.shutdown();
        this.giveawayStorage.closeBack();
        this.finishedGiveawayStorage.closeBack();
        this.serverStorage.closeBack();
        this.redisManager.shutdown();
        this.threadManager.shutdownPools();
        logger.info("Completing shut down sequence.");
    }

    public void configRelations() {
        this.getConfigStore().config("settings", Path::resolve, true);

        this.getConfigStore().common("token", "settings", config -> config.string("token"));
    }

    private void setupStorage() {
        Config settings = this.getConfigStore().getConfig("settings");
        this.storageSettings.setAddress(settings.string("mongo-ip") + ":" + settings.string("mongo-port"));
        this.storageSettings.setDatabase(settings.string("mongo-storage-database"));
        if (settings.has("mongo-username")) {
            this.storageSettings.setAuthDatabase(settings.string("mongo-auth-database"));
            this.storageSettings.setUsername(settings.string("mongo-username"));
            this.storageSettings.setPassword(settings.string("mongo-password"));
        }
    }

    private void setupThrowable() {
        this.deleteFailureThrowable = ex -> {
            if (!(ex instanceof ErrorResponseException)) {
                GiveawayBot.getLogger().error("", ex);
            }
        };
    }

    public Defaults getDefaults() {
        return this.defaults;
    }

    public Metrics getMetrics() {
        return this.metricsLogger;
    }

    public Consumer<Throwable> getDeleteFailureThrowable() {
        return this.deleteFailureThrowable;
    }

    public void runOnMainThread(Runnable runnable) {
        this.threadManager.runOnMainThread(runnable);
    }

    public CompletableFuture<?> runAsync(ThreadFunction function, Supplier<?> supplier) {
        return CompletableFuture.supplyAsync(supplier, this.threadManager.getAsyncExecutor(function));
    }

    public Future<?> runAsync(ThreadFunction function, Runnable runnable) {
        return this.threadManager.runAsync(function, runnable);
    }

    public ExecutorService getAsyncExecutor(ThreadFunction function) {
        return this.threadManager.getAsyncExecutor(function);
    }

    public ThreadManager getThreadManager() {
        return this.threadManager;
    }

    public RedisManager getRedisManager() {
        return this.redisManager;
    }

    public FinishedGiveawayStorage getFinishedGiveawayStorage() {
        return this.finishedGiveawayStorage;
    }

    public FinishedGiveawayCache getFinishedGiveawayCache() {
        return this.finishedGiveawayCache;
    }

    public Jedis getJedis() {
        return this.redisManager.getConnection();
    }

    public GiveawayStorage getGiveawayStorage() {
        return this.giveawayStorage;
    }

    public GiveawayCache getGiveawayCache() {
        return this.giveawayCache;
    }

    public ServerStorage getServerStorage() {
        return this.serverStorage;
    }

    public ServerCache getServerCache() {
        return this.serverCache;
    }

    public LanguageRegistry getLanguageRegistry() {
        return this.languageRegistry;
    }

    public GiveawayController getGiveawayController() {
        return this.giveawayController;
    }

    public EntryPipeline getEntryPipeline() {
        return this.entryPipeline;
    }

    private Set<GatewayIntent> getGatewayIntents() {
        return Sets.newHashSet(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS, GatewayIntent.GUILD_MESSAGE_REACTIONS);
    }
}
