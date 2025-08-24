package com.byvs.backend.service.util;

import org.springframework.web.multipart.MultipartFile;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class ImageCompressionUtil {

    public static byte[] compressAndSave(MultipartFile file, float compressionQuality, int maxWidth, int maxHeight) throws IOException {
        if (compressionQuality < 0 || compressionQuality > 1) {
            throw new IllegalArgumentException("Compression quality must be between 0 and 1");
        }
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IOException("Unsupported image format");
        }
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        int newWidth = originalWidth;
        int newHeight = originalHeight;

        if (originalWidth > maxWidth || originalHeight > maxHeight) {
            double widthRatio = (double) maxWidth / originalWidth;
            double heightRatio = (double) maxHeight / originalHeight;
            double ratio = Math.min(widthRatio, heightRatio);

            newWidth = (int) (originalWidth * ratio);
            newHeight = (int) (originalHeight * ratio);
        }
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaledImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        graphics.dispose();

        String formatName = getImageFormat(file.getOriginalFilename());
        ByteArrayOutputStream compressedOutputStream = new ByteArrayOutputStream();

        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(compressedOutputStream)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);

            if (!writers.hasNext()) {
                throw new IOException("No writer found for format: " + formatName);
            }

            ImageWriter writer = writers.next();
            writer.setOutput(imageOutputStream);

            ImageWriteParam writeParam = writer.getDefaultWriteParam();

            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(compressionQuality);
            }

            writer.write(null, new IIOImage(scaledImage, null, null), writeParam);
            writer.dispose();
        }

        return compressedOutputStream.toByteArray();
    }

    private static String getImageFormat(String filename) {
        if (filename != null) {
            String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
            if (extension.equals("jpg") || extension.equals("jpeg")) {
                return "jpeg";
            } else if (extension.equals("png")) {
                return "png";
            } else if (extension.equals("pdf")) {
                return "pdf";
            }
        }
        return "jpeg";
    }

    public static boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    public static boolean isImageSizeValid(MultipartFile file, long maxSizeBytes) {
        return file.getSize() <= maxSizeBytes;
    }
}
