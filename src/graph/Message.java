package graph;

import java.nio.charset.StandardCharsets;
import java.util.Date;

public final class Message {
    public final byte[] data;
    public final String asText;
    public final double asDouble;
    public final Date date;

    public Message(byte[] data) {
        this.data = data == null ? new byte[0] : data.clone();
        this.asText = new String(this.data, StandardCharsets.UTF_8);
        this.asDouble = parseDouble(this.asText);
        this.date = new Date();
    }

    public Message(String text) {
        this(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    public Message(double value) {
        this(Double.toString(value));
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
