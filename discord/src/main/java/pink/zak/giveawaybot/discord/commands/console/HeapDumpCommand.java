package pink.zak.giveawaybot.discord.commands.console;

import com.sun.management.HotSpotDiagnosticMXBean;
import lombok.SneakyThrows;
import pink.zak.giveawaybot.discord.GiveawayBot;
import pink.zak.giveawaybot.discord.service.command.console.command.ConsoleBaseCommand;

import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HeapDumpCommand extends ConsoleBaseCommand {

    public HeapDumpCommand(GiveawayBot bot) {
        super(bot, "dump");
    }

    @SneakyThrows
    @Override
    public void onExecute(List<String> args) {
        long startTime = System.currentTimeMillis();
        String fileName = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date(System.currentTimeMillis())) + " dump.hprof";
        ManagementFactory.newPlatformMXBeanProxy(ManagementFactory.getPlatformMBeanServer(),
                "com.sun.management:type=HotSpotDiagnostic", HotSpotDiagnosticMXBean.class)
                .dumpHeap(fileName, true);
        GiveawayBot.logger().info("Created your heap dump called {} in {}ms", fileName, System.currentTimeMillis() - startTime);
    }
}
