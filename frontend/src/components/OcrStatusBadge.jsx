const STYLES = {
  pending: "border-amber-300 bg-amber-50 text-amber-700",
  processing: "border-blue-300 bg-blue-50 text-blue-700",
  completed: "border-emerald-300 bg-emerald-50 text-emerald-700",
  failed: "border-red-300 bg-red-50 text-red-700",
  not_required: "border-border bg-background text-text-secondary",
};

const LABELS = {
  pending: "OCR pending",
  processing: "OCR processing",
  completed: "OCR completed",
  failed: "OCR failed",
  not_required: "OCR not required",
};

const OcrStatusBadge = ({ status }) => {
  const key = (status || "").toLowerCase();
  const style = STYLES[key] || "border-border bg-background text-text-secondary";
  const label = LABELS[key] || status || "Unknown";
  return (
    <span className={`inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium ${style}`}>
      {label}
    </span>
  );
};

export default OcrStatusBadge;
