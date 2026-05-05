const folderTree = [
  {
    label: "Root",
    children: [
      {
        label: "EAP Category 12 — Building",
        children: [
          {
            label: "Case Folder 2024-031",
            children: [
              { label: "Subfolder: Permits" },
              { label: "Subfolder: Reports" },
              { label: "Document: Permit.pdf" },
            ],
          },
          { label: "Case Folder 2024-032" },
        ],
      },
      { label: "EAP Category 08 — Citizens" },
      { label: "EAP Category 04 — Land" },
    ],
  },
];

const documents = [
  { type: "Folder", name: "Permits", eapNum: "—", docType: "—", ocr: "—", updated: "2026-04-20" },
  { type: "Folder", name: "Reports", eapNum: "—", docType: "—", ocr: "—", updated: "2026-04-19" },
  { type: "Doc", name: "Permit_2024-031.pdf", eapNum: "EAP-12.04.01", docType: "PDF", ocr: "COMPLETED", updated: "2026-04-21" },
  { type: "Doc", name: "Inspection_A12.tif", eapNum: "EAP-12.04.02", docType: "Scan", ocr: "PROCESSING", updated: "2026-04-20" },
  { type: "Doc", name: "Appeal_Letter.pdf", eapNum: "EAP-12.04.07", docType: "Scan", ocr: "FAILED", updated: "2026-04-17" },
];

const TreeNode = ({ node, level = 0 }) => {
  const hasChildren = node.children?.length > 0;

  return (
    <li className={level > 0 ? "ml-5" : ""}>
      <div className="flex items-start gap-2 py-1 text-sm text-text">
        <span className="mt-1 text-xs leading-none text-text-secondary">■</span>
        <span>{node.label}</span>
      </div>
      {hasChildren ? (
        <ul className="mt-1 space-y-1 border-l border-border pl-4">
          {node.children.map((child) => (
            <TreeNode key={child.label} node={child} level={level + 1} />
          ))}
        </ul>
      ) : null}
    </li>
  );
};

const DocumentsPage = () => {
  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-text">5. Document Hierarchy Browser</h1>
        <div className="mt-3 h-px w-full bg-border" />
      </div>

      <div className="grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="rounded-sm border border-border bg-surface p-4 shadow-sm">
          <h2 className="text-lg font-semibold text-text">Folder Tree</h2>
          <ul className="mt-4 space-y-1">
            {folderTree.map((node) => (
              <TreeNode key={node.label} node={node} />
            ))}
          </ul>
        </aside>

        <section className="rounded-sm border border-border bg-surface shadow-sm">
          <div className="border-b border-border px-4 py-3 text-sm text-text">
            Root <span className="px-2 text-text-secondary">›</span> EAP Category 12 — Building <span className="px-2 text-text-secondary">›</span> Case Folder 2024-031
          </div>

          <div className="flex flex-wrap gap-4 border-b border-border px-4 py-3">
            <button className="min-w-48 border border-border bg-background px-8 py-3 text-sm text-text transition hover:bg-surface">
              + Create Folder
            </button>
            <button className="min-w-48 border border-border bg-background px-8 py-3 text-sm text-text transition hover:bg-surface">
              ↑ Upload Document
            </button>
          </div>

          <div className="overflow-x-auto">
            <table className="min-w-full border-collapse text-sm text-text">
              <thead>
                <tr className="bg-background text-left">
                  <th className="border-b border-r border-border px-3 py-3 font-semibold">Type</th>
                  <th className="border-b border-r border-border px-3 py-3 font-semibold">Name</th>
                  <th className="border-b border-r border-border px-3 py-3 font-semibold">EAP Num.</th>
                  <th className="border-b border-r border-border px-3 py-3 font-semibold">Type</th>
                  <th className="border-b border-r border-border px-3 py-3 font-semibold">OCR</th>
                  <th className="border-b border-r border-border px-3 py-3 font-semibold">Updated</th>
                  <th className="border-b border-border px-3 py-3 font-semibold">Act.</th>
                </tr>
              </thead>
              <tbody>
                {documents.map((row) => (
                  <tr key={`${row.type}-${row.name}`} className="hover:bg-background/60">
                    <td className="border-b border-r border-border px-3 py-3">{row.type}</td>
                    <td className="border-b border-r border-border px-3 py-3">{row.name}</td>
                    <td className="border-b border-r border-border px-3 py-3">{row.eapNum}</td>
                    <td className="border-b border-r border-border px-3 py-3">{row.docType}</td>
                    <td className="border-b border-r border-border px-3 py-3">{row.ocr}</td>
                    <td className="border-b border-r border-border px-3 py-3">{row.updated}</td>
                    <td className="border-b border-border px-3 py-3 text-center text-primary">■</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="h-[360px]" />
        </section>
      </div>
    </div>
  );
};

export default DocumentsPage;