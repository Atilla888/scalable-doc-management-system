import React from "react";

const jobs = [
  { id: "OCR-1001", file: "Permit_2024-031.pdf", status: "Completed", started: "09:12", finished: "09:18" },
  { id: "OCR-1002", file: "Inspection_A12.tif", status: "Processing", started: "10:02", finished: "—" },
  { id: "OCR-1003", file: "Appeal_Letter.pdf", status: "Failed", started: "10:41", finished: "10:44" },
  { id: "OCR-1004", file: "Site_Photos.zip", status: "Pending", started: "11:01", finished: "—" },
];

const OCRStatusPage = () => {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">OCR Status</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Document recognition pipeline</h1>
        <p className="mt-3 max-w-3xl text-text-secondary">
          OCR is treated as an asynchronous process. This page visualizes job state, while the backend team hooks up the real OCR service.
        </p>
      </section>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {[
          { label: "Queued", value: 4 },
          { label: "Processing", value: 1 },
          { label: "Completed", value: 27 },
          { label: "Failed", value: 2 },
        ].map((item) => (
          <div key={item.label} className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
            <p className="text-sm text-text-secondary">{item.label}</p>
            <p className="mt-2 text-3xl font-semibold text-text">{item.value}</p>
          </div>
        ))}
      </div>

      <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-lg font-semibold text-text">OCR jobs</h2>
        </div>
        <table className="min-w-full divide-y divide-border text-sm">
          <thead className="bg-background text-left text-text-secondary">
            <tr>
              <th className="px-5 py-3 font-medium">Job ID</th>
              <th className="px-5 py-3 font-medium">File</th>
              <th className="px-5 py-3 font-medium">Status</th>
              <th className="px-5 py-3 font-medium">Started</th>
              <th className="px-5 py-3 font-medium">Finished</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {jobs.map((job) => (
              <tr key={job.id} className="hover:bg-background/50">
                <td className="px-5 py-4 font-medium text-text">{job.id}</td>
                <td className="px-5 py-4 text-text-secondary">{job.file}</td>
                <td className="px-5 py-4">
                  <span className="rounded-full border border-border bg-background px-3 py-1 text-xs text-text-secondary">
                    {job.status}
                  </span>
                </td>
                <td className="px-5 py-4 text-text-secondary">{job.started}</td>
                <td className="px-5 py-4 text-text-secondary">{job.finished}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
};

export default OCRStatusPage;