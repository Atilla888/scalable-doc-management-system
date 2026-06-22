import { Link, useParams } from "react-router-dom";
import { getFolder } from "../api/folders";
import useApiResource from "../hooks/useApiResource";
import FolderContents from "../components/FolderContents";
import Breadcrumb from "../components/Breadcrumb";
import ApiErrorPanel from "../components/ApiErrorPanel";
import LoadingState from "../components/LoadingState";

const FolderView = () => {
  const { id } = useParams();
  const { data, error, loading, reload } = useApiResource(
    (signal) => getFolder(id, signal),
    [id],
  );

  const folderName = data?.folder?.name === "/" ? "Root" : data?.folder?.name;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-2">
          {data && <Breadcrumb entries={data.breadcrumb} />}
          <h1 className="text-3xl font-semibold text-text">{folderName ?? "Folder"}</h1>
        </div>
        <Link
          to={`/upload?parentId=${encodeURIComponent(id)}`}
          className="rounded-xl border border-border bg-primary px-5 py-3 text-sm font-medium text-white shadow-sm hover:bg-primary/90"
        >
          Upload here
        </Link>
      </div>

      {loading && <LoadingState label="Loading folder…" />}
      {!loading && error && <ApiErrorPanel error={error} onRetry={reload} />}
      {!loading && !error && data && <FolderContents view={data} />}
    </div>
  );
};

export default FolderView;
