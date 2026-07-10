/**
 * @module pages/UploadPage
 * Document upload page: file + metadata form, parent-folder selection (root and
 * its immediate subfolders), and success/error feedback.
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

function formatDmsRoles(roles) {
  const dmsRoles = roles.filter((role) => role.startsWith("dms_"));
  if (dmsRoles.length === 0) return "no DMS role";
  return dmsRoles.map((role) => role.replace("dms_", "")).join(", ");
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

  // Parent options: root folder plus its immediate subfolders.
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

  const [form, setForm] = useState({
    title: "",
    documentType: DOC_TYPES[0],
    parentId: "",
    description: "",
    eapCategory: "",
  });
  const [file, setFile] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    if (!form.parentId && rootView) {
      setForm((prev) => ({ ...prev, parentId: presetParent || rootView.folder.id }));
    }
  }, [rootView, presetParent, form.parentId]);

  /**
   * Updates a single upload-form field.
   * @param {string} field Field name.
   * @param {*} value New value.
   * @returns {void}
   */
  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  /**
   * Validates the form and uploads the selected file plus metadata as multipart
   * form data, surfacing success or error feedback.
   * @param {React.FormEvent} e Form submit event.
   * @returns {Promise<void>}
   */
  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitError(null);
    setResult(null);

    if (!hasUploadRole) {
      setSubmitError(
        new ApiError(
          403,
          `You have ${roleSummary} role and cannot upload documents. Contact an administrator if you need contributor access.`,
        ),
      );
      return;
    }

    if (!file) {
      setSubmitError(new ApiError(400, "Please choose a file to upload."));
      return;
    }

    const data = new FormData();
    data.append("file", file);
    data.append("title", form.title);
    data.append("documentType", form.documentType);
    data.append("parentId", form.parentId);
    if (form.description) data.append("description", form.description);
    if (form.eapCategory) data.append("eapCategory", form.eapCategory);

    setSubmitting(true);
    try {
      const response = await uploadDocument(data);
      setResult(response);
    } catch (err) {
      setSubmitError(err);
    } finally {
      setSubmitting(false);
    }
  }

  if (rootLoading) return <LoadingState label="Loading upload form…" />;
  if (rootError) return <ApiErrorPanel error={rootError} />;

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-medium text-text-secondary">Upload</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Upload a document</h1>
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

      {result && (
        <div className="rounded-2xl border border-emerald-300 bg-emerald-50 p-5 text-sm text-emerald-800">
          <p className="font-semibold">Upload complete — status {result.status}</p>
          <div className="mt-2 flex flex-wrap items-center gap-3">
            <span>OCR:</span>
            <OcrStatusBadge status={result.ocrStatus} />
            <Link to={`/documents/${result.id}`} className="text-emerald-900 underline">
              Open document to track status
            </Link>
          </div>
        </div>
      )}

      {submitError && (
        <ApiErrorPanel
          error={submitError}
          title={submitError instanceof ApiError && submitError.isForbidden ? "Upload is not allowed for your role" : undefined}
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
        <div>
          <label className="mb-1 block text-sm font-medium text-text">File *</label>
          <input
            type="file"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="block w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
          />
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm font-medium text-text">Title *</label>
            <input
              required
              value={form.title}
              onChange={(e) => update("title", e.target.value)}
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
              placeholder="Document title"
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-text">Document type *</label>
            <select
              value={form.documentType}
              onChange={(e) => update("documentType", e.target.value)}
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
            >
              {DOC_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-text">Parent folder *</label>
            <select
              value={form.parentId}
              onChange={(e) => update("parentId", e.target.value)}
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
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

          <div>
            <label className="mb-1 block text-sm font-medium text-text">EAP category (optional)</label>
            <input
              value={form.eapCategory}
              onChange={(e) => update("eapCategory", e.target.value)}
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
              placeholder="e.g. 1001"
            />
          </div>
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-text">Description (optional)</label>
          <textarea
            value={form.description}
            onChange={(e) => update("description", e.target.value)}
            className="min-h-28 w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
            placeholder="Notes or metadata"
          />
        </div>

        <div>
          <button
            type="submit"
            disabled={submitting || !hasUploadRole}
            className="rounded-xl border border-border bg-primary px-6 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90 disabled:opacity-60"
          >
            {submitting ? "Uploading…" : hasUploadRole ? "Upload document" : "Upload disabled"}
          </button>
        </div>
      </form>
    </div>
  );
};

export default UploadPage;
