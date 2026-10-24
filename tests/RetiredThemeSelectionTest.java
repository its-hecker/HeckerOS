/* SPDX-License-Identifier: Apache-2.0 */
import com.android.server.theme.RetiredThemeSelection;
import java.util.LinkedHashMap;
import java.util.Map;
public class RetiredThemeSelectionTest {
    public static void main(String[] args) {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("android.theme.customization.system_palette", "AB1234");
        current.put("android.theme.customization.font", "custom.font");
        current.put("android.theme.customization.icon_pack.android", "com.android.systemui.signalGradicon");
        current.put("wifi", "com.custom.overlay.systemui.wifiAurora");
        current.put("timestamp", 123);
        Map<String, Object> clean = RetiredThemeSelection.apply(current);
        if (clean.size()!=4 || clean.containsKey("android.theme.customization.icon_pack.android")) throw new AssertionError(clean);
        if (current.size()!=5 || !clean.get("android.theme.customization.system_palette").equals("AB1234")) throw new AssertionError();
        if (!clean.equals(RetiredThemeSelection.apply(clean))) throw new AssertionError("migration must be idempotent");
        System.out.println("Retired theme migration preserves colors, fonts, Wi-Fi, timestamps and source selection: PASS");
    }
}
