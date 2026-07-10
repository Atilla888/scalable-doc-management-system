/**
 * @module components/LoadingState
 * Simple centered loading placeholder panel.
 */

/**
 * Renders a loading placeholder with a customizable label.
 * @param {Object} props
 * @param {string} [props.label="Loading…"] Text to display.
 * @returns {JSX.Element}
 */
const LoadingState = ({ label = "Loading…" }) => (
  <div className="flex items-center justify-center rounded-2xl border border-border bg-surface p-10 text-sm text-text-secondary">
    {label}
  </div>
);

export default LoadingState;
