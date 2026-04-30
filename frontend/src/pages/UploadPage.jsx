import React from "react";

const uploadSteps = [
  { title: "Select file", detail: "Choose a PDF, scan, XML, or JSON document." },
  { title: "Enter metadata", detail: "Set title, EAP number, folder, and permissions." },
  { title: "Start upload", detail: "Store the file and queue OCR if required." },
  { title: "Review status", detail: "Track upload and OCR progress here." },
];

const recentUploads = [
  { name: "Permit_2024-031.pdf", status: "Uploaded", progress: 100 },
  { name: "Inspection_A12.tif", status: "OCR processing", progress: 74 },
  { name: "Case_Notes.json", status: "Pending permission check", progress: 42 },
];

const UploadPage = () => {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">Upload</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Document ingestion workspace</h1>
        <p className="mt-3 max-w-3xl text-text-secondary">
          This screen demonstrates the upload flow that will later connect to the backend and OCR pipeline.
        </p>
      </section>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <div className="rounded-2xl border-2 border-dashed border-border bg-background p-10 text-center">
            <p className="text-lg font-semibold text-text">Drop files here</p>
            <p className="mt-2 text-sm text-text-secondary">Or click to select documents from your device</p>
            <button className="mt-5 rounded-xl border border-border bg-primary px-5 py-3 text-sm font-medium text-white hover:bg-primary/90">
              Browse Files
            </button>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <input className="rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary" placeholder="Document title" />
            <input className="rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary" placeholder="EAP number" />
            <input className="rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary" placeholder="Folder / case" />
            <select className="rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary">
              <option>Document type</option>
              <option>PDF</option>
              <option>Scan</option>
              <option>XML</option>
              <option>JSON</option>
            </select>
          </div>

          <textarea className="min-h-32 rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none focus:border-primary" placeholder="Description or metadata notes" />

          <div className="flex flex-wrap gap-3">
            <button className="rounded-xl border border-border bg-primary px-5 py-3 text-sm font-medium text-white hover:bg-primary/90">
              Upload Document
            </button>
            <button className="rounded-xl border border-border bg-background px-5 py-3 text-sm font-medium text-text hover:bg-surface">
              Save Draft
            </button>
          </div>
        </section>

        <aside className="space-y-4">
          <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-text">Upload flow</h2>
            <div className="mt-4 space-y-4">
              {uploadSteps.map((step, index) => (
                <div key={step.title} className="flex gap-3">
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                    {index + 1}
                  </div>
                  <div>
                    <p className="font-medium text-text">{step.title}</p>
                    <p className="text-sm text-text-secondary">{step.detail}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-text">Recent uploads</h2>
            <div className="mt-4 space-y-4">
              {recentUploads.map((item) => (
                <div key={item.name}>
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="font-medium text-text">{item.name}</span>
                    <span className="text-text-secondary">{item.status}</span>
                  </div>
                  <div className="mt-2 h-2 rounded-full bg-background">
                    <div className="h-2 rounded-full bg-primary" style={{ width: `${item.progress}%` }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
};

export default UploadPage;