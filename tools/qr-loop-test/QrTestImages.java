import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Generate the same M / four-module-margin QR codes as QrImageEncoding. */
class QrTestImages {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--verify-symbols")) {
            verifySymbols(Path.of(args[1]));
            return;
        }
        List<String> frames = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        List<String> images = new ArrayList<>();
        for (String frame : frames) {
            BitMatrix matrix = new QRCodeWriter().encode(frame, BarcodeFormat.QR_CODE, 0, 0,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
            int width = matrix.getWidth();
            int scanWidth = width * 4;
            LuminanceSource source = new LuminanceSource(scanWidth, scanWidth) {
                public byte[] getRow(int y, byte[] row) {
                    if (row == null || row.length < scanWidth) row = new byte[scanWidth];
                    for (int x = 0; x < scanWidth; x++) row[x] = (byte)(matrix.get(x / 4, y / 4) ? 0 : 255);
                    return row;
                }
                public byte[] getMatrix() {
                    byte[] pixels = new byte[scanWidth * scanWidth];
                    for (int y = 0; y < scanWidth; y++) System.arraycopy(getRow(y, null), 0, pixels, y * scanWidth, scanWidth);
                    return pixels;
                }
            };
            String decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)),
                Map.of(DecodeHintType.PURE_BARCODE, true)).getText();
            if (!frame.equals(decoded)) throw new IllegalStateException("QR round trip failed");
            String[] fields = decoded.split(":", 7);
            if (Base64.getDecoder().decode(fields[6]).length != 200) throw new IllegalStateException("Wrong fragment size");
            // Keep images at their exact module size; browser scales with nearest-neighbour filtering.
            BufferedImage image = new BufferedImage(width, width, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < width; y++) for (int x = 0; x < width; x++)
                image.setRGB(x, y, matrix.get(x, y) ? 0xff000000 : 0xffffffff);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            images.add("\"data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()) + "\"");
        }
        Files.writeString(Path.of(args[1]), "[" + String.join(",", images) + "]", StandardCharsets.UTF_8);
        System.out.println("Verified " + frames.size() + " QR decodes and 200-byte fragments (including intentional error cases).");
    }

    /** Decode the browser encoder's actual module matrices with the independent ZXing reader. */
    private static void verifySymbols(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int cursor = 0, count = 0;
        while (cursor < lines.size()) {
            String[] header = lines.get(cursor++).split(" ");
            String expected = new String(Base64.getDecoder().decode(header[0]), StandardCharsets.UTF_8);
            int size = Integer.parseInt(header[1]), width = (size + 8) * 4;
            int[] pixels = new int[width * width];
            Arrays.fill(pixels, 0xffffffff);
            for (int y = 0; y < size; y++) {
                String row = lines.get(cursor++);
                if (row.length() != size) throw new AssertionError("Invalid row width");
                for (int x = 0; x < size; x++) if (row.charAt(x) == '1') {
                    for (int dy = 0; dy < 4; dy++) for (int dx = 0; dx < 4; dx++)
                        pixels[((y + 4) * 4 + dy) * width + (x + 4) * 4 + dx] = 0xff000000;
                }
            }
            Result result;
            try {
                result = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, width, pixels))),
                    Map.of(DecodeHintType.PURE_BARCODE, true));
            } catch (ReaderException error) {
                throw new AssertionError("Browser symbol " + (count + 1) + " (" + size + " modules) failed to decode", error);
            }
            if (!expected.equals(result.getText())) throw new AssertionError("Browser QR round trip failed");
            if (!"M".equals(result.getResultMetadata().get(ResultMetadataType.ERROR_CORRECTION_LEVEL)))
                throw new AssertionError("Expected M correction");
            count++;
        }
        if (count != 48) throw new AssertionError("Expected 48 browser symbols");
        System.out.println("PASS: 48 browser-generated symbols decoded with ZXing; exact frame text and M correction.");
    }
}
