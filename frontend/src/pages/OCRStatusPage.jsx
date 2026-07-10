/**
 * @module pages/OCRStatusPage
 * Admin OCR queue page: summary counts, a jobs table, and a retry action for
 * failed jobs.
 */
import { useCallback, useState } from "react";
import useApiResource from "../hooks/useApiResource";
import { listOcrJobs, retryOcr } from "../api/ocr";
import LoadingState from "../components/LoadingState";
import ApiErrorPanel from "../components/ApiErrorPanel";
import OcrStatusBadge from "../components/OcrStatusBadge";

const SUMMARY = [
  { key: "pending", label: "Pending" },
  { key: "processing", label: "Processing" },
  { key: "completed", label: "Completed" },
  { key: "failed", label: "Failed" },
];

/**
 * Renders the OCR status/queue page.
 * @returns {JSX.Element}
 */
const OCRStatusPage = () => {
  const { data, error, loading, reload } = useApiResource(
    (signal) => listOcrJobs(undefined, signal),
    [],
  );
  const [retryingId, setRetryingId] = useState(null);
  const [retryError, setRetryError] = useState(null);

  const jobs = data ?? [];
  const counts = SUMMARY.map((item) => ({
    ...item,
    value: jobs.filter((job) => (job.ocrStatus || "").toLowerCase() === item.key).length,
  }));

  const onRetry = useCallback(
    async (id) => {
      setRetryingId(id);
      setRetryError(null);
      try {
        await retryOcr(id);
        reload();
      } catch (err) {
        setRetryError(err);
      } finally {
        setRetryingId(null);
      }
    },
    [reload],
  );

  if (loading) return <LoadingState label="Loading OCR jobs…" />;
  if (error) return <ApiErrorPanel error={error} onRetry={reload} />;

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="text-sm font-medium text-text-secondary">OCR Status</p>
            <h1 className="mt-2 text-3xl font-semibold text-text">Document recognition pipeline</h1>
            <p className="mt-3 max-w-3xl text-text-secondary">
              Live OCR queue from the worker. Failed jobs can be requeued here without editing the database.
            </p>
          </div>
          <button
            onClick={reload}
            className="rounded-xl border border-border bg-background px-5 py-2 text-sm font-medium text-text hover:bg-surface"
          >
            Refresh
          </button>
        </div>
      </section>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {counts.map((item) => (
          <div key={item.key} className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
            <p className="text-sm text-text-secondary">{item.label}</p>
            <p className="mt-2 text-3xl font-semibold text-text">{item.value}</p>
          </div>
        ))}
      </div>

      {retryError && <ApiErrorPanel error={retryError} />}

      <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-lg font-semibold text-text">OCR jobs</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-border text-sm">
            <thead className="bg-background text-left text-text-secondary">
              <tr>
                <th className="px-5 py-3 font-medium">File</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 font-medium">Method</th>
                <th className="px-5 py-3 font-medium">Retries</th>
                <th className="px-5 py-3 font-medium">Last error</th>
                <th className="px-5 py-3 font-medium">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {jobs.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-5 py-8 text-center text-text-secondary">
                    No OCR jobs.
                  </td>
                </tr>
              ) : (
                jobs.map((job) => {
                  const failed = (job.ocrStatus || "").toLowerCase() === "failed";
                  return (
                    <tr key={job.id} className="hover:bg-background/50 align-top">
                      <td className="px-5 py-4 font-medium text-text">{job.fileName || job.title || job.id}</td>
                      <td className="px-5 py-4">
                        <OcrStatusBadge status={job.ocrStatus} />
                      </td>
                      <td className="px-5 py-4 text-text-secondary">{job.extractionMethod || "—"}</td>
                      <td className="px-5 py-4 text-text-secondary">{job.retryCount ?? 0}</td>
                      <td className="px-5 py-4 max-w-sm text-text-secondary">{job.ocrError || "—"}</td>
                      <td className="px-5 py-4">
                        {failed ? (
                          <button
                            onClick={() => onRetry(job.id)}
                            disabled={retryingId === job.id}
                            className="rounded-lg border border-border bg-background px-4 py-2 text-xs font-medium text-text hover:bg-surface disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            {retryingId === job.id ? "Retrying…" : "Retry"}
                          </button>
                        ) : (
                          <span className="text-text-secondary">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};

export default OCRStatusPage;
