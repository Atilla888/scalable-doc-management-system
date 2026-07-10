/**
 * @module pages/DocumentDetail
 * Document detail page: shows metadata, a live-updating OCR badge (polled while
 * OCR runs), and an authenticated download action.
 */
import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getDocument, downloadDocument } from "../api/documents";
import { ApiError } from "../api/client";
import useApiResource from "../hooks/useApiResource";
import OcrStatusBadge from "../components/OcrStatusBadge";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";

const ACTIVE_OCR = new Set(["pending", "processing"]);

/**
 * Formats an ISO date/time string for display, falling back gracefully.
 * @param {string} [value] Date value.
 * @returns {string} Localized string, the raw value, or "—".
 */
function formatDate(value) {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

/**
 * Formats a byte count as B/KB/MB.
 * @param {number} [bytes] Size in bytes.
 * @returns {string} Human-readable size, or "—" when null/undefined.
 */
function formatBytes(bytes) {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * A single label/value row in the metadata panel.
 * @param {Object} props
 * @param {string} props.label Field label.
 * @param {*} [props.value] Field value; renders "—" when nullish.
 * @returns {JSX.Element}
 */
const MetadataRow = ({ label, value }) => (
  <div className="grid grid-cols-[160px_minmax(0,1fr)] gap-4 py-2 text-sm">
    <span className="text-text-secondary">{label}</span>
    <span className="text-text">{value ?? "—"}</span>
  </div>
);

/**
 * Document detail page for the `:id` route.
 * @returns {JSX.Element|null}
 */
const DocumentDetail = () => {
  const { id } = useParams();
  const { data, error, loading, reload } = useApiResource(
    (signal) => getDocument(id, signal),
    [id],
  );

  const [downloadError, setDownloadError] = useState(null);
  const [downloading, setDownloading] = useState(false);
  const pollRef = useRef(null);

  // Poll metadata while OCR is still running so the badge updates live.
  useEffect(() => {
    const ocr = (data?.ocrStatus || "").toLowerCase();
    if (data && ACTIVE_OCR.has(ocr)) {
      pollRef.current = setInterval(reload, 5000);
      return () => clearInterval(pollRef.current);
    }
    if (pollRef.current) clearInterval(pollRef.current);
    return undefined;
  }, [data, reload]);

  async function handleDownload() {
    setDownloadError(null);
    setDownloading(true);
    try {
      await downloadDocument(id, data?.fileName);
    } catch (err) {
      setDownloadError(err);
    } finally {
      setDownloading(false);
    }
  }

  if (loading) return <LoadingState label="Loading document…" />;
  // A 403/404 on the metadata call replaces the whole page with a clear message.
  if (error) return <ApiErrorPanel error={error} onRetry={reload} />;
  if (!data) return null;

  const downloadForbidden = downloadError instanceof ApiError && downloadError.isForbidden;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-text-secondary">Document</p>
          <h1 className="mt-2 text-3xl font-semibold text-text">{data.title}</h1>
          <div className="mt-3 flex items-center gap-3">
            <OcrStatusBadge status={data.ocrStatus} />
            {data.parentId && (
              <Link to={`/folders/${data.parentId}`} className="text-sm text-primary hover:underline">
                Open parent folder
              </Link>
            )}
          </div>
        </div>
        <div className="text-right">
          <button
            onClick={handleDownload}
            disabled={downloading}
            className="rounded-xl border border-border bg-primary px-5 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90 disabled:opacity-60"
          >
            {downloading ? "Preparing…" : "Download"}
          </button>
        </div>
      </div>

      {downloadError && (
        <div className="rounded-xl border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700">
          {downloadForbidden
            ? downloadError.detail || "You do not have permission to download this document."
            : downloadError.detail || "The download could not be completed."}
        </div>
      )}

      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-text">Metadata</h2>
        <div className="mt-4 divide-y divide-border">
          <MetadataRow label="Title" value={data.title} />
          <MetadataRow label="Description" value={data.description} />
          <MetadataRow label="Document type" value={data.documentType} />
          <MetadataRow label="EAP number" value={data.eapNumber} />
          <MetadataRow label="File name" value={data.fileName} />
          <MetadataRow label="Content type" value={data.contentType} />
          <MetadataRow label="Size" value={formatBytes(data.fileSize)} />
          <MetadataRow label="Uploaded by" value={data.uploaderId} />
          <MetadataRow label="Department" value={data.organizationalUnit} />
          <MetadataRow label="Uploaded at" value={formatDate(data.uploadDate)} />
          <MetadataRow label="Indexing status" value={data.indexingStatus} />
          <MetadataRow label="Document status" value={data.documentStatus} />
        </div>
      </section>
    </div>
  );
};

export default DocumentDetail;
