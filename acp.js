const require_chunk = require("./chunk.js");
//#region ../../node_modules/zod/dist/esm/v4/core/core.js
function $constructor(name, initializer, params) {
	function init(inst, def) {
		var _a;
		Object.defineProperty(inst, "_zod", {
			value: inst._zod ?? {},
			enumerable: false
		});
		(_a = inst._zod).traits ?? (_a.traits = /* @__PURE__ */ new Set());
		inst._zod.traits.add(name);
		initializer(inst, def);
		for (const k in _.prototype) if (!(k in inst)) Object.defineProperty(inst, k, { value: _.prototype[k].bind(inst) });
		inst._zod.constr = _;
		inst._zod.def = def;
	}
	const Parent = params?.Parent ?? Object;
	class Definition extends Parent {}
	Object.defineProperty(Definition, "name", { value: name });
	function _(def) {
		var _a;
		const inst = params?.Parent ? new Definition() : this;
		init(inst, def);
		(_a = inst._zod).deferred ?? (_a.deferred = []);
		for (const fn of inst._zod.deferred) fn();
		return inst;
	}
	Object.defineProperty(_, "init", { value: init });
	Object.defineProperty(_, Symbol.hasInstance, { value: (inst) => {
		if (params?.Parent && inst instanceof params.Parent) return true;
		return inst?._zod?.traits?.has(name);
	} });
	Object.defineProperty(_, "name", { value: name });
	return _;
}
function config(newConfig) {
	if (newConfig) Object.assign(globalConfig, newConfig);
	return globalConfig;
}
var $ZodAsyncError, globalConfig;
var init_core$1 = require_chunk.__esmMin((() => {
	$ZodAsyncError = class extends Error {
		constructor() {
			super(`Encountered Promise during synchronous parse. Use .parseAsync() instead.`);
		}
	};
	globalConfig = {};
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/util.js
function getEnumValues(entries) {
	const numericValues = Object.values(entries).filter((v) => typeof v === "number");
	return Object.entries(entries).filter(([k, _]) => numericValues.indexOf(+k) === -1).map(([_, v]) => v);
}
function joinValues(array, separator = "|") {
	return array.map((val) => stringifyPrimitive(val)).join(separator);
}
function jsonStringifyReplacer(_, value) {
	if (typeof value === "bigint") return value.toString();
	return value;
}
function cached(getter) {
	return { get value() {
		{
			const value = getter();
			Object.defineProperty(this, "value", { value });
			return value;
		}
		throw new Error("cached value already set");
	} };
}
function nullish(input) {
	return input === null || input === void 0;
}
function cleanRegex(source) {
	const start = source.startsWith("^") ? 1 : 0;
	const end = source.endsWith("$") ? source.length - 1 : source.length;
	return source.slice(start, end);
}
function floatSafeRemainder(val, step) {
	const valDecCount = (val.toString().split(".")[1] || "").length;
	const stepDecCount = (step.toString().split(".")[1] || "").length;
	const decCount = valDecCount > stepDecCount ? valDecCount : stepDecCount;
	return Number.parseInt(val.toFixed(decCount).replace(".", "")) % Number.parseInt(step.toFixed(decCount).replace(".", "")) / 10 ** decCount;
}
function defineLazy(object, key, getter) {
	Object.defineProperty(object, key, {
		get() {
			{
				const value = getter();
				object[key] = value;
				return value;
			}
			throw new Error("cached value already set");
		},
		set(v) {
			Object.defineProperty(object, key, { value: v });
		},
		configurable: true
	});
}
function assignProp(target, prop, value) {
	Object.defineProperty(target, prop, {
		value,
		writable: true,
		enumerable: true,
		configurable: true
	});
}
function randomString(length = 10) {
	const chars = "abcdefghijklmnopqrstuvwxyz";
	let str = "";
	for (let i = 0; i < length; i++) str += chars[Math.floor(Math.random() * 26)];
	return str;
}
function esc(str) {
	return JSON.stringify(str);
}
function isObject(data) {
	return typeof data === "object" && data !== null && !Array.isArray(data);
}
function isPlainObject(o) {
	if (isObject(o) === false) return false;
	const ctor = o.constructor;
	if (ctor === void 0) return true;
	const prot = ctor.prototype;
	if (isObject(prot) === false) return false;
	if (Object.prototype.hasOwnProperty.call(prot, "isPrototypeOf") === false) return false;
	return true;
}
function escapeRegex(str) {
	return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
function clone(inst, def, params) {
	const cl = new inst._zod.constr(def ?? inst._zod.def);
	if (!def || params?.parent) cl._zod.parent = inst;
	return cl;
}
function normalizeParams(_params) {
	const params = _params;
	if (!params) return {};
	if (typeof params === "string") return { error: () => params };
	if (params?.message !== void 0) {
		if (params?.error !== void 0) throw new Error("Cannot specify both `message` and `error` params");
		params.error = params.message;
	}
	delete params.message;
	if (typeof params.error === "string") return {
		...params,
		error: () => params.error
	};
	return params;
}
function stringifyPrimitive(value) {
	if (typeof value === "bigint") return value.toString() + "n";
	if (typeof value === "string") return `"${value}"`;
	return `${value}`;
}
function optionalKeys(shape) {
	return Object.keys(shape).filter((k) => {
		return shape[k]._zod.optin === "optional" && shape[k]._zod.optout === "optional";
	});
}
function pick(schema, mask) {
	const newShape = {};
	const currDef = schema._zod.def;
	for (const key in mask) {
		if (!(key in currDef.shape)) throw new Error(`Unrecognized key: "${key}"`);
		if (!mask[key]) continue;
		newShape[key] = currDef.shape[key];
	}
	return clone(schema, {
		...schema._zod.def,
		shape: newShape,
		checks: []
	});
}
function omit(schema, mask) {
	const newShape = { ...schema._zod.def.shape };
	const currDef = schema._zod.def;
	for (const key in mask) {
		if (!(key in currDef.shape)) throw new Error(`Unrecognized key: "${key}"`);
		if (!mask[key]) continue;
		delete newShape[key];
	}
	return clone(schema, {
		...schema._zod.def,
		shape: newShape,
		checks: []
	});
}
function extend(schema, shape) {
	return clone(schema, {
		...schema._zod.def,
		get shape() {
			const _shape = {
				...schema._zod.def.shape,
				...shape
			};
			assignProp(this, "shape", _shape);
			return _shape;
		},
		checks: []
	});
}
function merge(a, b) {
	return clone(a, {
		...a._zod.def,
		get shape() {
			const _shape = {
				...a._zod.def.shape,
				...b._zod.def.shape
			};
			assignProp(this, "shape", _shape);
			return _shape;
		},
		catchall: b._zod.def.catchall,
		checks: []
	});
}
function partial(Class, schema, mask) {
	const oldShape = schema._zod.def.shape;
	const shape = { ...oldShape };
	if (mask) for (const key in mask) {
		if (!(key in oldShape)) throw new Error(`Unrecognized key: "${key}"`);
		if (!mask[key]) continue;
		shape[key] = Class ? new Class({
			type: "optional",
			innerType: oldShape[key]
		}) : oldShape[key];
	}
	else for (const key in oldShape) shape[key] = Class ? new Class({
		type: "optional",
		innerType: oldShape[key]
	}) : oldShape[key];
	return clone(schema, {
		...schema._zod.def,
		shape,
		checks: []
	});
}
function required(Class, schema, mask) {
	const oldShape = schema._zod.def.shape;
	const shape = { ...oldShape };
	if (mask) for (const key in mask) {
		if (!(key in shape)) throw new Error(`Unrecognized key: "${key}"`);
		if (!mask[key]) continue;
		shape[key] = new Class({
			type: "nonoptional",
			innerType: oldShape[key]
		});
	}
	else for (const key in oldShape) shape[key] = new Class({
		type: "nonoptional",
		innerType: oldShape[key]
	});
	return clone(schema, {
		...schema._zod.def,
		shape,
		checks: []
	});
}
function aborted(x, startIndex = 0) {
	for (let i = startIndex; i < x.issues.length; i++) if (x.issues[i].continue !== true) return true;
	return false;
}
function prefixIssues(path, issues) {
	return issues.map((iss) => {
		var _a;
		(_a = iss).path ?? (_a.path = []);
		iss.path.unshift(path);
		return iss;
	});
}
function unwrapMessage(message) {
	return typeof message === "string" ? message : message?.message;
}
function finalizeIssue(iss, ctx, config) {
	const full = {
		...iss,
		path: iss.path ?? []
	};
	if (!iss.message) full.message = unwrapMessage(iss.inst?._zod.def?.error?.(iss)) ?? unwrapMessage(ctx?.error?.(iss)) ?? unwrapMessage(config.customError?.(iss)) ?? unwrapMessage(config.localeError?.(iss)) ?? "Invalid input";
	delete full.inst;
	delete full.continue;
	if (!ctx?.reportInput) delete full.input;
	return full;
}
function getLengthableOrigin(input) {
	if (Array.isArray(input)) return "array";
	if (typeof input === "string") return "string";
	return "unknown";
}
function issue(...args) {
	const [iss, input, inst] = args;
	if (typeof iss === "string") return {
		message: iss,
		code: "custom",
		input,
		inst
	};
	return { ...iss };
}
var captureStackTrace, allowsEval, propertyKeyTypes, NUMBER_FORMAT_RANGES;
var init_util = require_chunk.__esmMin((() => {
	captureStackTrace = Error.captureStackTrace ? Error.captureStackTrace : (..._args) => {};
	allowsEval = cached(() => {
		if (typeof navigator !== "undefined" && navigator?.userAgent?.includes("Cloudflare")) return false;
		try {
			new Function("");
			return true;
		} catch (_) {
			return false;
		}
	});
	propertyKeyTypes = new Set([
		"string",
		"number",
		"symbol"
	]);
	NUMBER_FORMAT_RANGES = {
		safeint: [Number.MIN_SAFE_INTEGER, Number.MAX_SAFE_INTEGER],
		int32: [-2147483648, 2147483647],
		uint32: [0, 4294967295],
		float32: [-34028234663852886e22, 34028234663852886e22],
		float64: [-Number.MAX_VALUE, Number.MAX_VALUE]
	};
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/errors.js
function flattenError(error, mapper = (issue) => issue.message) {
	const fieldErrors = {};
	const formErrors = [];
	for (const sub of error.issues) if (sub.path.length > 0) {
		fieldErrors[sub.path[0]] = fieldErrors[sub.path[0]] || [];
		fieldErrors[sub.path[0]].push(mapper(sub));
	} else formErrors.push(mapper(sub));
	return {
		formErrors,
		fieldErrors
	};
}
function formatError(error, _mapper) {
	const mapper = _mapper || function(issue) {
		return issue.message;
	};
	const fieldErrors = { _errors: [] };
	const processError = (error) => {
		for (const issue of error.issues) if (issue.code === "invalid_union" && issue.errors.length) issue.errors.map((issues) => processError({ issues }));
		else if (issue.code === "invalid_key") processError({ issues: issue.issues });
		else if (issue.code === "invalid_element") processError({ issues: issue.issues });
		else if (issue.path.length === 0) fieldErrors._errors.push(mapper(issue));
		else {
			let curr = fieldErrors;
			let i = 0;
			while (i < issue.path.length) {
				const el = issue.path[i];
				if (!(i === issue.path.length - 1)) curr[el] = curr[el] || { _errors: [] };
				else {
					curr[el] = curr[el] || { _errors: [] };
					curr[el]._errors.push(mapper(issue));
				}
				curr = curr[el];
				i++;
			}
		}
	};
	processError(error);
	return fieldErrors;
}
var initializer$1, $ZodError, $ZodRealError;
var init_errors$1 = require_chunk.__esmMin((() => {
	init_core$1();
	init_util();
	initializer$1 = (inst, def) => {
		inst.name = "$ZodError";
		Object.defineProperty(inst, "_zod", {
			value: inst._zod,
			enumerable: false
		});
		Object.defineProperty(inst, "issues", {
			value: def,
			enumerable: false
		});
		Object.defineProperty(inst, "message", {
			get() {
				return JSON.stringify(def, jsonStringifyReplacer, 2);
			},
			enumerable: true
		});
	};
	$ZodError = $constructor("$ZodError", initializer$1);
	$ZodRealError = $constructor("$ZodError", initializer$1, { Parent: Error });
})), _parse, _parseAsync, _safeParse, safeParse$1, _safeParseAsync, safeParseAsync$1;
var init_parse$1 = require_chunk.__esmMin((() => {
	init_core$1();
	init_errors$1();
	init_util();
	_parse = (_Err) => (schema, value, _ctx, _params) => {
		const ctx = _ctx ? Object.assign(_ctx, { async: false }) : { async: false };
		const result = schema._zod.run({
			value,
			issues: []
		}, ctx);
		if (result instanceof Promise) throw new $ZodAsyncError();
		if (result.issues.length) {
			const e = new (_params?.Err ?? _Err)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())));
			captureStackTrace(e, _params?.callee);
			throw e;
		}
		return result.value;
	};
	_parseAsync = (_Err) => async (schema, value, _ctx, params) => {
		const ctx = _ctx ? Object.assign(_ctx, { async: true }) : { async: true };
		let result = schema._zod.run({
			value,
			issues: []
		}, ctx);
		if (result instanceof Promise) result = await result;
		if (result.issues.length) {
			const e = new (params?.Err ?? _Err)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())));
			captureStackTrace(e, params?.callee);
			throw e;
		}
		return result.value;
	};
	_safeParse = (_Err) => (schema, value, _ctx) => {
		const ctx = _ctx ? {
			..._ctx,
			async: false
		} : { async: false };
		const result = schema._zod.run({
			value,
			issues: []
		}, ctx);
		if (result instanceof Promise) throw new $ZodAsyncError();
		return result.issues.length ? {
			success: false,
			error: new (_Err ?? $ZodError)(result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
		} : {
			success: true,
			data: result.value
		};
	};
	safeParse$1 = /* @__PURE__ */ _safeParse($ZodRealError);
	_safeParseAsync = (_Err) => async (schema, value, _ctx) => {
		const ctx = _ctx ? Object.assign(_ctx, { async: true }) : { async: true };
		let result = schema._zod.run({
			value,
			issues: []
		}, ctx);
		if (result instanceof Promise) result = await result;
		return result.issues.length ? {
			success: false,
			error: new _Err(result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
		} : {
			success: true,
			data: result.value
		};
	};
	safeParseAsync$1 = /* @__PURE__ */ _safeParseAsync($ZodRealError);
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/regexes.js
function emoji() {
	return new RegExp(_emoji$1, "u");
}
function timeSource(args) {
	const hhmm = `(?:[01]\\d|2[0-3]):[0-5]\\d`;
	return typeof args.precision === "number" ? args.precision === -1 ? `${hhmm}` : args.precision === 0 ? `${hhmm}:[0-5]\\d` : `${hhmm}:[0-5]\\d\\.\\d{${args.precision}}` : `${hhmm}(?::[0-5]\\d(?:\\.\\d+)?)?`;
}
function time$1(args) {
	return new RegExp(`^${timeSource(args)}$`);
}
function datetime$1(args) {
	const time = timeSource({ precision: args.precision });
	const opts = ["Z"];
	if (args.local) opts.push("");
	if (args.offset) opts.push(`([+-]\\d{2}:\\d{2})`);
	const timeRegex = `${time}(?:${opts.join("|")})`;
	return new RegExp(`^${dateSource}T(?:${timeRegex})$`);
}
var cuid, cuid2, ulid, xid, ksuid, nanoid, duration$1, guid, uuid, email, _emoji$1, ipv4, ipv6, cidrv4, cidrv6, base64, base64url, hostname, e164, dateSource, date$1, string$1, integer, number$1, boolean$1, lowercase, uppercase;
var init_regexes = require_chunk.__esmMin((() => {
	cuid = /^[cC][^\s-]{8,}$/;
	cuid2 = /^[0-9a-z]+$/;
	ulid = /^[0-9A-HJKMNP-TV-Za-hjkmnp-tv-z]{26}$/;
	xid = /^[0-9a-vA-V]{20}$/;
	ksuid = /^[A-Za-z0-9]{27}$/;
	nanoid = /^[a-zA-Z0-9_-]{21}$/;
	duration$1 = /^P(?:(\d+W)|(?!.*W)(?=\d|T\d)(\d+Y)?(\d+M)?(\d+D)?(T(?=\d)(\d+H)?(\d+M)?(\d+([.,]\d+)?S)?)?)$/;
	guid = /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/;
	uuid = (version) => {
		if (!version) return /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}|00000000-0000-0000-0000-000000000000)$/;
		return new RegExp(`^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-${version}[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})$`);
	};
	email = /^(?!\.)(?!.*\.\.)([A-Za-z0-9_'+\-\.]*)[A-Za-z0-9_+-]@([A-Za-z0-9][A-Za-z0-9\-]*\.)+[A-Za-z]{2,}$/;
	_emoji$1 = `^(\\p{Extended_Pictographic}|\\p{Emoji_Component})+$`;
	ipv4 = /^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$/;
	ipv6 = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|::|([0-9a-fA-F]{1,4})?::([0-9a-fA-F]{1,4}:?){0,6})$/;
	cidrv4 = /^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\/([0-9]|[1-2][0-9]|3[0-2])$/;
	cidrv6 = /^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|::|([0-9a-fA-F]{1,4})?::([0-9a-fA-F]{1,4}:?){0,6})\/(12[0-8]|1[01][0-9]|[1-9]?[0-9])$/;
	base64 = /^$|^(?:[0-9a-zA-Z+/]{4})*(?:(?:[0-9a-zA-Z+/]{2}==)|(?:[0-9a-zA-Z+/]{3}=))?$/;
	base64url = /^[A-Za-z0-9_-]*$/;
	hostname = /^([a-zA-Z0-9-]+\.)*[a-zA-Z0-9-]+$/;
	e164 = /^\+(?:[0-9]){6,14}[0-9]$/;
	dateSource = `(?:(?:\\d\\d[2468][048]|\\d\\d[13579][26]|\\d\\d0[48]|[02468][048]00|[13579][26]00)-02-29|\\d{4}-(?:(?:0[13578]|1[02])-(?:0[1-9]|[12]\\d|3[01])|(?:0[469]|11)-(?:0[1-9]|[12]\\d|30)|(?:02)-(?:0[1-9]|1\\d|2[0-8])))`;
	date$1 = /* @__PURE__ */ new RegExp(`^${dateSource}$`);
	string$1 = (params) => {
		const regex = params ? `[\\s\\S]{${params?.minimum ?? 0},${params?.maximum ?? ""}}` : `[\\s\\S]*`;
		return new RegExp(`^${regex}$`);
	};
	integer = /^\d+$/;
	number$1 = /^-?\d+(?:\.\d+)?/i;
	boolean$1 = /true|false/i;
	lowercase = /^[^A-Z]*$/;
	uppercase = /^[^a-z]*$/;
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/checks.js
var $ZodCheck, numericOriginMap, $ZodCheckLessThan, $ZodCheckGreaterThan, $ZodCheckMultipleOf, $ZodCheckNumberFormat, $ZodCheckMaxLength, $ZodCheckMinLength, $ZodCheckLengthEquals, $ZodCheckStringFormat, $ZodCheckRegex, $ZodCheckLowerCase, $ZodCheckUpperCase, $ZodCheckIncludes, $ZodCheckStartsWith, $ZodCheckEndsWith, $ZodCheckOverwrite;
var init_checks$1 = require_chunk.__esmMin((() => {
	init_core$1();
	init_regexes();
	init_util();
	$ZodCheck = /* @__PURE__ */ $constructor("$ZodCheck", (inst, def) => {
		var _a;
		inst._zod ?? (inst._zod = {});
		inst._zod.def = def;
		(_a = inst._zod).onattach ?? (_a.onattach = []);
	});
	numericOriginMap = {
		number: "number",
		bigint: "bigint",
		object: "date"
	};
	$ZodCheckLessThan = /* @__PURE__ */ $constructor("$ZodCheckLessThan", (inst, def) => {
		$ZodCheck.init(inst, def);
		const origin = numericOriginMap[typeof def.value];
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			const curr = (def.inclusive ? bag.maximum : bag.exclusiveMaximum) ?? Number.POSITIVE_INFINITY;
			if (def.value < curr) if (def.inclusive) bag.maximum = def.value;
			else bag.exclusiveMaximum = def.value;
		});
		inst._zod.check = (payload) => {
			if (def.inclusive ? payload.value <= def.value : payload.value < def.value) return;
			payload.issues.push({
				origin,
				code: "too_big",
				maximum: def.value,
				input: payload.value,
				inclusive: def.inclusive,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckGreaterThan = /* @__PURE__ */ $constructor("$ZodCheckGreaterThan", (inst, def) => {
		$ZodCheck.init(inst, def);
		const origin = numericOriginMap[typeof def.value];
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			const curr = (def.inclusive ? bag.minimum : bag.exclusiveMinimum) ?? Number.NEGATIVE_INFINITY;
			if (def.value > curr) if (def.inclusive) bag.minimum = def.value;
			else bag.exclusiveMinimum = def.value;
		});
		inst._zod.check = (payload) => {
			if (def.inclusive ? payload.value >= def.value : payload.value > def.value) return;
			payload.issues.push({
				origin,
				code: "too_small",
				minimum: def.value,
				input: payload.value,
				inclusive: def.inclusive,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckMultipleOf = /* @__PURE__ */ $constructor("$ZodCheckMultipleOf", (inst, def) => {
		$ZodCheck.init(inst, def);
		inst._zod.onattach.push((inst) => {
			var _a;
			(_a = inst._zod.bag).multipleOf ?? (_a.multipleOf = def.value);
		});
		inst._zod.check = (payload) => {
			if (typeof payload.value !== typeof def.value) throw new Error("Cannot mix number and bigint in multiple_of check.");
			if (typeof payload.value === "bigint" ? payload.value % def.value === BigInt(0) : floatSafeRemainder(payload.value, def.value) === 0) return;
			payload.issues.push({
				origin: typeof payload.value,
				code: "not_multiple_of",
				divisor: def.value,
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckNumberFormat = /* @__PURE__ */ $constructor("$ZodCheckNumberFormat", (inst, def) => {
		$ZodCheck.init(inst, def);
		def.format = def.format || "float64";
		const isInt = def.format?.includes("int");
		const origin = isInt ? "int" : "number";
		const [minimum, maximum] = NUMBER_FORMAT_RANGES[def.format];
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.format = def.format;
			bag.minimum = minimum;
			bag.maximum = maximum;
			if (isInt) bag.pattern = integer;
		});
		inst._zod.check = (payload) => {
			const input = payload.value;
			if (isInt) {
				if (!Number.isInteger(input)) {
					payload.issues.push({
						expected: origin,
						format: def.format,
						code: "invalid_type",
						input,
						inst
					});
					return;
				}
				if (!Number.isSafeInteger(input)) {
					if (input > 0) payload.issues.push({
						input,
						code: "too_big",
						maximum: Number.MAX_SAFE_INTEGER,
						note: "Integers must be within the safe integer range.",
						inst,
						origin,
						continue: !def.abort
					});
					else payload.issues.push({
						input,
						code: "too_small",
						minimum: Number.MIN_SAFE_INTEGER,
						note: "Integers must be within the safe integer range.",
						inst,
						origin,
						continue: !def.abort
					});
					return;
				}
			}
			if (input < minimum) payload.issues.push({
				origin: "number",
				input,
				code: "too_small",
				minimum,
				inclusive: true,
				inst,
				continue: !def.abort
			});
			if (input > maximum) payload.issues.push({
				origin: "number",
				input,
				code: "too_big",
				maximum,
				inst
			});
		};
	});
	$ZodCheckMaxLength = /* @__PURE__ */ $constructor("$ZodCheckMaxLength", (inst, def) => {
		$ZodCheck.init(inst, def);
		inst._zod.when = (payload) => {
			const val = payload.value;
			return !nullish(val) && val.length !== void 0;
		};
		inst._zod.onattach.push((inst) => {
			const curr = inst._zod.bag.maximum ?? Number.POSITIVE_INFINITY;
			if (def.maximum < curr) inst._zod.bag.maximum = def.maximum;
		});
		inst._zod.check = (payload) => {
			const input = payload.value;
			if (input.length <= def.maximum) return;
			const origin = getLengthableOrigin(input);
			payload.issues.push({
				origin,
				code: "too_big",
				maximum: def.maximum,
				inclusive: true,
				input,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckMinLength = /* @__PURE__ */ $constructor("$ZodCheckMinLength", (inst, def) => {
		$ZodCheck.init(inst, def);
		inst._zod.when = (payload) => {
			const val = payload.value;
			return !nullish(val) && val.length !== void 0;
		};
		inst._zod.onattach.push((inst) => {
			const curr = inst._zod.bag.minimum ?? Number.NEGATIVE_INFINITY;
			if (def.minimum > curr) inst._zod.bag.minimum = def.minimum;
		});
		inst._zod.check = (payload) => {
			const input = payload.value;
			if (input.length >= def.minimum) return;
			const origin = getLengthableOrigin(input);
			payload.issues.push({
				origin,
				code: "too_small",
				minimum: def.minimum,
				inclusive: true,
				input,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckLengthEquals = /* @__PURE__ */ $constructor("$ZodCheckLengthEquals", (inst, def) => {
		$ZodCheck.init(inst, def);
		inst._zod.when = (payload) => {
			const val = payload.value;
			return !nullish(val) && val.length !== void 0;
		};
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.minimum = def.length;
			bag.maximum = def.length;
			bag.length = def.length;
		});
		inst._zod.check = (payload) => {
			const input = payload.value;
			const length = input.length;
			if (length === def.length) return;
			const origin = getLengthableOrigin(input);
			const tooBig = length > def.length;
			payload.issues.push({
				origin,
				...tooBig ? {
					code: "too_big",
					maximum: def.length
				} : {
					code: "too_small",
					minimum: def.length
				},
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckStringFormat = /* @__PURE__ */ $constructor("$ZodCheckStringFormat", (inst, def) => {
		var _a;
		$ZodCheck.init(inst, def);
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.format = def.format;
			if (def.pattern) {
				bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
				bag.patterns.add(def.pattern);
			}
		});
		(_a = inst._zod).check ?? (_a.check = (payload) => {
			if (!def.pattern) throw new Error("Not implemented.");
			def.pattern.lastIndex = 0;
			if (def.pattern.test(payload.value)) return;
			payload.issues.push({
				origin: "string",
				code: "invalid_format",
				format: def.format,
				input: payload.value,
				...def.pattern ? { pattern: def.pattern.toString() } : {},
				inst,
				continue: !def.abort
			});
		});
	});
	$ZodCheckRegex = /* @__PURE__ */ $constructor("$ZodCheckRegex", (inst, def) => {
		$ZodCheckStringFormat.init(inst, def);
		inst._zod.check = (payload) => {
			def.pattern.lastIndex = 0;
			if (def.pattern.test(payload.value)) return;
			payload.issues.push({
				origin: "string",
				code: "invalid_format",
				format: "regex",
				input: payload.value,
				pattern: def.pattern.toString(),
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckLowerCase = /* @__PURE__ */ $constructor("$ZodCheckLowerCase", (inst, def) => {
		def.pattern ?? (def.pattern = lowercase);
		$ZodCheckStringFormat.init(inst, def);
	});
	$ZodCheckUpperCase = /* @__PURE__ */ $constructor("$ZodCheckUpperCase", (inst, def) => {
		def.pattern ?? (def.pattern = uppercase);
		$ZodCheckStringFormat.init(inst, def);
	});
	$ZodCheckIncludes = /* @__PURE__ */ $constructor("$ZodCheckIncludes", (inst, def) => {
		$ZodCheck.init(inst, def);
		const escapedRegex = escapeRegex(def.includes);
		const pattern = new RegExp(typeof def.position === "number" ? `^.{${def.position}}${escapedRegex}` : escapedRegex);
		def.pattern = pattern;
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
			bag.patterns.add(pattern);
		});
		inst._zod.check = (payload) => {
			if (payload.value.includes(def.includes, def.position)) return;
			payload.issues.push({
				origin: "string",
				code: "invalid_format",
				format: "includes",
				includes: def.includes,
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckStartsWith = /* @__PURE__ */ $constructor("$ZodCheckStartsWith", (inst, def) => {
		$ZodCheck.init(inst, def);
		const pattern = new RegExp(`^${escapeRegex(def.prefix)}.*`);
		def.pattern ?? (def.pattern = pattern);
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
			bag.patterns.add(pattern);
		});
		inst._zod.check = (payload) => {
			if (payload.value.startsWith(def.prefix)) return;
			payload.issues.push({
				origin: "string",
				code: "invalid_format",
				format: "starts_with",
				prefix: def.prefix,
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckEndsWith = /* @__PURE__ */ $constructor("$ZodCheckEndsWith", (inst, def) => {
		$ZodCheck.init(inst, def);
		const pattern = new RegExp(`.*${escapeRegex(def.suffix)}$`);
		def.pattern ?? (def.pattern = pattern);
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.patterns ?? (bag.patterns = /* @__PURE__ */ new Set());
			bag.patterns.add(pattern);
		});
		inst._zod.check = (payload) => {
			if (payload.value.endsWith(def.suffix)) return;
			payload.issues.push({
				origin: "string",
				code: "invalid_format",
				format: "ends_with",
				suffix: def.suffix,
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodCheckOverwrite = /* @__PURE__ */ $constructor("$ZodCheckOverwrite", (inst, def) => {
		$ZodCheck.init(inst, def);
		inst._zod.check = (payload) => {
			payload.value = def.tx(payload.value);
		};
	});
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/doc.js
var Doc;
var init_doc = require_chunk.__esmMin((() => {
	Doc = class {
		constructor(args = []) {
			this.content = [];
			this.indent = 0;
			if (this) this.args = args;
		}
		indented(fn) {
			this.indent += 1;
			fn(this);
			this.indent -= 1;
		}
		write(arg) {
			if (typeof arg === "function") {
				arg(this, { execution: "sync" });
				arg(this, { execution: "async" });
				return;
			}
			const lines = arg.split("\n").filter((x) => x);
			const minIndent = Math.min(...lines.map((x) => x.length - x.trimStart().length));
			const dedented = lines.map((x) => x.slice(minIndent)).map((x) => " ".repeat(this.indent * 2) + x);
			for (const line of dedented) this.content.push(line);
		}
		compile() {
			const F = Function;
			const args = this?.args;
			const lines = [...(this?.content ?? [``]).map((x) => `  ${x}`)];
			return new F(...args, lines.join("\n"));
		}
	};
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/versions.js
var version;
var init_versions = require_chunk.__esmMin((() => {
	version = {
		major: 4,
		minor: 0,
		patch: 0
	};
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/schemas.js
function isValidBase64(data) {
	if (data === "") return true;
	if (data.length % 4 !== 0) return false;
	try {
		atob(data);
		return true;
	} catch {
		return false;
	}
}
function isValidBase64URL(data) {
	if (!base64url.test(data)) return false;
	const base64 = data.replace(/[-_]/g, (c) => c === "-" ? "+" : "/");
	return isValidBase64(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
}
function isValidJWT(token, algorithm = null) {
	try {
		const tokensParts = token.split(".");
		if (tokensParts.length !== 3) return false;
		const [header] = tokensParts;
		const parsedHeader = JSON.parse(atob(header));
		if ("typ" in parsedHeader && parsedHeader?.typ !== "JWT") return false;
		if (!parsedHeader.alg) return false;
		if (algorithm && (!("alg" in parsedHeader) || parsedHeader.alg !== algorithm)) return false;
		return true;
	} catch {
		return false;
	}
}
function handleArrayResult(result, final, index) {
	if (result.issues.length) final.issues.push(...prefixIssues(index, result.issues));
	final.value[index] = result.value;
}
function handleObjectResult(result, final, key) {
	if (result.issues.length) final.issues.push(...prefixIssues(key, result.issues));
	final.value[key] = result.value;
}
function handleOptionalObjectResult(result, final, key, input) {
	if (result.issues.length) if (input[key] === void 0) if (key in input) final.value[key] = void 0;
	else final.value[key] = result.value;
	else final.issues.push(...prefixIssues(key, result.issues));
	else if (result.value === void 0) {
		if (key in input) final.value[key] = void 0;
	} else final.value[key] = result.value;
}
function handleUnionResults(results, final, inst, ctx) {
	for (const result of results) if (result.issues.length === 0) {
		final.value = result.value;
		return final;
	}
	final.issues.push({
		code: "invalid_union",
		input: final.value,
		inst,
		errors: results.map((result) => result.issues.map((iss) => finalizeIssue(iss, ctx, config())))
	});
	return final;
}
function mergeValues(a, b) {
	if (a === b) return {
		valid: true,
		data: a
	};
	if (a instanceof Date && b instanceof Date && +a === +b) return {
		valid: true,
		data: a
	};
	if (isPlainObject(a) && isPlainObject(b)) {
		const bKeys = Object.keys(b);
		const sharedKeys = Object.keys(a).filter((key) => bKeys.indexOf(key) !== -1);
		const newObj = {
			...a,
			...b
		};
		for (const key of sharedKeys) {
			const sharedValue = mergeValues(a[key], b[key]);
			if (!sharedValue.valid) return {
				valid: false,
				mergeErrorPath: [key, ...sharedValue.mergeErrorPath]
			};
			newObj[key] = sharedValue.data;
		}
		return {
			valid: true,
			data: newObj
		};
	}
	if (Array.isArray(a) && Array.isArray(b)) {
		if (a.length !== b.length) return {
			valid: false,
			mergeErrorPath: []
		};
		const newArray = [];
		for (let index = 0; index < a.length; index++) {
			const itemA = a[index];
			const itemB = b[index];
			const sharedValue = mergeValues(itemA, itemB);
			if (!sharedValue.valid) return {
				valid: false,
				mergeErrorPath: [index, ...sharedValue.mergeErrorPath]
			};
			newArray.push(sharedValue.data);
		}
		return {
			valid: true,
			data: newArray
		};
	}
	return {
		valid: false,
		mergeErrorPath: []
	};
}
function handleIntersectionResults(result, left, right) {
	if (left.issues.length) result.issues.push(...left.issues);
	if (right.issues.length) result.issues.push(...right.issues);
	if (aborted(result)) return result;
	const merged = mergeValues(left.value, right.value);
	if (!merged.valid) throw new Error(`Unmergable intersection. Error path: ${JSON.stringify(merged.mergeErrorPath)}`);
	result.value = merged.data;
	return result;
}
function handleDefaultResult(payload, def) {
	if (payload.value === void 0) payload.value = def.defaultValue;
	return payload;
}
function handleNonOptionalResult(payload, inst) {
	if (!payload.issues.length && payload.value === void 0) payload.issues.push({
		code: "invalid_type",
		expected: "nonoptional",
		input: payload.value,
		inst
	});
	return payload;
}
function handlePipeResult(left, def, ctx) {
	if (aborted(left)) return left;
	return def.out._zod.run({
		value: left.value,
		issues: left.issues
	}, ctx);
}
function handleReadonlyResult(payload) {
	payload.value = Object.freeze(payload.value);
	return payload;
}
function handleRefineResult(result, payload, input, inst) {
	if (!result) {
		const _iss = {
			code: "custom",
			input,
			inst,
			path: [...inst._zod.def.path ?? []],
			continue: !inst._zod.def.abort
		};
		if (inst._zod.def.params) _iss.params = inst._zod.def.params;
		payload.issues.push(issue(_iss));
	}
}
var $ZodType, $ZodString, $ZodStringFormat, $ZodGUID, $ZodUUID, $ZodEmail, $ZodURL, $ZodEmoji, $ZodNanoID, $ZodCUID, $ZodCUID2, $ZodULID, $ZodXID, $ZodKSUID, $ZodISODateTime, $ZodISODate, $ZodISOTime, $ZodISODuration, $ZodIPv4, $ZodIPv6, $ZodCIDRv4, $ZodCIDRv6, $ZodBase64, $ZodBase64URL, $ZodE164, $ZodJWT, $ZodNumber, $ZodNumberFormat, $ZodBoolean, $ZodUnknown, $ZodNever, $ZodArray, $ZodObject, $ZodUnion, $ZodIntersection, $ZodRecord, $ZodEnum, $ZodLiteral, $ZodTransform, $ZodOptional, $ZodNullable, $ZodDefault, $ZodPrefault, $ZodNonOptional, $ZodCatch, $ZodPipe, $ZodReadonly, $ZodCustom;
var init_schemas$1 = require_chunk.__esmMin((() => {
	init_checks$1();
	init_core$1();
	init_doc();
	init_parse$1();
	init_regexes();
	init_util();
	init_versions();
	$ZodType = /* @__PURE__ */ $constructor("$ZodType", (inst, def) => {
		var _a;
		inst ?? (inst = {});
		defineLazy(inst._zod, "id", () => def.type + "_" + randomString(10));
		inst._zod.def = def;
		inst._zod.bag = inst._zod.bag || {};
		inst._zod.version = version;
		const checks = [...inst._zod.def.checks ?? []];
		if (inst._zod.traits.has("$ZodCheck")) checks.unshift(inst);
		for (const ch of checks) for (const fn of ch._zod.onattach) fn(inst);
		if (checks.length === 0) {
			(_a = inst._zod).deferred ?? (_a.deferred = []);
			inst._zod.deferred?.push(() => {
				inst._zod.run = inst._zod.parse;
			});
		} else {
			const runChecks = (payload, checks, ctx) => {
				let isAborted = aborted(payload);
				let asyncResult;
				for (const ch of checks) {
					if (ch._zod.when) {
						if (!ch._zod.when(payload)) continue;
					} else if (isAborted) continue;
					const currLen = payload.issues.length;
					const _ = ch._zod.check(payload);
					if (_ instanceof Promise && ctx?.async === false) throw new $ZodAsyncError();
					if (asyncResult || _ instanceof Promise) asyncResult = (asyncResult ?? Promise.resolve()).then(async () => {
						await _;
						if (payload.issues.length === currLen) return;
						if (!isAborted) isAborted = aborted(payload, currLen);
					});
					else {
						if (payload.issues.length === currLen) continue;
						if (!isAborted) isAborted = aborted(payload, currLen);
					}
				}
				if (asyncResult) return asyncResult.then(() => {
					return payload;
				});
				return payload;
			};
			inst._zod.run = (payload, ctx) => {
				const result = inst._zod.parse(payload, ctx);
				if (result instanceof Promise) {
					if (ctx.async === false) throw new $ZodAsyncError();
					return result.then((result) => runChecks(result, checks, ctx));
				}
				return runChecks(result, checks, ctx);
			};
		}
		inst["~standard"] = {
			validate: (value) => {
				try {
					const r = safeParse$1(inst, value);
					return r.success ? { value: r.data } : { issues: r.error?.issues };
				} catch (_) {
					return safeParseAsync$1(inst, value).then((r) => r.success ? { value: r.data } : { issues: r.error?.issues });
				}
			},
			vendor: "zod",
			version: 1
		};
	});
	$ZodString = /* @__PURE__ */ $constructor("$ZodString", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.pattern = [...inst?._zod.bag?.patterns ?? []].pop() ?? string$1(inst._zod.bag);
		inst._zod.parse = (payload, _) => {
			if (def.coerce) try {
				payload.value = String(payload.value);
			} catch (_) {}
			if (typeof payload.value === "string") return payload;
			payload.issues.push({
				expected: "string",
				code: "invalid_type",
				input: payload.value,
				inst
			});
			return payload;
		};
	});
	$ZodStringFormat = /* @__PURE__ */ $constructor("$ZodStringFormat", (inst, def) => {
		$ZodCheckStringFormat.init(inst, def);
		$ZodString.init(inst, def);
	});
	$ZodGUID = /* @__PURE__ */ $constructor("$ZodGUID", (inst, def) => {
		def.pattern ?? (def.pattern = guid);
		$ZodStringFormat.init(inst, def);
	});
	$ZodUUID = /* @__PURE__ */ $constructor("$ZodUUID", (inst, def) => {
		if (def.version) {
			const v = {
				v1: 1,
				v2: 2,
				v3: 3,
				v4: 4,
				v5: 5,
				v6: 6,
				v7: 7,
				v8: 8
			}[def.version];
			if (v === void 0) throw new Error(`Invalid UUID version: "${def.version}"`);
			def.pattern ?? (def.pattern = uuid(v));
		} else def.pattern ?? (def.pattern = uuid());
		$ZodStringFormat.init(inst, def);
	});
	$ZodEmail = /* @__PURE__ */ $constructor("$ZodEmail", (inst, def) => {
		def.pattern ?? (def.pattern = email);
		$ZodStringFormat.init(inst, def);
	});
	$ZodURL = /* @__PURE__ */ $constructor("$ZodURL", (inst, def) => {
		$ZodStringFormat.init(inst, def);
		inst._zod.check = (payload) => {
			try {
				const url = new URL(payload.value);
				if (def.hostname) {
					def.hostname.lastIndex = 0;
					if (!def.hostname.test(url.hostname)) payload.issues.push({
						code: "invalid_format",
						format: "url",
						note: "Invalid hostname",
						pattern: hostname.source,
						input: payload.value,
						inst,
						continue: !def.abort
					});
				}
				if (def.protocol) {
					def.protocol.lastIndex = 0;
					if (!def.protocol.test(url.protocol.endsWith(":") ? url.protocol.slice(0, -1) : url.protocol)) payload.issues.push({
						code: "invalid_format",
						format: "url",
						note: "Invalid protocol",
						pattern: def.protocol.source,
						input: payload.value,
						inst,
						continue: !def.abort
					});
				}
				return;
			} catch (_) {
				payload.issues.push({
					code: "invalid_format",
					format: "url",
					input: payload.value,
					inst,
					continue: !def.abort
				});
			}
		};
	});
	$ZodEmoji = /* @__PURE__ */ $constructor("$ZodEmoji", (inst, def) => {
		def.pattern ?? (def.pattern = emoji());
		$ZodStringFormat.init(inst, def);
	});
	$ZodNanoID = /* @__PURE__ */ $constructor("$ZodNanoID", (inst, def) => {
		def.pattern ?? (def.pattern = nanoid);
		$ZodStringFormat.init(inst, def);
	});
	$ZodCUID = /* @__PURE__ */ $constructor("$ZodCUID", (inst, def) => {
		def.pattern ?? (def.pattern = cuid);
		$ZodStringFormat.init(inst, def);
	});
	$ZodCUID2 = /* @__PURE__ */ $constructor("$ZodCUID2", (inst, def) => {
		def.pattern ?? (def.pattern = cuid2);
		$ZodStringFormat.init(inst, def);
	});
	$ZodULID = /* @__PURE__ */ $constructor("$ZodULID", (inst, def) => {
		def.pattern ?? (def.pattern = ulid);
		$ZodStringFormat.init(inst, def);
	});
	$ZodXID = /* @__PURE__ */ $constructor("$ZodXID", (inst, def) => {
		def.pattern ?? (def.pattern = xid);
		$ZodStringFormat.init(inst, def);
	});
	$ZodKSUID = /* @__PURE__ */ $constructor("$ZodKSUID", (inst, def) => {
		def.pattern ?? (def.pattern = ksuid);
		$ZodStringFormat.init(inst, def);
	});
	$ZodISODateTime = /* @__PURE__ */ $constructor("$ZodISODateTime", (inst, def) => {
		def.pattern ?? (def.pattern = datetime$1(def));
		$ZodStringFormat.init(inst, def);
		inst._zod.check;
	});
	$ZodISODate = /* @__PURE__ */ $constructor("$ZodISODate", (inst, def) => {
		def.pattern ?? (def.pattern = date$1);
		$ZodStringFormat.init(inst, def);
	});
	$ZodISOTime = /* @__PURE__ */ $constructor("$ZodISOTime", (inst, def) => {
		def.pattern ?? (def.pattern = time$1(def));
		$ZodStringFormat.init(inst, def);
		inst._zod.check;
	});
	$ZodISODuration = /* @__PURE__ */ $constructor("$ZodISODuration", (inst, def) => {
		def.pattern ?? (def.pattern = duration$1);
		$ZodStringFormat.init(inst, def);
	});
	$ZodIPv4 = /* @__PURE__ */ $constructor("$ZodIPv4", (inst, def) => {
		def.pattern ?? (def.pattern = ipv4);
		$ZodStringFormat.init(inst, def);
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.format = `ipv4`;
		});
	});
	$ZodIPv6 = /* @__PURE__ */ $constructor("$ZodIPv6", (inst, def) => {
		def.pattern ?? (def.pattern = ipv6);
		$ZodStringFormat.init(inst, def);
		inst._zod.onattach.push((inst) => {
			const bag = inst._zod.bag;
			bag.format = `ipv6`;
		});
		inst._zod.check = (payload) => {
			try {
				new URL(`http://[${payload.value}]`);
			} catch {
				payload.issues.push({
					code: "invalid_format",
					format: "ipv6",
					input: payload.value,
					inst,
					continue: !def.abort
				});
			}
		};
	});
	$ZodCIDRv4 = /* @__PURE__ */ $constructor("$ZodCIDRv4", (inst, def) => {
		def.pattern ?? (def.pattern = cidrv4);
		$ZodStringFormat.init(inst, def);
	});
	$ZodCIDRv6 = /* @__PURE__ */ $constructor("$ZodCIDRv6", (inst, def) => {
		def.pattern ?? (def.pattern = cidrv6);
		$ZodStringFormat.init(inst, def);
		inst._zod.check = (payload) => {
			const [address, prefix] = payload.value.split("/");
			try {
				if (!prefix) throw new Error();
				const prefixNum = Number(prefix);
				if (`${prefixNum}` !== prefix) throw new Error();
				if (prefixNum < 0 || prefixNum > 128) throw new Error();
				new URL(`http://[${address}]`);
			} catch {
				payload.issues.push({
					code: "invalid_format",
					format: "cidrv6",
					input: payload.value,
					inst,
					continue: !def.abort
				});
			}
		};
	});
	$ZodBase64 = /* @__PURE__ */ $constructor("$ZodBase64", (inst, def) => {
		def.pattern ?? (def.pattern = base64);
		$ZodStringFormat.init(inst, def);
		inst._zod.onattach.push((inst) => {
			inst._zod.bag.contentEncoding = "base64";
		});
		inst._zod.check = (payload) => {
			if (isValidBase64(payload.value)) return;
			payload.issues.push({
				code: "invalid_format",
				format: "base64",
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodBase64URL = /* @__PURE__ */ $constructor("$ZodBase64URL", (inst, def) => {
		def.pattern ?? (def.pattern = base64url);
		$ZodStringFormat.init(inst, def);
		inst._zod.onattach.push((inst) => {
			inst._zod.bag.contentEncoding = "base64url";
		});
		inst._zod.check = (payload) => {
			if (isValidBase64URL(payload.value)) return;
			payload.issues.push({
				code: "invalid_format",
				format: "base64url",
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodE164 = /* @__PURE__ */ $constructor("$ZodE164", (inst, def) => {
		def.pattern ?? (def.pattern = e164);
		$ZodStringFormat.init(inst, def);
	});
	$ZodJWT = /* @__PURE__ */ $constructor("$ZodJWT", (inst, def) => {
		$ZodStringFormat.init(inst, def);
		inst._zod.check = (payload) => {
			if (isValidJWT(payload.value, def.alg)) return;
			payload.issues.push({
				code: "invalid_format",
				format: "jwt",
				input: payload.value,
				inst,
				continue: !def.abort
			});
		};
	});
	$ZodNumber = /* @__PURE__ */ $constructor("$ZodNumber", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.pattern = inst._zod.bag.pattern ?? number$1;
		inst._zod.parse = (payload, _ctx) => {
			if (def.coerce) try {
				payload.value = Number(payload.value);
			} catch (_) {}
			const input = payload.value;
			if (typeof input === "number" && !Number.isNaN(input) && Number.isFinite(input)) return payload;
			const received = typeof input === "number" ? Number.isNaN(input) ? "NaN" : !Number.isFinite(input) ? "Infinity" : void 0 : void 0;
			payload.issues.push({
				expected: "number",
				code: "invalid_type",
				input,
				inst,
				...received ? { received } : {}
			});
			return payload;
		};
	});
	$ZodNumberFormat = /* @__PURE__ */ $constructor("$ZodNumber", (inst, def) => {
		$ZodCheckNumberFormat.init(inst, def);
		$ZodNumber.init(inst, def);
	});
	$ZodBoolean = /* @__PURE__ */ $constructor("$ZodBoolean", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.pattern = boolean$1;
		inst._zod.parse = (payload, _ctx) => {
			if (def.coerce) try {
				payload.value = Boolean(payload.value);
			} catch (_) {}
			const input = payload.value;
			if (typeof input === "boolean") return payload;
			payload.issues.push({
				expected: "boolean",
				code: "invalid_type",
				input,
				inst
			});
			return payload;
		};
	});
	$ZodUnknown = /* @__PURE__ */ $constructor("$ZodUnknown", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.parse = (payload) => payload;
	});
	$ZodNever = /* @__PURE__ */ $constructor("$ZodNever", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.parse = (payload, _ctx) => {
			payload.issues.push({
				expected: "never",
				code: "invalid_type",
				input: payload.value,
				inst
			});
			return payload;
		};
	});
	$ZodArray = /* @__PURE__ */ $constructor("$ZodArray", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.parse = (payload, ctx) => {
			const input = payload.value;
			if (!Array.isArray(input)) {
				payload.issues.push({
					expected: "array",
					code: "invalid_type",
					input,
					inst
				});
				return payload;
			}
			payload.value = Array(input.length);
			const proms = [];
			for (let i = 0; i < input.length; i++) {
				const item = input[i];
				const result = def.element._zod.run({
					value: item,
					issues: []
				}, ctx);
				if (result instanceof Promise) proms.push(result.then((result) => handleArrayResult(result, payload, i)));
				else handleArrayResult(result, payload, i);
			}
			if (proms.length) return Promise.all(proms).then(() => payload);
			return payload;
		};
	});
	$ZodObject = /* @__PURE__ */ $constructor("$ZodObject", (inst, def) => {
		$ZodType.init(inst, def);
		const _normalized = cached(() => {
			const keys = Object.keys(def.shape);
			for (const k of keys) if (!(def.shape[k] instanceof $ZodType)) throw new Error(`Invalid element at key "${k}": expected a Zod schema`);
			const okeys = optionalKeys(def.shape);
			return {
				shape: def.shape,
				keys,
				keySet: new Set(keys),
				numKeys: keys.length,
				optionalKeys: new Set(okeys)
			};
		});
		defineLazy(inst._zod, "propValues", () => {
			const shape = def.shape;
			const propValues = {};
			for (const key in shape) {
				const field = shape[key]._zod;
				if (field.values) {
					propValues[key] ?? (propValues[key] = /* @__PURE__ */ new Set());
					for (const v of field.values) propValues[key].add(v);
				}
			}
			return propValues;
		});
		const generateFastpass = (shape) => {
			const doc = new Doc([
				"shape",
				"payload",
				"ctx"
			]);
			const { keys, optionalKeys } = _normalized.value;
			const parseStr = (key) => {
				const k = esc(key);
				return `shape[${k}]._zod.run({ value: input[${k}], issues: [] }, ctx)`;
			};
			doc.write(`const input = payload.value;`);
			const ids = Object.create(null);
			for (const key of keys) ids[key] = randomString(15);
			doc.write(`const newResult = {}`);
			for (const key of keys) if (optionalKeys.has(key)) {
				const id = ids[key];
				doc.write(`const ${id} = ${parseStr(key)};`);
				const k = esc(key);
				doc.write(`
        if (${id}.issues.length) {
          if (input[${k}] === undefined) {
            if (${k} in input) {
              newResult[${k}] = undefined;
            }
          } else {
            payload.issues = payload.issues.concat(
              ${id}.issues.map((iss) => ({
                ...iss,
                path: iss.path ? [${k}, ...iss.path] : [${k}],
              }))
            );
          }
        } else if (${id}.value === undefined) {
          if (${k} in input) newResult[${k}] = undefined;
        } else {
          newResult[${k}] = ${id}.value;
        }
        `);
			} else {
				const id = ids[key];
				doc.write(`const ${id} = ${parseStr(key)};`);
				doc.write(`
          if (${id}.issues.length) payload.issues = payload.issues.concat(${id}.issues.map(iss => ({
            ...iss,
            path: iss.path ? [${esc(key)}, ...iss.path] : [${esc(key)}]
          })));`);
				doc.write(`newResult[${esc(key)}] = ${id}.value`);
			}
			doc.write(`payload.value = newResult;`);
			doc.write(`return payload;`);
			const fn = doc.compile();
			return (payload, ctx) => fn(shape, payload, ctx);
		};
		let fastpass;
		const isObject$1 = isObject;
		const jit = !globalConfig.jitless;
		const fastEnabled = jit && allowsEval.value;
		const { catchall } = def;
		let value;
		inst._zod.parse = (payload, ctx) => {
			value ?? (value = _normalized.value);
			const input = payload.value;
			if (!isObject$1(input)) {
				payload.issues.push({
					expected: "object",
					code: "invalid_type",
					input,
					inst
				});
				return payload;
			}
			const proms = [];
			if (jit && fastEnabled && ctx?.async === false && ctx.jitless !== true) {
				if (!fastpass) fastpass = generateFastpass(def.shape);
				payload = fastpass(payload, ctx);
			} else {
				payload.value = {};
				const shape = value.shape;
				for (const key of value.keys) {
					const el = shape[key];
					const r = el._zod.run({
						value: input[key],
						issues: []
					}, ctx);
					const isOptional = el._zod.optin === "optional" && el._zod.optout === "optional";
					if (r instanceof Promise) proms.push(r.then((r) => isOptional ? handleOptionalObjectResult(r, payload, key, input) : handleObjectResult(r, payload, key)));
					else if (isOptional) handleOptionalObjectResult(r, payload, key, input);
					else handleObjectResult(r, payload, key);
				}
			}
			if (!catchall) return proms.length ? Promise.all(proms).then(() => payload) : payload;
			const unrecognized = [];
			const keySet = value.keySet;
			const _catchall = catchall._zod;
			const t = _catchall.def.type;
			for (const key of Object.keys(input)) {
				if (keySet.has(key)) continue;
				if (t === "never") {
					unrecognized.push(key);
					continue;
				}
				const r = _catchall.run({
					value: input[key],
					issues: []
				}, ctx);
				if (r instanceof Promise) proms.push(r.then((r) => handleObjectResult(r, payload, key)));
				else handleObjectResult(r, payload, key);
			}
			if (unrecognized.length) payload.issues.push({
				code: "unrecognized_keys",
				keys: unrecognized,
				input,
				inst
			});
			if (!proms.length) return payload;
			return Promise.all(proms).then(() => {
				return payload;
			});
		};
	});
	$ZodUnion = /* @__PURE__ */ $constructor("$ZodUnion", (inst, def) => {
		$ZodType.init(inst, def);
		defineLazy(inst._zod, "values", () => {
			if (def.options.every((o) => o._zod.values)) return new Set(def.options.flatMap((option) => Array.from(option._zod.values)));
		});
		defineLazy(inst._zod, "pattern", () => {
			if (def.options.every((o) => o._zod.pattern)) {
				const patterns = def.options.map((o) => o._zod.pattern);
				return new RegExp(`^(${patterns.map((p) => cleanRegex(p.source)).join("|")})$`);
			}
		});
		inst._zod.parse = (payload, ctx) => {
			let async = false;
			const results = [];
			for (const option of def.options) {
				const result = option._zod.run({
					value: payload.value,
					issues: []
				}, ctx);
				if (result instanceof Promise) {
					results.push(result);
					async = true;
				} else {
					if (result.issues.length === 0) return result;
					results.push(result);
				}
			}
			if (!async) return handleUnionResults(results, payload, inst, ctx);
			return Promise.all(results).then((results) => {
				return handleUnionResults(results, payload, inst, ctx);
			});
		};
	});
	$ZodIntersection = /* @__PURE__ */ $constructor("$ZodIntersection", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.parse = (payload, ctx) => {
			const { value: input } = payload;
			const left = def.left._zod.run({
				value: input,
				issues: []
			}, ctx);
			const right = def.right._zod.run({
				value: input,
				issues: []
			}, ctx);
			if (left instanceof Promise || right instanceof Promise) return Promise.all([left, right]).then(([left, right]) => {
				return handleIntersectionResults(payload, left, right);
			});
			return handleIntersectionResults(payload, left, right);
		};
	});
	$ZodRecord = /* @__PURE__ */ $constructor("$ZodRecord", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.parse = (payload, ctx) => {
			const input = payload.value;
			if (!isPlainObject(input)) {
				payload.issues.push({
					expected: "record",
					code: "invalid_type",
					input,
					inst
				});
				return payload;
			}
			const proms = [];
			if (def.keyType._zod.values) {
				const values = def.keyType._zod.values;
				payload.value = {};
				for (const key of values) if (typeof key === "string" || typeof key === "number" || typeof key === "symbol") {
					const result = def.valueType._zod.run({
						value: input[key],
						issues: []
					}, ctx);
					if (result instanceof Promise) proms.push(result.then((result) => {
						if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
						payload.value[key] = result.value;
					}));
					else {
						if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
						payload.value[key] = result.value;
					}
				}
				let unrecognized;
				for (const key in input) if (!values.has(key)) {
					unrecognized = unrecognized ?? [];
					unrecognized.push(key);
				}
				if (unrecognized && unrecognized.length > 0) payload.issues.push({
					code: "unrecognized_keys",
					input,
					inst,
					keys: unrecognized
				});
			} else {
				payload.value = {};
				for (const key of Reflect.ownKeys(input)) {
					if (key === "__proto__") continue;
					const keyResult = def.keyType._zod.run({
						value: key,
						issues: []
					}, ctx);
					if (keyResult instanceof Promise) throw new Error("Async schemas not supported in object keys currently");
					if (keyResult.issues.length) {
						payload.issues.push({
							origin: "record",
							code: "invalid_key",
							issues: keyResult.issues.map((iss) => finalizeIssue(iss, ctx, config())),
							input: key,
							path: [key],
							inst
						});
						payload.value[keyResult.value] = keyResult.value;
						continue;
					}
					const result = def.valueType._zod.run({
						value: input[key],
						issues: []
					}, ctx);
					if (result instanceof Promise) proms.push(result.then((result) => {
						if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
						payload.value[keyResult.value] = result.value;
					}));
					else {
						if (result.issues.length) payload.issues.push(...prefixIssues(key, result.issues));
						payload.value[keyResult.value] = result.value;
					}
				}
			}
			if (proms.length) return Promise.all(proms).then(() => payload);
			return payload;
		};
	});
	$ZodEnum = /* @__PURE__ */ $constructor("$ZodEnum", (inst, def) => {
		$ZodType.init(inst, def);
		const values = getEnumValues(def.entries);
		inst._zod.values = new Set(values);
		inst._zod.pattern = new RegExp(`^(${values.filter((k) => propertyKeyTypes.has(typeof k)).map((o) => typeof o === "string" ? escapeRegex(o) : o.toString()).join("|")})$`);
		inst._zod.parse = (payload, _ctx) => {
			const input = payload.value;
			if (inst._zod.values.has(input)) return payload;
			payload.issues.push({
				code: "invalid_value",
				values,
				input,
				inst
			});
			return payload;
		};
	});
	$ZodLiteral = /* @__PURE__ */ $constructor("$ZodLiteral", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.values = new Set(def.values);
		inst._zod.pattern = new RegExp(`^(${def.values.map((o) => typeof o === "string" ? escapeRegex(o) : o ? o.toString() : String(o)).join("|")})$`);
		inst._zod.parse = (payload, _ctx) => {
			const input = payload.value;
			if (inst._zod.values.has(input)) return payload;
			payload.issues.push({
				code: "invalid_value",
				values: def.values,
				input,
				inst
			});
			return payload;
		};
	});
	$ZodTransform = /* @__PURE__ */ $constructor("$ZodTransform", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.parse = (payload, _ctx) => {
			const _out = def.transform(payload.value, payload);
			if (_ctx.async) return (_out instanceof Promise ? _out : Promise.resolve(_out)).then((output) => {
				payload.value = output;
				return payload;
			});
			if (_out instanceof Promise) throw new $ZodAsyncError();
			payload.value = _out;
			return payload;
		};
	});
	$ZodOptional = /* @__PURE__ */ $constructor("$ZodOptional", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.optin = "optional";
		inst._zod.optout = "optional";
		defineLazy(inst._zod, "values", () => {
			return def.innerType._zod.values ? new Set([...def.innerType._zod.values, void 0]) : void 0;
		});
		defineLazy(inst._zod, "pattern", () => {
			const pattern = def.innerType._zod.pattern;
			return pattern ? new RegExp(`^(${cleanRegex(pattern.source)})?$`) : void 0;
		});
		inst._zod.parse = (payload, ctx) => {
			if (payload.value === void 0) return payload;
			return def.innerType._zod.run(payload, ctx);
		};
	});
	$ZodNullable = /* @__PURE__ */ $constructor("$ZodNullable", (inst, def) => {
		$ZodType.init(inst, def);
		defineLazy(inst._zod, "optin", () => def.innerType._zod.optin);
		defineLazy(inst._zod, "optout", () => def.innerType._zod.optout);
		defineLazy(inst._zod, "pattern", () => {
			const pattern = def.innerType._zod.pattern;
			return pattern ? new RegExp(`^(${cleanRegex(pattern.source)}|null)$`) : void 0;
		});
		defineLazy(inst._zod, "values", () => {
			return def.innerType._zod.values ? new Set([...def.innerType._zod.values, null]) : void 0;
		});
		inst._zod.parse = (payload, ctx) => {
			if (payload.value === null) return payload;
			return def.innerType._zod.run(payload, ctx);
		};
	});
	$ZodDefault = /* @__PURE__ */ $constructor("$ZodDefault", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.optin = "optional";
		defineLazy(inst._zod, "values", () => def.innerType._zod.values);
		inst._zod.parse = (payload, ctx) => {
			if (payload.value === void 0) {
				payload.value = def.defaultValue;
				/**
				* $ZodDefault always returns the default value immediately.
				* It doesn't pass the default value into the validator ("prefault"). There's no reason to pass the default value through validation. The validity of the default is enforced by TypeScript statically. Otherwise, it's the responsibility of the user to ensure the default is valid. In the case of pipes with divergent in/out types, you can specify the default on the `in` schema of your ZodPipe to set a "prefault" for the pipe.   */
				return payload;
			}
			const result = def.innerType._zod.run(payload, ctx);
			if (result instanceof Promise) return result.then((result) => handleDefaultResult(result, def));
			return handleDefaultResult(result, def);
		};
	});
	$ZodPrefault = /* @__PURE__ */ $constructor("$ZodPrefault", (inst, def) => {
		$ZodType.init(inst, def);
		inst._zod.optin = "optional";
		defineLazy(inst._zod, "values", () => def.innerType._zod.values);
		inst._zod.parse = (payload, ctx) => {
			if (payload.value === void 0) payload.value = def.defaultValue;
			return def.innerType._zod.run(payload, ctx);
		};
	});
	$ZodNonOptional = /* @__PURE__ */ $constructor("$ZodNonOptional", (inst, def) => {
		$ZodType.init(inst, def);
		defineLazy(inst._zod, "values", () => {
			const v = def.innerType._zod.values;
			return v ? new Set([...v].filter((x) => x !== void 0)) : void 0;
		});
		inst._zod.parse = (payload, ctx) => {
			const result = def.innerType._zod.run(payload, ctx);
			if (result instanceof Promise) return result.then((result) => handleNonOptionalResult(result, inst));
			return handleNonOptionalResult(result, inst);
		};
	});
	$ZodCatch = /* @__PURE__ */ $constructor("$ZodCatch", (inst, def) => {
		$ZodType.init(inst, def);
		defineLazy(inst._zod, "optin", () => def.innerType._zod.optin);
		defineLazy(inst._zod, "optout", () => def.innerType._zod.optout);
		defineLazy(inst._zod, "values", () => def.innerType._zod.values);
		inst._zod.parse = (payload, ctx) => {
			const result = def.innerType._zod.run(payload, ctx);
			if (result instanceof Promise) return result.then((result) => {
				payload.value = result.value;
				if (result.issues.length) {
					payload.value = def.catchValue({
						...payload,
						error: { issues: result.issues.map((iss) => finalizeIssue(iss, ctx, config())) },
						input: payload.value
					});
					payload.issues = [];
				}
				return payload;
			});
			payload.value = result.value;
			if (result.issues.length) {
				payload.value = def.catchValue({
					...payload,
					error: { issues: result.issues.map((iss) => finalizeIssue(iss, ctx, config())) },
					input: payload.value
				});
				payload.issues = [];
			}
			return payload;
		};
	});
	$ZodPipe = /* @__PURE__ */ $constructor("$ZodPipe", (inst, def) => {
		$ZodType.init(inst, def);
		defineLazy(inst._zod, "values", () => def.in._zod.values);
		defineLazy(inst._zod, "optin", () => def.in._zod.optin);
		defineLazy(inst._zod, "optout", () => def.out._zod.optout);
		inst._zod.parse = (payload, ctx) => {
			const left = def.in._zod.run(payload, ctx);
			if (left instanceof Promise) return left.then((left) => handlePipeResult(left, def, ctx));
			return handlePipeResult(left, def, ctx);
		};
	});
	$ZodReadonly = /* @__PURE__ */ $constructor("$ZodReadonly", (inst, def) => {
		$ZodType.init(inst, def);
		defineLazy(inst._zod, "propValues", () => def.innerType._zod.propValues);
		defineLazy(inst._zod, "optin", () => def.innerType._zod.optin);
		defineLazy(inst._zod, "optout", () => def.innerType._zod.optout);
		inst._zod.parse = (payload, ctx) => {
			const result = def.innerType._zod.run(payload, ctx);
			if (result instanceof Promise) return result.then(handleReadonlyResult);
			return handleReadonlyResult(result);
		};
	});
	$ZodCustom = /* @__PURE__ */ $constructor("$ZodCustom", (inst, def) => {
		$ZodCheck.init(inst, def);
		$ZodType.init(inst, def);
		inst._zod.parse = (payload, _) => {
			return payload;
		};
		inst._zod.check = (payload) => {
			const input = payload.value;
			const r = def.fn(input);
			if (r instanceof Promise) return r.then((r) => handleRefineResult(r, payload, input, inst));
			handleRefineResult(r, payload, input, inst);
		};
	});
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/locales/en.js
function en_default() {
	return { localeError: error() };
}
var parsedType, error;
var init_en = require_chunk.__esmMin((() => {
	init_util();
	parsedType = (data) => {
		const t = typeof data;
		switch (t) {
			case "number": return Number.isNaN(data) ? "NaN" : "number";
			case "object":
				if (Array.isArray(data)) return "array";
				if (data === null) return "null";
				if (Object.getPrototypeOf(data) !== Object.prototype && data.constructor) return data.constructor.name;
		}
		return t;
	};
	error = () => {
		const Sizable = {
			string: {
				unit: "characters",
				verb: "to have"
			},
			file: {
				unit: "bytes",
				verb: "to have"
			},
			array: {
				unit: "items",
				verb: "to have"
			},
			set: {
				unit: "items",
				verb: "to have"
			}
		};
		function getSizing(origin) {
			return Sizable[origin] ?? null;
		}
		const Nouns = {
			regex: "input",
			email: "email address",
			url: "URL",
			emoji: "emoji",
			uuid: "UUID",
			uuidv4: "UUIDv4",
			uuidv6: "UUIDv6",
			nanoid: "nanoid",
			guid: "GUID",
			cuid: "cuid",
			cuid2: "cuid2",
			ulid: "ULID",
			xid: "XID",
			ksuid: "KSUID",
			datetime: "ISO datetime",
			date: "ISO date",
			time: "ISO time",
			duration: "ISO duration",
			ipv4: "IPv4 address",
			ipv6: "IPv6 address",
			cidrv4: "IPv4 range",
			cidrv6: "IPv6 range",
			base64: "base64-encoded string",
			base64url: "base64url-encoded string",
			json_string: "JSON string",
			e164: "E.164 number",
			jwt: "JWT",
			template_literal: "input"
		};
		return (issue) => {
			switch (issue.code) {
				case "invalid_type": return `Invalid input: expected ${issue.expected}, received ${parsedType(issue.input)}`;
				case "invalid_value":
					if (issue.values.length === 1) return `Invalid input: expected ${stringifyPrimitive(issue.values[0])}`;
					return `Invalid option: expected one of ${joinValues(issue.values, "|")}`;
				case "too_big": {
					const adj = issue.inclusive ? "<=" : "<";
					const sizing = getSizing(issue.origin);
					if (sizing) return `Too big: expected ${issue.origin ?? "value"} to have ${adj}${issue.maximum.toString()} ${sizing.unit ?? "elements"}`;
					return `Too big: expected ${issue.origin ?? "value"} to be ${adj}${issue.maximum.toString()}`;
				}
				case "too_small": {
					const adj = issue.inclusive ? ">=" : ">";
					const sizing = getSizing(issue.origin);
					if (sizing) return `Too small: expected ${issue.origin} to have ${adj}${issue.minimum.toString()} ${sizing.unit}`;
					return `Too small: expected ${issue.origin} to be ${adj}${issue.minimum.toString()}`;
				}
				case "invalid_format": {
					const _issue = issue;
					if (_issue.format === "starts_with") return `Invalid string: must start with "${_issue.prefix}"`;
					if (_issue.format === "ends_with") return `Invalid string: must end with "${_issue.suffix}"`;
					if (_issue.format === "includes") return `Invalid string: must include "${_issue.includes}"`;
					if (_issue.format === "regex") return `Invalid string: must match pattern ${_issue.pattern}`;
					return `Invalid ${Nouns[_issue.format] ?? issue.format}`;
				}
				case "not_multiple_of": return `Invalid number: must be a multiple of ${issue.divisor}`;
				case "unrecognized_keys": return `Unrecognized key${issue.keys.length > 1 ? "s" : ""}: ${joinValues(issue.keys, ", ")}`;
				case "invalid_key": return `Invalid key in ${issue.origin}`;
				case "invalid_union": return "Invalid input";
				case "invalid_element": return `Invalid value in ${issue.origin}`;
				default: return `Invalid input`;
			}
		};
	};
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/registries.js
function registry() {
	return new $ZodRegistry();
}
var $ZodRegistry, globalRegistry;
var init_registries = require_chunk.__esmMin((() => {
	$ZodRegistry = class {
		constructor() {
			this._map = /* @__PURE__ */ new WeakMap();
			this._idmap = /* @__PURE__ */ new Map();
		}
		add(schema, ..._meta) {
			const meta = _meta[0];
			this._map.set(schema, meta);
			if (meta && typeof meta === "object" && "id" in meta) {
				if (this._idmap.has(meta.id)) throw new Error(`ID ${meta.id} already exists in the registry`);
				this._idmap.set(meta.id, schema);
			}
			return this;
		}
		remove(schema) {
			this._map.delete(schema);
			return this;
		}
		get(schema) {
			const p = schema._zod.parent;
			if (p) {
				const pm = { ...this.get(p) ?? {} };
				delete pm.id;
				return {
					...pm,
					...this._map.get(schema)
				};
			}
			return this._map.get(schema);
		}
		has(schema) {
			return this._map.has(schema);
		}
	};
	globalRegistry = /* @__PURE__ */ registry();
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/api.js
function _string(Class, params) {
	return new Class({
		type: "string",
		...normalizeParams(params)
	});
}
function _email(Class, params) {
	return new Class({
		type: "string",
		format: "email",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _guid(Class, params) {
	return new Class({
		type: "string",
		format: "guid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _uuid(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _uuidv4(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		version: "v4",
		...normalizeParams(params)
	});
}
function _uuidv6(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		version: "v6",
		...normalizeParams(params)
	});
}
function _uuidv7(Class, params) {
	return new Class({
		type: "string",
		format: "uuid",
		check: "string_format",
		abort: false,
		version: "v7",
		...normalizeParams(params)
	});
}
function _url(Class, params) {
	return new Class({
		type: "string",
		format: "url",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _emoji(Class, params) {
	return new Class({
		type: "string",
		format: "emoji",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _nanoid(Class, params) {
	return new Class({
		type: "string",
		format: "nanoid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _cuid(Class, params) {
	return new Class({
		type: "string",
		format: "cuid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _cuid2(Class, params) {
	return new Class({
		type: "string",
		format: "cuid2",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _ulid(Class, params) {
	return new Class({
		type: "string",
		format: "ulid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _xid(Class, params) {
	return new Class({
		type: "string",
		format: "xid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _ksuid(Class, params) {
	return new Class({
		type: "string",
		format: "ksuid",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _ipv4(Class, params) {
	return new Class({
		type: "string",
		format: "ipv4",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _ipv6(Class, params) {
	return new Class({
		type: "string",
		format: "ipv6",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _cidrv4(Class, params) {
	return new Class({
		type: "string",
		format: "cidrv4",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _cidrv6(Class, params) {
	return new Class({
		type: "string",
		format: "cidrv6",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _base64(Class, params) {
	return new Class({
		type: "string",
		format: "base64",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _base64url(Class, params) {
	return new Class({
		type: "string",
		format: "base64url",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _e164(Class, params) {
	return new Class({
		type: "string",
		format: "e164",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _jwt(Class, params) {
	return new Class({
		type: "string",
		format: "jwt",
		check: "string_format",
		abort: false,
		...normalizeParams(params)
	});
}
function _isoDateTime(Class, params) {
	return new Class({
		type: "string",
		format: "datetime",
		check: "string_format",
		offset: false,
		local: false,
		precision: null,
		...normalizeParams(params)
	});
}
function _isoDate(Class, params) {
	return new Class({
		type: "string",
		format: "date",
		check: "string_format",
		...normalizeParams(params)
	});
}
function _isoTime(Class, params) {
	return new Class({
		type: "string",
		format: "time",
		check: "string_format",
		precision: null,
		...normalizeParams(params)
	});
}
function _isoDuration(Class, params) {
	return new Class({
		type: "string",
		format: "duration",
		check: "string_format",
		...normalizeParams(params)
	});
}
function _number(Class, params) {
	return new Class({
		type: "number",
		checks: [],
		...normalizeParams(params)
	});
}
function _int(Class, params) {
	return new Class({
		type: "number",
		check: "number_format",
		abort: false,
		format: "safeint",
		...normalizeParams(params)
	});
}
function _boolean(Class, params) {
	return new Class({
		type: "boolean",
		...normalizeParams(params)
	});
}
function _unknown(Class) {
	return new Class({ type: "unknown" });
}
function _never(Class, params) {
	return new Class({
		type: "never",
		...normalizeParams(params)
	});
}
function _lt(value, params) {
	return new $ZodCheckLessThan({
		check: "less_than",
		...normalizeParams(params),
		value,
		inclusive: false
	});
}
function _lte(value, params) {
	return new $ZodCheckLessThan({
		check: "less_than",
		...normalizeParams(params),
		value,
		inclusive: true
	});
}
function _gt(value, params) {
	return new $ZodCheckGreaterThan({
		check: "greater_than",
		...normalizeParams(params),
		value,
		inclusive: false
	});
}
function _gte(value, params) {
	return new $ZodCheckGreaterThan({
		check: "greater_than",
		...normalizeParams(params),
		value,
		inclusive: true
	});
}
function _multipleOf(value, params) {
	return new $ZodCheckMultipleOf({
		check: "multiple_of",
		...normalizeParams(params),
		value
	});
}
function _maxLength(maximum, params) {
	return new $ZodCheckMaxLength({
		check: "max_length",
		...normalizeParams(params),
		maximum
	});
}
function _minLength(minimum, params) {
	return new $ZodCheckMinLength({
		check: "min_length",
		...normalizeParams(params),
		minimum
	});
}
function _length(length, params) {
	return new $ZodCheckLengthEquals({
		check: "length_equals",
		...normalizeParams(params),
		length
	});
}
function _regex(pattern, params) {
	return new $ZodCheckRegex({
		check: "string_format",
		format: "regex",
		...normalizeParams(params),
		pattern
	});
}
function _lowercase(params) {
	return new $ZodCheckLowerCase({
		check: "string_format",
		format: "lowercase",
		...normalizeParams(params)
	});
}
function _uppercase(params) {
	return new $ZodCheckUpperCase({
		check: "string_format",
		format: "uppercase",
		...normalizeParams(params)
	});
}
function _includes(includes, params) {
	return new $ZodCheckIncludes({
		check: "string_format",
		format: "includes",
		...normalizeParams(params),
		includes
	});
}
function _startsWith(prefix, params) {
	return new $ZodCheckStartsWith({
		check: "string_format",
		format: "starts_with",
		...normalizeParams(params),
		prefix
	});
}
function _endsWith(suffix, params) {
	return new $ZodCheckEndsWith({
		check: "string_format",
		format: "ends_with",
		...normalizeParams(params),
		suffix
	});
}
function _overwrite(tx) {
	return new $ZodCheckOverwrite({
		check: "overwrite",
		tx
	});
}
function _normalize(form) {
	return _overwrite((input) => input.normalize(form));
}
function _trim() {
	return _overwrite((input) => input.trim());
}
function _toLowerCase() {
	return _overwrite((input) => input.toLowerCase());
}
function _toUpperCase() {
	return _overwrite((input) => input.toUpperCase());
}
function _array(Class, element, params) {
	return new Class({
		type: "array",
		element,
		...normalizeParams(params)
	});
}
function _refine(Class, fn, _params) {
	return new Class({
		type: "custom",
		check: "custom",
		fn,
		...normalizeParams(_params)
	});
}
var init_api = require_chunk.__esmMin((() => {
	init_checks$1();
	init_util();
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/function.js
var init_function = require_chunk.__esmMin((() => {}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/to-json-schema.js
var init_to_json_schema = require_chunk.__esmMin((() => {}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/core/index.js
var init_core = require_chunk.__esmMin((() => {
	init_core$1();
	init_parse$1();
	init_errors$1();
	init_schemas$1();
	init_checks$1();
	init_versions();
	init_util();
	init_regexes();
	init_en();
	init_registries();
	init_doc();
	init_function();
	init_api();
	init_to_json_schema();
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/classic/checks.js
var init_checks = require_chunk.__esmMin((() => {
	init_core();
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/classic/iso.js
function datetime(params) {
	return _isoDateTime(ZodISODateTime, params);
}
function date(params) {
	return _isoDate(ZodISODate, params);
}
function time(params) {
	return _isoTime(ZodISOTime, params);
}
function duration(params) {
	return _isoDuration(ZodISODuration, params);
}
var ZodISODateTime, ZodISODate, ZodISOTime, ZodISODuration;
var init_iso = require_chunk.__esmMin((() => {
	init_core();
	init_schemas();
	ZodISODateTime = /* @__PURE__ */ $constructor("ZodISODateTime", (inst, def) => {
		$ZodISODateTime.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodISODate = /* @__PURE__ */ $constructor("ZodISODate", (inst, def) => {
		$ZodISODate.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodISOTime = /* @__PURE__ */ $constructor("ZodISOTime", (inst, def) => {
		$ZodISOTime.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodISODuration = /* @__PURE__ */ $constructor("ZodISODuration", (inst, def) => {
		$ZodISODuration.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/classic/errors.js
var initializer, ZodError, ZodRealError;
var init_errors = require_chunk.__esmMin((() => {
	init_core();
	initializer = (inst, issues) => {
		$ZodError.init(inst, issues);
		inst.name = "ZodError";
		Object.defineProperties(inst, {
			format: { value: (mapper) => formatError(inst, mapper) },
			flatten: { value: (mapper) => flattenError(inst, mapper) },
			addIssue: { value: (issue) => inst.issues.push(issue) },
			addIssues: { value: (issues) => inst.issues.push(...issues) },
			isEmpty: { get() {
				return inst.issues.length === 0;
			} }
		});
	};
	ZodError = $constructor("ZodError", initializer);
	ZodRealError = $constructor("ZodError", initializer, { Parent: Error });
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/classic/parse.js
var parse, parseAsync, safeParse, safeParseAsync;
var init_parse = require_chunk.__esmMin((() => {
	init_core();
	init_errors();
	parse = /* @__PURE__ */ _parse(ZodRealError);
	parseAsync = /* @__PURE__ */ _parseAsync(ZodRealError);
	safeParse = /* @__PURE__ */ _safeParse(ZodRealError);
	safeParseAsync = /* @__PURE__ */ _safeParseAsync(ZodRealError);
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/classic/schemas.js
function string(params) {
	return _string(ZodString, params);
}
function number(params) {
	return _number(ZodNumber, params);
}
function int(params) {
	return _int(ZodNumberFormat, params);
}
function boolean(params) {
	return _boolean(ZodBoolean, params);
}
function unknown() {
	return _unknown(ZodUnknown);
}
function never(params) {
	return _never(ZodNever, params);
}
function array(element, params) {
	return _array(ZodArray, element, params);
}
function looseObject(shape, params) {
	return new ZodObject({
		type: "object",
		get shape() {
			assignProp(this, "shape", { ...shape });
			return this.shape;
		},
		catchall: unknown(),
		...normalizeParams(params)
	});
}
function union(options, params) {
	return new ZodUnion({
		type: "union",
		options,
		...normalizeParams(params)
	});
}
function intersection(left, right) {
	return new ZodIntersection({
		type: "intersection",
		left,
		right
	});
}
function record(keyType, valueType, params) {
	return new ZodRecord({
		type: "record",
		keyType,
		valueType,
		...normalizeParams(params)
	});
}
function _enum(values, params) {
	return new ZodEnum({
		type: "enum",
		entries: Array.isArray(values) ? Object.fromEntries(values.map((v) => [v, v])) : values,
		...normalizeParams(params)
	});
}
function literal(value, params) {
	return new ZodLiteral({
		type: "literal",
		values: Array.isArray(value) ? value : [value],
		...normalizeParams(params)
	});
}
function transform(fn) {
	return new ZodTransform({
		type: "transform",
		transform: fn
	});
}
function optional(innerType) {
	return new ZodOptional({
		type: "optional",
		innerType
	});
}
function nullable(innerType) {
	return new ZodNullable({
		type: "nullable",
		innerType
	});
}
function _default(innerType, defaultValue) {
	return new ZodDefault({
		type: "default",
		innerType,
		get defaultValue() {
			return typeof defaultValue === "function" ? defaultValue() : defaultValue;
		}
	});
}
function prefault(innerType, defaultValue) {
	return new ZodPrefault({
		type: "prefault",
		innerType,
		get defaultValue() {
			return typeof defaultValue === "function" ? defaultValue() : defaultValue;
		}
	});
}
function nonoptional(innerType, params) {
	return new ZodNonOptional({
		type: "nonoptional",
		innerType,
		...normalizeParams(params)
	});
}
function _catch(innerType, catchValue) {
	return new ZodCatch({
		type: "catch",
		innerType,
		catchValue: typeof catchValue === "function" ? catchValue : () => catchValue
	});
}
function pipe(in_, out) {
	return new ZodPipe({
		type: "pipe",
		in: in_,
		out
	});
}
function readonly(innerType) {
	return new ZodReadonly({
		type: "readonly",
		innerType
	});
}
function check(fn, params) {
	const ch = new $ZodCheck({
		check: "custom",
		...normalizeParams(params)
	});
	ch._zod.check = fn;
	return ch;
}
function refine(fn, _params = {}) {
	return _refine(ZodCustom, fn, _params);
}
function superRefine(fn, params) {
	const ch = check((payload) => {
		payload.addIssue = (issue$2) => {
			if (typeof issue$2 === "string") payload.issues.push(issue(issue$2, payload.value, ch._zod.def));
			else {
				const _issue = issue$2;
				if (_issue.fatal) _issue.continue = false;
				_issue.code ?? (_issue.code = "custom");
				_issue.input ?? (_issue.input = payload.value);
				_issue.inst ?? (_issue.inst = ch);
				_issue.continue ?? (_issue.continue = !ch._zod.def.abort);
				payload.issues.push(issue(_issue));
			}
		};
		return fn(payload.value, payload);
	}, params);
	return ch;
}
var ZodType, _ZodString, ZodString, ZodStringFormat, ZodEmail, ZodGUID, ZodUUID, ZodURL, ZodEmoji, ZodNanoID, ZodCUID, ZodCUID2, ZodULID, ZodXID, ZodKSUID, ZodIPv4, ZodIPv6, ZodCIDRv4, ZodCIDRv6, ZodBase64, ZodBase64URL, ZodE164, ZodJWT, ZodNumber, ZodNumberFormat, ZodBoolean, ZodUnknown, ZodNever, ZodArray, ZodObject, ZodUnion, ZodIntersection, ZodRecord, ZodEnum, ZodLiteral, ZodTransform, ZodOptional, ZodNullable, ZodDefault, ZodPrefault, ZodNonOptional, ZodCatch, ZodPipe, ZodReadonly, ZodCustom;
var init_schemas = require_chunk.__esmMin((() => {
	init_core();
	init_checks();
	init_iso();
	init_parse();
	ZodType = /* @__PURE__ */ $constructor("ZodType", (inst, def) => {
		$ZodType.init(inst, def);
		inst.def = def;
		Object.defineProperty(inst, "_def", { value: def });
		inst.check = (...checks) => {
			return inst.clone({
				...def,
				checks: [...def.checks ?? [], ...checks.map((ch) => typeof ch === "function" ? { _zod: {
					check: ch,
					def: { check: "custom" },
					onattach: []
				} } : ch)]
			});
		};
		inst.clone = (def, params) => clone(inst, def, params);
		inst.brand = () => inst;
		inst.register = ((reg, meta) => {
			reg.add(inst, meta);
			return inst;
		});
		inst.parse = (data, params) => parse(inst, data, params, { callee: inst.parse });
		inst.safeParse = (data, params) => safeParse(inst, data, params);
		inst.parseAsync = async (data, params) => parseAsync(inst, data, params, { callee: inst.parseAsync });
		inst.safeParseAsync = async (data, params) => safeParseAsync(inst, data, params);
		inst.spa = inst.safeParseAsync;
		inst.refine = (check, params) => inst.check(refine(check, params));
		inst.superRefine = (refinement) => inst.check(superRefine(refinement));
		inst.overwrite = (fn) => inst.check(_overwrite(fn));
		inst.optional = () => optional(inst);
		inst.nullable = () => nullable(inst);
		inst.nullish = () => optional(nullable(inst));
		inst.nonoptional = (params) => nonoptional(inst, params);
		inst.array = () => array(inst);
		inst.or = (arg) => union([inst, arg]);
		inst.and = (arg) => intersection(inst, arg);
		inst.transform = (tx) => pipe(inst, transform(tx));
		inst.default = (def) => _default(inst, def);
		inst.prefault = (def) => prefault(inst, def);
		inst.catch = (params) => _catch(inst, params);
		inst.pipe = (target) => pipe(inst, target);
		inst.readonly = () => readonly(inst);
		inst.describe = (description) => {
			const cl = inst.clone();
			globalRegistry.add(cl, { description });
			return cl;
		};
		Object.defineProperty(inst, "description", {
			get() {
				return globalRegistry.get(inst)?.description;
			},
			configurable: true
		});
		inst.meta = (...args) => {
			if (args.length === 0) return globalRegistry.get(inst);
			const cl = inst.clone();
			globalRegistry.add(cl, args[0]);
			return cl;
		};
		inst.isOptional = () => inst.safeParse(void 0).success;
		inst.isNullable = () => inst.safeParse(null).success;
		return inst;
	});
	_ZodString = /* @__PURE__ */ $constructor("_ZodString", (inst, def) => {
		$ZodString.init(inst, def);
		ZodType.init(inst, def);
		const bag = inst._zod.bag;
		inst.format = bag.format ?? null;
		inst.minLength = bag.minimum ?? null;
		inst.maxLength = bag.maximum ?? null;
		inst.regex = (...args) => inst.check(_regex(...args));
		inst.includes = (...args) => inst.check(_includes(...args));
		inst.startsWith = (...args) => inst.check(_startsWith(...args));
		inst.endsWith = (...args) => inst.check(_endsWith(...args));
		inst.min = (...args) => inst.check(_minLength(...args));
		inst.max = (...args) => inst.check(_maxLength(...args));
		inst.length = (...args) => inst.check(_length(...args));
		inst.nonempty = (...args) => inst.check(_minLength(1, ...args));
		inst.lowercase = (params) => inst.check(_lowercase(params));
		inst.uppercase = (params) => inst.check(_uppercase(params));
		inst.trim = () => inst.check(_trim());
		inst.normalize = (...args) => inst.check(_normalize(...args));
		inst.toLowerCase = () => inst.check(_toLowerCase());
		inst.toUpperCase = () => inst.check(_toUpperCase());
	});
	ZodString = /* @__PURE__ */ $constructor("ZodString", (inst, def) => {
		$ZodString.init(inst, def);
		_ZodString.init(inst, def);
		inst.email = (params) => inst.check(_email(ZodEmail, params));
		inst.url = (params) => inst.check(_url(ZodURL, params));
		inst.jwt = (params) => inst.check(_jwt(ZodJWT, params));
		inst.emoji = (params) => inst.check(_emoji(ZodEmoji, params));
		inst.guid = (params) => inst.check(_guid(ZodGUID, params));
		inst.uuid = (params) => inst.check(_uuid(ZodUUID, params));
		inst.uuidv4 = (params) => inst.check(_uuidv4(ZodUUID, params));
		inst.uuidv6 = (params) => inst.check(_uuidv6(ZodUUID, params));
		inst.uuidv7 = (params) => inst.check(_uuidv7(ZodUUID, params));
		inst.nanoid = (params) => inst.check(_nanoid(ZodNanoID, params));
		inst.guid = (params) => inst.check(_guid(ZodGUID, params));
		inst.cuid = (params) => inst.check(_cuid(ZodCUID, params));
		inst.cuid2 = (params) => inst.check(_cuid2(ZodCUID2, params));
		inst.ulid = (params) => inst.check(_ulid(ZodULID, params));
		inst.base64 = (params) => inst.check(_base64(ZodBase64, params));
		inst.base64url = (params) => inst.check(_base64url(ZodBase64URL, params));
		inst.xid = (params) => inst.check(_xid(ZodXID, params));
		inst.ksuid = (params) => inst.check(_ksuid(ZodKSUID, params));
		inst.ipv4 = (params) => inst.check(_ipv4(ZodIPv4, params));
		inst.ipv6 = (params) => inst.check(_ipv6(ZodIPv6, params));
		inst.cidrv4 = (params) => inst.check(_cidrv4(ZodCIDRv4, params));
		inst.cidrv6 = (params) => inst.check(_cidrv6(ZodCIDRv6, params));
		inst.e164 = (params) => inst.check(_e164(ZodE164, params));
		inst.datetime = (params) => inst.check(datetime(params));
		inst.date = (params) => inst.check(date(params));
		inst.time = (params) => inst.check(time(params));
		inst.duration = (params) => inst.check(duration(params));
	});
	ZodStringFormat = /* @__PURE__ */ $constructor("ZodStringFormat", (inst, def) => {
		$ZodStringFormat.init(inst, def);
		_ZodString.init(inst, def);
	});
	ZodEmail = /* @__PURE__ */ $constructor("ZodEmail", (inst, def) => {
		$ZodEmail.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodGUID = /* @__PURE__ */ $constructor("ZodGUID", (inst, def) => {
		$ZodGUID.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodUUID = /* @__PURE__ */ $constructor("ZodUUID", (inst, def) => {
		$ZodUUID.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodURL = /* @__PURE__ */ $constructor("ZodURL", (inst, def) => {
		$ZodURL.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodEmoji = /* @__PURE__ */ $constructor("ZodEmoji", (inst, def) => {
		$ZodEmoji.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodNanoID = /* @__PURE__ */ $constructor("ZodNanoID", (inst, def) => {
		$ZodNanoID.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodCUID = /* @__PURE__ */ $constructor("ZodCUID", (inst, def) => {
		$ZodCUID.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodCUID2 = /* @__PURE__ */ $constructor("ZodCUID2", (inst, def) => {
		$ZodCUID2.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodULID = /* @__PURE__ */ $constructor("ZodULID", (inst, def) => {
		$ZodULID.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodXID = /* @__PURE__ */ $constructor("ZodXID", (inst, def) => {
		$ZodXID.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodKSUID = /* @__PURE__ */ $constructor("ZodKSUID", (inst, def) => {
		$ZodKSUID.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodIPv4 = /* @__PURE__ */ $constructor("ZodIPv4", (inst, def) => {
		$ZodIPv4.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodIPv6 = /* @__PURE__ */ $constructor("ZodIPv6", (inst, def) => {
		$ZodIPv6.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodCIDRv4 = /* @__PURE__ */ $constructor("ZodCIDRv4", (inst, def) => {
		$ZodCIDRv4.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodCIDRv6 = /* @__PURE__ */ $constructor("ZodCIDRv6", (inst, def) => {
		$ZodCIDRv6.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodBase64 = /* @__PURE__ */ $constructor("ZodBase64", (inst, def) => {
		$ZodBase64.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodBase64URL = /* @__PURE__ */ $constructor("ZodBase64URL", (inst, def) => {
		$ZodBase64URL.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodE164 = /* @__PURE__ */ $constructor("ZodE164", (inst, def) => {
		$ZodE164.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodJWT = /* @__PURE__ */ $constructor("ZodJWT", (inst, def) => {
		$ZodJWT.init(inst, def);
		ZodStringFormat.init(inst, def);
	});
	ZodNumber = /* @__PURE__ */ $constructor("ZodNumber", (inst, def) => {
		$ZodNumber.init(inst, def);
		ZodType.init(inst, def);
		inst.gt = (value, params) => inst.check(_gt(value, params));
		inst.gte = (value, params) => inst.check(_gte(value, params));
		inst.min = (value, params) => inst.check(_gte(value, params));
		inst.lt = (value, params) => inst.check(_lt(value, params));
		inst.lte = (value, params) => inst.check(_lte(value, params));
		inst.max = (value, params) => inst.check(_lte(value, params));
		inst.int = (params) => inst.check(int(params));
		inst.safe = (params) => inst.check(int(params));
		inst.positive = (params) => inst.check(_gt(0, params));
		inst.nonnegative = (params) => inst.check(_gte(0, params));
		inst.negative = (params) => inst.check(_lt(0, params));
		inst.nonpositive = (params) => inst.check(_lte(0, params));
		inst.multipleOf = (value, params) => inst.check(_multipleOf(value, params));
		inst.step = (value, params) => inst.check(_multipleOf(value, params));
		inst.finite = () => inst;
		const bag = inst._zod.bag;
		inst.minValue = Math.max(bag.minimum ?? Number.NEGATIVE_INFINITY, bag.exclusiveMinimum ?? Number.NEGATIVE_INFINITY) ?? null;
		inst.maxValue = Math.min(bag.maximum ?? Number.POSITIVE_INFINITY, bag.exclusiveMaximum ?? Number.POSITIVE_INFINITY) ?? null;
		inst.isInt = (bag.format ?? "").includes("int") || Number.isSafeInteger(bag.multipleOf ?? .5);
		inst.isFinite = true;
		inst.format = bag.format ?? null;
	});
	ZodNumberFormat = /* @__PURE__ */ $constructor("ZodNumberFormat", (inst, def) => {
		$ZodNumberFormat.init(inst, def);
		ZodNumber.init(inst, def);
	});
	ZodBoolean = /* @__PURE__ */ $constructor("ZodBoolean", (inst, def) => {
		$ZodBoolean.init(inst, def);
		ZodType.init(inst, def);
	});
	ZodUnknown = /* @__PURE__ */ $constructor("ZodUnknown", (inst, def) => {
		$ZodUnknown.init(inst, def);
		ZodType.init(inst, def);
	});
	ZodNever = /* @__PURE__ */ $constructor("ZodNever", (inst, def) => {
		$ZodNever.init(inst, def);
		ZodType.init(inst, def);
	});
	ZodArray = /* @__PURE__ */ $constructor("ZodArray", (inst, def) => {
		$ZodArray.init(inst, def);
		ZodType.init(inst, def);
		inst.element = def.element;
		inst.min = (minLength, params) => inst.check(_minLength(minLength, params));
		inst.nonempty = (params) => inst.check(_minLength(1, params));
		inst.max = (maxLength, params) => inst.check(_maxLength(maxLength, params));
		inst.length = (len, params) => inst.check(_length(len, params));
		inst.unwrap = () => inst.element;
	});
	ZodObject = /* @__PURE__ */ $constructor("ZodObject", (inst, def) => {
		$ZodObject.init(inst, def);
		ZodType.init(inst, def);
		defineLazy(inst, "shape", () => def.shape);
		inst.keyof = () => _enum(Object.keys(inst._zod.def.shape));
		inst.catchall = (catchall) => inst.clone({
			...inst._zod.def,
			catchall
		});
		inst.passthrough = () => inst.clone({
			...inst._zod.def,
			catchall: unknown()
		});
		inst.loose = () => inst.clone({
			...inst._zod.def,
			catchall: unknown()
		});
		inst.strict = () => inst.clone({
			...inst._zod.def,
			catchall: never()
		});
		inst.strip = () => inst.clone({
			...inst._zod.def,
			catchall: void 0
		});
		inst.extend = (incoming) => {
			return extend(inst, incoming);
		};
		inst.merge = (other) => merge(inst, other);
		inst.pick = (mask) => pick(inst, mask);
		inst.omit = (mask) => omit(inst, mask);
		inst.partial = (...args) => partial(ZodOptional, inst, args[0]);
		inst.required = (...args) => required(ZodNonOptional, inst, args[0]);
	});
	ZodUnion = /* @__PURE__ */ $constructor("ZodUnion", (inst, def) => {
		$ZodUnion.init(inst, def);
		ZodType.init(inst, def);
		inst.options = def.options;
	});
	ZodIntersection = /* @__PURE__ */ $constructor("ZodIntersection", (inst, def) => {
		$ZodIntersection.init(inst, def);
		ZodType.init(inst, def);
	});
	ZodRecord = /* @__PURE__ */ $constructor("ZodRecord", (inst, def) => {
		$ZodRecord.init(inst, def);
		ZodType.init(inst, def);
		inst.keyType = def.keyType;
		inst.valueType = def.valueType;
	});
	ZodEnum = /* @__PURE__ */ $constructor("ZodEnum", (inst, def) => {
		$ZodEnum.init(inst, def);
		ZodType.init(inst, def);
		inst.enum = def.entries;
		inst.options = Object.values(def.entries);
		const keys = new Set(Object.keys(def.entries));
		inst.extract = (values, params) => {
			const newEntries = {};
			for (const value of values) if (keys.has(value)) newEntries[value] = def.entries[value];
			else throw new Error(`Key ${value} not found in enum`);
			return new ZodEnum({
				...def,
				checks: [],
				...normalizeParams(params),
				entries: newEntries
			});
		};
		inst.exclude = (values, params) => {
			const newEntries = { ...def.entries };
			for (const value of values) if (keys.has(value)) delete newEntries[value];
			else throw new Error(`Key ${value} not found in enum`);
			return new ZodEnum({
				...def,
				checks: [],
				...normalizeParams(params),
				entries: newEntries
			});
		};
	});
	ZodLiteral = /* @__PURE__ */ $constructor("ZodLiteral", (inst, def) => {
		$ZodLiteral.init(inst, def);
		ZodType.init(inst, def);
		inst.values = new Set(def.values);
		Object.defineProperty(inst, "value", { get() {
			if (def.values.length > 1) throw new Error("This schema contains multiple valid literal values. Use `.values` instead.");
			return def.values[0];
		} });
	});
	ZodTransform = /* @__PURE__ */ $constructor("ZodTransform", (inst, def) => {
		$ZodTransform.init(inst, def);
		ZodType.init(inst, def);
		inst._zod.parse = (payload, _ctx) => {
			payload.addIssue = (issue$1) => {
				if (typeof issue$1 === "string") payload.issues.push(issue(issue$1, payload.value, def));
				else {
					const _issue = issue$1;
					if (_issue.fatal) _issue.continue = false;
					_issue.code ?? (_issue.code = "custom");
					_issue.input ?? (_issue.input = payload.value);
					_issue.inst ?? (_issue.inst = inst);
					_issue.continue ?? (_issue.continue = true);
					payload.issues.push(issue(_issue));
				}
			};
			const output = def.transform(payload.value, payload);
			if (output instanceof Promise) return output.then((output) => {
				payload.value = output;
				return payload;
			});
			payload.value = output;
			return payload;
		};
	});
	ZodOptional = /* @__PURE__ */ $constructor("ZodOptional", (inst, def) => {
		$ZodOptional.init(inst, def);
		ZodType.init(inst, def);
		inst.unwrap = () => inst._zod.def.innerType;
	});
	ZodNullable = /* @__PURE__ */ $constructor("ZodNullable", (inst, def) => {
		$ZodNullable.init(inst, def);
		ZodType.init(inst, def);
		inst.unwrap = () => inst._zod.def.innerType;
	});
	ZodDefault = /* @__PURE__ */ $constructor("ZodDefault", (inst, def) => {
		$ZodDefault.init(inst, def);
		ZodType.init(inst, def);
		inst.unwrap = () => inst._zod.def.innerType;
		inst.removeDefault = inst.unwrap;
	});
	ZodPrefault = /* @__PURE__ */ $constructor("ZodPrefault", (inst, def) => {
		$ZodPrefault.init(inst, def);
		ZodType.init(inst, def);
		inst.unwrap = () => inst._zod.def.innerType;
	});
	ZodNonOptional = /* @__PURE__ */ $constructor("ZodNonOptional", (inst, def) => {
		$ZodNonOptional.init(inst, def);
		ZodType.init(inst, def);
		inst.unwrap = () => inst._zod.def.innerType;
	});
	ZodCatch = /* @__PURE__ */ $constructor("ZodCatch", (inst, def) => {
		$ZodCatch.init(inst, def);
		ZodType.init(inst, def);
		inst.unwrap = () => inst._zod.def.innerType;
		inst.removeCatch = inst.unwrap;
	});
	ZodPipe = /* @__PURE__ */ $constructor("ZodPipe", (inst, def) => {
		$ZodPipe.init(inst, def);
		ZodType.init(inst, def);
		inst.in = def.in;
		inst.out = def.out;
	});
	ZodReadonly = /* @__PURE__ */ $constructor("ZodReadonly", (inst, def) => {
		$ZodReadonly.init(inst, def);
		ZodType.init(inst, def);
	});
	ZodCustom = /* @__PURE__ */ $constructor("ZodCustom", (inst, def) => {
		$ZodCustom.init(inst, def);
		ZodType.init(inst, def);
	});
}));
var init_compat = require_chunk.__esmMin((() => {
	init_core();
	Object.freeze({ status: "aborted" });
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/classic/external.js
var init_external = require_chunk.__esmMin((() => {
	init_core();
	init_schemas();
	init_checks();
	init_errors();
	init_parse();
	init_compat();
	init_en();
	init_util();
	init_iso();
	init_schemas();
	config(en_default());
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/classic/index.js
var init_classic = require_chunk.__esmMin((() => {
	init_external();
	init_external();
}));
//#endregion
//#region ../../node_modules/zod/dist/esm/v4/index.js
var init_v4 = require_chunk.__esmMin((() => {
	init_classic();
}));
//#endregion
//#region ../../node_modules/@agentclientprotocol/sdk/dist/schema/index.js
var AGENT_METHODS, CLIENT_METHODS, PROTOCOL_VERSION;
var init_schema = require_chunk.__esmMin((() => {
	AGENT_METHODS = {
		authenticate: "authenticate",
		document_did_change: "document/didChange",
		document_did_close: "document/didClose",
		document_did_focus: "document/didFocus",
		document_did_open: "document/didOpen",
		document_did_save: "document/didSave",
		initialize: "initialize",
		logout: "logout",
		nes_accept: "nes/accept",
		nes_close: "nes/close",
		nes_reject: "nes/reject",
		nes_start: "nes/start",
		nes_suggest: "nes/suggest",
		session_cancel: "session/cancel",
		session_close: "session/close",
		session_fork: "session/fork",
		session_list: "session/list",
		session_load: "session/load",
		session_new: "session/new",
		session_prompt: "session/prompt",
		session_resume: "session/resume",
		session_set_config_option: "session/set_config_option",
		session_set_mode: "session/set_mode",
		session_set_model: "session/set_model"
	};
	CLIENT_METHODS = {
		fs_read_text_file: "fs/read_text_file",
		fs_write_text_file: "fs/write_text_file",
		session_elicitation: "session/elicitation",
		session_elicitation_complete: "session/elicitation/complete",
		session_request_permission: "session/request_permission",
		session_update: "session/update",
		terminal_create: "terminal/create",
		terminal_kill: "terminal/kill",
		terminal_output: "terminal/output",
		terminal_release: "terminal/release",
		terminal_wait_for_exit: "terminal/wait_for_exit"
	};
	PROTOCOL_VERSION = 1;
})), zAuthCapabilities, zAuthEnvVar, zAuthMethodAgent, zAuthMethodEnvVar, zAuthMethodTerminal, zAuthMethod, zAuthenticateRequest, zAuthenticateResponse, zBlobResourceContents, zBooleanPropertySchema, zCloseNesResponse, zCloseSessionResponse, zCost, zCreateTerminalResponse, zDiff, zElicitationContentValue, zElicitationAcceptAction, zElicitationAction, zElicitationFormCapabilities, zElicitationId, zElicitationCompleteNotification, zElicitationResponse, zElicitationSchemaType, zElicitationStringType, zElicitationUrlCapabilities, zElicitationCapabilities, zElicitationUrlMode, zEnumOption, zEnvVariable, zErrorCode, zError, zExtNotification, zExtRequest, zExtResponse, zFileSystemCapabilities, zHttpHeader, zImplementation, zIntegerPropertySchema, zKillTerminalResponse, zListSessionsRequest, zLogoutCapabilities, zAgentAuthCapabilities, zLogoutRequest, zLogoutResponse, zMcpCapabilities, zMcpServerHttp, zMcpServerSse, zMcpServerStdio, zMcpServer, zModelId, zModelInfo, zNesDiagnosticSeverity, zNesDiagnosticsCapabilities, zNesDocumentDidCloseCapabilities, zNesDocumentDidFocusCapabilities, zNesDocumentDidOpenCapabilities, zNesDocumentDidSaveCapabilities, zNesEditHistoryCapabilities, zNesEditHistoryEntry, zNesExcerpt, zNesJumpCapabilities, zNesOpenFilesCapabilities, zNesRecentFile, zNesRecentFilesCapabilities, zNesRejectReason, zNesRelatedSnippet, zNesRelatedSnippetsCapabilities, zNesRenameCapabilities, zNesRepository, zNesSearchAndReplaceCapabilities, zClientNesCapabilities, zNesSearchAndReplaceSuggestion, zNesTriggerKind, zNesUserActionsCapabilities, zNesContextCapabilities, zNewSessionRequest, zNumberPropertySchema, zPermissionOptionId, zPermissionOptionKind, zPermissionOption, zPlanEntryPriority, zPlanEntryStatus, zPlanEntry, zPlan, zPosition, zNesJumpSuggestion, zNesRenameSuggestion, zNesUserAction, zPositionEncodingKind, zClientCapabilities, zPromptCapabilities, zProtocolVersion, zInitializeRequest, zRange, zNesDiagnostic, zNesOpenFile, zNesSuggestContext, zNesTextEdit, zNesEditSuggestion, zNesSuggestion, zReadTextFileResponse, zReleaseTerminalResponse, zRequestId, zRole, zAnnotations, zAudioContent, zImageContent, zResourceLink, zSelectedPermissionOutcome, zRequestPermissionOutcome, zRequestPermissionResponse, zSessionAdditionalDirectoriesCapabilities, zSessionCloseCapabilities, zSessionConfigBoolean, zSessionConfigGroupId, zSessionConfigId, zSessionConfigOptionCategory, zSessionConfigValueId, zSessionConfigSelectOption, zSessionConfigSelectGroup, zSessionConfigSelectOptions, zSessionConfigSelect, zSessionConfigOption, zConfigOptionUpdate, zSessionForkCapabilities, zSessionId, zAcceptNesNotification, zCancelNotification, zCloseNesRequest, zCloseSessionRequest, zCreateTerminalRequest, zDidCloseDocumentNotification, zDidFocusDocumentNotification, zDidOpenDocumentNotification, zDidSaveDocumentNotification, zForkSessionRequest, zKillTerminalRequest, zLoadSessionRequest, zReadTextFileRequest, zRejectNesNotification, zReleaseTerminalRequest, zResumeSessionRequest, zSessionInfo, zListSessionsResponse, zSessionInfoUpdate, zSessionListCapabilities, zSessionModeId, zCurrentModeUpdate, zSessionMode, zSessionModeState, zSessionModelState, zForkSessionResponse, zLoadSessionResponse, zNewSessionResponse, zResumeSessionResponse, zSessionResumeCapabilities, zSessionCapabilities, zSetSessionConfigOptionRequest, zSetSessionConfigOptionResponse, zSetSessionModeRequest, zSetSessionModeResponse, zSetSessionModelRequest, zSetSessionModelResponse, zStartNesResponse, zStopReason, zStringFormat, zStringPropertySchema, zSuggestNesRequest, zSuggestNesResponse, zTerminal, zTerminalExitStatus, zTerminalOutputRequest, zTerminalOutputResponse, zTextContent, zTextDocumentContentChangeEvent, zDidChangeDocumentNotification, zTextDocumentSyncKind, zNesDocumentDidChangeCapabilities, zNesDocumentEventCapabilities, zNesEventCapabilities, zNesCapabilities, zAgentCapabilities, zInitializeResponse, zTextResourceContents, zEmbeddedResourceResource, zEmbeddedResource, zContentBlock, zContent, zContentChunk, zPromptRequest, zTitledMultiSelectItems, zToolCallContent, zToolCallId, zToolCallLocation, zToolCallStatus, zToolKind, zToolCall, zToolCallUpdate, zRequestPermissionRequest, zUnstructuredCommandInput, zAvailableCommandInput, zAvailableCommand, zAvailableCommandsUpdate, zUntitledMultiSelectItems, zMultiSelectItems, zMultiSelectPropertySchema, zElicitationPropertySchema, zElicitationSchema, zElicitationFormMode, zElicitationRequest, zUsage, zPromptResponse, zUsageUpdate, zSessionUpdate, zSessionNotification, zWaitForTerminalExitRequest, zWaitForTerminalExitResponse, zWorkspaceFolder, zStartNesRequest, zWriteTextFileRequest, zWriteTextFileResponse;
var init_zod_gen = require_chunk.__esmMin((() => {
	init_v4();
	zAuthCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		terminal: boolean().optional().default(false)
	});
	zAuthEnvVar = looseObject({
		_meta: record(string(), unknown()).nullish(),
		label: string().nullish(),
		name: string(),
		optional: boolean().optional().default(false),
		secret: boolean().optional().default(true)
	});
	zAuthMethodAgent = looseObject({
		_meta: record(string(), unknown()).nullish(),
		description: string().nullish(),
		id: string(),
		name: string()
	});
	zAuthMethodEnvVar = looseObject({
		_meta: record(string(), unknown()).nullish(),
		description: string().nullish(),
		id: string(),
		link: string().nullish(),
		name: string(),
		vars: array(zAuthEnvVar)
	});
	zAuthMethodTerminal = looseObject({
		_meta: record(string(), unknown()).nullish(),
		args: array(string()).optional(),
		description: string().nullish(),
		env: record(string(), string()).optional(),
		id: string(),
		name: string()
	});
	zAuthMethod = union([
		zAuthMethodEnvVar.and(looseObject({ type: literal("env_var") })),
		zAuthMethodTerminal.and(looseObject({ type: literal("terminal") })),
		zAuthMethodAgent
	]);
	zAuthenticateRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		methodId: string()
	});
	zAuthenticateResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zBlobResourceContents = looseObject({
		_meta: record(string(), unknown()).nullish(),
		blob: string(),
		mimeType: string().nullish(),
		uri: string()
	});
	zBooleanPropertySchema = looseObject({
		default: boolean().nullish(),
		description: string().nullish(),
		title: string().nullish()
	});
	zCloseNesResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zCloseSessionResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zCost = looseObject({
		amount: number(),
		currency: string()
	});
	zCreateTerminalResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		terminalId: string()
	});
	zDiff = looseObject({
		_meta: record(string(), unknown()).nullish(),
		newText: string(),
		oldText: string().nullish(),
		path: string()
	});
	zElicitationContentValue = union([
		string(),
		number(),
		number(),
		boolean(),
		array(string())
	]);
	zElicitationAcceptAction = looseObject({ content: record(string(), zElicitationContentValue).nullish() });
	zElicitationAction = union([
		zElicitationAcceptAction.and(looseObject({ action: literal("accept") })),
		looseObject({ action: literal("decline") }),
		looseObject({ action: literal("cancel") })
	]);
	zElicitationFormCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zElicitationId = string();
	zElicitationCompleteNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		elicitationId: zElicitationId
	});
	zElicitationResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		action: zElicitationAction
	});
	zElicitationSchemaType = literal("object");
	zElicitationStringType = literal("string");
	zElicitationUrlCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zElicitationCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		form: zElicitationFormCapabilities.nullish(),
		url: zElicitationUrlCapabilities.nullish()
	});
	zElicitationUrlMode = looseObject({
		elicitationId: zElicitationId,
		url: string().url()
	});
	zEnumOption = looseObject({
		const: string(),
		title: string()
	});
	zEnvVariable = looseObject({
		_meta: record(string(), unknown()).nullish(),
		name: string(),
		value: string()
	});
	zErrorCode = union([
		literal(-32700),
		literal(-32600),
		literal(-32601),
		literal(-32602),
		literal(-32603),
		literal(-32800),
		literal(-32e3),
		literal(-32002),
		literal(-32042),
		number().int().min(-2147483648, { message: "Invalid value: Expected int32 to be >= -2147483648" }).max(2147483647, { message: "Invalid value: Expected int32 to be <= 2147483647" })
	]);
	zError = looseObject({
		code: zErrorCode,
		data: unknown().optional(),
		message: string()
	});
	zExtNotification = unknown();
	zExtRequest = unknown();
	zExtResponse = unknown();
	zFileSystemCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		readTextFile: boolean().optional().default(false),
		writeTextFile: boolean().optional().default(false)
	});
	zHttpHeader = looseObject({
		_meta: record(string(), unknown()).nullish(),
		name: string(),
		value: string()
	});
	zImplementation = looseObject({
		_meta: record(string(), unknown()).nullish(),
		name: string(),
		title: string().nullish(),
		version: string()
	});
	zIntegerPropertySchema = looseObject({
		default: number().nullish(),
		description: string().nullish(),
		maximum: number().nullish(),
		minimum: number().nullish(),
		title: string().nullish()
	});
	zKillTerminalResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zListSessionsRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		additionalDirectories: array(string()).optional(),
		cursor: string().nullish(),
		cwd: string().nullish()
	});
	zLogoutCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zAgentAuthCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		logout: zLogoutCapabilities.nullish()
	});
	zLogoutRequest = looseObject({ _meta: record(string(), unknown()).nullish() });
	zLogoutResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zMcpCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		http: boolean().optional().default(false),
		sse: boolean().optional().default(false)
	});
	zMcpServerHttp = looseObject({
		_meta: record(string(), unknown()).nullish(),
		headers: array(zHttpHeader),
		name: string(),
		url: string()
	});
	zMcpServerSse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		headers: array(zHttpHeader),
		name: string(),
		url: string()
	});
	zMcpServerStdio = looseObject({
		_meta: record(string(), unknown()).nullish(),
		args: array(string()),
		command: string(),
		env: array(zEnvVariable),
		name: string()
	});
	zMcpServer = union([
		zMcpServerHttp.and(looseObject({ type: literal("http") })),
		zMcpServerSse.and(looseObject({ type: literal("sse") })),
		zMcpServerStdio
	]);
	zModelId = string();
	zModelInfo = looseObject({
		_meta: record(string(), unknown()).nullish(),
		description: string().nullish(),
		modelId: zModelId,
		name: string()
	});
	zNesDiagnosticSeverity = union([
		literal("error"),
		literal("warning"),
		literal("information"),
		literal("hint")
	]);
	zNesDiagnosticsCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesDocumentDidCloseCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesDocumentDidFocusCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesDocumentDidOpenCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesDocumentDidSaveCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesEditHistoryCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		maxCount: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish()
	});
	zNesEditHistoryEntry = looseObject({
		diff: string(),
		uri: string()
	});
	zNesExcerpt = looseObject({
		endLine: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }),
		startLine: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }),
		text: string()
	});
	zNesJumpCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesOpenFilesCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesRecentFile = looseObject({
		languageId: string(),
		text: string(),
		uri: string()
	});
	zNesRecentFilesCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		maxCount: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish()
	});
	zNesRejectReason = union([
		literal("rejected"),
		literal("ignored"),
		literal("replaced"),
		literal("cancelled")
	]);
	zNesRelatedSnippet = looseObject({
		excerpts: array(zNesExcerpt),
		uri: string()
	});
	zNesRelatedSnippetsCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesRenameCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zNesRepository = looseObject({
		name: string(),
		owner: string(),
		remoteUrl: string()
	});
	zNesSearchAndReplaceCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zClientNesCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		jump: zNesJumpCapabilities.nullish(),
		rename: zNesRenameCapabilities.nullish(),
		searchAndReplace: zNesSearchAndReplaceCapabilities.nullish()
	});
	zNesSearchAndReplaceSuggestion = looseObject({
		id: string(),
		isRegex: boolean().nullish(),
		replace: string(),
		search: string(),
		uri: string()
	});
	zNesTriggerKind = union([
		literal("automatic"),
		literal("diagnostic"),
		literal("manual")
	]);
	zNesUserActionsCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		maxCount: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish()
	});
	zNesContextCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		diagnostics: zNesDiagnosticsCapabilities.nullish(),
		editHistory: zNesEditHistoryCapabilities.nullish(),
		openFiles: zNesOpenFilesCapabilities.nullish(),
		recentFiles: zNesRecentFilesCapabilities.nullish(),
		relatedSnippets: zNesRelatedSnippetsCapabilities.nullish(),
		userActions: zNesUserActionsCapabilities.nullish()
	});
	zNewSessionRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		additionalDirectories: array(string()).optional(),
		cwd: string(),
		mcpServers: array(zMcpServer)
	});
	zNumberPropertySchema = looseObject({
		default: number().nullish(),
		description: string().nullish(),
		maximum: number().nullish(),
		minimum: number().nullish(),
		title: string().nullish()
	});
	zPermissionOptionId = string();
	zPermissionOptionKind = union([
		literal("allow_once"),
		literal("allow_always"),
		literal("reject_once"),
		literal("reject_always")
	]);
	zPermissionOption = looseObject({
		_meta: record(string(), unknown()).nullish(),
		kind: zPermissionOptionKind,
		name: string(),
		optionId: zPermissionOptionId
	});
	zPlanEntryPriority = union([
		literal("high"),
		literal("medium"),
		literal("low")
	]);
	zPlanEntryStatus = union([
		literal("pending"),
		literal("in_progress"),
		literal("completed")
	]);
	zPlanEntry = looseObject({
		_meta: record(string(), unknown()).nullish(),
		content: string(),
		priority: zPlanEntryPriority,
		status: zPlanEntryStatus
	});
	zPlan = looseObject({
		_meta: record(string(), unknown()).nullish(),
		entries: array(zPlanEntry)
	});
	zPosition = looseObject({
		character: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }),
		line: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" })
	});
	zNesJumpSuggestion = looseObject({
		id: string(),
		position: zPosition,
		uri: string()
	});
	zNesRenameSuggestion = looseObject({
		id: string(),
		newName: string(),
		position: zPosition,
		uri: string()
	});
	zNesUserAction = looseObject({
		action: string(),
		position: zPosition,
		timestampMs: number(),
		uri: string()
	});
	zPositionEncodingKind = union([
		literal("utf-16"),
		literal("utf-32"),
		literal("utf-8")
	]);
	zClientCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		auth: zAuthCapabilities.optional().default({ terminal: false }),
		elicitation: zElicitationCapabilities.nullish(),
		fs: zFileSystemCapabilities.optional().default({
			readTextFile: false,
			writeTextFile: false
		}),
		nes: zClientNesCapabilities.nullish(),
		positionEncodings: array(zPositionEncodingKind).optional(),
		terminal: boolean().optional().default(false)
	});
	zPromptCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		audio: boolean().optional().default(false),
		embeddedContext: boolean().optional().default(false),
		image: boolean().optional().default(false)
	});
	zProtocolVersion = number().int().gte(0).lte(65535);
	zInitializeRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		clientCapabilities: zClientCapabilities.optional().default({
			auth: { terminal: false },
			fs: {
				readTextFile: false,
				writeTextFile: false
			},
			terminal: false
		}),
		clientInfo: zImplementation.nullish(),
		protocolVersion: zProtocolVersion
	});
	zRange = looseObject({
		end: zPosition,
		start: zPosition
	});
	zNesDiagnostic = looseObject({
		message: string(),
		range: zRange,
		severity: zNesDiagnosticSeverity,
		uri: string()
	});
	zNesOpenFile = looseObject({
		languageId: string(),
		lastFocusedMs: number().nullish(),
		uri: string(),
		visibleRange: zRange.nullish()
	});
	zNesSuggestContext = looseObject({
		_meta: record(string(), unknown()).nullish(),
		diagnostics: array(zNesDiagnostic).nullish(),
		editHistory: array(zNesEditHistoryEntry).nullish(),
		openFiles: array(zNesOpenFile).nullish(),
		recentFiles: array(zNesRecentFile).nullish(),
		relatedSnippets: array(zNesRelatedSnippet).nullish(),
		userActions: array(zNesUserAction).nullish()
	});
	zNesTextEdit = looseObject({
		newText: string(),
		range: zRange
	});
	zNesEditSuggestion = looseObject({
		cursorPosition: zPosition.nullish(),
		edits: array(zNesTextEdit),
		id: string(),
		uri: string()
	});
	zNesSuggestion = union([
		zNesEditSuggestion.and(looseObject({ kind: literal("edit") })),
		zNesJumpSuggestion.and(looseObject({ kind: literal("jump") })),
		zNesRenameSuggestion.and(looseObject({ kind: literal("rename") })),
		zNesSearchAndReplaceSuggestion.and(looseObject({ kind: literal("searchAndReplace") }))
	]);
	zReadTextFileResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		content: string()
	});
	zReleaseTerminalResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zRequestId = union([number(), string()]).nullable();
	looseObject({
		_meta: record(string(), unknown()).nullish(),
		requestId: zRequestId
	});
	zRole = _enum(["assistant", "user"]);
	zAnnotations = looseObject({
		_meta: record(string(), unknown()).nullish(),
		audience: array(zRole).nullish(),
		lastModified: string().nullish(),
		priority: number().nullish()
	});
	zAudioContent = looseObject({
		_meta: record(string(), unknown()).nullish(),
		annotations: zAnnotations.nullish(),
		data: string(),
		mimeType: string()
	});
	zImageContent = looseObject({
		_meta: record(string(), unknown()).nullish(),
		annotations: zAnnotations.nullish(),
		data: string(),
		mimeType: string(),
		uri: string().nullish()
	});
	zResourceLink = looseObject({
		_meta: record(string(), unknown()).nullish(),
		annotations: zAnnotations.nullish(),
		description: string().nullish(),
		mimeType: string().nullish(),
		name: string(),
		size: number().nullish(),
		title: string().nullish(),
		uri: string()
	});
	zSelectedPermissionOutcome = looseObject({
		_meta: record(string(), unknown()).nullish(),
		optionId: zPermissionOptionId
	});
	zRequestPermissionOutcome = union([looseObject({ outcome: literal("cancelled") }), zSelectedPermissionOutcome.and(looseObject({ outcome: literal("selected") }))]);
	zRequestPermissionResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		outcome: zRequestPermissionOutcome
	});
	zSessionAdditionalDirectoriesCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zSessionCloseCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zSessionConfigBoolean = looseObject({ currentValue: boolean() });
	zSessionConfigGroupId = string();
	zSessionConfigId = string();
	zSessionConfigOptionCategory = union([
		literal("mode"),
		literal("model"),
		literal("thought_level"),
		string()
	]);
	zSessionConfigValueId = string();
	zSessionConfigSelectOption = looseObject({
		_meta: record(string(), unknown()).nullish(),
		description: string().nullish(),
		name: string(),
		value: zSessionConfigValueId
	});
	zSessionConfigSelectGroup = looseObject({
		_meta: record(string(), unknown()).nullish(),
		group: zSessionConfigGroupId,
		name: string(),
		options: array(zSessionConfigSelectOption)
	});
	zSessionConfigSelectOptions = union([array(zSessionConfigSelectOption), array(zSessionConfigSelectGroup)]);
	zSessionConfigSelect = looseObject({
		currentValue: zSessionConfigValueId,
		options: zSessionConfigSelectOptions
	});
	zSessionConfigOption = intersection(union([zSessionConfigSelect.and(looseObject({ type: literal("select") })), zSessionConfigBoolean.and(looseObject({ type: literal("boolean") }))]), looseObject({
		_meta: record(string(), unknown()).nullish(),
		category: zSessionConfigOptionCategory.nullish(),
		description: string().nullish(),
		id: zSessionConfigId,
		name: string()
	}));
	zConfigOptionUpdate = looseObject({
		_meta: record(string(), unknown()).nullish(),
		configOptions: array(zSessionConfigOption)
	});
	zSessionForkCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zSessionId = string();
	zAcceptNesNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		id: string(),
		sessionId: zSessionId
	});
	zCancelNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId
	});
	zCloseNesRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId
	});
	zCloseSessionRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId
	});
	zCreateTerminalRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		args: array(string()).optional(),
		command: string(),
		cwd: string().nullish(),
		env: array(zEnvVariable).optional(),
		outputByteLimit: number().nullish(),
		sessionId: zSessionId
	});
	zDidCloseDocumentNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId,
		uri: string()
	});
	zDidFocusDocumentNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		position: zPosition,
		sessionId: zSessionId,
		uri: string(),
		version: number(),
		visibleRange: zRange
	});
	zDidOpenDocumentNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		languageId: string(),
		sessionId: zSessionId,
		text: string(),
		uri: string(),
		version: number()
	});
	zDidSaveDocumentNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId,
		uri: string()
	});
	zForkSessionRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		additionalDirectories: array(string()).optional(),
		cwd: string(),
		mcpServers: array(zMcpServer).optional(),
		sessionId: zSessionId
	});
	zKillTerminalRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId,
		terminalId: string()
	});
	zLoadSessionRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		additionalDirectories: array(string()).optional(),
		cwd: string(),
		mcpServers: array(zMcpServer),
		sessionId: zSessionId
	});
	zReadTextFileRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		limit: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish(),
		line: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish(),
		path: string(),
		sessionId: zSessionId
	});
	zRejectNesNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		id: string(),
		reason: zNesRejectReason.nullish(),
		sessionId: zSessionId
	});
	zReleaseTerminalRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId,
		terminalId: string()
	});
	zResumeSessionRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		additionalDirectories: array(string()).optional(),
		cwd: string(),
		mcpServers: array(zMcpServer).optional(),
		sessionId: zSessionId
	});
	zSessionInfo = looseObject({
		_meta: record(string(), unknown()).nullish(),
		additionalDirectories: array(string()).optional(),
		cwd: string(),
		sessionId: zSessionId,
		title: string().nullish(),
		updatedAt: string().nullish()
	});
	zListSessionsResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		nextCursor: string().nullish(),
		sessions: array(zSessionInfo)
	});
	zSessionInfoUpdate = looseObject({
		_meta: record(string(), unknown()).nullish(),
		title: string().nullish(),
		updatedAt: string().nullish()
	});
	zSessionListCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zSessionModeId = string();
	zCurrentModeUpdate = looseObject({
		_meta: record(string(), unknown()).nullish(),
		currentModeId: zSessionModeId
	});
	zSessionMode = looseObject({
		_meta: record(string(), unknown()).nullish(),
		description: string().nullish(),
		id: zSessionModeId,
		name: string()
	});
	zSessionModeState = looseObject({
		_meta: record(string(), unknown()).nullish(),
		availableModes: array(zSessionMode),
		currentModeId: zSessionModeId
	});
	zSessionModelState = looseObject({
		_meta: record(string(), unknown()).nullish(),
		availableModels: array(zModelInfo),
		currentModelId: zModelId
	});
	zForkSessionResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		configOptions: array(zSessionConfigOption).nullish(),
		models: zSessionModelState.nullish(),
		modes: zSessionModeState.nullish(),
		sessionId: zSessionId
	});
	zLoadSessionResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		configOptions: array(zSessionConfigOption).nullish(),
		models: zSessionModelState.nullish(),
		modes: zSessionModeState.nullish()
	});
	zNewSessionResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		configOptions: array(zSessionConfigOption).nullish(),
		models: zSessionModelState.nullish(),
		modes: zSessionModeState.nullish(),
		sessionId: zSessionId
	});
	zResumeSessionResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		configOptions: array(zSessionConfigOption).nullish(),
		models: zSessionModelState.nullish(),
		modes: zSessionModeState.nullish()
	});
	zSessionResumeCapabilities = looseObject({ _meta: record(string(), unknown()).nullish() });
	zSessionCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		additionalDirectories: zSessionAdditionalDirectoriesCapabilities.nullish(),
		close: zSessionCloseCapabilities.nullish(),
		fork: zSessionForkCapabilities.nullish(),
		list: zSessionListCapabilities.nullish(),
		resume: zSessionResumeCapabilities.nullish()
	});
	zSetSessionConfigOptionRequest = intersection(union([looseObject({
		type: literal("boolean"),
		value: boolean()
	}), looseObject({ value: zSessionConfigValueId })]), looseObject({
		_meta: record(string(), unknown()).nullish(),
		configId: zSessionConfigId,
		sessionId: zSessionId
	}));
	zSetSessionConfigOptionResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		configOptions: array(zSessionConfigOption)
	});
	zSetSessionModeRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		modeId: zSessionModeId,
		sessionId: zSessionId
	});
	zSetSessionModeResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zSetSessionModelRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		modelId: zModelId,
		sessionId: zSessionId
	});
	zSetSessionModelResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	zStartNesResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId
	});
	zStopReason = union([
		literal("end_turn"),
		literal("max_tokens"),
		literal("max_turn_requests"),
		literal("refusal"),
		literal("cancelled")
	]);
	zStringFormat = union([
		literal("email"),
		literal("uri"),
		literal("date"),
		literal("date-time")
	]);
	zStringPropertySchema = looseObject({
		default: string().nullish(),
		description: string().nullish(),
		enum: array(string()).nullish(),
		format: zStringFormat.nullish(),
		maxLength: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish(),
		minLength: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish(),
		oneOf: array(zEnumOption).nullish(),
		pattern: string().nullish(),
		title: string().nullish()
	});
	zSuggestNesRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		context: zNesSuggestContext.nullish(),
		position: zPosition,
		selection: zRange.nullish(),
		sessionId: zSessionId,
		triggerKind: zNesTriggerKind,
		uri: string(),
		version: number()
	});
	zSuggestNesResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		suggestions: array(zNesSuggestion)
	});
	zTerminal = looseObject({
		_meta: record(string(), unknown()).nullish(),
		terminalId: string()
	});
	zTerminalExitStatus = looseObject({
		_meta: record(string(), unknown()).nullish(),
		exitCode: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish(),
		signal: string().nullish()
	});
	zTerminalOutputRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId,
		terminalId: string()
	});
	zTerminalOutputResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		exitStatus: zTerminalExitStatus.nullish(),
		output: string(),
		truncated: boolean()
	});
	zTextContent = looseObject({
		_meta: record(string(), unknown()).nullish(),
		annotations: zAnnotations.nullish(),
		text: string()
	});
	zTextDocumentContentChangeEvent = looseObject({
		range: zRange.nullish(),
		text: string()
	});
	zDidChangeDocumentNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		contentChanges: array(zTextDocumentContentChangeEvent),
		sessionId: zSessionId,
		uri: string(),
		version: number()
	});
	looseObject({
		method: string(),
		params: union([
			zCancelNotification,
			zDidOpenDocumentNotification,
			zDidChangeDocumentNotification,
			zDidCloseDocumentNotification,
			zDidSaveDocumentNotification,
			zDidFocusDocumentNotification,
			zAcceptNesNotification,
			zRejectNesNotification,
			zExtNotification
		]).nullish()
	});
	zTextDocumentSyncKind = union([literal("full"), literal("incremental")]);
	zNesDocumentDidChangeCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		syncKind: zTextDocumentSyncKind
	});
	zNesDocumentEventCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		didChange: zNesDocumentDidChangeCapabilities.nullish(),
		didClose: zNesDocumentDidCloseCapabilities.nullish(),
		didFocus: zNesDocumentDidFocusCapabilities.nullish(),
		didOpen: zNesDocumentDidOpenCapabilities.nullish(),
		didSave: zNesDocumentDidSaveCapabilities.nullish()
	});
	zNesEventCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		document: zNesDocumentEventCapabilities.nullish()
	});
	zNesCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		context: zNesContextCapabilities.nullish(),
		events: zNesEventCapabilities.nullish()
	});
	zAgentCapabilities = looseObject({
		_meta: record(string(), unknown()).nullish(),
		auth: zAgentAuthCapabilities.optional().default({}),
		loadSession: boolean().optional().default(false),
		mcpCapabilities: zMcpCapabilities.optional().default({
			http: false,
			sse: false
		}),
		nes: zNesCapabilities.nullish(),
		positionEncoding: zPositionEncodingKind.nullish(),
		promptCapabilities: zPromptCapabilities.optional().default({
			audio: false,
			embeddedContext: false,
			image: false
		}),
		sessionCapabilities: zSessionCapabilities.optional().default({})
	});
	zInitializeResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		agentCapabilities: zAgentCapabilities.optional().default({
			auth: {},
			loadSession: false,
			mcpCapabilities: {
				http: false,
				sse: false
			},
			promptCapabilities: {
				audio: false,
				embeddedContext: false,
				image: false
			},
			sessionCapabilities: {}
		}),
		agentInfo: zImplementation.nullish(),
		authMethods: array(zAuthMethod).optional().default([]),
		protocolVersion: zProtocolVersion
	});
	zTextResourceContents = looseObject({
		_meta: record(string(), unknown()).nullish(),
		mimeType: string().nullish(),
		text: string(),
		uri: string()
	});
	zEmbeddedResourceResource = union([zTextResourceContents, zBlobResourceContents]);
	zEmbeddedResource = looseObject({
		_meta: record(string(), unknown()).nullish(),
		annotations: zAnnotations.nullish(),
		resource: zEmbeddedResourceResource
	});
	zContentBlock = union([
		zTextContent.and(looseObject({ type: literal("text") })),
		zImageContent.and(looseObject({ type: literal("image") })),
		zAudioContent.and(looseObject({ type: literal("audio") })),
		zResourceLink.and(looseObject({ type: literal("resource_link") })),
		zEmbeddedResource.and(looseObject({ type: literal("resource") }))
	]);
	zContent = looseObject({
		_meta: record(string(), unknown()).nullish(),
		content: zContentBlock
	});
	zContentChunk = looseObject({
		_meta: record(string(), unknown()).nullish(),
		content: zContentBlock,
		messageId: string().nullish()
	});
	zPromptRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		messageId: string().nullish(),
		prompt: array(zContentBlock),
		sessionId: zSessionId
	});
	zTitledMultiSelectItems = looseObject({ anyOf: array(zEnumOption) });
	zToolCallContent = union([
		zContent.and(looseObject({ type: literal("content") })),
		zDiff.and(looseObject({ type: literal("diff") })),
		zTerminal.and(looseObject({ type: literal("terminal") }))
	]);
	zToolCallId = string();
	zToolCallLocation = looseObject({
		_meta: record(string(), unknown()).nullish(),
		line: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish(),
		path: string()
	});
	zToolCallStatus = union([
		literal("pending"),
		literal("in_progress"),
		literal("completed"),
		literal("failed")
	]);
	zToolKind = union([
		literal("read"),
		literal("edit"),
		literal("delete"),
		literal("move"),
		literal("search"),
		literal("execute"),
		literal("think"),
		literal("fetch"),
		literal("switch_mode"),
		literal("other")
	]);
	zToolCall = looseObject({
		_meta: record(string(), unknown()).nullish(),
		content: array(zToolCallContent).optional(),
		kind: zToolKind.optional(),
		locations: array(zToolCallLocation).optional(),
		rawInput: unknown().optional(),
		rawOutput: unknown().optional(),
		status: zToolCallStatus.optional(),
		title: string(),
		toolCallId: zToolCallId
	});
	zToolCallUpdate = looseObject({
		_meta: record(string(), unknown()).nullish(),
		content: array(zToolCallContent).nullish(),
		kind: zToolKind.nullish(),
		locations: array(zToolCallLocation).nullish(),
		rawInput: unknown().optional(),
		rawOutput: unknown().optional(),
		status: zToolCallStatus.nullish(),
		title: string().nullish(),
		toolCallId: zToolCallId
	});
	zRequestPermissionRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		options: array(zPermissionOption),
		sessionId: zSessionId,
		toolCall: zToolCallUpdate
	});
	zUnstructuredCommandInput = looseObject({
		_meta: record(string(), unknown()).nullish(),
		hint: string()
	});
	zAvailableCommandInput = zUnstructuredCommandInput;
	zAvailableCommand = looseObject({
		_meta: record(string(), unknown()).nullish(),
		description: string(),
		input: zAvailableCommandInput.nullish(),
		name: string()
	});
	zAvailableCommandsUpdate = looseObject({
		_meta: record(string(), unknown()).nullish(),
		availableCommands: array(zAvailableCommand)
	});
	zUntitledMultiSelectItems = looseObject({
		enum: array(string()),
		type: zElicitationStringType
	});
	zMultiSelectItems = union([zUntitledMultiSelectItems, zTitledMultiSelectItems]);
	zMultiSelectPropertySchema = looseObject({
		default: array(string()).nullish(),
		description: string().nullish(),
		items: zMultiSelectItems,
		maxItems: number().nullish(),
		minItems: number().nullish(),
		title: string().nullish()
	});
	zElicitationPropertySchema = union([
		zStringPropertySchema.and(looseObject({ type: literal("string") })),
		zNumberPropertySchema.and(looseObject({ type: literal("number") })),
		zIntegerPropertySchema.and(looseObject({ type: literal("integer") })),
		zBooleanPropertySchema.and(looseObject({ type: literal("boolean") })),
		zMultiSelectPropertySchema.and(looseObject({ type: literal("array") }))
	]);
	zElicitationSchema = looseObject({
		description: string().nullish(),
		properties: record(string(), zElicitationPropertySchema).optional().default({}),
		required: array(string()).nullish(),
		title: string().nullish(),
		type: zElicitationSchemaType.optional().default("object")
	});
	zElicitationFormMode = looseObject({ requestedSchema: zElicitationSchema });
	zElicitationRequest = intersection(union([zElicitationFormMode.and(looseObject({ mode: literal("form") })), zElicitationUrlMode.and(looseObject({ mode: literal("url") }))]), looseObject({
		_meta: record(string(), unknown()).nullish(),
		message: string(),
		sessionId: zSessionId
	}));
	zUsage = looseObject({
		cachedReadTokens: number().nullish(),
		cachedWriteTokens: number().nullish(),
		inputTokens: number(),
		outputTokens: number(),
		thoughtTokens: number().nullish(),
		totalTokens: number()
	});
	zPromptResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		stopReason: zStopReason,
		usage: zUsage.nullish(),
		userMessageId: string().nullish()
	});
	union([looseObject({
		id: zRequestId,
		result: union([
			zInitializeResponse,
			zAuthenticateResponse,
			zLogoutResponse,
			zNewSessionResponse,
			zLoadSessionResponse,
			zListSessionsResponse,
			zForkSessionResponse,
			zResumeSessionResponse,
			zCloseSessionResponse,
			zSetSessionModeResponse,
			zSetSessionConfigOptionResponse,
			zPromptResponse,
			zSetSessionModelResponse,
			zStartNesResponse,
			zSuggestNesResponse,
			zCloseNesResponse,
			zExtResponse
		])
	}), looseObject({
		error: zError,
		id: zRequestId
	})]);
	zUsageUpdate = looseObject({
		_meta: record(string(), unknown()).nullish(),
		cost: zCost.nullish(),
		size: number(),
		used: number()
	});
	zSessionUpdate = union([
		zContentChunk.and(looseObject({ sessionUpdate: literal("user_message_chunk") })),
		zContentChunk.and(looseObject({ sessionUpdate: literal("agent_message_chunk") })),
		zContentChunk.and(looseObject({ sessionUpdate: literal("agent_thought_chunk") })),
		zToolCall.and(looseObject({ sessionUpdate: literal("tool_call") })),
		zToolCallUpdate.and(looseObject({ sessionUpdate: literal("tool_call_update") })),
		zPlan.and(looseObject({ sessionUpdate: literal("plan") })),
		zAvailableCommandsUpdate.and(looseObject({ sessionUpdate: literal("available_commands_update") })),
		zCurrentModeUpdate.and(looseObject({ sessionUpdate: literal("current_mode_update") })),
		zConfigOptionUpdate.and(looseObject({ sessionUpdate: literal("config_option_update") })),
		zSessionInfoUpdate.and(looseObject({ sessionUpdate: literal("session_info_update") })),
		zUsageUpdate.and(looseObject({ sessionUpdate: literal("usage_update") }))
	]);
	zSessionNotification = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId,
		update: zSessionUpdate
	});
	looseObject({
		method: string(),
		params: union([
			zSessionNotification,
			zElicitationCompleteNotification,
			zExtNotification
		]).nullish()
	});
	zWaitForTerminalExitRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		sessionId: zSessionId,
		terminalId: string()
	});
	zWaitForTerminalExitResponse = looseObject({
		_meta: record(string(), unknown()).nullish(),
		exitCode: number().int().gte(0).max(4294967295, { message: "Invalid value: Expected uint32 to be <= 4294967295" }).nullish(),
		signal: string().nullish()
	});
	zWorkspaceFolder = looseObject({
		name: string(),
		uri: string()
	});
	zStartNesRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		repository: zNesRepository.nullish(),
		workspaceFolders: array(zWorkspaceFolder).nullish(),
		workspaceUri: string().nullish()
	});
	looseObject({
		id: zRequestId,
		method: string(),
		params: union([
			zInitializeRequest,
			zAuthenticateRequest,
			zLogoutRequest,
			zNewSessionRequest,
			zLoadSessionRequest,
			zListSessionsRequest,
			zForkSessionRequest,
			zResumeSessionRequest,
			zCloseSessionRequest,
			zSetSessionModeRequest,
			zSetSessionConfigOptionRequest,
			zPromptRequest,
			zSetSessionModelRequest,
			zStartNesRequest,
			zSuggestNesRequest,
			zCloseNesRequest,
			zExtRequest
		]).nullish()
	});
	zWriteTextFileRequest = looseObject({
		_meta: record(string(), unknown()).nullish(),
		content: string(),
		path: string(),
		sessionId: zSessionId
	});
	looseObject({
		id: zRequestId,
		method: string(),
		params: union([
			zWriteTextFileRequest,
			zReadTextFileRequest,
			zRequestPermissionRequest,
			zCreateTerminalRequest,
			zTerminalOutputRequest,
			zReleaseTerminalRequest,
			zWaitForTerminalExitRequest,
			zKillTerminalRequest,
			zElicitationRequest,
			zExtRequest
		]).nullish()
	});
	zWriteTextFileResponse = looseObject({ _meta: record(string(), unknown()).nullish() });
	union([looseObject({
		id: zRequestId,
		result: union([
			zWriteTextFileResponse,
			zReadTextFileResponse,
			zRequestPermissionResponse,
			zCreateTerminalResponse,
			zTerminalOutputResponse,
			zReleaseTerminalResponse,
			zWaitForTerminalExitResponse,
			zKillTerminalResponse,
			zElicitationResponse,
			zExtResponse
		])
	}), looseObject({
		error: zError,
		id: zRequestId
	})]);
}));
//#endregion
//#region ../../node_modules/@agentclientprotocol/sdk/dist/stream.js
/**
* Creates an ACP Stream from a pair of newline-delimited JSON streams.
*
* This is the typical way to handle ACP connections over stdio, converting
* between AnyMessage objects and newline-delimited JSON.
*
* @param output - The writable stream to send encoded messages to
* @param input - The readable stream to receive encoded messages from
* @returns A Stream for bidirectional ACP communication
*/
function ndJsonStream(output, input) {
	const textEncoder = new TextEncoder();
	const textDecoder = new TextDecoder();
	return {
		readable: new ReadableStream({ async start(controller) {
			let content = "";
			const reader = input.getReader();
			try {
				while (true) {
					const { value, done } = await reader.read();
					if (done) break;
					if (!value) continue;
					content += textDecoder.decode(value, { stream: true });
					const lines = content.split("\n");
					content = lines.pop() || "";
					for (const line of lines) {
						const trimmedLine = line.trim();
						if (trimmedLine) try {
							const message = JSON.parse(trimmedLine);
							controller.enqueue(message);
						} catch (err) {
							console.error("Failed to parse JSON message:", trimmedLine, err);
						}
					}
				}
			} catch (err) {
				controller.error(err);
				return;
			} finally {
				reader.releaseLock();
			}
			controller.close();
		} }),
		writable: new WritableStream({ async write(message) {
			const content = JSON.stringify(message) + "\n";
			const writer = output.getWriter();
			try {
				await writer.write(textEncoder.encode(content));
			} finally {
				writer.releaseLock();
			}
		} })
	};
}
var init_stream = require_chunk.__esmMin((() => {}));
//#endregion
//#region ../../node_modules/@agentclientprotocol/sdk/dist/acp.js
var AgentSideConnection, TerminalHandle, ClientSideConnection, Connection, RequestError;
//#endregion
require_chunk.__esmMin((() => {
	init_v4();
	init_schema();
	init_zod_gen();
	init_schema();
	init_stream();
	AgentSideConnection = class {
		#connection;
		/**
		* Creates a new agent-side connection to a client.
		*
		* This establishes the communication channel from the agent's perspective
		* following the ACP specification.
		*
		* @param toAgent - A function that creates an Agent handler to process incoming client requests
		* @param stream - The bidirectional message stream for communication. Typically created using
		*                 {@link ndJsonStream} for stdio-based connections.
		*
		* See protocol docs: [Communication Model](https://agentclientprotocol.com/protocol/overview#communication-model)
		*/
		constructor(toAgent, stream) {
			const agent = toAgent(this);
			const requestHandler = async (method, params) => {
				switch (method) {
					case AGENT_METHODS.initialize: {
						const validatedParams = zInitializeRequest.parse(params);
						return agent.initialize(validatedParams);
					}
					case AGENT_METHODS.session_new: {
						const validatedParams = zNewSessionRequest.parse(params);
						return agent.newSession(validatedParams);
					}
					case AGENT_METHODS.session_load: {
						if (!agent.loadSession) throw RequestError.methodNotFound(method);
						const validatedParams = zLoadSessionRequest.parse(params);
						return agent.loadSession(validatedParams);
					}
					case AGENT_METHODS.session_list: {
						if (!agent.listSessions) throw RequestError.methodNotFound(method);
						const validatedParams = zListSessionsRequest.parse(params);
						return agent.listSessions(validatedParams);
					}
					case AGENT_METHODS.session_fork: {
						if (!agent.unstable_forkSession) throw RequestError.methodNotFound(method);
						const validatedParams = zForkSessionRequest.parse(params);
						return agent.unstable_forkSession(validatedParams);
					}
					case AGENT_METHODS.session_resume: {
						if (!agent.unstable_resumeSession) throw RequestError.methodNotFound(method);
						const validatedParams = zResumeSessionRequest.parse(params);
						return agent.unstable_resumeSession(validatedParams);
					}
					case AGENT_METHODS.session_close: {
						if (!agent.unstable_closeSession) throw RequestError.methodNotFound(method);
						const validatedParams = zCloseSessionRequest.parse(params);
						return agent.unstable_closeSession(validatedParams);
					}
					case AGENT_METHODS.session_set_mode: {
						if (!agent.setSessionMode) throw RequestError.methodNotFound(method);
						const validatedParams = zSetSessionModeRequest.parse(params);
						return await agent.setSessionMode(validatedParams) ?? {};
					}
					case AGENT_METHODS.authenticate: {
						const validatedParams = zAuthenticateRequest.parse(params);
						return await agent.authenticate(validatedParams) ?? {};
					}
					case AGENT_METHODS.logout: {
						if (!agent.unstable_logout) throw RequestError.methodNotFound(method);
						const validatedParams = zLogoutRequest.parse(params);
						return await agent.unstable_logout(validatedParams) ?? {};
					}
					case AGENT_METHODS.session_prompt: {
						const validatedParams = zPromptRequest.parse(params);
						return agent.prompt(validatedParams);
					}
					case AGENT_METHODS.session_set_model: {
						if (!agent.unstable_setSessionModel) throw RequestError.methodNotFound(method);
						const validatedParams = zSetSessionModelRequest.parse(params);
						return await agent.unstable_setSessionModel(validatedParams) ?? {};
					}
					case AGENT_METHODS.session_set_config_option: {
						if (!agent.setSessionConfigOption) throw RequestError.methodNotFound(method);
						const validatedParams = zSetSessionConfigOptionRequest.parse(params);
						return agent.setSessionConfigOption(validatedParams);
					}
					case AGENT_METHODS.nes_start: {
						if (!agent.unstable_startNes) throw RequestError.methodNotFound(method);
						const validatedParams = zStartNesRequest.parse(params);
						return agent.unstable_startNes(validatedParams);
					}
					case AGENT_METHODS.nes_suggest: {
						if (!agent.unstable_suggestNes) throw RequestError.methodNotFound(method);
						const validatedParams = zSuggestNesRequest.parse(params);
						return agent.unstable_suggestNes(validatedParams);
					}
					case AGENT_METHODS.nes_close: {
						if (!agent.unstable_closeNes) throw RequestError.methodNotFound(method);
						const validatedParams = zCloseNesRequest.parse(params);
						return await agent.unstable_closeNes(validatedParams) ?? {};
					}
					default:
						if (agent.extMethod) return agent.extMethod(method, params);
						throw RequestError.methodNotFound(method);
				}
			};
			const notificationHandler = async (method, params) => {
				switch (method) {
					case AGENT_METHODS.session_cancel: {
						const validatedParams = zCancelNotification.parse(params);
						return agent.cancel(validatedParams);
					}
					case AGENT_METHODS.document_did_open: {
						if (!agent.unstable_didOpenDocument) return;
						const validatedParams = zDidOpenDocumentNotification.parse(params);
						return agent.unstable_didOpenDocument(validatedParams);
					}
					case AGENT_METHODS.document_did_change: {
						if (!agent.unstable_didChangeDocument) return;
						const validatedParams = zDidChangeDocumentNotification.parse(params);
						return agent.unstable_didChangeDocument(validatedParams);
					}
					case AGENT_METHODS.document_did_close: {
						if (!agent.unstable_didCloseDocument) return;
						const validatedParams = zDidCloseDocumentNotification.parse(params);
						return agent.unstable_didCloseDocument(validatedParams);
					}
					case AGENT_METHODS.document_did_save: {
						if (!agent.unstable_didSaveDocument) return;
						const validatedParams = zDidSaveDocumentNotification.parse(params);
						return agent.unstable_didSaveDocument(validatedParams);
					}
					case AGENT_METHODS.document_did_focus: {
						if (!agent.unstable_didFocusDocument) return;
						const validatedParams = zDidFocusDocumentNotification.parse(params);
						return agent.unstable_didFocusDocument(validatedParams);
					}
					case AGENT_METHODS.nes_accept: {
						if (!agent.unstable_acceptNes) return;
						const validatedParams = zAcceptNesNotification.parse(params);
						return agent.unstable_acceptNes(validatedParams);
					}
					case AGENT_METHODS.nes_reject: {
						if (!agent.unstable_rejectNes) return;
						const validatedParams = zRejectNesNotification.parse(params);
						return agent.unstable_rejectNes(validatedParams);
					}
					default:
						if (agent.extNotification) return agent.extNotification(method, params);
						throw RequestError.methodNotFound(method);
				}
			};
			this.#connection = new Connection(requestHandler, notificationHandler, stream);
		}
		/**
		* Handles session update notifications from the agent.
		*
		* This is a notification endpoint (no response expected) that sends
		* real-time updates about session progress, including message chunks,
		* tool calls, and execution plans.
		*
		* Note: Clients SHOULD continue accepting tool call updates even after
		* sending a `session/cancel` notification, as the agent may send final
		* updates before responding with the cancelled stop reason.
		*
		* See protocol docs: [Agent Reports Output](https://agentclientprotocol.com/protocol/prompt-turn#3-agent-reports-output)
		*/
		async sessionUpdate(params) {
			return await this.#connection.sendNotification(CLIENT_METHODS.session_update, params);
		}
		/**
		* Requests permission from the user for a tool call operation.
		*
		* Called by the agent when it needs user authorization before executing
		* a potentially sensitive operation. The client should present the options
		* to the user and return their decision.
		*
		* If the client cancels the prompt turn via `session/cancel`, it MUST
		* respond to this request with `RequestPermissionOutcome::Cancelled`.
		*
		* See protocol docs: [Requesting Permission](https://agentclientprotocol.com/protocol/tool-calls#requesting-permission)
		*/
		async requestPermission(params) {
			return await this.#connection.sendRequest(CLIENT_METHODS.session_request_permission, params);
		}
		/**
		* Reads content from a text file in the client's file system.
		*
		* Only available if the client advertises the `fs.readTextFile` capability.
		* Allows the agent to access file contents within the client's environment.
		*
		* See protocol docs: [Client](https://agentclientprotocol.com/protocol/overview#client)
		*/
		async readTextFile(params) {
			return await this.#connection.sendRequest(CLIENT_METHODS.fs_read_text_file, params);
		}
		/**
		* Writes content to a text file in the client's file system.
		*
		* Only available if the client advertises the `fs.writeTextFile` capability.
		* Allows the agent to create or modify files within the client's environment.
		*
		* See protocol docs: [Client](https://agentclientprotocol.com/protocol/overview#client)
		*/
		async writeTextFile(params) {
			return await this.#connection.sendRequest(CLIENT_METHODS.fs_write_text_file, params) ?? {};
		}
		/**
		* Executes a command in a new terminal.
		*
		* Returns a `TerminalHandle` that can be used to get output, wait for exit,
		* kill the command, or release the terminal.
		*
		* The terminal can also be embedded in tool calls by using its ID in
		* `ToolCallContent` with type "terminal".
		*
		* @param params - The terminal creation parameters
		* @returns A handle to control and monitor the terminal
		*/
		async createTerminal(params) {
			return new TerminalHandle((await this.#connection.sendRequest(CLIENT_METHODS.terminal_create, params)).terminalId, params.sessionId, this.#connection);
		}
		/**
		* Extension method
		*
		* Allows the Agent to send an arbitrary request that is not part of the ACP spec.
		*/
		async extMethod(method, params) {
			return await this.#connection.sendRequest(method, params);
		}
		/**
		* Extension notification
		*
		* Allows the Agent to send an arbitrary notification that is not part of the ACP spec.
		*/
		async extNotification(method, params) {
			return await this.#connection.sendNotification(method, params);
		}
		/**
		* AbortSignal that aborts when the connection closes.
		*
		* This signal can be used to:
		* - Listen for connection closure: `connection.signal.addEventListener('abort', () => {...})`
		* - Check connection status synchronously: `if (connection.signal.aborted) {...}`
		* - Pass to other APIs (fetch, setTimeout) for automatic cancellation
		*
		* The connection closes when the underlying stream ends, either normally or due to an error.
		*
		* @example
		* ```typescript
		* const connection = new AgentSideConnection(agent, stream);
		*
		* // Listen for closure
		* connection.signal.addEventListener('abort', () => {
		*   console.log('Connection closed - performing cleanup');
		* });
		*
		* // Check status
		* if (connection.signal.aborted) {
		*   console.log('Connection is already closed');
		* }
		*
		* // Pass to other APIs
		* fetch(url, { signal: connection.signal });
		* ```
		*/
		get signal() {
			return this.#connection.signal;
		}
		/**
		* Promise that resolves when the connection closes.
		*
		* The connection closes when the underlying stream ends, either normally or due to an error.
		* Once closed, the connection cannot send or receive any more messages.
		*
		* This is useful for async/await style cleanup:
		*
		* @example
		* ```typescript
		* const connection = new AgentSideConnection(agent, stream);
		* await connection.closed;
		* console.log('Connection closed - performing cleanup');
		* ```
		*/
		get closed() {
			return this.#connection.closed;
		}
	};
	TerminalHandle = class {
		id;
		#sessionId;
		#connection;
		constructor(id, sessionId, conn) {
			this.id = id;
			this.#sessionId = sessionId;
			this.#connection = conn;
		}
		/**
		* Gets the current terminal output without waiting for the command to exit.
		*/
		async currentOutput() {
			return await this.#connection.sendRequest(CLIENT_METHODS.terminal_output, {
				sessionId: this.#sessionId,
				terminalId: this.id
			});
		}
		/**
		* Waits for the terminal command to complete and returns its exit status.
		*/
		async waitForExit() {
			return await this.#connection.sendRequest(CLIENT_METHODS.terminal_wait_for_exit, {
				sessionId: this.#sessionId,
				terminalId: this.id
			});
		}
		/**
		* Kills the terminal command without releasing the terminal.
		*
		* The terminal remains valid after killing, allowing you to:
		* - Get the final output with `currentOutput()`
		* - Check the exit status
		* - Release the terminal when done
		*
		* Useful for implementing timeouts or cancellation.
		*/
		async kill() {
			return await this.#connection.sendRequest(CLIENT_METHODS.terminal_kill, {
				sessionId: this.#sessionId,
				terminalId: this.id
			}) ?? {};
		}
		/**
		* Releases the terminal and frees all associated resources.
		*
		* If the command is still running, it will be killed.
		* After release, the terminal ID becomes invalid and cannot be used
		* with other terminal methods.
		*
		* Tool calls that already reference this terminal will continue to
		* display its output.
		*
		* **Important:** Always call this method when done with the terminal.
		*/
		async release() {
			return await this.#connection.sendRequest(CLIENT_METHODS.terminal_release, {
				sessionId: this.#sessionId,
				terminalId: this.id
			}) ?? {};
		}
		async [Symbol.asyncDispose]() {
			await this.release();
		}
	};
	ClientSideConnection = class {
		#connection;
		/**
		* Creates a new client-side connection to an agent.
		*
		* This establishes the communication channel between a client and agent
		* following the ACP specification.
		*
		* @param toClient - A function that creates a Client handler to process incoming agent requests
		* @param stream - The bidirectional message stream for communication. Typically created using
		*                 {@link ndJsonStream} for stdio-based connections.
		*
		* See protocol docs: [Communication Model](https://agentclientprotocol.com/protocol/overview#communication-model)
		*/
		constructor(toClient, stream) {
			const client = toClient(this);
			const requestHandler = async (method, params) => {
				switch (method) {
					case CLIENT_METHODS.fs_write_text_file: {
						const validatedParams = zWriteTextFileRequest.parse(params);
						return client.writeTextFile?.(validatedParams);
					}
					case CLIENT_METHODS.fs_read_text_file: {
						const validatedParams = zReadTextFileRequest.parse(params);
						return client.readTextFile?.(validatedParams);
					}
					case CLIENT_METHODS.session_request_permission: {
						const validatedParams = zRequestPermissionRequest.parse(params);
						return client.requestPermission(validatedParams);
					}
					case CLIENT_METHODS.terminal_create: {
						const validatedParams = zCreateTerminalRequest.parse(params);
						return client.createTerminal?.(validatedParams);
					}
					case CLIENT_METHODS.terminal_output: {
						const validatedParams = zTerminalOutputRequest.parse(params);
						return client.terminalOutput?.(validatedParams);
					}
					case CLIENT_METHODS.terminal_release: {
						const validatedParams = zReleaseTerminalRequest.parse(params);
						return await client.releaseTerminal?.(validatedParams) ?? {};
					}
					case CLIENT_METHODS.terminal_wait_for_exit: {
						const validatedParams = zWaitForTerminalExitRequest.parse(params);
						return client.waitForTerminalExit?.(validatedParams);
					}
					case CLIENT_METHODS.terminal_kill: {
						const validatedParams = zKillTerminalRequest.parse(params);
						return await client.killTerminal?.(validatedParams) ?? {};
					}
					default:
						if (client.extMethod) return client.extMethod(method, params);
						throw RequestError.methodNotFound(method);
				}
			};
			const notificationHandler = async (method, params) => {
				switch (method) {
					case CLIENT_METHODS.session_update: {
						const validatedParams = zSessionNotification.parse(params);
						return client.sessionUpdate(validatedParams);
					}
					default:
						if (client.extNotification) return client.extNotification(method, params);
						throw RequestError.methodNotFound(method);
				}
			};
			this.#connection = new Connection(requestHandler, notificationHandler, stream);
		}
		/**
		* Establishes the connection with a client and negotiates protocol capabilities.
		*
		* This method is called once at the beginning of the connection to:
		* - Negotiate the protocol version to use
		* - Exchange capability information between client and agent
		* - Determine available authentication methods
		*
		* The agent should respond with its supported protocol version and capabilities.
		*
		* See protocol docs: [Initialization](https://agentclientprotocol.com/protocol/initialization)
		*/
		async initialize(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.initialize, params);
		}
		/**
		* Creates a new conversation session with the agent.
		*
		* Sessions represent independent conversation contexts with their own history and state.
		*
		* The agent should:
		* - Create a new session context
		* - Connect to any specified MCP servers
		* - Return a unique session ID for future requests
		*
		* The request may include `additionalDirectories` to expand the session's filesystem
		* scope beyond `cwd` without changing the base for relative paths.
		*
		* May return an `auth_required` error if the agent requires authentication.
		*
		* See protocol docs: [Session Setup](https://agentclientprotocol.com/protocol/session-setup)
		*/
		async newSession(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_new, params);
		}
		/**
		* Loads an existing session to resume a previous conversation.
		*
		* This method is only available if the agent advertises the `loadSession` capability.
		*
		* The agent should:
		* - Restore the session context and conversation history
		* - Connect to the specified MCP servers
		* - Stream the entire conversation history back to the client via notifications
		*
		* The request may include `additionalDirectories` to set the complete list of
		* additional workspace roots for the loaded session.
		*
		* See protocol docs: [Loading Sessions](https://agentclientprotocol.com/protocol/session-setup#loading-sessions)
		*/
		async loadSession(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_load, params) ?? {};
		}
		/**
		* **UNSTABLE**
		*
		* This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Forks an existing session to create a new independent session.
		*
		* Creates a new session based on the context of an existing one, allowing
		* operations like generating summaries without affecting the original session's history.
		*
		* The request may include `additionalDirectories` to set the complete list of
		* additional workspace roots for the forked session.
		*
		* This method is only available if the agent advertises the `session.fork` capability.
		*
		* @experimental
		*/
		async unstable_forkSession(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_fork, params);
		}
		/**
		* Lists existing sessions from the agent.
		*
		* This method is only available if the agent advertises the `listSessions` capability.
		*
		* Returns a list of sessions with metadata like session ID, working directory,
		* title, and last update time. Supports filtering by working directory,
		* `additionalDirectories`, and cursor-based pagination.
		*/
		async listSessions(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_list, params);
		}
		/**
		* **UNSTABLE**
		*
		* This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Resumes an existing session without returning previous messages.
		*
		* This method is only available if the agent advertises the `session.resume` capability.
		*
		* The agent should resume the session context, allowing the conversation to continue
		* without replaying the message history (unlike `session/load`).
		*
		* The request may include `additionalDirectories` to set the complete list of
		* additional workspace roots for the resumed session.
		*
		* @experimental
		*/
		async unstable_resumeSession(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_resume, params);
		}
		/**
		* **UNSTABLE**
		*
		* This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Closes an active session and frees up any resources associated with it.
		*
		* This method is only available if the agent advertises the `session.close` capability.
		*
		* The agent must cancel any ongoing work (as if `session/cancel` was called)
		* and then free up any resources associated with the session.
		*
		* @experimental
		*/
		async unstable_closeSession(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_close, params);
		}
		/**
		* Sets the operational mode for a session.
		*
		* Allows switching between different agent modes (e.g., "ask", "architect", "code")
		* that affect system prompts, tool availability, and permission behaviors.
		*
		* The mode must be one of the modes advertised in `availableModes` during session
		* creation or loading. Agents may also change modes autonomously and notify the
		* client via `current_mode_update` notifications.
		*
		* This method can be called at any time during a session, whether the Agent is
		* idle or actively generating a turn.
		*
		* See protocol docs: [Session Modes](https://agentclientprotocol.com/protocol/session-modes)
		*/
		async setSessionMode(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_set_mode, params) ?? {};
		}
		/**
		* **UNSTABLE**
		*
		* This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Select a model for a given session.
		*
		* @experimental
		*/
		async unstable_setSessionModel(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_set_model, params) ?? {};
		}
		/**
		* Set a configuration option for a given session.
		*
		* The response contains the full set of configuration options and their current values,
		* as changing one option may affect the available values or state of other options.
		*/
		async setSessionConfigOption(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_set_config_option, params);
		}
		/**
		* Authenticates the client using the specified authentication method.
		*
		* Called when the agent requires authentication before allowing session creation.
		* The client provides the authentication method ID that was advertised during initialization.
		*
		* After successful authentication, the client can proceed to create sessions with
		* `newSession` without receiving an `auth_required` error.
		*
		* See protocol docs: [Initialization](https://agentclientprotocol.com/protocol/initialization)
		*/
		async authenticate(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.authenticate, params) ?? {};
		}
		/**
		* Terminates the current authenticated session.
		*
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* @experimental
		*/
		async unstable_logout(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.logout, params) ?? {};
		}
		/**
		* Processes a user prompt within a session.
		*
		* This method handles the whole lifecycle of a prompt:
		* - Receives user messages with optional context (files, images, etc.)
		* - Processes the prompt using language models
		* - Reports language model content and tool calls to the Clients
		* - Requests permission to run tools
		* - Executes any requested tool calls
		* - Returns when the turn is complete with a stop reason
		*
		* See protocol docs: [Prompt Turn](https://agentclientprotocol.com/protocol/prompt-turn)
		*/
		async prompt(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.session_prompt, params);
		}
		/**
		* Cancels ongoing operations for a session.
		*
		* This is a notification sent by the client to cancel an ongoing prompt turn.
		*
		* Upon receiving this notification, the Agent SHOULD:
		* - Stop all language model requests as soon as possible
		* - Abort all tool call invocations in progress
		* - Send any pending `session/update` notifications
		* - Respond to the original `session/prompt` request with `StopReason::Cancelled`
		*
		* See protocol docs: [Cancellation](https://agentclientprotocol.com/protocol/prompt-turn#cancellation)
		*/
		async cancel(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.session_cancel, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Starts a NES (Next Edit Suggestions) session.
		*
		* @experimental
		*/
		async unstable_startNes(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.nes_start, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Sends a NES suggestion request.
		*
		* @experimental
		*/
		async unstable_suggestNes(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.nes_suggest, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Closes a NES session.
		*
		* @experimental
		*/
		async unstable_closeNes(params) {
			return await this.#connection.sendRequest(AGENT_METHODS.nes_close, params) ?? {};
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Notifies the agent that a document was opened.
		*
		* @experimental
		*/
		async unstable_didOpenDocument(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.document_did_open, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Notifies the agent that a document was changed.
		*
		* @experimental
		*/
		async unstable_didChangeDocument(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.document_did_change, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Notifies the agent that a document was closed.
		*
		* @experimental
		*/
		async unstable_didCloseDocument(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.document_did_close, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Notifies the agent that a document was saved.
		*
		* @experimental
		*/
		async unstable_didSaveDocument(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.document_did_save, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Notifies the agent that a document received focus.
		*
		* @experimental
		*/
		async unstable_didFocusDocument(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.document_did_focus, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Notifies the agent that a NES suggestion was accepted.
		*
		* @experimental
		*/
		async unstable_acceptNes(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.nes_accept, params);
		}
		/**
		* **UNSTABLE**: This capability is not part of the spec yet, and may be removed or changed at any point.
		*
		* Notifies the agent that a NES suggestion was rejected.
		*
		* @experimental
		*/
		async unstable_rejectNes(params) {
			return await this.#connection.sendNotification(AGENT_METHODS.nes_reject, params);
		}
		/**
		* Extension method
		*
		* Allows the Client to send an arbitrary request that is not part of the ACP spec.
		*/
		async extMethod(method, params) {
			return await this.#connection.sendRequest(method, params);
		}
		/**
		* Extension notification
		*
		* Allows the Client to send an arbitrary notification that is not part of the ACP spec.
		*/
		async extNotification(method, params) {
			return await this.#connection.sendNotification(method, params);
		}
		/**
		* AbortSignal that aborts when the connection closes.
		*
		* This signal can be used to:
		* - Listen for connection closure: `connection.signal.addEventListener('abort', () => {...})`
		* - Check connection status synchronously: `if (connection.signal.aborted) {...}`
		* - Pass to other APIs (fetch, setTimeout) for automatic cancellation
		*
		* The connection closes when the underlying stream ends, either normally or due to an error.
		*
		* @example
		* ```typescript
		* const connection = new ClientSideConnection(client, stream);
		*
		* // Listen for closure
		* connection.signal.addEventListener('abort', () => {
		*   console.log('Connection closed - performing cleanup');
		* });
		*
		* // Check status
		* if (connection.signal.aborted) {
		*   console.log('Connection is already closed');
		* }
		*
		* // Pass to other APIs
		* fetch(url, { signal: connection.signal });
		* ```
		*/
		get signal() {
			return this.#connection.signal;
		}
		/**
		* Promise that resolves when the connection closes.
		*
		* The connection closes when the underlying stream ends, either normally or due to an error.
		* Once closed, the connection cannot send or receive any more messages.
		*
		* This is useful for async/await style cleanup:
		*
		* @example
		* ```typescript
		* const connection = new ClientSideConnection(client, stream);
		* await connection.closed;
		* console.log('Connection closed - performing cleanup');
		* ```
		*/
		get closed() {
			return this.#connection.closed;
		}
	};
	Connection = class {
		#pendingResponses = /* @__PURE__ */ new Map();
		#nextRequestId = 0;
		#requestHandler;
		#notificationHandler;
		#stream;
		#writeQueue = Promise.resolve();
		#abortController = new AbortController();
		#closedPromise;
		constructor(requestHandler, notificationHandler, stream) {
			this.#requestHandler = requestHandler;
			this.#notificationHandler = notificationHandler;
			this.#stream = stream;
			this.#closedPromise = new Promise((resolve) => {
				this.#abortController.signal.addEventListener("abort", () => resolve());
			});
			this.#receive();
		}
		/**
		* AbortSignal that aborts when the connection closes.
		*
		* This signal can be used to:
		* - Listen for connection closure via event listeners
		* - Check connection status synchronously with `signal.aborted`
		* - Pass to other APIs (fetch, setTimeout) for automatic cancellation
		*/
		get signal() {
			return this.#abortController.signal;
		}
		/**
		* Promise that resolves when the connection closes.
		*
		* The connection closes when the underlying stream ends, either normally
		* or due to an error. Once closed, the connection cannot send or receive
		* any more messages.
		*
		* @example
		* ```typescript
		* const connection = new ClientSideConnection(client, stream);
		* await connection.closed;
		* console.log('Connection closed - performing cleanup');
		* ```
		*/
		get closed() {
			return this.#closedPromise;
		}
		async #receive() {
			let closeError = void 0;
			try {
				const reader = this.#stream.readable.getReader();
				try {
					while (!this.#abortController.signal.aborted) {
						const { value: message, done } = await reader.read();
						if (done) break;
						if (!message) continue;
						try {
							this.#processMessage(message);
						} catch (err) {
							console.error("Unexpected error during message processing:", message, err);
							if ("id" in message && message.id !== void 0) this.#sendMessage({
								jsonrpc: "2.0",
								id: message.id,
								error: {
									code: -32700,
									message: "Parse error"
								}
							});
						}
					}
				} finally {
					reader.releaseLock();
				}
			} catch (error) {
				closeError = error;
			} finally {
				this.#close(closeError);
			}
		}
		#close(error) {
			if (this.#abortController.signal.aborted) return;
			const closeError = error ?? /* @__PURE__ */ new Error("ACP connection closed");
			for (const pendingResponse of this.#pendingResponses.values()) pendingResponse.reject(closeError);
			this.#pendingResponses.clear();
			this.#abortController.abort(closeError);
		}
		async #processMessage(message) {
			if ("method" in message && "id" in message) {
				const response = await this.#tryCallRequestHandler(message.method, message.params);
				if ("error" in response) console.error("Error handling request", message, response.error);
				await this.#sendMessage({
					jsonrpc: "2.0",
					id: message.id,
					...response
				});
			} else if ("method" in message) {
				const response = await this.#tryCallNotificationHandler(message.method, message.params);
				if ("error" in response) console.error("Error handling notification", message, response.error);
			} else if ("id" in message) this.#handleResponse(message);
			else console.error("Invalid message", { message });
		}
		async #tryCallRequestHandler(method, params) {
			try {
				return { result: await this.#requestHandler(method, params) ?? null };
			} catch (error) {
				if (error instanceof RequestError) return error.toResult();
				if (error instanceof ZodError) return RequestError.invalidParams(error.format()).toResult();
				let details;
				if (error instanceof Error) details = error.message;
				else if (typeof error === "object" && error != null && "message" in error && typeof error.message === "string") details = error.message;
				try {
					return RequestError.internalError(details ? JSON.parse(details) : {}).toResult();
				} catch {
					return RequestError.internalError({ details }).toResult();
				}
			}
		}
		async #tryCallNotificationHandler(method, params) {
			try {
				await this.#notificationHandler(method, params);
				return { result: null };
			} catch (error) {
				if (error instanceof RequestError) return error.toResult();
				if (error instanceof ZodError) return RequestError.invalidParams(error.format()).toResult();
				let details;
				if (error instanceof Error) details = error.message;
				else if (typeof error === "object" && error != null && "message" in error && typeof error.message === "string") details = error.message;
				try {
					return RequestError.internalError(details ? JSON.parse(details) : {}).toResult();
				} catch {
					return RequestError.internalError({ details }).toResult();
				}
			}
		}
		#handleResponse(response) {
			const pendingResponse = this.#pendingResponses.get(response.id);
			if (pendingResponse) {
				if ("result" in response) pendingResponse.resolve(response.result);
				else if ("error" in response) {
					const { code, message, data } = response.error;
					pendingResponse.reject(new RequestError(code, message, data));
				}
				this.#pendingResponses.delete(response.id);
			} else console.error("Got response to unknown request", response.id);
		}
		async sendRequest(method, params) {
			this.#throwIfClosed();
			const id = this.#nextRequestId++;
			const responsePromise = new Promise((resolve, reject) => {
				this.#pendingResponses.set(id, {
					resolve,
					reject
				});
			});
			await this.#sendMessage({
				jsonrpc: "2.0",
				id,
				method,
				params
			});
			return responsePromise;
		}
		async sendNotification(method, params) {
			this.#throwIfClosed();
			await this.#sendMessage({
				jsonrpc: "2.0",
				method,
				params
			});
		}
		#throwIfClosed() {
			if (this.#abortController.signal.aborted) throw this.#abortController.signal.reason ?? /* @__PURE__ */ new Error("ACP connection closed");
		}
		async #sendMessage(message) {
			this.#writeQueue = this.#writeQueue.then(async () => {
				const writer = this.#stream.writable.getWriter();
				try {
					await writer.write(message);
				} finally {
					writer.releaseLock();
				}
			}).catch((error) => {
				this.#close(error);
			});
			return this.#writeQueue;
		}
	};
	RequestError = class RequestError extends Error {
		code;
		data;
		constructor(code, message, data) {
			super(message);
			this.code = code;
			this.name = "RequestError";
			this.data = data;
		}
		/**
		* Invalid JSON was received by the server. An error occurred on the server while parsing the JSON text.
		*/
		static parseError(data, additionalMessage) {
			return new RequestError(-32700, `Parse error${additionalMessage ? `: ${additionalMessage}` : ""}`, data);
		}
		/**
		* The JSON sent is not a valid Request object.
		*/
		static invalidRequest(data, additionalMessage) {
			return new RequestError(-32600, `Invalid request${additionalMessage ? `: ${additionalMessage}` : ""}`, data);
		}
		/**
		* The method does not exist / is not available.
		*/
		static methodNotFound(method) {
			return new RequestError(-32601, `"Method not found": ${method}`, { method });
		}
		/**
		* Invalid method parameter(s).
		*/
		static invalidParams(data, additionalMessage) {
			return new RequestError(-32602, `Invalid params${additionalMessage ? `: ${additionalMessage}` : ""}`, data);
		}
		/**
		* Internal JSON-RPC error.
		*/
		static internalError(data, additionalMessage) {
			return new RequestError(-32603, `Internal error${additionalMessage ? `: ${additionalMessage}` : ""}`, data);
		}
		/**
		* Authentication required.
		*/
		static authRequired(data, additionalMessage) {
			return new RequestError(-32e3, `Authentication required${additionalMessage ? `: ${additionalMessage}` : ""}`, data);
		}
		/**
		* Resource, such as a file, was not found
		*/
		static resourceNotFound(uri) {
			return new RequestError(-32002, `Resource not found${uri ? `: ${uri}` : ""}`, uri && { uri });
		}
		toResult() {
			return { error: {
				code: this.code,
				message: this.message,
				data: this.data
			} };
		}
		toErrorResponse() {
			return {
				code: this.code,
				message: this.message,
				data: this.data
			};
		}
	};
}))();
exports.AGENT_METHODS = AGENT_METHODS;
exports.AgentSideConnection = AgentSideConnection;
exports.CLIENT_METHODS = CLIENT_METHODS;
exports.ClientSideConnection = ClientSideConnection;
exports.PROTOCOL_VERSION = PROTOCOL_VERSION;
exports.RequestError = RequestError;
exports.TerminalHandle = TerminalHandle;
exports.ndJsonStream = ndJsonStream;
