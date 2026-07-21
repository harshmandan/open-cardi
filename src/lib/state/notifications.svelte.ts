import { browser } from '$app/environment';

export type NotificationKind = 'error';

export type Notification = {
	id: string;
	kind: NotificationKind;
	message: string;
};

const AUTO_DISMISS_MS = 5000;

export const notifications = $state<{ list: Notification[] }>({ list: [] });

// eslint-disable-next-line svelte/prefer-svelte-reactivity -- setTimeout handles for auto-dismiss, not reactive state
const timers = new Map<string, ReturnType<typeof setTimeout>>();

function nextId(): string {
	return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
}

export function pushNotification(kind: NotificationKind, message: string): string {
	const id = nextId();
	notifications.list = [...notifications.list, { id, kind, message }];
	if (browser) {
		timers.set(
			id,
			setTimeout(() => dismissNotification(id), AUTO_DISMISS_MS)
		);
	}
	return id;
}

export function pushError(message: string): string {
	return pushNotification('error', message);
}

export function dismissNotification(id: string) {
	const t = timers.get(id);
	if (t) {
		clearTimeout(t);
		timers.delete(id);
	}
	notifications.list = notifications.list.filter((n) => n.id !== id);
}

export function clearNotifications() {
	for (const t of timers.values()) clearTimeout(t);
	timers.clear();
	notifications.list = [];
}
