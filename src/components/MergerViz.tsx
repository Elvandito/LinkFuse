import { useEffect, useState } from "react";

const LINKS = [
  { id: "wifi", label: "Wi-Fi 6", color: "var(--color-wifi)", y: 84, path: "M 84 84 C 190 84 230 196 302 196", speed: 38 },
  { id: "cell", label: "5G", color: "var(--color-cell)", y: 200, path: "M 84 200 C 180 200 220 200 302 200", speed: 24 },
  { id: "usb", label: "USB tether", color: "var(--color-wire)", y: 316, path: "M 84 316 C 190 316 230 204 302 204", speed: 46 },
];

function useJitter(base: number, spread: number, ms = 900) {
  const [v, setV] = useState(base);
  useEffect(() => {
    const t = setInterval(() => {
      setV(base + (Math.random() - 0.5) * spread);
    }, ms);
    return () => clearInterval(t);
  }, [base, spread, ms]);
  return v;
}

export default function MergerViz() {
  const s0 = useJitter(LINKS[0].speed, 9);
  const s1 = useJitter(LINKS[1].speed, 7);
  const s2 = useJitter(LINKS[2].speed, 11);
  const speeds = [s0, s1, s2];
  const total = s0 + s1 + s2;

  return (
    <div className="relative rounded-xl border border-edge bg-deep/80 p-4 shadow-[0_30px_80px_-30px_rgba(0,0,0,0.8)]">
      <div className="flex items-center justify-between px-2 pb-2">
        <span className="font-mono text-[11px] tracking-widest text-dim uppercase">tun0 · live</span>
        <span className="flex items-center gap-2 rounded-full border border-edge2 bg-panel px-3 py-1 font-mono text-[11px] tracking-wider text-okay">
          <span className="h-1.5 w-1.5 rounded-full bg-okay" style={{ animation: "nodepulse 1.6s infinite" }} />
          MERGING
        </span>
      </div>

      <svg viewBox="0 0 560 400" className="w-full" role="img" aria-label="Three network links fusing into one bonded pipe">
        {/* link rails */}
        {LINKS.map((l, i) => (
          <g key={l.id}>
            <path d={l.path} fill="none" stroke={l.color} strokeOpacity="0.16" strokeWidth="7" strokeLinecap="round" />
            <path d={l.path} fill="none" stroke={l.color} strokeWidth="2.2" className="flow-line" style={{ animationDelay: `${i * 0.4}s` }} />
            {/* moving packet */}
            <circle r="3.4" fill={l.color}>
              <animateMotion dur={`${2.1 + i * 0.35}s`} repeatCount="indefinite" path={l.path} />
            </circle>
            <circle r="2.2" fill={l.color} opacity="0.7">
              <animateMotion dur={`${2.1 + i * 0.35}s`} begin={`${(2.1 + i * 0.35) / 2}s`} repeatCount="indefinite" path={l.path} />
            </circle>

            {/* source node */}
            <rect x="14" y={l.y - 24} width="70" height="48" rx="10" fill="#0d1728" stroke={l.color} strokeOpacity="0.55" />
            <circle cx="30" cy={l.y} r="5" fill={l.color} className="node-pulse" style={{ animationDelay: `${i * 0.5}s` }} />
            <text x="42" y={l.y - 4} fill="#e9f1f9" fontSize="12" fontWeight="600" fontFamily="Chakra Petch">{l.label}</text>
            <text x="42" y={l.y + 12} fill={l.color} fontSize="10.5" fontFamily="JetBrains Mono">
              {speeds[i].toFixed(1)} Mb/s
            </text>
          </g>
        ))}

        {/* fuse core */}
        <circle cx="322" cy="200" r="30" fill="#0d1728" stroke="var(--color-fuse)" strokeWidth="2" />
        <circle cx="322" cy="200" r="17" fill="var(--color-fuse)" opacity="0.22" className="node-pulse" />
        <circle cx="322" cy="200" r="8" fill="var(--color-fuse)" />
        <text x="322" y="252" textAnchor="middle" fill="#93a7bd" fontSize="10.5" fontFamily="JetBrains Mono" letterSpacing="2">
          FLOW TABLE
        </text>

        {/* bonded output */}
        <path d="M 352 200 L 512 200" stroke="var(--color-fuse)" strokeOpacity="0.18" strokeWidth="12" strokeLinecap="round" />
        <path d="M 352 200 L 512 200" stroke="var(--color-fuse)" strokeWidth="3" className="flow-line" style={{ animationDuration: "1.3s" }} />
        <circle r="4.5" fill="var(--color-fuse)">
          <animateMotion dur="1.15s" repeatCount="indefinite" path="M 352 200 L 512 200" />
        </circle>
        <path d="M 512 192 L 528 200 L 512 208 Z" fill="var(--color-fuse)" />

        {/* combined readout */}
        <text x="432" y="176" textAnchor="middle" fill="#e9f1f9" fontSize="26" fontWeight="700" fontFamily="Chakra Petch">
          {total.toFixed(1)}
        </text>
        <text x="432" y="160" textAnchor="middle" fill="#93a7bd" fontSize="10" fontFamily="JetBrains Mono" letterSpacing="1.5">
          Mb/s COMBINED
        </text>
      </svg>

      <div className="mt-1 grid grid-cols-3 gap-2 px-1 font-mono text-[10.5px]">
        <div className="rounded-md border border-edge bg-panel/70 px-2.5 py-1.5 text-wifi">udp · 5-tuple pinned</div>
        <div className="rounded-md border border-edge bg-panel/70 px-2.5 py-1.5 text-cell">tcp · per-flow sticky</div>
        <div className="rounded-md border border-edge bg-panel/70 px-2.5 py-1.5 text-wire">failover · automatic</div>
      </div>
    </div>
  );
}
