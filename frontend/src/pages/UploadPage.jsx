/**
 * @module pages/UploadPage
 * Document upload page: batch file selection, per-file metadata, parent-folder
 * selection, and success/error feedback.
 */
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getRootFolder } from "../api/folders";
import { uploadDocument } from "../api/documents";
import useApiResource from "../hooks/useApiResource";
import { ApiError } from "../api/client";
import LoadingState from "../components/LoadingState";
import ApiErrorPanel from "../components/ApiErrorPanel";
import OcrStatusBadge from "../components/OcrStatusBadge";

const DOC_TYPES = ["report", "invoice", "contract", "scan", "letter", "other"];
const UPLOAD_ROLES = ["dms_admin", "dms_department_manager", "dms_contributor"];
const UPLOAD_STEPS = [
  { key: "validate", label: "Validate access" },
  { key: "upload", label: "Upload file" },
  { key: "queue", label: "Queue OCR" },
];

/**
 * Summarizes a user's DMS roles as a short, comma-separated label.
 * @param {string[]} roles All role names on the user.
 * @returns {string} Comma-separated DMS role names, or "no DMS role".
 */
function formatDmsRoles(roles) {
  const dmsRoles = roles.filter((role) => role.startsWith("dms_"));
  if (dmsRoles.length === 0) return "no DMS role";
  return dmsRoles.map((role) => role.replace("dms_", "")).join(", ");
}

/**
 * Formats a byte count as B/KB/MB.
 * @param {number} bytes Size in bytes.
 * @returns {string} Human-readable size, or "—" for non-finite input.
 */
function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Derives a default document title from a file, stripping its extension.
 * @param {File} file Selected file.
 * @returns {string} File name without extension, or the full name.
 */
function titleFromFile(file) {
  return file.name.replace(/\.[^.]+$/, "") || file.name;
}

/**
 * Progress panel shown while the upload queue is being processed.
 * @param {Object} props
 * @param {string} props.step Active upload step key.
 * @param {string} [props.currentFile] Name of the file being uploaded.
 * @param {number} props.completed Number of files uploaded so far.
 * @param {number} props.total Total number of files in the queue.
 * @returns {JSX.Element}
 */
function UploadProgress({ step, currentFile, completed, total }) {
  const activeIndex = Math.max(
    UPLOAD_STEPS.findIndex((item) => item.key === step),
    0,
  );
  const percent = total > 0 ? Math.round((completed / total) * 100) : 0;

  return (
    <div className="rounded-2xl border border-blue-200 bg-blue-50 p-5 text-sm text-blue-900">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="font-semibold">Uploading documents…</p>
          <p className="mt-1 text-blue-800">
            {currentFile ? `Current file: ${currentFile}` : "Preparing the upload queue."}
          </p>
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-blue-700">
          {completed} of {total} uploaded
        </span>
      </div>

      <div className="mt-4 h-2 overflow-hidden rounded-full bg-blue-100">
        <div
          className="h-full rounded-full bg-blue-600 transition-all"
          style={{ width: `${Math.max(percent, 8)}%` }}
        />
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-3">
        {UPLOAD_STEPS.map((item, index) => {
          const done = index < activeIndex;
          const active = index === activeIndex;
          return (
            <div
              key={item.key}
              className={`rounded-xl border px-4 py-3 ${
                active || done
                  ? "border-blue-300 bg-white text-blue-900"
                  : "border-blue-100 bg-blue-50/60 text-blue-500"
              }`}
            >
              <p className="text-xs font-semibold uppercase tracking-wide">
                {done ? "Done" : active ? "In progress" : "Waiting"}
              </p>
              <p className="mt-1 font-medium">{item.label}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Colored badge reflecting a single file's upload status.
 * @param {Object} props
 * @param {string} props.status One of "ready", "uploading", "uploaded", "failed".
 * @returns {JSX.Element}
 */
function UploadStatusBadge({ status }) {
  const styles = {
    ready: "border-border bg-background text-text-secondary",
    uploading: "border-blue-200 bg-blue-50 text-blue-700",
    uploaded: "border-emerald-200 bg-emerald-50 text-emerald-700",
    failed: "border-red-200 bg-red-50 text-red-700",
  };
  const labels = {
    ready: "Ready",
    uploading: "Uploading",
    uploaded: "Uploaded",
    failed: "Failed",
  };

  return (
    <span className={`rounded-full border px-3 py-1 text-xs font-medium ${styles[status]}`}>
      {labels[status] || status}
    </span>
  );
}

/**
 * Renders the document upload page.
 * @returns {JSX.Element}
 */
const UploadPage = () => {
  const { user, roles } = useAuth();
  const department = user?.department ?? "—";
  const hasUploadRole = UPLOAD_ROLES.some((role) => roles.includes(role));
  const roleSummary = formatDmsRoles(roles);
  const [searchParams] = useSearchParams();
  const presetParent = searchParams.get("parentId");

  const { data: rootView, error: rootError, loading: rootLoading } = useApiResource(
    (signal) => getRootFolder(signal),
    [],
  );

  const parentOptions = useMemo(() => {
    if (!rootView) return [];
    return [
      { id: rootView.folder.id, name: "Root" },
      ...rootView.subfolders.map((f) => ({ id: f.id, name: f.name })),
    ];
  }, [rootView]);

  const [parentId, setParentId] = useState("");
  const [items, setItems] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [uploadStep, setUploadStep] = useState(null);
  const [currentUploadId, setCurrentUploadId] = useState(null);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    if (!parentId && rootView) {
      setParentId(presetParent || rootView.folder.id);
    }
  }, [rootView, presetParent, parentId]);

  /**
   * Updates a single metadata field on one queued item.
   * @param {string} id Item id.
   * @param {string} field Field name to update.
   * @param {*} value New field value.
   * @returns {void}
   */
  function updateItem(id, field, value) {
    setItems((prev) =>
      prev.map((item) => (item.id === id ? { ...item, [field]: value } : item)),
    );
  }

  /**
   * Merges a partial status patch into one queued item.
   * @param {string} id Item id.
   * @param {Object} patch Partial item fields to merge.
   * @returns {void}
   */
  function updateItemStatus(id, patch) {
    setItems((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }

  /**
   * Removes a queued item from the upload list.
   * @param {string} id Item id.
   * @returns {void}
   */
  function removeItem(id) {
    setItems((prev) => prev.filter((item) => item.id !== id));
  }

  /**
   * Builds the queue from the file input selection, resetting prior errors.
   * @param {React.ChangeEvent<HTMLInputElement>} event File input change event.
   * @returns {void}
   */
  function handleFilesSelected(event) {
    const selected = Array.from(event.target.files ?? []);
    setSubmitError(null);
    setItems(
      selected.map((file, index) => ({
        id: `${file.name}-${file.size}-${file.lastModified}-${index}`,
        file,
        title: titleFromFile(file),
        documentType: DOC_TYPES[0],
        description: "",
        eapCategory: "",
        status: "ready",
        result: null,
        error: null,
      })),
    );
  }

  /**
   * Uploads every selected file sequentially using the existing single-file API.
   * @param {React.FormEvent} e Form submit event.
   * @returns {Promise<void>}
   */
  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitError(null);
    setUploadStep("validate");

    if (!hasUploadRole) {
      setUploadStep(null);
      setSubmitError(
        new ApiError(
          403,
          `You have ${roleSummary} role and cannot upload documents. Contact an administrator if you need contributor access.`,
        ),
      );
      return;
    }

    if (items.length === 0) {
      setUploadStep(null);
      setSubmitError(new ApiError(400, "Please choose at least one file to upload."));
      return;
    }

    const missingTitle = items.find((item) => !item.title.trim());
    if (missingTitle) {
      setUploadStep(null);
      setSubmitError(new ApiError(400, `Please enter a title for ${missingTitle.file.name}.`));
      return;
    }

    setSubmitting(true);
    let failures = 0;

    for (const item of items) {
      setCurrentUploadId(item.id);
      setUploadStep("upload");
      updateItemStatus(item.id, { status: "uploading", error: null, result: null });

      const data = new FormData();
      data.append("file", item.file);
      data.append("title", item.title.trim());
      data.append("documentType", item.documentType);
      data.append("parentId", parentId);
      if (item.description.trim()) data.append("description", item.description.trim());
      if (item.eapCategory.trim()) data.append("eapCategory", item.eapCategory.trim());

      try {
        const response = await uploadDocument(data);
        setUploadStep("queue");
        updateItemStatus(item.id, { status: "uploaded", result: response });
      } catch (err) {
        failures += 1;
        updateItemStatus(item.id, { status: "failed", error: err });
      }
    }

    setSubmitting(false);
    setCurrentUploadId(null);
    setUploadStep(null);

    if (failures > 0) {
      setSubmitError(
        new ApiError(
          400,
          `${failures} of ${items.length} upload${items.length === 1 ? "" : "s"} failed. Check the file cards below for details.`,
        ),
      );
    }
  }

  if (rootLoading) return <LoadingState label="Loading upload form…" />;
  if (rootError) return <ApiErrorPanel error={rootError} />;

  const uploadedCount = items.filter((item) => item.status === "uploaded").length;
  const currentUpload = items.find((item) => item.id === currentUploadId);
  const allUploaded = items.length > 0 && uploadedCount === items.length;

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-medium text-text-secondary">Upload</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Upload documents</h1>
        <p className="mt-2 text-sm text-text-secondary">
          Select one or more files, then adjust the metadata for each document before uploading.
        </p>
      </div>

      {!hasUploadRole && (
        <div className="rounded-2xl border border-amber-300 bg-amber-50 p-5 text-sm text-amber-900">
          <p className="font-semibold">You cannot upload documents with your current role.</p>
          <p className="mt-2">
            Your current DMS role is <span className="font-medium">{roleSummary}</span>. Uploading
            documents requires admin, department manager, or contributor access.
          </p>
          <p className="mt-2 text-amber-800">
            Contact an administrator if you need contributor access.
          </p>
        </div>
      )}

      {submitting && uploadStep && (
        <UploadProgress
          step={uploadStep}
          currentFile={currentUpload?.file.name}
          completed={uploadedCount}
          total={items.length}
        />
      )}

      {allUploaded && (
        <div className="rounded-2xl border border-emerald-300 bg-emerald-50 p-5 text-sm text-emerald-800">
          <p className="font-semibold">All documents uploaded successfully.</p>
          <p className="mt-2">OCR/indexing status can be tracked from each uploaded document.</p>
        </div>
      )}

      {submitError && (
        <ApiErrorPanel
          error={submitError}
          title={
            submitError instanceof ApiError && submitError.isForbidden
              ? "Upload is not allowed for your role"
              : undefined
          }
          message={
            submitError instanceof ApiError && submitError.isForbidden
              ? submitError.detail ||
                "Your current role cannot upload documents. Contact an administrator if you need contributor access."
              : undefined
          }
        />
      )}

      <form
        onSubmit={handleSubmit}
        className="grid gap-5 rounded-2xl border border-border bg-surface p-6 shadow-sm"
      >
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm font-medium text-text">Files *</label>
            <input
              type="file"
              multiple
              onChange={handleFilesSelected}
              disabled={submitting || !hasUploadRole}
              className="block w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary disabled:opacity-60"
            />
            <p className="mt-2 text-xs text-text-secondary">
              {items.length === 0
                ? "No files selected."
                : `${items.length} file${items.length === 1 ? "" : "s"} selected.`}
            </p>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-text">Parent folder *</label>
            <select
              value={parentId}
              onChange={(e) => setParentId(e.target.value)}
              disabled={submitting}
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary disabled:opacity-60"
            >
              {parentOptions.map((opt) => (
                <option key={opt.id} value={opt.id}>
                  {opt.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-text">Department</label>
            <input
              value={department}
              readOnly
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm text-text-secondary outline-none"
            />
          </div>
        </div>

        {items.length > 0 && (
          <div className="space-y-4">
            {items.map((item, index) => (
              <section
                key={item.id}
                className="rounded-2xl border border-border bg-background p-5"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-base font-semibold text-text">
                        {index + 1}. {item.file.name}
                      </h2>
                      <UploadStatusBadge status={item.status} />
                    </div>
                    <p className="mt-1 text-xs text-text-secondary">
                      {formatFileSize(item.file.size)}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => removeItem(item.id)}
                    disabled={submitting}
                    className="rounded-xl border border-border bg-surface px-3 py-2 text-xs font-medium text-text hover:bg-background disabled:opacity-50"
                  >
                    Remove
                  </button>
                </div>

                <div className="mt-4 grid gap-4 md:grid-cols-2">
                  <div>
                    <label className="mb-1 block text-sm font-medium text-text">Title *</label>
                    <input
                      required
                      value={item.title}
                      onChange={(e) => updateItem(item.id, "title", e.target.value)}
                      disabled={submitting}
                      className="w-full rounded-xl border border-border bg-surface px-4 py-3 text-sm outline-none focus:border-primary disabled:opacity-60"
                      placeholder="Document title"
                    />
                  </div>

                  <div>
                    <label className="mb-1 block text-sm font-medium text-text">
                      Document type *
                    </label>
                    <select
                      value={item.documentType}
                      onChange={(e) => updateItem(item.id, "documentType", e.target.value)}
                      disabled={submitting}
                      className="w-full rounded-xl border border-border bg-surface px-4 py-3 text-sm outline-none focus:border-primary disabled:opacity-60"
                    >
                      {DOC_TYPES.map((type) => (
                        <option key={type} value={type}>
                          {type}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="mb-1 block text-sm font-medium text-text">
                      EAP category (optional)
                    </label>
                    <input
                      value={item.eapCategory}
                      onChange={(e) => updateItem(item.id, "eapCategory", e.target.value)}
                      disabled={submitting}
                      className="w-full rounded-xl border border-border bg-surface px-4 py-3 text-sm outline-none focus:border-primary disabled:opacity-60"
                      placeholder="e.g. 1001"
                    />
                  </div>
                </div>

                <div className="mt-4">
                  <label className="mb-1 block text-sm font-medium text-text">
                    Description (optional)
                  </label>
                  <textarea
                    value={item.description}
                    onChange={(e) => updateItem(item.id, "description", e.target.value)}
                    disabled={submitting}
                    className="min-h-24 w-full rounded-xl border border-border bg-surface px-4 py-3 text-sm outline-none focus:border-primary disabled:opacity-60"
                    placeholder="Notes or metadata for this file"
                  />
                </div>

                {item.result && (
                  <div className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
                    <div className="flex flex-wrap items-center gap-3">
                      <span className="font-semibold">Uploaded</span>
                      <OcrStatusBadge status={item.result.ocrStatus} />
                      <Link to={`/documents/${item.result.id}`} className="text-emerald-900 underline">
                        Open document
                      </Link>
                    </div>
                  </div>
                )}

                {item.error && (
                  <div className="mt-4 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                    {item.error.detail || item.error.message || "Upload failed."}
                  </div>
                )}
              </section>
            ))}
          </div>
        )}

        <div>
          <button
            type="submit"
            disabled={submitting || !hasUploadRole || items.length === 0}
            className="rounded-xl border border-border bg-primary px-6 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90 disabled:opacity-60"
          >
            {submitting
              ? "Uploading queue…"
              : hasUploadRole
                ? `Upload ${items.length || ""} document${items.length === 1 ? "" : "s"}`
                : "Upload disabled"}
          </button>
        </div>
      </form>
    </div>
  );
};

export default UploadPage;
