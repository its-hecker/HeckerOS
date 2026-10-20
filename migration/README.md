HeckerOS platform ownership

The manifest has 91 PixelOS-derived platform projects: 84 on GitHub and seven
on GitLab. Settings, frameworks/base, vendor/custom and vendor/lineage have
verified HeckerOS forks and independent `heckeros-17` branches. The remaining
87 repositories must exist and be verified before the full manifest is switched.
This checkpoint is not a completed standalone source migration.

The connected GitHub API can edit repositories but cannot create/fork them.
The browser session timed out during further repository creation. The helper
below completes the remaining work using GitHub CLI authentication on a machine
with access to GitHub and GitLab. Do not paste access tokens into chat.

From a current HeckerOS manifest checkout, with git, Python 3, gh and git-lfs:

```
gh auth login
gh auth setup-git
python3 tools/migrate_platform.py
python3 tools/migrate_platform.py --provision
python3 tools/migrate_platform.py --activate --local-manifest ../HeckerOs-localmanifest
```

Omit `--local-manifest` only if there is no sibling checkout; the bundled
integration manifest still updates. The seven GitLab mirrors include prebuilt
payload repositories and can require substantial disk space/network transfer.
LFS payloads are copied before their branches are pushed. The helper preserves
existing owned branches and does not force-push or sync them from upstream.
It freezes upstream source commits on the first provisioning pass, so rerunning
does not silently import newer PixelOS changes.

Activation first verifies all 91 destinations and reviewed commit ancestry. It
then creates snippets/heckeros.xml, updates default.xml, and updates the native
integration/local manifest repository names and pins. Review and commit the
resulting changes in both repositories. Existing build/make linkfile records
and immutable core snapshots are preserved. Before-edit copies are placed in
out/platform-migration for recovery.

After migration, PixelOS changes are available to review/cherry-pick or merge.
They do not automatically reach HeckerOS or installed phones. Release updates
still require a new build, testing, signing and an OTA package.
