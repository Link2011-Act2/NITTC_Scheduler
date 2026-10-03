import jp.linkserver.nittcsc.logic.QrShareCollector;
import jp.linkserver.nittcsc.logic.QrShareException;
import jp.linkserver.nittcsc.logic.QrShareFailure;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Run generated test frames against the actual app Collector, without changing it. */
class QrTestProtocolCheck {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        int cursor = 0, cases = 0;
        while (cursor < lines.size()) {
            String[] header = lines.get(cursor++).split(" ");
            String mode = header[0];
            int count = Integer.parseInt(header[1]), length = Integer.parseInt(header[2]);
            List<String> frames = lines.subList(cursor, cursor + length);
            cursor += length;
            QrShareCollector collector = new QrShareCollector();
            if (mode.equals("duplicate")) {
                collector.add(frames.get(0));
                expectDamaged(collector, frames.get(1));
            } else if (mode.equals("hash")) {
                for (int i = 0; i < count - 1; i++) {
                    if (collector.add(frames.get(i)) != null || collector.getReceived() != i + 1)
                        throw new AssertionError("Hash error must wait until final fragment");
                }
                expectDamaged(collector, frames.get(count - 1));
            } else {
                // Start at the end, read a duplicate, then receive the rest out of order.
                collector.add(frames.get(count - 1));
                collector.add(frames.get(count - 1));
                if (collector.getReceived() != 1) throw new AssertionError("Duplicate counted twice");
                String json = null;
                for (int i = count - 2; i >= 0; i--) json = collector.add(frames.get(i));
                if (collector.getReceived() != count || json == null || !json.contains("\"format\":\"SKTTP/QR\""))
                    throw new AssertionError("Normal case did not complete");
            }
            cases++;
        }
        if (cases != 12) throw new AssertionError("Expected 12 cases");
        System.out.println("PASS: actual app Collector, all 12 cases; successful shuffled/duplicate reception, final SHA-256 rejection, conflicting fragment rejection.");
    }
    private static void expectDamaged(QrShareCollector collector, String frame) {
        try { collector.add(frame); }
        catch (QrShareException error) {
            if (error.getFailure() == QrShareFailure.DAMAGED) return;
            throw new AssertionError("Unexpected error: " + error.getFailure());
        }
        throw new AssertionError("Expected DAMAGED");
    }
}
