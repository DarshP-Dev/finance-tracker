export type Notification = {
  id: number;
  type: "BUDGET" | "RECURRING" | "SYSTEM";
  severity: "INFO" | "WARNING" | "ERROR";
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  targetPath: string;
  relatedEntityId: number | null;
};

export type NotificationPage = { notifications: Notification[]; page: number; hasMore: boolean };
