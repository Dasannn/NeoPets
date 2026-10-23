package dev.dasannn.neopets.plugin.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class MenuLayoutJson {
    private MenuLayoutJson() {
    }

    static void write(Path path, MenuLayout layout, List<Integer> tooltips) throws IOException {
        Files.createDirectories(path.getParent());
        var json = new StringBuilder();
        json.append("{\n  \"title\": ").append(string(layout.title()))
                .append(",\n  \"rows\": ").append(layout.rows()).append(",\n  \"slots\": {\n");
        int index = 0;
        for (var entry : layout.items().entrySet()) {
            if (index++ > 0) {
                json.append(",\n");
            }
            MenuItem item = entry.getValue();
            json.append("    \"").append(entry.getKey()).append("\": {\"material\": ")
                    .append(string(item.material())).append(", \"name\": ").append(string(item.name()));
            if (!item.lore().isEmpty()) {
                json.append(", \"lore\": [");
                for (int line = 0; line < item.lore().size(); line++) {
                    if (line > 0) {
                        json.append(", ");
                    }
                    json.append(string(item.lore().get(line)));
                }
                json.append(']');
            }
            if (item.glint()) {
                json.append(", \"glint\": true");
            }
            if (item.amount() != 1) {
                json.append(", \"amount\": ").append(item.amount());
            }
            json.append('}');
        }
        json.append("\n  },\n  \"tooltips\": [");
        for (int indexTooltip = 0; indexTooltip < tooltips.size(); indexTooltip++) {
            if (indexTooltip > 0) {
                json.append(", ");
            }
            json.append(tooltips.get(indexTooltip));
        }
        json.append("]\n}\n");
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }

    private static String string(String value) {
        var escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character);
            }
        }
        return escaped.append('"').toString();
    }
}
