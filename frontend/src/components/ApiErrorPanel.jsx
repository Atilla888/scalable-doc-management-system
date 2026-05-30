import { ApiError } from "../api/client";

function describe(error) {
  if (error instanceof ApiError) {
    if (error.isForbidden) {
      return {
        code: "403",
        title: "Access denied",
        message:
          error.detail ||
          "You do not have permission to view this item. Contact your department manager if you need access.",
      };
    }
    if (error.isNotFound) {
      return {
        code: "404",
        title: "Not found",
        message: error.detail || "The requested item does not exist or has been deleted.",
      };
    }
    if (error.isUnauthorized) {
      return {
        code: "401",
        title: "Sign in required",
        message: error.detail || "Your session expired. Please sign in again.",
      };
    }
    return {
      code: String(error.status),
      title: "Something went wrong",
      message: error.detail || "The request could not be completed.",
    };
  }
  return {
    code: "Error",
    title: "Something went wrong",
    message: error?.message || "The request could not be completed.",
  };
}

const ApiErrorPanel = ({ error, onRetry }) => {
  const { code, title, message } = describe(error);
  return (
    <div className="rounded-2xl border border-border bg-surface p-8 text-center shadow-sm">
      <p className="text-sm font-medium text-text-secondary">{code}</p>
      <h2 className="mt-2 text-xl font-semibold text-text">{title}</h2>
      <p className="mx-auto mt-3 max-w-md text-sm text-text-secondary">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-6 rounded-xl border border-border bg-background px-5 py-2 text-sm font-medium text-text hover:bg-surface"
        >
          Try again
        </button>
      )}
    </div>
  );
};

export default ApiErrorPanel;
