import React from 'react'

const Login = () => {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-4 text-text">
      <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-8 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">Sign in</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">Document management system</h1>
        <p className="mt-3 text-sm text-text-secondary">
          This route remains outside the shared layout so authentication can take over the full screen.
        </p>
      </div>
    </div>
  )
}

export default Login