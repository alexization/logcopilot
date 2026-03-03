# logcopilot Development Patterns

> Auto-generated skill from repository analysis

## Overview

The logcopilot repository is a Java-based project focused on log analysis and management. The codebase follows mixed conventions with an emphasis on documentation-driven development and standardized GitHub workflows. The project maintains a structured approach to both code organization and project documentation, with particular attention to workflow standardization and template management.

## Coding Conventions

### File Naming
- **Primary convention**: camelCase for Java files
- **Example**: `LogProcessor.java`, `ConfigManager.java`, `DataParser.java`

### Import Organization
- Mixed import style detected - consolidate imports at top of files
- Group imports by: standard library, third-party, internal packages

```java
// Standard library imports
import java.util.*;
import java.io.*;

// Third-party imports
import org.apache.commons.*;

// Internal imports
import com.logcopilot.core.*;
```

### Export/Module Patterns
- Mixed export patterns - standardize public API exposure
- Use clear public/private separation in classes

```java
public class LogProcessor {
    // Public API methods
    public void processLogs() { }
    
    // Private implementation details
    private void parseLogEntry() { }
}
```

### Commit Conventions
- Use descriptive prefixes: `docs:`, `init:`, `feat:`, `fix:`
- Keep messages concise (target ~36 characters for subject)
- Examples:
  - `docs: update API documentation`
  - `init: setup project structure`

## Workflows

### GitHub Template Configuration
**Trigger:** When someone wants to standardize GitHub workflows or update template policies
**Command:** `/update-github-templates`

1. **Update issue template files**
   - Navigate to `.github/ISSUE_TEMPLATE/`
   - Modify or create template markdown files
   - Include required fields and labels

2. **Modify PR template**
   - Edit `.github/PULL_REQUEST_TEMPLATE.md`
   - Add checklist items and review criteria
   - Include links to related documentation

3. **Update workflow documentation**
   - Update `docs/workflow/github-delivery.md`
   - Document any new processes or requirements
   - Add examples of proper usage

4. **Run verification tests**
   - Test template rendering in GitHub
   - Verify all links and references work
   - Validate template structure

### Documentation Structure Setup
**Trigger:** When someone wants to organize project documentation or establish workflow standards
**Command:** `/setup-docs-structure`

1. **Create docs directory structure**
   ```
   docs/
   ├── workflow/
   ├── templates/
   ├── checklists/
   └── api/
   ```

2. **Add workflow documentation**
   - Create workflow-specific guides in `docs/workflow/`
   - Document development processes
   - Include decision trees and flowcharts

3. **Setup templates**
   - Add reusable templates in `docs/templates/`
   - Create templates for common documentation types
   - Include markdown templates for consistency

4. **Add checklists**
   - Create actionable checklists in `docs/checklists/`
   - Cover code review, deployment, testing
   - Make checklists copy-pasteable

5. **Update main documentation index**
   - Update `AGENTS.md` with new structure
   - Add navigation between documentation sections
   - Include quick reference guides

## Testing Patterns

### Test File Organization
- Test files follow pattern: `*.test.*`
- Likely structure: `ClassName.test.java` or `test/ClassNameTest.java`

### Suggested Test Structure
```java
public class LogProcessorTest {
    @Test
    public void testProcessValidLog() {
        // Arrange
        LogProcessor processor = new LogProcessor();
        String validLog = "2024-01-01 INFO: Test message";
        
        // Act
        Result result = processor.process(validLog);
        
        // Assert
        assertTrue(result.isSuccess());
    }
}
```

## Commands

| Command | Purpose |
|---------|---------|
| `/update-github-templates` | Configure or update GitHub issue/PR templates and workflow documentation |
| `/setup-docs-structure` | Establish standardized documentation architecture with workflows and templates |
| `/format-java` | Apply consistent Java formatting and import organization |
| `/add-test` | Create test file following project conventions |
| `/commit-template` | Generate properly formatted commit message with appropriate prefix |