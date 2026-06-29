import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { createFolder, getFolder } from "../api/folders";
import useApiResource from "../hooks/useApiResource";
import FolderContents from "../components/FolderContents";
import Breadcrumb from "../components/Breadcrumb";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";

const FolderView = () => {
  const { id } = useParams();
  const { data, error, loading, reload } = useApiResource(
    (signal) => getFolder(id, signal),
    [id],
  );

  const [dialogOpen, setDialogOpen] = useState(false);
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [createError, setCreateError] = useState(null);

  const folderName = data?.folder?.name === "/" ? "Root" : data?.folder?.name;

  const openDialog = () => {
    setName("");
    setCreateError(null);
    setDialogOpen(true);
  };

  const closeDialog = () => {
    if (!submitting) setDialogOpen(false);
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setCreateError("Please enter a folder name.");
      return;
    }
    setSubmitting(true);
    setCreateError(null);
    try {
      await createFolder({ name: trimmed, parentId: id });
      setDialogOpen(false);
      reload();
    } catch (err) {
      setCreateError(err?.detail || err?.message || "Could not create folder.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-2">
          {data && <Breadcrumb entries={data.breadcrumb} />}
          <h1 className="text-3xl font-semibold text-text">{folderName ?? "Folder"}</h1>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={openDialog}
            className="rounded-xl border border-border bg-surface px-5 py-3 text-sm font-medium text-text shadow-sm hover:bg-background"
          >
            New folder
          </button>
          <Link
            to={`/upload?parentId=${encodeURIComponent(id)}`}
            className="rounded-xl border border-border bg-primary px-5 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90"
          >
            Upload here
          </Link>
        </div>
      </div>

      {loading && <LoadingState label="Loading folder…" />}
      {!loading && error && <ApiErrorPanel error={error} onRetry={reload} />}
      {!loading && !error && data && <FolderContents view={data} />}

      {dialogOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
          role="dialog"
          aria-modal="true"
          onClick={closeDialog}
        >
          <form
            onSubmit={handleCreate}
            onClick={(event) => event.stopPropagation()}
            className="w-full max-w-md space-y-4 rounded-2xl border border-border bg-surface p-6 shadow-lg"
          >
            <h2 className="text-lg font-semibold text-text">New folder</h2>
            <p className="text-sm text-text-secondary">
              Creating inside <span className="font-medium">{folderName ?? "this folder"}</span>.
            </p>
            <input
              type="text"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Folder name"
              autoFocus
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm text-text focus:border-primary focus:outline-none"
            />
            {createError && <p className="text-sm text-red-600">{createError}</p>}
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={closeDialog}
                disabled={submitting}
                className="rounded-xl border border-border bg-surface px-5 py-2.5 text-sm font-medium text-text hover:bg-background disabled:opacity-60"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="rounded-xl border border-border bg-primary px-5 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-primary/90 disabled:opacity-60"
              >
                {submitting ? "Creating…" : "Create"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};

export default FolderView;
