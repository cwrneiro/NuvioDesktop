# Nuvio Desktop (fork) — Gradle source build.
#
# Carried by the cwrneirOS NuvioDesktop fork, branch cwrn-focus-nav. Derived
# from nixpkgs PR #543092 ("nuvio-desktop: init at 0.1.26-alpha", griffi-gh),
# the reference Gradle source packaging.
#
# The fork branch = upstream NuvioMedia/NuvioDesktop Dev @ d4bdf258
# (post-0.1.27; includes merged PR #791 back handling) + the cwrneirOS
# controller/focus-navigation work applied as commits. The original per-round
# patch files are archived under docs/patch-history/.
#
# This expression builds the checked-out fork tree directly: the flake passes
# `self.outPath` as `src`. Through Dev d4bdf258 the only Gradle dependency
# change since 0.1.26 is `jna` (5.19.1), which deps.json already contains, so
# the dependency graph is covered.
#
# If upstream lands #180, drop this package and bump the AppImage wrap
# (nix/nuvio.nix) instead — less maintenance.
{
  lib,
  stdenv,
  src,
  fetchurl,
  gradle_9,
  jdk17_headless,
  jre25_minimal,
  autoPatchelfHook,
  cmake,
  imagemagick,
  makeBinaryWrapper,
  pkg-config,
  wrapGAppsHook3,
  fontconfig,
  glib-networking,
  gst_all_1,
  gtk3,
  libGL,
  libgbm,
  libx11,
  libxcomposite,
  libxext,
  mpv-unwrapped,
  webkitgtk_4_1,
  torrserver,
  xdg-utils,
}:
let
  gradle = gradle_9;

  jre = jre25_minimal.override {
    modules = [
      "java.base"
      "java.datatransfer"
      "java.desktop"
      "java.instrument"
      "java.logging"
      "java.management"
      "java.net.http"
      "java.prefs"
      "java.xml"
      "jdk.crypto.ec"
      "jdk.httpserver"
      "jdk.unsupported"
    ];
  };

  # logging implementation
  slf4j-simple = fetchurl {
    url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.1.0-alpha1/slf4j-simple-2.1.0-alpha1.jar";
    hash = "sha256-AU/trHoyKI7W+PcqEAfn+zKuxb/tsnFGfkluCVNIL3U=";
  };
in
stdenv.mkDerivation (finalAttrs: {
  pname = "nuvio-desktop";
  version = "0.1.27-alpha";

  __structuredAttrs = true;
  strictDeps = true;

  # `src` is the fork tree, passed by the flake (self.outPath).
  # The former `patches = [ ... ];` list is now this branch's commit
  # history; the original per-round patch files live in docs/patch-history/.
  inherit src;

  postPatch = ''
    substituteInPlace composeApp/src/desktopMain/kotlin/com/nuvio/app/core/build/AppFeaturePolicy.desktop.kt \
      --replace-fail 'inAppUpdaterEnabled: Boolean = true' 'inAppUpdaterEnabled: Boolean = false' \
      --replace-fail 'externalPlayerSupported: Boolean = false' 'externalPlayerSupported: Boolean = true'

    substituteInPlace scripts/linux/linux-shortcut-definition.sh \
      --replace-fail "usr/share/applications/" "share/applications/";

    # remove vendored binaries not used by the Linux build (Dev layout:
    # torrserver no longer ships in-tree; engine dylibs/dlls moved to
    # native/<os>/engine)
    rm -rf composeApp/src/desktopMain/torrserver
    rm -rf composeApp/src/desktopMain/native/macos/engine
    rm -rf composeApp/src/desktopMain/native/windows/engine

    # set gradle props
    cp --no-preserve=mode ${./gradle-local.properties} local.properties
  '';

  nativeBuildInputs = [
    gradle
    autoPatchelfHook
    cmake
    imagemagick
    makeBinaryWrapper
    pkg-config
    wrapGAppsHook3
  ];

  buildInputs = [
    fontconfig
    glib-networking
    gst_all_1.gstreamer
    gst_all_1.gst-plugins-base
    gst_all_1.gst-plugins-good
    gst_all_1.gst-libav
    gtk3
    libGL
    libgbm
    libx11
    libxcomposite
    libxext
    mpv-unwrapped
    webkitgtk_4_1
  ];

  dontUseCmakeConfigure = true;
  env.JAVA_HOME = gradle.jdk.home;

  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./deps.json;
  };
  __darwinAllowLocalNetworking = true;

  gradleBuildTask = ":composeApp:createReleaseDistributable";
  gradleUpdateTask = "${finalAttrs.gradleBuildTask} -x :composeMediaPlayer:buildNativeLinux";
  gradleFlags = [
    "-Dfile.encoding=utf-8"
    "--no-configuration-cache" # https://github.com/NixOS/nixpkgs/issues/381969
    "-Pkotlin.native.ignoreDisabledTargets=true"
    "-Pkotlin.native.enableKlibsCrossCompilation=false"
    "-Dorg.gradle.java.home=${jdk17_headless.home}"
  ];

  dontWrapGApps = true;

  installPhase = ''
    runHook preInstall

    mkdir -p $out/{bin,lib,share/{applications,metainfo,icons}}

    appDir=composeApp/build/compose/binaries/main-release/app/Nuvio/lib
    cp -r $appDir/app $out/lib/

    install -Dm644 ${slf4j-simple} $out/lib/app/slf4j-simple.jar
    install -Dm644 scripts/linux/com.nuvio.media.desktop.metainfo.xml $out/share/metainfo/com.nuvio.media.desktop.metainfo.xml

    source scripts/linux/linux-shortcut-definition.sh
    nuvio_linux_write_desktop_entry_file \
      "$out/share/applications/com.nuvio.media.desktop.desktop" \
      "nuvio %u" \
      "com.nuvio.media.desktop"

    # icon has unusual resolution
    for size in 48 64 128 256 512; do
      mkdir -p $out/share/icons/hicolor/''${size}x''${size}/apps
      magick $appDir/Nuvio.png -resize ''${size}x''${size} \
        $out/share/icons/hicolor/''${size}x''${size}/apps/com.nuvio.media.desktop.png
    done

    # compose.desktop.application.jvmArgs
    makeBinaryWrapper ${jre}/bin/java $out/bin/nuvio \
      --inherit-argv0 \
      --set _JAVA_AWT_WM_NONREPARENTING 1 \
      --set JAVA_HOME ${jre.home} \
      --suffix PATH : ${lib.makeBinPath [ xdg-utils ]} \
      "''${gappsWrapperArgs[@]}" \
      --add-flags "-Djava.library.path=${lib.makeLibraryPath finalAttrs.buildInputs}" \
      --add-flags "-Djpackage.app-version=${finalAttrs.version}" \
      --add-flags "-Dcompose.application.resources.dir=$out/lib/app/resources" \
      --add-flags "-Dcompose.application.configure.swing.globals=true" \
      --add-flags "-Dskiko.library.path=$out/lib/app" \
      --add-flags "-Djdk.gtk.version=0" \
      --add-flags "-Dnuvio.torrserver.binary=${lib.getExe' torrserver "torrserver"}" \
      --add-flags "--add-opens=java.desktop/java.awt=ALL-UNNAMED" \
      --add-flags "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED" \
      --add-flags "--enable-native-access=ALL-UNNAMED" \
      --add-flag "-cp" \
      --add-flag "$out/lib/app/*" \
      --add-flag "com.nuvio.app.MainKt"

    runHook postInstall
  '';


  meta = {
    description = "Discover movies and shows, save favorites and watch from your own library (source build with arrow-navigation patch)";
    longDescription = ''
      Discover movies and shows, save favorites and Watch from your own library.

      Nuvio is a modern media organizer for movies and TV shows.

      Discover titles, build your own library, track progress, and keep your watch history in sync with Trakt in a clean interface designed for large screens.

      This build carries the rebased upstream arrow-navigation patch (PR #180)
      for gamepad D-pad navigation in the cwrneirOS TV appliance.
    '';
    homepage = "https://github.com/NuvioMedia/NuvioDesktop";
    changelog = "https://github.com/NuvioMedia/NuvioDesktop/releases/tag/${finalAttrs.version}";
    license = lib.licenses.gpl3Only;
    sourceProvenance = with lib.sourceTypes; [
      fromSource
      binaryBytecode # gradle dependencies, slf4j-simple
      binaryNativeCode # some deps bundle native libraries
    ];
    maintainers = with lib.maintainers; [
      griffi-gh
    ];
    platforms = lib.platforms.linux; # TODO: darwin
    mainProgram = "nuvio";
  };
})
