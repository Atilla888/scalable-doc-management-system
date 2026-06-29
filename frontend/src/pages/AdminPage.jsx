import { getAdminOverview } from "../api/admin";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";
import useApiResource from "../hooks/useApiResource";

function asArray(value) {
  return Array.isArray(value) ? value : [];
}

function StatusBadge({ status }) {
  const healthy = status === "UP";
  return (
    <span
      className={`rounded-full px-3 py-1 text-xs font-medium ${
        healthy
          ? "bg-emerald-50 text-emerald-700"
          : "bg-red-50 text-red-700"
      }`}
    >
      {status || "UNKNOWN"}
    </span>
  );
}

function EmptyRow({ columns, message }) {
  return (
    <tr>
      <td colSpan={columns} className="px-5 py-8 text-center text-text-secondary">
        {message}
      </td>
    </tr>
  );
}

const AdminPage = () => {
  const { data, error, loading, reload } = useApiResource(
    (signal) => getAdminOverview(signal),
    [],
  );

  if (loading) return <LoadingState label="Loading administration data…" />;
  if (error) return <ApiErrorPanel error={error} onRetry={reload} />;

  const users = asArray(data?.users);
  const roles = asArray(data?.roles);
  const permissionScopes = asArray(data?.permissionScopes);
  const health = asArray(data?.health);
  const metrics = data?.metrics ?? { users: 0, documents: 0, folders: 0 };

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-text-secondary">Admin</p>
            <h1 className="mt-2 text-3xl font-semibold text-text">
              System administration console
            </h1>
            <p className="mt-3 max-w-3xl text-text-secondary">
              Live identity, permission, and service information from Keycloak
              and the DMS backend.
            </p>
          </div>
          <button
            onClick={reload}
            className="rounded-xl border border-border bg-background px-5 py-2 text-sm font-medium text-text hover:bg-surface"
          >
            Refresh
          </button>
        </div>
      </section>

      <div className="grid gap-4 sm:grid-cols-3">
        {[
          ["Users", metrics.users],
          ["Documents", metrics.documents],
          ["Folders", metrics.folders],
        ].map(([label, value]) => (
          <section
            key={label}
            className="rounded-2xl border border-border bg-surface p-5 shadow-sm"
          >
            <p className="text-sm text-text-secondary">{label}</p>
            <p className="mt-2 text-3xl font-semibold text-text">{value}</p>
          </section>
        ))}
      </div>

      <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-lg font-semibold text-text">Keycloak users</h2>
          <p className="mt-1 text-sm text-text-secondary">
            Enabled state, realm roles, and department attributes from the live realm.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-border text-sm">
            <thead className="bg-background text-left text-text-secondary">
              <tr>
                <th className="px-5 py-3 font-medium">User</th>
                <th className="px-5 py-3 font-medium">Roles</th>
                <th className="px-5 py-3 font-medium">Department</th>
                <th className="px-5 py-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {users.length === 0 ? (
                <EmptyRow
                  columns={4}
                  message="No users are available from Keycloak. Check the health section below."
                />
              ) : (
                users.map((user) => (
                  <tr key={user.id || user.username} className="align-top">
                    <td className="px-5 py-4">
                      <p className="font-medium text-text">{user.displayName}</p>
                      <p className="mt-1 text-xs text-text-secondary">
                        {user.email || user.username}
                      </p>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex flex-wrap gap-1.5">
                        {asArray(user.roles).length === 0 ? (
                          <span className="text-text-secondary">No DMS role</span>
                        ) : (
                          asArray(user.roles).map((role) => (
                            <span
                              key={role}
                              className="rounded-full border border-border bg-background px-2.5 py-1 text-xs text-text-secondary"
                            >
                              {role}
                            </span>
                          ))
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-4 text-text-secondary">
                      {user.department || "—"}
                    </td>
                    <td className="px-5 py-4">
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-medium ${
                          user.enabled
                            ? "bg-emerald-50 text-emerald-700"
                            : "bg-slate-100 text-slate-600"
                        }`}
                      >
                        {user.enabled ? "Enabled" : "Disabled"}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-text">Realm roles</h2>
          <div className="mt-4 space-y-3">
            {roles.length === 0 ? (
              <p className="rounded-xl border border-border bg-background p-4 text-sm text-text-secondary">
                No DMS roles are available from Keycloak.
              </p>
            ) : (
              roles.map((role) => (
                <div
                  key={role.name}
                  className="rounded-xl border border-border bg-background p-4"
                >
                  <p className="font-medium text-text">{role.name}</p>
                  <p className="mt-1 text-sm text-text-secondary">
                    {role.description || "No description configured in Keycloak."}
                  </p>
                </div>
              ))
            )}
          </div>
        </section>

        <section className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-text">System health</h2>
          <div className="mt-4 space-y-3">
            {health.length === 0 ? (
              <p className="rounded-xl border border-border bg-background p-4 text-sm text-text-secondary">
                No component health data was returned by the backend.
              </p>
            ) : (
              health.map((item) => (
                <div
                  key={item.component}
                  className="flex items-start justify-between gap-4 rounded-xl border border-border bg-background p-4"
                >
                  <div>
                    <p className="font-medium text-text">{item.component}</p>
                    <p className="mt-1 text-sm text-text-secondary">{item.detail}</p>
                  </div>
                  <StatusBadge status={item.status} />
                </div>
              ))
            )}
          </div>
        </section>
      </div>

      <section className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-text">Permission scopes</h2>
        <p className="mt-1 text-sm text-text-secondary">
          Operations enforced by the backend RBAC and ACL permission resolver.
        </p>
        <div className="mt-4 grid gap-3 lg:grid-cols-2">
          {permissionScopes.length === 0 ? (
            <p className="rounded-xl border border-border bg-background p-4 text-sm text-text-secondary">
              No permission scopes were returned by the backend.
            </p>
          ) : (
            permissionScopes.map((scope) => (
              <div
                key={`${scope.resource}-${scope.name}`}
                className="rounded-xl border border-border bg-background p-4"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-medium text-text">{scope.name}</p>
                    <p className="mt-1 text-xs uppercase tracking-wide text-text-secondary">
                      {scope.resource}
                    </p>
                  </div>
                  <span className="rounded-full border border-border bg-surface px-3 py-1 text-xs text-text-secondary">
                    Enforced
                  </span>
                </div>
                <p className="mt-3 text-sm text-text-secondary">{scope.description}</p>
                <p className="mt-3 text-xs text-text-secondary">
                  Granted by: {asArray(scope.grantedBy).join(", ") || "Not reported"}
                </p>
              </div>
            ))
          )}
        </div>
      </section>
    </div>
  );
};

export default AdminPage;
