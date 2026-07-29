import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the ToneIME review mock", async () => {
  const response = await render();
  const html = await response.text();
  const pageSource = await readFile(new URL("../app/page.tsx", import.meta.url), "utf8");

  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  assert.match(html, /<title>ToneIME Android UI Mock<\/title>/i);
  assert.match(html, /圆角界面提案/);
  assert.match(html, /翻译工作区/);
  assert.match(html, /ToneIME 实时翻译/);
  assert.match(pageSource, /role="slider"/);
  assert.match(html, /拖动右下角调整大小/);
  for (const code of ["zh", "ja", "en", "ko", "de"]) {
    assert.match(html, new RegExp(`value="${code}"`));
  }
});
