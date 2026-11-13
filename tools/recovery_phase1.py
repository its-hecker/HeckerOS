# SPDX-License-Identifier: Apache-2.0
"""Guarded Phase 1 sideload/install UI for pinned PixelOS recovery.

Applies only to recovery_ui/screen_ui.cpp and its header. The stock graphics,
installation/verification logic, HeckerOS branding assets and menu UX stay put.
Never silently overwrite an unrecognized upstream revision.
"""
from pathlib import Path

ROOT_FILES = {
    "recovery_ui/include/recovery_ui/screen_ui.h": [
        (
            "  virtual void draw_screen_locked();",
            "  void draw_hecker_install_locked();\n  virtual void draw_screen_locked();",
        ),
        (
            "  bool show_text_ever;  // has show_text ever been true?",
            """  bool show_text_ever;  // has show_text ever been true?
  // Phase 1: sideload status is driven by actual recovery messages/progress.
  bool hecker_sideload_ui_ = false;
  int hecker_install_step_ = 0;
  std::string hecker_install_status_ = "Preparing update...";
  double hecker_sideload_epoch_ = 0.0;""",
        ),
    ],
    "recovery_ui/screen_ui.cpp": [
        (
            "#include <chrono>",
            "#include <chrono>\n#include <cmath>",
        ),
        (
            "void ScreenRecoveryUI::draw_screen_locked() {",
            """// Draw only during an actual sideload or installation; menus and failure
// screens retain their native controls. All positions scale with display/dpi.
void ScreenRecoveryUI::draw_hecker_install_locked() {
  pagesIdentical = false;
  const int w = ScreenWidth();
  const int h = ScreenHeight();
  gr_color(0, 0, 0, 255);
  gr_clear();

  // Preserve the existing HeckerOS caracal/Recovery branding.
  if (default_logo) {
    const int logo_w = gr_get_width(default_logo.get());
    const int logo_h = gr_get_height(default_logo.get());
    if (logo_w > 0 && logo_h > 0 && logo_w <= w &&
        logo_h < h / 3) {
      DrawSurface(default_logo.get(), 0, 0, logo_w, logo_h,
                  (w - logo_w) / 2, margin_height_ + PixelsFromDp(20));
    }
  }

  const int side = std::max(margin_width_ + PixelsFromDp(20), w / 9);
  const int right = w - side;
  const int bar_w = std::max(1, right - side);
  const int title_y = h * 46 / 100;

  const bool determinate = progressBarType == DETERMINATE;
  const float fraction = determinate
      ? std::max(0.0f, std::min(1.0f, progressScopeStart + progress * progressScopeSize))
      : 0.0f;
  const std::string title = (determinate || current_icon_ == INSTALLING_UPDATE)
      ? "Installing HeckerOS" : "ADB sideload";

  gr_color(240, 242, 248, 255);
  DrawTextLine(side, title_y, title, true);
  if (determinate) {
    const std::string percent = android::base::StringPrintf(
        "%d%%", static_cast<int>(fraction * 100.0f + 0.5f));
    const int percent_x = std::max(side, right - static_cast<int>(percent.size()) * char_width_);
    DrawTextLine(percent_x, title_y, percent, true);
  }

  // Rounded-looking slim track, based on the actual AOSP progress fraction.
  // While waiting for ADB (indeterminate), a small white/blue marker oscillates.
  const int bar_y = title_y + char_height_ + PixelsFromDp(25);
  const int bar_h = std::max(3, PixelsFromDp(3));
  gr_color(46, 48, 55, 255);
  gr_fill(side, bar_y, right, bar_y + bar_h);
  gr_color(166, 198, 255, 255);  // Reuse the existing HeckerOS Recovery blue.
  if (determinate) {
    const int filled = static_cast<int>(bar_w * fraction + 0.5f);
    if (filled > 0) {
      gr_fill(side, bar_y, side + filled, bar_y + bar_h);
    }
  } else {
    const double origin = hecker_sideload_epoch_ > 0.0 ? hecker_sideload_epoch_ : 0.0;
    double phase = std::fmod(std::max(0.0, now() - origin) / 2.0, 2.0);
    phase = phase < 1.0 ? phase : 2.0 - phase;
    const int marker_w = std::min(bar_w, std::max(PixelsFromDp(30), bar_w / 7));
    const int left = side + static_cast<int>((bar_w - marker_w) * phase + 0.5);
    gr_fill(left, bar_y, left + marker_w, bar_y + bar_h);
  }

  const int caption_y = bar_y + PixelsFromDp(16);
  gr_color(172, 177, 190, 255);
  const size_t max_chars = static_cast<size_t>(std::max(1, bar_w / std::max(1, char_width_) - 14));
  DrawTextLine(side, caption_y, hecker_install_status_.substr(0, max_chars), false);
  // Only show stages that are actually reported by recovery or the message
  // parser. AOSP's OTA stage is optional, so do not manufacture OTA stages.
  int current_step = hecker_install_step_;
  int total_steps = current_step > 0 ? 3 : 0;
  if (max_stage > 0 && stage >= 0) {
    current_step = std::min(stage, max_stage);
    total_steps = max_stage;
  }
  if (total_steps > 0) {
    const std::string step = android::base::StringPrintf(
        "Step %d of %d", current_step, total_steps);
    const int step_x = std::max(side, right - static_cast<int>(step.size()) * char_width_);
    DrawTextLine(step_x, caption_y, step, false);
  }

  // The real recovery ring buffer remains intact; show only the recent lines
  // in a compact bottom panel. The full log is still available through menus.
  const int divider_y = h * 74 / 100;
  gr_color(36, 38, 44, 255);
  gr_fill(side, divider_y, right, divider_y + std::max(1, PixelsFromDp(1)));
  if (text_ != nullptr && text_rows_ > 0 && text_cols_ > 0) {
    gr_color(177, 182, 192, 255);
    int row = static_cast<int>(text_row_);
    int y = h - margin_height_ - char_height_ - PixelsFromDp(10);
    const int min_y = divider_y + PixelsFromDp(16);
    for (size_t i = 0; i < std::min<size_t>(text_rows_, 7) && y >= min_y; ++i) {
      if (text_[row][0] != '\\0') {
        const size_t columns = static_cast<size_t>(
            std::max(1, (right - side) / std::max(1, char_width_)));
        DrawTextLine(side, y, std::string(text_[row]).substr(0, columns), false);
      }
      row = (row + static_cast<int>(text_rows_) - 1) % static_cast<int>(text_rows_);
      y -= char_height_ + PixelsFromDp(2);
    }
  }
}

void ScreenRecoveryUI::draw_screen_locked() {""",
        ),
        (
            """void ScreenRecoveryUI::draw_screen_locked() {
  if (!show_text) {""",
            """void ScreenRecoveryUI::draw_screen_locked() {
  if ((hecker_sideload_ui_ || current_icon_ == INSTALLING_UPDATE) && !menu_ && !show_text) {
    draw_hecker_install_locked();
    return;
  }
  if (!show_text) {""",
        ),
        (
            "  if (show_text || !pagesIdentical) {",
            "  if (hecker_sideload_ui_ || current_icon_ == INSTALLING_UPDATE || show_text || !pagesIdentical) {",
        ),
        (
            """      if (redraw) update_progress_locked();
    }

    double end = now();""",
            """      if (hecker_sideload_ui_ && !menu_ && !show_text && progressBarType != DETERMINATE) {
        redraw = true;  // Keep the waiting-for-sideload indicator moving.
      }
      if (redraw) update_progress_locked();
    }

    double end = now();""",
        ),
        (
            """  current_icon_ = icon;
  update_screen_locked();
}""",
            """  current_icon_ = icon;
  if (icon == ERROR) hecker_sideload_ui_ = false;
  update_screen_locked();
}""",
        ),
        (
            """  std::lock_guard<std::mutex> lg(updateMutex);
  if (text_rows_ > 0 && text_cols_ > 0) {
    for (const char* ptr = str.c_str();""",
            """  std::lock_guard<std::mutex> lg(updateMutex);
  // These strings are emitted by the existing sideload and installer paths.
  // No ZIP verification or flashing behavior is modified.
  if (str.find("Now send the package you want to apply") != std::string::npos) {
    hecker_sideload_ui_ = true;
    hecker_sideload_epoch_ = now();
    hecker_install_step_ = 1;
    hecker_install_status_ = "Waiting for package...";
  } else if (str.find("Finding update package") != std::string::npos) {
    hecker_install_status_ = "Reading update package...";
    hecker_install_step_ = 1;
  } else if (str.find("Verifying update package") != std::string::npos) {
    hecker_install_status_ = "Verifying package...";
    hecker_install_step_ = 2;
  } else if (str.find("Installing update") != std::string::npos) {
    hecker_install_status_ = "Installing update...";
    hecker_install_step_ = 3;
  } else if (str.find("Installation aborted") != std::string::npos) {
    hecker_install_status_ = "Installation failed";
  } else if (str.find("Install from ADB complete") != std::string::npos) {
    hecker_sideload_ui_ = false;
    hecker_install_step_ = 0;
    hecker_install_status_ = "Preparing update...";
  }
  if (text_rows_ > 0 && text_cols_ > 0) {
    for (const char* ptr = str.c_str();""",
        ),
    ],
}


def _replace_guarded(content, original, patched, name, revert):
    if patched in content:
        if content.count(patched) != 1:
            raise ValueError("Duplicate HeckerOS recovery adapter: " + name)
        return content.replace(patched, original, 1) if revert else content
    if content.count(original) != 1:
        raise ValueError("Unrecognized recovery UI source; refusing to overwrite: " + name)
    return content if revert else content.replace(original, patched, 1)



# Build trees prepared with the previous Phase 1 adapter must upgrade safely.
# Normalize only these known snippets, then apply the usual guarded source checks.
PREVIOUS_UI_GUARDS = (
    ("  if ((hecker_sideload_ui_ || current_icon_ == INSTALLING_UPDATE) && !menu_) {", "  if ((hecker_sideload_ui_ || current_icon_ == INSTALLING_UPDATE) && !menu_ && !show_text) {"),
    ("      if (hecker_sideload_ui_ && !menu_ && progressBarType != DETERMINATE) {", "      if (hecker_sideload_ui_ && !menu_ && !show_text && progressBarType != DETERMINATE) {"),
)

def _upgrade_previous_phase1(source, relative):
    if relative != "recovery_ui/screen_ui.cpp" or "void ScreenRecoveryUI::draw_hecker_install_locked()" not in source:
        return source
    for before, after in PREVIOUS_UI_GUARDS:
        if before in source:
            if source.count(before) != 1 or after in source:
                raise ValueError("Unrecognized existing recovery UI: " + relative)
            source = source.replace(before, after, 1)
    return source

def recovery_phase1_plan(rom, revert=False):
    recovery = Path(rom) / "bootable/recovery"
    if not recovery.is_dir():
        return {}
    result = {}
    for relative, replacements in ROOT_FILES.items():
        target = recovery / relative
        source = target.read_text()
        changed = _upgrade_previous_phase1(source, relative)
        for old, new in reversed(replacements) if revert else replacements:
            changed = _replace_guarded(changed, old, new, relative, revert)
        if source != changed:
            result[target] = changed
    return result
