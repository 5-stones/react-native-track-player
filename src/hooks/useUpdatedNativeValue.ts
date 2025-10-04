import { useEffect, useState } from 'react';

/**
 * Generic hook for values that are fetched from native and updated via callbacks.
 *
 * @param getter - Synchronous function to get the current value from native
 * @param subscribe - Function that takes a callback and returns a cleanup function
 * @returns The current value, updated when the callback fires
 *
 * Note: While it is fetching the initial value from the native module, the
 * returned value will be whatever the getter returns (may be undefined or a default).
 */
export function useUpdatedNativeValue<T>(
  getter: () => T,
  subscribe: (callback: (value: T) => void) => () => void
): T {
  const [value, setValue] = useState(() => getter());

  useEffect(() => {
    return subscribe(setValue);
  }, [subscribe]);

  return value;
}
