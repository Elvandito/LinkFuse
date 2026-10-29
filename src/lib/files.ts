// Loads the actual Android project sources from /android as raw strings at build time,
// so the on-page code viewer can never drift from the files you'd actually compile.
const modules = import.meta.glob(
  "/android/**/*.{kt,kts,xml,md,pro,properties}",
  { query: "?raw", import: "default", eager: true }
) as Record<string, string>;

export interface FileNode {
  name: string;
  path: string;
  type: "file" | "dir";
  children?: FileNode[];
}

export const FILES: Record<string, string> = Object.fromEntries(
  Object.entries(modules)
    .map(([k, v]) => [k.replace(/^\/android\//, ""), v] as const)
    .filter(
      ([p]) =>
        !p.includes("app/build/") &&
        !p.startsWith(".gradle/") &&
        p !== "local.properties" // machine-specific, never ship it
    )
);

export function buildTree(): FileNode[] {
  const root: FileNode[] = [];
  for (const path of Object.keys(FILES).sort()) {
    const parts = path.split("/");
    let level = root;
    let acc = "";
    parts.forEach((part, i) => {
      acc = acc ? `${acc}/${part}` : part;
      const last = i === parts.length - 1;
      let node = level.find((n) => n.name === part);
      if (!node) {
        node = last
          ? { name: part, path: acc, type: "file" }
          : { name: part, path: acc, type: "dir", children: [] };
        level.push(node);
      }
      if (!last) level = node.children!;
    });
  }
  // directories first, then files, alphabetical
  const sortRec = (nodes: FileNode[]) => {
    nodes.sort((a, b) =>
      a.type === b.type ? a.name.localeCompare(b.name) : a.type === "dir" ? -1 : 1
    );
    nodes.forEach((n) => n.children && sortRec(n.children));
  };
  sortRec(root);
  return root;
}

export function langFor(path: string): string | null {
  if (path.endsWith(".kt") || path.endsWith(".kts")) return "kotlin";
  if (path.endsWith(".xml")) return "markup";
  return null;
}

export const FILE_COUNT = Object.keys(FILES).length;
