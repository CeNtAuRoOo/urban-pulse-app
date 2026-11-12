package es.grupo18.urbanpulse.attachment.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import es.grupo18.urbanpulse.attachment.model.ContentType;
import es.grupo18.urbanpulse.shared.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageSanitizerTest {
    private final ImageSanitizer sanitizer = new ImageSanitizer();

    @Test
    void jpegDecodesAndRemovesRealExifGpsIfd() throws Exception {
        byte[] jpeg = image("jpeg", 12, 9);
        // TIFF little endian: IFD0 GPSInfo pointer -> GPS IFD with GPSLatitudeRef=N.
        var tiff = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8).putShort((short) 1);
        tiff.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(26).putInt(0);
        tiff.putShort((short) 1).putShort((short) 1).putShort((short) 2).putInt(2).put((byte) 'N').put((byte) 0)
                .putShort((short) 0).putInt(0);
        var exif = new ByteArrayOutputStream();
        exif.write("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        exif.write(tiff.array());
        var marked = new ByteArrayOutputStream();
        marked.write(jpeg, 0, 2);
        marked.write(255);
        marked.write(225);
        marked.write((exif.size() + 2) >> 8);
        marked.write((exif.size() + 2) & 255);
        marked.write(exif.toByteArray());
        marked.write(jpeg, 2, jpeg.length - 2);
        byte[] source = marked.toByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(source))).isNotNull();
        assertThat(new String(source, StandardCharsets.ISO_8859_1)).contains("Exif");
        byte[] clean = sanitizer.sanitize(source, ContentType.IMAGE_JPEG);
        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        var decoded = ImageIO.read(new ByteArrayInputStream(clean));
        assertThat(decoded.getWidth()).isEqualTo(12);
        assertThat(decoded.getHeight()).isEqualTo(9);
        assertThat(sanitizer.detectContentType(clean)).isEqualTo(ContentType.IMAGE_JPEG);
    }

    @Test
    void pngPreservesTransparencyAndRemovesTextAndExifChunks() throws Exception {
        byte[] png = image("png", 12, 9);
        byte[] marked = insertChunk(png, "tEXt", "GPS\0private-coordinates".getBytes(StandardCharsets.US_ASCII));
        // Valid TIFF header and empty IFD for PNG eXIf.
        marked = insertChunk(marked, "eXIf", new byte[] { 73, 73, 42, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
        var before = ImageIO.read(new ByteArrayInputStream(marked));
        byte[] clean = sanitizer.sanitize(marked, ContentType.IMAGE_PNG);
        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("eXIf", "GPS", "private-coordinates",
                "tEXt");
        var decoded = ImageIO.read(new ByteArrayInputStream(clean));
        assertThat(decoded.getRGB(0, 0) >>> 24).isZero();
        assertThat(decoded.getRGB(1, 1) >>> 24).isEqualTo(128);
        assertThat(decoded.getWidth()).isEqualTo(before.getWidth());
        assertThat(decoded.getHeight()).isEqualTo(before.getHeight());
    }

    @Test
    void rejectsUnknownSpoofedCorruptTruncatedAndMismatchedImages() throws Exception {
        for (byte[] data : List.of(new byte[0], new byte[] { 1, 2, 3 }, "hello".getBytes(), "%PDF".getBytes(),
                "GIF89a".getBytes(), "RIFFxxxxWEBP".getBytes()))
            assertStatus(() -> sanitizer.detectContentType(data), 422);
        assertStatus(() -> sanitizer.sanitize(new byte[] { (byte) 255, (byte) 216, (byte) 255, (byte) 217 },
                ContentType.IMAGE_JPEG), 422);
        for (String format : List.of("jpeg", "png")) {
            byte[] data = image(format, 12, 9);
            ContentType type = sanitizer.detectContentType(data);
            assertStatus(() -> sanitizer.sanitize(Arrays.copyOf(data, data.length - 10), type), 422);
            assertStatus(() -> sanitizer.sanitize(data,
                    type == ContentType.IMAGE_PNG ? ContentType.IMAGE_JPEG : ContentType.IMAGE_PNG), 422);
        }
        byte[] corrupt = image("png", 12, 9);
        corrupt[29] ^= 1;
        assertStatus(() -> sanitizer.sanitize(corrupt, ContentType.IMAGE_PNG), 422);
        byte[] forged = new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0 };
        assertStatus(() -> sanitizer.sanitize(forged, ContentType.IMAGE_PNG), 422);
    }

    @Test
    void validatesDimensionsBeforeRasterAllocation() throws Exception {
        for (int[] dimensions : List.of(new int[] { 4001, 4000 }, new int[] { 8193, 1 }, new int[] { 1, 8193 })) {
            byte[] png = image("png", 1, 1);
            ByteBuffer.wrap(png, 16, 8).putInt(dimensions[0]).putInt(dimensions[1]);
            CRC32 crc = new CRC32();
            crc.update(png, 12, 17);
            ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
            assertStatus(() -> sanitizer.sanitize(png, ContentType.IMAGE_PNG), 422);
        }
    }

    @Test
    void acceptsExactInputByteLimitAndRejectsOneMore() throws Exception {
        byte[] png = image("png", 8, 8);
        for (int size : List.of(ImageSanitizer.MAX_BYTES - 1, ImageSanitizer.MAX_BYTES)) {
            // Ancillary private chunk with valid CRC, discarded by decoding/re-encoding.
            byte[] padded = insertChunk(png, "ruSt", new byte[size - png.length - 12]);
            assertThat(padded).hasSize(size);
            assertThat(sanitizer.sanitize(padded, ContentType.IMAGE_PNG).length).isLessThan(1024);
        }
        assertStatus(() -> sanitizer.detectContentType(new byte[ImageSanitizer.MAX_BYTES + 1]), 413);
    }

    @Test
    void writerCacheStopsAtStorageLimitBeforeAllocatingExcess() throws Exception {
        try (var stream = new ImageSanitizer.BoundedImageOutput(new ByteArrayOutputStream())) {
            stream.seek(ImageSanitizer.MAX_BYTES - 1);
            stream.write(1);
            assertStatus(() -> {
                try {
                    stream.write(2);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, 413);
        }
    }

    @Test
    void reencodedImageCannotExceedStorageLimit() throws Exception {
        // Palette PNG: 9 MB of random indices fit the input cap; RGB expansion does
        // not.
        var random = new Random(42);
        byte[] red = new byte[256], green = new byte[256], blue = new byte[256];
        random.nextBytes(red);
        random.nextBytes(green);
        random.nextBytes(blue);
        var palette = new java.awt.image.IndexColorModel(8, 256, red, green, blue);
        var image = new BufferedImage(3000, 3000, BufferedImage.TYPE_BYTE_INDEXED, palette);
        random.nextBytes(((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData());
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        assertThat(out.size()).isLessThan(ImageSanitizer.MAX_BYTES);
        assertStatus(() -> sanitizer.sanitize(out.toByteArray(), ContentType.IMAGE_PNG), 413);
    }

    @Test
    void filenamesAreUnicodeBoundedSafeAndPreservePhotographicExtensions() {
        assertThat(AttachmentService.sanitizeFilename("C:\\private\\a\r\n.jpg")).isEqualTo("a.jpg");
        assertThat(AttachmentService.sanitizeFilename("../../")).isEqualTo("attachment");
        assertThat(AttachmentService.sanitizeFilename(null)).isEqualTo("attachment");
        assertThat(AttachmentService.sanitizeFilename("..")).isEqualTo("attachment");
        assertThat(AttachmentService.sanitizeFilename("\u202e\u0000\r\n")).isEqualTo("attachment");
        for (String extension : List.of(".jpg", ".jpeg", ".png")) {
            String result = AttachmentService.sanitizeFilename("🚦".repeat(130) + extension);
            assertThat(result.codePointCount(0, result.length())).isEqualTo(128);
            assertThat(result).endsWith(extension);
        }
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        var image = new BufferedImage(width, height,
                format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        if (width > 1 && height > 1)
            image.setRGB(1, 1, 0x80123456);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] insertChunk(byte[] png, String type, byte[] data) throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(png, 0, 33);
        var chunk = new DataOutputStream(out);
        chunk.writeInt(data.length);
        chunk.writeBytes(type);
        chunk.write(data);
        CRC32 crc = new CRC32();
        crc.update(type.getBytes(StandardCharsets.US_ASCII));
        crc.update(data);
        chunk.writeInt((int) crc.getValue());
        out.write(png, 33, png.length - 33);
        return out.toByteArray();
    }

    private static void assertStatus(Runnable action, int expected) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status().value()).isEqualTo(expected));
    }
}
