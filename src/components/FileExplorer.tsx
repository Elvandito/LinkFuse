import { useMemo, useState } from "react";
import Prism from "prismjs";
import "prismjs/components/prism-kotlin";
import { buildTree, FILES, langFor, type FileNode } from "../lib/files";
import { cn } from "../utils/cn";

function escapeHtml(s: string) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

const extColor: Record<string, string> = {
  kt: "text-wifi",
  kts: "text-wire",
  xml: "text-cell",
  md: "text-fuse",
  pro: "text-dim",
  properties: "text-dim",
};

function TreeNode({
  node,
  depth,
  selected,
  expanded,
  onSelect,
  onToggle,
}: {
  node: FileNode;
  depth: number;
  selected: string;
  expanded: Set<string>;
  onSelect: (p: string) => void;
  onToggle: (p: string) => void;
}) {
  const pad = { paddingLeft: `${10 + depth * 14}px` };
  if (node.type === "dir") {
    const open = expanded.has(node.path);
    return (
      <div>
        <button
          onClick={() => onToggle(node.path)}
          style={pad}
          className="flex w-full items-center gap-1.5 py-[5px] pr-2 text-left font-mono text-[12px] text-dim transition-colors hover:bg-panel hover:text-ink"
        >
          <svg width="9" height="9" viewBox="0 0 10 10" className={cn("shrink-0 transition-transform", open && "rotate-90")}>
            <path d="M3 1l4 4-4 4" fill="none" stroke="currentColor" strokeWidth="1.6" />
          </svg>
          <span className="truncate">{node.name}</span>
        </button>
        {open &&
          node.children?.map((c) => (
            <TreeNode key={c.path} node={c} depth={depth + 1} selected={selected} expanded={expanded} onSelect={onSelect} onToggle={onToggle} />
          ))}
      </div>
    );
  }
  const ext = node.name.split(".").pop() ?? "";
  return (
    <button
      onClick={() => onSelect(node.path)}
      style={pad}
      className={cn(
        "flex w-full items-center gap-2 py-[5px] pr-2 text-left font-mono text-[12px] transition-colors",
        selected === node.path ? "bg-panel text-ink" : "text-dim hover:bg-panel/60 hover:text-ink"
      )}
    >
      <span className={cn("h-1.5 w-1.5 shrink-0 rounded-[2px]", extColor[ext] ?? "text-dim")} style={{ background: "currentColor" }} />
      <span className="truncate">{node.name}</span>
    </button>
  );
}

export default function FileExplorer() {
  const tree = useMemo(() => buildTree(), []);
  const [selected, setSelected] = useState<string>("README.md");
  const [expanded, setExpanded] = useState<Set<string>>(
    () => new Set(["app", "app/src", "app/src/main", "app/src/main/java", "app/src/main/java/dev", "app/src/main/java/dev/linkfuse", "app/src/main/java/dev/linkfuse/app"])
  );
  const [copied, setCopied] = useState(false);

  const code = FILES[selected] ?? "";
  const lang = langFor(selected);
  const html = useMemo(() => {
    if (lang === "kotlin" && Prism.languages.kotlin) return Prism.highlight(code, Prism.languages.kotlin, "kotlin");
    if (lang === "markup" && Prism.languages.markup) return Prism.highlight(code, Prism.languages.markup, "markup");
    return escapeHtml(code);
  }, [code, lang]);

  const lines = code.split("\n").length;
  const kb = (new Blob([code]).size / 1024).toFixed(1);

  const toggle = (p: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(p) ? next.delete(p) : next.add(p);
      return next;
    });

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    } catch {
      /* clipboard unavailable */
    }
  };

  return (
    <div className="grid overflow-hidden rounded-xl border border-edge bg-deep lg:grid-cols-[280px_1fr]">
      <aside className="max-h-64 overflow-y-auto border-b border-edge bg-abyss/60 py-2 lg:max-h-[620px] lg:border-b-0 lg:border-r code-scroll">
        <div className="px-3 pb-2 font-mono text-[10.5px] tracking-widest text-faint uppercase">android/</div>
        {tree.map((n) => (
          <TreeNode key={n.path} node={n} depth={0} selected={selected} expanded={expanded} onSelect={setSelected} onToggle={toggle} />
        ))}
      </aside>

      <div className="flex min-w-0 flex-col">
        <div className="flex items-center justify-between gap-3 border-b border-edge bg-panel/60 px-4 py-2.5">
          <div className="min-w-0">
            <div className="truncate font-mono text-[12.5px] text-ink">android/{selected}</div>
            <div className="font-mono text-[10.5px] text-faint">
              {lines} lines · {kb} KB · {lang ?? "text"}
            </div>
          </div>
          <button
            onClick={copy}
            className={cn(
              "shrink-0 rounded-md border px-3 py-1.5 font-mono text-[11px] tracking-wide transition-all",
              copied ? "border-wire/60 text-wire" : "border-edge2 text-dim hover:border-wifi/60 hover:text-wifi"
            )}
          >
            {copied ? "copied ✓" : "copy file"}
          </button>
        </div>
        <pre className="code-scroll max-h-[420px] overflow-auto bg-deep p-4 lg:max-h-[560px]">
          <code dangerouslySetInnerHTML={{ __html: html }} />
        </pre>
      </div>
    </div>
  );
}
