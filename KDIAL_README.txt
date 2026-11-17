Upload all 9 files from this folder together to the ROOT of the GitHub repository (not the ZIP). Then in Codespaces run:
  cd /workspaces/4X4-Android-Dialer
  git fetch origin main
  git show origin/main:KDIAL_INSTALL.sh > /tmp/KDIAL_INSTALL.sh
  bash /tmp/KDIAL_INSTALL.sh
The script backs up overwritten files and deletes only temporary KDIAL_* files. It does not automatically push or delete unrelated project files.
