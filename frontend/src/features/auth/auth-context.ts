import { createContext, useContext } from "react";

import type { UserResponse } from "@/types/api";

import type { LoginInput } from "./api";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

/** Why a session ended without the user signing out; shown on the login page. */
export type SignedOutReason = "expired" | "password-changed";

export interface AuthContextValue {
  status: AuthStatus;
  user: UserResponse | null;
  /** Why the user is anonymous, so the login page can explain it. */
  signedOutReason: SignedOutReason | null;
  login: (input: LoginInput) => Promise<UserResponse>;
  logout: () => Promise<void>;
  /** Drops local session state without calling the backend (e.g. after a password change revoked it). */
  endSession: (reason?: SignedOutReason) => void;
  setUser: (user: UserResponse) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside <AuthProvider>");
  return context;
}

/** The signed-in user; only call below a route guard that guarantees authentication. */
export function useCurrentUser(): UserResponse {
  const { user } = useAuth();
  if (!user) throw new Error("useCurrentUser called without an authenticated user");
  return user;
}
