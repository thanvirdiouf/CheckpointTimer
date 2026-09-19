{
  description = "Android app development shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        buildToolsVersion = "35.0.0";

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" ];
          buildToolsVersions = [ buildToolsVersion "35.0.0" ];
          includeEmulator = true;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis" ];
          abiVersions = [ "x86_64" ];
          includeNDK = false;
        };

        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            androidSdk
            pkgs.jdk17
            pkgs.gradle
            pkgs.kotlin
            pkgs.claude-code
          ];

          JAVA_HOME = "${pkgs.jdk17}";
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;

          GRADLE_OPTS =
            "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/${buildToolsVersion}/aapt2";

          # --- Non-secret DeepSeek / Claude Code configuration ---
          # These are safe to keep in the flake: they contain no credentials.
          ANTHROPIC_BASE_URL = "https://api.deepseek.com/anthropic";
          ANTHROPIC_MODEL = "deepseek-flash[1m]";
          ANTHROPIC_DEFAULT_OPUS_MODEL = "deepseek-flash[1m]";
          ANTHROPIC_DEFAULT_SONNET_MODEL = "deepseek-flash[1m]";
          ANTHROPIC_DEFAULT_HAIKU_MODEL = "deepseek-flash";
          CLAUDE_CODE_SUBAGENT_MODEL = "deepseek-flash";
          CLAUDE_CODE_EFFORT_LEVEL = "max";
          CLAUDE_CODE_AUTO_COMPACT_WINDOW = "786432";

          shellHook = ''
            # Load the secret token from a gitignored .env file at the project root.
            # `set -a` exports every variable that gets sourced.
            if [ -f "$PWD/.env" ]; then
              set -a
              . "$PWD/.env"
              set +a
            fi

            if [ -z "$ANTHROPIC_AUTH_TOKEN" ]; then
              echo "warning: ANTHROPIC_AUTH_TOKEN is unset; put it in $PWD/.env" >&2
            fi

            echo "Android devshell ready"
            echo "  ANDROID_HOME = $ANDROID_HOME"
            echo "  java         = $(java -version 2>&1 | head -n1)"
            echo "  claude       = $(claude --version 2>/dev/null || echo 'not found')"
            echo "  model        = $ANTHROPIC_MODEL"
            echo "  endpoint     = $ANTHROPIC_BASE_URL"
          '';
        };
      });
}
