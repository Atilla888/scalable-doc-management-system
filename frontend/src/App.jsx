import { Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import Home from "./pages/Home";
import Dashboard from "./pages/Dashboard";
import Layout from "./layouts/Layout";
import DocumentsPage from "./pages/DocumentsPage";
import SearchPage from "./pages/SearchPage";
import UploadPage from "./pages/UploadPage";
import OCRStatusPage from "./pages/OCRStatusPage";
import AdminPage from "./pages/AdminPage";
import ApiCmisPage from "./pages/ApiCmisPage";

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="documents" element={<DocumentsPage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="ocr-status" element={<OCRStatusPage />} />
        <Route path="admin" element={<AdminPage />} />
        <Route path="api-cmis" element={<ApiCmisPage />} />
      </Route>
      <Route path="/login" element={<Login />} />
    </Routes>
  )
}