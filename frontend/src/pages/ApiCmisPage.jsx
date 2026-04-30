import React from "react";

const endpoints = [
  { method: "GET", path: "/api/auth/me", description: "Current user profile and roles" },
  { method: "GET", path: "/api/folders", description: "List folder tree entries" },
  { method: "POST", path: "/api/documents", description: "Upload a new document" },
  { method: "GET", path: "/api/search", description: "Permission-safe document search" },
  { method: "GET", path: "/cmis/repository", description: "Repository information for CMIS clients" },
];

const ApiCmisPage = () => {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">API / CMIS</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Integration surface</h1>
        <p className="mt-3 max-w-3xl text-text-secondary">
          This page documents the frontend-facing integration expectations while the backend team builds the real REST and CMIS endpoints.
        </p>
      </section>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          <div className="border-b border-border px-5 py-4">
            <h2 className="text-lg font-semibold text-text">Expected endpoints</h2>
          </div>
          <div className="divide-y divide-border">
            {endpoints.map((endpoint) => (
              <div key={endpoint.path} className="flex flex-wrap items-start gap-4 p-5 hover:bg-background/50">
                <span className="rounded-full border border-border bg-background px-3 py-1 text-xs font-medium text-text-secondary">
                  {endpoint.method}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-text">{endpoint.path}</p>
                  <p className="mt-1 text-sm text-text-secondary">{endpoint.description}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        <aside className="space-y-4">
          <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-text">Frontend contract</h2>
            <ul className="mt-4 space-y-2 text-sm text-text-secondary">
              <li>Login will be handed over to Keycloak.</li>
              <li>All document, search, and upload actions remain mock data for now.</li>
              <li>Real RBAC and CMIS behavior will come from the backend.</li>
            </ul>
          </div>

          <div className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-text">Sample response</h2>
            <pre className="mt-4 overflow-x-auto rounded-xl bg-background p-4 text-xs text-text-secondary">
{`{
  "repository": "DMS",
  "status": "mocked",
  "auth": "keycloak"
}`}
            </pre>
          </div>
        </aside>
      </div>
    </div>
  );
};

export default ApiCmisPage;