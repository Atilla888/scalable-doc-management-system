import React from "react";

const SectionPage = ({ title, description }) => {
  return (
    <div className="space-y-6">
      <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <p className="text-sm font-medium text-text-secondary">Section</p>
        <h1 className="mt-2 text-3xl font-semibold text-text">{title}</h1>
        <p className="mt-3 max-w-2xl text-text-secondary">{description}</p>
      </div>

      <div className="rounded-2xl border border-dashed border-border bg-surface p-8 text-text-secondary shadow-sm">
        This is the {title.toLowerCase()} workspace. Replace this panel with the real page content when you wire the feature.
      </div>
    </div>
  );
};

export default SectionPage;