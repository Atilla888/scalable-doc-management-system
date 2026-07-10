/**
 * @module pages/Dashboard
 * Landing page after login: greets the user and lists the root folder's
 * contents with folder-management actions.
 */
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getRootFolder } from "../api/folders";
import useApiResource from "../hooks/useApiResource";
import useFolderActions from "../hooks/useFolderActions";
import FolderContents from "../components/FolderContents";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";

/**
 * Dashboard page showing the root folder contents.
 * @returns {JSX.Element}
 */
const Dashboard = () => {
  const { user, roles } = useAuth();
  const displayName = user?.preferred_username ?? user?.name ?? "User";
  const primaryRole = roles.find((r) => r.startsWith("dms_")) ?? "";

  const { data, error, loading, reload } = useApiResource((signal) => getRootFolder(signal), []);
  const { canManage, openCreate, openRename, openMove, remove, dialogElement } =
    useFolderActions(reload);
  const rootId = data?.folder?.id;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-text-secondary">Dashboard</p>
          <h1 className="mt-2 text-3xl font-semibold text-text">
            Welcome, {displayName}
            {primaryRole && (
              <span className="ml-3 text-base font-normal text-text-secondary">
                ({primaryRole.replace("dms_", "").replace(/_/g, " ")})
              </span>
            )}
          </h1>
        </div>
        <div className="flex items-center gap-3">
          {canManage && rootId && (
            <button
              type="button"
              onClick={() => openCreate(rootId)}
              className="rounded-xl border border-border bg-surface px-5 py-3 text-sm font-medium text-text shadow-sm hover:bg-background"
            >
              New folder
            </button>
          )}
          <Link
            to="/upload"
            className="rounded-xl border border-border bg-primary px-5 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90"
          >
            Upload document
          </Link>
        </div>
      </div>

      <div>
        <h2 className="mb-3 text-lg font-semibold text-text">Root folder</h2>
        {loading && <LoadingState label="Loading root folder…" />}
        {!loading && error && <ApiErrorPanel error={error} onRetry={reload} />}
        {!loading && !error && data && (
          <FolderContents
            view={data}
            canManage={canManage}
            onRename={openRename}
            onMove={openMove}
            onDelete={remove}
          />
        )}
      </div>

      {dialogElement}
    </div>
  );
};

export default Dashboard;
