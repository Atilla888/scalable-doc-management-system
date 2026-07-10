/**
 * @module pages/AdminPage
 * Administration console showing Keycloak users, realm roles, permission scopes,
 * component health, and aggregate metrics from the backend overview endpoint,
 * plus management of the department registry (create, rename, activate,
 * deactivate, delete) and per-user department assignment.
 */
import { useState } from "react";
import {
  assignUserDepartment,
  createDepartment,
  deleteDepartment,
  getAdminOverview,
  listDepartments,
  updateDepartment,
} from "../api/admin";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";
import useApiResource from "../hooks/useApiResource";

/**
 * Coerces a value to an array (empty array if it is not one).
 * @param {*} value Candidate value.
 * @returns {Array<*>}
 */
function asArray(value) {
  return Array.isArray(value) ? value : [];
}

/**
 * Health status pill: green for "UP", red otherwise.
 * @param {Object} props
 * @param {string} [props.status] Component status.
 * @returns {JSX.Element}
 */
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

/**
 * Full-width placeholder row shown when a table has no data.
 * @param {Object} props
 * @param {number} props.columns Number of columns to span.
 * @param {string} props.message Message to display.
 * @returns {JSX.Element}
 */
function EmptyRow({ columns, message }) {
  return (
    <tr>
      <td colSpan={columns} className="px-5 py-8 text-center text-text-secondary">
        {message}
      </td>
    </tr>
  );
}

/**
 * Dismissible success/error banner shown above the departments table.
 * @param {Object} props
 * @param {{type: string, text: string}|null} props.message Banner content.
 * @returns {JSX.Element|null}
 */
function ActionBanner({ message }) {
  if (!message) return null;
  const isError = message.type === "error";
  return (
    <p
      role="status"
      className={`mx-5 mt-4 rounded-xl border px-4 py-3 text-sm ${
        isError
          ? "border-red-300 bg-red-50 text-red-700"
          : "border-emerald-300 bg-emerald-50 text-emerald-800"
      }`}
    >
      {message.text}
    </p>
  );
}

/**
 * Department registry management: list, create, rename, activate/deactivate,
 * and delete departments through the admin API.
 * @param {Object} props
 * @param {Array<Object>} props.departments Current registry entries.
 * @param {*} props.error Load error, if any.
 * @param {boolean} props.loading Whether the list is loading.
 * @param {Function} props.onChanged Called after any successful mutation.
 * @returns {JSX.Element}
 */
function DepartmentsSection({ departments, error, loading, onChanged }) {
  const [form, setForm] = useState({ code: "", displayName: "" });
  const [editingCode, setEditingCode] = useState(null);
  const [editName, setEditName] = useState("");
  const [busy, setBusy] = useState(null);
  const [message, setMessage] = useState(null);

  /**
   * Runs one mutation with busy/message bookkeeping.
   * @param {string} busyKey Which control to disable while running.
   * @param {Function} action Async mutation to run.
   * @param {string} successText Banner text on success.
   */
  const run = async (busyKey, action, successText) => {
    setBusy(busyKey);
    setMessage(null);
    try {
      await action();
      setMessage({ type: "success", text: successText });
      onChanged();
    } catch (err) {
      setMessage({ type: "error", text: err?.detail || err?.message || "The request failed." });
    } finally {
      setBusy(null);
    }
  };

  const handleCreate = (event) => {
    event.preventDefault();
    const code = form.code.trim();
    if (!code) {
      setMessage({ type: "error", text: "Enter a department code first." });
      return;
    }
    run(
      "create",
      async () => {
        await createDepartment({ code, displayName: form.displayName.trim() });
        setForm({ code: "", displayName: "" });
      },
      `Department '${code.toUpperCase()}' was created.`,
    );
  };

  const handleRename = (code) => {
    const displayName = editName.trim();
    if (!displayName) {
      setMessage({ type: "error", text: "The display name must not be blank." });
      return;
    }
    run(
      `edit:${code}`,
      async () => {
        await updateDepartment(code, { displayName });
        setEditingCode(null);
      },
      `Department '${code}' was renamed.`,
    );
  };

  const handleToggleActive = (department) => {
    const activating = !department.active;
    run(
      `toggle:${department.code}`,
      () => updateDepartment(department.code, { active: activating }),
      `Department '${department.code}' was ${activating ? "activated" : "deactivated"}.`,
    );
  };

  const handleDelete = (code) => {
    if (
      !window.confirm(
        `Delete department '${code}'? This is only possible while no users, folders, documents, or ACLs reference it.`,
      )
    ) {
      return;
    }
    run(
      `delete:${code}`,
      () => deleteDepartment(code),
      `Department '${code}' was deleted.`,
    );
  };

  return (
    <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
      <div className="border-b border-border px-5 py-4">
        <h2 className="text-lg font-semibold text-text">Departments</h2>
        <p className="mt-1 text-sm text-text-secondary">
          Registry of department codes referenced by user accounts, folder and document
          ACLs, and JWT department claims. Codes are immutable; deactivate a department
          instead of deleting it while it is still referenced.
        </p>
      </div>

      <ActionBanner message={message} />

      <form onSubmit={handleCreate} className="flex flex-wrap items-end gap-3 px-5 py-4">
        <div className="w-40">
          <label htmlFor="department-code" className="mb-1 block text-sm font-medium text-text">
            Code
          </label>
          <input
            id="department-code"
            type="text"
            value={form.code}
            onChange={(event) => setForm((f) => ({ ...f, code: event.target.value }))}
            placeholder="e.g. FIN"
            disabled={busy === "create"}
            className="w-full rounded-xl border border-border bg-background px-4 py-2.5 text-sm text-text focus:border-primary focus:outline-none"
          />
        </div>
        <div className="w-64 grow sm:grow-0">
          <label
            htmlFor="department-display-name"
            className="mb-1 block text-sm font-medium text-text"
          >
            Display name (optional)
          </label>
          <input
            id="department-display-name"
            type="text"
            value={form.displayName}
            onChange={(event) => setForm((f) => ({ ...f, displayName: event.target.value }))}
            placeholder="e.g. Finance department"
            disabled={busy === "create"}
            className="w-full rounded-xl border border-border bg-background px-4 py-2.5 text-sm text-text focus:border-primary focus:outline-none"
          />
        </div>
        <button
          type="submit"
          disabled={busy === "create"}
          className="rounded-xl border border-border bg-primary px-5 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-primary/90 disabled:opacity-60"
        >
          {busy === "create" ? "Creating…" : "Create department"}
        </button>
      </form>

      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-border text-sm">
          <thead className="bg-background text-left text-text-secondary">
            <tr>
              <th className="px-5 py-3 font-medium">Code</th>
              <th className="px-5 py-3 font-medium">Display name</th>
              <th className="px-5 py-3 font-medium">Status</th>
              <th className="px-5 py-3 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {loading ? (
              <EmptyRow columns={4} message="Loading departments…" />
            ) : error ? (
              <EmptyRow
                columns={4}
                message={error?.detail || error?.message || "Departments could not be loaded."}
              />
            ) : departments.length === 0 ? (
              <EmptyRow columns={4} message="No departments exist yet. Create the first one above." />
            ) : (
              departments.map((department) => {
                const isEditing = editingCode === department.code;
                const rowBusy = busy != null && busy.endsWith(`:${department.code}`);
                return (
                  <tr key={department.code} className="align-top">
                    <td className="px-5 py-4 font-medium text-text">{department.code}</td>
                    <td className="px-5 py-4 text-text-secondary">
                      {isEditing ? (
                        <div className="flex flex-wrap items-center gap-2">
                          <input
                            type="text"
                            value={editName}
                            onChange={(event) => setEditName(event.target.value)}
                            disabled={rowBusy}
                            aria-label={`New display name for ${department.code}`}
                            className="w-56 rounded-xl border border-border bg-background px-3 py-1.5 text-sm text-text focus:border-primary focus:outline-none"
                          />
                          <button
                            onClick={() => handleRename(department.code)}
                            disabled={rowBusy}
                            className="rounded-xl border border-border bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary/90 disabled:opacity-60"
                          >
                            {rowBusy ? "Saving…" : "Save"}
                          </button>
                          <button
                            onClick={() => setEditingCode(null)}
                            disabled={rowBusy}
                            className="rounded-xl border border-border bg-background px-3 py-1.5 text-xs font-medium text-text hover:bg-surface disabled:opacity-60"
                          >
                            Cancel
                          </button>
                        </div>
                      ) : (
                        department.displayName
                      )}
                    </td>
                    <td className="px-5 py-4">
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-medium ${
                          department.active
                            ? "bg-emerald-50 text-emerald-700"
                            : "bg-slate-100 text-slate-600"
                        }`}
                      >
                        {department.active ? "Active" : "Inactive"}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex flex-wrap gap-2">
                        {!isEditing && (
                          <button
                            onClick={() => {
                              setEditingCode(department.code);
                              setEditName(department.displayName || "");
                            }}
                            disabled={rowBusy}
                            className="rounded-xl border border-border bg-background px-3 py-1.5 text-xs font-medium text-text hover:bg-surface disabled:opacity-60"
                          >
                            Edit name
                          </button>
                        )}
                        <button
                          onClick={() => handleToggleActive(department)}
                          disabled={rowBusy}
                          className="rounded-xl border border-border bg-background px-3 py-1.5 text-xs font-medium text-text hover:bg-surface disabled:opacity-60"
                        >
                          {busy === `toggle:${department.code}`
                            ? "Saving…"
                            : department.active
                              ? "Deactivate"
                              : "Activate"}
                        </button>
                        <button
                          onClick={() => handleDelete(department.code)}
                          disabled={rowBusy}
                          className="rounded-xl border border-red-300 bg-red-50 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-60"
                        >
                          {busy === `delete:${department.code}` ? "Deleting…" : "Delete"}
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

/**
 * Department dropdown for one user row; assigning writes the user's Keycloak
 * attribute through the admin API.
 * @param {Object} props
 * @param {Object} props.user Keycloak user summary.
 * @param {Array<Object>} props.departments Registry entries (active ones are offered).
 * @param {Function} props.onChanged Called after a successful assignment.
 * @param {Function} props.onError Called with a message when assignment fails.
 * @returns {JSX.Element}
 */
function UserDepartmentSelect({ user, departments, onChanged, onError }) {
  const [saving, setSaving] = useState(false);
  const options = departments.filter(
    (department) => department.active || department.code === user.department,
  );

  const handleChange = async (event) => {
    const value = event.target.value;
    setSaving(true);
    try {
      await assignUserDepartment(user.id, value || null);
      onChanged();
    } catch (err) {
      onError(err?.detail || err?.message || "The department assignment failed.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex items-center gap-2">
      <select
        value={user.department || ""}
        onChange={handleChange}
        disabled={saving}
        aria-label={`Department of ${user.username}`}
        className="rounded-xl border border-border bg-background px-3 py-1.5 text-sm text-text focus:border-primary focus:outline-none disabled:opacity-60"
      >
        <option value="">No department</option>
        {options.map((department) => (
          <option key={department.code} value={department.code}>
            {department.code}
            {department.active ? "" : " (inactive)"}
          </option>
        ))}
      </select>
      {saving && <span className="text-xs text-text-secondary">Saving…</span>}
    </div>
  );
}

/**
 * Admin console page; fetches and renders the system administration overview
 * and the department registry.
 * @returns {JSX.Element}
 */
const AdminPage = () => {
  const { data, error, loading, reload } = useApiResource(
    (signal) => getAdminOverview(signal),
    [],
  );
  const {
    data: departmentData,
    error: departmentsError,
    loading: departmentsLoading,
    reload: reloadDepartments,
  } = useApiResource((signal) => listDepartments(signal), []);
  const [assignmentError, setAssignmentError] = useState(null);

  if (loading) return <LoadingState label="Loading administration data…" />;
  if (error) return <ApiErrorPanel error={error} onRetry={reload} />;

  const users = asArray(data?.users);
  const roles = asArray(data?.roles);
  const permissionScopes = asArray(data?.permissionScopes);
  const health = asArray(data?.health);
  const metrics = data?.metrics ?? { users: 0, documents: 0, folders: 0 };
  const departments = asArray(departmentData);

  const reloadAll = () => {
    reload();
    reloadDepartments();
  };

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
            onClick={reloadAll}
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

      <DepartmentsSection
        departments={departments}
        error={departmentsError}
        loading={departmentsLoading}
        onChanged={reloadAll}
      />

      <section className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
        <div className="border-b border-border px-5 py-4">
          <h2 className="text-lg font-semibold text-text">Keycloak users</h2>
          <p className="mt-1 text-sm text-text-secondary">
            Enabled state, realm roles, and department attributes from the live realm.
            Changing a department updates the user&apos;s Keycloak attribute (new tokens
            carry the new claim after the next sign-in or refresh).
          </p>
        </div>
        {assignmentError && (
          <p
            role="alert"
            className="mx-5 mt-4 rounded-xl border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700"
          >
            {assignmentError}
          </p>
        )}
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
                      <UserDepartmentSelect
                        user={user}
                        departments={departments}
                        onChanged={() => {
                          setAssignmentError(null);
                          reloadAll();
                        }}
                        onError={setAssignmentError}
                      />
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
