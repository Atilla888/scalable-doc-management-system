/**
 * @module components/Sidebar
 * Left navigation sidebar; admin-only links appear for users with the
 * `dms_admin` role.
 */
import { NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const baseNavItems = [
  { label: "Dashboard", to: "/", end: true },
  { label: "Search",    to: "/search" },
  { label: "Upload",    to: "/upload" },
];

/**
 * Renders the primary navigation, appending admin links for admins.
 * @returns {JSX.Element}
 */
const Sidebar = () => {
  const { hasRole } = useAuth();

  const navItems = [
    ...baseNavItems,
    ...(hasRole("dms_admin")
      ? [
          { label: "Admin", to: "/admin" },
          { label: "OCR Queue", to: "/admin/ocr" },
        ]
      : []),
  ];

  return (
    <aside className="flex h-full w-72 shrink-0 flex-col overflow-y-auto border-r border-border bg-surface">
      <div className="border-b border-border px-5 py-5">
        <div className="inline-flex items-center rounded-xl border border-border bg-background px-4 py-3 text-sm font-semibold tracking-wide text-text">
          DMS
        </div>
      </div>

      <nav className="flex-1 space-y-2 px-4 py-5">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              [
                "block rounded-xl border px-4 py-3 text-sm font-medium transition",
                isActive
                  ? "border-primary bg-primary/10 text-primary shadow-sm"
                  : "border-transparent text-text-secondary hover:border-border hover:bg-background hover:text-text",
              ].join(" ")
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  );
};

export default Sidebar;
