import React from "react";
const tableRows = [
  {
    title: "Invoice Batch April",
    eapNumber: "EAP-2026-001",
    type: "Invoice",
    ocrStatus: "Completed",
    updatedAt: "2026-04-30 09:15",
  },
  {
    title: "HR Onboarding Pack",
    eapNumber: "EAP-2026-014",
    type: "HR",
    ocrStatus: "Processing",
    updatedAt: "2026-04-30 10:02",
  },
  {
    title: "Vendor Contract Draft",
    eapNumber: "EAP-2026-027",
    type: "Contract",
    ocrStatus: "Pending",
    updatedAt: "2026-04-30 10:27",
  },
  {
    title: "Customer Complaint Scan",
    eapNumber: "EAP-2026-031",
    type: "Support",
    ocrStatus: "Failed",
    updatedAt: "2026-04-30 11:11",
  },
];

const Dashboard = () => {
  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm font-medium text-text-secondary">Dashboard</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Welcome ABC</h1>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm text-text-secondary">Accessible Documents</p>
          <p className="mt-2 text-3xl font-semibold text-text">1284</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm text-text-secondary">Pending OCR Jobs</p>
          <p className="mt-2 text-3xl font-semibold text-text">12</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm text-text-secondary">Recent Uploads</p>
          <p className="mt-2 text-3xl font-semibold text-text">31</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm text-text-secondary">Failed OCR Jobs</p>
          <p className="mt-2 text-3xl font-semibold text-text">2</p>
        </div>
        
      </div>

      {/* <div className="grid gap-4 lg:grid-cols-2">
        <div className="min-h-64 rounded-2xl border border-border bg-surface p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-text">Recent activity</h2>
          <div className="mt-4 space-y-3 text-sm text-text-secondary">
            <p>Document batch imported from CMIS.</p>
            <p>OCR status updated for 12 files.</p>
            <p>Approval queue received 3 new items.</p>
          </div>
        </div>
        <div className="min-h-64 rounded-2xl border border-border bg-surface p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-text">System health</h2>
          <div className="mt-4 space-y-3 text-sm text-text-secondary">
            <p>Search service: healthy</p>
            <p>Upload pipeline: healthy</p>
            <p>OCR worker pool: running</p>
          </div>
        </div>
      </div> */}
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-border">
            <thead className="bg-background">
              <tr>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Titles
                </th>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  EAP number
                </th>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Type
                </th>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  OCR Status
                </th>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  Updated at
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-surface">
              {tableRows.map((row) => (
                <tr key={row.eapNumber} className="hover:bg-background/70">
                  <td className="px-6 py-4 text-sm font-medium text-text">{row.title}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{row.eapNumber}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{row.type}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{row.ocrStatus}</td>
                  <td className="px-6 py-4 text-sm text-text-secondary">{row.updatedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div>
            <h1 className="mt-2 text-xl font-semibold text-text">Quick Actions</h1>
            <div className="flex gap-5">
              <button className="mt-4 w-full rounded-xl border border-border bg-primary px-4 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90">
                Upload Documents
              </button>
              <button className="mt-4 w-full rounded-xl border border-border bg-primary px-4 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90">
                Browse Documents
              </button>
              <button className="mt-4 w-full rounded-xl border border-border bg-primary px-4 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90">
                Search
              </button>
            </div>
        </div>
    </div>
  );
};

export default Dashboard;
