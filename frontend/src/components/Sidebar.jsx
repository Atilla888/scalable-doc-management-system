import { NavLink } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const baseNavItems = [
  { label: "Dashboard",  to: "/dashboard" },
  { label: "Documents",  to: "/documents" },
  { label: "Search",     to: "/search" },
  { label: "Upload",     to: "/upload" },
  { label: "OCR Status", to: "/ocr-status" },
  { label: "API / CMIS", to: "/api-cmis" },
];

const Sidebar = () => {
  const { hasRole } = useAuth();

  const navItems = [
    ...baseNavItems,
    ...(hasRole("dms_admin") ? [{ label: "Admin", to: "/admin" }] : []),
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
