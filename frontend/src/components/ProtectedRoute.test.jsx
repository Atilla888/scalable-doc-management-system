import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";

const authState = { value: {} };
vi.mock("../context/AuthContext", () => ({
  useAuth: () => authState.value,
}));

const oauthCallback = { value: false };
vi.mock("../auth/keycloakAuth", () => ({
  isOAuthCallback: () => oauthCallback.value,
}));

import ProtectedRoute from "./ProtectedRoute";

function renderGuard(requiredRole) {
  return render(
    <MemoryRouter initialEntries={["/secret"]}>
      <Routes>
        <Route element={<ProtectedRoute requiredRole={requiredRole} />}>
          <Route path="/secret" element={<div>secret content</div>} />
        </Route>
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/unauthorized" element={<div>unauthorized page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    oauthCallback.value = false;
  });

  it("shows a loading state until Keycloak initializes", () => {
    authState.value = { initialized: false, authenticated: false, hasRole: () => false };
    renderGuard();
    expect(screen.getByText(/Initializing/i)).toBeInTheDocument();
  });

  it("redirects unauthenticated users to /login", () => {
    authState.value = { initialized: true, authenticated: false, hasRole: () => false };
    renderGuard();
    expect(screen.getByText("login page")).toBeInTheDocument();
  });

  it("shows a completing state during the OAuth callback instead of redirecting", () => {
    authState.value = { initialized: true, authenticated: false, hasRole: () => false };
    oauthCallback.value = true;
    renderGuard();
    expect(screen.getByText(/Completing sign in/i)).toBeInTheDocument();
  });

  it("renders the protected content for an authenticated user", () => {
    authState.value = { initialized: true, authenticated: true, hasRole: () => true };
    renderGuard();
    expect(screen.getByText("secret content")).toBeInTheDocument();
  });

  it("redirects to /unauthorized when the required role is missing", () => {
    authState.value = { initialized: true, authenticated: true, hasRole: () => false };
    renderGuard("dms_admin");
    expect(screen.getByText("unauthorized page")).toBeInTheDocument();
  });
});
