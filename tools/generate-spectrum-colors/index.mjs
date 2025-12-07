import { writeFile } from "node:fs/promises";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { getFileTokens } from "@adobe/spectrum-tokens";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = resolve(__dirname, "../..");
const OUTPUT_PATH = resolve(ROOT_DIR, "resources/public/css/spectrum-colors.css");

const THEME = "dark";

const colorPalette = await getFileTokens("color-palette.json");

const cssLines = ["@theme {"];
for (const [key, value] of Object.entries(colorPalette)) {
  if (value.value != null) {
    cssLines.push(`  --color-${key}: ${value.value};`);
  } else if (value.sets?.[THEME]?.value) {
    cssLines.push(`  --color-${key}: ${value.sets[THEME].value};`);
  }
}
cssLines.push("}");
cssLines.push("");

await writeFile(OUTPUT_PATH, cssLines.join("\n"), "utf8");

console.log(`Generated ${OUTPUT_PATH} (${cssLines.length - 2} colors)`);
