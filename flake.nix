{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    treefmt-nix.url = "github:numtide/treefmt-nix";
  };

  outputs = inputs @ { flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" "aarch64-darwin" ];
      imports = [
        inputs.treefmt-nix.flakeModule
      ];
      perSystem = {
        pkgs,
        system,
        ...
      }: let
        overlay = final: prev: let
          jdk = prev.graalvmPackages.graalvm-ce;
          nodejs = prev.nodejs_24;
          clojure = prev.clojure.override {inherit jdk;};
          pnpm = prev.pnpm_10.override {inherit nodejs;};
        in {
          inherit jdk nodejs clojure pnpm;
        };
        pkgs = import inputs.nixpkgs {
          inherit system;
          overlays = [overlay];
        };
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            jdk
            clojure
            nodejs
            pnpm
          ];
        };
        treefmt.config = {
          projectRootFile = "flake.nix";
          programs.nixpkgs-fmt.enable = true;
        };
      };
    };
}
