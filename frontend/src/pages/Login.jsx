import React from 'react'

const Login = () => {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 text-text">
      <div className="grid w-full max-w-5xl gap-0 overflow-hidden rounded-3xl border border-border bg-surface shadow-sm lg:grid-cols-2">
        <div className="relative flex min-h-[520px] flex-col justify-between overflow-hidden bg-gradient-to-br from-primary via-secondary to-accent p-8 text-white">
          <div>
            <p className="text-sm font-medium uppercase tracking-[0.25em] text-white/80">Central DMS</p>
            <h1 className="mt-4 max-w-md text-4xl font-semibold leading-tight">Permission-safe document management for public authorities</h1>
            <p className="mt-4 max-w-md text-sm text-white/85">
              This frontend is ready for Keycloak integration. Authentication will be handed to the backend team and plugged into this shell later.
            </p>
          </div>

          <div className="grid gap-3 text-sm text-white/90 sm:grid-cols-3">
            <div className="rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur">
              <p className="font-semibold">Keycloak</p>
              <p className="mt-1 text-white/80">SSO handoff</p>
            </div>
            <div className="rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur">
              <p className="font-semibold">RBAC</p>
              <p className="mt-1 text-white/80">Backend enforced</p>
            </div>
            <div className="rounded-2xl border border-white/20 bg-white/10 p-4 backdrop-blur">
              <p className="font-semibold">Search</p>
              <p className="mt-1 text-white/80">Permission-safe</p>
            </div>
          </div>
        </div>

        <div className="flex items-center p-8">
          <div className="w-full space-y-6">
            <div>
              <p className="text-sm font-medium text-text-secondary">Sign in</p>
              <h2 className="mt-2 text-3xl font-semibold text-text">Continue with Keycloak</h2>
              <p className="mt-3 text-sm text-text-secondary">
                The actual login flow will be connected by the backend team. This screen keeps the frontend ready for that handoff.
              </p>
            </div>

            <button className="flex w-full items-center justify-center gap-3 rounded-2xl border border-border bg-primary px-5 py-4 text-sm font-medium text-white shadow-sm hover:bg-primary/90">
              <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-white/15 text-base font-semibold">K</span>
              Sign in with Keycloak
            </button>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-sm font-semibold text-text">Demo users</p>
                <p className="mt-2 text-sm text-text-secondary">Managed externally in Keycloak</p>
              </div>
              <div className="rounded-2xl border border-border bg-background p-4">
                <p className="text-sm font-semibold text-text">Current status</p>
                <p className="mt-2 text-sm text-text-secondary">Frontend ready, auth pending</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Login