#!/usr/bin/env bash
#
# apply_fixes.sh
#
# Copies every file from ./fixed/ into the matching path in your project,
# backing up anything it overwrites first. Run this from your PROJECT ROOT
# (the folder that directly contains "app/"), with the "fixed" folder
# extracted alongside it.
#
# Usage:
#   1. Extract fixed-files-only.zip so you have a "fixed" folder sitting
#      next to your project's "app" folder.
#   2. Copy this script next to both of them (project root).
#   3. Run:  bash apply_fixes.sh
#
# Safety:
#   - Every file this script overwrites gets backed up first into
#     ./fix_backup_<timestamp>/ using the same relative path, so nothing
#     is ever lost.
#   - This script only COPIES/OVERWRITES files. It never deletes anything.
#     A short list of files you should manually delete (because a diff
#     can't represent deletions) is printed at the end - delete those
#     yourself once you've confirmed the build works.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
FIXED_DIR="$PROJECT_ROOT/fixed"
BACKUP_DIR="$PROJECT_ROOT/fix_backup_$(date +%Y%m%d_%H%M%S)"

if [ ! -d "$FIXED_DIR" ]; then
  echo "ERROR: '$FIXED_DIR' not found."
  echo "Extract fixed-files-only.zip into a folder named 'fixed' next to this script first."
  exit 1
fi

if [ ! -d "$PROJECT_ROOT/app" ]; then
  echo "ERROR: '$PROJECT_ROOT/app' not found."
  echo "Run this script from your project root (the folder that directly contains 'app/')."
  exit 1
fi

echo "Project root: $PROJECT_ROOT"
echo "Fixed files:  $FIXED_DIR"
echo "Backups go to: $BACKUP_DIR"
echo

copied=0
created=0

# Find every regular file under fixed/, preserving its relative path.
while IFS= read -r -d '' src; do
  rel="${src#"$FIXED_DIR"/}"
  dest="$PROJECT_ROOT/$rel"

  mkdir -p "$(dirname "$dest")"

  if [ -f "$dest" ]; then
    mkdir -p "$(dirname "$BACKUP_DIR/$rel")"
    cp "$dest" "$BACKUP_DIR/$rel"
    echo "UPDATED  $rel"
    copied=$((copied + 1))
  else
    echo "CREATED  $rel"
    created=$((created + 1))
  fi

  cp "$src" "$dest"
done < <(find "$FIXED_DIR" -type f -print0)

echo
echo "Done. $copied file(s) updated (backed up), $created file(s) newly created."
echo "Backups saved under: $BACKUP_DIR"
echo
echo "----------------------------------------------------------------------"
echo "These old files are now DEAD CODE and should be deleted by hand once"
echo "you've confirmed the project builds and runs correctly:"
echo "----------------------------------------------------------------------"

cat << 'DEAD_FILES'
app/src/main/java/com/livetvpro/app/ui/home/HomeFragment.kt
app/src/main/java/com/livetvpro/app/ui/live/LiveEventsFragment.kt
app/src/main/java/com/livetvpro/app/ui/sports/SportsFragment.kt
app/src/main/java/com/livetvpro/app/ui/favorites/FavoritesFragment.kt
app/src/main/java/com/livetvpro/app/ui/categories/CategoryChannelsFragment.kt
app/src/main/java/com/livetvpro/app/ui/settings/SettingsFragment.kt
app/src/main/java/com/livetvpro/app/ui/playlists/PlaylistsFragment.kt
app/src/main/java/com/livetvpro/app/ui/networkstream/NetworkStreamFragment.kt
app/src/main/java/com/livetvpro/app/ui/score/CricketScoreFragment.kt
app/src/main/java/com/livetvpro/app/ui/score/FootballScoreFragment.kt
app/src/main/java/com/livetvpro/app/ui/deviceid/DeviceIdFragment.kt
app/src/main/java/com/livetvpro/app/ui/appearance/AppearanceFragment.kt
app/src/main/java/com/livetvpro/app/ui/appearance/ThemePreviewAdapter.kt
app/src/main/java/com/livetvpro/app/ui/components/FloatingNavView.kt
app/src/main/res/navigation/nav_graph.xml
app/src/main/res/layout/activity_main.xml
app/src/main/res/layout-television/activity_main.xml
app/src/main/res/layout/item_theme_preview.xml
DEAD_FILES

echo "----------------------------------------------------------------------"
echo "Tip: once you're confident, delete them all at once with:"
echo "  cd \"$PROJECT_ROOT\" && xargs -d'\n' rm -f -- < dead_files.txt"
echo "(after saving the list above into dead_files.txt)"
