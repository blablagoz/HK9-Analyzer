package com.blablagoz.hk9analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Framing inferred from captures: AB/EA + unsigned big-endian length + body. */
public final class PacketDecoder {
    private byte[] pending = new byte[0];
    public void reset() { pending = new byte[0]; }
    public List<byte[]> feed(byte[] incoming) {
        List<byte[]> frames = new ArrayList<>();
        if (incoming == null || incoming.length == 0) return frames;
        if (pending.length + incoming.length > 8192) pending = new byte[0];
        byte[] all = Arrays.copyOf(pending, pending.length + incoming.length);
        System.arraycopy(incoming, 0, all, pending.length, incoming.length);
        int cursor = 0;
        while (cursor < all.length) {
            int first = all[cursor] & 255;
            if (first != 0xAB && first != 0xEA) { cursor++; continue; }
            if (all.length - cursor < 3) break;
            int size = ((all[cursor + 1] & 255) << 8) | (all[cursor + 2] & 255);
            if (size < 3 || size > 4096) { cursor++; continue; }
            if (all.length - cursor < size + 3) break;
            frames.add(Arrays.copyOfRange(all, cursor, cursor + size + 3));
            cursor += size + 3;
        }
        pending = Arrays.copyOfRange(all, cursor, all.length);
        return frames;
    }
    public static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02X ", b & 255));
        return value.toString().trim();
    }
    public static byte[] fromHex(String text) {
        String compact = text.replace("-", "").replace(" ", "").trim();
        if (compact.length() % 2 != 0 || !compact.matches("[0-9a-fA-F]*"))
            throw new IllegalArgumentException("Geçersiz hex verisi");
        byte[] bytes = new byte[compact.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(compact.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
    public static int command(byte[] frame) { return frame.length >= 6 ? frame[4] & 255 : -1; }
    public static String label(byte[] frame) {
        return switch (command(frame)) {
            case 0x20 -> "Sürüm yanıtı adayı";
            case 0x51 -> "Aktivite kaydı adayı";
            case 0x52 -> "Zamanlı kayıt adayı";
            case 0x91 -> "Pil yanıtı adayı";
            case 0xAA -> "Cihaz kimliği adayı";
            default -> "Saatten gelen paket";
        };
    }
}
