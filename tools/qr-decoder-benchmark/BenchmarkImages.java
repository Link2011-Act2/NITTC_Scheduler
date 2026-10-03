import jp.linkserver.nittcsc.qr.QrImageEncoding;
import jp.linkserver.nittcsc.logic.QrShareCodec;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Use the app's actual encoder and fragment sizes; no alternate QR generator. */
public class BenchmarkImages {
    static void save(Path root, String name, String text) throws Exception {
        var matrix = QrImageEncoding.INSTANCE.matrix(text, 0);
        int n = matrix.getWidth();
        var image = new BufferedImage(n, n, BufferedImage.TYPE_BYTE_GRAY);
        for (int y=0; y<n; y++) for (int x=0; x<n; x++)
            image.setRGB(x,y,matrix.get(x,y) ? 0xff000000 : 0xffffffff);
        ImageIO.write(image,"png",root.resolve(name+".png").toFile());
        Files.writeString(root.resolve(name+".txt"),text);
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]); Files.createDirectories(root);
        for (int bytes: new int[]{200,600,1600}) for (int seed=0;seed<2;seed++) {
            byte[] data=new byte[bytes]; new Random(42+seed).nextBytes(data);
            String text="SKTTP/QR:2:"+"a".repeat(32)+":"+"b".repeat(64)+":16:0:"+
                Base64.getEncoder().encodeToString(data);
            save(root,"b"+bytes+"s"+seed,text);
        }
        Random random=new Random(123); StringBuilder noise=new StringBuilder();
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for(int i=0;i<4096;i++) noise.append(alphabet.charAt(random.nextInt(alphabet.length())));
        String json="{\"format\":\"SKTTP/QR\",\"version\":2,\"year\":2026,\"label\":\"benchmark\",\"created\":0,\"semesterStart\":[10,1],\"sections\":[\"NOTES\"],\"notes\":[[\"2026-10-03\",0,\""+noise+"\"]]}";
        var transfer=QrShareCodec.INSTANCE.create(json);
        transfer=transfer.copy(transfer.getCompressed(),"c".repeat(32),transfer.getDigest());
        var frames=transfer.cameraFrames(200);
        for(int i=0;i<frames.size();i++) save(root,"stream"+i,frames.get(i));
        Files.writeString(root.resolve("stream-count.txt"),Integer.toString(frames.size()));
        Files.writeString(root.resolve("stream-json.txt"),json);
    }
}
