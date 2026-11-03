import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";

import type { UserResponse } from "@/types/api";

import { authApi, type LoginInput } from "./api";
import { AuthContext, type AuthContextValue, type AuthStatus, type SignedOutReason } from "./auth-context";
import { resumeSession, settleBoot } from "./resume";
import { clearSession, onSessionEvent, storeTokens } from "./session";

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<UserResponse | null>(null);
  const [signedOutReason, setSignedOutReason] = useState<SignedOutReason | null>(null);

  const endSession = useCallback(
    (reason?: SignedOutReason) => {
      clearSession();
      settleBoot(null);
      queryClient.clear();
      setUser(null);
      setSignedOutReason(reason ?? null);
      setStatus("anonymous");
    },
    [queryClient],
  );

  useEffect(() => {
    let active = true;
    void resumeSession().then((resumed) => {
      if (!active) return;
      setUser(resumed);
      setStatus(resumed ? "authenticated" : "anonymous");
    });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => onSessionEvent(() => endSession("expired")), [endSession]);

  const login = useCallback(async (input: LoginInput) => {
    const tokens = await authApi.login(input);
    storeTokens(tokens);
    const me = await authApi.me();
    settleBoot(me);
    setUser(me);
    setSignedOutReason(null);
    setStatus("authenticated");
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      endSession();
    }
  }, [endSession]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, signedOutReason, login, logout, endSession, setUser }),
    [status, user, signedOutReason, login, logout, endSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
