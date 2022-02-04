{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, utils }:
    utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { config = { allowUnfree = true; }; inherit system; };
      in {

        devShell = with pkgs; mkShell {
          buildInputs = [ android-studio ];
        };
      });

}
