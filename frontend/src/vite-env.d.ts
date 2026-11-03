/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Public. Base path of the CloudForge API, relative to the SPA origin. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
