import React from "react";

const Home = () => {
  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">Workspace overview</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Welcome back, Clerk</h1>
        <p className="mt-3 max-w-2xl text-text-secondary">
          Use the sidebar to move between document operations, search, upload,
          OCR tracking, and administration while keeping the shared shell in place.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm text-text-secondary">Queued documents</p>
          <p className="mt-2 text-3xl font-semibold text-text">18</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm text-text-secondary">OCR jobs running</p>
          <p className="mt-2 text-3xl font-semibold text-text">4</p>
        </div>
        <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <p className="text-sm text-text-secondary">Pending approvals</p>
          <p className="mt-2 text-3xl font-semibold text-text">7</p>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="min-h-56 rounded-2xl border border-dashed border-border bg-surface p-6 text-text-secondary shadow-sm">
          Content block 1
        </div>
        <div className="min-h-56 rounded-2xl border border-dashed border-border bg-surface p-6 text-text-secondary shadow-sm">
          Content block 2
        </div>
      </div>
    </div>
  );
};

export default Home;
