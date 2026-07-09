export interface RuntimeConfig {
  keycloakUrl?: string;
  keycloakClientId?: string;
}

declare global {
  interface Window {
    __FINYO_CONFIG__?: RuntimeConfig;
  }
}

/**
 * Deployment config injected at container start via /config.js — allows one
 * Docker image to run in any environment. Empty values fall through to the
 * build-time defaults.
 */
export function getRuntimeConfig(): RuntimeConfig {
  const raw = globalThis.window?.__FINYO_CONFIG__ ?? {};
  return {
    keycloakUrl: raw.keycloakUrl || undefined,
    keycloakClientId: raw.keycloakClientId || undefined,
  };
}
