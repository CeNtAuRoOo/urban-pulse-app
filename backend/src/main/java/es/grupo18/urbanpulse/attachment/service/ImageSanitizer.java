package es.grupo18.urbanpulse.attachment.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

import es.grupo18.urbanpulse.attachment.model.ContentType;

/**
 * Sanitizes uploaded photographs by decoding and re-encoding them.
 *
 * Original image metadata, including EXIF information, is not
 * copied into the resulting image.
 *
 * Supported formats: JPEG, PNG and WebP.
 */
@Component
public class ImageSanitizer {

    // Prevent excessively large images from consuming too much memory.
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;

    /**
     * Decodes and re-encodes an image, discarding its original metadata.
     *
     * @param originalBytes original image bytes
     * @param contentType   detected image format
     * @return sanitized image bytes
     * @throws IOException if the image cannot be decoded or encoded
     */
    public byte[] sanitize(
            byte[] originalBytes,
            ContentType contentType) throws IOException {

        if (originalBytes == null || originalBytes.length == 0) {
            throw new IOException("Empty image.");
        }

        String format = getFormat(contentType);

        // 1. Decode the original image.
        BufferedImage originalImage;

        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(originalBytes))) {

            if (input == null) {
                throw new IOException("Could not read image.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format.");
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(input, false, true);

                // 2. Validate image dimensions before decoding.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0 ||
                        (long) width * height > MAX_IMAGE_PIXELS) {

                    throw new IOException("Image dimensions are invalid or too large.");
                }

                // 3. Verify that the decoded format matches the detected format.
                String detectedFormat = reader.getFormatName();

                if (!matchesFormat(format, detectedFormat)) {
                    throw new IOException("Image format mismatch.");
                }

                originalImage = reader.read(0);

            } finally {
                reader.dispose();
            }
        }

        if (originalImage == null) {
            throw new IOException("Could not decode image.");
        }

        // 4. JPEG does not support transparency.
        BufferedImage imageToEncode = originalImage;

        if ("jpeg".equals(format)) {

            imageToEncode = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            Graphics2D graphics = imageToEncode.createGraphics();

            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(
                        0,
                        0,
                        imageToEncode.getWidth(),
                        imageToEncode.getHeight());

                graphics.drawImage(originalImage, 0, 0, null);

            } finally {
                graphics.dispose();
            }
        }

        // 5. Find a writer capable of encoding the original format.
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);

        if (!writers.hasNext()) {
            throw new IOException(
                    "No image writer available for format: " + format);
        }

        ImageWriter writer = writers.next();

        // 6. Re-encode without copying original metadata.
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {

            if (imageOutput == null) {
                throw new IOException("Could not create image output.");
            }

            writer.setOutput(imageOutput);

            writer.write(
                    null,
                    new IIOImage(imageToEncode, null, null),
                    writer.getDefaultWriteParam());

            imageOutput.flush();

            return output.toByteArray();

        } finally {
            writer.dispose();
        }
    }

    /**
     * Maps the application's ContentType enum to ImageIO format names.
     */
    private String getFormat(ContentType contentType) throws IOException {

        if (contentType == null) {
            throw new IOException("Missing image content type.");
        }

        return switch (contentType) {
            case IMAGE_JPEG -> "jpeg";
            case IMAGE_PNG -> "png";
            case IMAGE_WEBP -> "webp";
        };
    }

    /**
     * Checks that the decoded format matches the expected format.
     */
    private boolean matchesFormat(String expected, String detected) {

        return switch (expected) {
            case "jpeg" -> detected.equalsIgnoreCase("JPEG")
                    || detected.equalsIgnoreCase("JPG");
            case "png" -> detected.equalsIgnoreCase("PNG");
            case "webp" -> detected.equalsIgnoreCase("WEBP");
            default -> false;
        };
    }
}