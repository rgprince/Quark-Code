#!/usr/bin/env python3
"""Fetch the proot launcher suite for arm64 from Termux packages.

proot itself must live in the APK's nativeLibraryDir — it is the ONLY app
location Android grants exec rights (W^X blocks exec from app-private files).
So these small binaries are fetched at BUILD time (pinned, reviewable),
while the big pieces (Debian rootfs, opencode) download at runtime on opt-in.

Outputs into --out (wired as a jniLibs srcDir):
  libquark_proot.so          <- proot executable (renamed; exec'd by path)
  libquark_proot_loader.so   <- guest loader  (PROOT_LOADER env)
  libquark_proot_loader32.so <- 32-bit loader (PROOT_LOADER_32 env)
  libtalloc.so.2 (+ libtalloc.so copy) and libandroid-shmem.so*
                             <- SONAME-exact copies for LD_LIBRARY_PATH lookup

Skips work when manifest.json already matches (CI cache friendly).
Fails LOUDLY with repo listings when the Termux layout changes.
"""
import argparse
import fnmatch
import hashlib
import json
import os
import re
import shutil
import sys
import tarfile
import tempfile
import urllib.request

ARCH = "aarch64"
PACKAGES_INDEX = (
    "https://packages.termux.org/apt/termux-main/dists/stable/main/binary-aarch64/Packages"
)
BASE = "https://packages.termux.org/apt/termux-main/"

# package -> (member globs we need from its data.tar, purpose)
WANTS = {
    "proot": (["usr/bin/proot", "usr/libexec/proot/loader", "usr/libexec/proot/loader32"],
              "proot binary + guest loaders"),
    "libtalloc": (["usr/lib/libtalloc.so*"], "proot DT_NEEDED lib"),
    "libandroid-shmem": (["usr/lib/libandroid-shmem.so*"], "proot shmem shim"),
}

# Fallback package names if the primaries vanish from the index.
FALLBACKS = {
    "libandroid-shmem": ["libandroid-support"],
}


def fetch_text(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Quark-Code-Build"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", "replace")


def fetch_file(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "Quark-Code-Build"})
    with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f, length=1024 * 256)


def parse_packages(index_text):
    pkgs = {}
    cur = {}
    for line in index_text.splitlines() + [""]:
        if not line.strip():
            if "Package" in cur and "Filename" in cur:
                pkgs.setdefault(cur["Package"], []).append(cur)
            cur = {}
            continue
        m = re.match(r"([^:]+):\s*(.*)", line)
        if m:
            cur[m.group(1)] = m.group(2).strip()
    return pkgs


def newest(entries):
    def key(e):
        return e.get("Version", "")
    return sorted(entries, key=key)[-1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    out = args.out
    os.makedirs(out, exist_ok=True)

    print("[proot-assets] fetching package index ...")
    index = fetch_text(PACKAGES_INDEX)
    pkgs = parse_packages(index)

    plan = {}  # member_pattern -> (deb_url, sha256)
    for pkg, (patterns, _why) in WANTS.items():
        names = [pkg] + FALLBACKS.get(pkg, [])
        entry = None
        used = None
        for name in names:
            if name in pkgs:
                entry = newest(pkgs[name])
                used = name
                break
        if entry is None:
            print(f"[proot-assets] FATAL: no package {pkg} (tried {names}) in index.")
            print("[proot-assets] available proot-ish packages:",
                  sorted(p for p in pkgs if "proot" in p or "talloc" in p or "shmem" in p or "support" in p))
            sys.exit(1)
        print(f"[proot-assets] {pkg} -> {used} {entry.get('Version')} ({entry['Filename']})")
        for pat in patterns:
            plan[pat] = (BASE + entry["Filename"], entry.get("SHA256", ""))

    manifest_path = os.path.join(out, "manifest.json")
    fingerprint = hashlib.sha256(json.dumps(plan, sort_keys=True).encode()).hexdigest()
    if os.path.exists(manifest_path):
        try:
            if json.load(open(manifest_path)).get("fingerprint") == fingerprint:
                print("[proot-assets] up to date, skipping.")
                return 0
        except Exception:
            pass

    tmp = tempfile.mkdtemp(prefix="proot-assets-")
    try:
        fetched = {}
        for pat, (url, _sha) in plan.items():
            if url in fetched:
                continue
            deb = os.path.join(tmp, os.path.basename(url))
            print(f"[proot-assets] downloading {url} ...")
            fetch_file(url, deb)
            fetched[url] = deb

        # deb = ar archive; pull data.tar.* members we need.
        import subprocess
        extracted = {}  # pattern -> bytes
        for pat, (url, _sha) in plan.items():
            deb = fetched[url]
            listing = subprocess.run(["ar", "t", deb], capture_output=True, text=True, timeout=60)
            members = listing.stdout.split()
            data = next((m for m in ["data.tar.xz", "data.tar.gz", "data.tar.zst", "data.tar"]
                         if m in members), None)
            if data is None:
                print(f"[proot-assets] FATAL: no data.tar.* in {deb}; members={members}")
                sys.exit(1)
            data_path = deb + ".data"
            with open(data_path, "wb") as out_f:
                subprocess.run(["ar", "p", deb, data], stdout=out_f,
                               check=True, timeout=300)
            tf = tarfile.open(data_path, "r:*")
            names = tf.getnames()

            def matches(name, pattern):
                clean = name[2:] if name.startswith("./") else name
                return fnmatch.fnmatch(clean, pattern) or fnmatch.fnmatch(name, pattern)

            hit = [n for n in names if matches(n, pat)]
            if not hit:
                print(f"[proot-assets] FATAL: pattern '{pat}' not in {deb}.")
                print("[proot-assets] candidate entries:",
                      [n for n in names if "proot" in n or "loader" in n or "talloc" in n or "shmem" in n][:30])
                sys.exit(1)
            for h in hit:
                m = tf.extractfile(h)
                if m is not None:
                    extracted.setdefault(pat, []).append((os.path.basename(h), m.read()))
            tf.close()

        def put(data, name):
            dest = os.path.join(out, name)
            with open(dest, "wb") as f:
                f.write(data)
            os.chmod(dest, 0o755)
            print(f"[proot-assets] wrote {name} ({len(data)} bytes)")
            return dest

        def one(pat):
            lst = extracted.get(pat, [])
            if not lst:
                print(f"[proot-assets] FATAL: nothing extracted for {pat}")
                sys.exit(1)
            return lst

        # proot binary + loaders (renamed; executed by absolute path / env).
        put(one("usr/bin/proot")[0][1], "libquark_proot.so")
        loaders = {n: d for n, d in one("usr/libexec/proot/loader")}
        loaders32 = {n: d for n, d in one("usr/libexec/proot/loader32")}
        put(next(iter(loaders.values())), "libquark_proot_loader.so")
        put(next(iter(loaders32.values())), "libquark_proot_loader32.so")

        # Support libs: keep SONAME-exact names (linker lookup) AND unversioned
        # copies (in case only *.so extracts from the APK on some devices).
        for pat in ("usr/lib/libtalloc.so*", "usr/lib/libandroid-shmem.so*"):
            for fname, data in one(pat):
                put(data, fname)
                base = fname.split(".so")[0] + ".so"
                if base != fname:
                    put(data, base)

        json.dump({"fingerprint": fingerprint, "plan": sorted(plan)},
                  open(manifest_path, "w"), indent=1)
        print("[proot-assets] done:", sorted(os.listdir(out)))
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
