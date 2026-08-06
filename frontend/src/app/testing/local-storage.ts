/**
 * An in-memory `localStorage` for unit tests.
 *
 * The test runner's DOM ships a `localStorage` object that throws on every
 * method, so anything constructing AuthService (which restores the signed-in
 * user on creation) blows up before the component under test even renders.
 * Installing this shim keeps those tests about the component.
 */
export function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  const shim: Storage = {
    get length(): number {
      return store.size;
    },
    clear: () => store.clear(),
    getItem: (key: string) => store.get(key) ?? null,
    key: (index: number) => [...store.keys()][index] ?? null,
    removeItem: (key: string) => void store.delete(key),
    setItem: (key: string, value: string) => void store.set(key, String(value)),
  };
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: shim });
}
