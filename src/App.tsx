import { useEffect } from "react";
import MergerViz from "./components/MergerViz";
import FileExplorer from "./components/FileExplorer";
import { FILE_COUNT } from "./lib/files";

/* ---------- tiny inline icon set (lucide-style strokes) ---------- */
const I = {
  fuse: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" className="h-5 w-5">
      <path d="M3 6c5 3 7 5 9 6M3 12h9M3 18c5-3 7-5 9-6" />
      <circle cx="14" cy="12" r="2.4" fill="currentColor" stroke="none" />
      <path d="M16.5 12H21" strokeWidth="2.6" />
    </svg>
  ),
  tun: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" className="h-5 w-5">
      <rect x="3" y="5" width="18" height="14" rx="3" />
      <path d="M7 10h6M7 14h10" />
    </svg>
  ),
  table: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" className="h-5 w-5">
      <path d="M4 5h16M4 12h16M4 19h16M9 5v14M15 5v14" />
    </svg>
  ),
  scale: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" className="h-5 w-5">
      <path d="M12 3v18M5 8l7-5 7 5M5 8l-2.5 6a3.5 3.5 0 0 0 7 0L7 8M19 8l-2.5 6a3.5 3.5 0 0 0 7 0L21 8" transform="scale(0.86) translate(2 1)" />
    </svg>
  ),
  pin: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" className="h-5 w-5">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 3" />
    </svg>
  ),
  refresh: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" className="h-5 w-5">
      <path d="M21 12a9 9 0 1 1-2.6-6.3M21 3v6h-6" />
    </svg>
  ),
};

const RESEARCH = [
  ["VpnService + Builder", "API 21", "Creates the local TUN interface; establish() returns the fd the router reads from.", "Full capture means the app must forward every packet itself — ignore a protocol and it dies."],
  ["setUnderlyingNetworks()", "API 22", "Hands the validated networks to the system in preference order (Ethernet → Wi-Fi → cellular).", "Informational only — it never stripes traffic. On API 29+ the declared networks' capabilities are copied onto the VPN, so the list is kept live."],
  ["ConnectivityManager + NetworkCallback", "API 21", "Detects Wi-Fi, cellular and Ethernet/USB-tether add/remove, VALIDATED state and captive portals in real time.", "Callbacks slow down with the screen off; the Doze exemption keeps them prompt."],
  ["Network.bindSocket() + protect()", "API 21", "Pins each flow's upstream socket to exactly one link and keeps it outside the tunnel.", "One socket = one network. A single flow can never span links — that's the platform ceiling."],
  ["Foreground service · specialUse", "API 34", "The declared FGS type for the merge service, with a Play-reviewable use-case property.", "specialUse requires a written use case in Play Console under App content."],
  ["POST_NOTIFICATIONS", "API 33", "Runtime permission gating the persistent status notification on Android 13+.", "If denied the service runs silent; the Quick Settings tile still toggles it."],
  ["REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "API 23", "One-tap Doze exemption so relay threads aren't frozen with the screen off.", "Restricted permission on Play — the battery use case must be justified."],
] as const;

const SAFETY = [
  ["Crash-safe by construction", "onRevoke() and onDestroy() always close the TUN file descriptor, and the kernel immediately hands routing back to the system default network. A crash, a force-stop, or another VPN taking over can never leave the device stranded."],
  ["Never blocks the internet", "Zero validated links — or the user hitting Pause — tears the tunnel down instead of black-holing packets. The metered filter always keeps an escape hatch: if nothing else exists, metered links are used anyway."],
  ["Nothing leaves the device", "No logs, no analytics, no crash reporters, no relay. The only bytes LinkFuse ever sends are your own traffic, on links you chose, terminated locally."],
];

const SETUP = [
  ["Allow notifications", "Let LinkFuse post its status notification — on Android 13 and newer this needs your approval.", "POST_NOTIFICATIONS", "text-wifi"],
  ["Approve the tunnel", "Approve the system VPN dialog so LinkFuse can create its local tunnel interface.", "VpnService.prepare()", "text-cell"],
  ["Skip battery optimization", "Turn off battery optimization so Android doesn't freeze the merge while the screen is off.", "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "text-wire"],
  ["Foreground service", "The merge runs as a foreground service — the persistent notification is what keeps Android from killing it.", "specialUse · API 34", "text-fuse"],
] as const;

const PIPELINE = [
  [I.tun, "TUN capture", "0.0.0.0/0 route pulls every IPv4 packet into the tunnel fd."],
  [I.table, "Flow table", "Each packet is keyed by 5-tuple; existing flows are looked up first."],
  [I.scale, "Weighted picker", "Only brand-new flows roll the dice — Ethernet 4 · Wi-Fi 3 · cellular 2."],
  [I.pin, "Pinned sockets", "protect() + bindSocket() send the flow out one link and keep it there."],
  [I.refresh, "Live rebalance", "Plug or unplug anything; NetworkCallback fires and weights re-normalize."],
] as const;

function useReveal() {
  useEffect(() => {
    const els = document.querySelectorAll(".reveal");
    const io = new IntersectionObserver(
      (entries) => entries.forEach((e) => e.isIntersecting && e.target.classList.add("is-in")),
      { threshold: 0.12 }
    );
    els.forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, []);
}

function Kicker({ children }: { children: React.ReactNode }) {
  return <p className="mb-3 font-mono text-[11px] tracking-[0.28em] text-wifi uppercase">{children}</p>;
}

export default function App() {
  useReveal();

  return (
    <div className="backdrop min-h-screen text-ink">
      <div className="gridlines pointer-events-none fixed inset-0" />

      {/* ---------- header ---------- */}
      <header className="sticky top-0 z-40 border-b border-edge/70 bg-abyss/85 backdrop-blur-sm">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-5 py-3.5">
          <a href="#top" className="flex items-center gap-2.5 text-wifi">
            {I.fuse}
            <span className="font-display text-lg font-bold tracking-wide text-ink">LinkFuse</span>
            <span className="hidden rounded border border-edge2 px-1.5 py-0.5 font-mono text-[10px] text-dim sm:block">v1.0</span>
          </a>
          <nav className="flex items-center gap-5 font-mono text-[12px] text-dim">
            <a className="hidden transition-colors hover:text-wifi sm:block" href="#how">how</a>
            <a className="hidden transition-colors hover:text-wifi sm:block" href="#research">research</a>
            <a className="hidden transition-colors hover:text-wifi md:block" href="#safety">safety</a>
            <a className="hidden transition-colors hover:text-wifi md:block" href="#setup">setup</a>
            <a className="rounded-md border border-edge2 px-2.5 py-1 text-ink transition-colors hover:border-wifi hover:text-wifi" href="#source">
              source ↓
            </a>
          </nav>
        </div>
      </header>

      <main id="top" className="relative mx-auto max-w-6xl px-5">
        {/* ---------- opening ---------- */}
        <section className="grid items-center gap-12 pt-16 pb-20 lg:grid-cols-[1.05fr_1fr] lg:pt-24">
          <div>
            <Kicker>Android 8+ · VpnService · no root · no server</Kicker>
            <h1 className="font-display text-5xl leading-[1.02] font-bold tracking-tight sm:text-6xl">
              Every connection.
              <br />
              <span className="text-fuse">One pipe.</span>
            </h1>
            <p className="mt-6 max-w-lg text-[16.5px] leading-relaxed text-dim">
              LinkFuse bonds Wi-Fi, mobile data and USB tether into a single bonded interface on your phone.
              New flows are spread across links by weight; live flows never jump. When a cable comes out or a
              hotspot dies, traffic rebalances in under a second — and if everything fails, your internet
              simply falls back to normal.
            </p>
            <div className="mt-7 flex flex-wrap gap-2 font-mono text-[11.5px]">
              {["Kotlin + Jetpack Compose", "minSdk 26 → target 35", "foreground service", "Quick Settings tile", "MIT licensed"].map((t) => (
                <span key={t} className="rounded-md border border-edge bg-panel/70 px-2.5 py-1 text-dim">{t}</span>
              ))}
            </div>
            <div className="mt-9 flex flex-wrap items-center gap-4">
              <a href="#source" className="rounded-lg bg-fuse px-6 py-3 font-display text-[15px] font-semibold text-[#1a0e08] transition-transform hover:-translate-y-0.5">
                Browse the full source
              </a>
              <a href="#research" className="font-mono text-[13px] text-dim underline decoration-edge2 underline-offset-4 transition-colors hover:text-wifi">
                read the platform research first →
              </a>
            </div>
          </div>
          <div className="floaty">
            <MergerViz />
          </div>
        </section>

        {/* ---------- pipeline ---------- */}
        <section id="how" className="reveal scroll-mt-24 py-16">
          <Kicker>Inside the tunnel</Kicker>
          <h2 className="font-display text-3xl font-bold sm:text-4xl">How the merge actually works</h2>
          <p className="mt-3 max-w-2xl text-dim">
            Five stages, all on-device. The honest version: Android won't stripe a single TCP connection across
            interfaces — nobody can, without a relay server — so LinkFuse balances at flow granularity instead.
          </p>
          <div className="mt-10 grid gap-3 md:grid-cols-5">
            {PIPELINE.map(([icon, title, body], i) => (
              <div key={title} className="group relative rounded-xl border border-edge bg-panel/60 p-4 transition-all hover:-translate-y-1 hover:border-edge2">
                <div className="mb-3 flex items-center justify-between">
                  <span className="text-wifi transition-colors group-hover:text-fuse">{icon}</span>
                  <span className="font-mono text-[10px] text-faint">0{i + 1}</span>
                </div>
                <div className="font-display text-[15px] font-semibold">{title}</div>
                <p className="mt-1.5 text-[12.5px] leading-relaxed text-dim">{body}</p>
              </div>
            ))}
          </div>
          <div className="mt-4 rounded-xl border border-edge border-l-2 border-l-cell bg-panel/40 p-4 text-[13px] leading-relaxed text-dim">
            <span className="font-semibold text-cell">The TCP reality check.</span> A full-capture TUN also receives TCP,
            and Android offers no raw sockets — so <code className="font-mono text-ink">TcpProxy</code> is a minimal
            userspace TCP endpoint (the NetGuard pattern): it answers the handshake and opens a normal socket to the
            real server on the chosen link. In-order only, fixed window; anything exotic gets a clean RST so apps
            retry instead of hanging.
          </div>
        </section>

        {/* ---------- research ---------- */}
        <section id="research" className="reveal scroll-mt-24 py-16">
          <Kicker>Verified against developer.android.com</Kicker>
          <h2 className="font-display text-3xl font-bold sm:text-4xl">APIs used — and the limits found</h2>
          <div className="mt-8 overflow-hidden rounded-xl border border-edge">
            {RESEARCH.map(([api, level, role, limit], i) => (
              <div key={api} className={`grid gap-2 p-4 transition-colors hover:bg-panel/50 md:grid-cols-[240px_1fr] md:gap-6 ${i % 2 ? "bg-panel/30" : "bg-deep/60"}`}>
                <div>
                  <div className="font-mono text-[13px] text-wifi">{api}</div>
                  <span className="mt-1 inline-block rounded border border-edge2 px-1.5 py-0.5 font-mono text-[10px] text-dim">{level}</span>
                </div>
                <div className="text-[13px] leading-relaxed">
                  <p className="text-ink/90">{role}</p>
                  <p className="mt-1 text-dim"><span className="font-semibold text-cell">Limit:</span> {limit}</p>
                </div>
              </div>
            ))}
          </div>
          <p className="mt-4 font-mono text-[11.5px] text-faint">
            Bottom line: bandwidth aggregation of one TCP stream is impossible serverless on Android. Per-flow
            bonding + instant failover is the platform ceiling — and it's what LinkFuse ships.
          </p>
        </section>

        {/* ---------- safety ---------- */}
        <section id="safety" className="reveal scroll-mt-24 py-16">
          <Kicker>Non-negotiables</Kicker>
          <h2 className="font-display text-3xl font-bold sm:text-4xl">Safety guarantees</h2>
          <div className="mt-8 space-y-3">
            {SAFETY.map(([title, body], i) => (
              <div key={title} className="flex gap-5 rounded-xl border border-edge bg-panel/50 p-5 transition-colors hover:border-edge2">
                <span className="font-display text-2xl font-bold text-fuse/80">0{i + 1}</span>
                <div>
                  <div className="font-display text-[17px] font-semibold">{title}</div>
                  <p className="mt-1 max-w-3xl text-[13.5px] leading-relaxed text-dim">{body}</p>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* ---------- setup ---------- */}
        <section id="setup" className="reveal scroll-mt-24 py-16">
          <Kicker>First run, four taps</Kicker>
          <h2 className="font-display text-3xl font-bold sm:text-4xl">The onboarding walk-through</h2>
          <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {SETUP.map(([title, body, tag, color], i) => (
              <div key={title} className="rounded-xl border border-edge bg-panel/60 p-5 transition-all hover:-translate-y-1 hover:border-edge2">
                <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg border border-edge2 font-display text-[15px] font-bold text-ink">
                  {i + 1}
                </div>
                <div className="font-display text-[15.5px] font-semibold">{title}</div>
                <p className="mt-1.5 text-[12.5px] leading-relaxed text-dim">{body}</p>
                <div className={`mt-3 font-mono text-[10px] ${color}`}>{tag}</div>
              </div>
            ))}
          </div>
        </section>

        {/* ---------- source ---------- */}
        <section id="source" className="reveal scroll-mt-24 py-16">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <Kicker>The whole project</Kicker>
              <h2 className="font-display text-3xl font-bold sm:text-4xl">Full source, file by file</h2>
            </div>
            <span className="rounded-md border border-edge bg-panel px-3 py-1.5 font-mono text-[11.5px] text-dim">
              {FILE_COUNT} files · Gradle + manifest + Compose UI + VPN engine
            </span>
          </div>
          <p className="mt-3 max-w-2xl text-dim">
            This is the exact tree you build — Gradle setup, AndroidManifest, resources, the VPN engine and the
            Compose UI. Pick any file, read it, copy it.
          </p>
          <div className="mt-6">
            <FileExplorer />
          </div>
        </section>

        {/* ---------- build ---------- */}
        <section id="build" className="reveal scroll-mt-24 py-16">
          <Kicker>Ship it</Kicker>
          <h2 className="font-display text-3xl font-bold sm:text-4xl">Build &amp; install</h2>
          <div className="mt-8 grid gap-4 lg:grid-cols-2">
            <div className="rounded-xl border border-edge bg-deep p-5">
              <div className="mb-3 font-mono text-[11px] tracking-widest text-wire uppercase">sideload · signed APK</div>
              <pre className="code-scroll overflow-x-auto font-mono text-[12.5px] leading-relaxed text-ink/90">{`cd android
gradle wrapper --gradle-version 8.9   # once
./gradlew :app:assembleRelease

apksigner sign --ks release.jks \\
  --out linkfuse-signed.apk \\
  app/build/outputs/apk/release/\\
    app-release-unsigned.apk

adb install linkfuse-signed.apk`}</pre>
            </div>
            <div className="rounded-xl border border-edge bg-deep p-5">
              <div className="mb-3 font-mono text-[11px] tracking-widest text-cell uppercase">play store · AAB</div>
              <pre className="code-scroll overflow-x-auto font-mono text-[12.5px] leading-relaxed text-ink/90">{`./gradlew :app:bundleRelease

# Play Console → App content:
#  · declare the specialUse FGS use case
#    (already written in the manifest property)
#  · justify REQUEST_IGNORE_BATTERY_
#    OPTIMIZATIONS as keeping the merge
#    alive while the screen is off`}</pre>
            </div>
          </div>
          <p className="mt-4 text-[13px] text-dim">
            Requires JDK 17 and Android SDK 35. Opening <code className="font-mono text-ink">android/</code> in
            Android Studio generates the wrapper automatically. The debug build is installable as-is for testing.
          </p>
        </section>
      </main>

      <footer className="relative mt-8 border-t border-edge/70">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-5 py-8 font-mono text-[11.5px] text-faint">
          <span className="flex items-center gap-2 text-dim">{I.fuse} LinkFuse — bond every link, trust none of them.</span>
          <span>Kotlin · Compose · VpnService · MIT</span>
        </div>
      </footer>
    </div>
  );
}
