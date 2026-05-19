import { Link, Outlet, useNavigate } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import { useAuth } from "../context/AuthContext";

const Layout = () => {
  const { user, roles, logout } = useAuth();
  const navigate = useNavigate();

  const displayName = user?.preferred_username ?? user?.name ?? "User";
  const primaryRole = roles.find((r) => r.startsWith("dms_")) ?? "No role";

  function handleSearch(e) {
    if (e.key === "Enter" && e.target.value.trim()) {
      navigate(`/search?q=${encodeURIComponent(e.target.value.trim())}`);
    }
  }

  return (
    <div className="flex h-screen overflow-hidden bg-background text-text">
      <Sidebar />

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-20 items-center justify-between border-b border-border bg-surface px-6 shadow-sm">
          <Link to="/dashboard" className="text-lg font-semibold tracking-wide text-text">
            [ DMS ]
          </Link>

          <div className="w-full max-w-xl px-6">
            <input
              type="search"
              placeholder="Global Search…"
              onKeyDown={handleSearch}
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none transition placeholder:text-text-secondary focus:border-primary"
            />
          </div>

          <div className="flex items-center gap-3">
            <div className="rounded-xl border border-border bg-background px-4 py-3 text-sm text-text-secondary">
              {primaryRole.replace("dms_", "")}
            </div>
            <div className="rounded-xl border border-border bg-background px-4 py-3 text-sm text-text font-medium">
              {displayName}
            </div>
            <button
              onClick={logout}
              className="rounded-xl border border-border bg-background px-4 py-3 text-sm text-text-secondary hover:text-text hover:bg-surface transition"
            >
              Sign out
            </button>
          </div>
        </header>

        <main className="min-h-0 flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default Layout;
