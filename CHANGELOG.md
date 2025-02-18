# Changelog

### 0.4.0 (2025-02-18)

- Add support for [memory
  tracing](https://github.com/clojure-goes-fast/clj-memory-meter?tab=readme-ov-file#memory-usage-tracing).

### 0.3.0 (2023-05-29)

- Use newest upstream JAMM version that handles JDK17+ compatibility in a more
  sensible way.
- Restore the broken functionality of printing object trees.

### 0.2.3 (2023-04-26)

- Fix performance regression.

### 0.2.2 (2023-02-19)

- Ignore access errors when trying to get private fields with Unsafe (for
  example, in hidden classes) and skip the calculation for those fields.

### 0.2.1 (2022-07-28)

- Make Java version detection within JAMM more robust.

### 0.2.0 (2022-07-27)

- [#4](https://github.com/clojure-goes-fast/clj-memory-meter/issues/5): Switch
  JAMM dependency to a custom 0.4.0 build that uses Unsafe instead of reflection
  when run in JDK 17+.

### 0.1.3 (2020-08-11)

- Update JAMM dependency to 0.3.3.
- [#4](https://github.com/clojure-goes-fast/clj-memory-meter/issues/4): Use proper unit names for power-of-2 sizes.

### 0.1.2 (2018-08-28)

- [#2](https://github.com/clojure-goes-fast/clj-memory-meter/issues/2): Make it possible to use clj-memory-meter outside of REPL environment.

### 0.1.0 (2018-03-05)

- Initial release.
