import {
	DEVICE_NAME_PREFIXES,
	HANDSHAKE_WRITES,
	NOTIFY_CHARACTERISTIC,
	SERVICE_UUID,
	WRITE_CHARACTERISTIC
} from '$lib/protocol/constants';

export type ConnectionState = 'disconnected' | 'requesting' | 'connecting' | 'connected';

export type CardiClientEvents = {
	onState?: (state: ConnectionState) => void;
	onNotify?: (data: Uint8Array) => void;
	onError?: (err: Error) => void;
};

export class CardiClient {
	private device: BluetoothDevice | null = null;
	private writeChar: BluetoothRemoteGATTCharacteristic | null = null;
	private notifyChar: BluetoothRemoteGATTCharacteristic | null = null;
	private events: CardiClientEvents;
	private state: ConnectionState = 'disconnected';
	// Tail of the GATT write chain. Every send() awaits the previous write so
	// Web Bluetooth never sees overlapping operations (which Chrome rejects with
	// "GATT operation in progress" and can drop the link on Android).
	private writeTail: Promise<unknown> = Promise.resolve();

	constructor(events: CardiClientEvents = {}) {
		this.events = events;
	}

	get connected(): boolean {
		return this.state === 'connected';
	}

	get deviceName(): string | null {
		return this.device?.name ?? null;
	}

	private setState(s: ConnectionState) {
		this.state = s;
		this.events.onState?.(s);
	}

	static isSupported(): boolean {
		return typeof navigator !== 'undefined' && !!navigator.bluetooth;
	}

	async connect(): Promise<void> {
		if (!CardiClient.isSupported()) {
			throw new Error('Web Bluetooth is not available in this browser');
		}

		this.setState('requesting');
		this.device = await navigator.bluetooth.requestDevice({
			filters: [
				{ services: [SERVICE_UUID] },
				...DEVICE_NAME_PREFIXES.map((namePrefix) => ({ namePrefix }))
			],
			optionalServices: [SERVICE_UUID]
		});

		this.device.addEventListener('gattserverdisconnected', () => {
			this.writeChar = null;
			this.notifyChar = null;
			this.writeTail = Promise.resolve();
			this.setState('disconnected');
		});

		this.setState('connecting');
		this.writeTail = Promise.resolve();
		const server = await this.device.gatt!.connect();
		const service = await server.getPrimaryService(SERVICE_UUID);
		this.writeChar = await service.getCharacteristic(WRITE_CHARACTERISTIC);
		this.notifyChar = await service.getCharacteristic(NOTIFY_CHARACTERISTIC);

		await this.notifyChar.startNotifications();
		this.notifyChar.addEventListener('characteristicvaluechanged', (e) => {
			const v = (e.target as BluetoothRemoteGATTCharacteristic).value;
			if (v) this.events.onNotify?.(new Uint8Array(v.buffer));
		});

		await this.runHandshake();
		this.setState('connected');
	}

	async disconnect(): Promise<void> {
		try {
			this.device?.gatt?.disconnect();
		} catch {
			// ignore
		}
		this.writeChar = null;
		this.notifyChar = null;
		this.writeTail = Promise.resolve();
		this.setState('disconnected');
	}

	async send(payload: Uint8Array): Promise<void> {
		if (!this.writeChar) throw new Error('not connected');
		const w = this.writeChar;
		const next = this.writeTail.then(
			() => w.writeValueWithoutResponse(payload as BufferSource),
			() => w.writeValueWithoutResponse(payload as BufferSource)
		);
		this.writeTail = next.catch(() => undefined);
		await next;
	}

	private async runHandshake(): Promise<void> {
		for (const w of HANDSHAKE_WRITES) {
			await this.send(w);
			await sleep(100);
		}
	}
}

function sleep(ms: number): Promise<void> {
	return new Promise((r) => setTimeout(r, ms));
}
