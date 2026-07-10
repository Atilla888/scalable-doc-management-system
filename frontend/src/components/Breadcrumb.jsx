/**
 * @module components/Breadcrumb
 * Clickable folder-trail navigation.
 */
import { Link } from "react-router-dom";

/**
 * Renders a clickable folder trail. All but the last entry link to their
 * folder; the last entry is the current folder and is rendered as plain text.
 * @param {Object} props
 * @param {Array<{id: string, name: string}>} [props.entries=[]] Ordered trail from root to current.
 * @returns {JSX.Element|null} Null when there are no entries.
 */
const Breadcrumb = ({ entries = [] }) => {
  if (entries.length === 0) return null;
  return (
    <nav className="flex flex-wrap items-center gap-1 text-sm text-text-secondary">
      {entries.map((entry, index) => {
        const isLast = index === entries.length - 1;
        return (
          <span key={entry.id} className="flex items-center gap-1">
            {isLast ? (
              <span className="font-medium text-text">{entry.name === "/" ? "Root" : entry.name}</span>
            ) : (
              <Link to={`/folders/${entry.id}`} className="hover:text-text hover:underline">
                {entry.name === "/" ? "Root" : entry.name}
              </Link>
            )}
            {!isLast && <span className="px-1 text-text-secondary">›</span>}
          </span>
        );
      })}
    </nav>
  );
};

export default Breadcrumb;
