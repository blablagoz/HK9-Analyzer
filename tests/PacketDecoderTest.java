import com.blablagoz.hk9analyzer.PacketDecoder;
import java.util.Arrays;

public class PacketDecoderTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        byte[] a = PacketDecoder.fromHex("AB-00-05-FF-91-80-00-5F");
        byte[] b = PacketDecoder.fromHex("EA-00-03-00-0B-01");
        PacketDecoder decoder = new PacketDecoder();
        check(decoder.feed(Arrays.copyOfRange(a, 0, 2)).isEmpty(), "split header");
        check(decoder.feed(Arrays.copyOfRange(a, 2, 6)).isEmpty(), "split body");
        check(Arrays.equals(decoder.feed(Arrays.copyOfRange(a, 6, 8)).get(0), a), "fragment reassembly");
        byte[] joined = new byte[a.length + b.length + 2];
        joined[0] = 1; joined[1] = 2;
        System.arraycopy(a, 0, joined, 2, a.length);
        System.arraycopy(b, 0, joined, 2 + a.length, b.length);
        check(decoder.feed(joined).size() == 2, "noise plus multiple frames");
        check(decoder.feed(PacketDecoder.fromHex("AB-FF-FF-AB-00-03-FF-20-80")).size() == 1, "invalid length resync");
        decoder.feed(new byte[]{(byte)0xAB}); decoder.reset();
        check(decoder.feed(b).size() == 1, "session reset");
        check(PacketDecoder.command(a) == 0x91, "command position");
        check(PacketDecoder.hex(a).equals("AB 00 05 FF 91 80 00 5F"), "hex export");
        boolean rejected = false;
        try { PacketDecoder.fromHex("ABC"); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "malformed import");
        System.out.println("PacketDecoder: all checks passed");
    }
}
