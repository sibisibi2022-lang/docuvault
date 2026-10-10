import { Injectable, computed, signal } from '@angular/core';
import type { AuthResponse, Role } from '../models/api.models';

const TOKEN_KEY = 'docuvault.token';
const USER_KEY = 'docuvault.user';

interface StoredSession {
  userId: number;
  name: string;
  email: string;
  role: Role;
}

/**
 * The single source of truth for "who is signed in", held in signals.
 *
 * <p>The cached role drives which navigation links render. It is a convenience
 * only — the original treated `localStorage.role` as authorization, so editing
 * one value in DevTools unlocked the admin UI. Every privileged action here is
 * re-checked by the server against the signed token, so tampering with this
 * cache changes what the user sees and nothing about what they can do.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly token = signal<string | null>(readToken());
  private readonly session = signal<StoredSession | null>(readSession());

  readonly user = this.session.asReadonly();
  readonly isAuthenticated = computed(() => this.token() !== null);
  readonly role = computed<Role | null>(() => this.session()?.role ?? null);
  readonly displayName = computed(() => this.session()?.name ?? '');

  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isManager = computed(() => this.role() === 'MANAGER');
  readonly canManageWorkspace = computed(() => this.isAdmin() || this.isManager());

  accessToken(): string | null {
    return this.token();
  }

  signIn(response: AuthResponse): void {
    const session: StoredSession = {
      userId: response.userId,
      name: response.name,
      email: response.email,
      role: response.role,
    };

    this.token.set(response.accessToken);
    this.session.set(session);

    write(TOKEN_KEY, response.accessToken);
    write(USER_KEY, JSON.stringify(session));
  }

  /** Keeps the cached name in step after a profile edit. */
  patchName(name: string): void {
    const current = this.session();
    if (!current) return;

    const updated = { ...current, name };
    this.session.set(updated);
    write(USER_KEY, JSON.stringify(updated));
  }

  signOut(): void {
    this.token.set(null);
    this.session.set(null);
    remove(TOKEN_KEY);
    remove(USER_KEY);
  }
}

// Storage access is wrapped because it throws in private browsing modes and in
// any context where site data is blocked; a failure there must not break boot.
function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function readSession(): StoredSession | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as StoredSession) : null;
  } catch {
    return null;
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* session stays in memory for this tab only */
  }
}

function remove(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    /* nothing to do */
  }
}
