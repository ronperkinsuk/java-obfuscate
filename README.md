# Java Obfuscate

A lightweight, zero-dependency Java source code obfuscator that runs on Java 8+.

Designed for single-file or small Java projects where a full bytecode obfuscator like ProGuard is overkill. Just point it at a `.java` file and it will strip comments, rename private methods, fields, and local variables — no configuration needed.

## Features

- Strips all single-line and multi-line comments
- Renames private methods (`_m0`, `_m1`, ...)
- Renames private method parameters (`_p0`, `_p1`, ...)
- Renames private fields (including private static final) (`_f0`, `_f1`, ...)
- Renames local variables (`_v0`, `_v1`, ...)
- Prevents compiler inlining of private static final constants
- Removes blank lines and trims trailing whitespace
- Auto-detects imported types, inner class fields, and dot-accessed members
- Handles Java 13+ text blocks (triple-quoted strings)
- Single-pass rename engine for fast processing
- Progress output during obfuscation
- Works against any Java source file with zero configuration
- No third-party dependencies — pure `java.util` and `java.io`
- Java 8 compatible

## What It Preserves

- Public and protected methods and fields
- Public and protected static final constants
- String and char literals (including text blocks)
- Import statements and package declarations
- Member access expressions (e.g. `System.out`, `File.separator`)
- Class, interface, and enum names
- Annotation parameter names (e.g. `name` in `@Column(name = "id")`)
- Type references
- The `main` method signature

## Quick Start

```bash
# Compile
javac Obfuscate.java

# Obfuscate a file (output defaults to MyApp_obf.java)
java Obfuscate MyApp.java

# Obfuscate with explicit output path
java Obfuscate src/MyApp.java build/MyApp.java
```

## Example

Given this input:

```java
public class Example {
    private static boolean debugMode = false;
    private static int errorCount = 0;

    public static void main(String[] args) {
        String inputPath = args[0];
        processFile(inputPath);
    }

    // Process the input file
    private static void processFile(String path) {
        File inputFile = new File(path);
        if (inputFile.exists()) {
            System.out.println("Processing: " + path);
            errorCount++;
        }
    }
}
```

The obfuscator produces:

```java
public class Example {
    private static boolean _f0 = false;
    private static int _f1 = 0;
    public static void main(String[] args) {
        String _v0 = args[0];
        _m0(_v0);
    }
    private static void _m0(String _p0) {
        File _v1 = new File(_p0);
        if (_v1.exists()) {
            System.out.println("Processing: " + _p0);
            _f1++;
        }
    }
}
```

Notice that `System.out`, `File`, string literals, and the `main` signature are all preserved, while the private method parameter `path` is renamed to `_p0`.

## How It Works

The obfuscator runs in 10 phases:

| Phase | Description |
|-------|-------------|
| 1 | Read the source file |
| 2 | Strip all comments (preserving string/char literals and text blocks) |
| 3 | Auto-detect types from imports, class declarations, and inner class fields |
| 4 | Auto-detect dot-accessed identifiers (e.g. `System.out` → reserves `out`) |
| 5 | Collect private method names and generate rename mappings |
| 6 | Collect private method parameter names |
| 7 | Collect private fields and local variable names |
| 8 | Prevent constant inlining on private static final fields |
| 9 | Apply all renames in a single pass, skipping strings, imports, annotations, and member access |
| 10 | Remove blank lines, trim whitespace, and write output |

### Auto-Detection

Unlike many simple obfuscators that require a hardcoded list of reserved words, this tool dynamically builds its reserved set from the source file itself:

- **Import scanning** — parses all `import` statements to reserve type names and package segments
- **Declaration scanning** — finds `class`, `interface`, and `enum` declarations plus their inner class field names
- **Dot-access scanning** — scans for `.identifier` patterns and reserves them to prevent breaking member access like `data.flag` or `UTF_8.name()`

This means it works against any Java source file without project-specific configuration.

## Sample Output

```
[1/10] Reading source: src/MyApp.java
        279255 characters read
[2/10] Stripping comments...
        265346 characters after stripping
[3/10] Auto-detecting types from imports and declarations...
        Found 80 imported types
        Found 32 declared types
[4/10] Auto-detecting dot-accessed identifiers...
        Found 245 dot-accessed identifiers
[5/10] Collecting private methods...
        Found 98 private methods
[6/10] Collecting private method parameters...
        Found 47 private method parameters
[7/10] Collecting private fields and local variables...
        Found 22 private fields
        Found 335 local variables
        Total identifiers to rename: 502
[8/10] Preventing constant inlining...
       Prevented inlining on 12 constant(s)
        Constant inlining prevention applied
[9/10] Applying renames...
       0% processed...
       10% processed...
       ...
       90% processed...
        Renames applied
[10/10] Cleaning whitespace and writing: build/MyApp.java

Obfuscation complete in 1629ms
Renamed 502 identifiers:
  processFile -> _m0
  validateInput -> _m1
  ...
```

## Integration with Ant

You can integrate the obfuscator into an Ant build script to obfuscate before compiling:

```xml
<target name="obfuscate" description="Obfuscate the source">
    <javac srcdir="tools" includes="Obfuscate.java" destdir="tools"
           source="1.8" target="1.8" includeantruntime="false"/>
    <java classname="Obfuscate" fork="true" failonerror="true">
        <classpath>
            <pathelement location="tools"/>
        </classpath>
        <arg value="src/MyApp.java"/>
        <arg value="build/MyApp.java"/>
    </java>
</target>

<target name="compile" depends="obfuscate" description="Compile obfuscated source">
    <javac srcdir="build" destdir="build" source="1.8" target="1.8"
           includeantruntime="false">
        <classpath>
            <fileset dir="lib" includes="*.jar"/>
        </classpath>
    </javac>
</target>
```

## Limitations

- **Regex-based, not AST-based** — works well for typical Java code but edge cases in very complex or unconventional code may require manual review of the output
- **Single-file scope** — does not track renames across multiple source files. If class A calls a private method in class B, the rename won't be coordinated
- **Single rename map for all scopes** — identically-named variables in different methods get the same obfuscated name. This is correct but reduces obfuscation quality
- **Reflection** — code that uses reflection to access methods or fields by name will break if those names are renamed
- **No control flow obfuscation** — this tool only renames identifiers and strips comments. It does not insert dead code, flatten control flow, or encrypt strings

For more advanced obfuscation needs, consider [ProGuard](https://github.com/Guardsquare/proguard) (bytecode-level, free) or commercial tools like Allatori or Zelix KlassMaster.

## Requirements

- Java 8 or higher (compile and run)
- No third-party libraries

## License

[MIT License](LICENSE)

Copyright (c) 2025-2026 Ron Perkins
