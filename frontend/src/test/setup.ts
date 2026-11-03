import "@testing-library/jest-dom/vitest";

import { cleanup, configure } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";

import { resetBootForTests } from "@/features/auth/resume";
import { resetSessionForTests } from "@/features/auth/session";

import { server } from "./server";

// Route modules load lazily on first use; give the first findBy* in a test room for that.
configure({ asyncUtilTimeout: 5000 });

// Radix UI relies on these browser APIs, which jsdom does not implement.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver = ResizeObserverStub;
Element.prototype.scrollIntoView = () => {};
Element.prototype.hasPointerCapture = () => false;
Element.prototype.releasePointerCapture = () => {};
window.matchMedia = (query: string) =>
  ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }) as unknown as MediaQueryList;

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetSessionForTests();
  resetBootForTests();
  localStorage.clear();
});
afterAll(() => server.close());
