/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SUPABASE_URL: string;
  readonly VITE_SUPABASE_ANON_KEY: string;
  readonly VITE_MAP_STYLE?: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
