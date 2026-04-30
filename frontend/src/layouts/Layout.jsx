import React from "react";
import { Link, Outlet } from "react-router-dom";
import Sidebar from "../components/Sidebar";

const Layout = () => {
  return (
    <div className="flex h-screen overflow-hidden bg-background text-text">
      <Sidebar />

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex h-20 items-center justify-between border-b border-border bg-surface px-6 shadow-sm">
          <Link to="/" className="text-lg font-semibold tracking-wide text-text">
            [ DMS LOGO ]
          </Link>

          <div className="w-full max-w-xl px-6">
            <input
              type="search"
              placeholder="Global Search..."
              className="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm outline-none transition placeholder:text-text-secondary focus:border-primary"
            />
          </div>

          <div className="flex items-center gap-3">
            <div className="rounded-xl border border-border bg-background px-4 py-3 text-sm text-text-secondary">
              Role: Clerk
            </div>
            <div className="rounded-xl border border-border bg-background px-4 py-3 text-sm text-text-secondary">
              Profile
            </div>
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