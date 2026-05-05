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

const SearchPage = () => {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">Search</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Permission-safe full-text search</h1>
        <p className="mt-3 max-w-3xl text-text-secondary">
          This frontend mockup shows the search workflow while the real API is still being built. Results below represent authorized documents only.
        </p>

        <div className="mt-5 grid gap-3 lg:grid-cols-[minmax(0,1fr)_160px_160px]">
          <input className="rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary" placeholder="Search title, file number, OCR text, or metadata" />
          <select className="rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary">
            <option>All types</option>
            <option>PDF</option>
            <option>Scan</option>
            <option>Folder</option>
          </select>
          <button className="rounded-xl border border-border bg-primary px-4 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90">
            Search
          </button>
        </div>

        <div className="mt-4 flex flex-wrap gap-2 text-xs text-text-secondary">
          <span className="rounded-full border border-border bg-background px-3 py-1">Scope: EAP Category 12</span>
          <span className="rounded-full border border-border bg-background px-3 py-1">RBAC: Department visible</span>
          <span className="rounded-full border border-border bg-background px-3 py-1">OCR: included</span>
        </div>
      </section>

      <div className="grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-text">Filters</h2>

          <div className="space-y-2 text-sm text-text-secondary">
            <label className="flex items-center gap-2"><input type="checkbox" defaultChecked /> Include OCR text</label>
            <label className="flex items-center gap-2"><input type="checkbox" defaultChecked /> Show only accessible documents</label>
            <label className="flex items-center gap-2"><input type="checkbox" /> Include folders</label>
          </div>

          <div className="space-y-2 text-sm">
            <p className="font-medium text-text">Document type</p>
            <div className="flex flex-wrap gap-2">
              {['All', 'PDF', 'Scan', 'XML', 'JSON'].map((item) => (
                <button key={item} className="rounded-full border border-border bg-background px-3 py-1 text-text-secondary hover:text-text">
                  {item}
                </button>
              ))}
            </div>
          </div>

          <div className="rounded-xl border border-border bg-background p-4 text-sm text-text-secondary">
            Search results are filtered by document permissions before returning any snippet or metadata.
          </div>
        </aside>

        <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          <div className="border-b border-border px-5 py-4">
            <h2 className="text-lg font-semibold text-text">Results</h2>
            <p className="mt-1 text-sm text-text-secondary">3 documents found in the current scope</p>
          </div>

          <div className="divide-y divide-border">
            {searchResults.map((item) => (
              <article key={item.fileNumber} className="p-5 hover:bg-background/50">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h3 className="text-base font-semibold text-text">{item.title}</h3>
                    <p className="mt-1 text-sm text-text-secondary">{item.snippet}</p>
                  </div>
                  <span className="rounded-full border border-border bg-background px-3 py-1 text-xs text-text-secondary">
                    {item.type}
                  </span>
                </div>

                <div className="mt-4 grid gap-3 text-sm text-text-secondary md:grid-cols-4">
                  <div><span className="block text-xs uppercase tracking-wide text-text-secondary">File Number</span>{item.fileNumber}</div>
                  <div><span className="block text-xs uppercase tracking-wide text-text-secondary">Folder</span>{item.folder}</div>
                  <div><span className="block text-xs uppercase tracking-wide text-text-secondary">Updated</span>{item.updatedAt}</div>
                  <div className="flex items-end justify-start gap-2">
                    <button className="rounded-lg border border-border bg-background px-3 py-2 text-xs text-text hover:bg-surface">Open</button>
                    <button className="rounded-lg border border-border bg-background px-3 py-2 text-xs text-text hover:bg-surface">Download</button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};

export default SearchPage;