# logcopilot Development Patterns

> Auto-generated skill from repository analysis

## Overview

The logcopilot repository is a Java-based project focused on documentation management and GitHub workflow automation. This codebase emphasizes structured documentation workflows, GitHub template maintenance, and comprehensive workflow documentation. The project follows mixed conventions but maintains consistency within individual components.

## Coding Conventions

### File Naming
- **Style:** camelCase for Java files
- **Examples:**
  ```
  logProcessor.java
  workflowManager.java
  documentationHandler.java
  ```

### Import Organization
- **Style:** Mixed - varies by component
- **Pattern:** Standard Java imports with both wildcard and specific imports used contextually

### Documentation Structure
- **Workflow docs:** `docs/workflow/*.md`
- **Templates:** `.github/ISSUE_TEMPLATE/*.md`
- **Main docs:** `docs/README.md`, `AGENTS.md`

### Commit Message Format
- **Average length:** 36 characters
- **Prefixes used:** `docs:`, `init:`
- **Examples:**
  ```
  docs: update workflow documentation
  init: setup GitHub issue templates
  ```

## Workflows

### GitHub Template Maintenance
**Trigger:** When someone wants to modify GitHub workflow policies or templates
**Command:** `/update-github-templates`

1. **Update Issue Templates**
   - Modify files in `.github/ISSUE_TEMPLATE/`
   - Ensure proper YAML frontmatter for each template
   - Validate template formatting

2. **Update Workflow Documentation**
   - Edit `docs/workflow/github-delivery.md`
   - Sync changes with related workflow files
   - Update any cross-references

3. **Update Documentation References**
   - Check `docs/README.md` for template references
   - Update `AGENTS.md` if workflow agents are affected
   - Ensure consistency across all docs

4. **Run Verification Tests**
   - Validate template syntax
   - Check documentation links
   - Verify workflow file integrity

**Files typically modified:**
- `.github/ISSUE_TEMPLATE/*.md`
- `docs/workflow/github-delivery.md`

### Documentation Structure Changes
**Trigger:** When someone wants to reorganize docs or update workflow documentation
**Command:** `/restructure-docs`

1. **Plan Documentation Structure**
   - Review current `docs/` directory organization
   - Identify restructuring needs
   - Plan new hierarchy if needed

2. **Modify Workflow Documentation**
   - Update files in `docs/workflow/` directory
   - Ensure workflow steps remain accurate
   - Update cross-references between workflow docs

3. **Update Index Files**
   - Modify `docs/README.md` to reflect changes
   - Update `AGENTS.md` with new documentation paths
   - Ensure navigation remains intuitive

4. **Verification and Testing**
   - Run build verification processes
   - Check all internal documentation links
   - Validate markdown formatting

**Files typically modified:**
- `docs/workflow/*.md`
- `docs/README.md`
- `AGENTS.md`

## Testing Patterns

### Test File Organization
- **Pattern:** `*.test.*` naming convention
- **Framework:** Testing framework not clearly detected
- **Structure:** Tests appear to be organized alongside main code

### Testing Approach
```java
// Example test structure (inferred)
public class WorkflowManagerTest {
    // Test methods following *.test.* pattern
    public void testGithubTemplateUpdate() {
        // Test implementation
    }
}
```

## Commands

| Command | Purpose |
|---------|---------|
| `/update-github-templates` | Update GitHub issue/PR templates and related workflow documentation |
| `/restructure-docs` | Reorganize documentation structure and update workflow files |
| `/verify-docs` | Run verification tests on documentation and templates |
| `/sync-workflows` | Synchronize workflow documentation across all related files |