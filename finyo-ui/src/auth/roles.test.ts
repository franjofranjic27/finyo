import { describe, expect, it } from 'vitest';
import { getRealmRoles } from './roles';

function makeToken(payload: object): string {
  const base64 = btoa(JSON.stringify(payload))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${base64}.signature`;
}

describe('getRealmRoles', () => {
  it('extracts realm roles from a valid access token', () => {
    const token = makeToken({ realm_access: { roles: ['user', 'admin'] } });
    expect(getRealmRoles(token)).toEqual(['user', 'admin']);
  });

  it('returns an empty array when realm_access is missing', () => {
    const token = makeToken({ sub: 'abc' });
    expect(getRealmRoles(token)).toEqual([]);
  });

  it('returns an empty array when roles is missing', () => {
    const token = makeToken({ realm_access: {} });
    expect(getRealmRoles(token)).toEqual([]);
  });

  it('returns an empty array for undefined token', () => {
    expect(getRealmRoles(undefined)).toEqual([]);
  });

  it('returns an empty array for a malformed token', () => {
    expect(getRealmRoles('not-a-jwt')).toEqual([]);
    expect(getRealmRoles('a.%%%.c')).toEqual([]);
  });
});
