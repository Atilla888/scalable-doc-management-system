import { Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import Home from "./pages/Home";
import Dashboard from "./pages/Dashboard";
import Layout from "./layouts/Layout";
import SectionPage from "./pages/SectionPage";
import DocumentsPage from "./pages/DocumentsPage";

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="documents" element={<DocumentsPage />} />
        <Route
          path="search"
          element={
            <SectionPage
              title="Search"
              description="Use this area to look up files, metadata, and records across the system."
            />
          }
        />
        <Route
          path="upload"
          element={
            <SectionPage
              title="Upload"
              description="Send new files into the workflow and monitor ingestion progress here."
            />
          }
        />
        <Route
          path="ocr-status"
          element={
            <SectionPage
              title="OCR Status"
              description="Track recognition jobs, failures, and processing progress in one place."
            />
          }
        />
        <Route
          path="admin"
          element={
            <SectionPage
              title="Admin"
              description="Manage users, permissions, and system settings from this administrative view."
            />
          }
        />
        <Route
          path="api-cmis"
          element={
            <SectionPage
              title="API / CMIS"
              description="Review integration endpoints and CMIS connectivity details here."
            />
          }
        />
      </Route>
      <Route path="/login" element={<Login />} />
    </Routes>
  )
}