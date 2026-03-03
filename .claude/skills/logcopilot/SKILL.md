```markdown
# logcopilot Development Patterns

> Auto-generated skill from repository analysis

## Overview
The logcopilot repository is a Java-based application focused on log management and analysis. This codebase follows standard Java conventions with camelCase naming and demonstrates clean project structure patterns. The repository emphasizes documentation-driven development with careful attention to initialization and setup procedures.

## Coding Conventions

### File Naming
- Use **camelCase** for all Java files and packages
- Example: `LogProcessor.java`, `DataAnalyzer.java`, `ConfigManager.java`

### Import Organization
- Mixed import style detected - maintain consistency within individual files
- Group imports logically: standard library, third-party, local packages
```java
import java.util.List;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.logcopilot.core.LogEntry;
import com.logcopilot.util.FileHelper;
```

### Class Structure
- Follow standard Java conventions
- Use descriptive method and variable names
- Maintain consistent indentation and spacing

## Workflows

### Project Initialization
**Trigger:** Setting up a new component or module
**Command:** `/init-component`

1. Create main class file using camelCase naming
2. Add appropriate package declaration
3. Import required dependencies following the mixed import style
4. Document the class purpose and usage
5. Implement core functionality with descriptive method names

### Documentation Updates
**Trigger:** Adding or modifying features
**Command:** `/update-docs`

1. Update relevant documentation files
2. Use clear, concise commit messages starting with "docs:"
3. Keep documentation synchronized with code changes
4. Include usage examples where applicable

### Code Implementation
**Trigger:** Adding new functionality
**Command:** `/implement-feature`

1. Create appropriately named Java classes
2. Follow camelCase naming conventions
3. Organize imports according to established patterns
4. Write clean, readable code with proper error handling
5. Add inline documentation for complex logic

## Testing Patterns

### Test Structure
- Test files follow the `*.test.*` pattern
- Framework-agnostic approach allows flexibility in testing strategies
- Focus on comprehensive coverage of core functionality

### Test Naming
```java
public class LogProcessorTest {
    public void testProcessValidLogEntry() { }
    public void testHandleInvalidInput() { }
}
```

## Commit Patterns
- Use descriptive prefixes: `docs:`, `init:`, `feat:`, `fix:`
- Keep commit messages concise (average 33 characters)
- Focus on clear, actionable descriptions

## Commands
| Command | Purpose |
|---------|---------|
| /init-component | Initialize a new component following project conventions |
| /update-docs | Update documentation with proper formatting and examples |
| /implement-feature | Add new functionality following established patterns |
| /create-test | Generate test files following the *.test.* pattern |
| /organize-imports | Clean up and organize imports following mixed style guidelines |
```