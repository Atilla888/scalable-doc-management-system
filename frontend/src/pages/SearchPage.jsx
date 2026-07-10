/**
 * @module pages/SearchPage
 * Document search page: filter form synced to the URL query string, paginated
 * results, and term highlighting. Results are permission-scoped by the backend.
 */
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { searchDocuments } from "../api/search";
import { getFolderTree } from "../api/folders";
import OcrStatusBadge from "../components/OcrStatusBadge";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";

const PAGE_SIZE = 20;
const STATUS_OPTIONS = ["pending", "completed", "not_required"];
const SORT_OPTIONS = [
  { value: "date_desc", label: "Newest first" },
  { value: "date_asc", label: "Oldest first" },
  { value: "title_asc", label: "Title A–Z" },
  { value: "title_desc", label: "Title Z–A" },
];

/**
 * Wraps case-insensitive matches of `term` in <mark> for highlighting.
 * @param {string} text Text to render.
 * @param {string} [term] Search term to highlight.
 * @returns {React.ReactNode} The text with matches wrapped in <mark>.
 */
function highlight(text, term) {
  const value = term?.trim();
  if (!text || !value) return text;
  const escaped = value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const parts = String(text).split(new RegExp(`(${escaped})`, "ig"));
  return parts.map((part, i) =>
    part.toLowerCase() === value.toLowerCase() ? (
      <mark key={i} className="rounded bg-yellow-200 px-0.5 text-text">
        {part}
      </mark>
    ) : (
      part
    ),
  );
}

const emptyForm = {
  query: "",
  type: "",
  department: "",
  folder: "",
  status: "",
  dateFrom: "",
  dateTo: "",
  sort: "date_desc",
};

/**
 * Renders the document search page.
 * @returns {JSX.Element}
 */
const SearchPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const params = useMemo(
    () => ({
      query: searchParams.get("q") ?? "",
      type: searchParams.get("type") ?? "",
      department: searchParams.get("department") ?? "",
      folder: searchParams.get("folder") ?? "",
      status: searchParams.get("status") ?? "",
      dateFrom: searchParams.get("dateFrom") ?? "",
      dateTo: searchParams.get("dateTo") ?? "",
      sort: searchParams.get("sort") ?? "date_desc",
      page: parseInt(searchParams.get("page") ?? "0", 10) || 0,
    }),
    [searchParams],
  );

  const [form, setForm] = useState(params);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [folders, setFolders] = useState([]);

  // Keep the controlled inputs in sync when the URL changes (e.g. back button).
  useEffect(() => setForm(params), [params]);

  // Folder options for the filter dropdown.
  useEffect(() => {
    getFolderTree()
      .then(setFolders)
      .catch(() => setFolders([]));
  }, []);

  const hasCriteria =
    params.query.trim() ||
    params.type ||
    params.department ||
    params.folder ||
    params.status ||
    params.dateFrom ||
    params.dateTo;

  useEffect(() => {
    if (!hasCriteria) {
      setData(null);
      setError(null);
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    searchDocuments({ ...params, limit: PAGE_SIZE }, controller.signal)
      .then((res) => {
        setData(res);
        setLoading(false);
      })
      .catch((err) => {
        if (err?.name !== "AbortError") {
          setError(err);
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [params, hasCriteria]);

  const pushParams = (next) => {
    const sp = {};
    if (next.query?.trim()) sp.q = next.query.trim();
    if (next.type) sp.type = next.type;
    if (next.department) sp.department = next.department;
    if (next.folder) sp.folder = next.folder;
    if (next.status) sp.status = next.status;
    if (next.dateFrom) sp.dateFrom = next.dateFrom;
    if (next.dateTo) sp.dateTo = next.dateTo;
    if (next.sort && next.sort !== "date_desc") sp.sort = next.sort;
    if (next.page) sp.page = String(next.page);
    setSearchParams(sp);
  };

  const applyFilters = (event) => {
    event.preventDefault();
    pushParams({ ...form, page: 0 });
  };

  const resetFilters = () => {
    setForm(emptyForm);
    setSearchParams({});
  };

  const goToPage = (page) => pushParams({ ...params, page });

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value });

  const content = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const rangeStart = total === 0 ? 0 : params.page * PAGE_SIZE + 1;
  const rangeEnd = params.page * PAGE_SIZE + content.length;

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-medium text-text-secondary">Search</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Search documents</h1>
        <p className="mt-2 text-sm text-text-secondary">
          Results only include documents you are permitted to see.
        </p>
      </div>

      <form onSubmit={applyFilters} className="space-y-4">
        <div className="flex gap-3">
          <input
            value={form.query}
            onChange={update("query")}
            placeholder="Search title, EAP number, or text"
            className="flex-1 rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
          />
          <button
            type="submit"
            className="rounded-xl border border-border bg-primary px-6 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90"
          >
            Search
          </button>
        </div>

        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <input
            value={form.type}
            onChange={update("type")}
            placeholder="Type (e.g. report)"
            className="rounded-xl border border-border bg-background px-4 py-2.5 text-sm outline-none focus:border-primary"
          />
          <input
            value={form.department}
            onChange={update("department")}
            placeholder="Department (e.g. ITDLZ)"
            className="rounded-xl border border-border bg-background px-4 py-2.5 text-sm outline-none focus:border-primary"
          />
          <select
            value={form.folder}
            onChange={update("folder")}
            className="rounded-xl border border-border bg-background px-4 py-2.5 text-sm outline-none focus:border-primary"
          >
            <option value="">Any folder</option>
            {folders.map((f) => (
              <option key={f.id} value={f.id}>
                {f.path === "/" ? "Root (/)" : f.path}
              </option>
            ))}
          </select>
          <select
            value={form.status}
            onChange={update("status")}
            className="rounded-xl border border-border bg-background px-4 py-2.5 text-sm outline-none focus:border-primary"
          >
            <option value="">Any OCR status</option>
            {STATUS_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {s.replace("_", " ")}
              </option>
            ))}
          </select>
          <input
            type="date"
            value={form.dateFrom}
            onChange={update("dateFrom")}
            className="rounded-xl border border-border bg-background px-4 py-2.5 text-sm text-text-secondary outline-none focus:border-primary"
          />
          <input
            type="date"
            value={form.dateTo}
            onChange={update("dateTo")}
            className="rounded-xl border border-border bg-background px-4 py-2.5 text-sm text-text-secondary outline-none focus:border-primary"
          />
          <select
            value={form.sort}
            onChange={update("sort")}
            className="rounded-xl border border-border bg-background px-4 py-2.5 text-sm outline-none focus:border-primary"
          >
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={resetFilters}
            className="rounded-xl border border-border bg-surface px-4 py-2.5 text-sm font-medium text-text hover:bg-background"
          >
            Clear filters
          </button>
        </div>
      </form>

      {loading && <LoadingState label="Searching…" />}
      {!loading && error && <ApiErrorPanel error={error} />}

      {!loading && !error && !hasCriteria && (
        <div className="rounded-2xl border border-border bg-surface p-10 text-center text-sm text-text-secondary">
          Enter a search term or apply a filter to find documents.
        </div>
      )}

      {!loading && !error && hasCriteria && data && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-text-secondary">
            <span>
              {total === 0
                ? "No results"
                : `Showing ${rangeStart}–${rangeEnd} of ${total} result${total === 1 ? "" : "s"}`}
            </span>
          </div>

          {content.length === 0 ? (
            <div className="rounded-2xl border border-border bg-surface p-10 text-center text-sm text-text-secondary">
              No documents matched your search.
            </div>
          ) : (
            <>
              <div className="space-y-3">
                {content.map((item) => (
                  <Link
                    key={item.id}
                    to={`/documents/${item.id}`}
                    className="block rounded-2xl border border-border bg-surface p-5 shadow-sm hover:border-primary"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <h3 className="text-base font-semibold text-text">
                          {highlight(item.title, params.query)}
                        </h3>
                        <p className="mt-1 text-sm text-text-secondary">
                          {highlight(item.snippet, params.query)}
                        </p>
                      </div>
                      <OcrStatusBadge status={item.ocrStatus} />
                    </div>
                    <div className="mt-3 flex flex-wrap gap-4 text-xs text-text-secondary">
                      <span>EAP: {item.eapNumber}</span>
                      <span>Type: {item.documentType}</span>
                    </div>
                  </Link>
                ))}
              </div>

              <div className="flex items-center justify-between pt-2">
                <button
                  type="button"
                  onClick={() => goToPage(params.page - 1)}
                  disabled={params.page <= 0}
                  className="rounded-xl border border-border bg-surface px-4 py-2 text-sm font-medium text-text hover:bg-background disabled:opacity-50"
                >
                  ← Previous
                </button>
                <span className="text-sm text-text-secondary">
                  Page {params.page + 1} of {Math.max(data.totalPages, 1)}
                </span>
                <button
                  type="button"
                  onClick={() => goToPage(params.page + 1)}
                  disabled={!data.hasMore}
                  className="rounded-xl border border-border bg-surface px-4 py-2 text-sm font-medium text-text hover:bg-background disabled:opacity-50"
                >
                  Next →
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default SearchPage;
