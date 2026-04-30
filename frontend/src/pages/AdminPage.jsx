import React from "react";

const users = [
  { name: "Anna Keller", role: "DMS_ADMIN", unit: "Central IT", status: "Active" },
  { name: "Moritz Bauer", role: "DMS_DOCUMENT_VIEWER", unit: "Building Dept.", status: "Active" },
  { name: "Nina Hoffmann", role: "DMS_DOCUMENT_EDITOR", unit: "Land Registry", status: "Pending" },
];

const permissions = [
  { scope: "EAP Category 12 — Building", roles: "Viewer, Editor", inheritance: "Inherited" },
  { scope: "Case Folder 2024-031", roles: "Editor", inheritance: "Overridden" },
  { scope: "OCR results", roles: "Admin, Auditor", inheritance: "Restricted" },
];

const AdminPage = () => {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">Admin</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">System administration console</h1>
        <p className="mt-3 max-w-3xl text-text-secondary">
          This frontend mockup shows how role management, permission scopes, and operational health could look once Keycloak and backend APIs are connected.
        </p>
      </section>

      <div className="grid gap-4 xl:grid-cols-2">
        <section className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-text">Users</h2>
          <div className="mt-4 overflow-hidden rounded-xl border border-border">
            <table className="min-w-full divide-y divide-border text-sm">
              <thead className="bg-background text-left text-text-secondary">
                <tr>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">Role</th>
                  <th className="px-4 py-3 font-medium">Unit</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {users.map((user) => (
                  <tr key={user.name}>
                    <td className="px-4 py-3 text-text">{user.name}</td>
                    <td className="px-4 py-3 text-text-secondary">{user.role}</td>
                    <td className="px-4 py-3 text-text-secondary">{user.unit}</td>
                    <td className="px-4 py-3 text-text-secondary">{user.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-text">Permission model</h2>
          <div className="mt-4 space-y-3">
            {permissions.map((item) => (
              <div key={item.scope} className="rounded-xl border border-border bg-background p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-medium text-text">{item.scope}</p>
                    <p className="mt-1 text-sm text-text-secondary">Roles: {item.roles}</p>
                  </div>
                  <span className="rounded-full border border-border bg-surface px-3 py-1 text-xs text-text-secondary">
                    {item.inheritance}
                  </span>
                </div>
              </div>
            ))}
          </div>

          <div className="mt-5 rounded-xl border border-border bg-background p-4">
            <p className="text-sm font-medium text-text">System health</p>
            <ul className="mt-3 space-y-2 text-sm text-text-secondary">
              <li>Keycloak connection: simulated</li>
              <li>Document service: waiting for backend APIs</li>
              <li>Search index: frontend preview mode</li>
            </ul>
          </div>
        </section>
      </div>
    </div>
  );
};

export default AdminPage;