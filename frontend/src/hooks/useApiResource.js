/**
 * @module hooks/useApiResource
 * Reusable data-fetching hook with loading/error/data state and abort handling.
 */
import { useCallback, useEffect, useState } from "react";

/**
 * Small data-fetching helper: tracks loading/error/data for an async loader.
 * `loader` receives an AbortSignal. Re-runs whenever any value in `deps` changes
 * or `reload()` is called. AbortErrors are ignored.
 * @param {(signal: AbortSignal) => Promise<*>} loader Async loader function.
 * @param {Array<*>} [deps=[]] Dependency list that re-triggers the loader.
 * @returns {{data: *, error: *, loading: boolean, reload: () => void}}
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
