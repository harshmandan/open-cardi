export const SERVICE_UUID = 0xfff0;
export const WRITE_CHARACTERISTIC = 0xffe2; // Write No Response
export const NOTIFY_CHARACTERISTIC = 0xffe1; // Notify / Read

// All known ConSmart-family device name prefixes, lifted from the official app's
// BluetoothLeService.COMPANY_NAME regex plus Dream/Flash variants seen elsewhere.
export const DEVICE_NAME_PREFIXES = [
	'iLits',
	'CarDiLits',
	'BaoYin',
	'CaChuang',
	'CarL',
	'MTF-LIGHT',
	'AIKON-LIT',
	'Dream',
	'Flash'
] as const;

export const HANDSHAKE_WRITES: readonly Uint8Array[] = [
	new Uint8Array([0x3c, 0xaa, 0xaa, 0x3d]),
	new Uint8Array([0x0e, 0xaa, 0xe0]),
	new Uint8Array([0x18, 0x00, 0x81]),
	new Uint8Array([0x17, 0x00, 0x71]),
	new Uint8Array([0x16, 0x00, 0x61])
];

export const ZONES = [
	{ id: 'overall', label: 'Overall', value: 1, supportedOn: 'all' },
	{
		id: 'guide',
		label: 'Light Guide',
		value: 2,
		supportedOn: 'all',
		subtitle: 'Front + doors on K3'
	},
	{ id: 'doors', label: 'Doors', value: 3, supportedOn: 'k4+' },
	{ id: 'floor', label: 'Floor', value: 4, supportedOn: 'all', subtitle: 'Footwell' },
	{ id: 'skylight', label: 'Skylight', value: 5, supportedOn: 'k4+' },
	{ id: 'chassis-strobe', label: 'Chassis/Strobe', value: 6, supportedOn: 'k4+' },
	{ id: 'grill', label: 'Grill', value: 7, supportedOn: 'k4+' },
	{ id: 'chassis', label: 'Chassis', value: 8, supportedOn: 'k4+' },
	{ id: 'strobe', label: 'Strobe', value: 9, supportedOn: 'k4+' }
] as const;

export type ZoneId = (typeof ZONES)[number]['id'];

// Zones hidden from the main scroll-snap by default. Surfaced via the
// "Show all zones" toggle in the More sheet for users with kits that
// expose them (chassis lighting variants on premium SKUs).
export const ZONES_HIDDEN_BY_DEFAULT: readonly ZoneId[] = ['chassis-strobe', 'chassis', 'strobe'];

export const PATTERNS = [
	{ index: 0, opcode: 0x35, label: 'Smooth color sweep' },
	{ index: 1, opcode: 0x36, label: 'Multi-color strobe' },
	{ index: 2, opcode: 0x37, label: 'Breathing' },
	{ index: 3, opcode: 0x38, label: 'Blue breathing' },
	{ index: 4, opcode: 0x39, label: 'Yellow breathing' },
	{ index: 5, opcode: 0x3a, label: 'Cyan breathing' },
	{ index: 6, opcode: 0x3b, label: 'Pink breathing' },
	{ index: 7, opcode: 0x3c, label: 'White pulse' },
	{ index: 8, opcode: 0x3d, label: 'Red ↔ green pulse' },
	{ index: 9, opcode: 0x3e, label: 'Red ↔ blue pulse' },
	{ index: 10, opcode: 0x3f, label: 'Green ↔ blue pulse' },
	{ index: 11, opcode: 0x40, label: 'Random strobe' },
	{ index: 12, opcode: 0x41, label: 'Red strobe' },
	{ index: 13, opcode: 0x42, label: 'Green strobe' },
	{ index: 14, opcode: 0x43, label: 'Blue strobe' },
	{ index: 15, opcode: 0x44, label: 'Yellow strobe' },
	{ index: 16, opcode: 0x45, label: 'Cyan strobe' },
	{ index: 17, opcode: 0x46, label: 'Pink strobe' },
	{ index: 18, opcode: 0x47, label: 'White strobe' },
	{ index: 19, opcode: 0x48, label: 'Fast color sweep' },
	{ index: 20, opcode: 0x4a, label: 'RGB flash' },
	{ index: 21, opcode: 0x4b, label: 'Smooth color cycle' },
	{ index: 22, opcode: 0x4c, label: 'RGB strobe' }
] as const;

export const PATTERN_OPCODES = PATTERNS.map((p) => p.opcode);

export const MIC_MODES = [
	{ id: 0, label: 'Classic', subtitle: 'Smooth color changes' },
	{ id: 1, label: 'Soft', subtitle: 'Softer transitions' },
	{ id: 2, label: 'Jump', subtitle: 'Off when silent, flash on sound' },
	{ id: 3, label: 'Dance', subtitle: 'Like Jump, more sensitive' }
] as const;

export type MicModeId = (typeof MIC_MODES)[number]['id'];

export const APP_MODES = [
	{ id: 'classic', label: 'Classic', value: 1 },
	{ id: 'starlight', label: 'Starlight', value: 2 }
] as const;

export type AppModeId = (typeof APP_MODES)[number]['id'];
