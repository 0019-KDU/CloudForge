import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api/client";
import type {
  DepartmentResponse,
  InvitationResponse,
  InvitationStatus,
  IssuedInvitationResponse,
  PageResponse,
  PasswordResetResponse,
  Role,
  TeamResponse,
  UserResponse,
  UserStatus,
  UUID,
} from "@/types/api";

/** Optimistic concurrency: send the version we edited, the backend answers 412 if it changed. */
const ifMatch = (version: number | null | undefined) =>
  version === null || version === undefined ? {} : { headers: { "If-Match": `"${version}"` } };

export interface UserSearch {
  status?: UserStatus | undefined;
  teamId?: UUID | undefined;
  managerId?: UUID | undefined;
  page?: number;
  size?: number;
  sort?: string | undefined;
}

/** Walks every page; used for pickers (teams, departments, managers) where the full set is small. */
async function fetchAll<T>(load: (page: number) => Promise<PageResponse<T>>, maxPages = 20): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; page < maxPages; page++) {
    const result = await load(page);
    items.push(...result.content);
    if (page + 1 >= result.totalPages) break;
  }
  return items;
}

export const adminApi = {
  users: (params: UserSearch) => api.get<PageResponse<UserResponse>>("/admin/users", { params }).then((r) => r.data),
  user: (id: UUID) => api.get<UserResponse>(`/admin/users/${id}`).then((r) => r.data),
  assignManager: (user: UserResponse, managerId: UUID | null) =>
    api.put<UserResponse>(`/admin/users/${user.id}/manager`, { managerId }, ifMatch(user.version)).then((r) => r.data),
  assignTeam: (user: UserResponse, teamId: UUID | null) =>
    api.put<UserResponse>(`/admin/users/${user.id}/team`, { teamId }, ifMatch(user.version)).then((r) => r.data),
  replaceRoles: (user: UserResponse, roles: Role[]) =>
    api.put<UserResponse>(`/admin/users/${user.id}/roles`, { roles }, ifMatch(user.version)).then((r) => r.data),
  disable: (user: UserResponse) =>
    api.post<UserResponse>(`/admin/users/${user.id}/disable`, null, ifMatch(user.version)).then((r) => r.data),
  enable: (user: UserResponse) =>
    api.post<UserResponse>(`/admin/users/${user.id}/enable`, null, ifMatch(user.version)).then((r) => r.data),
  issuePasswordReset: (id: UUID) =>
    api.post<PasswordResetResponse>(`/admin/users/${id}/password-reset`).then((r) => r.data),

  invitations: (params: { status?: InvitationStatus | undefined; page?: number; size?: number; sort?: string | undefined }) =>
    api.get<PageResponse<InvitationResponse>>("/admin/invitations", { params }).then((r) => r.data),
  invite: (input: { email: string; roles: Role[]; managerId: UUID | null; teamId: UUID | null }) =>
    api.post<IssuedInvitationResponse>("/admin/invitations", input).then((r) => r.data),
  revokeInvitation: (id: UUID) => api.post<InvitationResponse>(`/admin/invitations/${id}/revoke`).then((r) => r.data),

  departments: (page = 0, size = 100) =>
    api.get<PageResponse<DepartmentResponse>>("/admin/departments", { params: { page, size } }).then((r) => r.data),
  createDepartment: (name: string) => api.post<DepartmentResponse>("/admin/departments", { name }).then((r) => r.data),
  renameDepartment: (department: DepartmentResponse, name: string) =>
    api.put<DepartmentResponse>(`/admin/departments/${department.id}/name`, { name }, ifMatch(department.version)).then((r) => r.data),

  teams: (page = 0, size = 100) => api.get<PageResponse<TeamResponse>>("/admin/teams", { params: { page, size } }).then((r) => r.data),
  createTeam: (departmentId: UUID, name: string) => api.post<TeamResponse>("/admin/teams", { departmentId, name }).then((r) => r.data),
  renameTeam: (team: TeamResponse, name: string) =>
    api.put<TeamResponse>(`/admin/teams/${team.id}/name`, { name }, ifMatch(team.version)).then((r) => r.data),

  allDepartments: () => fetchAll((page) => adminApi.departments(page)),
  allTeams: () => fetchAll((page) => adminApi.teams(page)),
  allActiveUsers: () => fetchAll((page) => adminApi.users({ status: "ACTIVE", page, size: 100, sort: "displayName,asc" })),
};

export const adminKeys = {
  users: ["admin", "users"] as const,
  userList: (params: UserSearch) => [...adminKeys.users, "list", params] as const,
  user: (id: UUID) => [...adminKeys.users, "detail", id] as const,
  activeUsers: () => [...adminKeys.users, "active"] as const,
  invitations: ["admin", "invitations"] as const,
  departments: ["admin", "departments"] as const,
  teams: ["admin", "teams"] as const,
};

export function useUsers(params: UserSearch) {
  return useQuery({ queryKey: adminKeys.userList(params), queryFn: () => adminApi.users(params), placeholderData: keepPreviousData });
}

export function useAdminUser(id: UUID) {
  return useQuery({ queryKey: adminKeys.user(id), queryFn: () => adminApi.user(id) });
}

export function useDepartments() {
  return useQuery({ queryKey: adminKeys.departments, queryFn: adminApi.allDepartments });
}

export function useTeams() {
  return useQuery({ queryKey: adminKeys.teams, queryFn: adminApi.allTeams });
}

/** Active users holding MANAGER: the only eligible managers (the backend rejects others). */
export function useManagerCandidates() {
  return useQuery({
    queryKey: adminKeys.activeUsers(),
    queryFn: adminApi.allActiveUsers,
    select: (users) => users.filter((user) => user.roles.includes("MANAGER")),
  });
}

/** Every user change also changes lists, pickers and names shown elsewhere. */
export function useInvalidateUsers() {
  const queryClient = useQueryClient();
  return (user?: UserResponse) => {
    if (user) queryClient.setQueryData(adminKeys.user(user.id), user);
    void queryClient.invalidateQueries({ queryKey: adminKeys.users });
    void queryClient.invalidateQueries({ queryKey: ["directory"] });
  };
}

export function useOrganizationMutations() {
  const queryClient = useQueryClient();
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: adminKeys.departments });
    void queryClient.invalidateQueries({ queryKey: adminKeys.teams });
  };
  return {
    createDepartment: useMutation({ mutationFn: adminApi.createDepartment, onSuccess: refresh }),
    renameDepartment: useMutation({
      mutationFn: ({ department, name }: { department: DepartmentResponse; name: string }) => adminApi.renameDepartment(department, name),
      onSuccess: refresh,
    }),
    createTeam: useMutation({
      mutationFn: ({ departmentId, name }: { departmentId: UUID; name: string }) => adminApi.createTeam(departmentId, name),
      onSuccess: refresh,
    }),
    renameTeam: useMutation({
      mutationFn: ({ team, name }: { team: TeamResponse; name: string }) => adminApi.renameTeam(team, name),
      onSuccess: refresh,
    }),
  };
}
