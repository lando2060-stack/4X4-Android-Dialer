#!/usr/bin/env bash
set -Eeuo pipefail
cd /workspaces/4X4-Android-Dialer
ROOT="$PWD"
BACKUP="$ROOT/.kdial-backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
# Files uploaded on GitHub may not yet be present in this Codespace.
git fetch origin main
files=(
  KDIAL_MainActivity.java KDIAL_index.html KDIAL_AndroidManifest.xml
  KDIAL_app_build.gradle.kts KDIAL_root_build.gradle.kts
  KDIAL_settings.gradle.kts KDIAL_build-apk.yml
)
for file in "${files[@]}"; do
  if [[ ! -f "$ROOT/$file" ]]; then
    git show "origin/main:$file" > "$ROOT/$file" || { echo "Missing uploaded file: $file"; exit 1; }
  fi
done
pairs=(
  'KDIAL_MainActivity.java|app/src/main/java/com/example/fourxfourdialer/MainActivity.java'
  'KDIAL_index.html|app/src/main/assets/index.html'
  'KDIAL_AndroidManifest.xml|app/src/main/AndroidManifest.xml'
  'KDIAL_app_build.gradle.kts|app/build.gradle.kts'
  'KDIAL_root_build.gradle.kts|build.gradle.kts'
  'KDIAL_settings.gradle.kts|settings.gradle.kts'
  'KDIAL_build-apk.yml|.github/workflows/build-apk.yml'
)
for pair in "${pairs[@]}"; do
  src="${pair%%|*}"; dest="${pair#*|}"
  if [[ -f "$dest" ]]; then
    mkdir -p "$BACKUP/$(dirname "$dest")"
    cp -p "$dest" "$BACKUP/$dest"
  fi
  mkdir -p "$(dirname "$dest")"
  cp "$src" "$dest"
done
# Delete only temporary files introduced by this upload; never other project files.
rm -f -- "${files[@]}"
printf '\nDONE. Original files backed up at: %s\n' "$BACKUP"
printf 'Review changes with: git status --short && git diff --stat\n'
printf 'Do not push yet if your local branch has diverged.\n'
