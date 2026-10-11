{
  description = "NuvioDesktop fork — cwrneirOS TV-shell controller/focus navigation";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f system);

      # Consumer-facing overlay. Resolves dependencies against the *consumer's*
      # nixpkgs (`prev`), so cwrneirOS keeps a single nixpkgs pin.
      overlay = final: prev: {
        nuvio-src = prev.callPackage ./nix/nuvio-src.nix { src = self.outPath; };
        nuvio = prev.callPackage ./nix/nuvio.nix { };
        nuvio-focus = prev.callPackage ./nix/nuvio-focus.nix { };
        focus-contract = prev.callPackage ./nix/focus-contract.nix { };
      };
    in
    {
      overlays.default = overlay;

      packages = forAllSystems (system:
        let
          pkgs = import nixpkgs { inherit system; overlays = [ overlay ]; };
        in
        {
          inherit (pkgs) nuvio nuvio-src nuvio-focus focus-contract;
          default = pkgs.nuvio-src;
        });

      checks = forAllSystems (system:
        let
          pkgs = import nixpkgs { inherit system; overlays = [ overlay ]; };
        in
        {
          # Cheap, non-Gradle checks for `nix flake check`.
          inherit (pkgs) nuvio-focus focus-contract;
        });
    };
}
