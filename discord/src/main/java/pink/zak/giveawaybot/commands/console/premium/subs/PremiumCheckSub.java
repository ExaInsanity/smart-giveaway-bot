package pink.zak.giveawaybot.commands.console.premium.subs;

import pink.zak.giveawaybot.GiveawayBot;
import pink.zak.giveawaybot.models.Server;
import pink.zak.giveawaybot.service.time.Time;
import pink.zak.giveawaybot.service.command.console.command.ConsoleSubCommand;

import java.util.List;

public class PremiumCheckSub extends ConsoleSubCommand {

    public PremiumCheckSub(GiveawayBot bot) {
        super(bot, false);

        this.addFlatWithAliases("check", "status");
        this.addArgument(Long.class); // server id
    }

    @Override
    public void onExecute(List<String> args) {
        Server server = this.parseServerInput(args, 1);
        if (server == null) {
            return;
        }
        GiveawayBot.logger().info("-- [ Premium Info For {} ] --", server.getId());
        if (server.isPremium()) {
            GiveawayBot.logger().info("Has Premium: Yes");
            GiveawayBot.logger().info("Expires In: {}", Time.format(server.getTimeToPremiumExpiry()));
        } else {
            GiveawayBot.logger().info("Has Premium: No");
            GiveawayBot.logger().info("Expired: {}", server.getPremiumExpiry() == -1 ? "has never had premium" : Time.format(-server.getTimeToPremiumExpiry()) + " ago");
        }
        GiveawayBot.logger().info("---------------------------------------------");
    }
}