package pink.zak.giveawaybot.discord.service.listener;

import pink.zak.giveawaybot.discord.GiveawayBot;
import pink.zak.giveawaybot.discord.service.command.console.ConsoleCommandBase;

import java.util.Scanner;

public class ConsoleListener implements Runnable {
    private final ConsoleCommandBase commandBase;

    public ConsoleListener(GiveawayBot bot) {
        this.commandBase = bot.getConsoleCommandBase();
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            this.commandBase.onExecute(scanner.nextLine());
        }
    }
}
