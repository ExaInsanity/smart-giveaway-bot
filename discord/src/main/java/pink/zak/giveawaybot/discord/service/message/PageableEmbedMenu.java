package pink.zak.giveawaybot.discord.service.message;

import com.google.common.collect.Maps;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent;
import pink.zak.giveawaybot.discord.GiveawayBot;
import pink.zak.giveawaybot.discord.listener.reaction.pageable.Page;
import pink.zak.giveawaybot.discord.listener.reaction.pageable.PageableReactionListener;
import pink.zak.giveawaybot.discord.service.colour.Palette;
import pink.zak.giveawaybot.discord.service.time.TimeIdentifier;
import pink.zak.giveawaybot.discord.lang.LanguageRegistry;
import pink.zak.giveawaybot.discord.models.Server;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class PageableEmbedMenu extends PageableMenu implements PageableReactionListener {
    protected final LanguageRegistry languageRegistry;
    protected final Palette palette;
    private final GiveawayBot bot;

    private final Map<Integer, MessageEmbed> cachedPages = Maps.newConcurrentMap();
    private long lastInteraction;
    private Message message;
    private final Server server;
    private final boolean managerOnly;

    protected PageableEmbedMenu(GiveawayBot bot, Server server, boolean managerOnly) {
        this.languageRegistry = bot.getLanguageRegistry();
        this.palette = bot.getDefaults().getPalette();
        this.bot = bot;

        this.server = server;
        this.managerOnly = managerOnly;

        bot.registerListeners(this);
    }

    public void sendInitialMessage(TextChannel channel) {
        MessageEmbed embed = this.createPage(super.currentPage.get());
        this.cachedPages.put(super.currentPage.get(), embed);
        channel.sendMessage(embed).queue(message -> {
            this.message = message;
            this.lastInteraction = System.currentTimeMillis();
            this.deleteOrReschedule();
            message.addReaction("\u2B05").queue();
            message.addReaction("\u27A1").queue();
        });
    }

    public abstract MessageEmbed createPage(int page);

    @Override
    public void drawPage(int page) {
        if (page > this.maxPage) {
            return;
        }
        MessageEmbed embed = this.cachedPages.computeIfAbsent(page, this::createPage);
        this.message.editMessage(embed).queue();
    }

    @Override
    public long getMessageId() {
        return this.message == null ? 0 : this.message.getIdLong();
    }

    @Override
    public void onReactionAdd(Page page, GuildMessageReactionAddEvent event) {
        if (this.managerOnly && !this.server.canMemberManage(event.getMember())) {
            return;
        }
        this.lastInteraction = System.currentTimeMillis();
        if (page == Page.NEXT) {
            this.nextPage();
        } else {
            this.previousPage();
        }
    }

    public void deleteOrReschedule() {
        long timeToDelete = this.lastInteraction + TimeIdentifier.MINUTE.getMilliseconds();
        if (timeToDelete <= System.currentTimeMillis()) {
            this.bot.unRegisterListeners(this);
        } else {
            bot.getThreadManager().getScheduler().schedule(this::deleteOrReschedule, timeToDelete, TimeUnit.MILLISECONDS);
        }
    }
}
