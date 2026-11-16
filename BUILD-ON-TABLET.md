# Build on Samsung tablet using GitHub Codespaces + Actions

1. Create a new **private** GitHub repository (no README) at https://github.com/new.
2. From the repository, open **Code > Codespaces > Create codespace on main**. Use Chrome desktop site if controls are hidden.
3. In the Codespace web editor, upload the ZIP archive to the workspace using Explorer > ... > Upload Files (or drag and drop). Open terminal using menu > Terminal > New Terminal.
4. Run these commands in terminal (replace the ZIP filename if different):

```bash
unzip -o 4X4-Android-Dialer-GitHub.zip
cp -a 4X4-Android-Dialer/. .
rm -rf 4X4-Android-Dialer 4X4-Android-Dialer-GitHub.zip
git add .
git commit -m 'Add Android dialer and APK build workflow'
git push
```

5. Open the GitHub repository > Actions > Build Android APK > Run workflow (if push did not start it already).
6. Wait for green build; open the workflow run and download artifact `4X4-Dialer-APK` (ZIP), extract `app-debug.apk`, install on tablet.

This is a *test* APK; HFP audio/SCO is not implemented, and dialing is not yet validated on this tablet. Android may block RFCOMM HFP access. If build fails, share the GitHub Actions log.
