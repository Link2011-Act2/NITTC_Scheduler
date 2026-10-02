package jp.linkserver.nittcsc.qr

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO
import org.w3c.dom.Node

class QrMonochromeGifTest {
    @Test fun preservesRandomPixelsThroughCodeWidthChangesAndDictionaryResets() {
        val random = Random(187)
        val width = 1024
        val height = 1256
        val frames = listOf(
            IntArray(width * height) { if (random.nextBoolean()) BLACK else WHITE },
            IntArray(width * height) { if (it % width < width / 2) BLACK else WHITE }
        )
        val output = ByteArrayOutputStream()
        val encoder = QrMonochromeGif(output, width, height, 200)
        frames.forEach(encoder::add)
        encoder.finish()
        val bytes = output.toByteArray()
        validateQrGif(bytes)
        withReader(bytes) { reader ->
            assertEquals(2, reader.getNumImages(true))
            frames.forEachIndexed { i, expected ->
                val image = reader.read(i)
                assertArrayEquals(expected, image.getRGB(0, 0, width, height, null, 0, width))
                val tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0")
                val control = child(tree, "GraphicControlExtension")
                assertEquals("20", control.attributes.getNamedItem("delayTime").nodeValue)
                assertEquals("doNotDispose", control.attributes.getNamedItem("disposalMethod").nodeValue)
            }
            val stream = reader.streamMetadata.getAsTree("javax_imageio_gif_stream_1.0")
            val palette = child(stream, "GlobalColorTable")
            assertEquals("2", palette.attributes.getNamedItem("sizeOfGlobalColorTable").nodeValue)
        }
    }

    @Test fun finalCodeWidthBoundaryAndSolidFramesDecode() {
        val random = Random(55)
        for (length in 1..160) {
            val pixels = IntArray(length) { if (random.nextBoolean()) BLACK else WHITE }
            val output = ByteArrayOutputStream()
            val encoder = QrMonochromeGif(output, length, 1, 200)
            encoder.add(pixels)
            encoder.add(IntArray(length) { WHITE })
            encoder.add(IntArray(length) { BLACK })
            encoder.finish()
            withReader(output.toByteArray()) { reader ->
                val image = reader.read(0)
                assertArrayEquals("length=$length", pixels, image.getRGB(0, 0, length, 1, null, 0, length))
                assertEquals(WHITE, reader.read(1).getRGB(length - 1, 0))
                assertEquals(BLACK, reader.read(2).getRGB(length - 1, 0))
            }
        }
    }

    private fun child(parent: Node, name: String): Node = (0 until parent.childNodes.length)
        .map { parent.childNodes.item(it) }.first { it.nodeName == name }

    private fun withReader(bytes: ByteArray, block: (javax.imageio.ImageReader) -> Unit) {
        val stream = ImageIO.createImageInputStream(bytes.inputStream())
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        try { reader.input = stream; block(reader) }
        finally { reader.dispose(); stream.close() }
    }

    companion object {
        private const val BLACK = -16777216
        private const val WHITE = -1
    }
}
