# ADR 002: JLine FFM Provider vs JNI for Terminal Access

## Status

Accepted

## Context

charm.clj requires native terminal access for raw mode input, terminal size detection, and cursor control. JLine provides this functionality through multiple terminal providers:

1. **JNI** (`jline-terminal-jni`) - Uses Java Native Interface with bundled native libraries
2. **FFM** (`jline-terminal-ffm`) - Uses Foreign Function & Memory API (JDK 21+)
3. **JNA** (`jline-terminal-jna`) - Uses Java Native Access
4. **Exec** (`jline-terminal`) - Falls back to executing system commands

The primary deployment target is GraalVM native-image and Babashka (which uses SCI interpreter on native-image).

## Decision

Use the FFM provider (`jline-terminal-ffm`). Offer JNI as an alternative via parameter if people are complaining.

## Consequences

### Pros of FFM Provider

- **No bundled native libraries** - JNI requires platform-specific `.so`/`.dylib`/`.dll` files bundled in the JAR
- **Simpler native-image build** - FFM is a pure Java API; JNI requires complex reflection and JNI configuration
- **Future-proof** - FFM is the modern replacement for JNI, stabilized in JDK 22
- **Smaller artifact size** - no native binaries to bundle
- **Babashka compatibility** - FFM works better with GraalVM native-image than JNI's ServiceLoader-based discovery
- **Direct provider instantiation** - can bypass JLine's SPI mechanism by instantiating `FfmTerminalProvider` directly, avoiding reflection

### Cons of FFM Provider

- **JDK 21+ required** - FFM requires JDK 21 (preview) or JDK 22+ (stable)
- **Native-image configuration needed** - requires `reachability-metadata.json` with downcall descriptors
- **Experimental in GraalVM** - requires `-H:+ForeignAPISupport` flag

### Native-Image Configuration

FFM downcalls must be pre-registered for native-image compilation. Required configuration in `reachability-metadata.json`:

```json
{
  "foreign": {
    "downcalls": [
      {
        "parameterTypes": ["int", "long", "void*"],
        "returnType": "int",
        "options": {"firstVariadicArg": 2}
      },
      {
        "parameterTypes": ["int"],
        "returnType": "int"
      },
      {
        "parameterTypes": ["int", "int", "void*"],
        "returnType": "int"
      },
      {
        "parameterTypes": ["int", "void*"],
        "returnType": "int"
      },
      {
        "parameterTypes": ["int", "void*", "long"],
        "returnType": "int"
      },
      {
        "parameterTypes": ["void*", "void*", "void*", "void*", "void*"],
        "returnType": "int"
      }
    ]
  }
}
```

These correspond to the POSIX functions used by JLine's `CLibrary`:
- `ioctl(fd, request, ...)` - terminal control (variadic)
- `isatty(fd)` - TTY detection
- `tcsetattr(fd, actions, termios*)` - set terminal attributes
- `tcgetattr(fd, termios*)` - get terminal attributes
- `ttyname_r(fd, buf*, size)` - get TTY name
- `openpty(master*, slave*, name*, termios*, winsize*)` - open pseudo-terminal

### Build Requirements

```
--enable-native-access=ALL-UNNAMED
-H:+ForeignAPISupport
-H:+UnlockExperimentalVMOptions  (for GraalVM warnings)
```

## Notes

The decision can be revisited if:
- JDK 21+ requirement becomes problematic for users
- GraalVM FFM support stabilizes and configuration becomes simpler
- JLine adds a provider with better native-image support out of the box
