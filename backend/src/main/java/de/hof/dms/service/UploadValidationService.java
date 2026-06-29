package de.hof.dms.service;

import de.hof.dms.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

@Service
public class UploadValidationService {

    private static final Map<String, Set<String>> EXTENSIONS_BY_CONTENT_TYPE =
            Map.of(
                    "application/pdf", Set.of("pdf"),
                    "image/png", Set.of("png"),
                    "image/jpeg", Set.of("jpg", "jpeg"),
                    "text/plain", Set.of("txt"),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            Set.of("docx"));

    private final Set<String> allowedContentTypes;
    private final long maxFileSize;

    public UploadValidationService(
            @Value("${dms.upload.allowed-content-types}") String[] allowedContentTypes,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.allowedContentTypes =
                Arrays.stream(allowedContentTypes)
                        .map(UploadValidationService::normalizeContentType)
                        .filter(value -> !value.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
        this.maxFileSize = maxFileSize.toBytes();
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is required");
        }
        if (file.getSize() > maxFileSize) {
            throw new ApiException(
                    HttpStatus.valueOf(413),
                    "File exceeds the configured upload limit of " + maxFileSize + " bytes");
        }

        String fileName = validateFileName(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        if (!allowedContentTypes.contains(contentType)) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File type is not allowed: " + displayContentType(contentType));
        }

        String extension = extensionOf(fileName);
        Set<String> allowedExtensions = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (allowedExtensions == null || !allowedExtensions.contains(extension)) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File extension does not match content type " + contentType);
        }

        if (!signatureMatches(file, contentType)) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File content does not match declared content type " + contentType);
        }
        return new ValidatedUpload(fileName, contentType);
    }

    private static String validateFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File name is required");
        }
        String fileName = originalFilename.trim();
        if (fileName.contains("/") || fileName.contains("\\") || fileName.indexOf('\0') >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File name contains invalid characters");
        }
        if (extensionOf(fileName).isBlank()) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File extension is required");
        }
        return fileName;
    }

    private static boolean signatureMatches(MultipartFile file, String contentType) {
        byte[] header;
        try (var input = file.getInputStream()) {
            header = input.readNBytes(16);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to inspect uploaded file");
        }

        return switch (contentType) {
            case "application/pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "image/png" ->
                    startsWith(
                            header,
                            new byte[] {
                                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                            });
            case "image/jpeg" ->
                    header.length >= 3
                            && header[0] == (byte) 0xFF
                            && header[1] == (byte) 0xD8
                            && header[2] == (byte) 0xFF;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    isDocx(file);
            case "text/plain" -> header.length > 0 && containsNoNullByte(header);
            default -> false;
        };
    }

    private static boolean isDocx(MultipartFile file) {
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        int entriesChecked = 0;

        try (var input = file.getInputStream(); var zip = new ZipInputStream(input)) {
            var entry = zip.getNextEntry();
            while (entry != null && entriesChecked < 2048) {
                String name = entry.getName();
                hasContentTypes |= "[Content_Types].xml".equals(name);
                hasDocument |= "word/document.xml".equals(name);
                if (hasContentTypes && hasDocument) {
                    return true;
                }
                entriesChecked++;
                entry = zip.getNextEntry();
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNoNullByte(byte[] value) {
        for (byte item : value) {
            if (item == 0) {
                return false;
            }
        }
        return true;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameter = contentType.indexOf(';');
        String baseType = parameter >= 0 ? contentType.substring(0, parameter) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    private static String displayContentType(String contentType) {
        return contentType.isBlank() ? "missing" : contentType;
    }

    public record ValidatedUpload(String fileName, String contentType) {}
}
