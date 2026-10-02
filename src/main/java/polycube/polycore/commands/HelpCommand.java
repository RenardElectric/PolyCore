package polycube.polycore.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.permissions.PermissionLevel;
import polycube.polycore.text.TextComponents;

import java.util.List;

public class HelpCommand extends PolyCommand {
    private static final int PAGE_SIZE = 6;

    public HelpCommand(String modId) {
        super(modId, "help", "Shows available commands and their usage", PermissionLevel.ALL);
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> getCommand(String name, CommandBuildContext buildContext) {
        return super.getCommand(name).then(Commands.argument("page_or_command", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        visible(context.getSource()).stream().map(PolyCommand::getName), builder))
                .executes(context -> show(context.getSource(), StringArgumentType.getString(context, "page_or_command"))));
    }

    @Override
    protected int execute(CommandSourceStack source) {
        return page(source, 1);
    }

    private List<PolyCommand> visible(CommandSourceStack source) {
        return PolyCommands.getCommands(this.modId).stream()
                .filter(command -> PolyCommands.findNode(source, this.modId + " " + command.getName()) != null)
                .toList();
    }

    private int show(CommandSourceStack source, String argument) {
        try {
            return page(source, Integer.parseInt(argument));
        } catch (NumberFormatException ignored) {
            for (PolyCommand command : visible(source)) {
                if (command.getName().equals(argument) || command.getAliases().contains(argument)) {
                    source.sendSuccess(() -> command.getFullDescription(source), false);
                    return 1;
                }
            }
            source.sendFailure(TextComponents.error("Unknown or unavailable command: " + argument));
            return 0;
        }
    }

    private int page(CommandSourceStack source, int page) {
        List<PolyCommand> visible = visible(source);
        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 1 || page > pages) {
            source.sendFailure(TextComponents.error("Choose a help page from 1 to " + pages + "."));
            return 0;
        }
        var message = TextComponents.header("Commands " + page + "/" + pages)
                .append("\nClick a command to prepare it; choose [Usage] for its syntax.");
        int end = Math.min(visible.size(), page * PAGE_SIZE);
        for (int i = (page - 1) * PAGE_SIZE; i < end; i++) {
            PolyCommand command = visible.get(i);
            String path = "/" + this.modId + " " + command.getName();
            message.append("\n\n  ").append(TextComponents.action(path, path + " "))
                    .append(" ").append(TextComponents.action("[Usage]", path + " help"))
                    .append("\n  " + command.getDescription());
        }
        if (page > 1 || page < pages) message.append("\n");
        if (page > 1) message.append(TextComponents.run("[Previous] ", "/" + this.modId + " help " + (page - 1)));
        if (page < pages) message.append(TextComponents.run("[Next]", "/" + this.modId + " help " + (page + 1)));
        source.sendSuccess(() -> message, false);
        return 1;
    }
}
