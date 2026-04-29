import React from "react";
import { Link } from "react-router";
import Dashboard from "./Dashboard";
const SidebarItem = ({ label, active }) => (
  <div
    className={`px-4 py-2 rounded cursor-pointer border ${
      active ? "bg-primary/10 border-primary text-primary" : "hover:bg-gray-100"
    }`}
  >
    ■ {label}
  </div>
);
const Home = () => {
  return (
    <div className="h-screen flex flex-col bg-background text-text">
      {/* 🔝 Topbar */}
      <div className="flex items-center justify-between border-b bg-surface px-4 py-3">
        {/* Logo */}
        <div className="border px-4 py-2 font-semibold">[ DMS LOGO ]</div>

        {/* Search */}
        <input
          type="text"
          placeholder="Global Search..."
          className="border px-4 py-2 w-1/2 rounded"
        />

        {/* Role + Profile */}
        <div className="flex items-center gap-4">
          <div className="border px-4 py-2">Role: Clerk</div>
          <div className="border px-4 py-2">Profile ■</div>
        </div>
      </div>

      {/* 🧱 Main Layout */}
      <div className="flex flex-1">
        {/* Sidebar */}
        <div className="w-64 border-r bg-surface p-4 space-y-3">
          <Link to="/dashboard" >
          <SidebarItem label="Dashboard" active />
          </Link>
          <SidebarItem label="Documents" />
          <SidebarItem label="Search" />
          <SidebarItem label="Upload" />
          <SidebarItem label="OCR Status" />
          <SidebarItem label="Admin" />
          <SidebarItem label="API / CMIS" />
        </div>

        {/* Content Area */}
        <div className="flex-1 p-6 space-y-6">
          {/* Content Block 1 */}
          <div className="border-2 border-dashed h-32 flex items-center justify-center text-gray-400">
            [ content block 1 ]
          </div>

          {/* Content Block 2 */}
          <div className="border-2 border-dashed h-32 flex items-center justify-center text-gray-400">
            [ content block 2 ]
          </div>

          {/* Content Block 3 */}
          <div className="border-2 border-dashed h-64 flex items-center justify-center text-gray-400">
            [ content block 3 ]
          </div>
        </div>
      </div>
    </div>
  );
};

export default Home;
