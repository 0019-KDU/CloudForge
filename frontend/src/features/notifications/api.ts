import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api/client";
import type { NotificationResponse, PageResponse, UUID } from "@/types/api";

export const notificationsApi = {
  list: (unreadOnly: boolean, size = 10) =>
    api
      .get<PageResponse<NotificationResponse>>("/notifications", { params: { unreadOnly, page: 0, size } })
      .then((r) => r.data),
  unreadCount: () => api.get<{ unread: number }>("/notifications/unread-count").then((r) => r.data.unread),
  markRead: (id: UUID) => api.post<NotificationResponse>(`/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => api.post<{ marked: number }>("/notifications/read-all").then((r) => r.data),
};

export const notificationKeys = {
  all: ["notifications"] as const,
  count: () => [...notificationKeys.all, "count"] as const,
  list: () => [...notificationKeys.all, "list"] as const,
};

/** There is no push channel: poll the count, and refetch on window focus. */
export function useUnreadCount() {
  return useQuery({
    queryKey: notificationKeys.count(),
    queryFn: notificationsApi.unreadCount,
    refetchInterval: 60_000,
    staleTime: 15_000,
  });
}

export function useNotifications(enabled: boolean) {
  return useQuery({ queryKey: notificationKeys.list(), queryFn: () => notificationsApi.list(false), enabled });
}

export function useNotificationMutations() {
  const queryClient = useQueryClient();
  const onSuccess = () => void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
  return {
    markRead: useMutation({ mutationFn: notificationsApi.markRead, onSuccess }),
    markAllRead: useMutation({ mutationFn: notificationsApi.markAllRead, onSuccess }),
  };
}
