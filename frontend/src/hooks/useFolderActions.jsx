import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import {
  createFolder,
  deleteFolder,
  getFolderTree,
  updateFolder,
} from "../api/folders";

// Roles allowed to manage folders in the UI. The backend enforces the real
// rules (RBAC); hiding the buttons here is just UX — a viewer never sees them.
const MANAGE_ROLES = ["dms_admin", "dms_department_manager", "dms_contributor"];

/**
 * Shared create/rename/move/delete folder behaviour for any page that lists
 * folders. Returns the role gate, the action handlers, and the modal element to
 * render. After every successful change it calls `reload()` so the tree
 * refreshes.
 */
export default function useFolderActions(reload) {
  const { roles } = useAuth();
  const canManage = MANAGE_ROLES.some((role) => roles.includes(role));

  const [dialog, setDialog] = useState(null); // { mode: "create" | "rename" | "move", parentId?, folder? }
  const [name, setName] = useState("");
  const [destination, setDestination] = useState("");
  const [options, setOptions] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const openCreate = (parentId) => {
    setDialog({ mode: "create", parentId });
    setName("");
    setError(null);
  };

  const openRename = (folder) => {
    setDialog({ mode: "rename", folder });
    setName(folder.name === "/" ? "" : folder.name);
    setError(null);
  };

  const openMove = async (folder) => {
    setDialog({ mode: "move", folder });
    setDestination("");
    setOptions([]);
    setError(null);
    try {
      const all = await getFolderTree();
      // Exclude the folder itself and its descendants — you cannot move a
      // folder into its own subtree (the backend rejects it too).
      const prefix = folder.path;
      setOptions(
        all.filter(
          (f) => f.id !== folder.id && !(prefix && f.path && f.path.startsWith(prefix)),
        ),
      );
    } catch (err) {
      setError(err?.detail || err?.message || "Could not load folders.");
    }
  };

  const close = () => {
    if (!busy) setDialog(null);
  };

  const submit = async (event) => {
    event.preventDefault();
    setError(null);

    if (dialog.mode === "move") {
      if (!destination) {
        setError("Please choose a destination folder.");
        return;
      }
      setBusy(true);
      try {
        await updateFolder(dialog.folder.id, { parentId: destination });
        setDialog(null);
        reload();
      } catch (err) {
        setError(err?.detail || err?.message || "Could not move folder.");
      } finally {
        setBusy(false);
      }
      return;
    }

    const trimmed = name.trim();
    if (!trimmed) {
      setError("Please enter a folder name.");
      return;
    }
    setBusy(true);
    try {
      if (dialog.mode === "create") {
        await createFolder({ name: trimmed, parentId: dialog.parentId });
      } else {
        await updateFolder(dialog.folder.id, { name: trimmed });
      }
      setDialog(null);
      reload();
    } catch (err) {
      setError(err?.detail || err?.message || "Operation failed.");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (folder) => {
    if (!window.confirm(`Delete folder "${folder.name}"?`)) return;
    try {
      await deleteFolder(folder.id, false);
      reload();
    } catch (err) {
      // Non-empty folder → offer to delete recursively.
      if (err?.status === 409) {
        if (window.confirm("This folder is not empty. Delete it and all its contents?")) {
          try {
            await deleteFolder(folder.id, true);
            reload();
          } catch (inner) {
            window.alert(inner?.detail || inner?.message || "Could not delete folder.");
          }
        }
        return;
      }
      window.alert(err?.detail || err?.message || "Could not delete folder.");
    }
  };

  const labelFor = (folder) => (folder.path === "/" ? "Root (/)" : folder.path);
  const title =
    dialog?.mode === "rename"
      ? "Rename folder"
      : dialog?.mode === "move"
        ? "Move folder"
        : "New folder";

  const dialogElement = dialog ? (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      onClick={close}
    >
      <form
        onSubmit={submit}
        onClick={(event) => event.stopPropagation()}
        className="w-full max-w-md space-y-4 rounded-2xl border border-border bg-surface p-6 shadow-lg"
      >
        <h2 className="text-lg font-semibold text-text">{title}</h2>

        {dialog.mode === "move" ? (
          <div className="space-y-2">
            <p className="text-sm text-text-secondary">
              Move <span className="font-medium">{dialog.folder.name}</span> to:
            </p>
            <select
              value={destination}
              onChange={(event) => setDestination(event.target.value)}
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm text-text focus:border-primary focus:outline-none"
            >
              <option value="">Select destination…</option>
              {options.map((folder) => (
                <option key={folder.id} value={folder.id}>
                  {labelFor(folder)}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <input
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Folder name"
            autoFocus
            className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm text-text focus:border-primary focus:outline-none"
          />
        )}

        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={close}
            disabled={busy}
            className="rounded-xl border border-border bg-surface px-5 py-2.5 text-sm font-medium text-text hover:bg-background disabled:opacity-60"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={busy}
            className="rounded-xl border border-border bg-primary px-5 py-2.5 text-sm font-medium text-white shadow-sm hover:bg-primary/90 disabled:opacity-60"
          >
            {busy ? "Saving…" : "Save"}
          </button>
        </div>
      </form>
    </div>
  ) : null;

  return { canManage, openCreate, openRename, openMove, remove, dialogElement };
}
