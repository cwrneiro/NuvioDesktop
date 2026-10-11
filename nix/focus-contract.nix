# focus-contract — journal -> pass/fail checker for the fork's
# `nuvio-nav:` / `nuvio-focus:` diagnostics (source: nix/focus-contract.sh).
#
# Consumes a couch/navigation journal and enforces the focus invariants the
# couch passes check by hand. Wrapped so its coreutils/grep/sed/awk/gawk
# runtime tools are on PATH.
{ lib
, stdenv
, makeWrapper
, coreutils
, gnugrep
, gnused
, gawk
, findutils
}:

stdenv.mkDerivation {
  pname = "focus-contract";
  version = "0.1.0";

  src = ./focus-contract.sh;

  dontUnpack = true;
  nativeBuildInputs = [ makeWrapper ];

  installPhase = ''
    runHook preInstall
    install -Dm755 $src $out/bin/focus-contract
    patchShebangs $out/bin/focus-contract
    wrapProgram $out/bin/focus-contract \
      --prefix PATH : ${lib.makeBinPath [ coreutils gnugrep gnused gawk findutils ]}
    runHook postInstall
  '';

  meta = {
    description = "Nuvio focus/navigation journal contract checker";
    platforms = lib.platforms.linux;
    mainProgram = "focus-contract";
  };
}
