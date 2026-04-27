/*
 * Obfuscate - A Simple Java Source Code Obfuscator
 *
 * A lightweight, zero-dependency Java source code obfuscator that runs on
 * Java 8 and above. Designed for single-file or small Java projects where
 * a full bytecode obfuscator like ProGuard is overkill.
 *
 * Features:
 *   - Strips all comments (single-line and multi-line)
 *   - Renames private methods to _m0, _m1, etc.
 *   - Renames local variables to _v0, _v1, etc.
 *   - Renames private fields (including private static final) to _f0, _f1, etc.
 *   - Removes blank lines and trims trailing whitespace
 *   - Single-pass rename engine for fast processing
 *   - Progress output during obfuscation
 *   - Auto-detects types, inner class fields, and method parameters
 *   - Works against any Java source file with no configuration
 *
 * Preserves:
 *   - Public/protected methods and fields (API surface)
 *   - String and char literals (content inside quotes is untouched)
 *   - Public/protected static final constants
 *   - Import statements and package declarations
 *   - Member access expressions (e.g. System.out, File.separator)
 *   - The main method signature
 *   - Class names, annotations, and type references
 *   - Inner class field names (auto-detected from dot access patterns)
 *   - Method parameter names
 *
 * Limitations:
 *   - Regex-based, not AST-based; edge cases in very complex code may
 *     require manual review
 *   - Does not rename across multiple source files
 *   - Does not handle reflection-based access patterns
 *
 * Usage:
 *   javac Obfuscate.java
 *   java Obfuscate <input.java> [output.java]
 *
 *   If output is omitted, writes to <input>_obf.java
 *
 * @author  Ron Perkins
 * @version 2.0
 * @since   2025
 * @license MIT License
 * @see     https://github.com/ronperkinsuk/java-obfuscate
 *
 * Copyright (c) 2025 Ron Perkins
 */

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class Obfuscate {

    private static int methodCounter = 0;
    private static int fieldCounter = 0;
    private static int varCounter = 0;
    private static final Map<String, String> renameMap = new LinkedHashMap<String, String>();

    // Java keywords that must never be renamed
    private static final Set<String> JAVA_KEYWORDS = new HashSet<String>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while", "true", "false", "null"
    ));

    // Built-in Java types and common API classes
    private static final Set<String> JAVA_TYPES = new HashSet<String>(Arrays.asList(
        "String", "Integer", "Long", "Double", "Float", "Boolean", "Byte", "Short",
        "Character", "Object", "Class", "System", "Math", "Thread", "Runnable",
        "Exception", "RuntimeException", "Error", "Throwable", "Override",
        "List", "Map", "Set", "ArrayList", "HashMap", "LinkedHashMap", "HashSet",
        "TreeMap", "TreeSet", "LinkedList", "Queue", "Deque", "ArrayDeque",
        "Arrays", "Collections", "Objects", "Optional", "Stream",
        "File", "Path", "Paths", "Files",
        "InputStream", "OutputStream", "Reader", "Writer", "BufferedReader",
        "BufferedWriter", "InputStreamReader", "OutputStreamWriter",
        "FileInputStream", "FileOutputStream", "FileReader", "FileWriter",
        "BufferedInputStream", "BufferedOutputStream",
        "ByteArrayInputStream", "ByteArrayOutputStream",
        "PrintStream", "PrintWriter", "Scanner",
        "StringBuilder", "StringBuffer", "Pattern", "Matcher",
        "UUID", "Date", "Calendar", "Locale", "TimeZone",
        "URI", "URL", "HttpURLConnection",
        "MessageDigest", "Cipher", "SecretKeySpec", "GCMParameterSpec",
        "FileDescriptor", "ProcessBuilder", "Process", "Runtime",
        "Comparable", "Iterable", "Iterator", "Enumeration",
        "Serializable", "Cloneable", "AutoCloseable", "Closeable",
        "Charset", "StandardCharsets", "BigDecimal", "BigInteger",
        "AtomicInteger", "AtomicLong", "AtomicBoolean",
        "ConcurrentHashMap", "CopyOnWriteArrayList",
        "ExecutorService", "Executors", "Future", "Callable",
        "SuppressWarnings", "Deprecated", "FunctionalInterface",
        "SafeVarargs"
    ));

    // The full reserved set, built dynamically per source file
    private static final Set<String> reserved = new HashSet<String>();

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();

        if (args.length < 1) {
            System.out.println("Usage: java Obfuscate <input.java> [output.java]");
            System.exit(1);
        }

        String inputPath = args[0];
        String outputPath = args.length > 1 ? args[1] : inputPath.replace(".java", "_obf.java");

        System.out.println("[1/8] Reading source: " + inputPath);
        String source = readFile(inputPath);
        System.out.println("       " + source.length() + " characters read");

        System.out.println("[2/8] Stripping comments...");
        source = stripComments(source);
        System.out.println("       " + source.length() + " characters after stripping");

        // Build the reserved set dynamically from the source
        reserved.addAll(JAVA_KEYWORDS);
        reserved.addAll(JAVA_TYPES);
        reserved.add("main");
        reserved.add("args");

        System.out.println("[3/8] Auto-detecting types from imports and declarations...");
        Set<String> importedTypes = collectImportedTypes(source);
        reserved.addAll(importedTypes);
        System.out.println("       Found " + importedTypes.size() + " imported types");

        Set<String> declaredTypes = collectDeclaredTypes(source);
        reserved.addAll(declaredTypes);
        System.out.println("       Found " + declaredTypes.size() + " declared types");

        System.out.println("[4/8] Auto-detecting dot-accessed identifiers...");
        Set<String> dotAccessed = collectDotAccessedIdentifiers(source);
        reserved.addAll(dotAccessed);
        System.out.println("       Found " + dotAccessed.size() + " dot-accessed identifiers");

        System.out.println("[5/8] Collecting private methods...");
        collectPrivateMethods(source);
        System.out.println("       Found " + methodCounter + " private methods");

        System.out.println("[6/8] Collecting private fields and local variables...");
        collectPrivateFields(source);
        System.out.println("       Found " + fieldCounter + " private fields");
        collectLocalVariables(source);
        System.out.println("       Found " + varCounter + " local variables");
        System.out.println("       Total identifiers to rename: " + renameMap.size());

        System.out.println("[7/9] Preventing constant inlining...");
        source = preventConstantInlining(source);
        System.out.println("       Constant inlining prevention applied");

        System.out.println("[8/9] Applying renames...");
        source = applyRenames(source);
        System.out.println("       Renames applied");

        System.out.println("[9/9] Cleaning whitespace and writing: " + outputPath);
        source = cleanWhitespace(source);
        writeFile(outputPath, source);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println();
        System.out.println("Obfuscation complete in " + elapsed + "ms");
        System.out.println("Renamed " + renameMap.size() + " identifiers:");
        for (Map.Entry<String, String> e : renameMap.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + e.getValue());
        }
    }

    /**
     * Extract type names from import statements.
     * e.g. "import org.example.Foo;" -> reserves "Foo"
     * Also extracts all package segments to prevent renaming them.
     */
    private static Set<String> collectImportedTypes(String src) {
        Set<String> types = new HashSet<String>();
        Pattern p = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?);", Pattern.MULTILINE);
        Matcher m = p.matcher(src);
        while (m.find()) {
            String full = m.group(1);
            String[] parts = full.split("\\.");
            // Reserve the class name (last segment)
            String last = parts[parts.length - 1];
            if (!"*".equals(last)) types.add(last);
            // Reserve all package segments
            for (String part : parts) {
                if (!"*".equals(part)) types.add(part);
            }
        }
        return types;
    }

    /**
     * Collect class/interface/enum names declared in the source.
     * Also collects inner class field names by finding class bodies with field declarations.
     */
    private static Set<String> collectDeclaredTypes(String src) {
        Set<String> types = new HashSet<String>();
        // Top-level and inner class/interface/enum declarations
        Pattern p = Pattern.compile("\\b(?:class|interface|enum)\\s+(\\w+)");
        Matcher m = p.matcher(src);
        while (m.find()) {
            types.add(m.group(1));
        }
        // Inner class fields: find simple field declarations inside inner class bodies
        // Only match classes that appear after the first opening brace (i.e. nested classes)
        int firstBrace = src.indexOf('{');
        if (firstBrace >= 0) {
            String afterFirst = src.substring(firstBrace + 1);
            Pattern innerClass = Pattern.compile("\\bclass\\s+(\\w+)\\s*\\{([^}]+)\\}");
            m = innerClass.matcher(afterFirst);
            while (m.find()) {
                String body = m.group(2);
                Pattern fieldPat = Pattern.compile("(?:^|;)\\s*(?:\\w+\\s+)*(\\w+)\\s+(\\w+)\\s*[=;,]");
                Matcher fm = fieldPat.matcher(body);
                while (fm.find()) {
                    types.add(fm.group(2));
                }
            }
        }
        return types;
    }

    /**
     * Collect all identifiers that appear after a dot (member access).
     * These must not be renamed as they reference API members or class fields.
     * e.g. System.out, File.separator, data.flag -> reserves "out", "separator", "flag"
     */
    private static Set<String> collectDotAccessedIdentifiers(String src) {
        Set<String> ids = new HashSet<String>();
        int i = 0;
        int len = src.length();
        while (i < len) {
            char c = src.charAt(i);
            // Skip strings
            if (c == '"') { i = findEndOfString(src, i); continue; }
            if (c == '\'') { i = findEndOfChar(src, i); continue; }
            // Look for .identifier pattern
            if (c == '.' && i + 1 < len && Character.isJavaIdentifierStart(src.charAt(i + 1))) {
                i++; // skip the dot
                int start = i;
                while (i < len && Character.isJavaIdentifierPart(src.charAt(i))) i++;
                ids.add(src.substring(start, i));
            } else {
                i++;
            }
        }
        return ids;
    }

    /**
     * Strip all comments while preserving string/char literals.
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int len = src.length();
        while (i < len) {
            char c = src.charAt(i);
            if (c == '"') {
                int end = findEndOfString(src, i);
                out.append(src, i, end);
                i = end;
            } else if (c == '\'') {
                int end = findEndOfChar(src, i);
                out.append(src, i, end);
                i = end;
            } else if (c == '/' && i + 1 < len && src.charAt(i + 1) == '/') {
                while (i < len && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < len && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < len && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int findEndOfString(String src, int start) {
        int i = start + 1;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '"') return i + 1;
            i++;
        }
        return src.length();
    }

    private static int findEndOfChar(String src, int start) {
        int i = start + 1;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '\\') { i += 2; continue; }
            if (c == '\'') return i + 1;
            i++;
        }
        return src.length();
    }

    /**
     * Find private method declarations and map their names.
     */
    private static void collectPrivateMethods(String src) {
        Pattern p = Pattern.compile(
            "\\bprivate\\s+(?:static\\s+)?(?:final\\s+)?(?:[\\w<>\\[\\],\\s]+?)\\s+(\\w+)\\s*\\("
        );
        Matcher m = p.matcher(src);
        while (m.find()) {
            String name = m.group(1);
            if (!reserved.contains(name) && !renameMap.containsKey(name)) {
                renameMap.put(name, "_m" + methodCounter++);
            }
        }
    }

    /**
     * Find private fields and map their names.
     * Includes private static final fields (safe for standalone apps with no reflection).
     */
    private static void collectPrivateFields(String src) {
        // Private non-final fields
        Pattern p = Pattern.compile(
            "\\bprivate\\s+(?:static\\s+)?(?:volatile\\s+)?(?!.*\\bfinal\\b)([\\w<>\\[\\]]+)\\s+(\\w+)\\s*[=;]"
        );
        Matcher m = p.matcher(src);
        while (m.find()) {
            String name = m.group(2);
            if (!reserved.contains(name) && !renameMap.containsKey(name)) {
                renameMap.put(name, "_f" + fieldCounter++);
            }
        }

        // Private static final fields
        Pattern pf = Pattern.compile(
            "\\bprivate\\s+static\\s+final\\s+([\\w<>\\[\\]]+)\\s+(\\w+)\\s*[=;]"
        );
        Matcher mf = pf.matcher(src);
        while (mf.find()) {
            String name = mf.group(2);
            if (!reserved.contains(name) && !renameMap.containsKey(name)) {
                renameMap.put(name, "_f" + fieldCounter++);
            }
        }
    }

    /**
     * Find local variable declarations inside method bodies.
     */
    private static void collectLocalVariables(String src) {
        Pattern p = Pattern.compile(
            "(?:^|[{;])\\s*(?:final\\s+)?([A-Z][\\w<>\\[\\],\\s]*?)\\s+(\\w+)\\s*(?:=|;|,)"
        , Pattern.MULTILINE);
        Matcher m = p.matcher(src);
        while (m.find()) {
            String type = m.group(1).trim();
            String name = m.group(2);
            if (reserved.contains(name) || renameMap.containsKey(name)) continue;
            if (name.length() <= 3) continue;
            if (type.contains("class") || type.contains("interface")) continue;
            renameMap.put(name, "_v" + varCounter++);
        }

        // for-each patterns
        Pattern forEach = Pattern.compile("\\bfor\\s*\\(\\s*(?:final\\s+)?([\\w<>\\[\\]]+)\\s+(\\w+)\\s*:");
        m = forEach.matcher(src);
        while (m.find()) {
            String name = m.group(2);
            if (!reserved.contains(name) && !renameMap.containsKey(name) && name.length() > 3) {
                renameMap.put(name, "_v" + varCounter++);
            }
        }
    }

    /**
     * Prevent the Java compiler from inlining private static final constants.
     * - String literals: "value" -> "value".toString()
     * - int literals: 123 -> Integer.valueOf(123)
     * - long literals: 123L -> Long.valueOf(123L)
     * - boolean literals: true -> Boolean.valueOf(true)
     * - double literals: 1.0 -> Double.valueOf(1.0)
     * - float literals: 1.0f -> Float.valueOf(1.0f)
     * This ensures the obfuscated field name is the only reference in bytecode.
     */
    private static String preventConstantInlining(String src) {
        StringBuilder out = new StringBuilder(src.length());
        String[] lines = src.split("\n", -1);
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("private static final ") && !trimmed.contains("[]")
                    && !trimmed.contains(" new ") && !trimmed.contains("Pattern.compile")
                    && !trimmed.contains(".valueOf(") && !trimmed.contains(".toString()")) {
                String replaced = line.replaceAll(
                    "(private\\s+static\\s+final\\s+String\\s+\\w+\\s*=\\s*)(\"[^\"]*\")\\s*;",
                    "$1$2.toString();"
                );
                if (!replaced.equals(line)) { out.append(replaced).append('\n'); count++; continue; }
                replaced = line.replaceAll(
                    "(private\\s+static\\s+final\\s+int\\s+\\w+\\s*=\\s*)(0x[0-9a-fA-F]+|\\d+)\\s*;",
                    "$1Integer.valueOf($2);"
                );
                if (!replaced.equals(line)) { out.append(replaced).append('\n'); count++; continue; }
                replaced = line.replaceAll(
                    "(private\\s+static\\s+final\\s+long\\s+\\w+\\s*=\\s*)(0x[0-9a-fA-F]+[lL]?|\\d+[lL])\\s*;",
                    "$1Long.valueOf($2);"
                );
                if (!replaced.equals(line)) { out.append(replaced).append('\n'); count++; continue; }
                replaced = line.replaceAll(
                    "(private\\s+static\\s+final\\s+boolean\\s+\\w+\\s*=\\s*)(true|false)\\s*;",
                    "$1Boolean.valueOf($2);"
                );
                if (!replaced.equals(line)) { out.append(replaced).append('\n'); count++; continue; }
                replaced = line.replaceAll(
                    "(private\\s+static\\s+final\\s+double\\s+\\w+\\s*=\\s*)([\\d.]+[dD]?)\\s*;",
                    "$1Double.valueOf($2);"
                );
                if (!replaced.equals(line)) { out.append(replaced).append('\n'); count++; continue; }
                replaced = line.replaceAll(
                    "(private\\s+static\\s+final\\s+float\\s+\\w+\\s*=\\s*)([\\d.]+[fF])\\s*;",
                    "$1Float.valueOf($2);"
                );
                if (!replaced.equals(line)) { out.append(replaced).append('\n'); count++; continue; }
            }
            out.append(line).append('\n');
        }
        System.out.println("       Prevented inlining on " + count + " constant(s)");
        return out.toString();
    }

    /**
     * Apply all renames in a single pass through the source,
     * skipping string/char literals, import statements, and dot-prefixed identifiers.
     */
    private static String applyRenames(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int len = src.length();
        int lastProgress = -1;
        boolean inImport = false;

        while (i < len) {
            int pct = (int) ((long) i * 100 / len);
            if (pct / 10 > lastProgress) {
                lastProgress = pct / 10;
                System.out.println("       " + pct + "% processed...");
            }

            char c = src.charAt(i);

            if (c == '"') {
                int end = findEndOfString(src, i);
                out.append(src, i, end);
                i = end;
                continue;
            }
            if (c == '\'') {
                int end = findEndOfChar(src, i);
                out.append(src, i, end);
                i = end;
                continue;
            }

            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < len && Character.isJavaIdentifierPart(src.charAt(i))) i++;
                String word = src.substring(start, i);
                boolean afterDot = start > 0 && src.charAt(start - 1) == '.';
                String replacement = (!afterDot && !inImport) ? renameMap.get(word) : null;
                out.append(replacement != null ? replacement : word);
                if ("import".equals(word)) inImport = true;
            } else {
                if (c == ';' || c == '\n') inImport = false;
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Remove blank lines and trim trailing whitespace.
     */
    private static String cleanWhitespace(String src) {
        StringBuilder out = new StringBuilder();
        String[] lines = src.split("\n");
        for (String line : lines) {
            String trimmed = line.replaceAll("\\s+$", "");
            if (!trimmed.isEmpty()) {
                out.append(trimmed).append('\n');
            }
        }
        return out.toString();
    }

    private static String readFile(String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static void writeFile(String path, String content) throws IOException {
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(path), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }
}
