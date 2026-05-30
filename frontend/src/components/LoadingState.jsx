const LoadingState = ({ label = "Loading…" }) => (
  <div className="flex items-center justify-center rounded-2xl border border-border bg-surface p-10 text-sm text-text-secondary">
    {label}
  </div>
);

export default LoadingState;
