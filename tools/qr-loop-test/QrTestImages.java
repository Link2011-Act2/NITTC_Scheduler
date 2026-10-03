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
}
