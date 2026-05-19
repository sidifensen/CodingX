const require_chunk = require("./chunk.js");
let fs = require("fs");
fs = require_chunk.__toESM(fs);
let os = require("os");
os = require_chunk.__toESM(os);
let path = require("path");
path = require_chunk.__toESM(path);
let crypto = require("crypto");
//#region src/main/sidecar/protocol.ts
/**
* Path to the user's workbuddy config dir (~/.workbuddy or an override). Used
* both as the stable hash input for the install-scoped instance token and by
* one-shot cleanup of pre-migration sidecar files. Nothing is written here.
*/
function workbuddyConfigDir() {
	return process.env.WORKBUDDY_CONFIG_DIR?.trim() || process.env.CODEBUDDY_CONFIG_DIR?.trim() || path.join(os.homedir(), ".workbuddy");
}
/** Idempotent best-effort unlink — swallows ENOENT and the like. */
function unlinkFileSafe(filepath) {
	try {
		fs.unlinkSync(filepath);
	} catch {}
}
function instanceToken() {
	return hashToken(workbuddyConfigDir(), 12);
}
function isWritableDir(dir) {
	try {
		if (!fs.statSync(dir).isDirectory()) return false;
		fs.accessSync(dir, fs.constants.W_OK);
		return true;
	} catch {
		return false;
	}
}
/**
* Runtime directory for sockets and the sidecar PID file.
*
* Linux prefers `$XDG_RUNTIME_DIR` → `/run/user/<uid>`; elsewhere (macOS,
* Linux without XDG, Windows) falls back to `os.tmpdir()`. Kept short so
* per-session sockets stay within macOS's 104-byte sun_path limit.
*
* Not memoized: tests mutate the config-dir env between cases, and call
* frequency is low (a handful of lookups per sidecar lifecycle).
*/
function sidecarRuntimeDir() {
	const token = instanceToken();
	const uid = typeof process.getuid === "function" ? process.getuid() : void 0;
	if (process.platform === "linux") {
		const xdg = process.env.XDG_RUNTIME_DIR?.trim();
		if (xdg && isWritableDir(xdg)) return path.join(xdg, "workbuddy", token);
		if (uid !== void 0) {
			const runUser = `/run/user/${uid}`;
			if (isWritableDir(runUser)) return path.join(runUser, "workbuddy", token);
		}
	}
	const base = uid !== void 0 ? `wb-${hashToken(String(uid), 6)}` : "wb";
	return path.join(os.tmpdir(), base, token);
}
/** Create the runtime directory (0700) if missing. Idempotent. */
function ensureSidecarRuntimeDir() {
	const dir = sidecarRuntimeDir();
	fs.mkdirSync(dir, {
		recursive: true,
		mode: 448
	});
	try {
		fs.chmodSync(dir, 448);
	} catch {}
	return dir;
}
function controlSocketPath() {
	if (isNamedPipe()) return buildNamedPipePath(CONTROL_PIPE_KEY);
	return path.join(sidecarRuntimeDir(), "sidecar.sock");
}
function dataSocketPath(id) {
	if (isNamedPipe()) return buildNamedPipePath(`${DATA_PIPE_KEY}-${sanitizePipeToken(id)}-${hashToken(id, 8)}`);
	const primary = path.join(sidecarRuntimeDir(), `s-${hashToken(id, 16)}.sock`);
	if (Buffer.byteLength(primary) + SUN_PATH_SAFETY_MARGIN <= SUN_PATH_MAX_BYTES) return primary;
	const uid = typeof process.getuid === "function" ? process.getuid() : void 0;
	const base = uid !== void 0 ? `wb-${hashToken(String(uid), 6)}` : "wb";
	const shortDir = path.join("/tmp", base, instanceToken());
	try {
		fs.mkdirSync(shortDir, {
			recursive: true,
			mode: 448
		});
		try {
			fs.chmodSync(shortDir, 448);
		} catch {}
	} catch {}
	return path.join(shortDir, `s-${hashToken(id, 16)}.sock`);
}
function pidFilePath() {
	return path.join(sidecarRuntimeDir(), "sidecar.pid");
}
function isNamedPipe() {
	return process.platform === "win32";
}
function buildNamedPipePath(name) {
	return `${WINDOWS_PIPE_PREFIX}workbuddy-${instanceToken()}-${name}`;
}
function sanitizePipeToken(value) {
	return value.replace(/[^a-zA-Z0-9._-]+/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "").slice(0, 48) || "session";
}
function hashToken(value, length) {
	return (0, crypto.createHash)("sha1").update(value).digest("hex").slice(0, length);
}
var DEFAULT_RING_BUFFER_BYTES, IDLE_TIMEOUT_MS, RPC_TIMEOUT_MS, SESSION_LIFECYCLE_RPC_TIMEOUT_MS, WINDOWS_PIPE_PREFIX, CONTROL_PIPE_KEY, DATA_PIPE_KEY, SUN_PATH_MAX_BYTES, SUN_PATH_SAFETY_MARGIN, JSON_RPC_PARSE_ERROR, JSON_RPC_METHOD_NOT_FOUND, JSON_RPC_INVALID_PARAMS, JSON_RPC_INTERNAL_ERROR, JSON_RPC_SESSION_NOT_FOUND;
var init_protocol = require_chunk.__esmMin((() => {
	DEFAULT_RING_BUFFER_BYTES = 8 * 1024 * 1024;
	IDLE_TIMEOUT_MS = 1800 * 1e3;
	RPC_TIMEOUT_MS = 1e4;
	SESSION_LIFECYCLE_RPC_TIMEOUT_MS = 6e4;
	WINDOWS_PIPE_PREFIX = "\\\\.\\pipe\\";
	CONTROL_PIPE_KEY = "sidecar-control";
	DATA_PIPE_KEY = "sidecar-data";
	SUN_PATH_MAX_BYTES = 104;
	SUN_PATH_SAFETY_MARGIN = 3;
	JSON_RPC_PARSE_ERROR = -32700;
	JSON_RPC_METHOD_NOT_FOUND = -32601;
	JSON_RPC_INVALID_PARAMS = -32602;
	JSON_RPC_INTERNAL_ERROR = -32603;
	JSON_RPC_SESSION_NOT_FOUND = -32e3;
}));
//#endregion
Object.defineProperty(exports, "DEFAULT_RING_BUFFER_BYTES", {
	enumerable: true,
	get: function() {
		return DEFAULT_RING_BUFFER_BYTES;
	}
});
Object.defineProperty(exports, "IDLE_TIMEOUT_MS", {
	enumerable: true,
	get: function() {
		return IDLE_TIMEOUT_MS;
	}
});
Object.defineProperty(exports, "JSON_RPC_INTERNAL_ERROR", {
	enumerable: true,
	get: function() {
		return JSON_RPC_INTERNAL_ERROR;
	}
});
Object.defineProperty(exports, "JSON_RPC_INVALID_PARAMS", {
	enumerable: true,
	get: function() {
		return JSON_RPC_INVALID_PARAMS;
	}
});
Object.defineProperty(exports, "JSON_RPC_METHOD_NOT_FOUND", {
	enumerable: true,
	get: function() {
		return JSON_RPC_METHOD_NOT_FOUND;
	}
});
Object.defineProperty(exports, "JSON_RPC_PARSE_ERROR", {
	enumerable: true,
	get: function() {
		return JSON_RPC_PARSE_ERROR;
	}
});
Object.defineProperty(exports, "JSON_RPC_SESSION_NOT_FOUND", {
	enumerable: true,
	get: function() {
		return JSON_RPC_SESSION_NOT_FOUND;
	}
});
Object.defineProperty(exports, "RPC_TIMEOUT_MS", {
	enumerable: true,
	get: function() {
		return RPC_TIMEOUT_MS;
	}
});
Object.defineProperty(exports, "SESSION_LIFECYCLE_RPC_TIMEOUT_MS", {
	enumerable: true,
	get: function() {
		return SESSION_LIFECYCLE_RPC_TIMEOUT_MS;
	}
});
Object.defineProperty(exports, "controlSocketPath", {
	enumerable: true,
	get: function() {
		return controlSocketPath;
	}
});
Object.defineProperty(exports, "dataSocketPath", {
	enumerable: true,
	get: function() {
		return dataSocketPath;
	}
});
Object.defineProperty(exports, "ensureSidecarRuntimeDir", {
	enumerable: true,
	get: function() {
		return ensureSidecarRuntimeDir;
	}
});
Object.defineProperty(exports, "init_protocol", {
	enumerable: true,
	get: function() {
		return init_protocol;
	}
});
Object.defineProperty(exports, "isNamedPipe", {
	enumerable: true,
	get: function() {
		return isNamedPipe;
	}
});
Object.defineProperty(exports, "pidFilePath", {
	enumerable: true,
	get: function() {
		return pidFilePath;
	}
});
Object.defineProperty(exports, "unlinkFileSafe", {
	enumerable: true,
	get: function() {
		return unlinkFileSafe;
	}
});
Object.defineProperty(exports, "workbuddyConfigDir", {
	enumerable: true,
	get: function() {
		return workbuddyConfigDir;
	}
});
