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

/**
 * Validates uploaded files before they are stored. Enforces the configured
 * size limit, an allow-list of content types, a matching file extension, a safe
 * file name, and magic-byte signatures (including DOCX ZIP structure) so the
 * declared content type cannot be spoofed.
 */
@Service
public class UploadValidationService {

    private static final Map<String, Set<String>> EXTENSIONS_BY_CONTENT_TYPE =
            Map.of(
                    "application/pdf", Set.of("pdf"),
                    "image/png", Set.of("png"),
                    "image/jpeg", Set.of("jpg", "jpeg"),
                    "text/plain", Set.of("txt"),
                    "application/xml", Set.of("xml"),
                    "application/json", Set.of("json"),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            Set.of("docx"));

    private final Set<String> allowedContentTypes;
    private final long maxFileSize;

    /**
     * Builds the validator from configured limits: normalizes the allowed
     * content-type allow-list and resolves the maximum file size to bytes.
     *
     * @param allowedContentTypes configured list of permitted MIME types
     * @param maxFileSize configured maximum upload size
     */
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

    /**
     * Validates a multipart upload against size, content-type, extension, file
     * name, and magic-byte signature rules.
     *
     * @return the sanitized file name and normalized content type
     * @throws ApiException with 400/413/415 describing which rule the upload failed
     */
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

    /**
     * Sanitizes and validates the upload's file name, rejecting path separators,
     * null bytes, quotes, and control characters, and requiring an extension.
     *
     * @return the trimmed, safe file name
     * @throws ApiException with 400/415 if the name is missing or unsafe
     */
    private static String validateFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File name is required");
        }
        String fileName = originalFilename.trim();
        if (fileName.contains("/") || fileName.contains("\\") || fileName.indexOf('\0') >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File name contains invalid characters");
        }
        // Reject quotes and control characters so the name can never break out of the
        // Content-Disposition header quoting on download (header spoofing / injection).
        for (int index = 0; index < fileName.length(); index++) {
            char character = fileName.charAt(index);
            if (character == '"' || character < 0x20 || character == 0x7f) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "File name contains invalid characters");
            }
        }
        if (extensionOf(fileName).isBlank()) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File extension is required");
        }
        return fileName;
    }

    /**
     * Checks the file's leading magic bytes (or ZIP structure for DOCX) against
     * the declared content type so the type cannot be spoofed.
     *
     * @return {@code true} if the content matches the declared type
     * @throws ApiException with 400 if the file cannot be read
     */
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
            case "text/plain", "application/xml", "application/json" ->
                    header.length > 0 && containsNoNullByte(header);
            default -> false;
        };
    }

    /**
     * Verifies a file is a real DOCX by confirming its ZIP contains both
     * {@code [Content_Types].xml} and {@code word/document.xml}.
     *
     * @return {@code true} if both required ZIP entries are present
     */
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

    /** Returns whether {@code value} begins with the given byte {@code prefix}. */
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

    /** Returns whether the byte array contains no null byte (a heuristic for text content). */
    private static boolean containsNoNullByte(byte[] value) {
        for (byte item : value) {
            if (item == 0) {
                return false;
            }
        }
        return true;
    }

    /** Returns the lower-cased file extension of {@code fileName}, or an empty string if none. */
    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Normalizes a content type by stripping parameters and lower-casing the base type. */
    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameter = contentType.indexOf(';');
        String baseType = parameter >= 0 ? contentType.substring(0, parameter) : contentType;
        return baseType.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns a human-readable content type for error messages, using "missing" when blank. */
    private static String displayContentType(String contentType) {
        return contentType.isBlank() ? "missing" : contentType;
    }

    /**
     * The sanitized file name and normalized content type of a validated upload.
     *
     * @param fileName the sanitized, safe file name
     * @param contentType the normalized, allow-listed content type
     */
    public record ValidatedUpload(String fileName, String contentType) {}
}
