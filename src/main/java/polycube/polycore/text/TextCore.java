package polycube.polycore.text;

import net.minecraft.resources.Identifier;

import java.util.Locale;

public final class TextCore {

    private TextCore() {}

    /// Abbreviate a string to a given length, adding an ellipsis if the string is longer than the limit.
    public static String abbreviate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }

    /// Capitalize the first letter of a string.
    public static String titleCase(String text) {
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /// Return a string representation of a number, omitting the decimal point if the number is an integer.
    public static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /// Return a string that counts a noun, adding an "s" if the count is not 1.
    /// E.g., count(1, "apple") returns "1 apple", and count(2, "apple") returns "2 apples".
    public static String count(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    /// Convert a number of ticks to a human-readable duration string.
    /// E.g., 20 ticks become "1s", 40 ticks become "2s", 1200 ticks become "1m", and 1250 ticks become "1m 5s".
    public static String duration(long ticks) {
        long seconds = Math.max(1, (ticks + 19) / 20);
        long minutes = seconds / 60;
        long trailingSeconds = seconds % 60;
        return minutes == 0
                ? seconds + "s"
                : minutes + "m" + (trailingSeconds == 0 ? "" : " " + trailingSeconds + "s");
    }

    /// Humanize an identifier by removing the namespace and capitalizing each word
    /// E.g. "minecraft:my_id/object_name" becomes "My Id Object Name".
    public static String humanize(Identifier id) {
        return humanizePath(id.getPath());
    }

    /// Humanize a reference by removing the namespace and capitalizing each word
    /// E.g. "minecraft:my_id/object_name" becomes "My Id Object Name".
    public static String humanizeReference(String reference) {
        String value = reference.startsWith("#") ? reference.substring(1) : reference;
        int namespaceSeparator = value.indexOf(':');
        String path = namespaceSeparator >= 0 ? value.substring(namespaceSeparator + 1) : value;
        return humanizePath(path) + (reference.startsWith("#") ? " tag" : "");
    }

    /// Humanize a path by capitalizing each word and removing underscores and slashes. E.g. "my_id/object_name" becomes "My Id Object Name".
    public static String humanizePath(String path) {
        String[] words = path.split("[_/]");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }
}
