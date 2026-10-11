# Nuvio Desktop — wrapped upstream type-2 AppImage (ADR 0001 §4).
#
# Nuvio is Kotlin Multiplatform + Compose Desktop (Skiko), NOT Electron: its
# window is an X11/XWayland toplevel and the Linux AppImage links the SYSTEM
# libmpv (libmpv.so.2) + webkit2gtk for its native bridge — neither is bundled
# in the AppImage. Pin the release tag: upstream is active alpha and explicitly
# warns about breaking changes between versions. See
# docs/adr/0001-tv-shell-session.md §4 and the packaging research (2026-10-05):
# nixpkgs PR #543092 (tested wrapType2 comment, nineluj) is the reference.
{ appimageTools
, fetchurl
, lib
, makeWrapper
, cairo
, glib
, glib-networking
, gtk3
, libX11
, libXcomposite
, libXext
, libGL
, libgbm
, mpv
, stdenv
, webkitgtk_4_1
}:

let
  pname = "nuvio";
  version = "0.1.27-alpha";
  src = fetchurl {
    url = "https://github.com/NuvioMedia/NuvioDesktop/releases/download/${version}/Nuvio-Linux-x86_64-${version}.AppImage";
    hash = "sha256-IcgnAGXs6MNoJ++cR2WhkqFFY3yVDx/UGOTzr3VjLKI=";
  };
in
appimageTools.wrapType2 {
  inherit pname version src;

  nativeBuildInputs = [ makeWrapper ];

  # _JAVA_AWT_WM_NONREPARENTING=1 is REQUIRED on sway/i3-class WMs (window
  # sizing) — confirmed by the nixpkgs reference wrapper. libmpv.so.2 +
  # webkit2gtk come from the system; GL/GBM for Skiko.
  extraInstallCommands = ''
    wrapProgram $out/bin/${pname} \
      --set _JAVA_AWT_WM_NONREPARENTING 1 \
      --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath [
        cairo glib glib-networking gtk3 libX11 libXcomposite libXext libGL libgbm mpv stdenv.cc.cc webkitgtk_4_1
      ]} \
      --set GIO_EXTRA_MODULES "${glib-networking}/lib/gio/modules"
  '';

  meta = {
    description = "Nuvio media streaming desktop client (wrapped upstream AppImage)";
    homepage = "https://github.com/NuvioMedia/NuvioDesktop";
    license = lib.licenses.gpl3Only;
    mainProgram = pname;
    platforms = [ "x86_64-linux" ];
    sourceProvenance = [ lib.sourceTypes.binaryNativeCode ];
  };
}
