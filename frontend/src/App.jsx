import { Navigate, Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Layout from "./layouts/Layout";
import DocumentsPage from "./pages/DocumentsPage";
import SearchPage from "./pages/SearchPage";
import UploadPage from "./pages/UploadPage";
import OCRStatusPage from "./pages/OCRStatusPage";
import AdminPage from "./pages/AdminPage";
import ApiCmisPage from "./pages/ApiCmisPage";
import ProtectedRoute from "./components/ProtectedRoute";

function Unauthorized() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 text-center">
      <div>
        <p className="text-sm font-medium text-text-secondary">403</p>
        <h1 className="mt-2 text-2xl font-semibold text-text">Access Denied</h1>
        <p className="mt-3 text-sm text-text-secondary">
          You do not have the required role to view this page.
        </p>
        <a href="/dashboard" className="mt-6 inline-block text-sm text-primary underline">
          Back to Dashboard
        </a>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login" element={<Login />} />
      <Route path="/unauthorized" element={<Unauthorized />} />

      {/* Protected routes — all authenticated users */}
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="documents" element={<DocumentsPage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="upload" element={<UploadPage />} />
          <Route path="ocr-status" element={<OCRStatusPage />} />
          <Route path="api-cmis" element={<ApiCmisPage />} />

          {/* Admin-only route */}
          <Route element={<ProtectedRoute requiredRole="dms_admin" />}>
            <Route path="admin" element={<AdminPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  );
}
