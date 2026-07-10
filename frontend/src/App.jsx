/**
 * @module App
 * Top-level route table: public routes (login, unauthorized) and protected
 * routes wrapped in {@link ProtectedRoute} and the shared {@link Layout}.
 */
import { Navigate, Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Layout from "./layouts/Layout";
import FolderView from "./pages/FolderView";
import DocumentDetail from "./pages/DocumentDetail";
import SearchPage from "./pages/SearchPage";
import UploadPage from "./pages/UploadPage";
import AdminPage from "./pages/AdminPage";
import OCRStatusPage from "./pages/OCRStatusPage";
import ProtectedRoute from "./components/ProtectedRoute";

/**
 * Full-page 403 shown when a user lacks the role required for a route.
 * @returns {JSX.Element}
 */
function Unauthorized() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 text-center">
      <div>
        <p className="text-sm font-medium text-text-secondary">403</p>
        <h1 className="mt-2 text-2xl font-semibold text-text">Access Denied</h1>
        <p className="mt-3 text-sm text-text-secondary">
          You do not have the required role to view this page.
        </p>
        <a href="/" className="mt-6 inline-block text-sm text-primary underline">
          Back to Dashboard
        </a>
      </div>
    </div>
  );
}

/**
 * Root application component defining the client-side route hierarchy.
 * @returns {JSX.Element}
 */
export default function App() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login" element={<Login />} />
      <Route path="/unauthorized" element={<Unauthorized />} />

      {/* Protected routes — all authenticated users */}
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="folders/:id" element={<FolderView />} />
          <Route path="documents/:id" element={<DocumentDetail />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="upload" element={<UploadPage />} />

          {/* Admin-only route */}
          <Route element={<ProtectedRoute requiredRole="dms_admin" />}>
            <Route path="admin" element={<AdminPage />} />
            <Route path="admin/ocr" element={<OCRStatusPage />} />
          </Route>
        </Route>
      </Route>

      {/* Unknown paths fall back to the dashboard */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
