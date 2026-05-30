import { Link } from "react-router-dom";

/**
 * Renders a clickable folder trail. The last entry is the current folder.
 * entries: [{ id, name }]
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
