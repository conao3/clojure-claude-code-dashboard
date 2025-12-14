import fs from "fs";
import path from "path";
import prettier from "prettier";

const KEYWORD_PATTERN =
  /(:div|:span|:button|:input|:a|:p|:h[1-6]|:label|:form|:section|:article|:header|:footer|:nav|:main|:aside|:ul|:ol|:li|:table|:tr|:td|:th|:thead|:tbody|:img|:details|:summary|:pre|:code)((?:\.[a-zA-Z0-9_/-]+(?:\.[a-zA-Z0-9_/-]+)*)?)/g;

const CLASS_STRING_PATTERN =
  /(:class(?:Name)?)\s+"([^"]+)"/g;

async function sortClasses(classString) {
  const formatted = await prettier.format(`<div class="${classString}"></div>`, {
    parser: "html",
    plugins: ["prettier-plugin-tailwindcss"],
    tailwindStylesheet: path.join(process.cwd(), "resources/public/css/main.css"),
  });
  const match = formatted.match(/class="([^"]*)"/);
  return match ? match[1] : classString;
}

function parseKeywordClasses(keyword) {
  const classes = keyword.slice(1).split(".");
  const tag = classes[0];
  const classNames = classes.slice(1);
  return { tag, classNames };
}

function buildKeyword(tag, classNames) {
  if (classNames.length === 0) {
    return `:${tag}`;
  }
  return `:${tag}.${classNames.join(".")}`;
}

async function processFile(filePath) {
  const content = fs.readFileSync(filePath, "utf-8");
  let result = content;
  let modified = false;

  const keywordMatches = [...content.matchAll(KEYWORD_PATTERN)];
  for (const match of keywordMatches.reverse()) {
    const [fullMatch, tag, classesPart] = match;
    if (!classesPart || classesPart === "") continue;

    const classNames = classesPart.slice(1).split(".");
    if (classNames.length <= 1) continue;

    const classString = classNames.join(" ");
    const sortedClassString = await sortClasses(classString);
    const sortedClasses = sortedClassString.split(" ").filter(Boolean);

    if (classNames.join(" ") !== sortedClasses.join(" ")) {
      const newKeyword = `${tag}.${sortedClasses.join(".")}`;
      result =
        result.slice(0, match.index) +
        newKeyword +
        result.slice(match.index + fullMatch.length);
      modified = true;
    }
  }

  const classStringMatches = [...result.matchAll(CLASS_STRING_PATTERN)];
  for (const match of classStringMatches.reverse()) {
    const [fullMatch, prefix, classString] = match;
    const sortedClassString = await sortClasses(classString);

    if (classString !== sortedClassString) {
      const newClassAttr = `${prefix} "${sortedClassString}"`;
      result =
        result.slice(0, match.index) +
        newClassAttr +
        result.slice(match.index + fullMatch.length);
      modified = true;
    }
  }

  if (modified) {
    fs.writeFileSync(filePath, result);
    console.log(`Modified: ${filePath}`);
    return true;
  }
  return false;
}

async function findCljsFiles(dir) {
  const files = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory() && !entry.name.startsWith(".")) {
      files.push(...(await findCljsFiles(fullPath)));
    } else if (entry.isFile() && entry.name.endsWith(".cljs")) {
      files.push(fullPath);
    }
  }
  return files;
}

async function main() {
  const args = process.argv.slice(2);
  const targetPath = args[0] || "src";

  const stat = fs.statSync(targetPath);
  let files;

  if (stat.isDirectory()) {
    files = await findCljsFiles(targetPath);
  } else {
    files = [targetPath];
  }

  let modifiedCount = 0;
  for (const file of files) {
    try {
      const wasModified = await processFile(file);
      if (wasModified) modifiedCount++;
    } catch (err) {
      console.error(`Error processing ${file}:`, err.message);
    }
  }

  console.log(`\nProcessed ${files.length} files, modified ${modifiedCount}`);
}

main().catch(console.error);
