/**
 * @module components/FolderContents
 * Table listing a folder's subfolders and documents, with optional management
 * actions (rename/move/delete) for privileged users.
 */
import { Link } from "react-router-dom";
import OcrStatusBadge from "./OcrStatusBadge";

/**
 * Formats an ISO date/time string for display, falling back gracefully.
 * @param {string} [value] Date value.
 * @returns {string} Localized string, the raw value, or "—".
 */
function formatDate(value) {
  if (!value) return "—";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

/**
 * Renders the subfolders and documents the backend returned for a folder.
 * Nothing is filtered client-side — the list reflects exactly what the API
 * allowed the current user to see.
 * @param {Object} props
 * @param {{subfolders?: Array<Object>, documents?: Array<Object>}} props.view Folder view payload.
 * @param {boolean} [props.canManage=false] Whether to show folder management actions.
 * @param {(folder: Object) => void} [props.onRename] Rename handler.
 * @param {(folder: Object) => void} [props.onMove] Move handler.
 * @param {(folder: Object) => void} [props.onDelete] Delete handler.
 * @returns {JSX.Element}
 */
const FolderContents = ({ view, canManage = false, onRename, onMove, onDelete }) => {
  const subfolders = view?.subfolders ?? [];
  const documents = view?.documents ?? [];
  const isEmpty = subfolders.length === 0 && documents.length === 0;
  const showActions = canManage && (onRename || onMove || onDelete);

  if (isEmpty) {
    return (
      <div className="rounded-2xl border border-border bg-surface p-10 text-center text-sm text-text-secondary">
        This folder is empty.
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
      <table className="min-w-full divide-y divide-border text-sm">
        <thead className="bg-background text-left text-text-secondary">
          <tr>
            <th className="px-5 py-3 font-medium">Name</th>
            <th className="px-5 py-3 font-medium">EAP number</th>
            <th className="px-5 py-3 font-medium">Type</th>
            <th className="px-5 py-3 font-medium">OCR</th>
            <th className="px-5 py-3 font-medium">Uploaded</th>
            {showActions && <th className="px-5 py-3 font-medium text-right">Actions</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {subfolders.map((folder) => (
            <tr key={`folder-${folder.id}`} className="hover:bg-background/60">
              <td className="px-5 py-3">
                <Link to={`/folders/${folder.id}`} className="font-medium text-primary hover:underline">
                  📁 {folder.name === "/" ? "Root" : folder.name}
                </Link>
              </td>
              <td className="px-5 py-3 text-text-secondary">—</td>
              <td className="px-5 py-3 text-text-secondary">Folder</td>
              <td className="px-5 py-3 text-text-secondary">—</td>
              <td className="px-5 py-3 text-text-secondary">—</td>
              {showActions && (
                <td className="px-5 py-3">
                  <div className="flex justify-end gap-3">
                    {onRename && (
                      <button
                        type="button"
                        onClick={() => onRename(folder)}
                        className="text-sm font-medium text-primary hover:underline"
                      >
                        Rename
                      </button>
                    )}
                    {onMove && (
                      <button
                        type="button"
                        onClick={() => onMove(folder)}
                        className="text-sm font-medium text-primary hover:underline"
                      >
                        Move
                      </button>
                    )}
                    {onDelete && (
                      <button
                        type="button"
                        onClick={() => onDelete(folder)}
                        className="text-sm font-medium text-red-600 hover:underline"
                      >
                        Delete
                      </button>
                    )}
                  </div>
                </td>
              )}
            </tr>
          ))}
          {documents.map((doc) => (
            <tr key={`doc-${doc.id}`} className="hover:bg-background/60">
              <td className="px-5 py-3">
                <Link to={`/documents/${doc.id}`} className="font-medium text-text hover:underline">
                  📄 {doc.title}
                </Link>
              </td>
              <td className="px-5 py-3 text-text-secondary">{doc.eapNumber}</td>
              <td className="px-5 py-3 text-text-secondary">{doc.documentType}</td>
              <td className="px-5 py-3">
                <OcrStatusBadge status={doc.ocrStatus} />
              </td>
              <td className="px-5 py-3 text-text-secondary">{formatDate(doc.uploadDate)}</td>
              {showActions && <td className="px-5 py-3" />}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default FolderContents;
