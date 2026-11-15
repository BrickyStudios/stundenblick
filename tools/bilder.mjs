// Renders the repo images from the demo-school screenshots in docs/bilder/app. Needs Playwright:
//   PLAYWRIGHT=/path/to/node_modules/playwright/index.mjs node tools/bilder.mjs
import path from "node:path";
import { fileURLToPath } from "node:url";
const { chromium } = await import(process.env.PLAYWRIGHT ?? "playwright");
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const app = (f) => `file://${root}/docs/bilder/app/${f}`;
const images = [
  ["1-woche.png", 1080, 1920, "tall", "Die ganze Woche|auf einen Blick", "Entfall, Vertretung, Raum, Klausur", app("week.png")],
  ["2-tag.png", 1080, 1920, "tall", "Wer, wo, statt wem", "Tagesansicht mit vollen Namen", app("day.png")],
  ["3-details.png", 1080, 1920, "tall", "Stoff und|Hausaufgaben", "pro Stunde, wenn die Schule sie einträgt", app("detail.png")],
  ["4-anmelden.png", 1080, 1920, "tall", "QR-Code scannen,|fertig", "auch mit Zwei-Faktor und IServ", app("login.png")],
  ["social-preview.png", 1280, 640, "wide", "Dein Untis-Plan,|endlich lesbar", "Android-App · Nachfolger von BetterUntis · GPL-3.0", app("week.png")],
];
const browser = await chromium.launch();
for (const [name, w, h, art, t, s, img] of images) {
  const page = await browser.newPage({ viewport: { width: w, height: h } });
  const u = new URL(`file://${root}/tools/rahmen.html`);
  for (const [k, v] of Object.entries({ w, h, art, t, s, img })) u.searchParams.set(k, v);
  await page.goto(u.href, { waitUntil: "networkidle" });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: `${root}/docs/bilder/${name}` });
  await page.close();
}
await browser.close();
