package de.hof.dms.service;

import de.hof.dms.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidationServiceTest {

    private final UploadValidationService validator =
            new UploadValidationService(
                    new String[] {
                        "application/pdf",
                        "image/png",
                        "image/jpeg",
                        "text/plain",
                        "application/xml",
                        "application/json",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    },
                    DataSize.ofKilobytes(10));

    @Test
    void acceptsPdfWhenExtensionTypeAndSignatureMatch() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "report.pdf",
                        "application/pdf",
                        "%PDF-1.4 content".getBytes(StandardCharsets.US_ASCII));

        UploadValidationService.ValidatedUpload result = validator.validate(file);

        assertThat(result.fileName()).isEqualTo("report.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void rejectsExecutableRenamedAsPdf() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "malware.pdf",
                        "application/pdf",
                        "MZ executable".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void acceptsXmlWhenExtensionTypeAndSignatureMatch() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "record.xml",
                        "application/xml",
                        "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8));

        UploadValidationService.ValidatedUpload result = validator.validate(file);

        assertThat(result.fileName()).isEqualTo("record.xml");
        assertThat(result.contentType()).isEqualTo("application/xml");
    }

    @Test
    void acceptsJsonWhenExtensionTypeAndSignatureMatch() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "record.json",
                        "application/json",
                        "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));

        UploadValidationService.ValidatedUpload result = validator.validate(file);

        assertThat(result.fileName()).isEqualTo("record.json");
        assertThat(result.contentType()).isEqualTo("application/json");
    }

    @Test
    void rejectsBinaryContentDeclaredAsJson() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "payload.json",
                        "application/json",
                        new byte[] {0x7B, 0x00, 0x01, 0x02});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsFileNameWithDoubleQuote() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "re\"port.pdf",
                        "application/pdf",
                        "%PDF-1.4 content".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsFileNameWithControlCharacter() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "report\n.pdf",
                        "application/pdf",
                        "%PDF-1.4 content".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsExtensionThatDoesNotMatchContentType() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "image.txt",
                        "image/png",
                        new byte[] {
                            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
                        });

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void rejectsDisallowedContentType() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "archive.zip",
                        "application/zip",
                        new byte[] {0x50, 0x4B, 0x03, 0x04});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsPathTraversalInFileName() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "../report.pdf",
                        "application/pdf",
                        "%PDF-1.4 content".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid characters");
    }

    @Test
    void rejectsFileAboveConfiguredLimit() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "large.txt",
                        "text/plain",
                        new byte[11 * 1024]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("upload limit");
    }

    @Test
    void acceptsDocxWithRequiredOfficeEntries() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "report.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        zipBytes("[Content_Types].xml", "word/document.xml"));

        UploadValidationService.ValidatedUpload result = validator.validate(file);

        assertThat(result.fileName()).isEqualTo("report.docx");
    }

    @Test
    void rejectsGenericZipRenamedAsDocx() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "archive.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        zipBytes("payload.txt"));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not match");
    }

    private static byte[] zipBytes(String... entryNames) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (String entryName : entryNames) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write("content".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
