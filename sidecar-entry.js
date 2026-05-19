const require_chunk = require("./chunk.js");
const require_protocol = require("./protocol.js");
let fs = require("fs");
fs = require_chunk.__toESM(fs);
let crypto = require("crypto");
let net = require("net");
net = require_chunk.__toESM(net);
let node_child_process = require("node:child_process");
//#region src/main/sidecar/ring-buffer.ts
require_protocol.init_protocol();
var RingBuffer = class {
	buf;
	head = 0;
	filled = 0;
	total = 0;
	constructor(cap = require_protocol.DEFAULT_RING_BUFFER_BYTES) {
		this.cap = cap;
		this.buf = Buffer.alloc(cap);
	}
	write(data) {
		const len = data.length;
		if (len === 0) return;
		if (len >= this.cap) {
			Buffer.from(data.buffer, data.byteOffset + len - this.cap, this.cap).copy(this.buf, 0);
			this.head = 0;
			this.filled = this.cap;
			this.total += len;
			return;
		}
		const spaceAtEnd = this.cap - this.head;
		if (len <= spaceAtEnd) Buffer.from(data).copy(this.buf, this.head);
		else {
			Buffer.from(data).copy(this.buf, this.head, 0, spaceAtEnd);
			Buffer.from(data).copy(this.buf, 0, spaceAtEnd);
		}
		this.head = (this.head + len) % this.cap;
		this.filled = Math.min(this.filled + len, this.cap);
		this.total += len;
	}
	snapshot() {
		if (this.filled === 0) return Buffer.alloc(0);
		if (this.filled < this.cap) return Buffer.from(this.buf.subarray(0, this.filled));
		const out = Buffer.alloc(this.cap);
		this.buf.copy(out, 0, this.head, this.cap);
		this.buf.copy(out, this.cap - this.head, 0, this.head);
		return out;
	}
	get totalWritten() {
		return this.total;
	}
	get currentFill() {
		return this.filled;
	}
	get capacity() {
		return this.cap;
	}
};
//#endregion
//#region src/main/sidecar/session-process.ts
/**
* SessionProcess — platform-abstracted child process used by SidecarServer
* to host a CLI (agent-cli `--serve`) instance per session.
*
* Two backends:
*   • PTY (macOS, Linux): @lydell/node-pty. Unchanged historical path.
*   • ChildProcess (Windows): node:child_process.spawn. Added for Issue #37718
*     to sidestep repeated edge cases in node-pty's Windows native layer
*     (winpty-conout ENOENT, missing ConPTY on Server 2016, named-pipe
*     lock contention, winpty output truncation). WorkBuddy Desktop does
*     not rely on TTY semantics — the CLI's ACP HTTP server on 127.0.0.1
*     is the real transport. PTY was only providing a log/TUI stream that
*     no consumer uses today (TerminalView is defined but never mounted).
*
* The two backends expose the same six-method surface the server depends
* on, so SidecarServer does not need platform branches.
*/
/**
* Build a session process for the current platform.
* Windows → ChildProcess; other → PTY.
*/
function createSessionProcess(opts) {
	if (process.platform === "win32") return createChildProcessSession(opts);
	return createPtySession(opts);
}
/**
* Spawn using `child_process.spawn` with piped stdio.
*
* Rationale: Windows node-pty repeatedly surfaces native-layer failures
* (`winpty-conout` ENOENT, ConPTY unavailable on older builds, named-pipe
* lock contention after restart, winpty output truncation). WorkBuddy
* Desktop talks to the CLI over ACP/HTTP, so PTY semantics are not
* required for task execution. Trading PTY for a plain pipe on Windows
* eliminates an entire class of boot-time failures.
*
* Kept out of scope: ANSI color passthrough and live `resize` support.
* Neither has a consumer in the desktop app today (TerminalView is not
* mounted anywhere).
*
* Exported for direct use in tests on non-Windows platforms.
*/
function createChildProcessSession(opts) {
	const child = (0, node_child_process.spawn)(opts.command, opts.args, {
		cwd: opts.cwd,
		env: opts.env,
		windowsHide: true,
		stdio: [
			"pipe",
			"pipe",
			"pipe"
		]
	});
	const dataListeners = /* @__PURE__ */ new Set();
	const exitListeners = /* @__PURE__ */ new Set();
	let exited = false;
	const emitData = (chunk) => {
		if (exited) return;
		for (const cb of dataListeners) try {
			cb(chunk);
		} catch {}
	};
	child.stdout.on("data", (chunk) => emitData(chunk));
	child.stderr.on("data", (chunk) => emitData(chunk));
	child.stdin.on("error", (_err) => {});
	child.on("exit", (code, signal) => {
		if (exited) return;
		exited = true;
		const evt = {
			exitCode: code ?? 0,
			signal: signal ? signalNameToNumber(signal) : void 0
		};
		for (const cb of exitListeners) try {
			cb(evt);
		} catch {}
		dataListeners.clear();
		exitListeners.clear();
	});
	child.on("error", (_err) => {
		if (exited) return;
		exited = true;
		const evt = {
			exitCode: 1,
			signal: void 0
		};
		for (const cb of exitListeners) try {
			cb(evt);
		} catch {}
		dataListeners.clear();
		exitListeners.clear();
	});
	return {
		get pid() {
			return child.pid ?? 0;
		},
		onData(cb) {
			dataListeners.add(cb);
			return { dispose() {
				dataListeners.delete(cb);
			} };
		},
		onExit(cb) {
			exitListeners.add(cb);
			return { dispose() {
				exitListeners.delete(cb);
			} };
		},
		write(data) {
			if (!child.stdin.writable) return;
			try {
				child.stdin.write(data);
			} catch {}
		},
		resize(_cols, _rows) {},
		kill(_signal) {
			try {
				child.kill();
			} catch {}
		}
	};
}
/**
* Legacy PTY-backed session. Kept unchanged behaviorally from the pre-
* refactor `pty.spawn` path in server.ts.
*
* We dynamically require `@lydell/node-pty` so that a Windows bundle
* which never calls this function never tries to load its native binding
* — Issue #37718 observed winpty binaries crashing even when only
* imported.
*/
function createPtySession(opts) {
	const proc = require("@lydell/node-pty").spawn(opts.command, opts.args, {
		name: "xterm-256color",
		cols: opts.cols,
		rows: opts.rows,
		cwd: opts.cwd,
		env: opts.env,
		encoding: null
	});
	return {
		get pid() {
			return proc.pid;
		},
		onData(cb) {
			const disposable = proc.onData((rawData) => {
				cb(typeof rawData === "string" ? Buffer.from(rawData, "binary") : Buffer.from(rawData));
			});
			return { dispose: () => disposable.dispose() };
		},
		onExit(cb) {
			const disposable = proc.onExit(({ exitCode, signal }) => {
				cb({
					exitCode,
					signal
				});
			});
			return { dispose: () => disposable.dispose() };
		},
		write(data) {
			if (typeof data === "string") proc.write(data);
			else proc.write(data.toString("binary"));
		},
		resize(cols, rows) {
			proc.resize(cols, rows);
		},
		kill(signal) {
			proc.kill(signal);
		}
	};
}
/**
* Map a POSIX signal name to its canonical number. Used only to match
* node-pty's onExit payload shape. Unknown names return undefined.
*/
function signalNameToNumber(name) {
	switch (name) {
		case "SIGTERM": return 15;
		case "SIGKILL": return 9;
		case "SIGINT": return 2;
		case "SIGHUP": return 1;
		default: return;
	}
}
//#endregion
//#region src/main/sidecar/server.ts
require_protocol.init_protocol();
function log(...args) {
	console.error("[Sidecar]", ...args);
}
function logError(...args) {
	console.error("[Sidecar]", ...args);
}
/**
* System-level variables that the CLI process needs to run correctly.
* We intentionally do NOT include CODEBUDDY_* or other app-config variables —
* those must come exclusively from params.env (built by buildCliEnv in the
* main process), so that a user's shell environment cannot interfere with
* WorkBuddy's internal CLI configuration.
*/
var SYSTEM_ENV_ALLOWLIST = new Set([
	"PATH",
	"HOME",
	"USER",
	"LOGNAME",
	"SHELL",
	"TMPDIR",
	"TEMP",
	"TMP",
	"XDG_RUNTIME_DIR",
	"XDG_DATA_HOME",
	"XDG_CONFIG_HOME",
	"XDG_CACHE_HOME",
	"LANG",
	"LC_ALL",
	"LC_CTYPE",
	"TERM",
	"COLORTERM",
	"DISPLAY",
	"WAYLAND_DISPLAY",
	"DBUS_SESSION_BUS_ADDRESS",
	"NSS_STRICT_NOFORK",
	"GOOGLE_APPLICATION_CREDENTIALS",
	"APPDATA",
	"LOCALAPPDATA",
	"USERPROFILE",
	"HOMEDRIVE",
	"HOMEPATH",
	"COMSPEC",
	"SYSTEMROOT",
	"SYSTEMDRIVE",
	"WINDIR"
]);
/**
* Build the environment for a CLI (cbc) PTY child process.
*
* Starts from a minimal set of system variables picked from the sidecar's
* process.env (so the CLI can find binaries, temp dirs, etc.), then overlays
* params.env which is the authoritative whitelist constructed by buildCliEnv()
* in the Electron main process. This prevents any CODEBUDDY_* variables set
* in the user's shell from leaking into the CLI and overriding WorkBuddy's
* own configuration.
*/
function buildCliProcessEnv(cliEnv) {
	const systemEnv = {};
	for (const key of SYSTEM_ENV_ALLOWLIST) {
		const val = process.env[key];
		if (val !== void 0) systemEnv[key] = val;
	}
	return {
		...systemEnv,
		...cliEnv
	};
}
var SidecarServer = class {
	sessions = /* @__PURE__ */ new Map();
	controlClients = /* @__PURE__ */ new Set();
	controlServer = null;
	idleTimer = null;
	startTime = Date.now();
	shuttingDown = false;
	constructor(token) {
		this.token = token;
	}
	async start() {
		const sockPath = require_protocol.controlSocketPath();
		require_protocol.ensureSidecarRuntimeDir();
		if (!require_protocol.isNamedPipe()) await this.cleanupStaleSocket(sockPath);
		await new Promise((resolve, reject) => {
			this.controlServer = net.createServer((client) => this.handleControlClient(client));
			this.controlServer.on("error", reject);
			this.controlServer.listen(sockPath, () => {
				log(`Control socket listening on ${sockPath}`);
				resolve();
			});
		});
		this.writePidFile();
		this.resetIdleTimer();
		log(`Sidecar v1 started (pid=${process.pid})`);
	}
	/** Graceful shutdown: kill all sessions, close sockets, remove PID file. */
	async shutdown() {
		if (this.shuttingDown) return;
		this.shuttingDown = true;
		log("Shutting down...");
		if (this.idleTimer) {
			clearTimeout(this.idleTimer);
			this.idleTimer = null;
		}
		const kills = Array.from(this.sessions.keys()).map((id) => this.killSession(id).catch((err) => logError(`Error killing session ${id}:`, err)));
		await Promise.all(kills);
		for (const client of this.controlClients) client.destroy();
		this.controlClients.clear();
		if (this.controlServer) {
			await new Promise((resolve) => {
				this.controlServer.close(() => resolve());
			});
			this.controlServer = null;
		}
		if (!require_protocol.isNamedPipe()) this.unlinkSafe(require_protocol.controlSocketPath());
		this.unlinkSafe(require_protocol.pidFilePath());
		log("Shutdown complete.");
	}
	handleControlClient(client) {
		this.controlClients.add(client);
		this.resetIdleTimer();
		let buffer = "";
		client.on("data", (chunk) => {
			buffer += chunk.toString("utf-8");
			let newlineIdx;
			while ((newlineIdx = buffer.indexOf("\n")) !== -1) {
				const line = buffer.slice(0, newlineIdx).trim();
				buffer = buffer.slice(newlineIdx + 1);
				if (line.length > 0) this.handleControlMessage(client, line);
			}
		});
		client.on("close", () => {
			this.controlClients.delete(client);
			this.resetIdleTimer();
		});
		client.on("error", (err) => {
			logError("Control client error:", err.message);
			this.controlClients.delete(client);
			client.destroy();
			this.resetIdleTimer();
		});
	}
	handleControlMessage(client, raw) {
		let req;
		try {
			req = JSON.parse(raw);
		} catch {
			this.sendResponse(client, {
				jsonrpc: "2.0",
				id: null,
				error: {
					code: require_protocol.JSON_RPC_PARSE_ERROR,
					message: "Parse error"
				}
			});
			return;
		}
		this.resetIdleTimer();
		this.dispatch(client, req);
	}
	async dispatch(client, req) {
		const respond = (result, error) => {
			const resp = {
				jsonrpc: "2.0",
				id: req.id
			};
			if (error) resp.error = error;
			else resp.result = result ?? null;
			this.sendResponse(client, resp);
		};
		try {
			switch (req.method) {
				case "session.create": {
					const params = req.params;
					if (!params || !params.command || !params.cwd || params.port == null) {
						respond(void 0, {
							code: require_protocol.JSON_RPC_INVALID_PARAMS,
							message: "Missing required params: command, cwd, port"
						});
						return;
					}
					respond(await this.createSession(params));
					break;
				}
				case "session.reconnect": {
					const p = req.params;
					if (!p?.sessionId) {
						respond(void 0, {
							code: require_protocol.JSON_RPC_INVALID_PARAMS,
							message: "Missing sessionId"
						});
						return;
					}
					respond(this.reconnectSession(p.sessionId, p.cols, p.rows));
					break;
				}
				case "session.resize": {
					const p = req.params;
					if (!p?.sessionId || p.cols == null || p.rows == null) {
						respond(void 0, {
							code: require_protocol.JSON_RPC_INVALID_PARAMS,
							message: "Missing sessionId, cols, rows"
						});
						return;
					}
					this.resizeSession(p.sessionId, p.cols, p.rows);
					respond({ ok: true });
					break;
				}
				case "session.kill": {
					const p = req.params;
					if (!p?.sessionId) {
						respond(void 0, {
							code: require_protocol.JSON_RPC_INVALID_PARAMS,
							message: "Missing sessionId"
						});
						return;
					}
					await this.killSession(p.sessionId);
					respond({ ok: true });
					break;
				}
				case "session.list":
					respond(this.listSessions());
					break;
				case "session.capture": {
					const p = req.params;
					if (!p?.sessionId) {
						respond(void 0, {
							code: require_protocol.JSON_RPC_INVALID_PARAMS,
							message: "Missing sessionId"
						});
						return;
					}
					respond({ text: this.captureSession(p.sessionId, p.lines) });
					break;
				}
				case "sidecar.ping":
					respond(this.ping());
					break;
				case "sidecar.shutdown":
					respond({ ok: true });
					setImmediate(() => this.shutdown().then(() => process.exit(0)));
					break;
				default: respond(void 0, {
					code: require_protocol.JSON_RPC_METHOD_NOT_FOUND,
					message: `Unknown method: ${req.method}`
				});
			}
		} catch (err) {
			const message = err instanceof Error ? err.message : String(err);
			logError(`Error handling ${req.method}:`, message);
			respond(void 0, {
				code: require_protocol.JSON_RPC_INTERNAL_ERROR,
				message
			});
		}
	}
	async createSession(params) {
		const sessionId = params.sessionId || (0, crypto.randomUUID)();
		const cols = params.cols ?? 80;
		const rows = params.rows ?? 24;
		log(`Creating session ${sessionId} — ${params.command} (port=${params.port})`);
		log(`Session ${sessionId} spawn details: command=${params.command}, args=${JSON.stringify(params.args ?? [])}, cwd=${params.cwd}, envKeys=${Object.keys(params.env).join(",")}`);
		const probePath = (p) => {
			try {
				const st = fs.statSync(p);
				return st.isDirectory() ? "dir" : st.isFile() ? "file" : "other";
			} catch (e) {
				return `missing(${e?.code ?? "UNKNOWN"})`;
			}
		};
		const scriptPath = (params.args ?? [])[0];
		log(`Session ${sessionId} pre-spawn probe: command=${probePath(params.command)}, cwd=${probePath(params.cwd)}, script=${scriptPath ? probePath(scriptPath) : "none"}`);
		try {
			if (!fs.statSync(params.cwd).isDirectory()) throw new Error(`Working directory "${params.cwd}" exists but is not a directory. Cannot start CLI session.`);
		} catch (cwdErr) {
			const e = cwdErr;
			const code = e?.code ?? "UNKNOWN";
			logError(`Session ${sessionId} pre-spawn cwd check failed: ${e?.message ?? String(cwdErr)}`);
			throw new Error(`Working directory not available: "${params.cwd}" (${code}). Please verify the default workspace path in Settings exists and is accessible.`);
		}
		const ptyArgs = [
			...params.args ?? [],
			"--port",
			String(params.port)
		];
		let ptyProcess;
		try {
			ptyProcess = createSessionProcess({
				command: params.command,
				args: ptyArgs,
				cwd: params.cwd,
				env: buildCliProcessEnv(params.env),
				cols,
				rows
			});
		} catch (spawnErr) {
			const original = spawnErr instanceof Error ? spawnErr.message : String(spawnErr);
			const errCode = spawnErr?.code ?? "";
			const isCwdError = errCode === "ENOENT" || errCode === "ENOTDIR" || /\berror code:\s*267\b/i.test(original) || /ERROR_DIRECTORY/i.test(original);
			let err;
			if (isCwdError) {
				const cwdState = (() => {
					try {
						return fs.statSync(params.cwd).isDirectory() ? "dir" : "not-a-directory";
					} catch (e) {
						return `missing(${e?.code ?? "UNKNOWN"})`;
					}
				})();
				err = /* @__PURE__ */ new Error(`Session spawn failed: working directory "${params.cwd}" is not usable (${cwdState}). Original error: ${original}. Please verify the default workspace path in Settings exists and is accessible.`);
			} else {
				const platformHint = process.platform === "win32" ? " Possible cause: CLI binary missing, blocked by antivirus, or permission denied." : " Possible cause: missing or incompatible native PTY binary.";
				err = /* @__PURE__ */ new Error(`Session spawn failed: ${original}.${platformHint}`);
			}
			logError(`Session ${sessionId} spawn failed (cwdError=${isCwdError}, code=${errCode || "n/a"}): ${original}`);
			throw err;
		}
		const ringBuffer = new RingBuffer();
		const acpEndpoint = `http://127.0.0.1:${params.port}/api/v1/acp`;
		const session = {
			id: sessionId,
			ptyProcess,
			ringBuffer,
			dataSocketServer: null,
			dataClient: null,
			reconnectQueue: null,
			acpEndpoint,
			port: params.port,
			cols,
			rows,
			terminating: false
		};
		const ptyStartTime = Date.now();
		let earlyOutputLogged = false;
		ptyProcess.onData((data) => {
			ringBuffer.write(data);
			if (!earlyOutputLogged && Date.now() - ptyStartTime < 15e3) {
				const text = data.toString("utf-8").trim();
				if (text) {
					const snippet = text.length > 500 ? text.slice(0, 500) + "...[truncated]" : text;
					log(`Session ${sessionId} early output (${Date.now() - ptyStartTime}ms after spawn): ${snippet}`);
				}
			}
			if (!earlyOutputLogged && Date.now() - ptyStartTime >= 15e3) earlyOutputLogged = true;
			if (session.reconnectQueue) session.reconnectQueue.push(Buffer.from(data));
			else if (session.dataClient) try {
				session.dataClient.write(data);
			} catch {}
		});
		ptyProcess.onExit(({ exitCode, signal }) => {
			const lastOutput = ringBuffer.snapshot().toString("utf-8").trim();
			const tail = lastOutput.length > 2e3 ? lastOutput.slice(-2e3) : lastOutput;
			log(`Session ${sessionId} exited (code=${exitCode}, signal=${signal}, pid=${ptyProcess.pid}, uptime=${Date.now() - ptyStartTime}ms, lastOutput=${tail ? tail.length + " chars" : "empty"})`);
			if (tail) log(`Session ${sessionId} last output:\n${tail}`);
			if (!tail && Date.now() - ptyStartTime < 5e3 && params.command === process.execPath) {
				log(`Session ${sessionId} post-exit probe: cwdExists=${fs.existsSync(params.cwd)}, commandExists=${fs.existsSync(params.command)}, scriptExists=${scriptPath ? fs.existsSync(scriptPath) : true}`);
				const { execFile } = require("child_process");
				const diagArgs = [
					...params.args ?? [],
					"--port",
					String(params.port)
				];
				execFile(params.command, diagArgs, {
					cwd: params.cwd,
					env: {
						...buildCliProcessEnv(params.env),
						ELECTRON_ENABLE_LOGGING: "1"
					},
					timeout: 5e3
				}, (err, stdout, stderr) => {
					log(`Session ${sessionId} diag-exec: code=${err?.code ?? 0}, stdout=${stdout?.trim()?.slice(0, 500) || "empty"}, stderr=${stderr?.trim()?.slice(0, 1e3) || "empty"}`);
				});
			}
			this.broadcastNotification("session.exited", {
				sessionId,
				exitCode,
				signal
			});
			this.cleanupSession(sessionId);
		});
		const dataPath = require_protocol.dataSocketPath(sessionId);
		if (!require_protocol.isNamedPipe()) this.unlinkSafe(dataPath);
		const dataServer = net.createServer((client) => {
			this.handleDataClient(session, client);
		});
		await new Promise((resolve, reject) => {
			dataServer.on("error", reject);
			dataServer.listen(dataPath, () => {
				log(`Data socket for ${sessionId} at ${dataPath}`);
				resolve();
			});
		});
		session.dataSocketServer = dataServer;
		this.sessions.set(sessionId, session);
		log(`Session ${sessionId} created (pid=${ptyProcess.pid}, port=${params.port}, acpEndpoint=${acpEndpoint})`);
		return {
			sessionId,
			socketPath: dataPath,
			acpEndpoint,
			pid: ptyProcess.pid
		};
	}
	handleDataClient(session, client) {
		if (session.dataClient) {
			log(`Session ${session.id}: replacing data client`);
			try {
				session.dataClient.destroy();
			} catch {}
		}
		session.dataClient = client;
		const snapshot = session.ringBuffer.snapshot();
		if (snapshot.length > 0) client.write(snapshot);
		if (session.reconnectQueue) {
			for (const chunk of session.reconnectQueue) client.write(chunk);
			session.reconnectQueue = null;
		}
		client.on("data", (chunk) => {
			if (!session.terminating) try {
				session.ptyProcess.write(chunk);
			} catch (err) {
				logError(`Session ${session.id}: error writing to PTY:`, err);
			}
		});
		client.on("close", () => {
			if (session.dataClient === client) session.dataClient = null;
		});
		client.on("error", (err) => {
			logError(`Session ${session.id}: data client error:`, err.message);
			if (session.dataClient === client) session.dataClient = null;
			client.destroy();
		});
	}
	reconnectSession(sessionId, cols, rows) {
		const session = this.getSession(sessionId);
		log(`Reconnecting session ${sessionId}`);
		session.reconnectQueue = [];
		if (cols != null && rows != null) {
			session.ptyProcess.resize(cols, rows);
			session.cols = cols;
			session.rows = rows;
		}
		return {
			sessionId,
			socketPath: require_protocol.dataSocketPath(sessionId),
			acpEndpoint: session.acpEndpoint
		};
	}
	resizeSession(sessionId, cols, rows) {
		const session = this.getSession(sessionId);
		session.ptyProcess.resize(cols, rows);
		session.cols = cols;
		session.rows = rows;
	}
	async killSession(sessionId) {
		const session = this.sessions.get(sessionId);
		if (!session) return;
		if (session.terminating) return;
		session.terminating = true;
		const ptyPid = session.ptyProcess.pid;
		log(`Killing session ${sessionId} (pid=${ptyPid})`);
		try {
			session.ptyProcess.kill("SIGTERM");
		} catch {}
		if (!await new Promise((resolve) => {
			const timeout = setTimeout(() => {
				resolve(false);
			}, 2e3);
			const onExit = session.ptyProcess.onExit(() => {
				clearTimeout(timeout);
				onExit.dispose();
				resolve(true);
			});
		})) {
			log(`Session ${sessionId}: graceful kill timed out, force-killing (pid=${ptyPid})`);
			try {
				process.kill(ptyPid);
			} catch {}
		}
		this.cleanupSession(sessionId);
	}
	listSessions() {
		const result = [];
		for (const session of this.sessions.values()) result.push({
			sessionId: session.id,
			socketPath: require_protocol.dataSocketPath(session.id),
			acpEndpoint: session.acpEndpoint,
			pid: session.ptyProcess.pid,
			cols: session.cols,
			rows: session.rows
		});
		return result;
	}
	captureSession(sessionId, lines) {
		const text = this.getSession(sessionId).ringBuffer.snapshot().toString("utf-8");
		if (lines == null || lines <= 0) return text;
		return text.split("\n").slice(-lines).join("\n");
	}
	ping() {
		return {
			pid: process.pid,
			uptime: Date.now() - this.startTime,
			version: 1,
			token: this.token
		};
	}
	cleanupSession(sessionId) {
		const session = this.sessions.get(sessionId);
		if (!session) return;
		this.sessions.delete(sessionId);
		if (session.dataClient) {
			try {
				session.dataClient.destroy();
			} catch {}
			session.dataClient = null;
		}
		try {
			session.dataSocketServer.close();
		} catch {}
		if (!require_protocol.isNamedPipe()) this.unlinkSafe(require_protocol.dataSocketPath(sessionId));
		log(`Session ${sessionId} cleaned up (${this.sessions.size} remaining)`);
		this.resetIdleTimer();
	}
	getSession(sessionId) {
		const session = this.sessions.get(sessionId);
		if (!session) {
			const err = /* @__PURE__ */ new Error(`Session not found: ${sessionId}`);
			err.code = require_protocol.JSON_RPC_SESSION_NOT_FOUND;
			throw err;
		}
		return session;
	}
	resetIdleTimer() {
		if (this.idleTimer) {
			clearTimeout(this.idleTimer);
			this.idleTimer = null;
		}
		if (this.sessions.size === 0 && this.controlClients.size === 0 && !this.shuttingDown) {
			this.idleTimer = setTimeout(() => {
				log("Idle timeout reached — shutting down");
				this.shutdown().then(() => process.exit(0));
			}, require_protocol.IDLE_TIMEOUT_MS);
			this.idleTimer.unref();
		}
	}
	broadcastNotification(method, params) {
		const payload = JSON.stringify({
			jsonrpc: "2.0",
			method,
			params
		}) + "\n";
		for (const client of this.controlClients) try {
			client.write(payload);
		} catch {}
	}
	sendResponse(client, resp) {
		try {
			client.write(JSON.stringify(resp) + "\n");
		} catch (err) {
			logError("Failed to send response:", err);
		}
	}
	unlinkSafe(filepath) {
		try {
			fs.unlinkSync(filepath);
		} catch {}
	}
	async cleanupStaleSocket(sockPath) {
		try {
			fs.unlinkSync(sockPath);
			return;
		} catch (err) {
			if (err?.code === "ENOENT") return;
			if (err?.code !== "EACCES" && err?.code !== "EPERM") return;
		}
		log("Socket file is locked, attempting to shut down previous sidecar...");
		try {
			await this.requestRemoteShutdown(sockPath);
		} catch {
			log("Could not reach previous sidecar for graceful shutdown");
		}
		await this.delay(500);
		try {
			fs.unlinkSync(sockPath);
			log("Stale socket file removed after shutdown");
		} catch (retryErr) {
			logError(`Failed to remove stale socket file (${retryErr?.code}): ${sockPath}. A previous sidecar process may still be running. Try terminating it manually or deleting the file.`);
		}
	}
	requestRemoteShutdown(sockPath) {
		return new Promise((resolve, reject) => {
			const client = net.createConnection(sockPath, () => {
				const req = JSON.stringify({
					jsonrpc: "2.0",
					id: 1,
					method: "sidecar.shutdown"
				}) + "\n";
				client.write(req);
				const timeout = setTimeout(() => {
					client.destroy();
					resolve();
				}, 2e3);
				client.on("data", () => {
					clearTimeout(timeout);
					client.destroy();
					resolve();
				});
			});
			client.on("error", (err) => {
				client.destroy();
				reject(err);
			});
		});
	}
	delay(ms) {
		return new Promise((resolve) => setTimeout(resolve, ms));
	}
	writePidFile() {
		const data = JSON.stringify({
			pid: process.pid,
			token: this.token,
			version: 1
		});
		const filePath = require_protocol.pidFilePath();
		fs.writeFileSync(filePath, data, "utf-8");
		log(`PID file written: ${filePath}`);
	}
};
//#endregion
//#region src/main/sidecar/entry.ts
/**
* Sidecar entry point — electron-vite build input for sidecar-entry.js.
* Uses the desktop-local sidecar server so node-pty bundling stays
* fully owned by WorkBuddy Desktop.
*/
function parseToken() {
	const idx = process.argv.indexOf("--token");
	if (idx === -1 || idx + 1 >= process.argv.length) {
		console.error("[Sidecar] Missing required --token argument");
		process.exit(1);
	}
	return process.argv[idx + 1];
}
async function main() {
	const server = new SidecarServer(parseToken());
	const onSignal = () => {
		server.shutdown().then(() => process.exit(0));
	};
	process.on("SIGTERM", onSignal);
	process.on("SIGINT", onSignal);
	await server.start();
}
main().catch((err) => {
	console.error("[Sidecar] Fatal error:", err);
	process.exit(1);
});
//#endregion
