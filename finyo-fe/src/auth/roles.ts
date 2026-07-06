/**
 * Extracts the Keycloak realm roles from a JWT access token.
 * Roles live in the access token (realm_access.roles), not in the ID token.
 */
export function getRealmRoles(accessToken?: string): string[] {
  if (!accessToken) return [];
  try {
    const payloadPart = accessToken.split('.')[1];
    if (!payloadPart) return [];
    const payload = JSON.parse(
      atob(payloadPart.replaceAll('-', '+').replaceAll('_', '/'))
    ) as { realm_access?: { roles?: string[] } };
    return payload.realm_access?.roles ?? [];
  } catch {
    return [];
  }
}
