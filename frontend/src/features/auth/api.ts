import { api } from "@/lib/api/client";
import type { RegisteredUserResponse, TokenResponse, UserResponse } from "@/types/api";

export interface LoginInput {
  email: string;
  password: string;
}

export interface RegisterInput {
  invitationToken: string;
  displayName: string;
  password: string;
}

export interface PasswordResetInput {
  token: string;
  newPassword: string;
}

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

export const authApi = {
  login: (input: LoginInput) => api.post<TokenResponse>("/auth/login", input).then((r) => r.data),
  logout: () => api.post("/auth/logout").then(() => undefined),
  register: (input: RegisterInput) =>
    api.post<RegisteredUserResponse>("/auth/register", input).then((r) => r.data),
  resetPassword: (input: PasswordResetInput) => api.post("/auth/password-reset", input).then(() => undefined),

  me: () => api.get<UserResponse>("/users/me").then((r) => r.data),
  updateProfile: (displayName: string) => api.put<UserResponse>("/users/me", { displayName }).then((r) => r.data),
  /** Revokes every session, including the caller's. */
  changePassword: (input: ChangePasswordInput) => api.post("/users/me/password", input).then(() => undefined),
};
