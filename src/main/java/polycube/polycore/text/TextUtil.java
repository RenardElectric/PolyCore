package polycube.polycore.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.predicates.DamageSourcePredicate;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.core.HolderSet;

import static polycube.polycore.text.TextCore.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
public final class TextUtil {

    private TextUtil() {}

    public static String itemTarget(ItemPredicate predicate) {
        String target = predicate.items()
                .map(items -> holderSetName(items, "matching items"))
                .orElse("matching items");
        return predicate.components().isEmpty() ? target : target + " with required properties";
    }

    public static String blockTarget(BlockPredicate predicate) {
        String target = predicate.blocks()
                .map(blocks -> holderSetName(blocks, "matching blocks"))
                .orElse("matching blocks");
        boolean hasProperties = predicate.properties().isPresent()
                || predicate.nbt().isPresent()
                || !predicate.components().isEmpty();
        return hasProperties ? target + " with required properties" : target;
    }

    public static String entityTarget(DynamicOps<JsonElement> ops, EntityPredicate predicate) {
        Optional<JsonElement> encoded = EntityPredicate.CODEC.encodeStart(ops, predicate).result();
        if (encoded.isEmpty() || !encoded.get().isJsonObject()) {
            return "matching entities";
        }

        JsonObject object = encoded.get().getAsJsonObject();
        for (var entry : object.entrySet()) {
            if (entry.getKey().endsWith("entity_type")) {
                String target = encodedReferences(entry.getValue(), "matching entities");
                return object.size() == 1 ? target : target + " with required traits";
            }
        }
        return object.isEmpty() ? "any entity" : "entities with required traits";
    }

    public static String damageTarget(DynamicOps<JsonElement> ops, DamageSourcePredicate predicate) {
        List<String> requirements = new ArrayList<>();
        for (var tag : predicate.tags()) {
            String target = holderSetName(tag.tag(), "specified damage");
            requirements.add(tag.expected() ? target : "not " + target);
        }
        predicate.directEntity().ifPresent(entity -> requirements.add("direct " + entityTarget(ops, entity)));
        predicate.sourceEntity().ifPresent(entity -> requirements.add("source " + entityTarget(ops, entity)));
        predicate.isDirect().ifPresent(direct -> requirements.add(direct ? "a direct hit" : "an indirect hit"));
        return requirements.isEmpty() ? "the required damage source" : String.join(", ", requirements);
    }

    public static String locationTarget(LocationPredicate predicate) {
        List<String> requirements = new ArrayList<>();
        predicate.dimension().ifPresent(dimension -> requirements.add(humanize(dimension.identifier())));
        predicate.biomes().ifPresent(biomes -> requirements.add(holderSetName(biomes, "required biome")));
        predicate.structures().ifPresent(structures -> requirements.add(holderSetName(structures, "required structure")));
        predicate.block().ifPresent(block -> requirements.add("near " + blockTarget(block)));
        predicate.smokey().ifPresent(smokey -> requirements.add(smokey ? "smoky" : "not smoky"));
        predicate.canSeeSky().ifPresent(canSeeSky -> requirements.add(canSeeSky ? "open to the sky" : "sheltered"));
        if (predicate.position().isPresent()) requirements.add("within the required coordinates");
        if (predicate.light().isPresent()) requirements.add("at the required light level");
        if (predicate.fluid().isPresent()) requirements.add("in the required fluid");
        return requirements.isEmpty() ? "the target location" : String.join(", ", requirements);
    }

    public static String holderSetName(HolderSet<?> holders, String fallback) {
        if (holders.unwrapKey().isPresent()) {
            return humanize(holders.unwrapKey().get().location()) + " tag";
        }

        List<String> names = holders.stream()
                .limit(3)
                .map(holder -> humanizeReference(holder.getRegisteredName()))
                .toList();
        if (names.isEmpty()) return fallback;
        String joined = String.join(" or ", names);
        return holders.size() > names.size()
                ? joined + " or " + (holders.size() - names.size()) + " more"
                : joined;
    }

    public static String encodedReferences(JsonElement element, String fallback) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return humanizeReference(element.getAsString());
        }
        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement value : element.getAsJsonArray()) {
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    values.add(humanizeReference(value.getAsString()));
                }
                if (values.size() == 3) break;
            }
            return values.isEmpty() ? fallback : String.join(" or ", values);
        }
        return fallback;
    }

    public static JsonObject nestedJson(JsonObject json, String field) {
        return json.has(field) && json.get(field).isJsonObject()
                ? json.getAsJsonObject(field)
                : new JsonObject();
    }

    public static JsonObject indexedJson(JsonObject json, String field, int index) {
        if (!json.has(field) || !json.get(field).isJsonArray()) return new JsonObject();

        JsonArray values = json.getAsJsonArray(field);
        return index < values.size() && values.get(index).isJsonObject()
                ? values.get(index).getAsJsonObject()
                : new JsonObject();
    }

    public static List<String> descriptionLines(List<String> description, int descriptionWidth, int maxDescriptionLines) {
        if (description.isEmpty()) {
            return List.of("No description provided.");
        }

        List<String> lines = new ArrayList<>();
        for (String paragraph : description) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                if (word.isEmpty()) continue;
                if (!line.isEmpty() && line.length() + word.length() + 1 > descriptionWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        if (lines.isEmpty()) {
            return List.of("No description provided.");
        }
        if (lines.size() <= maxDescriptionLines) {
            return lines;
        }
        return abbreviated(new ArrayList<>(lines.subList(0, maxDescriptionLines)), descriptionWidth);
    }

    public static List<String> abbreviated(List<String> lines, int descriptionWidth) {
        int last = lines.size() - 1;
        String value = lines.get(last);
        if (value.length() >= descriptionWidth) {
            value = value.substring(0, descriptionWidth - 1);
        }
        lines.set(last, value + "…");
        return lines;
    }
}
