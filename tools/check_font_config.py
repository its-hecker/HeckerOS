#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Run the pinned Android font parsers with host XML/data adapters, without JNI.

The parser sources are unchanged. The host types only store parser results and
adapt JDK StAX to the XML pull methods used by Android. This checks configuration
parsing, including Zygote-fatal IllegalArgumentException; it does not test native
font shaping, rendering, or a complete Android boot.
"""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'tests/font-parser'
STUBS = {
    'android/compat/annotation/UnsupportedAppUsage.java': '''
package android.compat.annotation;
public @interface UnsupportedAppUsage { int maxTargetSdk(); long trackingBug(); }
''',
    'android/ravenwood/annotation/RavenwoodKeepWholeClass.java': '''
package android.ravenwood.annotation;
public @interface RavenwoodKeepWholeClass {}
''',
    'android/os/Build.java': '''
package android.os;
public class Build { public static class VERSION_CODES { public static final int R = 30; } }
''',
    'android/os/LocaleList.java': '''
package android.os;
public class LocaleList {
    public static LocaleList forLanguageTags(String tags) { return new LocaleList(); }
}
''',
    'android/util/ArraySet.java': '''
package android.util;
public class ArraySet<E> extends java.util.HashSet<E> {}
''',
    'android/graphics/fonts/FontStyle.java': '''
package android.graphics.fonts;
public class FontStyle {
    public static final int FONT_WEIGHT_NORMAL = 400, FONT_SLANT_ITALIC = 1, FONT_SLANT_UPRIGHT = 0;
    public FontStyle(int weight, int slant) {}
}
''',
    'android/graphics/fonts/FontVariationAxis.java': '''
package android.graphics.fonts;
public class FontVariationAxis {
    public FontVariationAxis(String tag, float value) {}
    public static String toFontVariationSettings(FontVariationAxis[] axes) { return ""; }
}
''',
    'android/text/FontConfig.java': '''
package android.text;
import java.io.File;
import java.util.List;
import java.util.Locale;
import android.os.LocaleList;
import android.graphics.fonts.FontStyle;
public class FontConfig {
    public FontConfig(List<FontFamily> families, List<Alias> aliases, List<NamedFamilyList> named,
                      List<Customization.LocaleFallback> locales, long modified, int version) {}
    public static class Font {
        public static final int VAR_TYPE_AXES_WGHT = 1, VAR_TYPE_AXES_ITAL = 2;
        public Font(File file, File original, String ps, FontStyle style, int index,
                    String axes, String fallback, int variable) {}
    }
    public static class FontFamily {
        public static final int VARIANT_DEFAULT = 0, VARIANT_COMPACT = 1, VARIANT_ELEGANT = 2;
        public FontFamily(List<Font> fonts, LocaleList locales, int variant) {}
    }
    public static class NamedFamilyList {
        private final List<FontFamily> families;
        private final String name;
        public NamedFamilyList(List<FontFamily> families, String name, String fallback) {
            this.families = families; this.name = name;
        }
        public String getName() { return name; }
        public List<FontFamily> getFamilies() { return families; }
    }
    public static class Alias {
        private final String original;
        public Alias(String name, String original, int weight) { this.original = original; }
        public String getOriginal() { return original; }
    }
    public static class Customization {
        public static class LocaleFallback {
            public static final int OPERATION_APPEND = 0, OPERATION_PREPEND = 1, OPERATION_REPLACE = 2;
            public LocaleFallback(Locale locale, int operation, FontFamily family) {}
        }
    }
}
''',
    'org/xmlpull/v1/XmlPullParserException.java': '''
package org.xmlpull.v1;
public class XmlPullParserException extends Exception {
    public XmlPullParserException(String message) { super(message); }
}
''',
    'org/xmlpull/v1/XmlPullParser.java': '''
package org.xmlpull.v1;
import java.io.InputStream;
import java.io.IOException;
public interface XmlPullParser {
    int START_DOCUMENT = 0, END_DOCUMENT = 1, START_TAG = 2, END_TAG = 3, TEXT = 4;
    void setInput(InputStream in, String encoding) throws XmlPullParserException;
    int next() throws XmlPullParserException, IOException;
    int nextTag() throws XmlPullParserException, IOException;
    int getEventType();
    String getAttributeValue(String namespace, String name);
    String getName();
    String getText();
    void require(int type, String namespace, String name) throws XmlPullParserException;
}
''',
    'android/util/Xml.java': '''
package android.util;
import java.io.InputStream;
import java.io.IOException;
import javax.xml.stream.*;
import org.xmlpull.v1.*;
public class Xml {
    public static XmlPullParser newPullParser() { return new Pull(); }
    private static class Pull implements XmlPullParser {
        private XMLStreamReader reader;
        private int event = START_DOCUMENT;
        public void setInput(InputStream in, String encoding) throws XmlPullParserException {
            try {
                XMLInputFactory factory = XMLInputFactory.newFactory();
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                reader = factory.createXMLStreamReader(in);
            } catch (XMLStreamException e) { throw new XmlPullParserException(e.toString()); }
        }
        public int next() throws XmlPullParserException {
            try {
                while (reader.hasNext()) {
                    switch (reader.next()) {
                        case XMLStreamConstants.START_ELEMENT: return event = START_TAG;
                        case XMLStreamConstants.END_ELEMENT: return event = END_TAG;
                        case XMLStreamConstants.CHARACTERS:
                        case XMLStreamConstants.CDATA: return event = TEXT;
                        case XMLStreamConstants.END_DOCUMENT: return event = END_DOCUMENT;
                    }
                }
                return event = END_DOCUMENT;
            } catch (XMLStreamException e) { throw new XmlPullParserException(e.toString()); }
        }
        public int nextTag() throws XmlPullParserException {
            int type;
            do { type = next(); } while (type == TEXT && getText().isBlank());
            if (type != START_TAG && type != END_TAG) throw new XmlPullParserException("Expected tag");
            return type;
        }
        public int getEventType() { return event; }
        public String getAttributeValue(String namespace, String name) {
            return reader.getAttributeValue(namespace == null ? "" : namespace, name);
        }
        public String getName() { return reader.getLocalName(); }
        public String getText() { return reader.getText(); }
        public void require(int type, String namespace, String name) throws XmlPullParserException {
            if (event != type || !getName().equals(name)) throw new XmlPullParserException("Expected " + name);
        }
    }
}
''',
    'FontParserMain.java': '''
import java.io.FileInputStream;
import java.io.File;
import android.graphics.fonts.FontCustomizationParser;
public class FontParserMain {
    public static void main(String[] args) throws Exception {
        try (FileInputStream input = new FileInputStream(args[0])) {
            FontCustomizationParser.Result result = FontCustomizationParser.parse(
                input, args[1] + File.separator, null);
            System.out.println(result.getAdditionalNamedFamilies().size());
        }
    }
}
''',
}
for annotation in ('NonNull', 'Nullable'):
    STUBS['android/annotation/' + annotation + '.java'] = (
        'package android.annotation;\n'
        '@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE_USE, '
        'java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.METHOD, '
        'java.lang.annotation.ElementType.FIELD})\npublic @interface ' + annotation + ' {}\n')


def compile_parser(folder):
    folder = Path(folder)
    sources = []
    lock = json.loads((FIXTURE / 'source-lock.json').read_text())
    for name, expected in lock['files'].items():
        source = FIXTURE / name
        if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise ValueError('Pinned Android font parser source differs: ' + name)
        sources.append(str(source))
    for name, content in STUBS.items():
        path = folder / 'src' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        sources.append(str(path))
    output = folder / 'classes'
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(output),
                    *sources], check=True, capture_output=True, text=True)
    return output


def run_parser(classes, config, fonts):
    return subprocess.run(['java', '-cp', str(classes), 'FontParserMain', str(config), str(fonts)],
                          capture_output=True, text=True, timeout=30)
