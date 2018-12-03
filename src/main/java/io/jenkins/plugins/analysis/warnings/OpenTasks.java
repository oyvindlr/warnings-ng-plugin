package io.jenkins.plugins.analysis.warnings;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.hm.hafner.analysis.Issue;
import edu.hm.hafner.analysis.IssueBuilder;
import edu.hm.hafner.analysis.ParsingCanceledException;
import edu.hm.hafner.analysis.ParsingException;
import edu.hm.hafner.analysis.Report;
import io.jenkins.plugins.analysis.core.model.StaticAnalysisLabelProvider;
import io.jenkins.plugins.analysis.core.model.Tool;
import io.jenkins.plugins.analysis.core.steps.JobConfigurationModel;
import io.jenkins.plugins.analysis.core.util.LogHandler;
import io.jenkins.plugins.analysis.warnings.tasks.AgentScanner;
import io.jenkins.plugins.analysis.warnings.tasks.TaskScanner;
import io.jenkins.plugins.analysis.warnings.tasks.TaskScanner.CaseMode;
import io.jenkins.plugins.analysis.warnings.tasks.TaskScanner.MatcherMode;
import io.jenkins.plugins.analysis.warnings.tasks.TaskScannerBuilder;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.util.FormValidation;

/**
 * Provides a files scanner that detects open tasks in source code files.
 *
 * @author Ullrich Hafner
 */
public class OpenTasks extends Tool {
    private static final long serialVersionUID = 4692318309214830824L;

    static final String ID = "open-tasks";

    private String high;
    private String normal;
    private String low;
    private boolean ignoreCase;
    private boolean isRegularExpression;
    private String includePattern;
    private String excludePattern;

    // FIXME: rename tag setters

    /**
     * Returns the Ant file-set pattern of files to work with.
     *
     * @return Ant file-set pattern of files to work with
     */
    public String getIncludePattern() {
        return includePattern;
    }

    @DataBoundSetter
    public void setIncludePattern(final String includePattern) {
        this.includePattern = includePattern;
    }

    /**
     * Returns the Ant file-set pattern of files to exclude from work.
     *
     * @return Ant file-set pattern of files to exclude from work
     */
    public String getExcludePattern() {
        return excludePattern;
    }

    @DataBoundSetter
    public void setExcludePattern(final String excludePattern) {
        this.excludePattern = excludePattern;
    }

    /**
     * Returns the high priority tag identifiers.
     *
     * @return the high priority tag identifiers
     */
    public String getHigh() {
        return high;
    }

    @DataBoundSetter
    public void setHigh(final String high) {
        this.high = high;
    }

    /**
     * Returns the normal priority tag identifiers.
     *
     * @return the normal priority tag identifiers
     */
    public String getNormal() {
        return normal;
    }

    @DataBoundSetter
    public void setNormal(final String normal) {
        this.normal = normal;
    }

    /**
     * Returns the low priority tag identifiers.
     *
     * @return the low priority tag identifiers
     */
    public String getLow() {
        return low;
    }

    @DataBoundSetter
    public void setLow(final String low) {
        this.low = low;
    }

    /**
     * Returns whether case should be ignored during the scanning.
     *
     * @return {@code true}  if case should be ignored during the scanning
     */
    public boolean getIgnoreCase() {
        return ignoreCase;
    }

    @DataBoundSetter
    public void setIgnoreCase(final boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    /**
     * Returns whether the identifiers should be treated as regular expression.
     *
     * @return {@code true} if the identifiers should be treated as regular expression
     */
    public boolean getIsRegularExpression() {
        return isRegularExpression;
    }

    @DataBoundSetter
    public void setIsRegularExpression(final boolean isRegularExpression) {
        this.isRegularExpression = isRegularExpression;
    }

    @Override
    public Report scan(final Run<?, ?> run, final FilePath workspace, final Charset sourceCodeEncoding,
            final LogHandler logger) {
        try {
            return workspace.act(new AgentScanner(high, normal, low,
                    ignoreCase ? CaseMode.IGNORE_CASE : CaseMode.CASE_SENSITIVE,
                    isRegularExpression ? MatcherMode.REGEXP_MATCH : MatcherMode.STRING_MATCH,
                    includePattern, excludePattern, sourceCodeEncoding));
        }
        catch (IOException e) {
            throw new ParsingException(e);
        }
        catch (InterruptedException ignored) {
            throw new ParsingCanceledException();
        }
    }

    /** Creates a new instance of {@link OpenTasks}. */
    @DataBoundConstructor
    public OpenTasks() {
        super();
        // empty constructor required for stapler
    }

    /** Descriptor for this static analysis tool. */
    @Symbol("taskScanner")
    @Extension
    public static class Descriptor extends ToolDescriptor {
        private final JobConfigurationModel model = new JobConfigurationModel();

        /** Creates the descriptor instance. */
        public Descriptor() {
            super(ID);
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.Warnings_OpenTasks_Name();
        }

        @Override
        public StaticAnalysisLabelProvider getLabelProvider() {
            return new IconLabelProvider(getId(), getDisplayName());
        }

        /**
         * Performs on-the-fly validation on the ant pattern for input files.
         *
         * @param project
         *         the project
         * @param includePattern
         *         the file pattern
         *
         * @return the validation result
         */
        public FormValidation doCheckIncludePattern(@AncestorInPath final AbstractProject<?, ?> project,
                @QueryParameter final String includePattern) {
            return model.doCheckPattern(project, includePattern);
        }

        /**
         * Performs on-the-fly validation on the ant pattern for input files.
         *
         * @param project
         *         the project
         * @param excludePattern
         *         the file pattern
         *
         * @return the validation result
         */
        public FormValidation doCheckExcludePattern(@AncestorInPath final AbstractProject<?, ?> project,
                @QueryParameter final String excludePattern) {
            return model.doCheckPattern(project, excludePattern);
        }

        /**
         * Validates the example text that will be scanned for open tasks.
         *
         * @param example
         *         the text to be scanned for open tasks
         * @param high
         *         tag identifiers indicating high priority
         * @param normal
         *         tag identifiers indicating normal priority
         * @param low
         *         tag identifiers indicating low priority
         * @param ignoreCase
         *         if case should be ignored during matching
         * @param asRegexp
         *         if the identifiers should be treated as regular expression
         *
         * @return validation result
         */
        public FormValidation doCheckExample(@QueryParameter final String example,
                @QueryParameter final String high,
                @QueryParameter final String normal,
                @QueryParameter final String low,
                @QueryParameter final boolean ignoreCase,
                @QueryParameter final boolean asRegexp) {
            if (StringUtils.isEmpty(example)) {
                return FormValidation.ok();
            }

            TaskScannerBuilder builder = new TaskScannerBuilder();
            TaskScanner scanner = builder.setHigh(high)
                    .setNormal(normal)
                    .setLow(low)
                    .setCaseMode(ignoreCase ? CaseMode.IGNORE_CASE : CaseMode.CASE_SENSITIVE)
                    .setMatcherMode(asRegexp ? MatcherMode.REGEXP_MATCH : MatcherMode.STRING_MATCH).build();

            if (scanner.isInvalidPattern()) {
                return FormValidation.error(scanner.getErrors());
            }

            Report tasks = scanner.scan(new StringReader(example), new IssueBuilder());
            if (tasks.isEmpty()) {
                return FormValidation.warning(Messages.OpenTasks_Validation_NoTask());
            }
            else if (tasks.size() != 1) {
                return FormValidation.warning(Messages.OpenTasks_Validation_MultipleTasks(tasks.size()));
            }
            else {
                Issue task = tasks.get(0);
                return FormValidation.ok(Messages.OpenTasks_Validation_OneTask(task.getType(), task.getMessage()));
            }
        }
    }
}
