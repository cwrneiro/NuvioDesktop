# nuvio-focus — per-app X focus retrigger for Nuvio (see nuvio-focus.c for the
# root cause and invariants). Built here rather than pulled from nixpkgs: it is
# a ~60-line X11 program we own, scoped to one app's launch path (V1.2), not a
# platform pattern. Nuvio's upstream fix would be a Window.toFront() call after
# setVisible(true) (AWT is Globally Active by design; no -D property exists).
{ lib, stdenv, libX11, xorgproto }:

stdenv.mkDerivation {
  pname = "nuvio-focus";
  version = "0.1.0";

  src = ./nuvio-focus.c;

  buildInputs = [ libX11 xorgproto ];

  dontUnpack = true;

  buildPhase = ''
    runHook preBuild
    $CC -O2 -o nuvio-focus $src -lX11
    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall
    install -Dm755 nuvio-focus $out/bin/nuvio-focus
    runHook postInstall
  '';

  meta = {
    description = "Re-send WM_TAKE_FOCUS to the Nuvio toplevel (AWT focus proxy retrigger)";
    platforms = lib.platforms.linux;
    mainProgram = "nuvio-focus";
  };
}
