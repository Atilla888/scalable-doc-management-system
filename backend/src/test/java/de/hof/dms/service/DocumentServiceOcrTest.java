package de.hof.dms.service;

import org.junit.jupiter.api.Test;

import static de.hof.dms.service.DocumentService.OCR_NOT_REQUIRED;
import static de.hof.dms.service.DocumentService.OCR_PENDING;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentServiceOcrTest {

    @Test
    void pdfAndScanAndImagesRequireOcr() {
        assertThat(DocumentService.resolveOcrStatus("application/pdf", "report")).isEqualTo(OCR_PENDING);
        assertThat(DocumentService.resolveOcrStatus("image/png", "photo")).isEqualTo(OCR_PENDING);
        assertThat(DocumentService.resolveOcrStatus("application/octet-stream", "scan")).isEqualTo(OCR_PENDING);
    }

    @Test
    void otherTypesDoNotRequireOcr() {
        assertThat(DocumentService.resolveOcrStatus("text/plain", "note")).isEqualTo(OCR_NOT_REQUIRED);
        assertThat(DocumentService.resolveOcrStatus("application/vnd.ms-excel", "spreadsheet"))
                .isEqualTo(OCR_NOT_REQUIRED);
    }
}
