import { readFileSync, mkdirSync, writeFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const root = fileURLToPath(new URL("../", import.meta.url));
mkdirSync(new URL("../.local/", import.meta.url), { recursive: true });
const source = readFileSync(new URL("../src/lib/auth-session.ts", import.meta.url), "utf8");
const output = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
}).outputText;
writeFileSync(new URL("../.local/auth-session.cjs", import.meta.url), output);
writeFileSync(new URL("../.local/auth-session.js", import.meta.url), output);
const apiSource = readFileSync(new URL("../src/lib/api.ts", import.meta.url), "utf8");
writeFileSync(new URL("../.local/api.cjs", import.meta.url), ts.transpileModule(apiSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
}).outputText);
const result = spawnSync(process.execPath, ["--test", "tests/auth-session.test.mjs"], {
  cwd: root, stdio: "inherit", env: { ...process.env, NEXT_PUBLIC_API_BASE_URL: "", NODE_ENV: "test" },
});
process.exit(result.status ?? 1);
