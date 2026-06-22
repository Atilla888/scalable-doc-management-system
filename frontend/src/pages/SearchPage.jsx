<<<<<<< HEAD
const searchResults = [
  {
    title: "Permit_2024-031.pdf",
    fileNumber: "EAP-12.04.01",
    type: "PDF",
    folder: "Case Folder 2024-031",
    updatedAt: "2026-04-21",
    snippet: "Permit approval letter for the building case.",
  },
  {
    title: "Inspection_A12.tif",
    fileNumber: "EAP-12.04.02",
    type: "Scan",
    folder: "Case Folder 2024-031",
    updatedAt: "2026-04-20",
    snippet: "OCR text extracted from the inspection scan.",
  },
  {
    title: "Appeal_Letter.pdf",
    fileNumber: "EAP-12.04.07",
    type: "Scan",
    folder: "Case Folder 2024-031",
    updatedAt: "2026-04-17",
    snippet: "Appeal document linked to the same case folder.",
  },
];
=======
import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { searchDocuments } from "../api/search";
import { ApiError } from "../api/client";
import OcrStatusBadge from "../components/OcrStatusBadge";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";
>>>>>>> 7a096b8adde8bbb5ea2abb75ab164ce3f91f3231

const SearchPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const queryParam = searchParams.get("q") ?? "";

  const [input, setInput] = useState(queryParam);
  const [results, setResults] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setInput(queryParam);
    if (!queryParam.trim()) {
      setResults(null);
      setError(null);
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError(null);
    searchDocuments(queryParam, controller.signal)
      .then((data) => {
        setResults(data);
        setLoading(false);
      })
      .catch((err) => {
        if (err?.name !== "AbortError") {
          setError(err);
          setLoading(false);
        }
      });

    return () => controller.abort();
  }, [queryParam]);

  function submit(e) {
    e.preventDefault();
    const trimmed = input.trim();
    if (trimmed) setSearchParams({ q: trimmed });
    else setSearchParams({});
  }

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-medium text-text-secondary">Search</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Search documents</h1>
        <p className="mt-2 text-sm text-text-secondary">
          Results only include documents you are permitted to see.
        </p>
      </div>

      <form onSubmit={submit} className="flex gap-3">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Search title, EAP number, or text"
          className="flex-1 rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary"
        />
        <button
          type="submit"
          className="rounded-xl border border-border bg-primary px-6 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90"
        >
          Search
        </button>
      </form>

      {loading && <LoadingState label="Searching…" />}
      {!loading && error && error instanceof ApiError && error.isForbidden && (
        <ApiErrorPanel error={error} />
      )}
      {!loading && error && !(error instanceof ApiError && error.isForbidden) && (
        <ApiErrorPanel error={error} />
      )}

      {!loading && !error && results && (
        <div>
          <p className="mb-3 text-sm text-text-secondary">
            {results.length} {results.length === 1 ? "result" : "results"} for “{queryParam}”
          </p>
          {results.length === 0 ? (
            <div className="rounded-2xl border border-border bg-surface p-10 text-center text-sm text-text-secondary">
              No documents matched your search.
            </div>
          ) : (
            <div className="space-y-3">
              {results.map((item) => (
                <Link
                  key={item.id}
                  to={`/documents/${item.id}`}
                  className="block rounded-2xl border border-border bg-surface p-5 shadow-sm hover:border-primary"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h3 className="text-base font-semibold text-text">{item.title}</h3>
                      <p className="mt-1 text-sm text-text-secondary">{item.snippet}</p>
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
          )}
        </div>
      )}
    </div>
  );
};

export default SearchPage;
