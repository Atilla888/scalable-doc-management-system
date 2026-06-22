import { useCallback, useEffect, useState } from "react";

/**
 * Small data-fetching helper: tracks loading/error/data for an async loader.
 * `loader` receives an AbortSignal. Re-runs whenever any value in `deps` changes.
 */
export default function useApiResource(loader, deps = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    setLoading(true);
    setError(null);

    Promise.resolve(loader(controller.signal))
      .then((result) => {
        if (active) {
          setData(result);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active && err?.name !== "AbortError") {
          setError(err);
          setLoading(false);
        }
      });

    return () => {
      active = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadKey]);

  return { data, error, loading, reload };
}
