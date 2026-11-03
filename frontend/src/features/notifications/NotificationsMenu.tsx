import { Bell, BellOff, CheckCheck, CheckCircle, Inbox, Rocket, TriangleAlert, XCircle, type LucideIcon } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router";

import { ErrorState } from "@/components/feedback/ErrorState";
import { TableSkeleton } from "@/components/feedback/Loaders";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils/cn";
import { formatRelative } from "@/lib/utils/format";
import type { NotificationResponse, NotificationType } from "@/types/api";

import { useNotificationMutations, useNotifications, useUnreadCount } from "./api";

const ICONS: Record<NotificationType, { icon: LucideIcon; className: string }> = {
  APPROVAL_REQUESTED: { icon: Inbox, className: "text-warning" },
  REQUEST_APPROVED: { icon: CheckCircle, className: "text-success" },
  REQUEST_REJECTED: { icon: XCircle, className: "text-danger" },
  PROVISIONING_SUCCEEDED: { icon: Rocket, className: "text-success" },
  PROVISIONING_FAILED: { icon: TriangleAlert, className: "text-danger" },
};

export function NotificationsMenu() {
  const [open, setOpen] = useState(false);
  const count = useUnreadCount();
  const list = useNotifications(open);
  const { markRead, markAllRead } = useNotificationMutations();
  const navigate = useNavigate();
  const unread = count.data ?? 0;

  const openNotification = (notification: NotificationResponse) => {
    if (!notification.readAt) markRead.mutate(notification.id);
    setOpen(false);
    if (notification.requestId) void navigate(`/requests/${notification.requestId}`);
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon-sm"
          className="relative"
          aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
        >
          <Bell />
          {unread > 0 ? (
            <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground tabular-nums">
              {unread > 99 ? "99+" : unread}
            </span>
          ) : null}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-[min(24rem,calc(100vw-2rem))] p-0">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <h2 className="text-sm font-semibold">Notifications</h2>
          <Button
            variant="ghost"
            size="xs"
            onClick={() => markAllRead.mutate()}
            disabled={unread === 0 || markAllRead.isPending}
          >
            <CheckCheck aria-hidden /> Mark all read
          </Button>
        </div>
        {list.isLoading ? (
          <TableSkeleton rows={3} columns={1} />
        ) : list.error ? (
          <ErrorState error={list.error} onRetry={() => void list.refetch()} className="py-6" />
        ) : !list.data?.content.length ? (
          <div className="flex flex-col items-center px-6 py-10 text-center">
            <BellOff className="mb-2 size-5 text-muted-foreground" aria-hidden />
            <p className="text-sm font-medium">No notifications</p>
            <p className="text-xs text-muted-foreground">Approvals and provisioning updates appear here.</p>
          </div>
        ) : (
          <ScrollArea className="max-h-96">
            <ul className="divide-y">
              {list.data.content.map((notification) => {
                const style = ICONS[notification.type];
                const Icon = style.icon;
                return (
                  <li key={notification.id}>
                    <button
                      type="button"
                      onClick={() => openNotification(notification)}
                      className={cn(
                        "flex w-full gap-3 px-4 py-3 text-left transition-colors hover:bg-accent focus-visible:bg-accent focus-visible:outline-none",
                        !notification.readAt && "bg-primary/[0.03]",
                      )}
                    >
                      <Icon className={cn("mt-0.5 size-4 shrink-0", style.className)} aria-hidden />
                      <span className="min-w-0 flex-1">
                        <span className="flex items-start justify-between gap-2">
                          <span className={cn("text-sm", !notification.readAt && "font-medium")}>{notification.title}</span>
                          {!notification.readAt ? (
                            <span className="mt-1.5 size-2 shrink-0 rounded-full bg-primary">
                              <span className="sr-only">Unread</span>
                            </span>
                          ) : null}
                        </span>
                        <span className="mt-0.5 line-clamp-2 block text-xs text-muted-foreground">{notification.body}</span>
                        <span className="mt-1 block text-[11px] text-muted-foreground">{formatRelative(notification.createdAt)}</span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          </ScrollArea>
        )}
      </PopoverContent>
    </Popover>
  );
}
