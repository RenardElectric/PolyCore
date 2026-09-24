package polycube.polycore.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/// Shared, vanilla-client-compatible chat formatting. Never styles a caller's component in place.
@SuppressWarnings("unused")
public final class TextComponents {
    public static String modName = "";

    private TextComponents() {}

    public static MutableComponent colored(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    public static MutableComponent message() {
        return Component.empty().withStyle(ChatFormatting.GRAY)
                .append(colored("[" + modName + "] ", ChatFormatting.GOLD));
    }

    public static MutableComponent header(String title) {
        return message().append(colored(title, ChatFormatting.GOLD).withStyle(ChatFormatting.BOLD));
    }

    public static MutableComponent success(String text) {
        return message().append(colored(text, ChatFormatting.GREEN));
    }

    public static MutableComponent error(String text) {
        return error(Component.literal(text));
    }

    public static MutableComponent error(Component text) {
        return message().append(colored("Error: ", ChatFormatting.RED))
                .append(text.copy().withStyle(ChatFormatting.RED));
    }

    public static MutableComponent warning(String text) {
        return message().append(colored("Warning: " + text, ChatFormatting.YELLOW));
    }

    public static MutableComponent value(Object value) {
        return colored(String.valueOf(value), ChatFormatting.AQUA);
    }

    public static MutableComponent value(Component value) {
        return value.copy().withStyle(ChatFormatting.AQUA);
    }

    public static MutableComponent muted(String text) {
        return colored(text, ChatFormatting.DARK_GRAY);
    }

    public static MutableComponent amount(Component amount) {
        return amount.copy().withStyle(ChatFormatting.GREEN);
    }

    public static MutableComponent field(String label, Component value) {
        return colored("\n  " + label + ": ", ChatFormatting.GRAY).append(value);
    }

    public static MutableComponent field(Component label, Component value) {
        return Component.literal("\n  ").append(label).append(": ").append(value);
    }

    public static MutableComponent indentedField(String label, Component value) {
        return colored("\n    " + label + ": ", ChatFormatting.GRAY).append(value);
    }

    public static MutableComponent badge() {
        return colored(" [default]", ChatFormatting.YELLOW);
    }

    public static MutableComponent yesNo(boolean value) {
        return colored(value ? "Yes" : "No", value ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    public static MutableComponent property(Component subject, String label, Component value) {
        return message().append(subject).append(field(label, value));
    }

    public static MutableComponent updated(Component subject, String label, Component value) {
        return success("Updated ").append(subject).append(field(label, value));
    }

    public static MutableComponent action(String label, String command) {
        return action(label, command, Component.literal("Put this command in chat:\n" + command));
    }

    public static MutableComponent action(String label, String command, Component hover) {
        return action(Component.literal(label), command, hover);
    }

    public static MutableComponent action(Component label, String command, Component hover) {
        return label.copy().withStyle(style -> style.withUnderlined(true)
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    public static MutableComponent hover(Component label, Component hover) {
        return label.copy().withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    public static MutableComponent copy(String label, String copyText) {
        return copy(label, copyText, Component.literal("Copy this text:\n" + copyText));
    }

    public static MutableComponent copy(String label, String copyText, Component hover) {
        return value(label).withStyle(style -> style.withUnderlined(true)
                .withClickEvent(new ClickEvent.CopyToClipboard(copyText))
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    public static MutableComponent confirmation(Component subject, Component consequences, String command) {
        return warning("Delete ").append(subject).append("?")
                .append(consequences)
                .append(colored("\nThis cannot be undone. Do nothing to cancel.", ChatFormatting.RED))
                .append("\n").append(action("[Prepare confirmation]", command))
                .append(" then press Enter to confirm.")
                .append("\n").append(muted(command));
    }

    public static Component bullet(String text) {
        return styled("• ", ChatFormatting.DARK_GRAY).append(styled(text, ChatFormatting.GREEN));
    }

    public static Component section(String title) {
        return styled(title, ChatFormatting.GOLD, true);
    }

    public static MutableComponent detail(String label, String value, ChatFormatting valueColor) {
        return styled(label + "  ", ChatFormatting.DARK_GRAY).append(styled(value, valueColor));
    }

    public static MutableComponent styled(String text, ChatFormatting color) {
        return styled(text, color, false);
    }

    public static MutableComponent styled(String text, ChatFormatting color, boolean bold) {
        return Component.literal(text).withStyle(style -> style
                .withColor(color)
                .withBold(bold)
                .withItalic(false));
    }
}
