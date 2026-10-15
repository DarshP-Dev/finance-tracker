import { test } from "node:test";
import assert from "node:assert/strict";
import { AxiosError } from "axios";
import { createRequire } from "node:module";
import sessionModule from "../.local/auth-session.cjs";
const { createSessionClient } = sessionModule;
const user = token => ({ accessToken: token, tokenType: "Bearer", userId: 1, username: "Test", email: "test@example.invalid", expiresIn: 900 });
const response = (config, data, status = 200) => ({ config, data, status, statusText: String(status), headers: {} });
function reject(config, status = 401, code = "AUTHENTICATION_REQUIRED") {
  throw new AxiosError("Rejected", "ERR_BAD_RESPONSE", config, {}, response(config, { code }, status));
}
function fixture() {
  const session = createSessionClient("http://test.invalid");
  const calls = [];
  session.authApi.defaults.adapter = async config => {
    calls.push(config.url);
    assert.equal(config.withCredentials, true);
    assert.equal(config.headers.Authorization, undefined);
    if (config.url.endsWith("/csrf")) return response(config, { token: "masked-csrf", headerName: "X-XSRF-TOKEN" });
    assert.equal(config.headers["X-XSRF-TOKEN"], "masked-csrf");
    return response(config, user("renewed"));
  };
  return { session, calls };
}
function deferred() { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; }

test("login retains access token only in memory and includes CSRF", async () => {
  const { session, calls } = fixture();
  const result = await session.authenticate("login", { email: "test@example.invalid", password: "test-password" });
  assert.equal(session.getStoredAuth(), result);
  assert.deepEqual(calls, ["/api/auth/csrf", "/api/auth/login"]);
});
test("registration preserves explicit sign-in flow", async () => {
  const { session } = fixture();
  await session.registerAccount({ username: "Test", email: "test@example.invalid", password: "test-password" });
  assert.equal(session.getStoredAuth(), null);
});
test("browser reload restores from refresh cookie once for concurrent mount calls", async () => {
  const { session, calls } = fixture();
  const restored = await Promise.all([session.restoreSession(), session.restoreSession()]);
  assert.equal(restored[0], restored[1]);
  assert.equal(calls.filter(c => c.endsWith("/refresh")).length, 1);
});
test("expired access token refreshes and retries an authenticated mutation once", async () => {
  const { session, calls } = fixture(); session.storeAuth(user("expired"));
  let attempts = 0, writes = 0;
  session.api.defaults.adapter = async config => {
    attempts++;
    if (config.headers.Authorization === "Bearer expired") reject(config);
    writes++; return response(config, { id: 1 });
  };
  assert.equal((await session.api.post("/api/transactions", { amount: 10 })).data.id, 1);
  assert.equal(attempts, 2); assert.equal(writes, 1);
  assert.equal(calls.filter(c => c.endsWith("/refresh")).length, 1);
});
test("simultaneous requests share one rotation", async () => {
  const { session, calls } = fixture(); session.storeAuth(user("expired"));
  session.api.defaults.adapter = async config => {
    if (config.headers.Authorization === "Bearer expired") reject(config);
    return response(config, "ok");
  };
  await Promise.all(Array.from({ length: 20 }, () => session.api.get("/api/transactions")));
  assert.equal(calls.filter(c => c.endsWith("/refresh")).length, 1);
});
test("late 401 reuses refreshed access token instead of rotating twice", async () => {
  const { session, calls } = fixture(); session.storeAuth(user("expired"));
  const gate = deferred();
  session.api.defaults.adapter = async config => {
    if (config.url === "/late" && config.headers.Authorization === "Bearer expired") { await gate.promise; reject(config); }
    if (config.headers.Authorization === "Bearer expired") reject(config);
    return response(config, "ok");
  };
  const late = session.api.get("/late"); await session.api.get("/early"); gate.resolve(); await late;
  assert.equal(calls.filter(c => c.endsWith("/refresh")).length, 1);
});
test("expired refresh clears state and reports session expiration", async () => {
  const { session } = fixture(); session.storeAuth(user("expired")); let message;
  session.subscribe((_auth, text) => { message = text; });
  session.api.defaults.adapter = async config => reject(config);
  const original = session.authApi.defaults.adapter;
  session.authApi.defaults.adapter = async config => config.url.endsWith("/refresh") ? reject(config, 401, "INVALID_SESSION") : original(config);
  await assert.rejects(session.api.get("/api/transactions"));
  assert.equal(session.getStoredAuth(), null); assert.match(message, /session expired/i);
});
test("second access rejection stops instead of entering a refresh loop", async () => {
  const { session, calls } = fixture(); session.storeAuth(user("expired")); let attempts = 0;
  session.api.defaults.adapter = async config => { attempts++; reject(config); };
  await assert.rejects(session.api.get("/api/transactions"));
  assert.equal(attempts, 2); assert.equal(calls.filter(c => c.endsWith("/refresh")).length, 1);
  assert.equal(session.getStoredAuth(), null);
});
test("403 and business 401 never refresh, retry or sign out", async () => {
  for (const [status, code] of [[403, "FORBIDDEN"], [401, "BUSINESS_ERROR"]]) {
    const { session, calls } = fixture(); session.storeAuth(user("valid")); let attempts = 0;
    session.api.defaults.adapter = async config => { attempts++; reject(config, status, code); };
    await assert.rejects(session.api.post("/api/transactions", { amount: 10 }));
    assert.equal(attempts, 1); assert.equal(calls.length, 0); assert.equal(session.getStoredAuth().accessToken, "valid");
  }
});
test("network failures never retry financial writes", async () => {
  const { session, calls } = fixture(); session.storeAuth(user("valid")); let attempts = 0;
  session.api.defaults.adapter = async config => { attempts++; throw new AxiosError("Network error", "ERR_NETWORK", config); };
  await assert.rejects(session.api.post("/api/transactions", { amount: 10 }));
  assert.equal(attempts, 1); assert.equal(calls.length, 0); assert.ok(session.getStoredAuth());
});
test("network error restoring session exits loading and reports connectivity", async () => {
  const { session } = fixture(); let message;
  session.subscribe((_auth, text) => { message = text; });
  session.authApi.defaults.adapter = async config => { throw new AxiosError("Network error", "ERR_NETWORK", config); };
  assert.equal(await session.restoreSession(), null); assert.match(message, /connection/i);
});
test("logout clears memory and makes a cookie-authenticated backend request", async () => {
  const { session, calls } = fixture(); session.storeAuth(user("valid"));
  await session.logoutSession(); assert.equal(session.getStoredAuth(), null);
  assert.deepEqual(calls, ["/api/auth/csrf", "/api/auth/logout"]);
});
test("failed backend logout is reported honestly and keeps memory cleared", async () => {
  const { session } = fixture(); session.storeAuth(user("valid")); let message;
  session.subscribe((_auth, text) => { message = text; });
  session.authApi.defaults.adapter = async config => { throw new AxiosError("Network error", "ERR_NETWORK", config); };
  await session.logoutSession(); assert.equal(session.getStoredAuth(), null); assert.match(message, /could not be confirmed/i);
});
test("logout during refresh cannot resurrect authentication or retry a mutation", async () => {
  const { session } = fixture(); session.storeAuth(user("expired"));
  const gate = deferred(), started = deferred(); let attempts = 0;
  const original = session.authApi.defaults.adapter;
  session.authApi.defaults.adapter = async config => {
    if (config.url.endsWith("/refresh")) { started.resolve(); await gate.promise; }
    return original(config);
  };
  session.api.defaults.adapter = async config => { attempts++; reject(config); };
  const request = session.api.post("/api/transactions", { amount: 10 });
  const failed = assert.rejects(request); await started.promise;
  const logout = session.logoutSession(); gate.resolve(); await logout; await failed;
  assert.equal(session.getStoredAuth(), null); assert.equal(attempts, 1);
});
test("an old successful response cannot change a newly signed-in session", async () => {
  const { session } = fixture(); session.storeAuth(user("old")); const gate=deferred();
  session.api.defaults.adapter = async config => { await gate.promise; return response(config, { secret: "old-account" }); };
  const request=session.api.get("/api/transactions"); const failed=assert.rejects(request);
  await new Promise(resolve => setImmediate(resolve)); session.clearStoredAuth(); session.storeAuth(user("new")); gate.resolve(); await failed;
  assert.equal(session.getStoredAuth().accessToken,"new");
});
test("browser storage removes legacy tokens and cross-tab logout clears memory", async () => {
  const savedWindow=global.window, savedChannel=global.BroadcastChannel;
  const map=new Map([["financeTrackerToken","legacy"],["financeTrackerAuth","legacy"]]), events=new Map(), channels=[];
  global.window={ localStorage: { removeItem: key => map.delete(key), setItem: (key,value) => map.set(key,value) },
    addEventListener: (name,listener) => events.set(name,listener), dispatchEvent: () => {} };
  global.BroadcastChannel=class { constructor() { channels.push(this); } postMessage() {} };
  try {
    const { session }=fixture(); session.storeAuth(user("memory"));
    assert.equal(map.has("financeTrackerToken"),false); assert.equal(map.has("financeTrackerAuth"),false);
    events.get("storage")({ key:"finance-tracker:logout" }); assert.equal(session.getStoredAuth(),null);
    session.storeAuth(user("memory")); channels[0].onmessage({ data:"logout" }); assert.equal(session.getStoredAuth(),null);
    await session.logoutSession(); assert.ok(map.has("finance-tracker:logout"));
    assert.ok([...map.values()].every(value => !value.includes("memory")));
  } finally { global.window=savedWindow; global.BroadcastChannel=savedChannel; }
});

test("a recovered financial mutation notifies once and commits once through the shared API", async () => {
  const compiledAxios=createRequire(import.meta.url)("axios");
  const savedWindow=global.window, savedChannel=global.BroadcastChannel, savedAdapter=compiledAxios.defaults.adapter;
  const events=[];
  global.window={ localStorage: { removeItem() {}, setItem() {} }, addEventListener() {}, dispatchEvent: event => events.push(event.type) };
  global.BroadcastChannel=class { postMessage() {} };
  let commits=0, refreshes=0;
  compiledAxios.defaults.adapter=async config => {
    if (config.url.endsWith("/csrf")) return response(config,{ token:"masked-csrf", headerName:"X-XSRF-TOKEN" });
    if (config.url.endsWith("/refresh")) { refreshes++; return response(config,user("renewed")); }
    if (config.headers.Authorization==="Bearer expired") reject(config);
    commits++; return response(config,{ id:1 });
  };
  try {
    const { default: apiModule }=await import("../.local/api.cjs");
    apiModule.storeAuth(user("expired"));
    await apiModule.createTransaction({ amount:10, category:"OTHER", type:"EXPENSE", date:"2026-10-01" });
    assert.equal(commits,1); assert.equal(refreshes,1);
    assert.equal(events.filter(name => name==="finance-tracker:notifications-changed").length,1);
  } finally { global.window=savedWindow; global.BroadcastChannel=savedChannel; compiledAxios.defaults.adapter=savedAdapter; }
});
